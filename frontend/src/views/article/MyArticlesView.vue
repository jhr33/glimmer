<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { View, EditPen, Delete, Back, EditPen as WriteIcon } from '@element-plus/icons-vue'
import { getMyArticles, getArticle, deleteArticle, changeArticleVisibility } from '@/api/article'
import { useUserStore } from '@/stores/user'
import ArticleEditorDialog from './ArticleEditorDialog.vue'
import UserAvatar from '@/components/UserAvatar.vue'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)
// visibility: all 全部 / public 仅公开 / private 仅私密
const visibility = ref('all')
const channel = ref('all')
const page = reactive({ current: 1, size: 10 })

// 编辑弹窗
const editorVisible = ref(false)
const editingArticle = ref(null)

const channels = [
  { value: 'all', label: '全部频道' },
  { value: 'chat', label: '杂谈' },
  { value: 'tech', label: '技术笔记' }
]
const channelLabel = (value) => channels.find((c) => c.value === value)?.label || '杂谈'

async function fetchList() {
  loading.value = true
  try {
    const params = { page: page.current, size: page.size }
    if (channel.value !== 'all') params.channel = channel.value
    if (visibility.value === 'public') params.isPublic = true
    if (visibility.value === 'private') params.isPublic = false
    const res = await getMyArticles(params)
    list.value = res.data?.list || []
    total.value = Number(res.data?.total || 0)
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

// 切换公开/私密：即时生效
async function handleVisibilityChange(item, val) {
  try {
    await changeArticleVisibility(item.id, val)
    item.isPublic = val
    ElMessage.success(val ? '已设为公开' : '已设为私密')
  } catch (e) {
    // 失败时 switch 由 :value 单向绑定控制，不会发生状态错位
  }
}

// 编辑前先拉完整正文（列表接口不返回 content）
async function handleEdit(item) {
  try {
    const res = await getArticle(item.id)
    editingArticle.value = res.data
    editorVisible.value = true
  } catch (e) {
    // 错误已统一提示
  }
}

async function handleDelete(item) {
  try {
    await ElMessageBox.confirm(`确定删除文章「${item.title}」吗？删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return // 用户取消
  }
  try {
    await deleteArticle(item.id)
    ElMessage.success('已删除')
    // 删除最后一页唯一一条时回退一页，避免空页
    if (list.value.length === 1 && page.current > 1) {
      page.current -= 1
    }
    fetchList()
  } catch (e) {
    // 错误已统一提示
  }
}

function handleEditorSuccess() {
  editingArticle.value = null
  fetchList()
}

onMounted(() => {
  // 全局守卫对普通页面采用游客可浏览策略，随记涉及个人内容，页面级再强制一次登录
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后查看我的随记')
    router.replace({ path: '/login', query: { redirect: '/articles/mine' } })
    return
  }
  fetchList()
})
</script>

<template>
  <div class="my-article-page">
    <div class="page-header">
      <div class="header-text">
        <h2 class="page-title">📒 我的随记</h2>
        <p class="page-subtitle">管理你发布的全部文章，包括私密草稿</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Back" @click="router.push('/articles')">返回广场</el-button>
        <el-button type="primary" :icon="WriteIcon" @click="editorVisible = true; editingArticle = null">
          写点什么
        </el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-radio-group v-model="visibility" @change="handleFilterChange">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="public">公开</el-radio-button>
        <el-radio-button value="private">私密</el-radio-button>
      </el-radio-group>
      <el-select v-model="channel" style="width: 140px" @change="handleFilterChange">
        <el-option v-for="c in channels" :key="c.value" :label="c.label" :value="c.value" />
      </el-select>
    </div>

    <el-card v-loading="loading" shadow="never" class="list-card">
      <el-empty v-if="!loading && list.length === 0" description="还没有随记，写下第一篇吧" />

      <ul v-else class="article-list">
        <li v-for="item in list" :key="item.id" class="article-item">
          <div class="item-main" @click="router.push(`/articles/${item.id}`)">
            <div class="item-head">
              <el-tag size="small" :type="item.channel === 'tech' ? 'success' : 'warning'" effect="plain">
                {{ channelLabel(item.channel) }}
              </el-tag>
              <el-tag size="small" :type="item.isPublic ? 'primary' : 'info'" effect="plain">
                {{ item.isPublic ? '公开' : '私密' }}
              </el-tag>
              <!-- 审核状态标识：被举报待审 / 被打回 -->
              <el-tag v-if="item.reviewStatus === 'PENDING_REVIEW'" size="small" type="warning">
                待审核
              </el-tag>
              <el-tooltip
                v-if="item.reviewStatus === 'RETURNED'"
                :content="`已打回：${item.reviewReason || '未填写原因'}，请修改后重新提交`"
                placement="top"
              >
                <el-tag size="small" type="danger">已打回</el-tag>
              </el-tooltip>
              <span class="item-title">{{ item.title }}</span>
            </div>
            <p class="item-summary">{{ item.summary }}</p>
            <div class="item-tags">
              <el-tag v-for="tag in item.tags" :key="tag" size="small" effect="light" class="tag-chip">
                # {{ tag }}
              </el-tag>
            </div>
            <div class="item-meta">
              <UserAvatar :url="item.authorAvatar" :user-id="item.authorId" :size="18" />
              <span>{{ item.penName }}</span>
              <span class="meta-dot">·</span>
              <span>{{ item.createdAt }}</span>
              <span class="meta-dot">·</span>
              <span class="meta-views"><el-icon><View /></el-icon> {{ item.viewCount || 0 }}</span>
            </div>
          </div>

          <div class="item-ops" @click.stop>
            <div class="visibility-line">
              <span class="visibility-label">{{ item.isPublic ? '公开' : '私密' }}</span>
              <el-switch
                :model-value="item.isPublic"
                @change="(val) => handleVisibilityChange(item, val)"
              />
            </div>
            <el-button text type="primary" :icon="EditPen" @click="handleEdit(item)">编辑</el-button>
            <el-button text type="danger" :icon="Delete" @click="handleDelete(item)">删除</el-button>
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

    <ArticleEditorDialog
      v-model:visible="editorVisible"
      :article="editingArticle"
      @success="handleEditorSuccess"
    />
  </div>
</template>

<style scoped>
.my-article-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding: 8px 4px;
  gap: 16px;
  flex-wrap: wrap;
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
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 4px;
}
.list-card {
  border-radius: 10px;
}
.article-list {
  display: flex;
  flex-direction: column;
}
.article-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 12px;
  border-bottom: 1px solid #f0e6d2;
}
.article-item:last-child {
  border-bottom: none;
}
.item-main {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}
.item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.item-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-summary {
  margin: 0 0 10px;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.item-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.tag-chip {
  cursor: default;
}
.item-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
}
.meta-dot {
  color: #dcdfe6;
}
.meta-views {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.item-ops {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: flex-start;
  gap: 4px;
  flex-shrink: 0;
}
.visibility-line {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
