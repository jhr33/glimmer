<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createFeedback, createAppeal, getMyFeedbacks, getMyAppealGroups } from '@/api/feedback'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()
const activeTab = ref('feedback')
const feedbackContent = ref('')
const appealContent = ref('')
const appealReportId = ref('')
const appealPunishmentId = ref('')
const submitting = ref(false)
const appealSubmitting = ref(false)
const lastSubmitAt = ref(0)
const FREQUENCY_LIMIT = 60 * 1000
const remainSeconds = computed(() => {
  if (!lastSubmitAt.value) return 0
  const diff = FREQUENCY_LIMIT - (Date.now() - lastSubmitAt.value)
  return diff > 0 ? Math.ceil(diff / 1000) : 0
})
const isFrequencyLimited = computed(() => remainSeconds.value > 0)
const isBanned = ref(false)

// ===== 意见信列表状态 =====
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = reactive({ current: 1, size: 10 })

// ===== 申诉分组列表状态 =====
const appealLoading = ref(false)
const appealGroups = ref([])
// 展开的分组 groupKey 集合
const expandedGroups = ref(new Set())

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

// ===== 意见信相关方法 =====
function isReplied(item) {
  return (item.status ?? item.state) === 'replied'
}
function statusLabel(item) {
  return isReplied(item) ? '已回复' : '待回复'
}
function statusType(item) {
  return isReplied(item) ? 'success' : 'warning'
}
function replyContent(item) {
  return item.reply ?? item.replyContent ?? item.reply_content ?? ''
}
function createdAt(item) {
  return item.createdAt || item.created_at || '-'
}
function repliedAt(item) {
  return item.repliedAt || item.replied_at || '-'
}
/** 处罚类型对应的标签颜色 */
function punishmentTagType(type) {
  switch ((type || '').toUpperCase()) {
    case 'BAN': return 'danger'
    case 'MUTE_7D': return 'danger'
    case 'MUTE_24H': return 'warning'
    case 'WARNING': return 'info'
    default: return 'info'
  }
}
/** 处罚状态对应的标签颜色 */
function punishmentStatusTagType(status) {
  switch ((status || '').toUpperCase()) {
    case 'ACTIVE': return 'danger'
    case 'REVOKED': return 'success'
    case 'EXPIRED': return 'info'
    default: return 'info'
  }
}

