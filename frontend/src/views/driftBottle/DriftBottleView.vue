<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  throwBottle,
  pickBottle,
  getBottle,
  releaseBottle,
  replyBottle,
  getBottleReplies,
  thankBottle,
  thankBottleReply,
  sinkBottle,
  getMyBottles
} from '@/api/driftBottle'
import { writeLetter } from '@/api/letter'
import { useUserStore } from '@/stores/user'
import ReportDialog from '@/components/ReportDialog.vue'

const userStore = useUserStore()
const currentUserId = computed(() => userStore.userInfo?.id)

// 举报弹窗
const reportDialog = ref(null)
const reportTargetType = ref('drift_bottle')
const reportTargetId = ref(null)

function openReportBottle() {
  if (!openedBottle.value) return
  reportTargetType.value = 'drift_bottle'
  reportTargetId.value = bottleIdOf(openedBottle.value)
  reportDialog.value?.open()
}

function openReportReply(reply) {
  if (!reply) return
  reportTargetType.value = 'bottle_reply'
  reportTargetId.value = reply.id ?? reply.replyId ?? reply.reply_id
  reportDialog.value?.open()
}

// 场景：main 主场景 / picked 捡到瓶子 / mine 我的瓶子
const scene = ref('main')

// 用户封禁标记（4015）
const isBanned = ref(false)

// 扔瓶子对话框
const throwVisible = ref(false)
const throwContent = ref('')
const throwLoading = ref(false)

// 捡到的瓶子
const pickedBottle = ref(null) // { id, pickedAt }
const openedBottle = ref(null) // 完整内容
const opened = ref(false)
const pickLoading = ref(false)
const openLoading = ref(false)

// 回复对话框
const replyVisible = ref(false)
const replyContent = ref('')
const replyLoading = ref(false)

// 我的瓶子列表
const mineLoading = ref(false)
const mineList = ref([])
const mineTotal = ref(0)
const minePage = reactive({ current: 1, size: 10 })

// 我的瓶子详情（含回复）
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailBottle = ref(null)
const detailReplies = ref([])

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

function bottleIdOf(b) {
  return b?.id ?? b?.bottleId ?? b?.bottle_id
}

function statusLabel(s) {
  if (s === 'drifting') return '漂流中'
  if (s === 'sunk') return '已沉底'
  return s || '-'
}

function statusType(s) {
  if (s === 'drifting') return 'success'
  if (s === 'sunk') return 'info'
  return 'info'
}

// 当前用户是否已感谢该瓶子
function hasThankedBottle(b) {
  if (!b) return false
  if (b.hasThanked ?? b.has_thanked) return true
  const arr = b.thankedBy || b.thanked_by || []
  return Array.isArray(arr) && currentUserId.value != null && arr.includes(currentUserId.value)
}

// 当前用户是否已回复该瓶子
function hasRepliedBottle(b) {
  if (!b) return false
  if (b.hasReplied ?? b.has_replied) return true
  if (b.replied) return true
  return false
}

function hasThankedReply(r) {
  if (!r) return false
  if (r.hasThanked ?? r.has_thanked) return true
  const arr = r.thankedBy || r.thanked_by || []
  return Array.isArray(arr) && currentUserId.value != null && arr.includes(currentUserId.value)
}

// 处理 4015/4019 封禁/禁言
function handleBanned(e) {
  if (e?.code === 4015 || e?.code === 4019) {
    isBanned.value = true
    // 刷新用户信息同步封禁状态
    userStore.fetchUserInfo().catch(() => {})
  }
}

// === 主场景操作 ===

function openThrowDialog() {
  if (isBanned.value) {
    ElMessage.error('账号已被封禁，无法操作')
    return
  }
  throwContent.value = ''
  throwVisible.value = true
}

async function handleThrow() {
  if (!throwContent.value.trim()) {
    ElMessage.warning('请写点什么吧')
    return
  }
  throwLoading.value = true
  try {
    await throwBottle({ content: throwContent.value.trim() })
    ElMessage.success('漂流瓶已投入大海')
    throwVisible.value = false
    throwContent.value = ''
  } catch (e) {
    handleBanned(e)
  } finally {
    throwLoading.value = false
  }
}

