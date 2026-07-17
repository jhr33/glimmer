<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getAnnouncements, getAnnouncement } from '@/api/announcement'

const loading = ref(false)
const detailLoading = ref(false)
const list = ref([])
const total = ref(0)
const page = reactive({
  current: 1,
  size: 10
})

const detailVisible = ref(false)
const detail = ref(null)

// 兼容多种分页响应结构（records / list / items）
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
    const res = await getAnnouncements({ page: page.current, size: page.size })
    const data = res.data
    list.value = pickList(data)
    total.value = pickTotal(data)
  } catch (e) {
    // 错误已由 request.js 拦截器统一提示
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function openDetail(item) {
  detail.value = null
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getAnnouncement(item.id)
    detail.value = res.data
  } catch (e) {
    // 错误已由 request.js 拦截器统一提示
  } finally {
    detailLoading.value = false
  }
}

function handlePageChange(p) {
  page.current = p
  fetchList()
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="announcement-page">
    <div class="page-header">
      <h2 class="page-title">📢 公告</h2>
      <p class="page-subtitle">了解 glimmer 的最新动态</p>
    </div>

    <el-card v-loading="loading" shadow="never" class="list-card">
      <el-empty v-if="!loading && list.length === 0" description="暂无公告" />

      <ul v-else class="announcement-list">
        <li
          v-for="item in list"
          :key="item.id"
          class="announcement-item"
          @click="openDetail(item)"
        >
          <div class="item-title">{{ item.title }}</div>
          <div class="item-meta">
            <span class="meta-time">{{ item.createdAt || item.created_at || '-' }}</span>
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

    <!-- 公告详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      :title="detail?.title || '公告详情'"
      width="640px"
      destroy-on-close
    >
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <div class="detail-meta">
            <span>发布时间：{{ detail.createdAt || detail.created_at || '-' }}</span>
            <el-divider direction="vertical" />
            <span>发布者：{{ detail.publisherName || detail.publisher_name || detail.publisher?.nickname || '管理员' }}</span>
          </div>
          <el-divider />
          <div class="detail-content">{{ detail.content }}</div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.announcement-page {
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
.list-card {
  border-radius: 10px;
}
.announcement-list {
  display: flex;
  flex-direction: column;
}
.announcement-item {
  padding: 14px 12px;
  border-bottom: 1px solid #f0e6d2;
  cursor: pointer;
  transition: background 0.2s ease;
}
.announcement-item:last-child {
  border-bottom: none;
}
.announcement-item:hover {
  background: #fef7ea;
}
.item-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}
.item-meta {
  font-size: 12px;
  color: #909399;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
.detail-body {
  min-height: 120px;
}
.detail-meta {
  color: #909399;
  font-size: 13px;
}
.detail-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.8;
  color: #303133;
  font-size: 14px;
}
</style>
