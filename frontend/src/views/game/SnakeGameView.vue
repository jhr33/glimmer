<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useSnakeGame, GRID_SIZE } from '@/composables/useSnakeGame'
import { useUserStore } from '@/stores/user'
import { submitScore, getLeaderboard, getMyBest, getMyAlias, saveMyAlias } from '@/api/game'

const router = useRouter()
const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)

// ==================== 游戏引擎接入 ====================
const {
  engine,
  status,
  score,
  level,
  foodsInLevel,
  elapsedMs,
  bindRenderer,
  start,
  pause,
  resume,
  togglePause,
  turn
} = useSnakeGame()

/** 触摸设备判定：决定是否展示屏幕方向键 */
const isTouchDevice = ref(
  typeof window !== 'undefined'
  && (window.matchMedia?.('(pointer: coarse)').matches || 'ontouchstart' in window)
)

// ==================== HUD 展示数据 ====================
/** 等级进度（每级吃 5 个食物） */
const levelProgress = computed(() => Math.round((foodsInLevel.value / 5) * 100))
/** 当前移动速度（格/秒），与引擎固定步长保持一致 */
const speedPerSecond = computed(() => {
  const interval = Math.max(70, 170 - (level.value - 1) * 9)
  return (1000 / interval).toFixed(1)
})
/** 本局时长 mm:ss */
const durationText = computed(() => {
  const total = Math.floor(elapsedMs.value / 1000)
  const m = String(Math.floor(total / 60)).padStart(2, '0')
  const s = String(total % 60).padStart(2, '0')
  return `${m}:${s}`
})

// ==================== 本地最高分（游客也可见） ====================
const BEST_KEY = 'glimmer_snake_best_v1'
const localBest = ref(0)
const isNewLocalBest = ref(false)

function loadLocalBest() {
  try {
    const raw = localStorage.getItem(BEST_KEY)
    localBest.value = raw ? Number(JSON.parse(raw).score) || 0 : 0
  } catch (e) {
    localBest.value = 0
  }
}

function saveLocalBest(newScore, newLevel) {
  try {
    localStorage.setItem(BEST_KEY, JSON.stringify({ score: newScore, level: newLevel }))
  } catch (e) {
    // 隐私模式等场景静默降级
  }
}

// ==================== 成绩提交与排行榜 ====================
const leaderboard = ref([])
const myBest = ref(null)
/** 提交状态：'' | submitting | success | fail | guest */
const submitState = ref('')
const submitResult = ref(null)
let lastPayload = null

// ==================== 排行榜身份：昵称 / 手动匿名代号 ====================
/** 身份偏好本机持久化键 */
const IDENTITY_KEY = 'glimmer_snake_identity_v1'
/** 上一版本"匿名开关"的旧键，用于一次性迁移偏好 */
const LEGACY_ANON_KEY = 'glimmer_snake_anonymous_v1'
/** 代号合法规则：2-12 位中文/英文/数字/下划线，与后端校验保持一致 */
const ALIAS_PATTERN = /^[一-龥A-Za-z0-9_]{2,12}$/
/** 身份模式：nickname=显示昵称；alias=手动匿名代号 */
const identityMode = ref('nickname')
/** 服务端已保存的代号（null 表示尚未设置） */
const savedAlias = ref(null)
/** 代号输入框内容与保存加载态 */
const aliasInput = ref('')
const aliasSaving = ref(false)

/** 实名展示预览：优先昵称，兜底匿名旅人 */
const nicknamePreview = computed(() => {
  const u = userStore.userInfo
  return u?.nickname || '匿名旅人'
})

/** 匿名是否真正生效：选中代号模式且服务端已存在代号 */
const anonymousEffective = computed(() => identityMode.value === 'alias' && !!savedAlias.value)

function persistIdentity(mode) {
  try {
    localStorage.setItem(IDENTITY_KEY, mode)
  } catch (e) {
    // 隐私模式等场景静默降级
  }
}

