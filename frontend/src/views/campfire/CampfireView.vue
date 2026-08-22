<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Switch } from '@element-plus/icons-vue'
import {
  getCampfires,
  createCampfire,
  getCampfire,
  getCampfireMessages,
  joinCampfire,
  leaveCampfire,
  extinguishCampfire
} from '@/api/campfire'
import { createStompClient } from '@/utils/stomp'
import { useUserStore } from '@/stores/user'
import ReportDialog from '@/components/ReportDialog.vue'

const userStore = useUserStore()
const currentUserId = computed(() => userStore.userInfo?.id)
const isLoggedIn = computed(() => userStore.isLoggedIn)
const router = useRouter()

const reportDialog = ref(null)
const reportTargetId = ref(null)

function openReportMessage(msg) {
  if (!msg) return
  if (!isLoggedIn.value) { ElMessage.warning('请先登录'); return }
  const id = msg.id ?? msg.messageId ?? msg.message_id
  if (id == null) {
    ElMessage.warning('消息ID缺失，无法举报')
    return
  }
  reportTargetId.value = id
  reportDialog.value?.open()
}

const isBanned = ref(false)

const listLoading = ref(false)
const campfireList = ref([])

const createVisible = ref(false)
const createLoading = ref(false)
const createForm = reactive({
  name: '',
  maxMembers: 10
})
const maxMembersOptions = [
  { value: 10, cost: 1 },
  { value: 20, cost: 2 },
  { value: 30, cost: 3 }
]
const currentCost = computed(
  () => maxMembersOptions.find((o) => o.value === createForm.maxMembers)?.cost ?? 0
)

// 当前身份："nickname" | "anonymous"
const currentMode = ref('nickname')
// 当前使用的显示名称（由后端返回的成员记录中的 anonymousName 字段）
// 初始从昵称值，anonymous时由后端决定anonymousName
const currentIdentityName = ref('')

const activeCampfire = ref(null)
const detailLoading = ref(false)
const messages = ref([])
const inputContent = ref('')
const sending = ref(false)
const switching = ref(false)
const stompClient = ref(null)
const stompConnected = ref(false)
const stompConnecting = ref(false)
const subscription = ref(null)
const messageListRef = ref(null)
const messageLoading = ref(false)

// 历史消息分页加载
// 规则：page=1是最新的100条，向上翻加载更早的，最多10页
const messagePage = ref(1)
const messagePageSize = 100
const hasMoreMessages = ref(true)
const loadingMoreMessages = ref(false)
const MAX_HISTORY_PAGES = 10
const loadedPageCount = ref(1) // 已加载的页数（默认加载了第1页）

// 20分钟超时自动退出
const IDLE_TIMEOUT_MS = 20 * 60 * 1000
const IDLE_STORAGE_KEY = 'glimmer_campfire_last_active'

function readLastActive() {
  try {
    const raw = localStorage.getItem(IDLE_STORAGE_KEY)
    if (!raw) return null
    return JSON.parse(raw)
  } catch (e) {
    return null
  }
}

function writeLastActive(campfireId) {
  const id = campfireIdOf(campfireId)
  if (!id) {
    localStorage.removeItem(IDLE_STORAGE_KEY)
    return
  }
  localStorage.setItem(IDLE_STORAGE_KEY, JSON.stringify({ campfireId: id, time: Date.now() }))
}

function clearLastActive() {
  localStorage.removeItem(IDLE_STORAGE_KEY)
}

function pickList(data) {
  if (!data) return []
  if (Array.isArray(data)) return data
  return data.records || data.list || data.items || []
}

function campfireIdOf(c) {
  return c?.id ?? c?.campfireId ?? c?.campfire_id
}

function campfireNameOf(c) {
  return c?.name ?? c?.campfireName ?? c?.campfire_name ?? '未命名篝火'
}

function isCreator(c) {
  if (!c) return false
  const uid = currentUserId.value
  if (uid == null) return false
  return c.creatorId === uid || c.creator_id === uid
}

function canExtinguish(c) {
  if (!c) return false
  return isCreator(c) && c.type !== 'default' && c.type !== 'system'
}

function typeLabel(t) {
  return t === 'default' ? '系统默认' : '自定义'
}
function typeTagType(t) {
  return t === 'default' ? 'success' : 'warning'
}

function formatTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  if (isNaN(d.getTime())) return String(t)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function handleBanned(e) {
  if (e?.code === 4015 || e?.code === 4019) {
    isBanned.value = true
    // 刷新用户信息同步封禁状态
    userStore.fetchUserInfo().catch(() => {})
  }
}

async function fetchList() {
  listLoading.value = true
  try {
    const res = await getCampfires()
    campfireList.value = pickList(res.data)
  } catch (e) {
    campfireList.value = []
    handleBanned(e)
  } finally {
    listLoading.value = false
  }
}

function openCreateDialog() {
  if (!isLoggedIn.value) { ElMessage.warning('请先登录'); return }
  if (isBanned.value) {
    ElMessage.error('账号已被封禁，无法操作')
    return
  }
  createForm.name = ''
  createForm.maxMembers = 10
  createVisible.value = true
}

async function handleCreate() {
  if (!createForm.name.trim()) {
    ElMessage.warning('请输入篝火名称')
    return
  }
  createLoading.value = true
  try {
    const res = await createCampfire({
      name: createForm.name.trim(),
      maxMembers: createForm.maxMembers
    })
    createVisible.value = false
    ElMessage.success('创建成功！正在进入篝火...')
    await enterCampfire(res.data)
  } catch (e) {
    handleBanned(e)
    await fetchList()
  } finally {
    createLoading.value = false
  }
}

async function enterCampfire(campfire) {
  const id = campfireIdOf(campfire)
  if (!id) return
  // 默认初始状态为昵称模式
  currentMode.value = 'nickname'
  detailLoading.value = true
  messages.value = []
  inputContent.value = ''
  // 重置分页状态
  messagePage.value = 1
  hasMoreMessages.value = true
  loadedPageCount.value = 1
  try {
    // 游客模式跳过 join，仅围观
    if (isLoggedIn.value) {
      const joinRes = await joinCampfire(id, { displayMode: currentMode.value })
      // 使用后端返回的成员信息（含 anonymousName）
      if (joinRes?.data?.anonymousName) {
        currentIdentityName.value = joinRes.data.anonymousName
      } else {
        currentIdentityName.value = userStore.userInfo?.nickname || '旅人'
      }
    } else {
      currentIdentityName.value = '游客'
    }
    const res = await getCampfire(id)
    activeCampfire.value = res.data
    await loadMessages(id)
    connectStomp(id)
    // 页面记录活跃时间
    writeLastActive(id)
  } catch (e) {
    handleBanned(e)
    ElMessage.error('进入篝火失败')
  } finally {
    detailLoading.value = false
  }
}

async function loadMessages(id) {
  messageLoading.value = true
  try {
    // 默认加载第1页（最新的100条）
    const res = await getCampfireMessages(id, { page: 1, size: messagePageSize })
    const list = pickList(res.data)
    messages.value = list
    messagePage.value = 1
    loadedPageCount.value = 1
    hasMoreMessages.value = list.length >= messagePageSize
    await nextTick()
    scrollToBottom()
  } catch (e) {
    messages.value = []
    hasMoreMessages.value = false
    handleBanned(e)
  } finally {
    messageLoading.value = false
  }
}

/** 向上滚动加载更多历史消息 */
async function loadMoreMessages(id) {
  if (loadingMoreMessages.value || !hasMoreMessages.value) return
  loadingMoreMessages.value = true
  try {
    const nextPage = messagePage.value + 1
    if (nextPage > MAX_HISTORY_PAGES) {
      hasMoreMessages.value = false
      return
    }
    const res = await getCampfireMessages(id, { page: nextPage, size: messagePageSize })
    const list = pickList(res.data)
    if (list.length > 0) {
      // 保存加载前的滚动位置
      const el = messageListRef.value
      const previousScrollHeight = el?.scrollHeight || 0
      const previousScrollTop = el?.scrollTop || 0

      // 在消息列表前面添加更早的消息
      messages.value = [...list, ...messages.value]
      messagePage.value = nextPage
      loadedPageCount.value++
      // 判断是否还有更多
      hasMoreMessages.value = list.length >= messagePageSize && nextPage < MAX_HISTORY_PAGES
      await nextTick()
      
      // 恢复滚动位置
      if (el) {
        const newScrollHeight = el.scrollHeight
        const diff = newScrollHeight - previousScrollHeight
        el.scrollTop = previousScrollTop + diff
      }
    } else {
      hasMoreMessages.value = false
    }
  } catch (e) {
    hasMoreMessages.value = false
  } finally {
    loadingMoreMessages.value = false
  }
}

