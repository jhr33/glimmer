<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, updateUserStatus } from '@/api/user'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const currentUserId = computed(() => userStore.userInfo?.id)

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = reactive({ current: 1, size: 10 })
const statusFilter = ref('') // '' 全部 / active / banned
const roleFilter = ref('') // '' 全部 / user / admin

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

function roleLabel(r) {
  return r === 'admin' ? '管理员' : '普通用户'
}

function roleType(r) {
  return r === 'admin' ? 'danger' : 'info'
}

function statusLabel(s) {
  return s === 'banned' ? '封禁' : s === 'active' ? '正常' : s || '-'
}

function statusType(s) {
  return s === 'banned' ? 'danger' : s === 'active' ? 'success' : 'info'
}

function nickname(item) {
  return item.nickname || item.nickname || '未设置'
}

function anonymousName(item) {
  return item.anonymousName || item.anonymous_name || '-'
}

function username(item) {
  return item.username || '-'
}

function createdAt(item) {
  return item.createdAt || item.created_at || '-'
}

function num(item, ...keys) {
  for (const k of keys) {
    if (item[k] != null) return item[k]
  }
  return 0
}

// 是否当前登录用户
function isSelf(item) {
  const uid = currentUserId.value
  if (uid == null) return false
  return item.id === uid
}

async function fetchList() {
  loading.value = true
  try {
    const params = { page: page.current, size: page.size }
    if (statusFilter.value) params.status = statusFilter.value
    if (roleFilter.value) params.role = roleFilter.value
    const res = await getUserList(params)
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

async function handleToggleStatus(item) {
  const isBanned = item.status === 'banned'
  const action = isBanned ? '解封' : '封禁'
  try {
    await ElMessageBox.confirm(
      `确定要${action}用户「${item.username || item.nickname || item.id}」吗？`,
      `${action}确认`,
      {
        type: isBanned ? 'success' : 'warning',
        confirmButtonText: action,
        cancelButtonText: '取消'
      }
    )
  } catch (e) {
    return
  }
  try {
    await updateUserStatus(item.id, {
      status: isBanned ? 'active' : 'banned'
    })
    ElMessage.success(`已${action}`)
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
  <div class="admin-user-page">
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
      <div class="filter-bar">
        <span class="filter-label">状态：</span>
        <el-select
          v-model="statusFilter"
          placeholder="全部"
          clearable
          style="width: 120px"
          @change="handleFilterChange"
        >
          <el-option label="全部" value="" />
          <el-option label="正常" value="active" />
          <el-option label="封禁" value="banned" />
        </el-select>
        <span class="filter-label">角色：</span>
        <el-select
          v-model="roleFilter"
          placeholder="全部"
          clearable
          style="width: 140px"
          @change="handleFilterChange"
        >
          <el-option label="全部" value="" />
          <el-option label="普通用户" value="user" />
          <el-option label="管理员" value="admin" />
        </el-select>
      </div>
    </div>

    <el-card v-loading="loading" shadow="never" class="table-card">
      <el-table :data="list" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="用户名" min-width="120">
          <template #default="{ row }">{{ username(row) }}</template>
        </el-table-column>
        <el-table-column label="昵称" min-width="120">
          <template #default="{ row }">{{ nickname(row) }}</template>
        </el-table-column>
        <el-table-column label="匿名昵称" min-width="140">
          <template #default="{ row }">{{ anonymousName(row) }}</template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="roleType(row.role)">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="代币余额" width="100">
          <template #default="{ row }">{{ num(row, 'tokenBalance', 'token_balance') }}</template>
        </el-table-column>
        <el-table-column label="萤火值" width="90">
          <template #default="{ row }">{{ num(row, 'totalFirefly', 'total_firefly') }}</template>
        </el-table-column>
        <el-table-column label="累计签到" width="100">
          <template #default="{ row }">{{ num(row, 'totalSignDays', 'total_sign_days') }}</template>
        </el-table-column>
        <el-table-column label="待处理举报数" width="120">
          <template #default="{ row }">
            <span :class="{ 'report-pending': num(row, 'pendingReportCount', 'pending_report_count') > 0 }">
              {{ num(row, 'pendingReportCount', 'pending_report_count') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" min-width="160">
          <template #default="{ row }">{{ createdAt(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <!-- 不能封禁自己 -->
            <el-tooltip
              v-if="isSelf(row)"
              content="不能封禁自己"
              placement="top"
            >
              <el-button size="small" type="danger" link disabled>
                {{ row.status === 'banned' ? '解封' : '封禁' }}
              </el-button>
            </el-tooltip>
            <!-- 不能封禁其他管理员 -->
            <el-tooltip
              v-else-if="row.role === 'admin' && row.status !== 'banned'"
              content="不能封禁其他管理员"
              placement="top"
            >
              <el-button size="small" type="danger" link disabled>封禁</el-button>
            </el-tooltip>
            <el-button
              v-else
              size="small"
              :type="row.status === 'banned' ? 'success' : 'danger'"
              link
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 'banned' ? '解封' : '封禁' }}
            </el-button>
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
  </div>
</template>

<style scoped>
.admin-user-page {
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
  flex-wrap: wrap;
}
.filter-label {
  font-size: 13px;
  color: #606266;
}
.table-card {
  border-radius: 8px;
}
.report-pending {
  color: #f56c6c;
  font-weight: 600;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