async function fetchList() {
  // 申诉 Tab 走分组接口
  if (activeTab.value === 'appeal') {
    await fetchAppealGroups()
    return
  }
  // 意见 Tab 走分页接口
  loading.value = true
  try {
    const params = { page: page.current, size: page.size }
    if (activeTab.value) {
      params.type = activeTab.value
    }
    const res = await getMyFeedbacks(params)
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

function handlePageChange(p) {
  page.current = p
  fetchList()
}

// ===== 申诉分组相关方法 =====
async function fetchAppealGroups() {
  appealLoading.value = true
  try {
    const res = await getMyAppealGroups()
    appealGroups.value = pickList(res.data)
  } catch (e) {
    appealGroups.value = []
  } finally {
    appealLoading.value = false
  }
}

function groupStatusType(group) {
  return group.latestStatus === 'replied' ? 'success' : 'warning'
}
function groupStatusLabel(group) {
  return group.latestStatus === 'replied' ? '已回复' : '待回复'
}
function toggleGroup(group) {
  const key = group.groupKey
  if (expandedGroups.value.has(key)) {
    expandedGroups.value.delete(key)
  } else {
    expandedGroups.value.add(key)
  }
  // 触发响应式更新
  expandedGroups.value = new Set(expandedGroups.value)
}
function isGroupExpanded(group) {
  return expandedGroups.value.has(group.groupKey)
}

function targetTypeLabel(type) {
  const map = {
    drift_bottle: '漂流瓶',
    bottle_reply: '漂流瓶回复',
    letter: '私信',
    campfire_message: '篝火消息',
  }
  return map[type] || type || '内容'
}

// ===== 提交相关 =====
async function handleFeedbackSubmit() {
  if (isBanned.value) {
    ElMessage.error('账号已被封禁，无法提交')
    return
  }
  if (isFrequencyLimited.value) {
    ElMessage.warning(`提交过于频繁，请 ${remainSeconds.value} 秒后再试`)
    return
  }
  if (!feedbackContent.value.trim()) {
    ElMessage.warning('请填写意见内容')
    return
  }
  submitting.value = true
  try {
    await createFeedback({ content: feedbackContent.value.trim() })
    ElMessage.success('意见已提交')
    feedbackContent.value = ''
    lastSubmitAt.value = Date.now()
    page.current = 1
    await fetchList()
  } catch (e) {
    if (e?.code === 4015 || e?.code === 4019) {
      isBanned.value = true
      ElMessage.error('账号已被封禁，无法提交意见')
      userStore.fetchUserInfo().catch(() => {})
    }
  } finally {
    submitting.value = false
  }
}

async function handleAppealSubmit() {
  if (!appealContent.value.trim()) {
    ElMessage.warning('请填写申诉内容')
    return
  }
  appealSubmitting.value = true
  try {
    const data = { content: appealContent.value.trim() }
    if (appealReportId.value) {
      data.reportId = appealReportId.value
    }
    if (appealPunishmentId.value) {
      data.punishmentId = appealPunishmentId.value
    }
    await createAppeal(data)
    ElMessage.success('申诉已提交')
    setTimeout(() => {
      window.history.back()
    }, 1500)
  } catch (e) {
    if (e?.code === 409) {
      ElMessage.error(e?.message || '申诉失败')
    }
  } finally {
    appealSubmitting.value = false
  }
}

// 监听用户信息变化，自动同步封禁状态
watch(
  () => userStore.userInfo?.status,
  () => {
    isBanned.value = userStore.userInfo?.status === 'banned'
  }
)

onMounted(() => {
  const reportId = route.query.reportId
  const punishmentId = route.query.punishmentId
  if (reportId) {
    appealReportId.value = reportId
  }
  if (punishmentId) {
    appealPunishmentId.value = punishmentId
  }
  if (reportId || punishmentId) {
    activeTab.value = 'appeal'
  }
  isBanned.value = userStore.userInfo?.status === 'banned'
  fetchList()
})
</script>

<template>
  <div class="feedback-page">
    <div class="page-header">
      <h2 class="page-title">📬 意见与申诉</h2>
      <p class="page-subtitle">你的每一条建议，都会让 glimmer 更温暖</p>
    </div>

    <!-- 标签切换 -->
    <div class="tabs-wrap">
      <el-tabs
        v-model="activeTab"
        @tab-change="
          () => {
            page.current = 1
            expandedGroups.clear()
            fetchList()
          }
        "
      >
        <el-tab-pane label="意见反馈" name="feedback" />
        <el-tab-pane label="申诉" name="appeal" />
      </el-tabs>
    </div>

    <!-- 提交意见区 -->
    <el-card v-if="activeTab === 'feedback'" shadow="never" class="submit-card">
      <h3 class="section-title">提交意见</h3>
      <el-input
        v-model="feedbackContent"
        type="textarea"
        :rows="5"
        maxlength="500"
        show-word-limit
        placeholder="写下你的建议、问题或感受…（最长 500 字）"
        :disabled="isBanned"
      />
      <div class="submit-actions">
        <span v-if="isFrequencyLimited" class="freq-tip">
          提交过于频繁，请 {{ remainSeconds }} 秒后再试
        </span>
        <span v-else-if="isBanned" class="banned-tip">账号已被封禁，无法提交</span>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="isFrequencyLimited || isBanned"
          @click="handleFeedbackSubmit"
        >
          提交
        </el-button>
      </div>
    </el-card>

    <!-- 提交申诉区 -->
    <el-card v-if="activeTab === 'appeal'" shadow="never" class="submit-card">
      <h3 class="section-title">提交申诉</h3>
      <div v-if="appealPunishmentId || appealReportId" class="appeal-hint">
        <el-tag type="info" effect="plain">
          <template v-if="appealPunishmentId">正在为处罚单 #{{ appealPunishmentId }} 提交申诉</template>
          <template v-else>正在为举报 #{{ appealReportId }} 提交申诉</template>
        </el-tag>
      </div>
      <el-input
        v-model="appealContent"
        type="textarea"
        :rows="5"
        maxlength="500"
        show-word-limit
        placeholder="请详细描述你的申诉理由…（最长 500 字）"
      />
      <div class="appeal-rules">
        <ul>
          <li>一条被举报信息最多可以申诉三次</li>
          <li>一个账户一天最多提交七次申诉</li>
          <li>申诉提交后，管理员将重新进行审核</li>
        </ul>
      </div>
      <div class="submit-actions">
        <el-button type="primary" :loading="appealSubmitting" @click="handleAppealSubmit">
          提交申诉
        </el-button>
      </div>
    </el-card>

    <!-- 意见信列表 -->
    <el-card
      v-if="activeTab === 'feedback'"
      v-loading="loading"
      shadow="never"
      class="list-card"
    >
      <template #header>
        <span class="card-header-title">我的意见信</span>
      </template>

      <el-empty v-if="!loading && list.length === 0" description="还没有提交过意见" />

      <ul v-else class="feedback-list">
        <li v-for="item in list" :key="item.id" class="feedback-item">
          <div class="item-top">
            <el-tag size="small" :type="statusType(item)" effect="plain">
              {{ statusLabel(item) }}
            </el-tag>
            <span class="item-time">提交时间：{{ createdAt(item) }}</span>
          </div>
          <div class="item-content">{{ item.content }}</div>
          <div v-if="isReplied(item)" class="item-reply">
            <div class="reply-label">回复：</div>
            <div class="reply-body">{{ replyContent(item) }}</div>
            <div class="reply-time">回复时间：{{ repliedAt(item) }}</div>
          </div>
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

    <!-- 申诉分组列表 -->
    <el-card
      v-if="activeTab === 'appeal'"
      v-loading="appealLoading"
      shadow="never"
      class="list-card"
    >
      <template #header>
        <span class="card-header-title">我的申诉</span>
      </template>

      <el-empty
        v-if="!appealLoading && appealGroups.length === 0"
        description="还没有提交过申诉"
      />

      <ul v-else class="appeal-group-list">
        <li
          v-for="group in appealGroups"
          :key="group.groupKey"
          class="appeal-group-item"
        >
          <!-- 分组概要 -->
          <div class="group-summary">
            <div class="group-top">
              <el-tag size="small" :type="groupStatusType(group)" effect="plain">
                {{ groupStatusLabel(group) }}
              </el-tag>
              <el-tag size="small" type="info" effect="plain">
                {{ targetTypeLabel(group.targetType) }}
              </el-tag>
              <span v-if="group.location" class="group-location">
                📍 {{ group.location }}
              </span>
              <span class="group-appeal-count">
                共 {{ group.appealCount }} 次申诉
              </span>
            </div>
            <div class="group-reported">
              <span class="reported-label">被举报内容：</span>
              <span class="reported-content">{{ group.reportedContent }}</span>
            </div>
            <!-- 原始处罚信息 -->
            <div v-if="group.punishment" class="group-punishment">
              <span class="punishment-label">处罚信息：</span>
              <el-tag size="small" :type="punishmentTagType(group.punishment.type)" effect="dark">
                {{ group.punishment.typeDescription || group.punishment.type }}
              </el-tag>
              <el-tag size="small" :type="punishmentStatusTagType(group.punishment.status)" effect="plain">
                {{ group.punishment.statusDescription || group.punishment.status }}
              </el-tag>
              <span v-if="group.punishment.reason" class="punishment-reason">
                {{ group.punishment.reason }}
              </span>
              <span class="punishment-time">
                {{ group.punishment.startAt || '-' }}
                <template v-if="group.punishment.endAt"> ~ {{ group.punishment.endAt }}</template>
                <template v-else> 起（永久）</template>
              </span>
            </div>
            <div class="group-meta">
              <span class="meta-time">最近申诉：{{ group.latestCreatedAt || '-' }}</span>
              <el-button
                type="primary"
                size="small"
                text
                @click="toggleGroup(group)"
              >
                {{ isGroupExpanded(group) ? '收起详情' : '查看详情' }}
                <el-icon class="toggle-icon" :class="{ expanded: isGroupExpanded(group) }">
                  ▼
                </el-icon>
              </el-button>
            </div>
          </div>

          <!-- 展开的申诉明细 -->
          <div v-if="isGroupExpanded(group)" class="group-detail">
            <div
              v-for="(appeal, idx) in group.appeals"
              :key="appeal.id"
              class="appeal-record"
            >
              <div class="appeal-record-header">
                <span class="appeal-index">第 {{ idx + 1 }} 次申诉</span>
                <el-tag
                  size="small"
                  :type="isReplied(appeal) ? 'success' : 'warning'"
                  effect="plain"
                >
                  {{ statusLabel(appeal) }}
                </el-tag>
                <span class="appeal-time">{{ createdAt(appeal) }}</span>
              </div>
              <div class="appeal-content">{{ appeal.content }}</div>
              <div v-if="isReplied(appeal)" class="appeal-reply">
                <div class="reply-label">审核回复：</div>
                <div class="reply-body">{{ replyContent(appeal) }}</div>
                <div class="reply-time">回复时间：{{ repliedAt(appeal) }}</div>
              </div>
              <!-- 申诉后处罚结果 -->
              <div v-if="appeal.punishment" class="appeal-punishment-result">
                <span class="result-label">处罚结果：</span>
                <el-tag size="small" :type="punishmentTagType(appeal.punishment.type)" effect="dark">
                  {{ appeal.punishment.typeDescription || appeal.punishment.type }}
                </el-tag>
                <el-tag size="small" :type="punishmentStatusTagType(appeal.punishment.status)" effect="plain">
                  {{ appeal.punishment.statusDescription || appeal.punishment.status }}
                </el-tag>
              </div>
            </div>
          </div>
        </li>
      </ul>
    </el-card>
  </div>
</template>

<style scoped>
.feedback-page {
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
.tabs-wrap {
  margin-bottom: 8px;
}
.submit-card,
.list-card {
  border-radius: 10px;
}
.section-title {
  margin: 0 0 10px;
  font-size: 16px;
  color: #303133;
}
.appeal-hint {
  margin-bottom: 10px;
}
.appeal-rules {
  margin: 10px 0;
  padding: 10px 12px;
  background: #fef7ea;
  border-radius: 8px;
}
.appeal-rules ul {
  margin: 0;
  padding-left: 20px;
}
.appeal-rules li {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.appeal-rules li:last-child {
  margin-bottom: 0;
}
.submit-actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}
.freq-tip {
  font-size: 12px;
  color: #e89a1a;
}
.banned-tip {
  font-size: 12px;
  color: #f56c6c;
}
.card-header-title {
  font-weight: 600;
  color: #303133;
}

/* ===== 意见信列表 ===== */
.feedback-list {
  display: flex;
  flex-direction: column;
}
.feedback-item {
  padding: 14px 12px;
  border-bottom: 1px solid #f0e6d2;
}
.feedback-item:last-child {
  border-bottom: none;
}
.item-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.item-time {
  font-size: 12px;
  color: #909399;
}
.item-content {
  font-size: 14px;
  color: #303133;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  background: #fef7ea;
  padding: 12px;
  border-radius: 8px;
}
.item-reply {
  margin-top: 10px;
  padding: 12px;
  background: #f0f9eb;
  border-radius: 8px;
  border-left: 3px solid #67c23a;
}
.reply-label {
  font-size: 12px;
  color: #67c23a;
  font-weight: 600;
  margin-bottom: 4px;
}
.reply-body {
  font-size: 14px;
  color: #303133;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.reply-time {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

/* ===== 申诉分组列表 ===== */
.appeal-group-list {
  display: flex;
  flex-direction: column;
}
.appeal-group-item {
  padding: 0;
  border: 1px solid #f0e6d2;
  border-radius: 10px;
  margin-bottom: 12px;
  overflow: hidden;
  background: #fffdf7;
}
.appeal-group-item:last-child {
  margin-bottom: 0;
}
.group-summary {
  padding: 14px 16px;
}
.group-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.group-location {
  font-size: 12px;
  color: #909399;
}
.group-appeal-count {
  font-size: 12px;
  color: #e89a1a;
  font-weight: 600;
  margin-left: auto;
}
.group-reported {
  padding: 10px 12px;
  background: #fef0f0;
  border-radius: 8px;
  border-left: 3px solid #f56c6c;
  margin-bottom: 10px;
}
.reported-label {
  font-size: 12px;
  color: #f56c6c;
  font-weight: 600;
}
.reported-content {
  font-size: 13px;
  color: #303133;
  word-break: break-word;
}
/* 原始处罚信息 */
.group-punishment {
  padding: 10px 12px;
  background: #fdf6ec;
  border-radius: 8px;
  border-left: 3px solid #e6a23c;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.punishment-label {
  font-size: 12px;
  color: #e6a23c;
  font-weight: 600;
  white-space: nowrap;
}
.punishment-reason {
  font-size: 12px;
  color: #606266;
  word-break: break-word;
}
.punishment-time {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}
.group-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.meta-time {
  font-size: 12px;
  color: #909399;
}
.toggle-icon {
  font-size: 10px;
  margin-left: 2px;
  transition: transform 0.2s ease;
  display: inline-block;
}
.toggle-icon.expanded {
  transform: rotate(180deg);
}

/* 分组详情展开 */
.group-detail {
  padding: 0 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.appeal-record {
  padding: 12px;
  background: #fff;
  border: 1px solid #f0e6d2;
  border-radius: 8px;
}
.appeal-record-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.appeal-index {
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
}
.appeal-time {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}
.appeal-content {
  font-size: 14px;
  color: #303133;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  background: #fef7ea;
  padding: 10px 12px;
  border-radius: 8px;
}
.appeal-reply {
  margin-top: 8px;
  padding: 10px 12px;
  background: #f0f9eb;
  border-radius: 8px;
  border-left: 3px solid #67c23a;
}
/* 申诉后处罚结果 */
.appeal-punishment-result {
  margin-top: 8px;
  padding: 8px 12px;
  background: #f4f4f5;
  border-radius: 8px;
  border-left: 3px solid #909399;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.result-label {
  font-size: 12px;
  color: #909399;
  font-weight: 600;
  white-space: nowrap;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
