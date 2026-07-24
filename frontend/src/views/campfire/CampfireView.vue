<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
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
const router = useRouter()

const reportDialog = ref(null)
const reportTargetId = ref(null)

function openReportMessage(msg) {
  if (!msg) return
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

const activeCampfire = ref(null)
const detailLoading = ref(false)
const messages = ref([])
const inputContent = ref('')
const sending = ref(false)
const stompClient = ref(null)
const stompConnected = ref(false)
const stompConnecting = ref(false)
const subscription = ref(null)
const messageListRef = ref(null)
const messageLoading = ref(false)

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
  return t || '-'
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
  detailLoading.value = true
  messages.value = []
  inputContent.value = ''
  try {
    await joinCampfire(id)
    const res = await getCampfire(id)
    activeCampfire.value = res.data
    await loadMessages(id)
    connectStomp(id)
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
    const res = await getCampfireMessages(id, { page: 1, size: 50 })
    const list = pickList(res.data)
    messages.value = list
    await nextTick()
    scrollToBottom()
  } catch (e) {
    messages.value = []
    handleBanned(e)
  } finally {
    messageLoading.value = false
  }
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
  try {
    await leaveCampfire(campfireIdOf(c))
  } catch (e) {
    handleBanned(e)
  } finally {
    disconnectStomp()
    activeCampfire.value = null
    messages.value = []
    inputContent.value = ''
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

function sendMessage() {
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
    stompClient.value.publish({
      destination: `/app/campfire/${campfireIdOf(activeCampfire.value)}/send`,
      body: JSON.stringify({ content })
    })
    inputContent.value = ''
  } catch (e) {
    ElMessage.error('发送失败，请重试')
  } finally {
    sending.value = false
  }
}

function appendMessage(msg) {
  if (!msg) return
  const id = msg.id ?? msg.messageId ?? msg.message_id
  if (id != null && messages.value.some((m) => (m.id ?? m.messageId) === id)) {
    return
  }
  messages.value.push(msg)
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

// 监听用户信息变化，自动同步封禁状态
watch(() => userStore.userInfo?.status, () => {
  isBanned.value = userStore.userInfo?.status === 'banned'
})

onMounted(() => {
  isBanned.value = userStore.userInfo?.status === 'banned'
  fetchList()
})

onBeforeRouteLeave(async (to, from, next) => {
  await leaveCurrentCampfire()
  next()
})

onUnmounted(() => {
  leaveCurrentCampfire()
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
              <el-button type="primary" size="small" @click="enterCampfire(c)">
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

      <div class="chat-body" ref="messageListRef">
        <el-empty
          v-if="messages.length === 0"
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
            <div class="bubble-content">{{ m.content }}</div>
            <div class="bubble-time">
              <span>{{ formatTime(m.createdAt || m.created_at) }}</span>
              <el-button
                v-if="!isMine(m)"
                size="small"
                link
                type="danger"
                class="report-btn"
                @click.stop="openReportMessage(m)"
              >
                举报
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <el-input
          v-model="inputContent"
          placeholder="写一句温暖的话…"
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
  gap: 8px;
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  box-sizing: border-box;
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
.bubble-time {
  font-size: 11px;
  margin-top: 4px;
  opacity: 0.7;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.report-btn {
  opacity: 0.85;
  padding: 0;
  height: auto;
  font-size: 11px;
}
.chat-input {
  display: flex;
  gap: 10px;
  padding: 12px 20px;
  background: #fff;
  border-top: 1px solid #f0e6d2;
}
</style>