async function handlePick() {
  if (isBanned.value) {
    ElMessage.error('账号已被封禁，无法操作')
    return
  }
  pickLoading.value = true
  try {
    const res = await pickBottle()
    const data = res.data
    if (!data || !data.found || !data.bottle) {
      ElMessage.info('大海暂时没有瓶子了')
      return
    }
    const bottle = data.bottle
    pickedBottle.value = {
      id: bottleIdOf(bottle),
      pickedAt: bottle.createdAt || bottle.created_at || '-'
    }
    openedBottle.value = null
    opened.value = false
    scene.value = 'picked'
  } catch (e) {
    handleBanned(e)
  } finally {
    pickLoading.value = false
  }
}

function goMine() {
  scene.value = 'mine'
  minePage.current = 1
  fetchMine()
}

function backToMain() {
  scene.value = 'main'
  pickedBottle.value = null
  openedBottle.value = null
  opened.value = false
}

// === 捡到瓶子场景 ===

async function handleOpenBottle() {
  if (!pickedBottle.value?.id) return
  openLoading.value = true
  try {
    const res = await getBottle(pickedBottle.value.id)
    openedBottle.value = res.data
    opened.value = true
  } catch (e) {
    handleBanned(e)
  } finally {
    openLoading.value = false
  }
}

async function handleRelease() {
  if (!pickedBottle.value?.id) {
    backToMain()
    return
  }
  try {
    await releaseBottle(pickedBottle.value.id)
    ElMessage.success('已放回大海')
  } catch (e) {
    handleBanned(e)
  } finally {
    backToMain()
  }
}

function openReplyDialog() {
  if (isBanned.value) {
    ElMessage.error('账号已被封禁，无法操作')
    return
  }
  replyContent.value = ''
  replyVisible.value = true
}

async function handleReply() {
  if (!replyContent.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  replyLoading.value = true
  try {
    await replyBottle(pickedBottle.value.id, { content: replyContent.value.trim() })
    ElMessage.success('回复已送出')
    replyVisible.value = false
    replyContent.value = ''
    // 标记本瓶已回复
    if (openedBottle.value) {
      openedBottle.value = { ...openedBottle.value, hasReplied: true }
    }
  } catch (e) {
    handleBanned(e)
  } finally {
    replyLoading.value = false
  }
}

async function handleThankBottle() {
  if (!openedBottle.value) return
  try {
    await thankBottle(bottleIdOf(openedBottle.value))
    ElMessage.success('已表达感谢')
    openedBottle.value = { ...openedBottle.value, hasThanked: true }
  } catch (e) {
    handleBanned(e)
  }
}

// === 我的瓶子列表 ===

async function fetchMine() {
  mineLoading.value = true
  try {
    const res = await getMyBottles({ page: minePage.current, size: minePage.size })
    const data = res.data
    mineList.value = pickList(data)
    mineTotal.value = pickTotal(data)
  } catch (e) {
    mineList.value = []
    mineTotal.value = 0
  } finally {
    mineLoading.value = false
  }
}

function handleMinePageChange(p) {
  minePage.current = p
  fetchMine()
}

async function openMineDetail(item) {
  detailBottle.value = item
  detailReplies.value = []
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getBottleReplies(item.id, { page: 1, size: 100 })
    detailReplies.value = pickList(res.data)
  } catch (e) {
    detailReplies.value = []
  } finally {
    detailLoading.value = false
  }
}