function loadIdentityPref() {
  try {
    let mode = localStorage.getItem(IDENTITY_KEY)
    if (!mode) {
      // 兼容旧版本开关：旧键为 1 时默认进入代号模式
      mode = localStorage.getItem(LEGACY_ANON_KEY) === '1' ? 'alias' : 'nickname'
    }
    identityMode.value = mode === 'alias' ? 'alias' : 'nickname'
  } catch (e) {
    identityMode.value = 'nickname'
  }
}

/** 切换身份单选：切昵称立即持久化；代号模式需保存代号后才持久化 */
function onIdentityChange(mode) {
  if (mode === 'nickname') {
    persistIdentity('nickname')
  }
}

async function loadMyAlias() {
  if (!isLoggedIn.value) {
    savedAlias.value = null
    return
  }
  try {
    const res = await getMyAlias('snake')
    savedAlias.value = res.data?.alias || null
    aliasInput.value = savedAlias.value || ''
  } catch (e) {
    savedAlias.value = null
  }
}

/** 保存/修改匿名代号（用户×游戏维度，成功后对之后提交的新成绩生效） */
async function handleSaveAlias() {
  const alias = aliasInput.value.trim()
  if (!ALIAS_PATTERN.test(alias)) {
    ElMessage.warning('匿名代号需为 2-12 位中文、英文、数字或下划线')
    return
  }
  aliasSaving.value = true
  try {
    const res = await saveMyAlias('snake', alias)
    savedAlias.value = res.data.alias
    identityMode.value = 'alias'
    persistIdentity('alias')
    ElMessage.success('匿名代号已保存，之后的成绩将以该代号上榜')
  } catch (e) {
    // request.js 拦截器已统一弹出后端错误提示
  } finally {
    aliasSaving.value = false
  }
}

async function loadLeaderboard() {
  try {
    const res = await getLeaderboard('snake', 20)
    leaderboard.value = res.data || []
  } catch (e) {
    leaderboard.value = []
  }
}

async function loadMyBest() {
  if (!isLoggedIn.value) {
    myBest.value = null
    return
  }
  try {
    const res = await getMyBest('snake')
    myBest.value = res.data
  } catch (e) {
    myBest.value = null
  }
}

async function doSubmit() {
  if (!lastPayload) return
  submitState.value = 'submitting'
  try {
    const res = await submitScore(lastPayload)
    submitResult.value = res.data
    submitState.value = 'success'
    // 提交成功后刷新榜单与个人名次
    await Promise.all([loadLeaderboard(), loadMyBest()])
  } catch (e) {
    submitState.value = 'fail'
  }
}

/** 游戏结束回调（由引擎在撞墙/撞自己时触发） */
function handleGameOver({ score: finalScore, level: finalLevel, durationMs }) {
  // 本地最高分先更新（游客同样保留）
  isNewLocalBest.value = finalScore > 0 && finalScore > localBest.value
  if (isNewLocalBest.value) {
    saveLocalBest(finalScore, finalLevel)
    localBest.value = finalScore
  }
  submitResult.value = null
  if (!isLoggedIn.value || finalScore <= 0) {
    submitState.value = !isLoggedIn.value ? 'guest' : ''
    return
  }
  // 选中代号模式但尚未保存代号：提示并按昵称提交本局，避免成绩丢失
  let anonymous = anonymousEffective.value
  if (identityMode.value === 'alias' && !savedAlias.value) {
    anonymous = false
    ElMessage.warning('匿名代号尚未保存，本局先按昵称上榜')
  }
  lastPayload = {
    gameType: 'snake',
    score: finalScore,
    level: finalLevel,
    durationSeconds: Math.round(durationMs / 1000),
    // 玩家自选：昵称实名 / 手动匿名代号
    anonymous
  }
  doSubmit()
}

// ==================== 游戏控制 ====================
function startGame() {
  submitState.value = ''
  submitResult.value = null
  isNewLocalBest.value = false
  start()
}

