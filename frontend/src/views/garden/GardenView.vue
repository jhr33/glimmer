<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  getMyFlowers,
  waterFlower,
  getFlower,
  getFlowerTypes
} from '@/api/garden'
import { useUserStore } from '@/stores/user'
import FireflyCanvas from '@/components/FireflyCanvas.vue'

const router = useRouter()
const userStore = useUserStore()
const user = computed(() => userStore.userInfo || {})

const totalFirefly = computed(() => user.value.totalFirefly ?? 0)
const fireflyBalance = computed(() => user.value.fireflyBalance ?? 0)

const flowerTypeMap = ref(new Map())

async function fetchFlowerTypes() {
  try {
    const res = await getFlowerTypes()
    const list = pickList(res.data)
    const map = new Map()
    list.forEach((ft) => {
      const id = ft?.id ?? ft?.flowerTypeId ?? ft?.flower_type_id
      if (id != null) map.set(Number(id), ft)
    })
    flowerTypeMap.value = map
  } catch (e) {
    flowerTypeMap.value = new Map()
  }
}

function getFlowerTypeById(typeId) {
  if (typeId == null) return null
  return flowerTypeMap.value.get(Number(typeId)) || null
}

function calculateFireflyCount(total) {
  const v = Number(total) || 0
  if (v === 0) return 0
  if (v <= 5) return Math.floor(Math.random() * 4) + 5
  if (v <= 15) return Math.floor(Math.random() * 6) + 15
  if (v <= 30) return Math.floor(Math.random() * 11) + 30
  if (v <= 50) return Math.floor(Math.random() * 21) + 50
  if (v <= 100) return Math.floor(Math.random() * 41) + 80
  return Math.floor(Math.random() * 51) + 150
}
const fireflyCount = ref(calculateFireflyCount(totalFirefly.value))
import { watch } from 'vue'
watch(totalFirefly, (newVal) => {
  fireflyCount.value = calculateFireflyCount(newVal)
})

function getBrightnessLevel(total) {
  const v = Number(total) || 0
  if (v >= 200) return 5
  if (v >= 100) return 4
  if (v >= 60) return 3
  if (v >= 30) return 2
  if (v >= 10) return 1
  return 0
}
const brightnessLevel = computed(() => getBrightnessLevel(totalFirefly.value))

const gardenBrightness = computed(() => {
  const map = [0.25, 0.45, 0.65, 0.85, 1.0, 1.15]
  return map[brightnessLevel.value] ?? 0.25
})

const STAGE_LABELS = {
  seed: '种子',
  sprout: '幼苗',
  seedling: '中苗',
  bud: '花苞',
  bloom: '开放'
}

function stageLabel(s) {
  return STAGE_LABELS[s] || s || '-'
}

function flowerIdOf(f) {
  return f?.id ?? f?.flowerId ?? f?.flower_id
}

function getFlowerType(f) {
  const nested = f?.flowerType ?? f?.flower_type ?? null
  if (nested) return nested
  const typeId = f?.flowerTypeId ?? f?.flower_type_id
  return getFlowerTypeById(typeId)
}

function getStageWaterCount(f) {
  return f?.stageWaterCount ?? f?.stage_water_count ?? 0
}
function getCurrentStageThreshold(f) {
  return f?.currentStageThreshold ?? f?.current_stage_threshold ?? 0
}
function getProgress(f) {
  const cur = getStageWaterCount(f)
  const thr = getCurrentStageThreshold(f)
  if (!thr || thr <= 0) return 0
  const p = Math.round((cur / thr) * 100)
  return Math.min(p, 100)
}

const WATER_COST = 2

function isBloomed(f) {
  return f?.stage === 'bloom'
}
function canWater(f) {
  return !isBloomed(f) && fireflyBalance.value >= WATER_COST
}
function waterDisabledReason(f) {
  if (isBloomed(f)) return '已开花'
  if (fireflyBalance.value < WATER_COST) return `萤火余额不足（需 ${WATER_COST} 点）`
  return ''
}

