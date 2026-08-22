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

// 检索条件
const keyword = ref('')
const timeRange = ref('') // '' 全部 / today / week / month / custom
const dateRange = ref([]) // [startDate, endDate]

function formatYMD(d) {
  if (!d) return ''
  const dt = new Date(d)
  const y = dt.getFullYear()
  const m = String(dt.getMonth() + 1).padStart(2, '0')
  const dd = String(dt.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

function buildParams() {
  const params = { page: page.current, size: page.size }
  if (keyword.value?.trim()) {
    params.keyword = keyword.value.trim()
  }
  if (timeRange.value) {
    params.timeRange = timeRange.value
    if (timeRange.value === 'custom' && Array.isArray(dateRange.value) && dateRange.value.length === 2) {
      params.startDate = formatYMD(dateRange.value[0])
      params.endDate = formatYMD(dateRange.value[1])
    }
  }
  return params
}

function applySearch() {
  page.current = 1
  fetchList()
}
function resetSearch() {
  keyword.value = ''
  timeRange.value = ''
  dateRange.value = []
  page.current = 1
  fetchList()
}

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
    const params = buildParams()
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
      <!-- 搜索栏：关键词 + 时间范围 -->
      <div class="search-bar">
        <el-input
          v-model="keyword"
          placeholder="搜索信件内容关键词"
          clearable
          class="search-input"
          @keyup.enter="applySearch"
        >
          <template #prefix>🔍</template>
        </el-input>
        <el-select
          v-model="timeRange"
          placeholder="按时间筛选"
          clearable
          class="time-select"
          @change="applySearch"
        >
          <el-option label="全部时间" value="" />
          <el-option label="今天" value="today" />
          <el-option label="近7天" value="week" />
          <el-option label="近30天" value="month" />
          <el-option label="自定义" value="custom" />
        </el-select>
        <el-date-picker
          v-if="timeRange === 'custom'"
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          class="date-range"
          @change="applySearch"
        />
        <el-button type="primary" @click="applySearch">搜索</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="收件箱" name="inbox" />
        <el-tab-pane label="发件箱" name="sent" />
      </el-tabs>

      <div v-loading="loading">
        <el-empty v-if="!loading && list.length === 0" description="暂无信件或无匹配结果" />

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
/* 检索栏 */
.search-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
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
  font-family: 'LXGW WenKai', 'KaiTi', '楷体', 'STKaiti', '华文楷体', serif;
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
