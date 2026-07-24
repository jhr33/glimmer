<script setup>import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { createFeedback, createAppeal, getMyFeedbacks } from '@/api/feedback';
import { useRoute } from 'vue-router';
import { useUserStore } from '@/stores/user';
const route = useRoute();
const userStore = useUserStore();
const activeTab = ref('feedback');
const feedbackContent = ref('');
const appealContent = ref('');
const appealReportId = ref('');
const submitting = ref(false);
const appealSubmitting = ref(false);
const lastSubmitAt = ref(0);
const FREQUENCY_LIMIT = 60 * 1000;
const remainSeconds = computed(() => {
 if (!lastSubmitAt.value)
 return 0;
 const diff = FREQUENCY_LIMIT - (Date.now() - lastSubmitAt.value);
 return diff > 0 ? Math.ceil(diff / 1000) : 0;
});
const isFrequencyLimited = computed(() => remainSeconds.value > 0);
const isBanned = ref(false);
const loading = ref(false);
const list = ref([]);
const total = ref(0);
const page = reactive({ current: 1, size: 10 });
function pickList(data) {
 if (!data)
 return [];
 if (Array.isArray(data))
 return data;
 return data.records || data.list || data.items || [];
}
function pickTotal(data) {
 if (!data)
 return 0;
 if (Array.isArray(data))
 return data.length;
 return Number(data.total ?? data.totalCount ?? 0);
}
function isReplied(item) {
 return (item.status ?? item.state) === 'replied';
}
function statusLabel(item) {
 return isReplied(item) ? '已回复' : '待回复';
}
function statusType(item) {
 return isReplied(item) ? 'success' : 'warning';
}
function itemTypeLabel(item) {
 return (item.type ?? 'feedback') === 'appeal' ? '申诉' : '意见';
}
function itemTypeClass(item) {
 return (item.type ?? 'feedback') === 'appeal' ? 'appeal-type' : 'feedback-type';
}
function replyContent(item) {
 return item.reply ?? item.replyContent ?? item.reply_content ?? '';
}
function createdAt(item) {
 return item.createdAt || item.created_at || '-';
}
function repliedAt(item) {
 return item.repliedAt || item.replied_at || '-';
}
async function fetchList() {
 loading.value = true;
 try {
 const params = { page: page.current, size: page.size };
 if (activeTab.value) {
 params.type = activeTab.value;
 }
 const res = await getMyFeedbacks(params);
 const data = res.data;
 list.value = pickList(data);
 total.value = pickTotal(data);
 }
 catch (e) {
 list.value = [];
 total.value = 0;
 }
 finally {
 loading.value = false;
 }
}
function handlePageChange(p) {
 page.current = p;
 fetchList();
}
async function handleFeedbackSubmit() {
 if (isBanned.value) {
 ElMessage.error('账号已被封禁，无法提交');
 return;
 }
 if (isFrequencyLimited.value) {
 ElMessage.warning(`提交过于频繁，请 ${remainSeconds.value} 秒后再试`);
 return;
 }
 if (!feedbackContent.value.trim()) {
 ElMessage.warning('请填写意见内容');
 return;
 }
 submitting.value = true;
 try {
 await createFeedback({ content: feedbackContent.value.trim() });
 ElMessage.success('意见已提交');
 feedbackContent.value = '';
 lastSubmitAt.value = Date.now();
 page.current = 1;
 await fetchList();
 }
 catch (e) {
 if (e?.code === 4015 || e?.code === 4019) {
 isBanned.value = true;
 ElMessage.error('账号已被封禁，无法提交意见');
 // 刷新用户信息同步封禁状态
 userStore.fetchUserInfo().catch(() => {});
 }
 }
 finally {
 submitting.value = false;
 }
}
async function handleAppealSubmit() {
 if (!appealContent.value.trim()) {
 ElMessage.warning('请填写申诉内容');
 return;
 }
 appealSubmitting.value = true;
 try {
 const data = { content: appealContent.value.trim() };
 if (appealReportId.value) {
 data.reportId = appealReportId.value;
 }
 await createAppeal(data);
 ElMessage.success('申诉已提交');
 appealContent.value = '';
 appealReportId.value = '';
 page.current = 1;
 await fetchList();
 }
 catch (e) {
 if (e?.code === 409) {
 ElMessage.error(e?.message || '申诉失败');
 }
 }
 finally {
 appealSubmitting.value = false;
 }
}
// 监听用户信息变化，自动同步封禁状态
watch(() => userStore.userInfo?.status, () => {
 isBanned.value = userStore.userInfo?.status === 'banned';
});