/** 空格/回车的主按钮语义：开始 / 暂停 / 继续 */
function handlePrimaryKey() {
  if (status.value === 'running') {
    pause()
  } else if (status.value === 'paused') {
    resume()
  } else {
    startGame()
  }
}

function goLogin() {
  router.push({ name: 'login', query: { redirect: '/game/snake' } })
}

// ==================== 键盘控制（桌面端） ====================
const KEY_DIR_MAP = {
  ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right',
  w: 'up', s: 'down', a: 'left', d: 'right',
  W: 'up', S: 'down', A: 'left', D: 'right'
}

function onKeydown(e) {
  const tag = e.target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA') return
  const dir = KEY_DIR_MAP[e.key]
  if (dir) {
    // 阻止方向键滚动页面
    e.preventDefault()
    turn(dir)
    return
  }
  if (e.code === 'Space') {
    e.preventDefault()
    handlePrimaryKey()
  } else if (e.key === 'Enter') {
    handlePrimaryKey()
  } else if (e.key === 'p' || e.key === 'P') {
    togglePause()
  }
}

// ==================== 触摸滑动控制（移动端） ====================
let touchStartX = 0
let touchStartY = 0

function onTouchStart(e) {
  const touch = e.touches[0]
  touchStartX = touch.clientX
  touchStartY = touch.clientY
}

function onTouchEnd(e) {
  const touch = e.changedTouches[0]
  const dx = touch.clientX - touchStartX
  const dy = touch.clientY - touchStartY
  // 滑动距离不足视为点击，不阻止默认行为——
  // 遮罩层按钮（开始/继续/再来一局）也是 board-wrap 子元素，
  // 无条件 preventDefault 会吞掉它们在移动端的 click 合成
  if (Math.abs(dx) < 20 && Math.abs(dy) < 20) return
  // 确认为滑动手势：阻止页面滚动/回弹等默认行为
  e.preventDefault()
  if (Math.abs(dx) > Math.abs(dy)) {
    turn(dx > 0 ? 'right' : 'left')
  } else {
    turn(dy > 0 ? 'down' : 'up')
  }
}

// ==================== Canvas 渲染（DPR 自适应） ====================
const boardWrapRef = ref(null)
const canvasRef = ref(null)
let resizeObserver = null

function resizeCanvas() {
  const canvas = canvasRef.value
  const wrap = boardWrapRef.value
  if (!canvas || !wrap) return
  const cssSize = wrap.clientWidth
  const dpr = window.devicePixelRatio || 1
  canvas.width = Math.round(cssSize * dpr)
  canvas.height = Math.round(cssSize * dpr)
  render()
}

/** 圆角矩形路径（兼容不支持 ctx.roundRect 的浏览器） */
function roundRectPath(ctx, x, y, w, h, r) {
  const radius = Math.min(r, w / 2, h / 2)
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.arcTo(x + w, y, x + w, y + h, radius)
  ctx.arcTo(x + w, y + h, x, y + h, radius)
  ctx.arcTo(x, y + h, x, y, radius)
  ctx.arcTo(x, y, x + w, y, radius)
  ctx.closePath()
}

