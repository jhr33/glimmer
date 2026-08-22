<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLetter, replyLetter, thankLetter, markLetterRead } from '@/api/letter'
import { useUserStore } from '@/stores/user'
import ReportDialog from '@/components/ReportDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const currentUserId = computed(() => userStore.userInfo?.id)
const isLoggedIn = computed(() => userStore.isLoggedIn)

// 举报弹窗
const reportDialog = ref(null)

function openReportLetter() {
  if (!letter.value) return
  if (!isLoggedIn.value) { ElMessage.warning('请先登录'); return }
  reportDialog.value?.open()
}

const loading = ref(false)
const replyLoading = ref(false)
const thankLoading = ref(false)
const letter = ref(null)

const replyContent = ref('')

const letterId = computed(() => route.params.id)

// 当前用户是否为收信人
const isReceiver = computed(() => {
  if (!letter.value) return false
  const rid = letter.value.receiverId ?? letter.value.receiver_id
  return rid != null && rid === currentUserId.value
})

// 是否已回复（4009 错误码或 isReplied 字段）
const replied = computed(() => {
  return !!(letter.value?.isReplied ?? letter.value?.is_replied)
})

// 当前用户是否已感谢
function hasThanked() {
  const l = letter.value
  if (!l) return false
  if (l.hasThanked ?? l.has_thanked) return true
  const arr = l.thankedBy || l.thanked_by || []
  return Array.isArray(arr) && currentUserId.value != null && arr.includes(currentUserId.value)
}

// 来源类型文案
const sourceText = computed(() => {
  const t = letter.value?.sourceType || letter.value?.source_type
  if (t === 'bottle_reply') return '来自漂流瓶回复'
  if (t === 'direct') return '直接来信'
  return t ? `来源：${t}` : ''
})

function senderLabel() {
  const l = letter.value
  return l?.senderNickname || l?.sender_nickname || l?.sender?.nickname || l?.senderAnonymousName || l?.sender_anonymous_name || '匿名旅人'
}

async function fetchDetail() {
  loading.value = true
  try {
    const res = await getLetter(letterId.value)
    letter.value = res.data
    await markLetterRead(letterId.value)
  } catch (e) {
    handleBanned(e)
  } finally {
    loading.value = false
  }
}