onMounted(() => {
 const reportId = route.query.reportId;
 if (reportId) {
 appealReportId.value = reportId;
 activeTab.value = 'appeal';
 }
 // 初始化封禁状态
 isBanned.value = userStore.userInfo?.status === 'banned';
 fetchList();
});
</script>

<template>
  <div class="feedback-page">
    <div class="page-header">
      <h2 class="page-title">📬 意见与申诉</h2>
      <p class="page-subtitle">你的每一条建议，都会让 glimmer 更温暖</p>
    </div>

    <!-- 标签切换 -->
    <div class="tabs-wrap">
      <el-tabs v-model="activeTab" @tab-change="() => { page.current = 1; fetchList(); }">
        <el-tab-pane label="意见反馈" name="feedback" />
        <el-tab-pane label="申诉" name="appeal" />
      </el-tabs>
    </div>

    <!-- 提交意见区 -->
    <el-card v-if="activeTab === 'feedback'" shadow="never" class="submit-card">
      <h3 class="section-title">提交意见</h3>
      <el-input
        v-model="feedbackContent"
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
          @click="handleFeedbackSubmit"
        >
          提交
        </el-button>
      </div>
    </el-card>

    <!-- 提交申诉区 -->
    <el-card v-if="activeTab === 'appeal'" shadow="never" class="submit-card">
      <h3 class="section-title">提交申诉</h3>
      <div v-if="appealReportId" class="appeal-hint">
        <el-tag type="info" effect="plain">正在为举报 #{{ appealReportId }} 提交申诉</el-tag>
      </div>
      <el-input
        v-model="appealContent"
        type="textarea"
        :rows="5"
        maxlength="500"
        show-word-limit
        placeholder="请详细描述你的申诉理由…（最长 500 字）"
      />
      <div class="appeal-rules">
        <ul>
          <li>一条被举报信息最多可以申诉三次</li>
          <li>一个账户一天最多提交七次申诉</li>
          <li>申诉提交后，管理员将重新进行审核</li>
        </ul>
      </div>
      <div class="submit-actions">
        <el-button
          type="primary"
          :loading="appealSubmitting"
          @click="handleAppealSubmit"
        >
          提交申诉
        </el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card v-loading="loading" shadow="never" class="list-card">
      <template #header>
        <span class="card-header-title">{{ activeTab === 'appeal' ? '我的申诉' : '我的意见信' }}</span>
      </template>

      <el-empty v-if="!loading && list.length === 0" :description="activeTab === 'appeal' ? '还没有提交过申诉' : '还没有提交过意见'" />

      <ul v-else class="feedback-list">
        <li v-for="item in list" :key="item.id" class="feedback-item">
          <div class="item-top">
            <el-tag size="small" :type="statusType(item)" effect="plain">
              {{ statusLabel(item) }}
            </el-tag>
            <el-tag size="small" class="type-tag" :class="itemTypeClass(item)">
              {{ itemTypeLabel(item) }}
            </el-tag>
            <span v-if="item.reportId" class="report-id">举报 #{{ item.reportId }}</span>
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
.tabs-wrap {
  margin-bottom: 8px;
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
.appeal-hint {
  margin-bottom: 10px;
}
.appeal-rules {
  margin: 10px 0;
  padding: 10px 12px;
  background: #fef7ea;
  border-radius: 8px;
}
.appeal-rules ul {
  margin: 0;
  padding-left: 20px;
}
.appeal-rules li {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.appeal-rules li:last-child {
  margin-bottom: 0;
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
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.type-tag {
  background: #f0f9eb;
  color: #67c23a;
}
.type-tag.appeal-type {
  background: #ecf5ff;
  color: #409eff;
}
.report-id {
  font-size: 12px;
  color: #909399;
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 4px;
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