/** 监听滚动事件，向上滚动加载更多 */
function onMessageScroll() {
  const el = messageListRef.value
  if (!el) return
  // 当滚动到顶部时加载更多
  if (el.scrollTop <= 100) {
    loadMoreMessages(getCurrentCampfireId())
  }
}

function getCurrentCampfireId() {
  return campfireIdOf(activeCampfire.value)
}

function connectStomp(campfireId) {
  disconnectStomp()
  stompConnecting.value = true
  stompConnected.value = false
  console.log('连接WebSocket, campfireId:', campfireId, 'token:', userStore.token?.substring(0, 20) + '...')
  try {
    const client = createStompClient({
      token: userStore.token,
      reconnectDelay: 5000,
      onConnect: () => {
        console.log('WebSocket连接成功')
        stompConnecting.value = false
        stompConnected.value = true
        subscription.value = client.subscribe(
          `/topic/campfire/${campfireId}`,
          (message) => {
            try {
              const body = JSON.parse(message.body)
              appendMessage(body)
            } catch (e) {
            }
          }
        )
      },
      onDisconnect: () => {
        console.log('WebSocket连接断开')
        stompConnected.value = false
        stompConnecting.value = false
      },
      onError: (frame) => {
        console.error('STOMP错误:', frame)
        stompConnected.value = false
        stompConnecting.value = false
        const msg = frame?.headers?.message || frame?.body || '服务器返回错误'
        if (!isBanned.value) {
          ElMessage.warning(`篝火消息：${msg}`)
        }
      },
      onWebSocketError: (evt) => {
        console.error('WebSocket错误:', evt)
        stompConnected.value = false
        stompConnecting.value = false
      }
    })
    stompClient.value = client
    client.activate()
  } catch (e) {
    console.error('创建WebSocket客户端失败:', e)
    stompConnecting.value = false
  }
}

function disconnectStomp() {
  if (subscription.value) {
    try {
      subscription.value.unsubscribe()
    } catch (e) {
    }
    subscription.value = null
  }
  if (stompClient.value) {
    try {
      stompClient.value.deactivate()
    } catch (e) {
    }
    stompClient.value = null
  }
  stompConnected.value = false
  stompConnecting.value = false
}

async function leaveCurrentCampfire() {
  const c = activeCampfire.value
  if (!c) return
  const id = campfireIdOf(c)
  try {
    await leaveCampfire(id)
  } catch (e) {
    handleBanned(e)
  } finally {
    disconnectStomp()
    activeCampfire.value = null
    messages.value = []
    inputContent.value = ''
    currentIdentityName.value = ''
    currentMode.value = 'nickname'
    clearLastActive()
  }
}

async function backToList() {
  await leaveCurrentCampfire()
  await fetchList()
}

