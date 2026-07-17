<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createConversation,
  getConversations,
  getConversation,
  sendMessage,
  closeConversation
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

function statusLabel(s) {
  if (s === 'active') return '进行中'
  if (s === 'closed') return '已关闭'
  if (s === 'timeout') return '已超时'
  return s || '-'
}
function statusTagType(s) {
  if (s === 'active') return 'success'
  if (s === 'closed') return 'info'
  if (s === 'timeout') return 'warning'
  return 'info'
}

function formatTime(t) {
  return t || '-'
}

// 会话是否可发送消息
function canSend() {
  const c = activeConversation.value
  if (!c) return false
  if (isBanned.value) return false
  const status = c.status
  if (status !== 'active') return false
  const count = c.messageCount ?? c.message_count ?? 0
  const max = c.maxMessages ?? c.max_messages ?? 100
  if (count >= max) return false
  return true
}

function handleBanned(e) {
  if (e?.code === 4015) {
    isBanned.value = true
  }
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
    // 直接进入新会话
    await openConversation(res.data)
    // 刷新列表
    fetchList()
  } catch (e) {
    handleBanned(e)
    // 4003 代币不足：错误信息已由拦截器提示，这里无需额外处理
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
    activeConversation.value = res.data
    // 会话详情含全部消息
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
  // 先乐观追加用户消息
  const tempUserMsg = {
    id: `temp-${Date.now()}`,
    role: 'user',
    content,
    createdAt: nowStr()
  }
  messages.value.push(tempUserMsg)
  inputContent.value = ''
  await nextTick()
  scrollToBottom()
  try {
    const res = await sendMessage(conversationIdOf(c), { content })
    const data = res.data
    // 移除临时消息
    const idx = messages.value.findIndex((m) => m.id === tempUserMsg.id)
    if (idx >= 0) messages.value.splice(idx, 1)
    // 追加服务端返回的 user 消息与 ai 消息（避免重复）
    if (data?.userMessage) {
      pushIfNotExist(messages.value, data.userMessage)
    }
    if (data?.aiMessage) {
      pushIfNotExist(messages.value, data.aiMessage)
    }
    // 更新会话状态
    if (data?.conversationStatus) {
      c.status = data.conversationStatus
    }
    if (data?.messageCount != null) {
      c.messageCount = data.messageCount
    }
    if (data?.maxMessages != null) {
      c.maxMessages = data.maxMessages
    }
    await nextTick()
    scrollToBottom()
  } catch (e) {
    // 移除临时消息，避免误导
    const idx = messages.value.findIndex((m) => m.id === tempUserMsg.id)
    if (idx >= 0) messages.value.splice(idx, 1)
    handleBanned(e)
    // 4010 会话已关闭：同步状态
    if (e?.code === 4010 && activeConversation.value) {
      activeConversation.value.status = 'closed'
    }
  } finally {
    sending.value = false
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

// === 关闭会话 ===

async function handleClose() {
  const c = activeConversation.value
  if (!c) return
  try {
    await ElMessageBox.confirm('关闭后将无法继续发送消息，确定关闭当前会话吗？', '关闭会话', {
      type: 'warning',
      confirmButtonText: '关闭',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await closeConversation(conversationIdOf(c))
    ElMessage.success('会话已关闭')
    c.status = 'closed'
  } catch (e) {
    handleBanned(e)
  }
}

onMounted(() => {
  if (userStore.userInfo?.status === 'banned') {
    isBanned.value = true
  }
  fetchList()
})
</script>

<template>
  <div class="ai-page" v-loading="detailLoading">
    <!-- 会话列表 -->
    <div v-if="scene === 'list'" class="list-scene">
      <div class="scene-header">
        <h2 class="page-title">🤖 AI 对话</h2>
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
              <div class="conv-title">会话 #{{ conversationIdOf(c) }}</div>
              <div class="conv-meta">
                <el-tag size="small" :type="statusTagType(c.status)">
                  {{ statusLabel(c.status) }}
                </el-tag>
                <span class="meta-text">
                  消息：{{ c.messageCount ?? c.message_count ?? 0 }}/{{ c.maxMessages ?? c.max_messages ?? 100 }}
                </span>
                <span class="meta-text">{{ formatTime(c.createdAt || c.created_at) }}</span>
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
      <div class="detail-header">
        <div class="detail-title">
          <span class="title-text">会话 #{{ conversationIdOf(activeConversation) }}</span>
          <el-tag size="small" :type="statusTagType(activeConversation?.status)">
            {{ statusLabel(activeConversation?.status) }}
          </el-tag>
          <span class="msg-count">
            消息：{{ activeConversation?.messageCount ?? activeConversation?.message_count ?? 0 }}/{{ activeConversation?.maxMessages ?? activeConversation?.max_messages ?? 100 }}
          </span>
        </div>
        <div class="detail-actions">
          <el-button size="small" @click="backToList">返回列表</el-button>
          <el-button
            size="small"
            type="danger"
            plain
            :disabled="activeConversation?.status !== 'active'"
            @click="handleClose"
          >
            关闭会话
          </el-button>
        </div>
      </div>

      <el-card shadow="never" class="chat-body" body-class="chat-body-inner">
        <div class="message-list" ref="messageListRef">
          <el-empty
            v-if="messages.length === 0"
            description="打个招呼吧，AI 正在等你"
            :image-size="80"
          />
          <div
            v-for="m in messages"
            :key="m.id ?? m.messageId"
            class="message-item"
            :class="{ mine: isUserMsg(m) }"
          >
            <div class="bubble">
              <div class="bubble-role">{{ isUserMsg(m) ? '我' : 'AI' }}</div>
              <div class="bubble-content">{{ m.content }}</div>
              <div class="bubble-time">{{ formatTime(m.createdAt || m.created_at) }}</div>
            </div>
          </div>
          <div v-if="sending" class="message-item">
            <div class="bubble ai-typing">
              <div class="bubble-role">AI</div>
              <div class="typing-dots">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <div class="chat-input">
        <template v-if="canSend()">
          <el-input
            v-model="inputContent"
            placeholder="写下你想说的话…"
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
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 4px 12px;
  flex-wrap: wrap;
}
.detail-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.title-text {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.msg-count {
  font-size: 13px;
  color: #909399;
}
.detail-actions {
  display: flex;
  gap: 8px;
}
.chat-body {
  flex: 1;
  min-height: 0;
  border-radius: 10px;
}
:deep(.chat-body-inner) {
  padding: 0;
  height: 100%;
}
.message-list {
  height: 100%;
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
  background: #f4f4f5;
  color: #303133;
  word-break: break-word;
}
.message-item.mine .bubble {
  background: linear-gradient(135deg, #f5a623 0%, #ffd970 100%);
  color: #3a2a00;
}
.bubble-role {
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
}
.ai-typing {
  display: inline-block;
}
.typing-dots {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 4px 0;
}
.typing-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c0c4cc;
  animation: typing 1.2s infinite ease-in-out;
}
.typing-dots span:nth-child(2) {
  animation-delay: 0.2s;
}
.typing-dots span:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-4px); opacity: 1; }
}
.chat-input {
  display: flex;
  gap: 10px;
  padding: 12px 4px 4px;
  align-items: center;
}
</style>
