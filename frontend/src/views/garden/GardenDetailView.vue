<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getFlowerTypes,
  redeemFlower,
  getMyFlowers,
  waterFlower,
  getFlower
} from '@/api/garden'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const user = computed(() => userStore.userInfo || {})
const isLoggedIn = computed(() => userStore.isLoggedIn)

const totalFirefly = computed(() => user.value.totalFirefly ?? 0)
const fireflyBalance = computed(() => user.value.fireflyBalance ?? 0)

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
const brightnessLabel = computed(() => {
  const labels = ['全黑', '微光', '暗淡', '明亮', '萤光环绕', '满园星光']
  return labels[brightnessLevel.value] || '全黑'
})

const STAGE_LABELS = {
  seed: '种子',
  sprout: '幼苗',
  seedling: '中苗',
  bud: '花苞',
  bloom: '开放'
}
const STAGE_ORDER = ['seed', 'sprout', 'seedling', 'bud', 'bloom']
const STAGE_ICONS = ['iconSeed', 'iconSprout', 'iconSeedling', 'iconBud', 'iconBloom']
const STAGE_ICONS_SNAKE = [
  'icon_seed',
  'icon_sprout',
  'icon_seedling',
  'icon_bud',
  'icon_bloom'
]

function stageLabel(s) {
  return STAGE_LABELS[s] || s || '-'
}
function stageIndex(s) {
  const i = STAGE_ORDER.indexOf(s)
  return i >= 0 ? i : 0
}

function getStageIcon(ft, idx) {
  if (!ft) return ''
  return ft[STAGE_ICONS[idx]] || ft[STAGE_ICONS_SNAKE[idx]] || ''
}

function flowerTypeIdOf(ft) {
  return ft?.id ?? ft?.flowerTypeId ?? ft?.flower_type_id
}
function flowerIdOf(f) {
  return f?.id ?? f?.flowerId ?? f?.flower_id
}

