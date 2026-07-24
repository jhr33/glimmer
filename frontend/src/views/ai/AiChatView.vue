<script setup>
import { nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createConversation,
  getConversations,
  getConversation,
  sendMessage,
  sendMessageFetchStream
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
  const count = c.messageCount ?? c.message_count ?? 0
  const max = c.maxMessages ?? c.max_messages ?? 100
  if (count >= max) return false
  return true
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
        streamingAiMessageId.value = null
        streamingAiContent.value = ''
        nextTick().then(() => scrollToBottom())
      } else if (data.type === 'error') {
        // 错误处理
        throw new Error(data.error)
      }
    })
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
    ElMessage.error(e.message || '发送失败')
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

// 监听用户信息变化，自动同步封禁状态
watch(() => userStore.userInfo?.status, () => {
  syncUserStatus()
})

onMounted(() => {
  syncUserStatus()
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
                <span class="meta-text">消息：{{ c.messageCount ?? c.message_count ?? 0 }}/{{ c.maxMessages ?? c.max_messages ?? 100 }}</span>
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
      <!-- 顶部：只显示 AI 名字 glimmer -->
      <div class="detail-header">
        <div class="ai-name">✨ glimmer</div>
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
          <div class="message-content">{{ m.content }}</div>
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
  gap: 10px;
  padding: 12px 20px;
  background: #f5f7fa;
  border-top: 1px solid #dcdfe6;
}
</style>
