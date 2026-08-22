<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  throwBottle,
  pickBottle,
  getBottle,
  releaseBottle,
  replyBottle,
  getBottleReplies,
  thankBottle,
  thankBottleReply,
  sinkBottle,
  getMyBottles,
  getBottles
} from '@/api/driftBottle'
import { writeLetter } from '@/api/letter'
import { useUserStore } from '@/stores/user'
import ReportDialog from '@/components/ReportDialog.vue'

const userStore = useUserStore()
const route = useRoute()
const currentUserId = computed(() => userStore.userInfo?.id)
const isLoggedIn = computed(() => userStore.isLoggedIn)

// 举报弹窗
const reportDialog = ref(null)
const reportTargetType = ref('drift_bottle')
const reportTargetId = ref(null)

function openReportBottle() {
  if (!openedBottle.value) return
  if (!isLoggedIn.value) { ElMessage.warning('请先登录'); return }
  reportTargetType.value = 'drift_bottle'
  reportTargetId.value = bottleIdOf(openedBottle.value)
  reportDialog.value?.open()
}

function openReportReply(reply) {
  if (!reply) return
  if (!isLoggedIn.value) { ElMessage.warning('请先登录'); return }
  reportTargetType.value = 'bottle_reply'
  reportTargetId.value = reply.id ?? reply.replyId ?? reply.reply_id
  reportDialog.value?.open()
}

// 场景：main 主场景 / picked 捡到瓶子 / mine 我的瓶子
const scene = ref('main')

// 捡瓶模式：public 公海（默认，捞他人的瓶子）/ private 私海（仅捞自己的瓶子）
const pickMode = ref('public')
const isPrivate = computed(() => pickMode.value === 'private')
const pickModeLabel = computed(() => (isPrivate.value ? '私海' : '公海'))
const pickDesc = computed(() =>
  isPrivate.value
    ? '在自己的海域里打捞曾经的自己 · 只会捞到你的瓶子'
    : '把心事装入瓶中，让海浪带它去远方 · 点击被推上岸的瓶子打开'
)
const noBottleMsg = computed(() =>
  isPrivate.value ? '私海暂时没有你的瓶子了' : '大海暂时没有瓶子了'
)

// 被海浪推上沙滩的瓶子（点击可打开）
const washedBottles = ref([]) // { uid, left, rotate, landed, leaving, loading }
let spawnTimer = null
let bottleSeq = 0

function spawnBottle() {
  if (washedBottles.value.length >= 4) return
  const uid = ++bottleSeq
  const left = 10 + Math.random() * 80 // 10% ~ 90%
  const rotate = -25 + Math.random() * 50 // -25° ~ 25°
  const bobDur = 11 + Math.random() * 4 // 11s ~ 15s 下沉-上浮-消失周期
  const bobDelay = 0.9 + Math.random() * 0.5 // 等 bottleAppear 完成后开始
  washedBottles.value.push({
    uid,
    left,
    rotate,
    bobDur: +bobDur.toFixed(2),
    bobDelay: +bobDelay.toFixed(2),
    landed: false,
    leaving: false,
    loading: false
  })
  // 进入水面后可点击
  setTimeout(() => {
    const b = washedBottles.value.find((x) => x.uid === uid)
    if (b) b.landed = true
  }, 1800)
  // 下沉-上浮周期结束后被海浪带走
  setTimeout(() => {
    removeBottle(uid)
  }, (bobDur + bobDelay) * 1000 + 500)
}

function removeBottle(uid) {
  const b = washedBottles.value.find((x) => x.uid === uid)
  if (!b || b.leaving) return
  b.leaving = true
  setTimeout(() => {
    washedBottles.value = washedBottles.value.filter((x) => x.uid !== uid)
  }, 1400)
}

function scheduleSpawn() {
  const delay = 3000 + Math.random() * 3500 // 3s ~ 6.5s
  spawnTimer = setTimeout(() => {
    spawnBottle()
    scheduleSpawn()
  }, delay)
}

// 用户封禁标记（4015）
const isBanned = ref(false)

// 扔瓶子对话框
const throwVisible = ref(false)
const throwContent = ref('')
const throwLoading = ref(false)
// 扔出动画
const throwing = ref(false)

// 捡到的瓶子
const pickedBottle = ref(null) // { id, pickedAt }
const openedBottle = ref(null) // 完整内容
const opened = ref(false)
const pickLoading = ref(false)
const openLoading = ref(false)

// 回复对话框
const replyVisible = ref(false)
const replyContent = ref('')
const replyLoading = ref(false)

// 我的瓶子列表
const mineLoading = ref(false)
const mineList = ref([])
const mineTotal = ref(0)
const minePage = reactive({ current: 1, size: 10 })
// 检索条件
const mineKeyword = ref('')
const mineTimeRange = ref('') // '' 全部 / today / week / month / custom
const mineDateRange = ref([]) // [startDate, endDate]