async function handleSink(item) {
  try {
    await ElMessageBox.confirm('确定要将这个漂流瓶沉底吗？沉底后将不再被他人捡到。', '提示', {
      type: 'warning',
      confirmButtonText: '沉底',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await sinkBottle(item.id)
    ElMessage.success('已沉底')
    item.status = 'sunk'
    if (detailBottle.value?.id === item.id) {
      detailBottle.value = { ...detailBottle.value, status: 'sunk' }
    }
  } catch (e) {
    handleBanned(e)
  }
}

async function handleThankReply(reply) {
  try {
    await thankBottleReply(reply.id)
    ElMessage.success('已表达感谢')
    reply.hasThanked = true
  } catch (e) {
    handleBanned(e)
  }
}

// === 写信给对方 ===
const letterVisible = ref(false)
const letterContent = ref('')
const letterLoading = ref(false)
const letterTarget = ref(null) // { replyId, userId }

function openLetterDialog(reply) {
  if (isBanned.value) {
    ElMessage.error('账号已被封禁，无法操作')
    return
  }
  letterTarget.value = { replyId: reply.id, userId: reply.userId }
  letterContent.value = ''
  letterVisible.value = true
}

async function handleSendLetter() {
  if (!letterContent.value.trim()) {
    ElMessage.warning('请输入信件内容')
    return
  }
  if (!letterTarget.value) return
  letterLoading.value = true
  try {
    await writeLetter({
      receiverId: letterTarget.value.userId,
      content: letterContent.value.trim(),
      sourceBottleReplyId: letterTarget.value.replyId
    })
    ElMessage.success('信件已寄出（消耗1代币）')
    letterVisible.value = false
    letterContent.value = ''
    // 刷新用户信息（代币余额）
    await userStore.fetchUserInfo()
  } catch (e) {
    handleBanned(e)
  } finally {
    letterLoading.value = false
  }
}

function previewContent(content) {
  if (!content) return ''
  return content.length > 40 ? content.slice(0, 40) + '…' : content
}

// 监听用户信息变化，自动同步封禁状态
watch(() => userStore.userInfo?.status, () => {
  isBanned.value = userStore.userInfo?.status === 'banned'
})

onMounted(() => {
  // 进入页面时同步封禁状态
  isBanned.value = userStore.userInfo?.status === 'banned'
})
</script>

<template>
  <div class="drift-bottle-page">
    <!-- 主场景 -->
    <div v-if="scene === 'main'" class="ocean-scene">
      <div class="ocean-bg">
        <div class="ocean-title">🌊 漂流瓶</div>
        <p class="ocean-desc">把心事装入瓶中，让海浪带它去远方</p>
        <div class="ocean-actions">
          <el-button
            size="large"
            round
            :disabled="isBanned"
            @click="openThrowDialog"
          >
            ✍️ 扔漂流瓶
          </el-button>
          <el-button
            size="large"
            round
            type="primary"
            :loading="pickLoading"
            :disabled="isBanned"
            @click="handlePick"
          >
            🤚 捡漂流瓶
          </el-button>
          <el-button
            size="large"
            round
            @click="goMine"
          >
            📦 我的瓶子
          </el-button>
        </div>
        <div v-if="isBanned" class="banned-tip">账号已被封禁，暂无法投放或捡瓶子</div>
      </div>
    </div>

    <!-- 捡到瓶子场景 -->
    <div v-else-if="scene === 'picked'" class="picked-scene">
      <el-card shadow="hover" class="picked-card">
        <template #header>
          <div class="picked-header">
            <span>🍾 你捡到了一个漂流瓶</span>
          </div>
        </template>

        <div v-if="!opened" class="picked-info">
          <div class="picked-meta">瓶子编号：#{{ pickedBottle?.id || '-' }}</div>
          <div class="picked-meta">捡到时间：{{ pickedBottle?.pickedAt || '-' }}</div>
          <div class="picked-actions">
            <el-button
              type="primary"
              :loading="openLoading"
              :disabled="isBanned"
              @click="handleOpenBottle"
            >
              打开看看
            </el-button>
            <el-button :disabled="isBanned" @click="handleRelease">放回大海</el-button>
            <el-button @click="backToMain">返回</el-button>
          </div>
        </div>

        <div v-else v-loading="openLoading" class="bottle-content-wrap">
          <div class="bottle-content">{{ openedBottle?.content || '（空）' }}</div>
          <div class="bottle-meta">
            投放时间：{{ openedBottle?.createdAt || openedBottle?.created_at || '-' }}
          </div>

          <el-alert
            v-if="hasRepliedBottle(openedBottle)"
            title="你已回复过这个瓶子"
            type="info"
            :closable="false"
            show-icon
            class="replied-tip"
          />

          <div class="bottle-actions">
            <el-button
              type="primary"
              :disabled="isBanned || hasRepliedBottle(openedBottle)"
              @click="openReplyDialog"
            >
              回复
            </el-button>
            <el-button
              :disabled="hasThankedBottle(openedBottle)"
              @click="handleThankBottle"
            >
              {{ hasThankedBottle(openedBottle) ? '已感谢' : '感谢' }}
            </el-button>
            <el-button @click="openReportBottle">举报</el-button>
            <el-button :disabled="isBanned" @click="handleRelease">放回大海</el-button>
            <el-button @click="backToMain">返回</el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 我的瓶子列表 -->
    <div v-else-if="scene === 'mine'" class="mine-scene">
      <div class="mine-header">
        <h2 class="page-title">📦 我的瓶子</h2>
        <el-button @click="backToMain">返回大海</el-button>
      </div>

      <el-card v-loading="mineLoading" shadow="never" class="list-card">
        <el-empty v-if="!mineLoading && mineList.length === 0" description="你还没有扔过漂流瓶" />

        <ul v-else class="mine-list">
          <li v-for="item in mineList" :key="item.id" class="mine-item">
            <div class="mine-item-main" @click="openMineDetail(item)">
              <div class="mine-item-content">{{ previewContent(item.content) }}</div>
              <div class="mine-item-meta">
                <el-tag size="small" :type="statusType(item.status)">
                  {{ statusLabel(item.status) }}
                </el-tag>
                <span class="meta-text">回复数：{{ item.replyCount ?? item.reply_count ?? 0 }}</span>
                <span class="meta-text">{{ item.createdAt || item.created_at || '-' }}</span>
              </div>
            </div>
            <div class="mine-item-actions">
              <el-button
                v-if="item.status === 'drifting'"
                size="small"
                :disabled="isBanned"
                @click.stop="handleSink(item)"
              >
                沉底
              </el-button>
              <el-button size="small" @click.stop="openMineDetail(item)">查看详情</el-button>
            </div>
          </li>
        </ul>

        <div v-if="mineTotal > 0" class="pagination-wrap">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="minePage.current"
            :page-size="minePage.size"
            :total="mineTotal"
            @current-change="handleMinePageChange"
          />
        </div>
      </el-card>
    </div>

    <!-- 扔瓶子对话框 -->
    <el-dialog
      v-model="throwVisible"
      title="写下你想说的话"
      width="520px"
      destroy-on-close
    >
      <el-input
        v-model="throwContent"
        type="textarea"
        :rows="6"
        maxlength="500"
        show-word-limit
        placeholder="把心事装入瓶中…"
      />
      <template #footer>
        <el-button @click="throwVisible = false">取消</el-button>
        <el-button type="primary" :loading="throwLoading" @click="handleThrow">
          投入大海
        </el-button>
      </template>
    </el-dialog>

    <!-- 回复瓶子对话框 -->
    <el-dialog
      v-model="replyVisible"
      title="回复这个漂流瓶"
      width="520px"
      destroy-on-close
    >
      <el-input
        v-model="replyContent"
        type="textarea"
        :rows="6"
        maxlength="500"
        show-word-limit
        placeholder="写一句温柔的回复…"
      />
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="replyLoading" @click="handleReply">
          送出回复
        </el-button>
      </template>
    </el-dialog>

    <!-- 我的瓶子详情（含回复） -->
    <el-dialog
      v-model="detailVisible"
      title="瓶子详情"
      width="600px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <template v-if="detailBottle">
          <div class="detail-section">
            <el-tag size="small" :type="statusType(detailBottle.status)">
              {{ statusLabel(detailBottle.status) }}
            </el-tag>
            <span class="detail-time">
              {{ detailBottle.createdAt || detailBottle.created_at || '-' }}
            </span>
          </div>
          <div class="detail-content">{{ detailBottle.content }}</div>

          <el-divider content-position="left">回复列表</el-divider>

          <el-empty
            v-if="!detailLoading && detailReplies.length === 0"
            description="还没有回复"
            :image-size="80"
          />

          <ul v-else class="reply-list">
            <li v-for="r in detailReplies" :key="r.id" class="reply-item">
              <div class="reply-content">{{ r.content }}</div>
              <div class="reply-meta">
                <span>{{ r.createdAt || r.created_at || '-' }}</span>
                <div class="reply-actions">
                  <el-button
                    size="small"
                    link
                    :disabled="hasThankedReply(r)"
                    @click="handleThankReply(r)"
                  >
                    {{ hasThankedReply(r) ? '已感谢' : '感谢' }}
                  </el-button>
                  <el-button
                    size="small"
                    link
                    type="primary"
                    @click="openLetterDialog(r)"
                  >
                    写信
                  </el-button>
                  <el-button
                    size="small"
                    link
                    type="danger"
                    @click="openReportReply(r)"
                  >
                    举报
                  </el-button>
                </div>
              </div>
            </li>
          </ul>
        </template>
      </div>
    </el-dialog>

    <!-- 写信对话框 -->
    <el-dialog
      v-model="letterVisible"
      title="写一封信给对方"
      width="520px"
      destroy-on-close
    >
      <el-alert
        title="写信需要消耗 1 代币"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />
      <el-input
        v-model="letterContent"
        type="textarea"
        :rows="6"
        maxlength="2000"
        show-word-limit
        placeholder="写一段温柔的话…"
      />
      <template #footer>
        <el-button @click="letterVisible = false">取消</el-button>
        <el-button type="primary" :loading="letterLoading" @click="handleSendLetter">
          寄出
        </el-button>
      </template>
    </el-dialog>

    <!-- 举报弹窗 -->
    <ReportDialog
      ref="reportDialog"
      :target-type="reportTargetType"
      :target-id="reportTargetId"
    />
  </div>
</template>

<style scoped>
.drift-bottle-page {
  min-height: 60vh;
}

/* 主场景：海洋感大色块 */
.ocean-scene {
  border-radius: 12px;
  overflow: hidden;
}
.ocean-bg {
  background: linear-gradient(160deg, #4facfe 0%, #00c6fb 50%, #2f80ed 100%);
  color: #fff;
  padding: 60px 24px;
  text-align: center;
  min-height: 380px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}
.ocean-title {
  font-size: 32px;
  font-weight: bold;
  letter-spacing: 2px;
}
.ocean-desc {
  font-size: 14px;
  opacity: 0.9;
  margin-bottom: 12px;
}
.ocean-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  justify-content: center;
}
.banned-tip {
  margin-top: 12px;
  font-size: 13px;
  background: rgba(245, 108, 108, 0.85);
  padding: 6px 14px;
  border-radius: 16px;
}

/* 捡到瓶子场景 */
.picked-scene {
  max-width: 640px;
  margin: 0 auto;
}
.picked-card {
  border-radius: 12px;
}
.picked-header {
  font-weight: 600;
  color: #303133;
}
.picked-info {
  text-align: center;
  padding: 24px 8px;
}
.picked-meta {
  color: #606266;
  font-size: 14px;
  margin-bottom: 8px;
}
.picked-actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}
.bottle-content-wrap {
  padding: 8px 4px;
}
.bottle-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.8;
  font-size: 15px;
  color: #303133;
  background: #fef7ea;
  padding: 16px;
  border-radius: 8px;
}
.bottle-meta {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}
.replied-tip {
  margin-top: 12px;
}
.bottle-actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

