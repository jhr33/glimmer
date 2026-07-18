<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminGetFeedbacks, adminReplyFeedback, adminGetAppeals, adminReviewAppeal } from '@/api/feedback'

const activeTab = ref('feedback') // feedback / appeal

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = reactive({ current: 1, size: 10 })
const statusFilter = ref('') // '' 全部 / pending / replied

// 回复弹窗（意见反馈）
const replyVisible = ref(false)
const replyLoading = ref(false)
const current = ref(null)
const replyContent = ref('')

// 审核弹窗（申诉）
const reviewVisible = ref(false)
const reviewLoading = ref(false)
const appealDetail = ref(null)
const appealDetailLoading = ref(false)
const reviewForm = reactive({
  result: 'approved', // approved / rejected
  newPenaltyType: '', // null/warning/mute_24h/mute_7d/ban
  reply: ''
})

const penaltyTypeOptions = [
  { value: '', label: '解除处罚' },
  { value: 'warning', label: '警告处理' },
  { value: 'mute_24h', label: '禁言24小时' },
  { value: 'mute_7d', label: '禁言7天' },
  { value: 'ban', label: '永久封禁' }
]

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

function isReplied(item) {
  return (item.status ?? item.state) === 'replied'
}

function statusLabel(s) {
  return s === 'replied' ? '已回复' : s === 'pending' ? '待回复' : s || '-'
}

function statusType(s) {
  return s === 'replied' ? 'success' : s === 'pending' ? 'warning' : 'info'
}

function submitterLabel(item) {
  return item.userNickname || item.user_nickname || item.user?.nickname || item.user?.anonymousName || `用户#${item.userId ?? item.user_id ?? '-'}`
}

function replyText(item) {
  return item.reply ?? item.replyContent ?? item.reply_content ?? ''
}

function createdAt(item) {
  return item.createdAt || item.created_at || '-'
}

function repliedAt(item) {
  return item.repliedAt || item.replied_at || '-'
}