function render() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const size = canvas.width // 设备像素
  const cell = size / GRID_SIZE

  // 1. 棋盘背景 + 深浅格纹理
  ctx.fillStyle = '#202736'
  ctx.fillRect(0, 0, size, size)
  for (let y = 0; y < GRID_SIZE; y++) {
    for (let x = 0; x < GRID_SIZE; x++) {
      if ((x + y) % 2 === 0) {
        ctx.fillStyle = 'rgba(255, 255, 255, 0.025)'
        ctx.fillRect(x * cell, y * cell, cell, cell)
      }
    }
  }

  // 2. 普通食物（红色萤果 + 微光）
  if (engine.food) {
    drawFood(ctx, engine.food, cell, '#fb7185', 0.28)
  }

  // 3. 金色限时奖励食物（+50 分），外圈绘制剩余时间环
  if (engine.golden) {
    const remainRatio = Math.max(0, (engine.golden.expireAt - performance.now()) / 7000)
    drawFood(ctx, engine.golden, cell, '#fbbf24', 0.3, remainRatio)
  }

  // 4. 蛇（尾→头顺序绘制，头在最上层）
  const snake = engine.snake
  for (let i = snake.length - 1; i >= 0; i--) {
    const seg = snake[i]
    const isHead = i === 0
    const padding = cell * (isHead ? 0.08 : 0.12)
    const hue = 152 - Math.min(i * 1.2, 30)
    const lightness = isHead ? 58 : Math.max(38, 48 - i * 0.4)
    ctx.fillStyle = `hsl(${hue}, 72%, ${lightness}%)`
    roundRectPath(
      ctx,
      seg.x * cell + padding,
      seg.y * cell + padding,
      cell - padding * 2,
      cell - padding * 2,
      cell * 0.28
    )
    ctx.fill()

    if (isHead) {
      drawHeadEyes(ctx, seg, cell)
    }
  }
}

/** 绘制食物（带辉光；金色食物附剩余时间环） */
function drawFood(ctx, pos, cell, color, sizeRatio, remainRatio = null) {
  const cx = pos.x * cell + cell / 2
  const cy = pos.y * cell + cell / 2
  const r = cell * sizeRatio
  ctx.save()
  ctx.shadowColor = color
  ctx.shadowBlur = cell * 0.35
  ctx.fillStyle = color
  ctx.beginPath()
  ctx.arc(cx, cy, r, 0, Math.PI * 2)
  ctx.fill()
  ctx.restore()
  // 高光点
  ctx.fillStyle = 'rgba(255,255,255,0.55)'
  ctx.beginPath()
  ctx.arc(cx - r * 0.3, cy - r * 0.3, r * 0.22, 0, Math.PI * 2)
  ctx.fill()
  // 金色食物：剩余时间倒计时环
  if (remainRatio !== null) {
    ctx.strokeStyle = 'rgba(251,191,36,0.85)'
    ctx.lineWidth = Math.max(2, cell * 0.06)
    ctx.beginPath()
    ctx.arc(cx, cy, cell * 0.42, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * remainRatio)
    ctx.stroke()
  }
}

/** 根据移动方向绘制蛇头眼睛 */
function drawHeadEyes(ctx, head, cell) {
  const cx = head.x * cell + cell / 2
  const cy = head.y * cell + cell / 2
  const offset = cell * 0.18
  const eyeR = cell * 0.09
  // 两只眼睛相对垂直于移动方向分布
  let eyes
  switch (engine.direction) {
    case 'up':
      eyes = [{ x: cx - offset, y: cy - offset * 0.6 }, { x: cx + offset, y: cy - offset * 0.6 }]
      break
    case 'down':
      eyes = [{ x: cx - offset, y: cy + offset * 0.6 }, { x: cx + offset, y: cy + offset * 0.6 }]
      break
    case 'left':
      eyes = [{ x: cx - offset * 0.6, y: cy - offset }, { x: cx - offset * 0.6, y: cy + offset }]
      break
    default:
      eyes = [{ x: cx + offset * 0.6, y: cy - offset }, { x: cx + offset * 0.6, y: cy + offset }]
  }
  eyes.forEach((eye) => {
    ctx.fillStyle = '#ffffff'
    ctx.beginPath()
    ctx.arc(eye.x, eye.y, eyeR, 0, Math.PI * 2)
    ctx.fill()
    ctx.fillStyle = '#1f2937'
    ctx.beginPath()
    ctx.arc(eye.x, eye.y, eyeR * 0.55, 0, Math.PI * 2)
    ctx.fill()
  })
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadLocalBest()
  loadIdentityPref()
  // 引擎结束回调：落成绩
  engine.onGameOver = handleGameOver
  // 注入渲染器并完成首帧绘制（idle 状态也画出棋盘与初始蛇身）
  bindRenderer(render)
  render()
  // 自适应画布尺寸
  resizeCanvas()
  resizeObserver = new ResizeObserver(resizeCanvas)
  if (boardWrapRef.value) resizeObserver.observe(boardWrapRef.value)
  window.addEventListener('keydown', onKeydown)
  // 榜单数据
  loadLeaderboard()
  loadMyBest()
  loadMyAlias()
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  if (resizeObserver) resizeObserver.disconnect()
})
</script>

