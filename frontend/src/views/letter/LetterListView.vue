<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getInbox, getSent } from '@/api/letter'

const router = useRouter()

const activeTab = ref('inbox')
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = reactive({ current: 1, size: 10 })

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

// 是否已回复
function isReplied(item) {
  return !!(item.isReplied ?? item.is_replied)
}

// 是否已读
function isRead(item) {
  return !!(item.isRead ?? item.is_read)
}

// 对方昵称（收件箱显示发送者，发件箱显示接收者）
function counterpart(item) {
  if (activeTab.value === 'inbox') {
    return item.senderNickname || item.sender_nickname || '匿名旅人'
  }
  return item.receiverNickname || item.receiver_nickname || '匿名旅人'
}

function counterpartLabel() {
  return activeTab.value === 'inbox' ? '来自' : '寄给'
}

function preview(content) {
  if (!content) return ''
  return content.length > 50 ? content.slice(0, 50) + '…' : content
}

async function fetchList() {
  loading.value = true
  try {
    const params = { page: page.current, size: page.size }
    const res = activeTab.value === 'inbox' ? await getInbox(params) : await getSent(params)
    const data = res.data
    list.value = pickList(data)
    total.value = pickTotal(data)
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleTabChange() {
  page.current = 1
  fetchList()
}

function handlePageChange(p) {
  page.current = p
  fetchList()
}

function goDetail(item) {
  router.push({ name: 'letterDetail', params: { id: item.id } })
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="letter-page">
    <div class="page-header">
      <h2 class="page-title">✉️ 信件</h2>
      <p class="page-subtitle">一封封温柔的来信，只属于收发双方</p>
    </div>

    <el-card shadow="never" class="list-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="收件箱" name="inbox" />
        <el-tab-pane label="发件箱" name="sent" />
      </el-tabs>

      <div v-loading="loading">
        <el-empty v-if="!loading && list.length === 0" description="暂无信件" />

        <ul v-else class="letter-list">
          <li
            v-for="item in list"
            :key="item.id"
            class="letter-item"
            :class="{ 'is-read': isRead(item), 'is-unread': !isRead(item) }"
            @click="goDetail(item)"
          >
            <div class="letter-main">
              <div class="letter-top">
                <span class="counterpart">{{ counterpartLabel() }}：{{ counterpart(item) }}</span>
                <el-tag
                  v-if="activeTab === 'inbox' && isReplied(item)"
                  size="small"
                  type="success"
                  effect="plain"
                >
                  已回复
                </el-tag>
                <el-tag
                  v-else-if="activeTab === 'inbox' && !isReplied(item)"
                  size="small"
                  type="warning"
                  effect="plain"
                >
                  待回复
                </el-tag>
              </div>
              <div class="letter-preview">{{ preview(item.content) }}</div>
              <div class="letter-time">{{ item.createdAt || item.created_at || '-' }}</div>
            </div>
            <span v-if="!isRead(item)" class="unread-dot" />
          </li>
        </ul>

        <div v-if="total > 0" class="pagination-wrap">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="page.current"
            :page-size="page.size"
            :total="total"
            @current-change="handlePageChange"
          />
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.letter-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-header {
  padding: 8px 4px;
}
.page-title {
  margin: 0 0 4px;
  font-size: 22px;
  color: #303133;
}
.page-subtitle {
  margin: 0;
  color: #909399;
  font-size: 13px;
}
.list-card {
  border-radius: 10px;
}
.letter-list {
  display: flex;
  flex-direction: column;
}
.letter-item {
  padding: 14px 12px;
  border-bottom: 1px solid #f0e6d2;
  cursor: pointer;
  transition: background 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.letter-item:last-child {
  border-bottom: none;
}
.letter-item:hover {
  background: #fef7ea;
}
.letter-item.is-unread .letter-top {
  font-weight: 700;
}
.unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
}
.letter-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.letter-top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.counterpart {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.letter-preview {
  font-size: 13px;
  color: #606266;
  word-break: break-word;
}
.letter-time {
  font-size: 12px;
  color: #909399;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
