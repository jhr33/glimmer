<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getNotifications, markRead, markAllRead } from '@/api/notification'
import { checkAppealEligibility } from '@/api/feedback'
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

// 类型筛选标签
const typeTabs = [
  { key: '', label: '全部', type: 'primary' },
  { key: 'bottle_thank', label: '感谢', type: 'success' },
  { key: 'bottle_reply', label: '漂流瓶回复', type: 'warning' },
  { key: 'report_result', label: '举报通知', type: 'danger' },
  { key: 'appeal_result', label: '申诉审核', type: 'warning' },
  { key: 'feedback_reply', label: '反馈回复', type: 'primary' },
  { key: 'system', label: '系统', type: 'info' }
]
const activeType = ref('')

// 通知类型 → 标签/颜色映射（开发文档 5.4.9 节）
const typeMap = {
  report_result: { label: '举报结果', type: 'danger' },
  feedback_reply: { label: '反馈回复', type: 'primary' },
  announcement: { label: '公告', type: 'success' },
  system: { label: '系统', type: 'info' },
  bottle_reply: { label: '漂流瓶回复', type: 'warning' },
  bottle_thank: { label: '感谢', type: 'success' },
  appeal_result: { label: '申诉审核', type: 'warning' }
}

function typeMeta(t) {
  return typeMap[t] || { label: t || '通知', type: 'info' }
}

function isRead(item) {
  return !!(item.isRead ?? item.is_read)
}

function getTypeUnread(key) {
  return notificationStore.getTypeUnread(key)
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
    const params = { page: page.current, size: page.size }
    if (activeType.value) {
      params.type = activeType.value
    }
    const res = await getNotifications(params)
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

function handleTypeChange(key) {
  activeType.value = key
  page.current = 1
  fetchList()
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
  // 申诉审核结果通知包含"已到达上限"时不可申诉
  const content = item.content || ''
  if (content.includes('已到达上限')) {
    return false
  }
  
  // 系统封禁通知可申诉（sourceType=auto_ban）
  if (item.type === 'system') {
    if (!item.extra) return false
    try {
      const extra = typeof item.extra === 'string' ? JSON.parse(item.extra) : item.extra
      return extra?.sourceType === 'auto_ban'
    } catch {
      return false
    }
  }
  
  // 举报结果通知可申诉
  if (item.type === 'report_result') {
    const title = item.title || ''
    if (title.includes('您的内容被举报')) {
      if (!item.extra) return false
      try {
        const extra = typeof item.extra === 'string' ? JSON.parse(item.extra) : item.extra
        // 举报驳回（result=rejected）时不显示申诉按钮
        return extra?.result === 'approved'
      } catch {
        return false
      }
    }
    return false
  }
  // 申诉审核结果通知：内容包含"可点击此处继续申诉"时可继续申诉
  if (item.type === 'appeal_result') {
    return content.includes('可点击此处继续申诉')
  }
  return false
}

function canHandlePunishment(item) {
  // 管理员收到的系统封禁通知，末尾显示"前往处理"
  if (item.type === 'system') {
    if (!item.extra) return false
    try {
      const extra = typeof item.extra === 'string' ? JSON.parse(item.extra) : item.extra
      return extra?.action === 'revoke_punishment'
    } catch {
      return false
    }
  }
  return false
}

function formatContent(content) {
  if (!content) return ''
  let result = content
  // 将"可点击此处继续申诉"替换为可点击的链接
  result = result.replace('可点击此处继续申诉', '<span class="appeal-link">可点击此处继续申诉</span>')
  // 将"点击前往处理"替换为可点击的链接
  result = result.replace('点击前往处理可撤销该处罚', '<span class="handle-link">点击前往处理可撤销该处罚</span>')
  return result
}

function getAppealParams(item) {
  const params = new URLSearchParams()
  if (!item.extra) return params
  try {
    const extra = typeof item.extra === 'string' ? JSON.parse(item.extra) : item.extra
    if (extra?.punishmentId) {
      params.set('punishmentId', extra.punishmentId)
    }
    if (extra?.reportId) {
      params.set('reportId', extra.reportId)
    }
    return params
  } catch {
    return params
  }
}

async function handleAppeal(item) {
  // 点击申诉时先标记通知为已读
  if (!isRead(item)) {
    handleReadOne(item)
  }
  
  // 获取punishmentId检查申诉资格
  const params = getAppealParams(item)
  const punishmentId = params.get('punishmentId')
  
  if (punishmentId) {
    try {
      const res = await checkAppealEligibility(punishmentId)
      const checkResult = res.data
      if (!checkResult.canAppeal) {
        ElMessage.warning(checkResult.reason || '无法申诉')
        return
      }
    } catch (e) {
      // 检查失败时继续跳转
    }
  }
  
  const query = params.toString()
  if (query) {
    router.push(`/feedback?${query}`)
  } else {
    router.push('/feedback?type=appeal')
  }
}

function handleContentClick(e, item) {
  // 如果点击的是申诉链接，触发申诉跳转
  if (e.target.classList.contains('appeal-link')) {
    e.stopPropagation()
    e.preventDefault()
    handleAppeal(item)
  }
  // 如果点击的是前往处理链接，跳转到审核页面
  if (e.target.classList.contains('handle-link')) {
    e.stopPropagation()
    e.preventDefault()
    handlePunishment(item)
  }
}

async function handlePunishment(item) {
  // 点击前往处理时先标记通知为已读
  if (!isRead(item)) {
    handleReadOne(item)
  }
  
  // 获取extra中的punishmentId跳转到审核页面
  if (!item.extra) return
  try {
    const extra = typeof item.extra === 'string' ? JSON.parse(item.extra) : item.extra
    if (extra?.punishmentId) {
      router.push(`/admin/reports?tab=punishments&highlight=${extra.punishmentId}`)
    } else {
      router.push('/admin/reports?tab=punishments')
    }
  } catch {
    router.push('/admin/reports?tab=punishments')
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

    <!-- 类型筛选标签栏 -->
    <div class="type-tabs">
      <el-button
        v-for="tab in typeTabs"
        :key="tab.key"
        :type="activeType === tab.key ? tab.type : 'default'"
        :class="{ active: activeType === tab.key }"
        @click="handleTypeChange(tab.key)"
      >
        {{ tab.label }}
        <el-badge
          v-if="getTypeUnread(tab.key) > 0"
          :value="getTypeUnread(tab.key)"
          :max="99"
          class="type-badge"
        />
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
            <div v-if="item.content" class="item-content" v-html="formatContent(item.content)" @click="(e) => handleContentClick(e, item)"></div>
            <div class="item-time">{{ item.createdAt || item.created_at || '-' }}</div>
            <div v-if="canAppeal(item) || canHandlePunishment(item)" class="action-btns-wrap">
              <el-button v-if="canAppeal(item)" size="small" type="primary" @click.stop="handleAppeal(item)">
                申诉
              </el-button>
              <el-button v-if="canHandlePunishment(item)" size="small" type="warning" @click.stop="handlePunishment(item)">
                前往处理
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
.type-tabs {
  display: flex;
  gap: 8px;
  padding: 4px;
}
.type-tabs .el-button {
  border-radius: 20px;
  padding: 6px 16px;
  position: relative;
}
.type-tabs .type-badge {
  position: absolute;
  top: -4px;
  right: -4px;
}
.type-tabs .el-button.active {
  font-weight: 600;
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
.appeal-link {
  color: #409eff;
  cursor: pointer;
  text-decoration: underline;
}
.appeal-link:hover {
  color: #66b1ff;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
