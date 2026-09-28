<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getAdminArticles, reviewAdminArticle, deleteAdminArticle } from '@/api/article'

const router = useRouter()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const activeStatus = ref('')
const keyword = ref('')
const keywordInput = ref('')
const page = reactive({ current: 1, size: 10 })

// 审核状态筛选定义（全部 → 不传参数）
const statusOptions = [
  { value: '', label: '全部' },
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'RETURNED', label: '已打回' },
  { value: 'APPROVED', label: '审核通过' }
]

const statusLabel = (value) => statusOptions.find((s) => s.value === value)?.label || value
const statusTagType = (value) =>
  ({ PENDING_REVIEW: 'warning', RETURNED: 'danger', APPROVED: 'success' }[value] || 'info')

const channels = { chat: '杂谈', tech: '技术笔记' }

async function fetchList() {
  loading.value = true
  try {
    const params = { page: page.current, size: page.size }
    if (activeStatus.value) params.reviewStatus = activeStatus.value
    if (keyword.value) params.keyword = keyword.value
    const res = await getAdminArticles(params)
    list.value = res.data?.list || []
    total.value = Number(res.data?.total || 0)
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleStatusChange() {
  page.current = 1
  fetchList()
}

function handleSearch() {
  keyword.value = keywordInput.value.trim()
  page.current = 1
  fetchList()
}

function handlePageChange(p) {
  page.current = p
  fetchList()
}

/** 查看文章全文（管理员审核内容用） */
const previewVisible = ref(false)
const previewArticle = ref(null)

function openPreview(item) {
  previewArticle.value = item
  previewVisible.value = true
}

/** 审核通过：恢复文章在广场展示 */
async function handleApprove(item) {
  try {
    await ElMessageBox.confirm(
      `确定审核通过《${item.title}》吗？通过后文章将重新对广场展示。`,
      '审核通过',
      { type: 'info', confirmButtonText: '通过', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await reviewAdminArticle(item.id, { action: 'approve' })
    ElMessage.success('已审核通过')
    fetchList()
  } catch (e) {
    // 错误已统一提示
  }
}

/** 屏蔽打回：填写原因后通知作者修改重提 */
async function handleReturn(item) {
  let reason
  try {
    const res = await ElMessageBox.prompt(
      `文章将被屏蔽打回给作者，请填写打回原因（将通过通知告知作者）`,
      `打回《${item.title}》`,
      {
        confirmButtonText: '确认打回',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：包含广告推销内容',
        inputValidator: (v) => (v && v.trim().length >= 2) || '请输入至少2个字的打回原因'
      }
    )
    reason = res.value.trim()
  } catch (e) {
    return
  }
  try {
    await reviewAdminArticle(item.id, { action: 'return', reason })
    ElMessage.success('已屏蔽打回并通知作者')
    fetchList()
  } catch (e) {
    // 错误已统一提示
  }
}

/** 删除违规文章（物理删除，级联清理互动数据） */
async function handleDelete(item) {
  try {
    await ElMessageBox.confirm(
      `确定删除《${item.title}》吗？文章及其评论、点赞、收藏将被永久删除，不可恢复。`,
      '删除文章',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteAdminArticle(item.id)
    ElMessage.success('已删除')
    fetchList()
  } catch (e) {
    // 错误已统一提示
  }
}

const pendingCount = computed(() => list.value.filter(a => a.reviewStatus === 'PENDING_REVIEW').length)

function formatTime(t) {
  return t ? String(t).replace('T', ' ').substring(0, 16) : ''
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="admin-article-page">
    <div class="toolbar">
      <el-radio-group v-model="activeStatus" @change="handleStatusChange">
        <el-radio-button v-for="s in statusOptions" :key="s.value" :value="s.value">
          {{ s.label }}
        </el-radio-button>
      </el-radio-group>
      <el-input
        v-model="keywordInput"
        class="search-input"
        placeholder="按笔名或标题搜索"
        clearable
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #append>
          <el-button :icon="Search" @click="handleSearch" />
        </template>
      </el-input>
      <span v-if="activeStatus === 'PENDING_REVIEW'" class="pending-tip">
        本页待审核 {{ pendingCount }} 篇
      </span>
    </div>

    <el-card v-loading="loading" shadow="never" class="table-card">
      <el-table :data="list" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="openPreview(row)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="penName" label="笔名" width="120" show-overflow-tooltip />
        <el-table-column label="频道" width="90">
          <template #default="{ row }">{{ channels[row.channel] || row.channel }}</template>
        </el-table-column>
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.reviewStatus)">
              {{ statusLabel(row.reviewStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="打回原因" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reviewReason || '—' }}</template>
        </el-table-column>
        <el-table-column label="互动" width="150">
          <template #default="{ row }">
            ❤️ {{ row.likeCount || 0 }} / 💬 {{ row.commentCount || 0 }} / ⭐ {{ row.favoriteCount || 0 }}
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ formatTime(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.reviewStatus !== 'APPROVED'"
              size="small"
              type="success"
              plain
              @click="handleApprove(row)"
            >通过</el-button>
            <el-button
              v-if="row.reviewStatus !== 'RETURNED'"
              size="small"
              type="warning"
              plain
              @click="handleReturn(row)"
            >打回</el-button>
            <el-button size="small" type="danger" plain @click="handleDelete(row)">删除</el-button>
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

    <!-- 文章全文预览 -->
    <el-dialog
      v-model="previewVisible"
      :title="previewArticle?.title"
      width="640px"
      top="6vh"
    >
      <div v-if="previewArticle" class="preview-body">
        <div class="preview-meta">
          <el-tag size="small" :type="statusTagType(previewArticle.reviewStatus)">
            {{ statusLabel(previewArticle.reviewStatus) }}
          </el-tag>
          <span class="meta-text">笔名：{{ previewArticle.penName }}</span>
          <span class="meta-text">发布于 {{ formatTime(previewArticle.createdAt) }}</span>
        </div>
        <div v-if="previewArticle.reviewReason" class="preview-reason">
          打回原因：{{ previewArticle.reviewReason }}
        </div>
        <div class="preview-content">{{ previewArticle.content }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-article-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.search-input {
  width: 240px;
}
.pending-tip {
  font-size: 13px;
  color: #e6a23c;
}
.table-card {
  border-radius: 10px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
.preview-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.meta-text {
  font-size: 13px;
  color: #909399;
}
.preview-reason {
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #fef0f0;
  border-radius: 6px;
  color: #c45656;
  font-size: 13px;
}
.preview-content {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #303133;
  font-size: 14px;
  max-height: 55vh;
  overflow-y: auto;
}
</style>