const FLOWER_COLORS = [
  { primary: '#ff6b9d', secondary: '#ffc0d6', center: '#fff0f5', shadow: '#cc3366' }, // 粉
  { primary: '#e74c3c', secondary: '#ff8a80', center: '#ffebee', shadow: '#b71c1c' }, // 红
  { primary: '#eceff1', secondary: '#ffffff', center: '#fffde7', shadow: '#9e9e9e' }, // 白
  { primary: '#9b59b6', secondary: '#d6a3e6', center: '#f3e5f5', shadow: '#6a1b9a' }, // 紫
  { primary: '#f1c40f', secondary: '#fff59d', center: '#fffde7', shadow: '#f57f17' }, // 黄
  { primary: '#3498db', secondary: '#81c7ec', center: '#e3f2fd', shadow: '#1565c0' }  // 蓝
]
function getFlowerColor(f) {
  const typeId = f?.flowerTypeId ?? f?.flower_type_id
  const idx = (Number(typeId) || 0) % FLOWER_COLORS.length
  return FLOWER_COLORS[idx]
}
function flowerColorStyle(f) {
  const c = getFlowerColor(f)
  return {
    '--flower-primary': c.primary,
    '--flower-secondary': c.secondary,
    '--flower-center': c.center,
    '--flower-shadow': c.shadow
  }
}

const myFlowersLoading = ref(false)
const myFlowers = ref([])
const wateringId = ref(null)
const wateringAnimIds = ref(new Set())
// 单击显示的花语浮层：记录当前显示的花ID和位置
const tooltipFlower = ref(null)
const tooltipPos = ref({ x: 0, y: 0 })

function pickList(data) {
  if (!data) return []
  if (Array.isArray(data)) return data
  return data.records || data.list || data.items || []
}

async function fetchMyFlowers() {
  myFlowersLoading.value = true
  try {
    const res = await getMyFlowers()
    const list = pickList(res.data)
    myFlowers.value = list
    list.forEach((f) => {
      const id = flowerIdOf(f)
      if (id == null) return
      if (
        getCurrentStageThreshold(f) == null ||
        getFlowerType(f) == null
      ) {
        getFlower(id)
          .then((res) => {
            const detail = res.data
            if (detail) {
              const idx = myFlowers.value.findIndex(
                (m) => flowerIdOf(m) === id
              )
              if (idx >= 0) {
                myFlowers.value[idx] = { ...myFlowers.value[idx], ...detail }
              }
            }
          })
          .catch(() => {})
      }
    })
  } catch (e) {
    myFlowers.value = []
  } finally {
    myFlowersLoading.value = false
  }
}

async function refreshUserInfo() {
  try {
    await userStore.fetchUserInfo()
  } catch (e) {
  }
}

async function handleWater(f) {
  const id = flowerIdOf(f)
  if (id == null) return
  if (!canWater(f)) {
    ElMessage.warning(waterDisabledReason(f))
    return
  }
  wateringId.value = id
  // 播放浇水动画
  wateringAnimIds.value = new Set([...wateringAnimIds.value, id])
  setTimeout(() => {
    const next = new Set(wateringAnimIds.value)
    next.delete(id)
    wateringAnimIds.value = next
  }, 800)
  try {
    const res = await waterFlower(id)
    ElMessage.success('浇水成功 💧')
    const detail = res.data
    const idx = myFlowers.value.findIndex((m) => flowerIdOf(m) === id)
    if (idx >= 0) {
      const old = myFlowers.value[idx]
      const oldStage = old?.stage
      const updated = detail ? { ...old, ...detail } : { ...old }
      myFlowers.value[idx] = updated
      if (detail?.stage && detail.stage !== oldStage) {
        ElMessage.success(`成长到新阶段：${stageLabel(detail.stage)} 🌿`)
      }
    }
    refreshUserInfo()
  } catch (e) {
  } finally {
    wateringId.value = null
  }
}

// 单击花朵：显示花语浮层
function handleFlowerClick(e, f) {
  // 如果正在浇水动画中，不响应单击
  if (wateringAnimIds.value.has(flowerIdOf(f))) return
  
  const id = flowerIdOf(f)
  // 点击同一朵花则关闭
  if (tooltipFlower.value && flowerIdOf(tooltipFlower.value) === id) {
    tooltipFlower.value = null
    return
  }
  
  tooltipFlower.value = f
  // 获取点击位置，浮层显示在花朵上方
  const rect = e.currentTarget.getBoundingClientRect()
  tooltipPos.value = {
    x: rect.left + rect.width / 2,
    y: rect.top - 10
  }
}