async function handleReply() {
  if (!isLoggedIn.value) { ElMessage.warning('请先登录'); return }
  if (!replyContent.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  replyLoading.value = true
  try {
    await replyLetter(letterId.value, { content: replyContent.value.trim() })
    ElMessage.success('回复已送出（消耗1代币）')
    replyContent.value = ''
    await userStore.fetchUserInfo()
    await fetchDetail()
  } catch (e) {
    if (e?.code === 4009 && letter.value) {
      letter.value = { ...letter.value, isReplied: true }
    }
  } finally {
    replyLoading.value = false
  }
}

async function handleThank() {
  if (!isLoggedIn.value) { ElMessage.warning('请先登录'); return }
  thankLoading.value = true
  try {
    await thankLetter(letterId.value)
    ElMessage.success('已表达感谢')
    if (letter.value) {
      letter.value = { ...letter.value, hasThanked: true }
    }
  } catch (e) {
    // 错误已由拦截器统一提示（如 4008 已感谢过）
  } finally {
    thankLoading.value = false
  }
}

function goBack() {
  router.push({ name: 'letter' })
}

onMounted(() => {
  fetchDetail()
})
</script>

<template>
  <div class="letter-detail-page">
    <div class="page-header">
      <el-button link @click="goBack">← 返回信件列表</el-button>
    </div>

    <el-card v-loading="loading" shadow="never" class="detail-card">
      <el-empty
        v-if="!loading && !letter"
        description="信件不存在或无权查看"
      />

      <template v-else-if="letter">
        <div class="letter-meta">
          <span>来自：{{ senderLabel() }}</span>
          <el-divider direction="vertical" />
          <span>{{ letter.createdAt || letter.created_at || '-' }}</span>
          <template v-if="sourceText">
            <el-divider direction="vertical" />
            <el-tag size="small" type="info" effect="plain">{{ sourceText }}</el-tag>
          </template>
        </div>

        <!-- 来源漂流瓶信息 -->
        <div v-if="letter.sourceBottleContent || letter.source_bottle_content" class="source-bottle">
          <div class="source-title">📮 来源漂流瓶</div>
          <div class="source-bottle-content">{{ letter.sourceBottleContent || letter.source_bottle_content }}</div>
          <div v-if="letter.sourceReplyContent || letter.source_reply_content" class="source-reply">
            <div class="source-reply-label">对方的回复：</div>
            <div class="source-reply-content">{{ letter.sourceReplyContent || letter.source_reply_content }}</div>
          </div>
        </div>

        <el-divider />

        <div class="letter-content">{{ letter.content }}</div>

        <div class="letter-status">
          <el-tag v-if="replied" size="small" type="success" effect="plain">已回复</el-tag>
          <el-tag v-else-if="isReceiver" size="small" type="warning" effect="plain">待回复</el-tag>
        </div>

        <!-- 回复区：仅登录的收信人且未回复时可回复 -->
        <div v-if="isLoggedIn && isReceiver && !replied" class="reply-section">
          <h3 class="reply-title">回信</h3>
          <el-input
            v-model="replyContent"
            type="textarea"
            :rows="6"
            maxlength="500"
            show-word-limit
            placeholder="写一封温柔的回信…（登录后可回复）"
            :disabled="!isLoggedIn"
          />
          <div class="reply-actions">
            <el-button
              type="primary"
              :loading="replyLoading"
              @click="handleReply"
            >
              送出回信
            </el-button>
          </div>
        </div>

        <!-- 已回复提示 -->
        <el-alert
          v-else-if="isReceiver && replied"
          title="这封信已回复，无法再次回复"
          type="info"
          :closable="false"
          show-icon
          class="replied-tip"
        />
        <el-alert
          v-else-if="!isLoggedIn && isReceiver"
          title="请登录后查看信件内容并回复"
          type="info"
          :closable="false"
          show-icon
          class="replied-tip"
        />

        <!-- 感谢按钮：登录的收信人可用 -->
        <div v-if="isReceiver" class="thank-section">
          <button
            class="thank-heart-btn letter-thank-btn"
            :class="{ thanked: hasThanked() }"
            :disabled="hasThanked() || thankLoading"
            @click="handleThank"
            :title="hasThanked() ? '已感谢' : '感谢这封信'"
          >
            <span v-if="thankLoading" class="thank-loading">⋯</span>
            <span v-else class="heart-icon">{{ hasThanked() ? '❤️' : '🤍' }}</span>
          </button>
        </div>

        <!-- 举报按钮：AI（回音）写的信不显示举报按钮 -->
        <div v-if="!letter.isFromBot" class="report-section">
          <el-button @click="openReportLetter">举报</el-button>
        </div>
      </template>
    </el-card>

    <!-- 举报弹窗 -->
    <ReportDialog
      ref="reportDialog"
      target-type="letter"
      :target-id="letterId"
    />
  </div>
</template>

<style scoped>
.letter-detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 760px;
  margin: 0 auto;
}
.page-header {
  padding: 4px;
}
.detail-card {
  border-radius: 10px;
}
.letter-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 13px;
  color: #606266;
}
.source-bottle {
  margin: 14px 0;
  padding: 14px 16px;
  background: #fbf6ea;
  border-radius: 6px;
  border-left: 3px solid #d4a857;
}
.source-title {
  font-size: 13px;
  font-weight: 600;
  color: #9a6b2e;
  margin-bottom: 8px;
}
.source-bottle-content {
  font-family: 'LXGW WenKai', 'KaiTi', '楷体', 'STKaiti', '华文楷体', serif;
  font-size: 13px;
  color: #303133;
  line-height: 1.7;
  white-space: pre-wrap;
}
.source-reply {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #dcdfe6;
}
.source-reply-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.source-reply-content {
  font-family: 'LXGW WenKai', 'KaiTi', '楷体', 'STKaiti', '华文楷体', serif;
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
  white-space: pre-wrap;
}
.letter-content {
  font-family: 'LXGW WenKai', 'KaiTi', '楷体', 'STKaiti', '华文楷体', serif;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 2;
  font-size: 16px;
  letter-spacing: 0.02em;
  color: #4a3a28;
  /* 信笺纸质感：暖色纸张 + 横向行线 */
  background-color: #fdfbf2;
  background-image: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent calc(2em - 1px),
    #ece0c4 calc(2em - 1px),
    #ece0c4 2em
  );
  padding: 2em 30px 1.6em 58px;
  border-radius: 3px;
  border: 1px solid #e8dcc0;
  box-shadow: 0 6px 20px rgba(120, 90, 40, 0.1), 0 1px 3px rgba(120, 90, 40, 0.08);
  position: relative;
}
/* 信笺左侧装订红线 */
.letter-content::before {
  content: '';
  position: absolute;
  left: 38px;
  top: 0;
  bottom: 0;
  width: 1px;
  background: rgba(196, 84, 82, 0.35);
}
.letter-status {
  margin-top: 14px;
}
.reply-section {
  margin-top: 24px;
}
.reply-title {
  margin: 0 0 10px;
  font-size: 16px;
  color: #303133;
}
.reply-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.replied-tip {
  margin-top: 20px;
}
.thank-section {
  margin-top: 20px;
}
.report-section {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

/* 感谢爱心按钮 */
.thank-heart-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: none;
  border: 1px solid #dcdfe6;
  border-radius: 10px;
  padding: 10px 18px;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  transition: all 0.2s ease;
}
.thank-heart-btn:hover:not(:disabled) {
  border-color: #f56c6c;
  color: #f56c6c;
  background: #fef0f0;
}
.thank-heart-btn:disabled {
  cursor: default;
  opacity: 0.7;
}
.thank-heart-btn.thanked {
  border-color: #f56c6c;
  background: #fef0f0;
  color: #f56c6c;
}
.thank-heart-btn .heart-icon {
  font-size: 18px;
  line-height: 1;
}
.thank-heart-btn .thank-label {
  font-size: 14px;
}
.thank-heart-btn .thank-loading {
  font-size: 18px;
  line-height: 1;
  animation: thankLoading 0.8s ease-in-out infinite;
}
@keyframes thankLoading {
  0%, 100% { opacity: 0.4; transform: scale(0.9); }
  50% { opacity: 1; transform: scale(1.1); }
}
.letter-thank-btn {
  padding: 12px 22px;
  border-radius: 12px;
}
</style>
