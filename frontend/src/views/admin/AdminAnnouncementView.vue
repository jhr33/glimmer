<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminGetAnnouncements,
  adminCreateAnnouncement,
  adminTakeDownAnnouncement
} from '@/api/announcement'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = reactive({ current: 1, size: 10 })

// 发布弹窗
const createVisible = ref(false)
const createLoading = ref(false)
const createForm = reactive({
  title: '',
  content: ''
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

function statusLabel(s) {
  if (s === 'published') return '已发布'
  if (s === 'taken_down') return '已下架'
  return s || '-'
}

function statusType(s) {
  if (s === 'published') return 'success'
  if (s === 'taken_down') return 'info'
  return 'info'
}

function publisherLabel(item) {
  return item.publisherName || item.publisher_nickname || item.publisher?.nickname || '管理员'
}

function createdAt(item) {
  return item.createdAt || item.created_at || '-'
}

async function fetchList() {
  loading.value = true
  try {
    const res = await adminGetAnnouncements({ page: page.current, size: page.size })
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

function openCreate() {
  createForm.title = ''
  createForm.content = ''
  createVisible.value = true
}

async function handleCreate() {
  if (!createForm.title.trim()) {
    ElMessage.warning('请输入公告标题')
    return
  }
  if (!createForm.content.trim()) {
    ElMessage.warning('请输入公告内容')
    return
  }
  createLoading.value = true
  try {
    await adminCreateAnnouncement({
      title: createForm.title.trim(),
      content: createForm.content.trim()
    })
    ElMessage.success('公告已发布')
    createVisible.value = false
    page.current = 1
    await fetchList()
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    createLoading.value = false
  }
}

async function handleTakeDown(item) {
  try {
    await ElMessageBox.confirm(
      `确定要下架公告「${item.title}」吗？下架后用户列表将不再展示。`,
      '下架确认',
      {
        type: 'warning',
        confirmButtonText: '下架',
        cancelButtonText: '取消'
      }
    )
  } catch (e) {
    return
  }
  try {
    await adminTakeDownAnnouncement(item.id)
    ElMessage.success('已下架')
    await fetchList()
  } catch (e) {
    // 错误已由拦截器统一提示
  }
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="admin-announcement-page">
    <div class="page-header">
      <h2 class="page-title">公告管理</h2>
      <el-button type="primary" @click="openCreate">+ 发布公告</el-button>
    </div>

    <el-card v-loading="loading" shadow="never" class="table-card">
      <el-table :data="list" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.title || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布者" min-width="120">
          <template #default="{ row }">{{ publisherLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="发布时间" min-width="160">
          <template #default="{ row }">{{ createdAt(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'published'"
              size="small"
              type="danger"
              link
              @click="handleTakeDown(row)"
            >
              下架
            </el-button>
            <span v-else class="text-muted">已下架</span>
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

    <!-- 发布公告弹窗 -->
    <el-dialog
      v-model="createVisible"
      title="发布公告"
      width="560px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="标题" required>
          <el-input
            v-model="createForm.title"
            placeholder="请输入公告标题"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input
            v-model="createForm.content"
            type="textarea"
            :rows="6"
            maxlength="2000"
            show-word-limit
            placeholder="请输入公告内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="createLoading"
          @click="handleCreate"
        >
          发布
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-announcement-page {
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
</style>
