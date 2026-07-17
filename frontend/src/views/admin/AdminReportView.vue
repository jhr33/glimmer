<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminGetReports, adminGetReport, adminReviewReport } from '@/api/report'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = reactive({ current: 1, size: 10 })
const statusFilter = ref('') // '' 全部 / pending / reviewed

// 审核弹窗
const reviewVisible = ref(false)
const reviewLoading = ref(false)
const detail = ref(null)
const detailLoading = ref(false)
const reviewForm = reactive({
  result: 'approved', // approved / rejected
  reviewComment: ''
})

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

// 举报目标类型文案
const targetTypeMap = {
  drift_bottle: '漂流瓶',
  bottle_reply: '瓶子回复',
  letter: '信件',
  campfire_message: '篝火消息'
}

function targetTypeLabel(t) {
  return targetTypeMap[t] || t || '-'
}

function statusLabel(s) {
  return s === 'reviewed' ? '已审核' : s === 'pending' ? '待审核' : s || '-'
}

function statusType(s) {
  return s === 'reviewed' ? 'success' : s === 'pending' ? 'warning' : 'info'
}

function resultLabel(r) {
  if (r === 'approved') return '举报成立'
  if (r === 'rejected') return '举报驳回'
  return '-'
}

function resultType(r) {
  if (r === 'approved') return 'danger'
  if (r === 'rejected') return 'info'
  return 'info'
}

function reporterLabel(item) {
  return item.reporterNickname || item.reporter_nickname || item.reporter?.nickname || item.reporter?.anonymousName || `用户#${item.reporterId ?? item.reporter_id ?? '-'}`
}

function targetUserLabel(item) {
  return item.targetUserNickname || item.target_user_nickname || item.targetUser?.nickname || item.targetUser?.anonymousName || `用户#${item.targetUserId ?? item.target_user_id ?? '-'}`
}

function createdAt(item) {
  return item.createdAt || item.created_at || '-'
}

function reviewedAt(item) {
  return item.reviewedAt || item.reviewed_at || '-'
}

async function fetchList() {
  loading.value = true
  try {
    const params = { page: page.current, size: page.size }
    if (statusFilter.value) {
      params.status = statusFilter.value
    }
    const res = await adminGetReports(params)
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

function handleFilterChange() {
  page.current = 1
  fetchList()
}

function handlePageChange(p) {
  page.current = p
  fetchList()
}

async function openReview(item) {
  detail.value = null
  reviewForm.result = 'approved'
  reviewForm.reviewComment = ''
  reviewVisible.value = true
  detailLoading.value = true
  try {
    const res = await adminGetReport(item.id)
    detail.value = res.data
    // 若已有审核结果，预填
    if (detail.value?.result) {
      reviewForm.result = detail.value.result
    }
    if (detail.value?.reviewComment ?? detail.value?.review_comment) {
      reviewForm.reviewComment = detail.value.reviewComment || detail.value.review_comment
    }
  } catch (e) {
    // 退化为列表项数据
    detail.value = item
  } finally {
    detailLoading.value = false
  }
}

async function handleReviewSubmit() {
  if (!detail.value?.id) return
  if (!reviewForm.result) {
    ElMessage.warning('请选择审核结果')
    return
  }
  reviewLoading.value = true
  try {
    await adminReviewReport(detail.value.id, {
      result: reviewForm.result,
      reviewComment: reviewForm.reviewComment.trim()
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

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="admin-report-page">
    <div class="page-header">
      <h2 class="page-title">举报管理</h2>
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
          <el-option label="待审核" value="pending" />
          <el-option label="已审核" value="reviewed" />
        </el-select>
      </div>
    </div>

    <el-card v-loading="loading" shadow="never" class="table-card">
      <el-table :data="list" stripe style="width: 100%">
        <el-table-column prop="id" label="举报ID" width="80" />
        <el-table-column label="举报人" min-width="120">
          <template #default="{ row }">{{ reporterLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="被举报人" min-width="120">
          <template #default="{ row }">{{ targetUserLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="目标类型" width="110">
          <template #default="{ row }">{{ targetTypeLabel(row.targetType || row.target_type) }}</template>
        </el-table-column>
        <el-table-column label="目标ID" width="90">
          <template #default="{ row }">{{ row.targetId ?? row.target_id ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="举报原因" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核结果" width="110">
          <template #default="{ row }">
            <el-tag
              v-if="row.status === 'reviewed'"
              size="small"
              :type="resultType(row.result)"
              effect="plain"
            >
              {{ resultLabel(row.result) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="举报时间" min-width="160">
          <template #default="{ row }">{{ createdAt(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'pending'"
              size="small"
              type="primary"
              link
              @click="openReview(row)"
            >
              审核
            </el-button>
            <span v-else class="text-muted">已处理</span>
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

    <!-- 审核弹窗 -->
    <el-dialog
      v-model="reviewVisible"
      title="审核举报"
      width="560px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <template v-if="detail">
          <div class="detail-row">
            <span class="detail-label">举报ID：</span>
            <span>{{ detail.id }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">目标类型：</span>
            <span>{{ targetTypeLabel(detail.targetType || detail.target_type) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">目标ID：</span>
            <span>{{ detail.targetId ?? detail.target_id ?? '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">举报人：</span>
            <span>{{ reporterLabel(detail) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">被举报人：</span>
            <span>{{ targetUserLabel(detail) }}</span>
          </div>
          <div class="detail-block">
            <div class="detail-label">举报原因：</div>
            <div class="detail-content">{{ detail.content || '-' }}</div>
          </div>
        </template>
      </div>

      <el-divider />

      <el-form label-position="top" class="review-form">
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="reviewForm.result">
            <el-radio value="approved">举报成立</el-radio>
            <el-radio value="rejected">举报驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input
            v-model="reviewForm.reviewComment"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="填写审核意见（可选）"
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
.admin-report-page {
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
.review-form {
  margin-top: 4px;
}
</style>
