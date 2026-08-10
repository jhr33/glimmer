<script setup>
import { nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createConversation,
  getConversations,
  getConversation,
  sendMessage,
  sendMessageFetchStream,
  unlockQuota
} from '@/api/ai'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

// 场景：list 会话列表 / detail 对话详情
const scene = ref('list')

// 用户封禁标记（4015）
const isBanned = ref(false)

// === 会话列表 ===
const listLoading = ref(false)
const conversationList = ref([])
const listPage = reactive({ current: 1, size: 10 })
const listTotal = ref(0)

// === 对话详情 ===
const activeConversation = ref(null)
const detailLoading = ref(false)
const messages = ref([])
const inputContent = ref('')
const sending = ref(false)
const messageListRef = ref(null)

// 流式回复相关
const streamingAiMessageId = ref(null)
const streamingAiContent = ref('')
// AbortController：组件卸载或重新发送时取消进行中的 fetch 流式请求，释放浏览器连接
let abortController = null

// === 额度解锁弹窗 ===
const unlockDialogVisible = ref(false)
const unlocking = ref(false)

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

function conversationIdOf(c) {
  return c?.id ?? c?.conversationId ?? c?.conversation_id
}

// 会话是否可发送消息
function canSend() {
  const c = activeConversation.value
  if (!c) return false
  if (isBanned.value) return false
  const status = c.status
  if (status !== 'active') return false
  // 配额校验：token 用完不可发送
  const quotaUsed = c.quotaUsed ?? 0
  const quotaLimit = c.quotaLimit ?? 9999
  if (quotaUsed >= quotaLimit) return false
  return true
}

// 当前会话是否额度已耗尽（用于显示解锁提示）
function isQuotaExhausted() {
  const c = activeConversation.value
  if (!c) return false
  const quotaUsed = c.quotaUsed ?? 0
  const quotaLimit = c.quotaLimit ?? 9999
  return quotaUsed >= quotaLimit
}

// 弹出解锁弹窗
function showUnlockDialog() {
  unlockDialogVisible.value = true
}

// 解锁额度：消耗 1 代币，额度 +10
async function handleUnlock() {
  const c = activeConversation.value
  if (!c) return
  unlocking.value = true
  try {
    const res = await unlockQuota(conversationIdOf(c))
    const data = res.data
    if (data) {
      c.quotaLimit = data.quotaLimit
      c.quotaUsed = data.quotaUsed
    }
    // 刷新用户代币余额
    userStore.fetchUserInfo().catch(() => {})
    unlockDialogVisible.value = false
    ElMessage.success('已解锁新的对话额度，继续聊聊吧～')
  } catch (e) {
    if (e?.code === 4003) {
      ElMessage.error('代币不足，无法解锁')
    } else {
      ElMessage.error(e.message || '解锁失败')
    }
  } finally {
    unlocking.value = false
  }
}

function handleBanned(e) {
  if (e?.code === 4015 || e?.code === 4019) {
    isBanned.value = true
    // 刷新用户信息同步封禁状态
    userStore.fetchUserInfo().catch(() => {})
  }
}

// 同步用户封禁状态（用于解封后恢复）
function syncUserStatus() {
  // 简化检查：只要status是banned就禁止发言
  isBanned.value = userStore.userInfo?.status === 'banned'
}

// === 列表 ===

async function fetchList() {
  listLoading.value = true
  try {
    const res = await getConversations({
      page: listPage.current,
      size: listPage.size
    })
    const data = res.data
    conversationList.value = pickList(data)
    listTotal.value = pickTotal(data)
  } catch (e) {
    conversationList.value = []
    listTotal.value = 0
    handleBanned(e)
  } finally {
    listLoading.value = false
  }
}

function handlePageChange(p) {
  listPage.current = p
  fetchList()
}

async function handleNewConversation() {
  if (isBanned.value) {
    ElMessage.error('账号已被封禁，无法操作')
    return
  }
  try {
    const res = await createConversation()
    ElMessage.success('已开启新对话（消耗 1 代币）')
    await openConversation(res.data)
    fetchList()
  } catch (e) {
    handleBanned(e)
  }
}

async function openConversation(item) {
  const id = conversationIdOf(item)
  if (!id) return
  detailLoading.value = true
  messages.value = []
  inputContent.value = ''
  try {
    const res = await getConversation(id)
    activeConversation.value = res.data?.conversation
    messages.value = pickList(res.data?.messages)
    scene.value = 'detail'
    await nextTick()
    scrollToBottom()
  } catch (e) {
    handleBanned(e)
    ElMessage.error('打开会话失败')
  } finally {
    detailLoading.value = false
  }
}