async function fetchList() {
  loading.value = true
  try {
    const params = { page: page.current, size: page.size }
    if (statusFilter.value) {
      params.status = statusFilter.value
    }
    const res = activeTab.value === 'feedback' 
      ? await adminGetFeedbacks(params)
      : await adminGetAppeals(params)
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

function handleTabChange(tab) {
  activeTab.value = tab
  page.current = 1
  statusFilter.value = ''
  fetchList()
}

async function openReview(item) {
  appealDetail.value = null
  reviewForm.result = 'approved'
  reviewForm.newPenaltyType = ''
  reviewForm.reply = ''
  reviewVisible.value = true
  appealDetailLoading.value = true
  try {
    appealDetail.value = item
  } catch (e) {
    appealDetail.value = item
  } finally {
    appealDetailLoading.value = false
  }
}

async function handleReviewSubmit() {
  if (!appealDetail.value?.id) return
  if (!reviewForm.result) {
    ElMessage.warning('请选择审核结果')
    return
  }
  reviewLoading.value = true
  try {
    await adminReviewAppeal(appealDetail.value.id, {
      result: reviewForm.result,
      reply: reviewForm.reply.trim(),
      newPenaltyType: reviewForm.newPenaltyType || null
    })
    ElMessage.success('审核已提交')
    reviewVisible.value = false
    await fetchList()
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    reviewLoading.value = false
  }
}

function reviewResultLabel(r) {
  if (r === 'approved') return '申诉成功'
  if (r === 'rejected') return '申诉失败'
  return '-'
}

function handleFilterChange() {
  page.current = 1
  fetchList()
}

function handlePageChange(p) {
  page.current = p
  fetchList()
}

function openReply(item) {
  current.value = item
  replyContent.value = ''
  replyVisible.value = true
}

async function handleReplySubmit() {
  if (!current.value?.id) return
  if (!replyContent.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  replyLoading.value = true
  try {
    await adminReplyFeedback(current.value.id, { reply: replyContent.value.trim() })
    ElMessage.success('回复已提交')
    replyVisible.value = false
    await fetchList()
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    replyLoading.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="admin-feedback-page">
    <div class="page-header">
      <h2 class="page-title">意见与申诉管理</h2>
      <div class="header-right">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange" class="header-tabs">
          <el-tab-pane label="意见反馈" name="feedback" />
          <el-tab-pane label="申诉处理" name="appeal" />
        </el-tabs>
        <div class="filter-bar">
          <span class="filter-label">状态：</span>
          <el-select
            v-model="statusFilter"
            placeholder="全部"
            clearable
            style="width: 140px"
            @change="handleFilterChange"
          >
            <el-option label="全部" value="" />
            <el-option label="待处理" value="pending" />
            <el-option label="已处理" value="replied" />
          </el-select>
        </div>
      </div>
    </div>

    <el-card v-loading="loading" shadow="never" class="table-card">
      <el-table :data="list" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="提交者" min-width="120">
          <template #default="{ row }">{{ submitterLabel(row) }}</template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'appeal'" label="关联举报ID" width="120">
          <template #default="{ row }">{{ row.reportId ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="内容" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'appeal'" label="审核结果" width="110">
          <template #default="{ row }">
            <el-tag
              v-if="row.status === 'replied'"
              size="small"
              :type="row.result === 'approved' ? 'success' : 'danger'"
              effect="plain"
            >
              {{ reviewResultLabel(row.result) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="回复内容" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ replyText(row) || '-' }}</template>
        </el-table-column>
        <el-table-column label="回复时间" min-width="160">
          <template #default="{ row }">{{ isReplied(row) ? repliedAt(row) : '-' }}</template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="160">
          <template #default="{ row }">{{ createdAt(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <template v-if="activeTab === 'feedback'">
              <el-button
                v-if="!isReplied(row)"
                size="small"
                type="primary"
                link
                @click="openReply(row)"
              >
                回复
              </el-button>
              <span v-else class="text-muted">已回复</span>
            </template>
            <template v-else>
              <el-button
                v-if="!isReplied(row)"
                size="small"
                type="primary"
                link
                @click="openReview(row)"
              >
                审核
              </el-button>
              <span v-else class="text-muted">已审核</span>
            </template>
          </template>
        </el-table-column>
      </el-table>

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

    <!-- 回复弹窗 -->
    <el-dialog
      v-model="replyVisible"
      title="回复意见信"
      width="560px"
      destroy-on-close
    >
      <template v-if="current">
        <div class="detail-row">
          <span class="detail-label">意见ID：</span>
          <span>{{ current.id }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">提交者：</span>
          <span>{{ submitterLabel(current) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">提交时间：</span>
          <span>{{ createdAt(current) }}</span>
        </div>
        <div class="detail-block">
          <div class="detail-label">意见内容：</div>
          <div class="detail-content">{{ current.content || '-' }}</div>
        </div>
      </template>

      <el-divider />

      <el-form label-position="top">
        <el-form-item label="回复内容" required>
          <el-input
            v-model="replyContent"
            type="textarea"
            :rows="5"
            maxlength="500"
            show-word-limit
            placeholder="请输入回复内容"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="replyLoading"
          @click="handleReplySubmit"
        >
          提交回复
        </el-button>
      </template>
    </el-dialog>

    <!-- 申诉审核弹窗 -->
    <el-dialog
      v-model="reviewVisible"
      title="审核申诉"
      width="560px"
      destroy-on-close
    >
      <div v-loading="appealDetailLoading">
        <template v-if="appealDetail">
          <div class="detail-row">
            <span class="detail-label">申诉ID：</span>
            <span>{{ appealDetail.id }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">关联举报ID：</span>
            <span>{{ appealDetail.reportId ?? '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">申诉人：</span>
            <span>{{ submitterLabel(appealDetail) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">提交时间：</span>
            <span>{{ createdAt(appealDetail) }}</span>
          </div>
          <div class="detail-block">
            <div class="detail-label">申诉内容：</div>
            <div class="detail-content">{{ appealDetail.content || '-' }}</div>
          </div>
        </template>
      </div>

      <el-divider />

      <el-form label-position="top" class="review-form">
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="reviewForm.result">
            <el-radio value="approved">申诉成功</el-radio>
            <el-radio value="rejected">申诉失败</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处罚变更" v-if="reviewForm.result === 'approved'">
          <el-radio-group v-model="reviewForm.newPenaltyType">
            <el-radio :value="''">解除处罚</el-radio>
            <el-radio value="warning">警告处理</el-radio>
            <el-radio value="mute_24h">禁言24小时</el-radio>
            <el-radio value="mute_7d">禁言7天</el-radio>
            <el-radio value="ban">永久封禁</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核回复">
          <el-input
            v-model="reviewForm.reply"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="填写审核回复（可选）"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="reviewLoading"
          @click="handleReviewSubmit"
        >
          提交审核
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-feedback-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}
.page-title {
  margin: 0;
  font-size: 18px;
  color: #303133;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.header-tabs {
  font-size: 14px;
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}
.filter-label {
  font-size: 13px;
  color: #606266;
}
.table-card {
  border-radius: 8px;
}
.text-muted {
  color: #c0c4cc;
  font-size: 12px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
.detail-row {
  display: flex;
  margin-bottom: 8px;
  font-size: 13px;
  color: #606266;
}
.detail-label {
  width: 90px;
  color: #909399;
  flex-shrink: 0;
}
.detail-block {
  margin-top: 8px;
}
.detail-content {
  margin-top: 6px;
  background: #fef7ea;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 13px;
  color: #303133;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
