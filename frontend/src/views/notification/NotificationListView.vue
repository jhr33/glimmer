<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getNotifications, markRead, markAllRead } from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'
import { useRouter } from 'vue-router'

const notificationStore = useNotificationStore()
const router = useRouter()

const loading = ref(false)
const markAllLoading = ref(false)
const list = ref([])
const total = ref(0)
const page = reactive({
  current: 1,
  size: 10
})

// 通知类型 → 标签/颜色映射（开发文档 5.4.9 节）
const typeMap = {
  report_result: { label: '举报结果', type: 'danger' },
  feedback_reply: { label: '反馈回复', type: 'primary' },
  announcement: { label: '公告', type: 'success' },
  system: { label: '系统', type: 'info' },
  bottle_reply: { label: '漂流瓶回复', type: 'warning' },
  bottle_thank: { label: '感谢', type: 'success' }
}

function typeMeta(t) {
  return typeMap[t] || { label: t || '通知', type: 'info' }
}

function isRead(item) {
  return !!(item.isRead ?? item.is_read)
}

// 兼容多种分页响应结构
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

async function fetchList() {
  loading.value = true
  try {
    const res = await getNotifications({ page: page.current, size: page.size })
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

async function handleReadOne(item) {
  if (isRead(item)) return
  try {
    await markRead(item.id)
    // 更新本地状态
    item.isRead = 1
    item.is_read = 1
    // 同步 store 未读数
    await notificationStore.fetchUnreadCount()
  } catch (e) {
    // 错误已由拦截器统一提示
  }
}

async function handleMarkAll() {
  if (markAllLoading.value) return
  markAllLoading.value = true
  try {
    await markAllRead()
    list.value.forEach((n) => {
      n.isRead = 1
      n.is_read = 1
    })
    notificationStore.clear()
    ElMessage.success('已全部标记为已读')
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    markAllLoading.value = false
  }
}

function handlePageChange(p) {
  page.current = p
  fetchList()
}

const hasUnread = computed(() => list.value.some((n) => !isRead(n)))

function canAppeal(item) {
  if (item.type !== 'report_result') return false
  const title = item.title || ''
  if (title.includes('您的内容被举报')) {
    return true
  }
  if (!item.extra) return false
  try {
    const extra = typeof item.extra === 'string' ? JSON.parse(item.extra) : item.extra
    return extra?.result === 'approved'
  } catch {
    return false
  }
}

function getReportId(item) {
  if (!item.extra) return null
  try {
    const extra = typeof item.extra === 'string' ? JSON.parse(item.extra) : item.extra
    return extra?.reportId
  } catch {
    return null
  }
}

function handleAppeal(item) {
  const reportId = getReportId(item)
  if (reportId) {
    router.push(`/feedback?reportId=${reportId}`)
  } else {
    router.push('/feedback?type=appeal')
  }
}

onMounted(() => {
  fetchList()
  // 进入页面同步一次顶部红点
  notificationStore.fetchUnreadCount()
})
</script>

<template>
  <div class="notification-page">
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">🔔 通知中心</h2>
        <p class="page-subtitle">你的消息，集中查看</p>
      </div>
      <el-button
        type="primary"
        :loading="markAllLoading"
        :disabled="!hasUnread"
        @click="handleMarkAll"
      >
        全部已读
      </el-button>
    </div>

    <el-card v-loading="loading" shadow="never" class="list-card">
      <el-empty v-if="!loading && list.length === 0" description="暂无通知" />

      <ul v-else class="notification-list">
        <li
          v-for="item in list"
          :key="item.id"
          class="notification-item"
          :class="{ 'is-read': isRead(item), 'is-unread': !isRead(item) }"
          @click="handleReadOne(item)"
        >
          <el-tag size="small" :type="typeMeta(item.type).type" effect="light">
            {{ typeMeta(item.type).label }}
          </el-tag>
          <div class="item-body">
            <div class="item-title">{{ item.title || '无标题' }}</div>
            <div v-if="item.content" class="item-content">{{ item.content }}</div>
            <div class="item-time">{{ item.createdAt || item.created_at || '-' }}</div>
            <div v-if="canAppeal(item)" class="appeal-btn-wrap">
              <el-button size="small" type="primary" @click.stop="handleAppeal(item)">
                申诉
              </el-button>
            </div>
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
    </el-card>
  </div>
</template>

<style scoped>
.notification-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
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
.notification-list {
  display: flex;
  flex-direction: column;
}
.notification-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 12px;
  border-bottom: 1px solid #f0e6d2;
  cursor: pointer;
  transition: background 0.2s ease;
  position: relative;
}
.notification-item:last-child {
  border-bottom: none;
}
.notification-item:hover {
  background: #fef7ea;
}
.notification-item.is-unread .item-title {
  font-weight: 700;
  color: #303133;
}
.notification-item.is-read {
  opacity: 0.65;
}
.notification-item.is-read .item-title {
  font-weight: 400;
  color: #606266;
}
.item-body {
  flex: 1;
  min-width: 0;
}
.item-title {
  font-size: 15px;
  color: #303133;
  margin-bottom: 4px;
  word-break: break-word;
}
.item-content {
  font-size: 13px;
  color: #606266;
  margin-bottom: 4px;
  word-break: break-word;
  white-space: pre-wrap;
}
.item-time {
  font-size: 12px;
  color: #909399;
}
.unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
  margin-top: 6px;
}
.appeal-btn-wrap {
  margin-top: 8px;
}
.appeal-btn-wrap .el-button {
  padding: 2px 10px;
  font-size: 12px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