// 浮层浇水按钮点击
function onTooltipWater() {
  if (!tooltipFlower.value) return
  handleWater(tooltipFlower.value)
}

// 点击花圃空白处关闭浮层
function handleFieldClick() {
  tooltipFlower.value = null
}

async function handleWaterAll() {
  const needWater = myFlowers.value.filter(f => canWater(f))
  if (needWater.length === 0) {
    ElMessage.info('没有需要浇水的花朵')
    return
  }
  let remaining = fireflyBalance.value
  let watered = 0
  for (const f of needWater) {
    if (remaining < WATER_COST) break
    remaining -= WATER_COST
    await handleWater(f)
    watered++
  }
  if (watered > 0) {
    ElMessage.success(`已为 ${watered} 朵花浇水，消耗 ${watered * WATER_COST} 萤火`)
  }
}

function seededRandom(seed) {
  let x = Math.sin(Number(seed) || 0) * 10000
  return x - Math.floor(x)
}

function getFlowerStyle(f, index) {
  const id = flowerIdOf(f) ?? index
  const rx = seededRandom(id)
  const ry = seededRandom(id * 1.7 + 3)
  const x = 8 + rx * 84
  const y = 10 + ry * 80
  return {
    left: `${x}%`,
    top: `${y}%`,
    animationDelay: `${index * 0.08}s`
  }
}

onMounted(() => {
  if (!userStore.userInfo) {
    refreshUserInfo()
  }
  fetchFlowerTypes()
  fetchMyFlowers()
})
</script>