<template>
  <div class="snake-page">
    <!-- 顶部返回与标题 -->
    <div class="page-head">
      <el-button text @click="router.push('/game')">← 游戏中心</el-button>
      <h2 class="page-title">🐍 贪吃蛇</h2>
      <span class="page-sub">吃萤果提速，看看谁能在 Glimmer 爬到最久</span>
    </div>

    <div class="snake-layout">
      <!-- ========== 左侧：游戏区 ========== -->
      <div class="game-col">
        <!-- HUD 数据条 -->
        <div class="hud-bar">
          <div class="hud-item">
            <span class="hud-label">得分</span>
            <span class="hud-value score">{{ score }}</span>
          </div>
          <div class="hud-item">
            <span class="hud-label">等级</span>
            <span class="hud-value">Lv.{{ level }}</span>
          </div>
          <div class="hud-item hud-progress-item">
            <span class="hud-label">下一级</span>
            <el-progress
              :percentage="levelProgress"
              :stroke-width="8"
              :show-text="false"
              color="#f5a623"
              class="hud-progress"
            />
          </div>
          <div class="hud-item">
            <span class="hud-label">速度</span>
            <span class="hud-value">{{ speedPerSecond }} 格/秒</span>
          </div>
          <div class="hud-item">
            <span class="hud-label">时长</span>
            <span class="hud-value">{{ durationText }}</span>
          </div>
          <div class="hud-actions">
            <el-button
              v-if="status === 'running'"
              size="small"
              @click="pause()"
            >暂停</el-button>
            <el-button
              v-else-if="status === 'paused'"
              size="small"
              type="primary"
              @click="resume()"
            >继续</el-button>
            <el-button
              size="small"
              :disabled="status === 'idle'"
              @click="startGame()"
            >重开</el-button>
          </div>
        </div>

        <!-- Canvas 棋盘 + 遮罩层 -->
        <div
          ref="boardWrapRef"
          class="board-wrap"
          @touchstart.passive="onTouchStart"
          @touchend="onTouchEnd"
        >
          <canvas ref="canvasRef" class="game-canvas"></canvas>

          <!-- 待开始遮罩 -->
          <div v-if="status === 'idle'" class="overlay">
            <div class="overlay-icon">🐍</div>
            <div class="overlay-title">贪吃蛇</div>
            <div class="overlay-desc">
              普通萤果 +{{ 10 }} 分，金色萤果限时 +{{ 50 }} 分<br />
              每吃 5 个食物提升一级，速度越来越快<br />
              撞墙或撞到自己即结束，挑战你的极限
            </div>
            <div class="control-tip">
              <template v-if="isTouchDevice">👆 滑动棋盘或点击下方方向键控制</template>
              <template v-else>⌨️ 方向键 / WASD 移动，空格暂停</template>
            </div>
            <el-button type="primary" size="large" class="overlay-btn" @click="startGame()">
              开始游戏
            </el-button>
          </div>

          <!-- 暂停遮罩 -->
          <div v-else-if="status === 'paused'" class="overlay">
            <div class="overlay-icon">⏸️</div>
            <div class="overlay-title">已暂停</div>
            <div class="overlay-desc">切到后台会自动暂停，放松一下再继续</div>
            <el-button type="primary" size="large" class="overlay-btn" @click="resume()">
              继续游戏
            </el-button>
          </div>

          <!-- 结束遮罩 -->
          <div v-else-if="status === 'over'" class="overlay">
            <div class="overlay-icon">💥</div>
            <div class="overlay-title">游戏结束</div>
            <div v-if="isNewLocalBest" class="new-record-badge">🎉 新纪录！</div>
            <div class="result-stats">
              <div class="result-score">{{ score }} 分</div>
              <div class="result-meta">达到 Lv.{{ level }} · 用时 {{ durationText }}</div>
            </div>

            <!-- 成绩提交状态 -->
            <div class="submit-area">
              <div v-if="submitState === 'submitting'" class="submit-tip">成绩提交中…</div>
              <template v-else-if="submitState === 'success'">
                <div class="submit-tip success">
                  ✅ 已记入排行榜
                  <template v-if="submitResult?.rank"> · 当前第 {{ submitResult.rank }} 名</template>
                </div>
                <div v-if="submitResult?.anonymous" class="submit-tip">
                  🎭 已以「{{ submitResult.displayName }}」匿名上榜
                </div>
                <div v-if="submitResult?.newPersonalBest" class="submit-tip">🏆 刷新了你的个人纪录！</div>
              </template>
              <template v-else-if="submitState === 'fail'">
                <div class="submit-tip fail">成绩提交失败</div>
                <el-button size="small" @click="doSubmit">重试</el-button>
              </template>
              <template v-else-if="submitState === 'guest'">
                <div class="submit-tip">登录后成绩可计入排行榜</div>
                <el-button size="small" type="primary" @click="goLogin">去登录</el-button>
              </template>
            </div>

            <el-button type="primary" size="large" class="overlay-btn" @click="startGame()">
              再来一局
            </el-button>
          </div>
        </div>

        <!-- 移动端方向键（触摸设备显示） -->
        <div v-if="isTouchDevice" class="dpad">
          <el-button
            class="dpad-btn dpad-up"
            circle
            size="large"
            @pointerdown.prevent.stop="turn('up')"
          >▲</el-button>
          <el-button
            class="dpad-btn dpad-left"
            circle
            size="large"
            @pointerdown.prevent.stop="turn('left')"
          >◀</el-button>
          <el-button
            class="dpad-btn dpad-down"
            circle
            size="large"
            @pointerdown.prevent.stop="turn('down')"
          >▼</el-button>
          <el-button
            class="dpad-btn dpad-right"
            circle
            size="large"
            @pointerdown.prevent.stop="turn('right')"
          >▶</el-button>
        </div>

        <div v-else class="desktop-tip">⌨️ 方向键 / WASD 控制方向 · 空格暂停或继续 · P 键暂停</div>
      </div>

      <!-- ========== 右侧：排行榜 ========== -->
      <div class="side-col">
        <el-card class="rank-card" shadow="never">
          <template #header>
            <div class="rank-header">
              <span>🏆 贪吃蛇排行榜</span>
            </div>
          </template>

          <!-- 排行榜身份设置（仅登录用户）：昵称 或 手动匿名代号 -->
          <div v-if="isLoggedIn" class="identity-setting">
            <div class="identity-title">🏷️ 排行榜显示身份</div>
            <el-radio-group
              v-model="identityMode"
              size="small"
              class="identity-radio-group"
              @change="onIdentityChange"
            >
              <el-radio value="nickname" class="identity-radio">
                昵称（{{ nicknamePreview }}）
              </el-radio>
              <el-radio value="alias" class="identity-radio">匿名代号</el-radio>
            </el-radio-group>
            <!-- 代号编辑区：选中"匿名代号"时展开 -->
            <div v-if="identityMode === 'alias'" class="alias-editor">
              <el-input
                v-model="aliasInput"
                size="small"
                maxlength="12"
                placeholder="2-12位中文/英文/数字/下划线"
                clearable
              />
              <el-button
                type="primary"
                size="small"
                :loading="aliasSaving"
                @click="handleSaveAlias"
              >保存</el-button>
            </div>
            <div v-if="identityMode === 'alias'" class="identity-tip">
              <template v-if="savedAlias">
                当前代号「{{ savedAlias }}」；代号仅用于贪吃蛇排行榜，修改保存后对新成绩生效，历史成绩不变
              </template>
              <template v-else>
                代号仅用于贪吃蛇排行榜（与篝火/漂流瓶无关），请先输入并保存
              </template>
            </div>
          </div>

          <!-- 我的最佳 -->
          <div v-if="isLoggedIn && myBest" class="my-best">
            <template v-if="myBest.bestScore > 0">
              <span class="my-best-score">{{ myBest.bestScore }} 分</span>
              <span class="my-best-meta">
                最佳第 <b>{{ myBest.rank ?? '-' }}</b> 名 · 共 {{ myBest.playCount }} 局
              </span>
            </template>
            <span v-else class="my-best-empty">还没有成绩，开始第一局吧</span>
          </div>
          <div v-else class="my-best guest-best">
            <span class="my-best-score">{{ localBest }} 分</span>
            <span class="my-best-meta">本机最高分 · 登录后上榜</span>
          </div>

          <!-- 榜单列表 -->
          <div class="rank-list">
            <div v-if="leaderboard.length === 0" class="rank-empty">
              榜单空空如也，来拿下第一名吧
            </div>
            <div
              v-for="item in leaderboard"
              :key="item.rank"
              class="rank-row"
              :class="{ me: item.currentUser, anonymous: item.anonymous }"
            >
              <span class="rank-no" :class="'rank-no-' + item.rank">{{ item.rank }}</span>
              <span class="rank-name">
                <span v-if="item.anonymous" class="anon-badge" title="匿名上榜">🎭</span>
                {{ item.displayName }}
              </span>
              <span class="rank-level">Lv.{{ item.level }}</span>
              <span class="rank-score">{{ item.score }}</span>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<style scoped>
