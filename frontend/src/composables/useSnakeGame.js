import { ref, onUnmounted } from 'vue'

/**
 * 贪吃蛇游戏引擎（与 Vue/C DOM 解耦的纯逻辑模块）+ Vue 响应式包装
 *
 * 设计要点：
 * 1. 固定步长累加器（fixed timestep accumulator）：update 按固定逻辑间隔执行，
 *    render 每帧执行，帧率波动不会改变游戏速度；切后台返回时限流，避免蛇瞬间瞬移。
 * 2. 难度递增：每吃 FOODS_PER_LEVEL 个食物升一级，移动间隔线性缩短至下限。
 * 3. 无限模式：无通关条件，撞到墙或自己才结束。
 * 4. 方向输入缓冲队列：一帧内连续按两个方向不会因"立即反向"误判自杀。
 */

/** 棋盘边长（20×20 格） */
export const GRID_SIZE = 20

/** 1 级时每步间隔（毫秒），越小越快 */
const BASE_INTERVAL_MS = 170
/** 最快移动间隔（毫秒），难度上限 */
const MIN_INTERVAL_MS = 70
/** 每升一级缩短的间隔 */
const SPEED_DOWN_PER_LEVEL_MS = 9
/** 每升一级需要吃掉的普通食物数 */
const FOODS_PER_LEVEL = 5
/** 普通食物得分 */
const NORMAL_FOOD_SCORE = 10
/** 金色奖励食物得分 */
const GOLDEN_FOOD_SCORE = 50
/** 金色奖励食物存活时长（毫秒），超时消失 */
const GOLDEN_FOOD_LIFETIME_MS = 7000
/** 吃掉普通食物后刷出金色奖励食物的概率 */
const GOLDEN_FOOD_CHANCE = 0.22
/** 单帧最大补步步数（防止切后台后 accumulator 暴涨连走多步） */
const MAX_STEPS_PER_FRAME = 5

/** 方向向量表 */
const DIR_VECTORS = {
  up: { x: 0, y: -1 },
  down: { x: 0, y: 1 },
  left: { x: -1, y: 0 },
  right: { x: 1, y: 0 }
}
/** 反方向表（180°掉头非法） */
const OPPOSITE_DIR = { up: 'down', down: 'up', left: 'right', right: 'left' }

function isSameCell(a, b) {
  return !!a && !!b && a.x === b.x && a.y === b.y
}

/**
 * 贪吃蛇核心引擎：只管状态与规则，不触碰 DOM
 */
class SnakeGameEngine {
  constructor() {
    // 状态机：idle 待开始 / running 进行中 / paused 暂停 / over 已结束
    this.status = 'idle'
    this.resetState()

    // 循环调度
    this.rafId = null
    this.lastFrameAt = 0
    this.accumulator = 0
    this.boundFrame = this.frame.bind(this)
    this.boundHandleVisibility = this.handleVisibilityChange.bind(this)
    document.addEventListener('visibilitychange', this.boundHandleVisibility)

    // 由视图层注入的回调
    this.onHud = null // 分数/等级/状态变化
    this.onFrame = null // 每帧渲染钩子
    this.onGameOver = null // 游戏结束钩子
  }

  /** 初始化/重置一局的全部数据 */
  resetState() {
    const center = Math.floor(GRID_SIZE / 2)
    // 蛇头在数组首位，初始长度 3，向右移动
    this.snake = [
      { x: center, y: center },
      { x: center - 1, y: center },
      { x: center - 2, y: center }
    ]
    this.direction = 'right'
    this.dirQueue = []
    this.score = 0
    this.level = 1
    this.foodsInLevel = 0
    this.food = this.spawnFood()
    this.golden = null
    this.elapsedMs = 0
  }

  /** 当前等级对应的移动间隔（毫秒） */
  get stepInterval() {
    return Math.max(MIN_INTERVAL_MS, BASE_INTERVAL_MS - (this.level - 1) * SPEED_DOWN_PER_LEVEL_MS)
  }