<template>
  <div class="garden-page">
    <!-- 萤火虫粒子层 -->
    <div class="firefly-layer">
      <FireflyCanvas :fireflyCount="fireflyCount" :brightnessLevel="brightnessLevel" />
    </div>

    <!-- 顶部标题栏 -->
    <div class="top-bar">
      <button class="bar-btn" @click="router.push('/garden/detail')">
        <span>📋</span><span class="bar-btn-text">详情</span>
      </button>
      <div class="top-title">🌷 萤火花园</div>
      <button
        class="bar-btn"
        @click="handleWaterAll"
        :disabled="myFlowersLoading || !myFlowers.filter(f => canWater(f)).length"
      >
        <span>💧</span><span class="bar-btn-text">一键浇水</span>
      </button>
    </div>

    <!-- 花圃区域 -->
    <div
      class="garden-field"
      :style="{ filter: `brightness(${gardenBrightness})` }"
      @click="handleFieldClick"
    >
      <div class="field-bg"></div>

      <div v-if="myFlowersLoading" class="field-loading">加载中…</div>

      <div v-else-if="myFlowers.length === 0" class="field-empty">
        <div class="empty-icon">🌱</div>
        <div class="empty-text">花圃还是空的</div>
        <div class="empty-hint">点击底部「花种商店」兑换一颗种子吧</div>
      </div>

      <!-- 花朵 -->
      <div
        v-for="(f, index) in myFlowers"
        :key="flowerIdOf(f)"
        class="flower-spot"
        :style="{ ...getFlowerStyle(f, index), ...flowerColorStyle(f) }"
        :class="{ 'is-watering': wateringAnimIds.has(flowerIdOf(f)) }"
        @click.stop="(e) => handleFlowerClick(e, f)"
      >
        <div class="flower-wrapper">
          <!-- seed 种子 -->
          <div v-if="f?.stage === 'seed'" class="flower-seed"></div>
          
          <!-- sprout 幼苗 -->
          <div v-else-if="f?.stage === 'sprout'" class="flower-sprout">
            <div class="sprout-leaf" v-for="i in 6" :key="i" :style="{ transform: `rotate(${i * 60}deg)` }"></div>
          </div>
          
          <!-- seedling 中苗 -->
          <div v-else-if="f?.stage === 'seedling'" class="flower-seedling">
            <div class="seedling-leaf" v-for="i in 8" :key="i" :style="{ transform: `rotate(${i * 45}deg) translateY(-12px)` }"></div>
            <div class="seedling-center"></div>
          </div>
          
          <!-- bud 花苞 -->
          <div v-else-if="f?.stage === 'bud'" class="flower-bud">
            <div class="bud-leaf" v-for="i in 6" :key="i" :style="{ transform: `rotate(${i * 60}deg) translateY(-16px)` }"></div>
            <div class="bud-body"></div>
          </div>
          
          <!-- bloom 开放：完整俯视玫瑰植株 -->
          <div v-else class="flower-bloom">
            <!-- 外层叶片环（6片深绿椭圆叶片） -->
            <div class="leaf-ring">
              <div class="outer-leaf" v-for="i in 6" :key="'leaf-' + i" :style="{ transform: `rotate(${i * 60}deg)` }"></div>
            </div>
            <!-- 花萼层（较短叶片，深绿偏褐） -->
            <div class="sepal-ring">
              <div class="sepal" v-for="i in 5" :key="'sepal-' + i" :style="{ transform: `rotate(${i * 72}deg)` }"></div>
            </div>
            <!-- 花头：多层立体花瓣 -->
            <div class="flower-head">
              <!-- 外层花瓣（宽大外翻，深红） -->
              <div class="petal outer" v-for="i in 8" :key="'outer-' + i" :style="{ transform: `rotate(${i * 45}deg) translateY(-14px)` }"></div>
              <!-- 中层花瓣（收拢，红色） -->
              <div class="petal middle" v-for="i in 6" :key="'middle-' + i" :style="{ transform: `rotate(${i * 60 + 30}deg) translateY(-9px)` }"></div>
              <!-- 内层花瓣（包裹花心，粉红） -->
              <div class="petal inner" v-for="i in 5" :key="'inner-' + i" :style="{ transform: `rotate(${i * 72 + 15}deg) translateY(-5px)` }"></div>
              <!-- 花心 -->
              <div class="flower-center"></div>
            </div>
          </div>
        </div>

        <!-- 浇水动画 -->
        <div v-if="wateringAnimIds.has(flowerIdOf(f))" class="water-drop-anim"></div>
        <div v-if="wateringAnimIds.has(flowerIdOf(f))" class="water-splash"></div>
      </div>
    </div>

    <!-- 花语浮层 -->
    <div
      v-if="tooltipFlower"
      class="flower-tooltip"
      :style="{ left: `${tooltipPos.x}px`, top: `${tooltipPos.y}px` }"
    >
      <div class="tooltip-arrow"></div>
      <div class="tooltip-name">{{ getFlowerType(tooltipFlower)?.name ?? '未知花种' }}</div>
      <div class="tooltip-desc">{{ getFlowerType(tooltipFlower)?.description ?? '暂无描述' }}</div>
      <div class="tooltip-water">
        <el-button
          v-if="isBloomed(tooltipFlower)"
          type="success"
          plain
          disabled
          size="small"
        >
          已开花 🌸
        </el-button>
        <el-tooltip
          v-else-if="!canWater(tooltipFlower)"
          :content="waterDisabledReason(tooltipFlower)"
          placement="top"
        >
          <span>
            <el-button disabled size="small">💧 浇水（{{ WATER_COST }} 萤火）</el-button>
          </span>
        </el-tooltip>
        <el-button
          v-else
          type="primary"
          size="small"
          :loading="wateringId === flowerIdOf(tooltipFlower)"
          @click="onTooltipWater"
        >
          💧 浇水（{{ WATER_COST }} 萤火）
        </el-button>
      </div>
    </div>

    <!-- 底部信息栏 -->
    <div class="bottom-bar">
      <div class="bottom-item">
        <span class="bottom-label">累计萤火</span>
        <span class="bottom-value">{{ totalFirefly }}</span>
      </div>
      <div class="bottom-divider"></div>
      <div class="bottom-item">
        <span class="bottom-label">萤火余额</span>
        <span class="bottom-value">{{ fireflyBalance }}</span>
      </div>
      <div class="bottom-divider"></div>
      <div class="bottom-item">
        <span class="bottom-label">花朵数量</span>
        <span class="bottom-value">{{ myFlowers.length }}</span>
      </div>
      <div class="bottom-divider"></div>
      <button class="bottom-item shop-entry" @click="router.push('/garden/detail')">
        <span class="bottom-label">花种商店</span>
        <span class="bottom-value">🛒 进入</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.garden-page {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #0a0a12 0%, #0f0f1a 100%);
  overflow: hidden;
}

.firefly-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 50;
}

