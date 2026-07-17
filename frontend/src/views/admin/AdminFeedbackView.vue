<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminGetFeedbacks, adminReplyFeedback } from '@/api/feedback'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = reactive({ current: 1, size: 10 })
const statusFilter = ref('') // '' 全部 / pending / replied

// 回复弹窗
const replyVisible = ref(false)
const replyLoading = ref(false)
const current = ref(null)
const replyContent = ref('')

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
    const res = await adminGetFeedbacks(params)
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
      <h2 class="page-title">意见信管理</h2>
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
          <el-option label="待回复" value="pending" />
          <el-option label="已回复" value="replied" />
        </el-select>
      </div>
    </div>

    <el-card v-loading="loading" shadow="never" class="table-card">
      <el-table :data="list" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="提交者" min-width="120">
          <template #default="{ row }">{{ submitterLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="内容" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content || '-' }}</template>
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