  /** 开始新一局（idle/over 状态下调用） */
  start() {
    if (this.status === 'running') return
    this.resetState()
    this.status = 'running'
    this.lastFrameAt = 0
    this.accumulator = 0
    this.emitHud()
    this.rafId = requestAnimationFrame(this.boundFrame)
  }

  pause() {
    if (this.status !== 'running') return
    this.status = 'paused'
    this.cancelFrame()
    this.emitHud()
  }

  resume() {
    if (this.status !== 'paused') return
    this.status = 'running'
    // 重置帧时间，避免暂停期间的时间差被计入补步
    this.lastFrameAt = 0
    this.rafId = requestAnimationFrame(this.boundFrame)
    this.emitHud()
  }

  togglePause() {
    if (this.status === 'running') {
      this.pause()
    } else if (this.status === 'paused') {
      this.resume()
    }
  }

  /**
   * 转向请求（加入缓冲队列，最多缓存 2 个）
   * 以"队列末尾的方向"为基准过滤同向/反向输入，避免一帧内 180° 掉头
   */
  turn(dir) {
    if (!DIR_VECTORS[dir]) return
    if (this.status !== 'running' && this.status !== 'paused') return
    const baseDir = this.dirQueue.length > 0
      ? this.dirQueue[this.dirQueue.length - 1]
      : this.direction
    if (dir === baseDir || dir === OPPOSITE_DIR[baseDir]) return
    if (this.dirQueue.length < 2) {
      this.dirQueue.push(dir)
    }
  }

  /** 销毁引擎：取消循环与事件监听 */
  destroy() {
    this.cancelFrame()
    document.removeEventListener('visibilitychange', this.boundHandleVisibility)
  }

  /** 切到后台标签页时自动暂停，防止回来时 accumulator 爆炸式补步 */
  handleVisibilityChange() {
    if (document.hidden && this.status === 'running') {
      this.pause()
    }
  }

  cancelFrame() {
    if (this.rafId !== null) {
      cancelAnimationFrame(this.rafId)
      this.rafId = null
    }
  }

  /** requestAnimationFrame 主循环：固定步长 update + 每帧 render */
  frame(timestamp) {
    if (this.status !== 'running') return
    if (!this.lastFrameAt) this.lastFrameAt = timestamp
    let delta = timestamp - this.lastFrameAt
    this.lastFrameAt = timestamp
    // 单帧时差兜底（卡顿/切后台瞬间）
    if (delta > 1000) delta = 1000
    this.elapsedMs += delta

    // 金色食物按墙钟时间过期
    if (this.golden && timestamp >= this.golden.expireAt) {
      this.golden = null
    }

    // 固定步长补步
    this.accumulator += delta
    let steps = 0
    while (this.accumulator >= this.stepInterval
      && steps < MAX_STEPS_PER_FRAME
      && this.status === 'running') {
      this.step(timestamp)
      this.accumulator -= this.stepInterval
      steps++
    }
    // 达到补步上限说明已严重滞后，丢弃积压时间，不追帧
    if (steps >= MAX_STEPS_PER_FRAME) {
      this.accumulator = 0
    }

    if (this.onFrame) this.onFrame()
    if (this.status === 'running') {
      this.rafId = requestAnimationFrame(this.boundFrame)
    }
  }

