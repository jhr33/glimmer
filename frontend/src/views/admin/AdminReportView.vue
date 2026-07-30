<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminGetReportGroups, adminGetReportGroupDetail, adminReviewReportGroup } from '@/api/report'

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
  penaltyType: '', // warning / mute_24h / mute_7d / ban
  reviewComment: ''
})

const penaltyTypeOptions = [
  { value: '', label: '无处罚' },
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

function formatDateTime(dt) {
  if (!dt) return '-'
  return new Date(dt).toLocaleString('zh-CN')
}

function reporterLabel(item) {
  return item.reporterUsername || item.reporter_username || `用户#${item.reporterId ?? item.reporter_id ?? '-'}`
}

async function fetchList() {
  loading.value = true
  try {
    const params = { page: page.current, size: page.size }
    if (statusFilter.value) {
      params.status = statusFilter.value
    }
    const res = await adminGetReportGroups(params)
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

async function openDetail(item) {
  detail.value = null
  reviewForm.result = 'approved'
  reviewForm.reviewComment = ''
  reviewForm.penaltyType = ''
  reviewVisible.value = true
  detailLoading.value = true
  try {
    const res = await adminGetReportGroupDetail(item.targetType, item.targetId)
    detail.value = res.data
    // 若已有审核结果，预填
    if (detail.value?.groupResult) {
      reviewForm.result = detail.value.groupResult
    }
  } catch (e) {
    detail.value = item
  } finally {
    detailLoading.value = false
  }
}

async function handleReviewSubmit() {
  if (!detail.value?.targetType || !detail.value?.targetId) return
  if (!reviewForm.result) {
    ElMessage.warning('请选择审核结果')
    return
  }

  reviewLoading.value = true
  try {
    await adminReviewReportGroup({
      targetType: detail.value.targetType,
      targetId: detail.value.targetId,
      result: reviewForm.result,
      penaltyType: reviewForm.penaltyType,
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
        <el-table-column label="被举报人" min-width="120">
          <template #default="{ row }">{{ row.targetUsername || `用户#${row.targetUserId}` }}</template>
        </el-table-column>
        <el-table-column label="举报内容" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.reportedContent || '-' }}</div>
            <div class="text-muted text-sm">【{{ targetTypeLabel(row.targetType) }}】{{ row.location || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="举报人数" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="danger">{{ row.reporterCount || 0 }}人</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.groupStatus)">{{ statusLabel(row.groupStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核结果" width="110">
          <template #default="{ row }">
            <el-tag
              v-if="row.groupStatus === 'reviewed'"
              size="small"
              :type="resultType(row.groupResult)"
              effect="plain"
            >
              {{ resultLabel(row.groupResult) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="最近举报时间" min-width="160">
          <template #default="{ row }">{{ formatDateTime(row.lastReportedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.groupStatus === 'pending'"
              size="small"
              type="primary"
              link
              @click="openDetail(row)"
            >
              审核
            </el-button>
            <el-button
              size="small"
              type="success"
              link
              @click="openDetail(row)"
            >
              查看详情
            </el-button>
            <span v-if="row.groupStatus !== 'pending'" class="text-muted">已处理</span>
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
      width="700px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <template v-if="detail">
          <div class="detail-row">
            <span class="detail-label">被举报人：</span>
            <span>{{ detail.targetUsername || `用户#${detail.targetUserId}` }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">目标类型：</span>
            <span>{{ targetTypeLabel(detail.targetType) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">发言场所：</span>
            <span>{{ detail.location || '-' }}</span>
          </div>
          <div class="detail-block">
            <div class="detail-label">被举报内容：</div>
            <div class="detail-content">{{ detail.reportedContent || '-' }}</div>
          </div>
          <div class="detail-row">
            <span class="detail-label">举报人数：</span>
            <el-tag type="danger">{{ detail.reporterCount || 0 }}人</el-tag>
          </div>
          <div class="detail-row">
            <span class="detail-label">最早举报时间：</span>
            <span>{{ formatDateTime(detail.firstReportedAt) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">最近举报时间：</span>
            <span>{{ formatDateTime(detail.lastReportedAt) }}</span>
          </div>

          <!-- 所有举报人的举报理由 -->
          <div v-if="detail.reports && detail.reports.length > 0" class="detail-block">
            <div class="detail-label">举报详情（共 {{ detail.reports.length }} 条）：</div>
            <div class="reports-list">
              <div v-for="report in detail.reports" :key="report.id" class="report-item">
                <div class="report-header">
                  <span class="reporter-name">{{ reporterLabel(report) }}</span>
                  <span class="report-time">{{ formatDateTime(report.createdAt) }}</span>
                  <el-tag v-if="report.status === 'reviewed'" size="small" :type="report.result === 'approved' ? 'danger' : 'info'">
                    {{ resultLabel(report.result) }}
                  </el-tag>
                  <span v-else class="pending-tag">待审核</span>
                </div>
                <div class="report-content">{{ report.content || '-' }}</div>
              </div>
            </div>
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
        <el-form-item label="处罚类型" v-if="reviewForm.result === 'approved'" required>
          <el-radio-group v-model="reviewForm.penaltyType">
            <el-radio :value="''">无处罚</el-radio>
            <el-radio value="warning">警告处理</el-radio>
            <el-radio value="mute_24h">禁言24小时</el-radio>
            <el-radio value="mute_7d">禁言7天</el-radio>
            <el-radio value="ban">永久封禁</el-radio>
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
.text-sm {
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
  width: 110px;
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
.reports-list {
  margin-top: 8px;
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 8px;
}
.report-item {
  padding: 10px;
  background: #fafafa;
  border-radius: 4px;
  margin-bottom: 8px;
}
.report-item:last-child {
  margin-bottom: 0;
}
.report-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 12px;
}
.reporter-name {
  color: #303133;
  font-weight: 500;
}
.report-time {
  color: #909399;
}
.pending-tag {
  font-size: 12px;
  color: #e6a23c;
}
.report-content {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
}
</style>