function buildMineParams() {
  const params = { page: minePage.current, size: minePage.size }
  if (mineKeyword.value?.trim()) {
    params.keyword = mineKeyword.value.trim()
  }
  if (mineTimeRange.value) {
    params.timeRange = mineTimeRange.value
    if (mineTimeRange.value === 'custom' && Array.isArray(mineDateRange.value) && mineDateRange.value.length === 2) {
      params.startDate = formatYMD(mineDateRange.value[0])
      params.endDate = formatYMD(mineDateRange.value[1])
    }
  }
  return params
}
function formatYMD(d) {
  if (!d) return ''
  const dt = new Date(d)
  const y = dt.getFullYear()
  const m = String(dt.getMonth() + 1).padStart(2, '0')
  const dd = String(dt.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

function applyMineSearch() {
  minePage.current = 1
  fetchMine()
}
function resetMineSearch() {
  mineKeyword.value = ''
  mineTimeRange.value = ''
  mineDateRange.value = []
  minePage.current = 1
  fetchMine()
}

// 我的瓶子详情（含回复）
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailBottle = ref(null)
const detailReplies = ref([])

// 兼容分页结构
function pickList(data) {
  if (!data) return []
  if (Array.isArray(data)) return data
  return data.records || data.list || data.items || []
}
function pickTotal(data) {
  if (!data) return 0
  if (Array.isArray(data)) return data.length
  return Number(data.total ?? data.totalCount ?? 0)
}

function bottleIdOf(b) {
  return b?.id ?? b?.bottleId ?? b?.bottle_id
}

function statusLabel(s) {
  if (s === 'drifting') return '漂流中'
  if (s === 'sunk') return '已沉底'
  return s || '-'
}

function statusType(s) {
  if (s === 'drifting') return 'success'
  if (s === 'sunk') return 'info'
  return 'info'
}

// 当前用户是否已感谢该瓶子
function hasThankedBottle(b) {
  if (!b) return false
  if (b.hasThanked ?? b.has_thanked) return true
  const arr = b.thankedBy || b.thanked_by || []
  return Array.isArray(arr) && currentUserId.value != null && arr.includes(currentUserId.value)
}

// 当前用户是否已回复该瓶子
function hasRepliedBottle(b) {
  if (!b) return false
  if (b.hasReplied ?? b.has_replied) return true
  if (b.replied) return true
  return false
}

function hasThankedReply(r) {
  if (!r) return false
  if (r.hasThanked ?? r.has_thanked) return true
  const arr = r.thankedBy || r.thanked_by || []
  return Array.isArray(arr) && currentUserId.value != null && arr.includes(currentUserId.value)
}

// 处理 4015/4019 封禁/禁言
function handleBanned(e) {
  if (e?.code === 4015 || e?.code === 4019) {
    isBanned.value = true
    // 刷新用户信息同步封禁状态
    userStore.fetchUserInfo().catch(() => {})
  }
}

// === 主场景操作 ===

function openThrowDialog() {
  if (isBanned.value) {
    ElMessage.error('账号已被封禁，无法操作')
    return
  }
  throwContent.value = ''
  throwVisible.value = true
}

async function handleThrow() {
  if (!throwContent.value.trim()) {
    ElMessage.warning('请写点什么吧')
    return
  }
  throwLoading.value = true
  try {
    await throwBottle({ content: throwContent.value.trim() })
    throwVisible.value = false
    throwContent.value = ''
    // 播放扔出动画
    throwing.value = true
    setTimeout(() => {
      throwing.value = false
      ElMessage.success('漂流瓶已投入大海')
    }, 1800)
  } catch (e) {
    handleBanned(e)
  } finally {
    throwLoading.value = false
  }
}

async function handlePick() {
  if (isBanned.value) {
    ElMessage.error('账号已被封禁，无法操作')
    return
  }
  pickLoading.value = true
  try {
    let bottleId = null
    let pickedAt = '-'

    if (!isLoggedIn.value) {
      // 游客模式：从公海列表随机取一个瓶子查看
      const listRes = await getBottles({ page: 1, size: 50 })
      const list = pickList(listRes.data?.list || listRes.data?.records || listRes.data)
      if (!list || list.length === 0) {
        ElMessage.info(noBottleMsg.value)
        return
      }
      const random = list[Math.floor(Math.random() * list.length)]
      bottleId = random.id ?? random.bottleId ?? random.bottle_id
      pickedAt = random.createdAt || random.created_at || '-'
    } else {
      // 已登录：正常捡瓶
      const res = await pickBottle(pickMode.value)
      const data = res.data
      if (!data || !data.found || !data.bottle) {
        ElMessage.info(noBottleMsg.value)
        return
      }
      const bottle = data.bottle
      bottleId = bottleIdOf(bottle)
      pickedAt = bottle.createdAt || bottle.created_at || '-'
    }

    pickedBottle.value = { id: bottleId, pickedAt }
    openedBottle.value = null
    opened.value = false
    scene.value = 'picked'
    // 直接拉取内容并打开
    openLoading.value = true
    try {
      const res2 = await getBottle(pickedBottle.value.id)
      openedBottle.value = res2.data
      opened.value = true
    } catch (e) {
      handleBanned(e)
    } finally {
      openLoading.value = false
    }
  } catch (e) {
    handleBanned(e)
  } finally {
    pickLoading.value = false
  }
}

// 点击被推上岸的瓶子：捡起并直接打开
async function openWashedBottle(bottle) {
  if (isBanned.value) {
    ElMessage.error('账号已被封禁，无法操作')
    return
  }
  if (!bottle.landed || bottle.loading || bottle.leaving) return
  bottle.loading = true
  pickLoading.value = true
  try {
    let bottleId = null
    let pickedAt = '-'

    if (!isLoggedIn.value) {
      // 游客模式：从公海列表随机取一个瓶子查看
      const listRes = await getBottles({ page: 1, size: 50 })
      const list = pickList(listRes.data?.list || listRes.data?.records || listRes.data)
      if (!list || list.length === 0) {
        ElMessage.info(noBottleMsg.value)
        removeBottle(bottle.uid)
        return
      }
      const random = list[Math.floor(Math.random() * list.length)]
      bottleId = random.id ?? random.bottleId ?? random.bottle_id
      pickedAt = random.createdAt || random.created_at || '-'
    } else {
      const res = await pickBottle(pickMode.value)
      const data = res.data
      if (!data || !data.found || !data.bottle) {
        ElMessage.info(noBottleMsg.value)
        removeBottle(bottle.uid)
        return
      }
      const bk = data.bottle
      bottleId = bottleIdOf(bk)
      pickedAt = bk.createdAt || bk.created_at || '-'
    }

    pickedBottle.value = { id: bottleId, pickedAt }
    openedBottle.value = null
    opened.value = false
    scene.value = 'picked'
    openLoading.value = true
    try {
      const res2 = await getBottle(pickedBottle.value.id)
      openedBottle.value = res2.data
      opened.value = true
    } catch (e) {
      handleBanned(e)
    } finally {
      openLoading.value = false
    }
    removeBottle(bottle.uid)
  } catch (e) {
    handleBanned(e)
  } finally {
    pickLoading.value = false
    bottle.loading = false
  }
}

function goMine() {
  scene.value = 'mine'
  minePage.current = 1
  fetchMine()
}

function backToMain() {
  scene.value = 'main'
  pickedBottle.value = null
  openedBottle.value = null
  opened.value = false
}

// === 捡到瓶子场景 ===

async function handleOpenBottle() {
  if (!pickedBottle.value?.id) return
  openLoading.value = true
  try {
    const res = await getBottle(pickedBottle.value.id)
    openedBottle.value = res.data
    opened.value = true
  } catch (e) {
    handleBanned(e)
  } finally {
    openLoading.value = false
  }
}

async function handleRelease() {
  if (!pickedBottle.value?.id) {
    backToMain()
    return
  }
  try {
    await releaseBottle(pickedBottle.value.id)
    ElMessage.success('已放回大海')
  } catch (e) {
    handleBanned(e)
  } finally {
    backToMain()
  }
}

function openReplyDialog() {
  if (!isLoggedIn.value) { ElMessage.warning('请先登录'); return }
  if (isBanned.value) {
    ElMessage.error('账号已被封禁，无法操作')
    return
  }
  replyContent.value = ''
  replyVisible.value = true
}

async function handleReply() {
  if (!replyContent.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  replyLoading.value = true
  try {
    await replyBottle(pickedBottle.value.id, { content: replyContent.value.trim() })
    ElMessage.success('回复已送出')
    replyVisible.value = false
    replyContent.value = ''
    // 标记本瓶已回复
    if (openedBottle.value) {
      openedBottle.value = { ...openedBottle.value, hasReplied: true }
    }
  } catch (e) {
    handleBanned(e)
  } finally {
    replyLoading.value = false
  }
}

async function handleThankBottle() {
  if (!openedBottle.value) return
  if (!isLoggedIn.value) { ElMessage.warning('请先登录'); return }
  try {
    await thankBottle(bottleIdOf(openedBottle.value))
    ElMessage.success('已表达感谢')
    openedBottle.value = { ...openedBottle.value, hasThanked: true }
  } catch (e) {
    handleBanned(e)
  }
}

// === 我的瓶子列表 ===

async function fetchMine() {
  mineLoading.value = true
  try {
    const res = await getMyBottles(buildMineParams())
    const data = res.data
    mineList.value = pickList(data)
    mineTotal.value = pickTotal(data)
  } catch (e) {
    mineList.value = []
    mineTotal.value = 0
  } finally {
    mineLoading.value = false
  }
}

function handleMinePageChange(p) {
  minePage.current = p
  fetchMine()
}

async function openMineDetail(item) {
  detailBottle.value = item
  detailReplies.value = []
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getBottleReplies(item.id, { page: 1, size: 100 })
    detailReplies.value = pickList(res.data)
    if (detailReplies.value.length === 0) {
      // 静默显示空列表，可能确实没有回复
    }
  } catch (e) {
    detailReplies.value = []
    const msg = e?.response?.data?.message || e?.message || '获取回复失败'
    ElMessage.error(msg)
  } finally {
    detailLoading.value = false
  }
}

async function handleSink(item) {
  try {
    await ElMessageBox.confirm('确定要将这个漂流瓶沉底吗？沉底后将不再被他人捡到。', '提示', {
      type: 'warning',
      confirmButtonText: '沉底',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await sinkBottle(item.id)
    ElMessage.success('已沉底')
    item.status = 'sunk'
    if (detailBottle.value?.id === item.id) {
      detailBottle.value = { ...detailBottle.value, status: 'sunk' }
    }
  } catch (e) {
    handleBanned(e)
  }
}

async function handleThankReply(reply) {
  if (!isLoggedIn.value) { ElMessage.warning('请先登录'); return }
  try {
    await thankBottleReply(reply.id)
    ElMessage.success('已表达感谢')
    reply.hasThanked = true
  } catch (e) {
    handleBanned(e)
  }
}

// === 写信给对方 ===
const letterVisible = ref(false)
const letterContent = ref('')
const letterLoading = ref(false)
const letterTarget = ref(null) // { replyId, userId }

function openLetterDialog(reply) {
  if (!isLoggedIn.value) { ElMessage.warning('请先登录'); return }
  if (isBanned.value) {
    ElMessage.error('账号已被封禁，无法操作')
    return
  }
  letterTarget.value = { replyId: reply.id, userId: reply.userId }
  letterContent.value = ''
  letterVisible.value = true
}

async function handleSendLetter() {
  if (!letterContent.value.trim()) {
    ElMessage.warning('请输入信件内容')
    return
  }
  if (!letterTarget.value) return
  letterLoading.value = true
  try {
    await writeLetter({
      receiverId: letterTarget.value.userId,
      content: letterContent.value.trim(),
      sourceBottleReplyId: letterTarget.value.replyId
    })
    ElMessage.success('信件已寄出（消耗1代币）')
    letterVisible.value = false
    letterContent.value = ''
    // 刷新用户信息（代币余额）
    await userStore.fetchUserInfo()
  } catch (e) {
    handleBanned(e)
  } finally {
    letterLoading.value = false
  }
}

function previewContent(content) {
  if (!content) return ''
  return content.length > 40 ? content.slice(0, 40) + '…' : content
}

// 监听用户信息变化，自动同步封禁状态
watch(() => userStore.userInfo?.status, () => {
  isBanned.value = userStore.userInfo?.status === 'banned'
})

onMounted(async () => {
  // 进入页面时同步封禁状态
  isBanned.value = userStore.userInfo?.status === 'banned'
  // 启动瓶子生成定时器
  scheduleSpawn()
  // 首个瓶子尽快出现
  setTimeout(spawnBottle, 600)

  // 从通知跳转过来时，自动打开对应瓶子的详情
  const bottleId = route.query.id
  if (bottleId) {
    // 先进入"我的瓶子"场景
    scene.value = 'mine'
    minePage.current = 1
    await fetchMine()
    // 找到对应瓶子并打开详情
    const target = mineList.value.find((b) => String(b.id) === String(bottleId))
    if (target) {
      openMineDetail(target)
    } else {
      // 可能不在第一页，尝试用 bottleId 直接查回复
      detailBottle.value = { id: Number(bottleId), content: '', status: 'drifting', createdAt: '-' }
      detailVisible.value = true
      detailLoading.value = true
      try {
        const res = await getBottleReplies(bottleId, { page: 1, size: 100 })
        detailReplies.value = pickList(res.data)
      } catch (e) {
        // 非瓶主或不存在时，后端会返回错误，提示一下
        ElMessage.warning('无法查看该漂流瓶回复')
        detailVisible.value = false
      } finally {
        detailLoading.value = false
      }
    }
  }
})

onUnmounted(() => {
  if (spawnTimer) {
    clearTimeout(spawnTimer)
    spawnTimer = null
  }
})
</script>

<template>
  <div class="drift-bottle-page">
    <!-- 主场景：海浪沙滩（全屏） -->
    <div v-if="scene === 'main'" class="ocean-scene">
      <!-- 天空 -->
      <div class="sky">
        <div class="sun"></div>
        <div class="cloud cloud-1"></div>
        <div class="cloud cloud-2"></div>
      </div>
      <!-- 海洋 -->
      <div class="sea">
        <!-- 海面泡沫粒子 -->
        <div class="wave-crest wave-crest-1"></div>
        <div class="wave-crest wave-crest-2"></div>
        <div class="wave-crest wave-crest-3"></div>
      </div>
      <!-- 海面上的漂流瓶（与 sea / beach 平级） -->
      <div
        v-for="b in washedBottles"
        :key="b.uid"
        class="washed-bottle"
        :class="{ landed: b.landed, leaving: b.leaving, loading: b.loading }"
        :style="{ left: b.left + '%', '--rot': b.rotate + 'deg', '--bob-dur': b.bobDur + 's', '--bob-delay': b.bobDelay + 's' }"
        @click="openWashedBottle(b)"
      >
        <div class="bottle-bob">
          <div class="bottle-visual">🍾</div>
        </div>
      </div>
      <!-- 沙滩 -->
      <div class="beach">
        <!-- 海滩波浪（置于沙滩顶部，随沙滩高度浮动，始终贴着水沙交界线） -->
        <div class="break-wave">
          <svg class="wave-svg" viewBox="0 0 1440 80" preserveAspectRatio="none" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <linearGradient id="waveGrad1" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="rgba(13,148,136,0.45)" />
                <stop offset="20%" stop-color="rgba(125,200,230,0.55)" />
                <stop offset="50%" stop-color="rgba(220,250,255,0.6)" />
                <stop offset="78%" stop-color="rgba(248,252,254,0.92)" />
                <stop offset="100%" stop-color="rgba(255,255,255,1)" />
              </linearGradient>
            </defs>
            <path class="wave-path" fill="url(#waveGrad1)" d="M0,0 L1440,0 L1440,80 C1320,55 1200,35 1080,50 C960,65 840,30 720,45 C600,60 480,30 360,40 C240,50 120,65 0,50 Z" />
          </svg>
        </div>
        <div class="break-wave break-wave-2">
          <svg class="wave-svg" viewBox="0 0 1440 80" preserveAspectRatio="none" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <linearGradient id="waveGrad2" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="rgba(28,207,176,0.4)" />
                <stop offset="20%" stop-color="rgba(130,205,230,0.5)" />
                <stop offset="50%" stop-color="rgba(210,245,250,0.55)" />
                <stop offset="78%" stop-color="rgba(248,252,254,0.85)" />
                <stop offset="100%" stop-color="rgba(255,255,255,0.95)" />
              </linearGradient>
            </defs>
            <path class="wave-path" fill="url(#waveGrad2)" d="M0,0 L1440,0 L1440,80 C1300,60 1160,40 1020,55 C880,70 740,35 600,50 C460,65 320,35 180,50 C100,58 40,52 0,48 Z" />
          </svg>
        </div>
        <div class="break-wave break-wave-3">
          <svg class="wave-svg" viewBox="0 0 1440 80" preserveAspectRatio="none" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <linearGradient id="waveGrad3" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="rgba(45,212,191,0.3)" />
                <stop offset="20%" stop-color="rgba(140,210,230,0.4)" />
                <stop offset="50%" stop-color="rgba(200,240,245,0.45)" />
                <stop offset="78%" stop-color="rgba(248,252,254,0.78)" />
                <stop offset="100%" stop-color="rgba(255,255,255,0.9)" />
              </linearGradient>
            </defs>
            <path class="wave-path" fill="url(#waveGrad3)" d="M0,0 L1440,0 L1440,80 C1350,48 1220,32 1100,52 C980,72 860,32 740,42 C620,52 500,72 380,38 C260,4 140,56 0,40 Z" />
          </svg>
        </div>
        <!-- 沙滩纹理 -->
        <div class="beach-texture"></div>
        <!-- 沙滩操作区 -->
        <div class="beach-inner">
          <div class="ocean-title">🌊 漂流瓶</div>
          <p class="ocean-desc">{{ pickDesc }}</p>
          <!-- 公海/私海模式切换 -->
          <div class="pick-mode-toggle">
            <span class="mode-label">捞瓶海域：</span>
            <el-radio-group v-if="isLoggedIn" v-model="pickMode" size="small" :disabled="isBanned">
              <el-radio-button value="public">🌍 公海</el-radio-button>
              <el-radio-button value="private">🏝️ 私海</el-radio-button>
            </el-radio-group>
          </div>
          <div class="ocean-actions">
            <el-button
              v-if="isLoggedIn"
              size="large"
              round
              :disabled="isBanned"
              @click="openThrowDialog"
            >
              ✍️ 投漂流瓶
            </el-button>
            <el-button
              size="large"
              round
              type="primary"
              :loading="pickLoading"
              :disabled="isBanned"
              @click="handlePick"
            >
              🤚 捡漂流瓶
            </el-button>
            <el-button
              v-if="isLoggedIn"
              size="large"
              round
              @click="goMine"
            >
              📦 我的瓶子
            </el-button>
          </div>
          <div v-if="isBanned" class="banned-tip">账号已被封禁，暂无法投放或捡瓶子</div>
        </div>
      </div>
      <!-- 扔出动画瓶子 -->
      <div v-if="throwing" class="throwing-bottle">
        <div class="bottle-visual">🍾</div>
      </div>
    </div>

    <!-- 捡到瓶子场景 -->
    <div v-else-if="scene === 'picked'" class="picked-scene">
      <el-card shadow="hover" class="picked-card">
        <template #header>
          <div class="picked-header">
            <span>🍾 你捡到了一个漂流瓶</span>
          </div>
        </template>

        <div v-if="!opened" class="picked-info">
          <div class="picked-meta">瓶子编号：#{{ pickedBottle?.id || '-' }}</div>
          <div class="picked-meta">捡到时间：{{ pickedBottle?.pickedAt || '-' }}</div>
          <div class="picked-actions">
            <el-button
              type="primary"
              :loading="openLoading"
              :disabled="isBanned"
              @click="handleOpenBottle"
            >
              打开看看
            </el-button>
            <el-button :disabled="isBanned" @click="handleRelease">放回大海</el-button>
            <el-button @click="backToMain">返回</el-button>
          </div>
        </div>

        <div v-else v-loading="openLoading" class="bottle-content-wrap">
          <div class="bottle-author">
            <span class="author-name">🏷️ {{ openedBottle?.anonymousName || '匿名旅人' }}</span>
          </div>
          <div class="bottle-content">{{ openedBottle?.content || '（空）' }}</div>
          <div class="bottle-meta">
            投放时间：{{ openedBottle?.createdAt || openedBottle?.created_at || '-' }}
          </div>

          <el-alert
            v-if="hasRepliedBottle(openedBottle)"
            title="你已回复过这个瓶子"
            type="info"
            :closable="false"
            show-icon
            class="replied-tip"
          />

          <div class="bottle-actions">
            <el-button
              type="primary"
              :disabled="isBanned || hasRepliedBottle(openedBottle)"
              @click="openReplyDialog"
            >
              回复
            </el-button>
            <button
              class="thank-heart-btn"
              :class="{ thanked: hasThankedBottle(openedBottle) }"
              :disabled="hasThankedBottle(openedBottle)"
              @click="handleThankBottle"
              :title="hasThankedBottle(openedBottle) ? '已感谢' : '表达感谢'"
            >
              <span class="heart-icon">{{ hasThankedBottle(openedBottle) ? '❤️' : '🤍' }}</span>
            </button>
            <el-button v-if="!openedBottle.isFromBot" @click="openReportBottle">举报</el-button>
            <el-button :disabled="isBanned" @click="handleRelease">放回大海</el-button>
            <el-button @click="backToMain">返回</el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 我的瓶子列表 -->
    <div v-else-if="scene === 'mine'" class="mine-scene">
      <div class="mine-header">
        <h2 class="page-title">📦 我的瓶子</h2>
        <el-button @click="backToMain">返回大海</el-button>
      </div>

      <el-card v-loading="mineLoading" shadow="never" class="list-card">
        <!-- 搜索栏：关键词 + 时间范围 -->
        <div class="search-bar">
          <el-input
            v-model="mineKeyword"
            placeholder="搜索瓶子内容关键词"
            clearable
            class="search-input"
            @keyup.enter="applyMineSearch"
          >
            <template #prefix>🔍</template>
          </el-input>
          <el-select
            v-model="mineTimeRange"
            placeholder="按时间筛选"
            clearable
            class="time-select"
            @change="applyMineSearch"
          >
            <el-option label="全部时间" value="" />
            <el-option label="今天" value="today" />
            <el-option label="近7天" value="week" />
            <el-option label="近30天" value="month" />
            <el-option label="自定义" value="custom" />
          </el-select>
          <el-date-picker
            v-if="mineTimeRange === 'custom'"
            v-model="mineDateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            class="date-range"
            @change="applyMineSearch"
          />
          <el-button type="primary" @click="applyMineSearch">搜索</el-button>
          <el-button @click="resetMineSearch">重置</el-button>
        </div>

        <el-empty v-if="!mineLoading && mineList.length === 0" description="你还没有扔过漂流瓶或没有匹配结果" />

        <ul v-else class="mine-list">
          <li v-for="item in mineList" :key="item.id" class="mine-item">
            <div class="mine-item-main" @click="openMineDetail(item)">
              <div class="mine-item-head">
                <span class="mine-author">🏷️ {{ item.anonymousName || '匿名旅人' }}</span>
              </div>
              <div class="mine-item-content">{{ previewContent(item.content) }}</div>
              <div class="mine-item-meta">
                <el-tag size="small" :type="statusType(item.status)">
                  {{ statusLabel(item.status) }}
                </el-tag>
                <span class="meta-text">回复数：{{ item.replyCount ?? item.reply_count ?? 0 }}</span>
                <span class="meta-text">{{ item.createdAt || item.created_at || '-' }}</span>
              </div>
            </div>
            <div class="mine-item-actions">
              <el-button
                v-if="item.status === 'drifting'"
                size="small"
                :disabled="!isLoggedIn || isBanned"
                @click.stop="handleSink(item)"
              >
                沉底
              </el-button>
              <el-button size="small" @click.stop="openMineDetail(item)">查看详情</el-button>
            </div>
          </li>
        </ul>

        <div v-if="mineTotal > 0" class="pagination-wrap">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="minePage.current"
            :page-size="minePage.size"
            :total="mineTotal"
            @current-change="handleMinePageChange"
          />
        </div>
      </el-card>
    </div>

    <!-- 扔瓶子对话框 -->
    <el-dialog
      v-model="throwVisible"
      title="写下你想说的话"
      width="520px"
      destroy-on-close
    >
      <el-input
        v-model="throwContent"
        type="textarea"
        :rows="6"
        maxlength="500"
        show-word-limit
        placeholder="把心事装入瓶中…"
      />
      <template #footer>
        <el-button @click="throwVisible = false">取消</el-button>
        <el-button type="primary" :loading="throwLoading" @click="handleThrow">
          投入大海
        </el-button>
      </template>
    </el-dialog>

    <!-- 回复瓶子对话框 -->
    <el-dialog
      v-model="replyVisible"
      title="回复这个漂流瓶"
      width="520px"
      destroy-on-close
    >
      <el-input
        v-model="replyContent"
        type="textarea"
        :rows="6"
        maxlength="500"
        show-word-limit
        placeholder="写一句温柔的回复…"
      />
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="replyLoading" @click="handleReply">
          送出回复
        </el-button>
      </template>
    </el-dialog>

    <!-- 我的瓶子详情（含回复） -->
    <el-dialog
      v-model="detailVisible"
      title="瓶子详情"
      width="600px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <template v-if="detailBottle">
          <div class="detail-section">
            <el-tag size="small" :type="statusType(detailBottle.status)">
              {{ statusLabel(detailBottle.status) }}
            </el-tag>
            <span class="detail-time">
              {{ detailBottle.createdAt || detailBottle.created_at || '-' }}
            </span>
          </div>
          <div class="detail-content">{{ detailBottle.content }}</div>

          <el-divider content-position="left">回复列表</el-divider>

          <el-empty
            v-if="!detailLoading && detailReplies.length === 0"
            description="还没有回复"
            :image-size="80"
          />

          <ul v-else class="reply-list">
            <li v-for="r in detailReplies" :key="r.id" class="reply-item">
              <div class="reply-header">
                <span class="reply-author">💬 {{ r.anonymousName || '匿名旅人' }}</span>
              </div>
              <div class="reply-content">{{ r.content }}</div>
              <div class="reply-meta">
                <span>{{ r.createdAt || r.created_at || '-' }}</span>
                <div class="reply-actions">
                  <button
                    class="thank-heart-btn thank-heart-btn-small"
                    :class="{ thanked: hasThankedReply(r) }"
                    :disabled="hasThankedReply(r)"
                    @click="handleThankReply(r)"
                    :title="hasThankedReply(r) ? '已感谢' : '表达感谢'"
                  >
                    <span class="heart-icon">{{ hasThankedReply(r) ? '❤️' : '🤍' }}</span>
                  </button>
                  <el-button
                    size="small"
                    link
                    type="primary"
                    @click="openLetterDialog(r)"
                  >
                    写信
                  </el-button>
                  <el-button
                    v-if="!r.isFromBot"
                    size="small"
                    link
                    type="danger"
                    @click="openReportReply(r)"
                  >
                    举报
                  </el-button>
                </div>
              </div>
            </li>
          </ul>
        </template>
      </div>
    </el-dialog>

    <!-- 写信对话框 -->
    <el-dialog
      v-model="letterVisible"
      title="写一封信给对方"
      width="520px"
      destroy-on-close
    >
      <el-alert
        title="写信需要消耗 1 代币"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />
      <el-input
        v-model="letterContent"
        type="textarea"
        :rows="6"
        maxlength="2000"
        show-word-limit
        placeholder="写一段温柔的话…"
      />
      <template #footer>
        <el-button @click="letterVisible = false">取消</el-button>
        <el-button type="primary" :loading="letterLoading" @click="handleSendLetter">
          寄出
        </el-button>
      </template>
    </el-dialog>

    <!-- 举报弹窗 -->
    <ReportDialog
      ref="reportDialog"
      :target-type="reportTargetType"
      :target-id="reportTargetId"
    />
  </div>
</template>

<style scoped>
.drift-bottle-page {
  /* 主场景全屏；其它场景自带内边距 */
}

/* === 主场景：海浪沙滩（全屏） === */
.ocean-scene {
  position: relative;
  width: 100%;
  height: calc(100vh - 60px);
  min-height: 520px;
  overflow: hidden;
  background: linear-gradient(180deg, #040820 0%, #0a1438 18%, #102454 32%, #0a3a6b 58%, #062a4a 76%, #031a30 100%);
}
/* 天空（夜空 + 繁星） */
.sky {
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 38%;
  overflow: hidden;
  background:
    radial-gradient(ellipse at 30% 20%, rgba(80, 60, 140, 0.45) 0%, transparent 55%),
    radial-gradient(ellipse at 70% 25%, rgba(40, 80, 140, 0.4) 0%, transparent 60%),
    linear-gradient(180deg, #040820 0%, #0a1438 55%, #102454 95%, rgba(16, 36, 84, 0) 100%);
}
/* 繁星：三层不同大小的星点 */
.sky::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(1px 1px at 12% 18%, #ffffff 50%, transparent 51%),
    radial-gradient(1px 1px at 28% 8%, #ffffff 50%, transparent 51%),
    radial-gradient(1px 1px at 45% 22%, #ffffff 50%, transparent 51%),
    radial-gradient(1px 1px at 62% 12%, #ffffff 50%, transparent 51%),
    radial-gradient(1px 1px at 80% 20%, #ffffff 50%, transparent 51%),
    radial-gradient(1px 1px at 92% 6%, #ffffff 50%, transparent 51%),
    radial-gradient(1.5px 1.5px at 20% 30%, #fff9d4 50%, transparent 51%),
    radial-gradient(1.5px 1.5px at 55% 28%, #cfe2ff 50%, transparent 51%),
    radial-gradient(1.5px 1.5px at 78% 32%, #fff9d4 50%, transparent 51%),
    radial-gradient(1px 1px at 5% 40%, #ffffff 50%, transparent 51%),
    radial-gradient(1px 1px at 38% 38%, #ffffff 50%, transparent 51%),
    radial-gradient(1px 1px at 68% 42%, #ffffff 50%, transparent 51%),
    radial-gradient(1px 1px at 88% 38%, #ffffff 50%, transparent 51%),
    radial-gradient(2px 2px at 15% 28%, #fff 50%, transparent 51%),
    radial-gradient(2px 2px at 72% 18%, #ffe49f 50%, transparent 51%);
  animation: starTwinkle 4s ease-in-out infinite;
}
.sky::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(1px 1px at 8% 24%, rgba(255,255,255,0.8) 50%, transparent 51%),
    radial-gradient(1px 1px at 35% 14%, rgba(255,255,255,0.7) 50%, transparent 51%),
    radial-gradient(1px 1px at 58% 24%, rgba(255,255,255,0.85) 50%, transparent 51%),
    radial-gradient(1px 1px at 85% 10%, rgba(255,255,255,0.75) 50%, transparent 51%),
    radial-gradient(1.2px 1.2px at 22% 36%, #fff 50%, transparent 51%),
    radial-gradient(1.2px 1.2px at 65% 34%, #cfe2ff 50%, transparent 51%);
  animation: starTwinkle 5.5s ease-in-out infinite reverse;
}
@keyframes starTwinkle {
  0%, 100% { opacity: 0.85; }
  50% { opacity: 0.45; }
}
/* 月亮 */
.sun {
  position: absolute;
  top: 14%;
  right: 12%;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: radial-gradient(circle, #fff9e0 0%, #f5e8b0 55%, #d8c070 100%);
  box-shadow:
    0 0 40px 16px rgba(255, 240, 180, 0.35),
    0 0 80px 30px rgba(255, 220, 140, 0.18);
  z-index: 1;
}
.cloud {
  position: absolute;
  background: rgba(200, 215, 240, 0.25);
  border-radius: 50px;
  filter: blur(3px);
}
.cloud-1 {
  top: 22%;
  left: -12%;
  width: 140px;
  height: 16px;
  animation: cloudDrift 90s linear infinite;
}
.cloud-2 {
  top: 12%;
  left: 55%;
  width: 100px;
  height: 12px;
  opacity: 0.6;
  animation: cloudDrift 120s linear infinite;
  animation-delay: -40s;
}
@keyframes cloudDrift {
  from { transform: translateX(0); }
  to { transform: translateX(1200px); }
}
/* 海洋 */
.sea {
  position: absolute;
  bottom: 0; left: 0; right: 0;
  height: 74%;
  background: linear-gradient(180deg, #051530 0%, #0a2e5c 15%, #1a5fb4 40%, #0d9488 72%, #1ccfb0 86%, #2dd4bf 100%);
  overflow: hidden;
  z-index: 2;
}
/* 天海交界处：渐变模糊带 */
.sea::before {
  content: '';
  position: absolute;
  top: -10%;
  left: 0; right: 0;
  height: 22%;
  background: linear-gradient(180deg,
    rgba(16, 36, 84, 0) 0%,
    rgba(16, 36, 84, 0.55) 45%,
    rgba(10, 46, 92, 0.82) 100%);
  filter: blur(14px);
  pointer-events: none;
  z-index: 3;
}
/* 海面泡沫粒子（从远海向沙滩漂移） */
.wave-crest {
  position: absolute;
  left: -120px;
  width: calc(100% + 240px);
  background-repeat: repeat;
  pointer-events: none;
}
.wave-crest-1 {
  top: 10%;
  height: 30%;
  background-image:
    radial-gradient(circle at 12px 14px, rgba(255,255,255,0.85) 1.3px, transparent 2.2px),
    radial-gradient(circle at 34px 30px, rgba(255,255,255,0.6) 1px, transparent 1.8px),
    radial-gradient(circle at 52px 10px, rgba(255,255,255,0.5) 1.5px, transparent 2.6px);
  background-size: 64px 64px;
  animation: foamDrift1 18s linear infinite;
}
.wave-crest-2 {
  top: 35%;
  height: 32%;
  opacity: 0.85;
  background-image:
    radial-gradient(circle at 8px 22px, rgba(255,255,255,0.7) 1.1px, transparent 2px),
    radial-gradient(circle at 40px 12px, rgba(255,255,255,0.55) 1.4px, transparent 2.4px),
    radial-gradient(circle at 70px 30px, rgba(255,255,255,0.45) 1px, transparent 1.8px);
  background-size: 82px 82px;
  animation: foamDrift2 26s linear infinite reverse;
}
.wave-crest-3 {
  top: 60%;
  height: 28%;
  opacity: 0.7;
  background-image:
    radial-gradient(circle at 18px 18px, rgba(255,255,255,0.8) 1.5px, transparent 2.6px),
    radial-gradient(circle at 50px 34px, rgba(255,255,255,0.5) 1.1px, transparent 2px),
    radial-gradient(circle at 80px 12px, rgba(255,255,255,0.4) 1.3px, transparent 2.2px);
  background-size: 100px 100px;
  animation: foamDrift3 34s linear infinite;
}
@keyframes foamDrift1 {
  from { transform: translate(0, 0); }
  to { transform: translate(80px, -110px); }
}
@keyframes foamDrift2 {
  from { transform: translate(0, 0); }
  to { transform: translate(-90px, -120px); }
}
@keyframes foamDrift3 {
  from { transform: translate(0, 0); }
  to { transform: translate(110px, -140px); }
}
/* 海滩波浪（浅白色，向下凸起，起伏进退） */
.break-wave {
  position: absolute;
  /* 锚定沙滩顶部，随沙滩高度浮动，始终贴着水沙交界线（不再用相对 ocean-scene 的百分比） */
  top: -40px;
  left: 0;
  width: 100%;
  height: 80px;
  z-index: 10;
  pointer-events: none;
  overflow: visible;
}
.wave-svg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 80px;
  overflow: visible;
}
.wave-path {
  /* fill 由 SVG 内联 linearGradient 提供，顶部白色泡沫→底部青绿融入海水 */
}
/* 第二层（更远，更小） */
.break-wave-2 {
  z-index: 9;
  height: 72px;
  opacity: 0.75;
}
/* 第三层（最远，最小，最透） */
.break-wave-3 {
  z-index: 8;
  height: 64px;
  opacity: 0.55;
}
/* 进退动画：波浪从上方向下涌向沙滩，再退回 */
@keyframes breakWaveSurge {
  0% { transform: translateY(-6px) scaleX(1); }
  50% { transform: translateY(16px) scaleX(1.03); }
  100% { transform: translateY(-6px) scaleX(1); }
}
@keyframes breakWaveSurge2 {
  0% { transform: translateY(-4px) scaleX(0.98); }
  50% { transform: translateY(14px) scaleX(1.02); }
  100% { transform: translateY(-4px) scaleX(0.98); }
}
@keyframes breakWaveSurge3 {
  0% { transform: translateY(-3px) scaleX(1); }
  50% { transform: translateY(12px) scaleX(1.015); }
  100% { transform: translateY(-3px) scaleX(1); }
}
.break-wave {
  animation: breakWaveSurge 4s ease-in-out infinite;
}
.break-wave-2 {
  animation: breakWaveSurge2 5.5s ease-in-out infinite;
  animation-delay: -1.8s;
}
.break-wave-3 {
  animation: breakWaveSurge3 7s ease-in-out infinite;
  animation-delay: -3.2s;
}
/* 沙滩 */
.beach {
  position: absolute;
  bottom: 0; left: 0; right: 0;
  z-index: 5;
  /* 高度跟随内容，避免窗口缩小时按钮被 ocean-scene 的 overflow:hidden 裁掉 */
  min-height: 130px;
  background: linear-gradient(180deg, rgba(245,225,180,0) 0%, #f5e1b4 18%, #e8c98a 50%, #d4ad6a 100%);
  box-shadow: 0 -8px 24px rgba(0,0,0,0.12);
}
/* 海沙交界处：模糊过渡带（青绿→沙色） */
.beach::before {
  content: '';
  position: absolute;
  top: -12%;
  left: 0; right: 0;
  height: 28%;
  background: linear-gradient(180deg,
    rgba(45, 212, 191, 0) 0%,
    rgba(45, 212, 191, 0.35) 35%,
    rgba(245, 225, 180, 0.55) 70%,
    rgba(245, 225, 180, 0) 100%);
  filter: blur(16px);
  pointer-events: none;
  z-index: 6;
}
.beach-texture {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(circle at 20% 70%, rgba(180,140,80,0.25) 1px, transparent 2px),
    radial-gradient(circle at 60% 85%, rgba(180,140,80,0.2) 1px, transparent 2px),
    radial-gradient(circle at 80% 60%, rgba(180,140,80,0.22) 1px, transparent 2px),
    radial-gradient(circle at 35% 90%, rgba(180,140,80,0.18) 1px, transparent 2px);
  background-size: 40px 40px, 55px 55px, 35px 35px, 50px 50px;
  opacity: 0.6;
  pointer-events: none;
}
.beach-inner {
  position: relative;
  text-align: center;
  color: #5a4020;
  padding: 22px 24px 28px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.4);
}
.ocean-title {
  font-size: 30px;
  font-weight: bold;
  letter-spacing: 2px;
  color: #fff;
  text-shadow: 0 0 16px rgba(180, 210, 255, 0.6), 0 2px 8px rgba(0, 0, 0, 0.55);
}
.ocean-desc {
  font-size: 14px;
  margin: 8px 0 16px;
  color: #fff;
  text-shadow: 0 1px 4px rgba(0,0,0,0.35);
}
/* 公海/私海模式切换 */
.pick-mode-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 14px;
}
.mode-label {
  font-size: 13px;
  color: #fff;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.35);
}
.pick-mode-toggle :deep(.el-radio-button__inner) {
  font-weight: 600;
}
.ocean-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  justify-content: center;
}
/* 图标发光描边 + 文字阴影 */
.ocean-actions :deep(.el-button) {
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.35);
}
.ocean-actions :deep(.el-button span) {
  text-shadow: 0 0 10px rgba(255, 235, 150, 0.85), 0 0 4px rgba(255, 255, 255, 0.6);
}
.banned-tip {
  margin-top: 12px;
  font-size: 13px;
  background: rgba(245, 108, 108, 0.85);
  color: #fff;
  padding: 6px 14px;
  border-radius: 16px;
  display: inline-block;
}

/* 被推上岸的瓶子 */
.washed-bottle {
  position: absolute;
  bottom: 26%;
  z-index: 15;
  cursor: pointer;
  transform: translateX(-50%);
  animation: bottleAppear 0.9s ease-out forwards;
  filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.3));
  user-select: none;
  --bob-dur: 14s;
  --bob-delay: 0s;
}
.washed-bottle:not(.landed) {
  pointer-events: none;
}
.bottle-bob {
  display: inline-block;
  animation: bottleFloat var(--bob-dur) linear forwards;
  animation-delay: var(--bob-delay);
  transform-origin: 50% 100%;
}
.bottle-visual {
  position: relative;
  display: inline-block;
  width: 13px;
  height: 19px;
  font-size: 0;            /* 隐藏占位 emoji，瓶身由 CSS 绘制 */
  background: rgba(120, 200, 230, 0.18);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 0.5px solid rgba(255, 255, 255, 0.65);
  border-radius: 3px 3px 6px 6px;
  box-shadow:
    inset 0 0 4px rgba(255, 255, 255, 0.45),
    inset 0 -3px 5px rgba(120, 180, 220, 0.28),
    0 2px 6px rgba(0, 0, 0, 0.25);
  transform: rotate(var(--rot, 0deg));
  transition: transform 0.25s ease, filter 0.25s ease, box-shadow 0.25s ease;
}
/* 瓶颈 + 软木塞 */
.bottle-visual::before {
  content: '';
  position: absolute;
  top: -5px;
  left: 50%;
  transform: translateX(-50%);
  width: 5px;
  height: 6px;
  border: 0.5px solid rgba(255, 255, 255, 0.65);
  border-radius: 1px 1px 0 0;
  background: linear-gradient(180deg,
    #c08552 0%, #c08552 38%,
    rgba(120, 200, 230, 0.22) 38%, rgba(120, 200, 230, 0.22) 100%);
  box-shadow: inset 0 0 2px rgba(255, 255, 255, 0.35);
}
/* 高光斜条纹 */
.bottle-visual::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 3px 3px 6px 6px;
  background: repeating-linear-gradient(
    135deg,
    rgba(255, 255, 255, 0.35) 0px,
    rgba(255, 255, 255, 0.35) 0.6px,
    transparent 0.6px,
    transparent 3px
  );
  pointer-events: none;
}
.washed-bottle.landed:hover {
  filter: drop-shadow(0 0 14px rgba(255, 235, 120, 0.95));
}
.washed-bottle.landed:hover .bottle-visual {
  transform: rotate(var(--rot, 0deg)) scale(1.2);
}
.washed-bottle.loading {
  pointer-events: none;
}
.washed-bottle.loading .bottle-visual {
  opacity: 0.6;
  animation: bottleShake 0.4s ease-in-out infinite;
}
.washed-bottle.leaving {
  animation: bottleWashBack 1.4s ease-in forwards;
  pointer-events: none;
}
@keyframes bottleAppear {
  0% { transform: translateX(-50%) translateY(14px); opacity: 0; }
  100% { transform: translateX(-50%) translateY(0); opacity: 1; }
}
@keyframes bottleWashBack {
  0% { transform: translateX(-50%) translateY(0); opacity: 1; }
  100% { transform: translateX(-50%) translateY(30px); opacity: 0; }
}
@keyframes bottleFloat {
  0% { transform: translateY(0) rotate(0deg); opacity: 1; animation-timing-function: ease-out; }
  28% { transform: translateY(55px) rotate(6deg); opacity: 1; animation-timing-function: linear; }
  38% { transform: translateY(55px) rotate(6deg); opacity: 1; animation-timing-function: ease-in; }
  60% { transform: translateY(15px) rotate(2deg); opacity: 1; animation-timing-function: linear; }
  90% { transform: translateY(0) rotate(0deg); opacity: 1; animation-timing-function: linear; }
  100% { transform: translateY(0) rotate(0deg); opacity: 0; }
}
@keyframes bottleShake {
  0%, 100% { transform: rotate(0deg); }
  25% { transform: rotate(-8deg); }
  75% { transform: rotate(8deg); }
}

/* 扔出动画瓶子 */
.throwing-bottle {
  position: absolute;
  bottom: 10%;
  left: 50%;
  z-index: 11;
  font-size: 42px;
  animation: throwArc 1.8s cubic-bezier(0.3, 0.7, 0.6, 1) forwards;
  filter: drop-shadow(0 6px 8px rgba(0,0,0,0.3));
}
@keyframes throwArc {
  0% {
    bottom: 8%;
    left: 50%;
    transform: translateX(-50%) rotate(0deg) scale(0.8);
    opacity: 0;
  }
  15% {
    opacity: 1;
    transform: translateX(-50%) rotate(-25deg) scale(1);
  }
  50% {
    bottom: 60%;
    left: 58%;
    transform: translateX(-50%) rotate(180deg) scale(1);
  }
  85% {
    bottom: 40%;
    left: 72%;
    opacity: 1;
    transform: translateX(-50%) rotate(360deg) scale(0.9);
  }
  100% {
    bottom: 30%;
    left: 82%;
    opacity: 0;
    transform: translateX(-50%) rotate(540deg) scale(0.7);
  }
}

/* 捡到瓶子场景 */
.picked-scene {
  max-width: 640px;
  margin: 0 auto;
  padding: 24px 20px;
}
.picked-card {
  border-radius: 12px;
}
.picked-header {
  font-weight: 600;
  color: #303133;
}
.picked-info {
  text-align: center;
  padding: 24px 8px;
}
.picked-meta {
  color: #606266;
  font-size: 14px;
  margin-bottom: 8px;
}
.picked-actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}
.bottle-content-wrap {
  padding: 8px 4px;
}
.bottle-author {
  margin-bottom: 8px;
}
.author-name {
  display: inline-block;
  font-size: 13px;
  font-weight: 600;
  color: #6b4e2e;
  background: #fef0d8;
  padding: 3px 10px;
  border-radius: 12px;
}
.bottle-content {
  font-family: 'Ma Shan Zheng', 'KaiTi', '楷体', 'STKaiti', cursive;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.8;
  font-size: 15px;
  color: #303133;
  background: #fef7ea;
  padding: 16px;
  border-radius: 8px;
}
.bottle-meta {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}
.replied-tip {
  margin-top: 12px;
}
.bottle-actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

/* 我的瓶子 */
.mine-scene {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 20px;
}
.mine-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px 16px;
}
.page-title {
  margin: 0;
  font-size: 22px;
  color: #303133;
}
/* 检索栏 */
.search-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px;
  background: #faf7ee;
  border-radius: 8px;
  border: 1px solid #f0e6d2;
}
.search-input {
  max-width: 240px;
}
.time-select {
  width: 140px;
}
.date-range {
  width: auto;
  min-width: 260px;
}
.list-card {
  border-radius: 10px;
}
.mine-list {
  display: flex;
  flex-direction: column;
}
.mine-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 12px;
  border-bottom: 1px solid #f0e6d2;
}
.mine-item:last-child {
  border-bottom: none;
}
.mine-item-main {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}
.mine-item-main:hover .mine-item-content {
  color: #e89a1a;
}
.mine-item-head {
  margin-bottom: 6px;
}
.mine-author {
  display: inline-block;
  font-size: 12px;
  color: #6b4e2e;
  background: #fef0d8;
  padding: 2px 8px;
  border-radius: 10px;
}
.mine-item-content {
  font-family: 'Ma Shan Zheng', 'KaiTi', '楷体', 'STKaiti', cursive;
  font-size: 14px;
  color: #303133;
  margin-bottom: 8px;
  word-break: break-word;
  transition: color 0.2s ease;
}
.mine-item-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #909399;
  flex-wrap: wrap;
}
.meta-text {
  color: #909399;
}
.mine-item-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}

/* 详情对话框 */
.detail-section {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.detail-time {
  font-size: 12px;
  color: #909399;
}
.detail-content {
  font-family: 'Ma Shan Zheng', 'KaiTi', '楷体', 'STKaiti', cursive;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.8;
  background: #fef7ea;
  padding: 14px;
  border-radius: 8px;
  color: #303133;
}
.reply-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.reply-item {
  background: #fafafa;
  border-radius: 8px;
  padding: 10px 12px;
}
.reply-header {
  margin-bottom: 4px;
}
.reply-author {
  font-size: 12px;
  color: #6b4e2e;
  font-weight: 600;
}
.reply-content {
  font-family: 'Ma Shan Zheng', 'KaiTi', '楷体', 'STKaiti', cursive;
  white-space: pre-wrap;
  word-break: break-word;
  color: #303133;
  font-size: 14px;
  margin-bottom: 6px;
}
.reply-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}
.reply-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}

/* 感谢爱心按钮 */
.thank-heart-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  transition: all 0.2s ease;
}
.thank-heart-btn:hover:not(:disabled) {
  border-color: #f56c6c;
  color: #f56c6c;
  background: #fef0f0;
}
.thank-heart-btn:disabled {
  cursor: default;
  opacity: 0.7;
}
.thank-heart-btn.thanked {
  border-color: #f56c6c;
  background: #fef0f0;
  color: #f56c6c;
}
.thank-heart-btn .heart-icon {
  font-size: 16px;
  line-height: 1;
}
.thank-heart-btn .thank-label {
  font-size: 13px;
}
/* 小尺寸（回复列表用） */
.thank-heart-btn-small {
  border: none;
  padding: 4px 8px;
  background: transparent;
}
.thank-heart-btn-small:hover:not(:disabled) {
  background: #fef0f0;
  border-color: transparent;
}
.thank-heart-btn-small.thanked {
  background: transparent;
  border-color: transparent;
  color: #f56c6c;
}
.thank-heart-btn-small .heart-icon {
  font-size: 15px;
}
</style>