function backToList() {
  scene.value = 'list'
  activeConversation.value = null
  messages.value = []
  inputContent.value = ''
  fetchList()
}

// === 发送消息 ===

async function handleSend() {
  const c = activeConversation.value
  if (!c) return
  // 防止并发发送：上一次流式请求未完成时，禁止发起新请求
  // 否则旧 AbortController 被覆盖，旧连接泄漏，可能导致浏览器 6 连接耗尽
  if (sending.value) return
  if (isBanned.value) {
    ElMessage.error('账号已被封禁，无法发送')
    return
  }
  const content = inputContent.value.trim()
  if (!content) return
  if (!canSend()) {
    ElMessage.warning('当前会话不可发送消息')
    return
  }
  sending.value = true

  // 创建 AbortController，支持组件卸载时取消请求
  abortController = new AbortController()

  // 添加用户消息
  const userMsg = {
    id: `temp-${Date.now()}`,
    role: 'user',
    content,
    createdAt: nowStr()
  }
  messages.value.push(userMsg)
  inputContent.value = ''
  await nextTick()
  scrollToBottom()
  
  // 添加 AI 流式回复占位
  streamingAiMessageId.value = `stream-${Date.now()}`
  streamingAiContent.value = ''
  messages.value.push({
    id: streamingAiMessageId.value,
    role: 'ai',
    content: '',
    createdAt: nowStr(),
    isStreaming: true
  })
  await nextTick()
  scrollToBottom()
  
  try {
    await sendMessageFetchStream(conversationIdOf(c), content, (data) => {
      // 传递 signal 支持取消
      if (data.type === 'delta') {
        // 增量内容，更新流式消息
        streamingAiContent.value += data.delta || ''
        const aiMsgIdx = messages.value.findIndex(m => m.id === streamingAiMessageId.value)
        if (aiMsgIdx >= 0) {
          messages.value[aiMsgIdx].content = streamingAiContent.value
        }
        nextTick().then(() => scrollToBottom())
      } else if (data.type === 'final') {
        // 回复结束，替换为完整消息
        const aiMsgIdx = messages.value.findIndex(m => m.id === streamingAiMessageId.value)
        if (aiMsgIdx >= 0) {
          messages.value.splice(aiMsgIdx, 1)
        }
        if (data.userMessage) {
          const tempIdx = messages.value.findIndex(m => m.id === userMsg.id)
          if (tempIdx >= 0) {
            messages.value.splice(tempIdx, 1)
          }
          pushIfNotExist(messages.value, data.userMessage)
        }
        if (data.aiMessage) {
          pushIfNotExist(messages.value, data.aiMessage)
        }
        if (data.conversationStatus) {
          c.status = data.conversationStatus
        }
        if (data.messageCount != null) {
          c.messageCount = data.messageCount
        }
        if (data.maxMessages != null) {
          c.maxMessages = data.maxMessages
        }
        // 更新配额信息
        if (data.quotaUsed != null) {
          c.quotaUsed = data.quotaUsed
        }
        if (data.quotaLimit != null) {
          c.quotaLimit = data.quotaLimit
        }
        streamingAiMessageId.value = null
        streamingAiContent.value = ''
        nextTick().then(() => scrollToBottom())
        // 额度耗尽时弹出解锁窗
        if (data.quotaExhausted === true) {
          showUnlockDialog()
        }
      } else if (data.type === 'error') {
        // 错误处理：额度耗尽时弹解锁窗而非普通错误
        const errMsg = data.error || ''
        if (errMsg.includes('额度已用完') || errMsg.includes('额度')) {
          showUnlockDialog()
          return
        }
        throw new Error(errMsg)
      }
    }, abortController.signal)
  } catch (e) {
    const aiMsgIdx = messages.value.findIndex(m => m.id === streamingAiMessageId.value)
    if (aiMsgIdx >= 0) {
      messages.value.splice(aiMsgIdx, 1)
    }
    streamingAiMessageId.value = null
    streamingAiContent.value = ''
    handleBanned(e)
    if (e?.code === 4010 && activeConversation.value) {
      activeConversation.value.status = 'closed'
    }
    // 额度耗尽错误（4020）弹解锁窗
    if (e?.code === 4020 || (e?.message && e.message.includes('额度'))) {
      showUnlockDialog()
    } else {
      ElMessage.error(e.message || '发送失败')
    }
  } finally {
    sending.value = false
    abortController = null
  }
}