.top-bar {
  position: relative;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(8px);
}
.top-title {
  font-size: 18px;
  font-weight: bold;
  color: #ffd970;
  text-shadow: 0 2px 6px rgba(0, 0, 0, 0.6);
}
.bar-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.18);
  color: #e8e8e8;
  cursor: pointer;
  transition: all 0.25s ease;
  font-size: 14px;
}
.bar-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.22);
  transform: scale(1.04);
}
.bar-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.bar-btn-text {
  font-size: 13px;
}

.garden-field {
  position: relative;
  flex: 1;
  margin: 12px;
  border-radius: 16px;
  overflow: hidden;
  z-index: 10;
  transition: filter 0.6s ease;
}

.field-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 15% 25%, rgba(120, 110, 100, 0.25) 3px, transparent 4px),
    radial-gradient(circle at 75% 40%, rgba(120, 110, 100, 0.2) 2px, transparent 3px),
    radial-gradient(circle at 40% 70%, rgba(120, 110, 100, 0.22) 3px, transparent 4px),
    radial-gradient(circle at 85% 85%, rgba(120, 110, 100, 0.2) 2px, transparent 3px),
    radial-gradient(circle at 25% 90%, rgba(120, 110, 100, 0.22) 3px, transparent 4px),
    radial-gradient(circle at 60% 15%, rgba(120, 110, 100, 0.2) 2px, transparent 3px),
    repeating-linear-gradient(
      45deg,
      transparent 0,
      transparent 18px,
      rgba(45, 80, 50, 0.15) 18px,
      rgba(45, 80, 50, 0.15) 20px
    ),
    linear-gradient(135deg, #2a1f15 0%, #3d2a1a 50%, #2a1f15 100%);
  background-size: 80px 80px, 100px 100px, 60px 60px, 90px 90px, 70px 70px, 85px 85px, auto, auto;
}

.field-loading,
.field-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  color: #ffd970;
  z-index: 5;
}
.empty-icon {
  font-size: 56px;
  margin-bottom: 16px;
}
.empty-text {
  font-size: 18px;
  margin-bottom: 8px;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.6);
}
.empty-hint {
  font-size: 13px;
  opacity: 0.75;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.6);
}

/* 花朵花位 */
.flower-spot {
  position: absolute;
  transform: translate(-50%, -50%);
  cursor: pointer;
  z-index: 3;
  animation: spotFadeIn 0.5s ease forwards;
  opacity: 0;
}
@keyframes spotFadeIn {
  from { opacity: 0; transform: translate(-50%, -40%) scale(0.6); }
  to { opacity: 1; transform: translate(-50%, -50%) scale(1); }
}
.flower-spot:hover {
  z-index: 4;
}
.flower-spot:hover .flower-wrapper {
  transform: scale(1.15);
}

/* 浇水时花朵弹跳 */
.flower-spot.is-watering .flower-wrapper {
  animation: flowerBounce 0.8s ease;
}
@keyframes flowerBounce {
  0%, 100% { transform: scale(1) translateY(0); }
  20% { transform: scale(1.2) translateY(-6px); }
  40% { transform: scale(0.95) translateY(2px); }
  60% { transform: scale(1.08) translateY(-3px); }
  80% { transform: scale(0.98) translateY(1px); }
}

.flower-wrapper {
  position: relative;
  width: 52px;
  height: 52px;
  transition: transform 0.3s ease;
}

/* ============ 各阶段立体花朵 ============ */