async function handleExtinguish() {
  const c = activeCampfire.value
  if (!c) return
  try {
    await ElMessageBox.confirm('确定要熄灭这个篝火吗？熄灭后所有成员将被移出，篝火将不再可见。', '熄灭篝火', {
      confirmButtonText: '确定熄灭',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await extinguishCampfire(campfireIdOf(c))
    ElMessage.success('篝火已熄灭')
    await leaveCurrentCampfire()
    await fetchList()
  } catch (e) {
    if (e !== 'cancel') {
      handleBanned(e)
    }
  }
}

/**
 * 切换身份：nickname <-> anonymous
 * 调用 joinCampfire 更新 campfire_member.anonymous_name
 * 之后重新拉取历史消息不影响，但是当前显示
 */
async function toggleIdentity() {
  const c = activeCampfire.value
  if (!c) return
  const id = campfireIdOf(c)
  if (!id) return
  const newMode = currentMode.value === 'nickname' ? 'anonymous' : 'nickname'
  switching.value = true
  try {
    // 后端返回更新后的成员信息（含新的 anonymousName）
    const joinRes = await joinCampfire(id, { displayMode: newMode })
    currentMode.value = newMode
    // 直接使用后端返回的 anonymousName
    if (joinRes?.data?.anonymousName) {
      currentIdentityName.value = joinRes.data.anonymousName
    } else if (newMode === 'nickname') {
      currentIdentityName.value = userStore.userInfo?.nickname || '旅人'
    } else {
      currentIdentityName.value = '匿名旅人'
    }
    // 重置分页状态并重新加载历史消息，刷新消息中的名称
    messagePage.value = 1
    hasMoreMessages.value = true
    loadedPageCount.value = 1
    await loadMessages(id)
    ElMessage.success(`已切换为：${currentIdentityName.value}`)
  } catch (e) {
    handleBanned(e)
    ElMessage.error('切换身份失败')
  } finally {
    switching.value = false
  }
}

function sendMessage() {
  if (!isLoggedIn.value) { ElMessage.warning('请先登录'); return }
  if (isBanned.value) {
    ElMessage.error('账号已被封禁，无法发送')
    return
  }
  const content = inputContent.value.trim()
  if (!content) return
  if (!stompClient.value || !stompConnected.value) {
    ElMessage.warning('正在连接聊天室，请稍候')
    return
  }
  sending.value = true
  try {
    const payload = { content }
    if (replyingTo.value?.id) {
      payload.quotedMessageId = replyingTo.value.id
    }
    stompClient.value.publish({
      destination: `/app/campfire/${campfireIdOf(activeCampfire.value)}/send`,
      body: JSON.stringify(payload)
    })
    inputContent.value = ''
    replyingTo.value = null
  } catch (e) {
    ElMessage.error('发送失败，请重试')
  } finally {
    sending.value = false
  }
}

// 引用回复状态
const replyingTo = ref(null) // { id, anonymousName, content }

function startReply(msg) {
  const content = msg.content || ''
  replyingTo.value = {
    id: msg.id ?? msg.messageId,
    anonymousName: msg.anonymousName ?? msg.anonymous_name ?? '旅人',
    content: content.length > 50 ? content.substring(0, 50) + '...' : content
  }
}

function cancelReply() {
  replyingTo.value = null
}

function scrollToQuoted(messageId) {
  if (!messageId) return
  const idx = messages.value.findIndex(m => (m.id ?? m.messageId) === messageId)
  if (idx < 0) {
    ElMessage.info('该历史消息不在当前加载范围内')
    return
  }
  const el = messageListRef.value
  if (el) {
    const target = el.children[idx + 1] // +1 because first child is the load-more-tip
    if (target) {
      el.scrollTo({ top: target.offsetTop - 20, behavior: 'smooth' })
      // 高亮效果
      target.classList.add('highlight-quoted')
      setTimeout(() => target.classList.remove('highlight-quoted'), 1500)
    }
  }
}

function appendMessage(msg) {
  if (!msg) return
  const id = msg.id ?? msg.messageId ?? msg.message_id
  if (id != null && messages.value.some((m) => (m.id ?? m.messageId) === id)) {
    return
  }
  messages.value.push(msg)
  // 如果是自己的消息，更新currentIdentityName
  if ((msg.userId ?? msg.user_id) === currentUserId.value) {
    const n = msg.anonymousName ?? msg.anonymous_name
    if (n) currentIdentityName.value = n
  }
  nextTick(() => scrollToBottom())
}

function scrollToBottom() {
  const el = messageListRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

function isMine(msg) {
  const uid = currentUserId.value
  if (uid == null) return false
  return msg.userId === uid || msg.user_id === uid
}

function handleVisibilityChange() {
  if (!activeCampfire.value) return
  if (document.visibilityState === 'hidden') {
    // 记录离开时间
    writeLastActive(activeCampfire.value)
  } else if (document.visibilityState === 'visible') {
    // 检查超过20分钟→自动退出
    const last = readLastActive()
    const now = Date.now()
    if (last && last.campfireId === campfireIdOf(activeCampfire.value)) {
      if (now - last.time > IDLE_TIMEOUT_MS) {
        ElMessage.info('离开已超过20分钟，已自动退出篝火')
        backToList()
      } else {
        // 20分钟内→无需重新进入，更新活跃时间
        writeLastActive(activeCampfire.value)
      }
    }
  }
}

// 监听用户信息变化，自动同步封禁状态
watch(() => userStore.userInfo?.status, () => {
  isBanned.value = userStore.userInfo?.status === 'banned'
})

onMounted(async () => {
  isBanned.value = userStore.userInfo?.status === 'banned'
  document.addEventListener('visibilitychange', handleVisibilityChange)
  // 检查是否有20分钟内未超时的篝火会话 → 直接进入该篝火
  const last = readLastActive()
  const now = Date.now()
  if (last && now - last.time <= IDLE_TIMEOUT_MS) {
    // 20分钟内返回 → 自动进入上次篝火，无需重选
    try {
      await fetchList()
      const campfire = campfireList.value.find((c) => campfireIdOf(c) === last.campfireId)
      if (campfire) {
        await enterCampfire(campfire)
        return
      }
      // 找不到该篝火了（可能被熄灭），正常进入列表
    } catch (e) {
      console.warn('恢复上次篝火失败，转入列表', e)
    }
  }
  await fetchList()
})

onBeforeRouteLeave(async (to, from, next) => {
  // 记录离开时间，但不掉 leaveCampfire — 不调用
  if (activeCampfire.value) {
    writeLastActive(activeCampfire.value)
  }
  disconnectStomp()
  next()
})

onUnmounted(() => {
  // 记录离开时间但不 leaveCampfire — 20分钟内回来可恢复
  if (activeCampfire.value) {
    writeLastActive(activeCampfire.value)
  }
  disconnectStomp()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <div class="campfire-page" v-loading="detailLoading">
    <div v-if="!activeCampfire" class="list-scene">
      <div class="scene-header">
        <h2 class="page-title">🔥 小篝火</h2>
        <el-button
          type="primary"
          :disabled="isBanned"
          @click="openCreateDialog"
        >
          + 创建篝火
        </el-button>
      </div>
      <div v-if="isBanned" class="banned-tip">账号已被封禁，暂无法创建或加入篝火</div>

      <el-card v-loading="listLoading" shadow="never" class="list-card">
        <el-empty
          v-if="!listLoading && campfireList.length === 0"
          description="暂无篝火"
        />
        <div v-else class="campfire-grid">
          <el-card
            v-for="c in campfireList"
            :key="campfireIdOf(c)"
            shadow="hover"
            class="campfire-card"
          >
            <div class="card-head">
              <span class="card-name">{{ campfireNameOf(c) }}</span>
              <el-tag size="small" :type="typeTagType(c.type)">
                {{ typeLabel(c.type) }}
              </el-tag>
            </div>
            <div class="card-meta">
              <span>👥 {{ c.memberCount ?? c.member_count ?? 0 }}/{{ c.maxMembers ?? c.max_members ?? '-' }}</span>
              <span class="meta-time">{{ formatTime(c.createdAt || c.created_at) }}</span>
            </div>
            <div class="card-footer">
              <el-button
                type="primary"
                size="small"
                @click="enterCampfire(c)"
              >
                进入
              </el-button>
              <span v-if="isCreator(c)" class="creator-flag">创建者</span>
            </div>
          </el-card>
        </div>
      </el-card>
    </div>

    <div v-else class="chat-scene">
      <div class="chat-header">
        <div class="chat-title">
          <span class="title-name">{{ campfireNameOf(activeCampfire) }}</span>
          <el-tag size="small" :type="typeTagType(activeCampfire?.type)">
            {{ typeLabel(activeCampfire?.type) }}
          </el-tag>
          <span class="online-count">
            👥 {{ activeCampfire?.memberCount ?? activeCampfire?.member_count ?? 0 }}/{{ activeCampfire?.maxMembers ?? activeCampfire?.max_members ?? '-' }}
          </span>
          <span class="ws-status">
            <el-tag
              size="small"
              :type="stompConnected ? 'success' : 'info'"
              effect="plain"
            >
              {{ stompConnected ? '已连接' : stompConnecting ? '连接中' : '未连接' }}
            </el-tag>
          </span>
        </div>
        <div class="chat-actions">
          <!-- 身份切换区：右上角切换按钮 -->
          <div class="identity-switcher">
            <el-tooltip
              :content="currentMode === 'nickname' ? '当前：昵称模式，点击切换为匿名' : '当前：匿名模式，点击切换为昵称'"
              placement="bottom"
            >
              <el-button
                size="small"
                type="warning"
                effect="light"
                round
                class="identity-btn"
                :loading="switching"
                @click="toggleIdentity"
              >
                <el-icon class="mode-icon"><Switch /></el-icon>
                <span class="identity-name">{{ currentIdentityName || (currentMode === 'nickname' ? '🎭' : '👤') }}</span>
              </el-button>
            </el-tooltip>
            </div>
          <el-button size="small" @click="backToList">返回列表</el-button>
          <el-button
            v-if="canExtinguish(activeCampfire)"
            size="small"
            type="danger"
            @click="handleExtinguish"
          >
            熄灭篝火
          </el-button>
        </div>
      </div>

      <div class="chat-body" ref="messageListRef" @scroll="onMessageScroll">
        <!-- 加载更多提示 -->
        <div v-if="loadingMoreMessages" class="load-more-tip">加载中...</div>
        <div v-else-if="!hasMoreMessages && messages.length > 0" class="load-more-tip">火星已经消散啦</div>
        <div v-else-if="hasMoreMessages && messages.length > 0" class="load-more-tip">↑ 向上滚动加载更多历史消息</div>
        
        <el-empty
          v-if="messages.length === 0 && !messageLoading"
          description="还没有消息，说点什么吧"
          :image-size="80"
        />
        <div
          v-for="m in messages"
          :key="m.id ?? m.messageId"
          class="message-item"
          :class="{ mine: isMine(m) }"
        >
          <div class="bubble">
            <div class="bubble-name">{{ m.anonymousName ?? m.anonymous_name ?? '旅人' }}</div>
            <!-- 引用内容显示 -->
            <div v-if="m.quotedContent" class="quote-block" @click="scrollToQuoted(m.quotedMessageId)">
              <div class="quote-line"></div>
              <div class="quote-content">
                <div class="quote-author">{{ m.quotedAnonymousName || '旅人' }}</div>
                <div class="quote-text">{{ m.quotedContent }}</div>
              </div>
            </div>
            <div class="bubble-content">{{ m.content }}</div>
            <div class="bubble-actions">
              <span class="bubble-time">{{ formatTime(m.createdAt || m.created_at) }}</span>
              <div class="bubble-btns">
                <!-- 回复按钮 -->
                <button
                  class="reply-btn"
                  v-if="!isMine(m) && !m.isFromBot"
                  @click.stop="startReply(m)"
                >
                  回复
                </button>
                <!-- 举报按钮 -->
                <button
                  v-if="!isMine(m) && !m.isFromBot"
                  class="report-btn"
                  @click.stop="openReportMessage(m)"
                >
                  举报
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 引用回复预览 -->
      <div v-if="replyingTo" class="reply-preview-bar">
        <div class="reply-preview-info">
          <span class="reply-label">回复</span>
          <span class="reply-target-name">{{ replyingTo.anonymousName }}</span>
          <span class="reply-target-content">{{ replyingTo.content }}</span>
        </div>
        <el-icon class="reply-close" @click="cancelReply">✕</el-icon>
      </div>

      <div class="chat-input">
        <el-input
          v-model="inputContent"
          :placeholder="replyingTo ? `回复 ${replyingTo.anonymousName}...` : '写一句温暖的话…'"
          maxlength="500"
          :disabled="isBanned"
          @keyup.enter="sendMessage"
        />
        <el-button
          type="primary"
          :loading="sending"
          :disabled="isBanned || !inputContent.trim()"
          @click="sendMessage"
        >
          发送
        </el-button>
      </div>
      <div v-if="isBanned" class="banned-tip">账号已被封禁，发送功能已禁用</div>
    </div>

    <el-dialog
      v-model="createVisible"
      title="创建篝火"
      width="460px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="篝火名称">
          <el-input
            v-model="createForm.name"
            placeholder="给篝火取个名字"
            maxlength="30"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="人数上限">
          <el-radio-group v-model="createForm.maxMembers">
            <el-radio
              v-for="o in maxMembersOptions"
              :key="o.value"
              :value="o.value"
            >
              {{ o.value }}人（消耗 {{ o.cost }} 代币）
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="创建篝火将消耗代币，创建者自动成为成员"
        />
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreate">
          创建（消耗 {{ currentCost }} 代币）
        </el-button>
      </template>
    </el-dialog>

    <ReportDialog
      ref="reportDialog"
      target-type="campfire_message"
      :target-id="reportTargetId"
    />
  </div>
</template>

<style scoped>
.campfire-page {
  min-height: 60vh;
}
.scene-header {
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
.banned-tip {
  margin: 0 4px 12px;
  font-size: 13px;
  background: rgba(245, 108, 108, 0.85);
  color: #fff;
  padding: 6px 14px;
  border-radius: 16px;
  display: inline-block;
}
.list-card {
  border-radius: 10px;
}
.campfire-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}
.campfire-card {
  border-radius: 10px;
  transition: transform 0.2s ease;
}
.campfire-card:hover {
  transform: translateY(-3px);
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}
.card-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  word-break: break-all;
}
.card-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: #909399;
  margin-bottom: 12px;
}
.meta-time {
  font-size: 12px;
}
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.creator-flag {
  font-size: 12px;
  color: #e89a1a;
}

.chat-scene {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #fff8eb;
}
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 20px;
  background: linear-gradient(135deg, #f5a623 0%, #ffd970 100%);
  color: #3a2a00;
  flex-wrap: wrap;
}
.chat-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.title-name {
  font-size: 18px;
  font-weight: 600;
}
.online-count,
.ws-status {
  font-size: 13px;
}
.chat-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.identity-switcher {
  margin-right: 4px;
}
.identity-btn {
  max-width: 240px;
  padding: 0 14px;
  font-weight: 500;
}
.mode-icon {
  margin-right: 4px;
}
.identity-name {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-block;
  vertical-align: middle;
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  box-sizing: border-box;
}
.load-more-tip {
  text-align: center;
  padding: 8px;
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
  border-radius: 6px;
  margin-bottom: 12px;
}
.message-item {
  display: flex;
  margin-bottom: 12px;
}
.message-item.mine {
  justify-content: flex-end;
}
.bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 12px;
  background: #fff;
  color: #303133;
  word-break: break-word;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.message-item.mine .bubble {
  background: linear-gradient(135deg, #f5a623 0%, #ffd970 100%);
  color: #3a2a00;
}
.bubble-name {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 4px;
  opacity: 0.85;
}
.bubble-content {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.bubble-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  gap: 8px;
}
.bubble-time {
  font-size: 11px;
  opacity: 0.6;
  flex-shrink: 0;
}
.bubble-btns {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
/* 回复按钮 */
.reply-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
  font-size: 11px;
  color: #909399;
  opacity: 0.7;
}
.reply-btn:hover {
  color: #409eff;
  opacity: 1;
}
/* 举报按钮 */
.report-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
  font-size: 11px;
  color: #909399;
  opacity: 0.7;
}
.report-btn:hover {
  color: #f56c6c;
  opacity: 1;
}
/* 引用块 */
.quote-block {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 6px 8px;
  margin: 4px 0;
  background: rgba(0, 0, 0, 0.06);
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}
.message-item.mine .quote-block {
  background: rgba(255, 255, 255, 0.3);
}
.quote-block:hover {
  background: rgba(0, 0, 0, 0.1);
}
.message-item.mine .quote-block:hover {
  background: rgba(255, 255, 255, 0.5);
}
.quote-line {
  width: 3px;
  min-height: 100%;
  background: #909399;
  border-radius: 2px;
  flex-shrink: 0;
}
.quote-content {
  flex: 1;
  min-width: 0;
}
.quote-author {
  font-size: 11px;
  font-weight: 600;
  opacity: 0.7;
  margin-bottom: 2px;
}
.quote-text {
  font-size: 12px;
  opacity: 0.65;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
/* 引用高亮效果 */
.message-item.highlight-quoted .bubble {
  animation: highlight-pulse 1.5s ease;
}
@keyframes highlight-pulse {
  0%, 100% { box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
  50% { box-shadow: 0 0 16px rgba(245, 108, 108, 0.5); }
}
/* 回复预览栏 */
.reply-preview-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #ecf5ff;
  border-top: 1px solid #d9ecff;
  font-size: 12px;
}
.reply-preview-info {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
}
.reply-label {
  color: #409eff;
  font-weight: 600;
  flex-shrink: 0;
}
.reply-target-name {
  color: #303133;
  font-weight: 500;
  flex-shrink: 0;
}
.reply-target-content {
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.reply-close {
  cursor: pointer;
  color: #909399;
  padding: 0 8px;
  font-size: 14px;
  flex-shrink: 0;
}
.reply-close:hover {
  color: #f56c6c;
}
.chat-input {
  display: flex;
  gap: 10px;
  padding: 12px 20px;
  background: #fff;
  border-top: 1px solid #f0e6d2;
}
</style>