function pushIfNotExist(arr, msg) {
  if (!msg) return
  const id = msg.id ?? msg.messageId
  if (id != null && arr.some((m) => (m.id ?? m.messageId) === id)) return
  arr.push(msg)
}

function nowStr() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function scrollToBottom() {
  const el = messageListRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

function isUserMsg(m) {
  return m?.role === 'user'
}

// 监听用户信息变化，自动同步封禁状态
watch(() => userStore.userInfo?.status, () => {
  syncUserStatus()
})

onMounted(() => {
  syncUserStatus()
  fetchList()
})

// 组件卸载时取消进行中的流式请求，释放浏览器 HTTP 连接
// 这是防止"使用一段时间后通知接口超时"的关键修复
onUnmounted(() => {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
})
</script>

<template>
  <div class="ai-page" v-loading="detailLoading">
    <!-- 会话列表 -->
    <div v-if="scene === 'list'" class="list-scene">
      <div class="scene-header">
        <h2 class="page-title">✨ 树洞</h2>
        <el-button
          type="primary"
          :disabled="isBanned"
          @click="handleNewConversation"
        >
          + 开始新对话（消耗 1 代币）
        </el-button>
      </div>
      <div v-if="isBanned" class="banned-tip">账号已被封禁，暂无法开启新对话</div>

      <el-card v-loading="listLoading" shadow="never" class="list-card">
        <el-empty
          v-if="!listLoading && conversationList.length === 0"
          description="还没有对话，开启一段新的旅程吧"
        />
        <ul v-else class="conv-list">
          <li
            v-for="c in conversationList"
            :key="conversationIdOf(c)"
            class="conv-item"
            @click="openConversation(c)"
          >
            <div class="conv-main">
              <div class="conv-title">
                <span v-if="c.conversationType === 'free'" class="free-badge">🌙</span>
                <span v-else class="paid-badge">✨</span>
                {{ c.title || ('会话 #' + conversationIdOf(c)) }}
              </div>
              <div class="conv-meta">
                <span class="meta-text">{{ c.conversationType === 'free' ? '🌙 每日免费' : '✨ 代币会话' }}</span>
              </div>
            </div>
            <el-button size="small" @click.stop="openConversation(c)">查看</el-button>
          </li>
        </ul>

        <div v-if="listTotal > 0" class="pagination-wrap">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="listPage.current"
            :page-size="listPage.size"
            :total="listTotal"
            @current-change="handlePageChange"
          />
        </div>
      </el-card>
    </div>

    <!-- 对话详情 -->
    <div v-else class="detail-scene">
      <!-- 顶部：AI 名字 + 额度显示 -->
      <div class="detail-header">
        <div class="ai-name">✨ glimmer</div>
        <div class="quota-display">
          <span v-if="activeConversation?.conversationType === 'free'" class="free-tag">🌙 每日免费</span>
          <span v-else class="paid-tag">✨ 代币会话</span>
        </div>
        <button class="back-btn" @click="backToList">← 返回</button>
      </div>

      <!-- 消息列表：只显示内容 -->
      <div class="message-list" ref="messageListRef">
        <div v-if="messages.length === 0" class="empty-tip">
          <div class="empty-icon">💬</div>
          <div class="empty-text">开始与 glimmer 对话吧</div>
        </div>
        <div
          v-for="m in messages"
          :key="m.id ?? m.messageId"
          class="message-item"
          :class="{ mine: isUserMsg(m) }"
        >
          <div class="message-content">
            <span v-if="m.isStreaming && !m.content" class="typing-indicator">
              <span class="dot"></span>
              <span class="dot"></span>
              <span class="dot"></span>
            </span>
            <span v-else>{{ m.content }}</span>
          </div>
        </div>

      </div>

      <!-- 输入框 -->
      <div class="chat-input">
        <template v-if="canSend()">
          <el-input
            v-model="inputContent"
            placeholder="输入消息…"
            maxlength="500"
            :disabled="isBanned"
            @keyup.enter="handleSend"
          />
          <el-button
            type="primary"
            :loading="sending"
            :disabled="isBanned || !inputContent.trim()"
            @click="handleSend"
          >
            发送
          </el-button>
        </template>
        <template v-else-if="isQuotaExhausted() && !isBanned">
          <el-alert
            title="今日免费额度已用完～消耗 1 枚代币可以解锁新的对话额度 🌙"
            type="warning"
            :closable="false"
            show-icon
          />
          <el-button type="warning" @click="showUnlockDialog">
            🔓 解锁（-1代币）
          </el-button>
        </template>
        <template v-else>
          <el-alert
            :title="isBanned ? '账号已被封禁，无法发送消息' : '会话已关闭，无法发送消息'"
            type="info"
            :closable="false"
            show-icon
          />
        </template>
      </div>
    </div>

    <!-- 额度解锁弹窗 -->
    <el-dialog
      v-model="unlockDialogVisible"
      title="🌙 额度已达上限"
      width="420px"
      :close-on-click-modal="false"
      align-center
    >
      <div class="unlock-dialog-body">
        <p class="unlock-tip">
          今日免费额度已用完～<br>
          消耗 1 枚代币可以解锁新的对话额度继续聊 🌙
        </p>
      </div>
      <template #footer>
        <el-button @click="unlockDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="unlocking" @click="handleUnlock">
          🔓 解锁（-1代币）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ai-page {
  min-height: 60vh;
}
.scene-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px 16px;
  flex-wrap: wrap;
  gap: 12px;
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
.conv-list {
  display: flex;
  flex-direction: column;
}
.conv-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 12px;
  border-bottom: 1px solid #f0e6d2;
  cursor: pointer;
  transition: background 0.2s ease;
}
.conv-item:last-child {
  border-bottom: none;
}
.conv-item:hover {
  background: #fef7ea;
}
.conv-main {
  flex: 1;
  min-width: 0;
}
.conv-title {
  font-size: 15px;
  color: #303133;
  font-weight: 600;
  margin-bottom: 8px;
}
.conv-meta {
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
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}