/* seed 种子：泥土中褐色圆点 */
.flower-seed {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 10px;
  height: 8px;
  margin: -4px 0 0 -5px;
  background: radial-gradient(ellipse at 30% 40%, #6b4423 0%, #5d3a1a 50%, #3d2812 100%);
  border-radius: 50%;
  box-shadow:
    inset -1px -1px 2px rgba(0,0,0,0.6),
    inset 1px 1px 2px rgba(100,70,40,0.4),
    0 2px 4px rgba(0,0,0,0.5);
}

/* sprout 幼苗：一圈嫩绿小芽 */
.flower-sprout {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
}
.sprout-leaf {
  position: absolute;
  width: 6px;
  height: 14px;
  margin-left: -3px;
  background: linear-gradient(180deg, #8bc34a 0%, #689f38 100%);
  border-radius: 50% 50% 50% 50% / 70% 70% 30% 30%;
  box-shadow:
    inset 1px 0 2px rgba(255,255,255,0.3),
    -1px 2px 3px rgba(0,0,0,0.3);
  animation: sproutGrow 0.5s ease-out forwards;
}
@keyframes sproutGrow {
  from { transform: rotate(0deg) scaleY(0); }
  to { transform: rotate(var(--rotate, 0deg)) scaleY(1); }
}

/* seedling 中苗：绿色叶簇莲座 */
.flower-seedling {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
}
.seedling-leaf {
  position: absolute;
  width: 10px;
  height: 18px;
  margin-left: -5px;
  background: linear-gradient(180deg, #66bb6a 0%, #43a047 50%, #2e7d32 100%);
  border-radius: 50% 50% 45% 45% / 75% 75% 25% 25%;
  box-shadow:
    inset 2px 0 4px rgba(255,255,255,0.25),
    inset -1px 0 2px rgba(0,0,0,0.2),
    -1px 3px 5px rgba(0,0,0,0.3);
}
.seedling-center {
  position: absolute;
  width: 14px;
  height: 14px;
  margin: -7px 0 0 -7px;
  background: radial-gradient(circle at 30% 30%, #81c784 0%, #4caf50 50%, #388e3c 100%);
  border-radius: 50%;
  box-shadow:
    inset 2px 2px 4px rgba(255,255,255,0.3),
    inset -1px -1px 2px rgba(0,0,0,0.2),
    0 2px 4px rgba(0,0,0,0.4);
}

/* bud 花苞：叶簇中央显现花苞 */
.flower-bud {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
}
.bud-leaf {
  position: absolute;
  width: 10px;
  height: 20px;
  margin-left: -5px;
  background: linear-gradient(180deg, #66bb6a 0%, #43a047 100%);
  border-radius: 50% 50% 45% 45% / 75% 75% 25% 25%;
  box-shadow:
    inset 2px 0 3px rgba(255,255,255,0.2),
    -1px 3px 4px rgba(0,0,0,0.3);
}
.bud-body {
  position: absolute;
  width: 16px;
  height: 24px;
  margin: -14px 0 0 -8px;
  background: linear-gradient(180deg,
    var(--flower-center) 0%,
    var(--flower-secondary) 30%,
    var(--flower-primary) 70%,
    var(--flower-shadow) 100%);
  border-radius: 40% 40% 50% 50% / 60% 60% 40% 40%;
  box-shadow:
    inset 3px 2px 6px rgba(255,255,255,0.4),
    inset -2px 0 4px rgba(0,0,0,0.15),
    -3px 4px 8px rgba(0,0,0,0.4);
}

/* bloom 开放：完整俯视玫瑰植株 */
.flower-bloom {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 55px;
  height: 55px;
  margin: -27.5px;
  filter: drop-shadow(2px 4px 6px rgba(0,0,0,0.4));
  animation: bloomGlow 3s ease-in-out infinite;
}
@keyframes bloomGlow {
  0%, 100% { filter: drop-shadow(2px 4px 6px rgba(0,0,0,0.4)); }
  50% { filter: drop-shadow(2px 4px 8px rgba(0,0,0,0.5)) brightness(1.05); }
}

/* ============ 外层叶片环 ============ */
.leaf-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
}
.outer-leaf {
  position: absolute;
  width: 12px;
  height: 22px;
  margin-left: -6px;
  background: radial-gradient(ellipse at 30% 40%,
    #4caf50 0%,
    #388e3c 40%,
    #2e7d32 100%);
  border-radius: 60% 40% 55% 45% / 75% 70% 30% 25%;
  box-shadow:
    inset 2px 0 4px rgba(255,255,255,0.25),
    inset -1px 0 3px rgba(0,0,0,0.2),
    -1px 3px 5px rgba(0,0,0,0.35);
  transform-origin: center bottom;
}
.outer-leaf:nth-child(1) { transform: rotate(0deg) translateY(-26px); }
.outer-leaf:nth-child(2) { transform: rotate(60deg) translateY(-26px); }
.outer-leaf:nth-child(3) { transform: rotate(120deg) translateY(-26px); }
.outer-leaf:nth-child(4) { transform: rotate(180deg) translateY(-26px); }
.outer-leaf:nth-child(5) { transform: rotate(240deg) translateY(-26px); }
.outer-leaf:nth-child(6) { transform: rotate(300deg) translateY(-26px); }

/* ============ 花萼层 ============ */
.sepal-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
}
.sepal {
  position: absolute;
  width: 8px;
  height: 14px;
  margin-left: -4px;
  background: radial-gradient(ellipse at 35% 40%,
    #5d4e37 0%,
    #4a3728 50%,
    #3d2f24 100%);
  border-radius: 55% 45% 60% 40% / 70% 65% 35% 30%;
  box-shadow:
    inset 1px 0 2px rgba(255,255,255,0.15),
    -1px 2px 3px rgba(0,0,0,0.3);
  transform-origin: center bottom;
}
.sepal:nth-child(1) { transform: rotate(0deg) translateY(-18px); }
.sepal:nth-child(2) { transform: rotate(72deg) translateY(-18px); }
.sepal:nth-child(3) { transform: rotate(144deg) translateY(-18px); }
.sepal:nth-child(4) { transform: rotate(216deg) translateY(-18px); }
.sepal:nth-child(5) { transform: rotate(288deg) translateY(-18px); }

/* ============ 花头：多层立体花瓣 ============ */
.flower-head {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 28px;
  height: 28px;
  margin: -14px;
}

/* 花瓣基础样式：水滴/扇形 */
.petal {
  position: absolute;
  top: 50%;
  left: 50%;
  transform-origin: center bottom;
}

/* 外层花瓣：宽大外翻，深红 */
.petal.outer {
  width: 10px;
  height: 16px;
  margin-left: -5px;
  margin-top: -16px;
  background: radial-gradient(ellipse at 30% 35%,
    #dc5a4c 0%,
    #c0392b 40%,
    #a93226 100%);
  border-radius: 65% 35% 70% 30% / 80% 70% 30% 20%;
  box-shadow:
    inset 2px 1px 4px rgba(255,255,255,0.3),
    inset -1px 0 3px rgba(0,0,0,0.25),
    -2px 2px 5px rgba(0,0,0,0.3);
}
.petal.outer:nth-child(1) { transform: rotate(0deg); }
.petal.outer:nth-child(2) { transform: rotate(45deg); }
.petal.outer:nth-child(3) { transform: rotate(90deg); }
.petal.outer:nth-child(4) { transform: rotate(135deg); }
.petal.outer:nth-child(5) { transform: rotate(180deg); }
.petal.outer:nth-child(6) { transform: rotate(225deg); }
.petal.outer:nth-child(7) { transform: rotate(270deg); }
.petal.outer:nth-child(8) { transform: rotate(315deg); }

/* 中层花瓣：收拢，红色 */
.petal.middle {
  width: 8px;
  height: 13px;
  margin-left: -4px;
  margin-top: -13px;
  background: radial-gradient(ellipse at 30% 35%,
    #f16051 0%,
    #e74c3c 40%,
    #c0392b 100%);
  border-radius: 60% 40% 65% 35% / 75% 70% 30% 25%;
  box-shadow:
    inset 2px 1px 3px rgba(255,255,255,0.25),
    inset -1px 0 2px rgba(0,0,0,0.2),
    -1px 2px 4px rgba(0,0,0,0.25);
}
.petal.middle:nth-child(9) { transform: rotate(30deg); }
.petal.middle:nth-child(10) { transform: rotate(90deg); }
.petal.middle:nth-child(11) { transform: rotate(150deg); }
.petal.middle:nth-child(12) { transform: rotate(210deg); }
.petal.middle:nth-child(13) { transform: rotate(270deg); }
.petal.middle:nth-child(14) { transform: rotate(330deg); }

/* 内层花瓣：包裹花心，粉红 */
.petal.inner {
  width: 6px;
  height: 10px;
  margin-left: -3px;
  margin-top: -10px;
  background: radial-gradient(ellipse at 30% 35%,
    #ffcccc 0%,
    #ff6b6b 40%,
    #e74c3c 100%);
  border-radius: 55% 45% 60% 40% / 70% 65% 35% 30%;
  box-shadow:
    inset 1px 0 3px rgba(255,255,255,0.3),
    inset -1px 0 2px rgba(0,0,0,0.15),
    -1px 1px 3px rgba(0,0,0,0.2);
}
.petal.inner:nth-child(15) { transform: rotate(15deg); }
.petal.inner:nth-child(16) { transform: rotate(87deg); }
.petal.inner:nth-child(17) { transform: rotate(159deg); }
.petal.inner:nth-child(18) { transform: rotate(231deg); }
.petal.inner:nth-child(19) { transform: rotate(303deg); }

/* 花心 */
.flower-center {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 6px;
  height: 6px;
  margin: -3px;
  background: radial-gradient(circle at 30% 30%,
    #fff8e1 0%,
    #ffecb3 40%,
    #ffe082 70%,
    #ffd54f 100%);
  border-radius: 50%;
  box-shadow:
    0 0 3px rgba(255, 215, 0, 0.5),
    inset 0 0 2px rgba(255,255,255,0.4);
}

/* 浇水动画 */
.water-drop-anim {
  position: absolute;
  top: -40px;
  left: 50%;
  transform: translateX(-50%);
  width: 10px;
  height: 14px;
  background: linear-gradient(180deg, #81d4fa, #0288d1);
  border-radius: 50% 50% 50% 50% / 60% 60% 40% 40%;
  animation: waterFall 0.8s ease-in forwards;
  z-index: 6;
  pointer-events: none;
}
@keyframes waterFall {
  0% { top: -40px; opacity: 0; transform: translateX(-50%) scale(0.8); }
  20% { opacity: 1; }
  60% { top: 10px; opacity: 1; transform: translateX(-50%) scale(1); }
  80% { top: 14px; transform: translateX(-50%) scale(1.6, 0.4); opacity: 0.5; }
  100% { top: 16px; transform: translateX(-50%) scale(2, 0.2); opacity: 0; }
}

.water-splash {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 40px;
  height: 40px;
  margin: -20px;
  border-radius: 50%;
  border: 2px solid rgba(33, 150, 243, 0.6);
  animation: waterSplash 0.6s ease-out forwards;
  z-index: 6;
  pointer-events: none;
}
@keyframes waterSplash {
  0% { transform: scale(0.3); opacity: 1; }
  100% { transform: scale(1.5); opacity: 0; }
}

/* 花语浮层 */
.flower-tooltip {
  position: fixed;
  transform: translate(-50%, -100%);
  padding: 12px 18px;
  background: rgba(30, 30, 40, 0.92);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 217, 112, 0.3);
  border-radius: 12px;
  z-index: 100;
  max-width: 280px;
  animation: tooltipFadeIn 0.25s ease;
}
@keyframes tooltipFadeIn {
  from { opacity: 0; transform: translate(-50%, -90%) scale(0.95); }
  to { opacity: 1; transform: translate(-50%, -100%) scale(1); }
}
.tooltip-arrow {
  position: absolute;
  bottom: -6px;
  left: 50%;
  transform: translateX(-50%);
  width: 0;
  height: 0;
  border-left: 8px solid transparent;
  border-right: 8px solid transparent;
  border-top: 8px solid rgba(255, 217, 112, 0.3);
}
.tooltip-arrow::before {
  content: '';
  position: absolute;
  bottom: 2px;
  left: -7px;
  border-left: 7px solid transparent;
  border-right: 7px solid transparent;
  border-top: 7px solid rgba(30, 30, 40, 0.92);
}
.tooltip-name {
  font-size: 16px;
  font-weight: bold;
  color: #ffd970;
  margin-bottom: 6px;
}
.tooltip-desc {
  font-size: 13px;
  color: #e0e0e0;
  line-height: 1.5;
  margin-bottom: 12px;
}
.tooltip-water {
  display: flex;
  justify-content: center;
}

/* 底部信息栏 */
.bottom-bar {
  position: relative;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 14px 20px;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(8px);
}
.bottom-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  background: transparent;
  border: none;
  cursor: pointer;
  color: inherit;
  font-family: inherit;
}
.bottom-item.shop-entry:hover .bottom-value {
  color: #ffd970;
}
.bottom-label {
  font-size: 11px;
  color: #a0a0a0;
}
.bottom-value {
  font-size: 15px;
  font-weight: 600;
  color: #ffd970;
  transition: color 0.2s ease;
}
.bottom-divider {
  width: 1px;
  height: 28px;
  background: rgba(255, 255, 255, 0.15);
}
</style>