.snake-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.page-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.page-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}
.page-sub {
  font-size: 13px;
  color: #909399;
}

.snake-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}
.game-col {
  flex: 1 1 auto;
  min-width: 0;
  max-width: 520px;
}
.side-col {
  flex: 0 0 300px;
  width: 300px;
}

/* ===== HUD ===== */
.hud-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #f0e6d2;
  border-radius: 10px 10px 0 0;
  border-bottom: none;
  flex-wrap: wrap;
}
.hud-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.hud-label {
  font-size: 11px;
  color: #909399;
}
.hud-value {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}
.hud-value.score {
  color: #f5a623;
}
.hud-progress-item {
  min-width: 90px;
}
.hud-progress {
  width: 90px;
}
.hud-actions {
  margin-left: auto;
  display: flex;
  gap: 6px;
}

/* ===== 棋盘 ===== */
.board-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1;
  border-radius: 0 0 10px 10px;
  overflow: hidden;
  /* 阻止移动端滑动棋盘时带动页面滚动/下拉刷新 */
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
  box-shadow: 0 4px 16px rgba(32, 39, 54, 0.18);
}
.game-canvas {
  display: block;
  width: 100%;
  height: 100%;
}

/* ===== 遮罩层 ===== */
.overlay {
  position: absolute;
  inset: 0;
  background: rgba(20, 24, 33, 0.82);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 16px;
  text-align: center;
  color: #fff;
}
.overlay-icon {
  font-size: 44px;
  line-height: 1;
}
.overlay-title {
  font-size: 24px;
  font-weight: 700;
}
.overlay-desc {
  font-size: 13px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.78);
}
.control-tip {
  font-size: 13px;
  color: #ffd970;
  margin: 2px 0 6px;
}
.overlay-btn {
  margin-top: 6px;
  min-width: 160px;
}
.new-record-badge {
  color: #ffd970;
  font-weight: 700;
  font-size: 15px;
}
.result-stats {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.result-score {
  font-size: 36px;
  font-weight: 800;
  color: #fbbf24;
  line-height: 1.1;
}
.result-meta {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
}
.submit-area {
  min-height: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.submit-tip {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.82);
}
.submit-tip.success {
  color: #86efac;
}
.submit-tip.fail {
  color: #fca5a5;
}

/* ===== 移动端方向键 ===== */
.dpad {
  margin: 16px auto 4px;
  display: grid;
  grid-template-columns: repeat(3, 64px);
  grid-template-rows: repeat(3, 64px);
  gap: 8px;
  justify-content: center;
}
.dpad-btn {
  width: 64px !important;
  height: 64px !important;
  font-size: 18px;
}
.dpad-up { grid-column: 2; grid-row: 1; }
.dpad-left { grid-column: 1; grid-row: 2; }
.dpad-down { grid-column: 2; grid-row: 3; }
.dpad-right { grid-column: 3; grid-row: 2; }

.desktop-tip {
  margin-top: 14px;
  text-align: center;
  font-size: 13px;
  color: #909399;
}

/* ===== 排行榜卡片 ===== */
.rank-card {
  border-radius: 10px;
  border-color: #f0e6d2;
}
.rank-card :deep(.el-card__header) {
  padding: 12px 16px;
}
.rank-header {
  font-weight: 700;
  color: #303133;
}

/* ===== 排行榜身份设置（昵称/匿名代号） ===== */
.identity-setting {
  padding: 10px 12px;
  margin-bottom: 12px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}
.identity-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.identity-radio-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}
.identity-radio {
  height: auto;
  margin-right: 0;
  white-space: normal;
}
.alias-editor {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.identity-tip {
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.5;
  color: #909399;
}

/* ===== 排行榜匿名行 ===== */
.anon-badge {
  margin-right: 4px;
  font-size: 13px;
}
.rank-row.anonymous .rank-name {
  color: #7a8699;
}
.my-best {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  background: #fff8e6;
  border: 1px solid #ffe6a8;
  border-radius: 8px;
  margin-bottom: 12px;
}
.my-best.guest-best {
  background: #f5f7fa;
  border-color: #e4e7ed;
}
.my-best-score {
  font-size: 20px;
  font-weight: 800;
  color: #f5a623;
}
.my-best-meta {
  font-size: 12px;
  color: #909399;
}
.my-best-empty {
  font-size: 13px;
  color: #909399;
}
.rank-list {
  display: flex;
  flex-direction: column;
}
.rank-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: #c0c4cc;
}
.rank-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  border-bottom: 1px dashed #f0e6d2;
  font-size: 14px;
}
.rank-row:last-child {
  border-bottom: none;
}
.rank-row.me {
  background: #fff8e6;
  border-radius: 6px;
  padding-left: 8px;
  padding-right: 8px;
  font-weight: 700;
}
.rank-no {
  width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
  border-radius: 50%;
  background: #f0f2f5;
  color: #909399;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}
.rank-no-1 { background: #fbbf24; color: #fff; }
.rank-no-2 { background: #cbd5e1; color: #fff; }
.rank-no-3 { background: #d99a6c; color: #fff; }
.rank-name {
  flex: 1;
  color: #303133;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.rank-level {
  font-size: 12px;
  color: #909399;
}
.rank-score {
  font-weight: 700;
  color: #f5a623;
  min-width: 48px;
  text-align: right;
}

/* ===== 响应式：窄屏（手机）上下布局 ===== */
@media (max-width: 768px) {
  .snake-layout {
    flex-direction: column;
    gap: 14px;
  }
  .game-col {
    max-width: 520px;
    width: 100%;
    margin: 0 auto;
  }
  .side-col {
    flex: none;
    width: 100%;
    max-width: 520px;
    margin: 0 auto;
  }
  .page-sub {
    display: none;
  }
}
</style>