function getFlowerType(f) {
  return f?.flowerType ?? f?.flower_type ?? null
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
// 浇水条件：未开花且萤火余额足够（不限制每日次数）
function canWater(f) {
  return !isBloomed(f) && fireflyBalance.value >= WATER_COST
}
// 萤火余额不足提示
function waterDisabledReason(f) {
  if (isBloomed(f)) return '已开花'
  if (fireflyBalance.value < WATER_COST) return `萤火余额不足（需 ${WATER_COST} 点）`
  return ''
}

const shopLoading = ref(false)
const flowerTypes = ref([])
const myFlowersLoading = ref(false)
const myFlowers = ref([])
const redeeming = ref(false)
const wateringId = ref(null)

function pickList(data) {
  if (!data) return []
  if (Array.isArray(data)) return data
  return data.records || data.list || data.items || []
}

async function fetchFlowerTypes() {
  shopLoading.value = true
  try {
    const res = await getFlowerTypes()
    flowerTypes.value = pickList(res.data)
  } catch (e) {
    flowerTypes.value = []
  } finally {
    shopLoading.value = false
  }
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

function isLockedByTotal(ft) {
  const req = ft?.requiredFirefly ?? ft?.required_firefly ?? 0
  return totalFirefly.value < req
}
function isLockedByBalance(ft) {
  const need = ft?.redeemFirefly ?? ft?.redeem_firefly ?? 0
  return fireflyBalance.value < need
}
function redeemDisabled(ft) {
  return isLockedByTotal(ft) || isLockedByBalance(ft)
}
function redeemDisabledReason(ft) {
  if (isLockedByTotal(ft)) return '累计萤火值不足'
  if (isLockedByBalance(ft)) return '萤火余额不足'
  return ''
}

async function handleRedeem(ft) {
  if (!isLoggedIn.value) { ElMessage.warning('请先登录'); return }
  if (redeemDisabled(ft)) return
  try {
    await redeemFlower({ flowerTypeId: flowerTypeIdOf(ft) })
    ElMessage.success('兑换成功，已种下一颗种子 🌱')
    redeeming.value = true
    await Promise.all([fetchMyFlowers(), refreshUserInfo()])
  } catch (e) {
  } finally {
    redeeming.value = false
  }
}

async function handleWater(f) {
  if (!isLoggedIn.value) { ElMessage.warning('请先登录'); return }
  const id = flowerIdOf(f)
  if (id == null) return
  if (!canWater(f)) return
  wateringId.value = id
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

function onImgError(e) {
  e.target.style.display = 'none'
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
  <div class="garden-detail-page">
    <div class="garden-banner">
      <div class="banner-title">🌷 萤火花园</div>
      <div class="banner-level">亮度等级 {{ brightnessLevel }} · {{ brightnessLabel }}</div>
      <div class="banner-stats">
        <span>累计萤火：{{ totalFirefly }}</span>
        <el-divider direction="vertical" />
        <span>萤火余额：{{ fireflyBalance }}</span>
      </div>
      <div class="banner-desc">种下花朵，让花园更明亮</div>
    </div>

    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-header">
          <span class="section-title">🌱 我的花朵</span>
          <el-button link @click="fetchMyFlowers">刷新</el-button>
        </div>
      </template>
      <div v-loading="myFlowersLoading">
        <el-empty
          v-if="!myFlowersLoading && myFlowers.length === 0"
          description="还没有花朵，去花种商店兑换一颗吧"
        />
        <div v-else class="flower-grid">
          <el-card
            v-for="f in myFlowers"
            :key="flowerIdOf(f)"
            shadow="hover"
            class="flower-card"
          >
            <div class="flower-head">
              <span class="flower-name">
                {{ getFlowerType(f)?.name ?? f?.flowerTypeName ?? f?.flower_type_name ?? '未知花种' }}
              </span>
              <el-tag
                size="small"
                :type="isBloomed(f) ? 'success' : 'warning'"
              >
                {{ stageLabel(f?.stage) }}
              </el-tag>
            </div>

            <div class="stage-icons">
              <div
                v-for="(st, i) in STAGE_ORDER"
                :key="st"
                class="stage-icon"
                :class="{ active: stageIndex(f?.stage) === i }"
              >
                <img
                  v-if="getStageIcon(getFlowerType(f), i)"
                  :src="getStageIcon(getFlowerType(f), i)"
                  :alt="stageLabel(st)"
                  class="stage-img"
                  @error="onImgError"
                />
                <span v-else class="stage-placeholder">{{ stageLabel(st).charAt(0) }}</span>
                <div class="stage-text">{{ stageLabel(st) }}</div>
              </div>
            </div>

            <div class="progress-wrap">
              <div class="progress-label">
                当前阶段进度：{{ getStageWaterCount(f) }}/{{ getCurrentStageThreshold(f) || '-' }}
              </div>
              <el-progress
                :percentage="getProgress(f)"
                :color="isBloomed(f) ? '#67c23a' : '#f5a623'"
                :stroke-width="10"
              />
            </div>

            <div class="flower-meta">
              <span>🌱 种植：{{ f?.plantedAt ?? f?.planted_at ?? '-' }}</span>
              <span>💧 上次浇水：{{ f?.lastWaterAt ?? f?.last_water_at ?? '从未' }}</span>
              <span v-if="isBloomed(f)">🌸 开花：{{ f?.bloomedAt ?? f?.bloomed_at ?? '-' }}</span>
            </div>

            <div class="flower-actions">
              <el-button
                v-if="isBloomed(f)"
                type="success"
                plain
                disabled
              >
                已开花 🌸
              </el-button>
              <el-tooltip
                v-if="!isLoggedIn"
                content="请登录后浇水"
                placement="top"
              >
                <span>
                  <el-button disabled>💧 浇水（{{ WATER_COST }} 萤火）</el-button>
                </span>
              </el-tooltip>
              <el-tooltip
                v-else-if="!canWater(f)"
                :content="waterDisabledReason(f)"
                placement="top"
              >
                <span>
                  <el-button disabled>💧 浇水（{{ WATER_COST }} 萤火）</el-button>
                </span>
              </el-tooltip>
              <el-button
                v-else
                type="primary"
                :loading="wateringId === flowerIdOf(f)"
                @click="handleWater(f)"
              >
                💧 浇水（{{ WATER_COST }} 萤火）
              </el-button>
            </div>
          </el-card>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-header">
          <span class="section-title">🛒 花种商店</span>
          <el-button link @click="fetchFlowerTypes">刷新</el-button>
        </div>
      </template>
      <div v-loading="shopLoading">
        <el-empty
          v-if="!shopLoading && flowerTypes.length === 0"
          description="暂无可兑换的花种"
        />
        <div v-else class="shop-grid">
          <el-card
            v-for="ft in flowerTypes"
            :key="flowerTypeIdOf(ft)"
            shadow="hover"
            class="shop-card"
          >
            <div class="shop-head">
              <span class="shop-name">{{ ft.name }}</span>
            </div>
            <div class="shop-desc">{{ ft.description }}</div>

            <div class="stage-icons preview">
              <div
                v-for="(st, i) in STAGE_ORDER"
                :key="st"
                class="stage-icon"
              >
                <img
                  v-if="getStageIcon(ft, i)"
                  :src="getStageIcon(ft, i)"
                  :alt="stageLabel(st)"
                  class="stage-img"
                  @error="onImgError"
                />
                <span v-else class="stage-placeholder">{{ stageLabel(st).charAt(0) }}</span>
              </div>
            </div>

            <div class="shop-cost">
              <div>
                <span class="cost-label">兑换：</span>
                <el-tag size="small" type="warning">
                  {{ ft.redeemFirefly ?? ft.redeem_firefly ?? 0 }} 萤火
                </el-tag>
              </div>
              <div>
                <span class="cost-label">解锁需累计：</span>
                <el-tag size="small" type="info">
                  {{ ft.requiredFirefly ?? ft.required_firefly ?? 0 }} 萤火
                </el-tag>
              </div>
            </div>

            <div class="shop-actions">
              <el-tooltip
                v-if="!isLoggedIn"
                content="请登录后兑换"
                placement="top"
              >
                <span>
                  <el-button disabled>兑换</el-button>
                </span>
              </el-tooltip>
              <el-tooltip
                v-else-if="redeemDisabled(ft)"
                :content="redeemDisabledReason(ft)"
                placement="top"
              >
                <span>
                  <el-button disabled>兑换</el-button>
                </span>
              </el-tooltip>
              <el-button
                v-else
                type="primary"
                :loading="redeeming"
                @click="handleRedeem(ft)"
              >
                兑换
              </el-button>
            </div>
          </el-card>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.garden-detail-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.garden-banner {
  border-radius: 12px;
  padding: 40px 24px;
  text-align: center;
  transition: background 0.4s ease;
  background: linear-gradient(135deg, #5a4a6e 0%, #3a4a7e 100%);
  color: #ffd970;
}
.banner-title {
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 8px;
}
.banner-level {
  font-size: 16px;
  margin-bottom: 8px;
}
.banner-stats {
  font-size: 14px;
  margin-bottom: 6px;
}
.banner-desc {
  font-size: 13px;
  opacity: 0.85;
}

.section-card {
  border-radius: 10px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.flower-grid,
.shop-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}
.flower-card,
.shop-card {
  border-radius: 10px;
}
.flower-head,
.shop-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.flower-name,
.shop-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.shop-desc {
  font-size: 13px;
  color: #909399;
  margin-bottom: 12px;
  min-height: 20px;
}

.stage-icons {
  display: flex;
  justify-content: space-between;
  gap: 4px;
  margin: 12px 0;
  padding: 8px;
  background: #fef7ea;
  border-radius: 8px;
}
.stage-icons.preview {
  padding: 6px;
}
.stage-icon {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  opacity: 0.4;
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.stage-icon.active {
  opacity: 1;
  transform: scale(1.1);
}
.stage-img {
  width: 36px;
  height: 36px;
  object-fit: contain;
}
.stage-placeholder {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #f5a623;
  border: 1px dashed #f5a623;
}
.stage-text {
  font-size: 11px;
  color: #909399;
}

.progress-wrap {
  margin: 12px 0;
}
.progress-label {
  font-size: 12px;
  color: #606266;
  margin-bottom: 6px;
}

.flower-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 12px;
}

.flower-actions,
.shop-actions {
  display: flex;
  justify-content: center;
  margin-top: 8px;
}

.shop-cost {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 8px 0;
  font-size: 13px;
}
.cost-label {
  color: #909399;
  margin-right: 6px;
}
</style>
