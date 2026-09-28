<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { View, EditPen, Notebook, Star, UserFilled, Search } from '@element-plus/icons-vue'
import { getArticles } from '@/api/article'
import { useUserStore } from '@/stores/user'
import ArticleEditorDialog from './ArticleEditorDialog.vue'
import UserAvatar from '@/components/UserAvatar.vue'

const router = useRouter()
const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)

const loading = ref(false)
const list = ref([])
const total = ref(0)
const activeChannel = ref('all')
const activeTag = ref('')
const editorVisible = ref(false)
// 排序方式: latest 最新发布 / hot 热度（点赞数+评论数）优先
const activeSort = ref('latest')
// 综合检索关键字（匹配笔名/标题/标签）
const keyword = ref('')
const keywordInput = ref('')

const page = reactive({ current: 1, size: 10 })

// 频道定义：all 不传 channel 参数，chat/tech 与后端一致
const channels = [
  { value: 'all', label: '全部' },
  { value: 'chat', label: '杂谈' },
  { value: 'tech', label: '技术笔记' }
]

const channelLabel = (value) => channels.find((c) => c.value === value)?.label || '杂谈'

async function fetchList() {
  loading.value = true
  try {
    const params = {
      page: page.current,
      size: page.size,
      sort: activeSort.value
    }
    if (activeChannel.value !== 'all') params.channel = activeChannel.value
    if (activeTag.value) params.tag = activeTag.value
    if (keyword.value) params.keyword = keyword.value
    const res = await getArticles(params)
    list.value = res.data?.list || []
    total.value = Number(res.data?.total || 0)
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleChannelChange() {
  activeTag.value = ''
  page.current = 1
  fetchList()
}

function handleSortChange() {
  page.current = 1
  fetchList()
}

function handleSearch() {
  keyword.value = keywordInput.value.trim()
  page.current = 1
  fetchList()
}

function clearSearch() {
  keyword.value = ''
  keywordInput.value = ''
  page.current = 1
  fetchList()
}

function handleTagClick(tag) {
  activeTag.value = tag
  page.current = 1
  fetchList()
}

function clearTag() {
  activeTag.value = ''
  page.current = 1
  fetchList()
}

function handlePageChange(p) {
  page.current = p
  fetchList()
}

function openDetail(item) {
  router.push(`/articles/${item.id}`)
}

// 游客写文章：提示并引导登录（游客模式全站统一交互）
function requireLogin(action) {
  if (isLoggedIn.value) {
    action()
    return
  }
  ElMessage.warning('请先登录后再操作')
  router.push({ path: '/login', query: { redirect: '/articles' } })
}

function openEditor() {
  requireLogin(() => {
    editorVisible.value = true
  })
}

function goMyArticles() {
  requireLogin(() => router.push('/articles/mine'))
}

function goFavorites() {
  requireLogin(() => router.push('/articles/favorites'))
}

function goFollowing() {
  requireLogin(() => router.push('/articles/following'))
}

function handleEditorSuccess() {
  page.current = 1
  fetchList()
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="article-page">
    <div class="page-header">
      <div class="header-text">
        <h2 class="page-title">✨ 萤火交流会</h2>
        <p class="page-subtitle">用笔名写下你的随记，在广场与陌生人交换微光</p>
      </div>
      <div class="header-actions">
        <el-button :icon="UserFilled" @click="goFollowing">我的关注</el-button>
        <el-button :icon="Star" @click="goFavorites">我的收藏</el-button>
        <el-button :icon="Notebook" @click="goMyArticles">我的随记</el-button>
        <el-button type="primary" :icon="EditPen" @click="openEditor">写点什么</el-button>
      </div>
    </div>

    <!-- 频道切换 / 排序 / 综合检索 -->
    <div class="channel-bar">
      <el-radio-group v-model="activeChannel" @change="handleChannelChange">
        <el-radio-button v-for="c in channels" :key="c.value" :value="c.value">
          {{ c.label }}
        </el-radio-button>
      </el-radio-group>
      <el-radio-group v-model="activeSort" @change="handleSortChange">
        <el-radio-button value="latest">最新</el-radio-button>
        <el-radio-button value="hot">最热</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="keywordInput"
        class="search-input"
        placeholder="可通过标签或笔名/标题关键字检索文章"
        clearable
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
        @clear="clearSearch"
      >
        <template #append>
          <el-button :icon="Search" @click="handleSearch" />
        </template>
      </el-input>
      <el-tag
        v-if="keyword"
        closable
        type="info"
        effect="light"
        @close="clearSearch"
        class="tag-filter"
      >
        关键字：{{ keyword }}
      </el-tag>
      <el-tag
        v-if="activeTag"
        closable
        type="warning"
        effect="light"
        @close="clearTag"
        class="tag-filter"
      >
        标签筛选：{{ activeTag }}
      </el-tag>
    </div>

    <el-card v-loading="loading" shadow="never" class="list-card">
      <el-empty v-if="!loading && list.length === 0" description="还没有文章，来当第一个点灯的人吧" />

      <ul v-else class="article-list">
        <li
          v-for="item in list"
          :key="item.id"
          class="article-item"
          @click="openDetail(item)"
        >
          <div class="item-head">
            <el-tag size="small" :type="item.channel === 'tech' ? 'success' : 'warning'" effect="plain">
              {{ channelLabel(item.channel) }}
            </el-tag>
            <span class="item-title">{{ item.title }}</span>
          </div>
          <p class="item-summary">{{ item.summary }}</p>
          <div class="item-foot">
            <div class="item-tags">
              <el-tag
                v-for="tag in item.tags"
                :key="tag"
                size="small"
                effect="light"
                class="tag-chip"
                @click.stop="handleTagClick(tag)"
              >
                # {{ tag }}
              </el-tag>
            </div>
            <div class="item-meta">
              <UserAvatar class="meta-avatar" :url="item.authorAvatar" :user-id="item.authorId" :size="18" />
              <span class="meta-author">{{ item.penName }}</span>
              <span class="meta-dot">·</span>
              <span class="meta-time">{{ item.createdAt }}</span>
              <span class="meta-dot">·</span>
              <span class="meta-views"><el-icon><View /></el-icon> {{ item.viewCount || 0 }}</span>
              <span class="meta-dot">·</span>
              <span class="meta-likes">❤️ {{ item.likeCount || 0 }}</span>
              <span class="meta-dot">·</span>
              <span class="meta-comments">💬 {{ item.commentCount || 0 }}</span>
            </div>
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

    <!-- 发布文章弹窗 -->
    <ArticleEditorDialog v-model:visible="editorVisible" :article="null" @success="handleEditorSuccess" />
  </div>
</template>

<style scoped>
.article-page {
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
.channel-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 4px;
  flex-wrap: wrap;
}
.search-input {
  width: 220px;
}
.tag-filter {
  cursor: default;
}
.list-card {
  border-radius: 10px;
}
.article-list {
  display: flex;
  flex-direction: column;
}
.article-item {
  padding: 18px 12px;
  border-bottom: 1px solid #f0e6d2;
  cursor: pointer;
  transition: background 0.2s ease;
}
.article-item:last-child {
  border-bottom: none;
}
.article-item:hover {
  background: #fef7ea;
}
.item-head {
  display: flex;
  align-items: center;
  gap: 10px;
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
.item-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.item-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.tag-chip {
  cursor: pointer;
}
.item-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}
.meta-author {
  color: #b8821f;
}
.meta-dot {
  color: #dcdfe6;
}
.meta-views {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