  /** 推进一个逻辑步（蛇移动一格） */
  step(frameTimestamp) {
    // 1. 应用缓冲的转向
    if (this.dirQueue.length > 0) {
      this.direction = this.dirQueue.shift()
    }
    const vector = DIR_VECTORS[this.direction]
    const oldHead = this.snake[0]
    const newHead = { x: oldHead.x + vector.x, y: oldHead.y + vector.y }

    // 2. 撞墙判定
    if (newHead.x < 0 || newHead.x >= GRID_SIZE || newHead.y < 0 || newHead.y >= GRID_SIZE) {
      this.gameOver()
      return
    }

    const eatNormal = isSameCell(newHead, this.food)
    const eatGolden = isSameCell(newHead, this.golden)

    // 3. 撞自己判定：不吃食物时蛇尾会让出一格，因此排除尾节
    const bodyToCheck = eatNormal ? this.snake : this.snake.slice(0, -1)
    if (bodyToCheck.some((cell) => isSameCell(cell, newHead))) {
      this.gameOver()
      return
    }

    // 4. 移动：头进
    this.snake.unshift(newHead)

    // 5. 进食结算
    if (eatNormal) {
      // 吃普通食物：增长（不弹尾）、加分、升级判定、刷新食物
      this.score += NORMAL_FOOD_SCORE
      this.foodsInLevel += 1
      if (this.foodsInLevel >= FOODS_PER_LEVEL) {
        this.level += 1
        this.foodsInLevel = 0
      }
      this.food = this.spawnFood()
      // 场上没有金色食物时，按概率刷出一个限时奖励
      if (!this.golden && Math.random() < GOLDEN_FOOD_CHANCE) {
        this.golden = {
          ...this.spawnFood(),
          expireAt: frameTimestamp + GOLDEN_FOOD_LIFETIME_MS
        }
      }
    } else if (eatGolden) {
      // 金色食物：只加奖励分，不增长
      this.score += GOLDEN_FOOD_SCORE
      this.golden = null
      this.snake.pop()
    } else {
      // 普通移动：尾出
      this.snake.pop()
    }

    this.emitHud()
  }

  gameOver() {
    this.status = 'over'
    this.cancelFrame()
    this.emitHud()
    if (this.onGameOver) {
      this.onGameOver({
        score: this.score,
        level: this.level,
        durationMs: this.elapsedMs
      })
    }
  }

  /** 在空格子中随机生成食物（避开蛇身、普通食物、金色食物） */
  spawnFood() {
    const occupied = new Set()
    occupied.add(`${this.food?.x},${this.food?.y}`)
    occupied.add(`${this.golden?.x},${this.golden?.y}`)
    this.snake.forEach((cell) => occupied.add(`${cell.x},${cell.y}`))
    const emptyCells = []
    for (let y = 0; y < GRID_SIZE; y++) {
      for (let x = 0; x < GRID_SIZE; x++) {
        if (!occupied.has(`${x},${y}`)) emptyCells.push({ x, y })
      }
    }
    if (emptyCells.length === 0) return null
    return emptyCells[Math.floor(Math.random() * emptyCells.length)]
  }

  emitHud() {
    if (this.onHud) this.onHud()
  }
}

/**
 * Vue 包装：把引擎状态镜像为响应式 ref，并暴露控制方法与引擎实例（供渲染层直读）
 */
export function useSnakeGame() {
  const engine = new SnakeGameEngine()

  const status = ref(engine.status)
  const score = ref(engine.score)
  const level = ref(engine.level)
  const foodsInLevel = ref(engine.foodsInLevel)
  const elapsedMs = ref(0)

  // 视图层注入的 Canvas 渲染器（每帧执行）
  let renderer = null

  // HUD 数据变化时同步到响应式 ref
  engine.onHud = () => {
    status.value = engine.status
    score.value = engine.score
    level.value = engine.level
    foodsInLevel.value = engine.foodsInLevel
  }
  // 每帧同步计时并驱动 Canvas 渲染（渲染不经 Vue 响应式，避免高频 patch）
  engine.onFrame = () => {
    elapsedMs.value = engine.elapsedMs
    if (renderer) renderer()
  }

  onUnmounted(() => {
    engine.destroy()
  })

  return {
    engine,
    status,
    score,
    level,
    foodsInLevel,
    elapsedMs,
    bindRenderer: (fn) => {
      renderer = fn
    },
    start: () => engine.start(),
    pause: () => engine.pause(),
    resume: () => engine.resume(),
    togglePause: () => engine.togglePause(),
    turn: (dir) => engine.turn(dir)
  }
}
