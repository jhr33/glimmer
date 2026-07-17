<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createFeedback, getMyFeedbacks } from '@/api/feedback'

const content = ref('')
const submitting = ref(false)

// 频控：1 分钟内只能提交 1 条
const lastSubmitAt = ref(0)
const FREQUENCY_LIMIT = 60 * 1000

const remainSeconds = computed(() => {
  if (!lastSubmitAt.value) return 0
  const diff = FREQUENCY_LIMIT - (Date.now() - lastSubmitAt.value)
  return diff > 0 ? Math.ceil(diff / 1000) : 0
})

const isFrequencyLimited = computed(() => remainSeconds.value > 0)

const isBanned = ref(false)

// 列表
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = reactive({ current: 1, size: 10 })

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

// 状态：pending 待回复 / replied 已回复
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

async function fetchList() {
  loading.value = true
  try {
    const res = await getMyFeedbacks({ page: page.current, size: page.size })
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

async function handleSubmit() {
  if (isBanned.value) {
    ElMessage.error('账号已被封禁，无法提交')
    return
  }
  if (isFrequencyLimited.value) {
    ElMessage.warning(`提交过于频繁，请 ${remainSeconds.value} 秒后再试`)
    return
  }
  if (!content.value.trim()) {
    ElMessage.warning('请填写意见内容')
    return
  }
  submitting.value = true
  try {
    await createFeedback({ content: content.value.trim() })
    ElMessage.success('意见已提交')
    content.value = ''
    lastSubmitAt.value = Date.now()
    page.current = 1
    await fetchList()
  } catch (e) {
    if (e?.code === 4015) {
      isBanned.value = true
      ElMessage.error('账号已被封禁，无法提交意见')
    }
    // 其它错误已由拦截器统一提示
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="feedback-page">
    <div class="page-header">
      <h2 class="page-title">📬 意见反馈</h2>
      <p class="page-subtitle">你的每一条建议，都会让 glimmer 更温暖</p>
    </div>

    <!-- 提交意见区 -->
    <el-card shadow="never" class="submit-card">
      <h3 class="section-title">提交意见</h3>
      <el-input
        v-model="content"
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
          @click="handleSubmit"
        >
          提交
        </el-button>
      </div>
    </el-card>

    <!-- 我的意见信列表 -->
    <el-card v-loading="loading" shadow="never" class="list-card">
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
.submit-card,
.list-card {
  border-radius: 10px;
}
.section-title {
  margin: 0 0 10px;
  font-size: 16px;
  color: #303133;
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
  gap: 12px;
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
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