/* 我的瓶子 */
.mine-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px 16px;
}
.page-title {
  margin: 0;
  font-size: 22px;
  color: #303133;
}
.list-card {
  border-radius: 10px;
}
.mine-list {
  display: flex;
  flex-direction: column;
}
.mine-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 12px;
  border-bottom: 1px solid #f0e6d2;
}
.mine-item:last-child {
  border-bottom: none;
}
.mine-item-main {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}
.mine-item-main:hover .mine-item-content {
  color: #e89a1a;
}
.mine-item-content {
  font-size: 14px;
  color: #303133;
  margin-bottom: 8px;
  word-break: break-word;
  transition: color 0.2s ease;
}
.mine-item-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #909399;
  flex-wrap: wrap;
}
.meta-text {
  color: #909399;
}
.mine-item-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}

/* 详情对话框 */
.detail-section {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.detail-time {
  font-size: 12px;
  color: #909399;
}
.detail-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.8;
  background: #fef7ea;
  padding: 14px;
  border-radius: 8px;
  color: #303133;
}
.reply-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.reply-item {
  background: #fafafa;
  border-radius: 8px;
  padding: 10px 12px;
}
.reply-content {
  white-space: pre-wrap;
  word-break: break-word;
  color: #303133;
  font-size: 14px;
  margin-bottom: 6px;
}
.reply-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}
.reply-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}
</style>