/* 对话详情 */
.detail-scene {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 180px);
  min-height: 480px;
}

/* 顶部：AI 名字 */
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: #ffffff;
  border-bottom: 1px solid #dcdfe6;
}
.ai-name {
  font-size: 20px;
  font-weight: bold;
  color: #303133;
}
.back-btn {
  padding: 8px 16px;
  border-radius: 8px;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s ease;
}
.back-btn:hover {
  background: #e4e7ed;
}

/* 消息列表 */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #ffffff;
}

.empty-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
}
.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}
.empty-text {
  font-size: 16px;
  color: #909399;
}

.message-item {
  display: flex;
  margin-bottom: 16px;
}
.message-item.mine {
  justify-content: flex-end;
}

.message-content {
  max-width: 75%;
  padding: 12px 18px;
  border-radius: 16px;
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}
.message-item:not(.mine) .message-content {
  background: #ffffff;
  color: #303133;
  border: 1px solid #dcdfe6;
}
.message-item.mine .message-content {
  background: #e3f2fd;
  color: #303133;
}

/* 流式消息样式 */
.message-item .message-content {
  transition: opacity 0.1s ease;
}

/* 输入框 */
.chat-input {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px 20px;
  background: #f5f7fa;
  border-top: 1px solid #dcdfe6;
}

/* 会话列表：免费/付费标记 */
.free-badge {
  display: inline-block;
  background: #fef0e6;
  color: #e6a23c;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  margin-right: 6px;
}
.paid-badge {
  display: inline-block;
  background: #f0f9eb;
  color: #67c23a;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  margin-right: 6px;
}

/* 详情页：额度显示 */
.quota-display {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  justify-content: center;
}
.free-tag {
  background: #fef0e6;
  color: #e6a23c;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
}
.paid-tag {
  background: #f0f9eb;
  color: #67c23a;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
}
.quota-text {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

/* 解锁弹窗 */
.unlock-dialog-body {
  text-align: center;
  padding: 12px 0;
}
.unlock-tip {
  font-size: 15px;
  line-height: 1.8;
  color: #606266;
  margin: 0;
}
.unlock-tip strong {
  color: #e6a23c;
  font-size: 18px;
}

/* AI 正在思考的打字动画 */
.typing-indicator {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
}
.typing-indicator .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
  animation: typing-bounce 1.4s infinite ease-in-out;
}
.typing-indicator .dot:nth-child(1) {
  animation-delay: 0s;
}
.typing-indicator .dot:nth-child(2) {
  animation-delay: 0.2s;
}
.typing-indicator .dot:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes typing-bounce {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-8px);
    opacity: 1;
  }
}
</style>
