<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Back, View, Search } from '@element-plus/icons-vue'
import { getMyFollows, unfollowAuthor, updateFollowRemark, getFollowingFeed } from '@/api/article'
import { useUserStore } from '@/stores/user'
import UserAvatar from '@/components/UserAvatar.vue'

const router = useRouter()
const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)

// 当前激活的页签: follows 关注列表 / feed 动态
const activeTab = ref('follows')

// ============================ 关注列表 ============================

const followLoading = ref(false)
const follows = ref([])
const followTotal = ref(0)
const followPage = reactive({ current: 1, size: 10 })

async function fetchFollows() {
  followLoading.value = true
  try {
    const res = await getMyFollows({ page: followPage.current, size: followPage.size })
    follows.value = res.data?.list || []
    followTotal.value = Number(res.data?.total || 0)
  } catch (e) {
    follows.value = []
    followTotal.value = 0
  } finally {
    followLoading.value = false
  }
}

/** 修改关注备注：区分不同账户使用同一笔名的情况 */
async function handleEditRemark(follow) {
  let remark
  try {
    const res = await ElMessageBox.prompt(
      `为笔名「${follow.penName}」设置备注（可留空清除），用于区分使用同一笔名的不同作者`,
      '编辑备注',
      {
        confirmButtonText: '保存',
        cancelButtonText: '取消',
        inputValue: follow.remark || '',
        inputPlaceholder: '例如：写技术文的那个',
        inputValidator: (v) => !v || v.trim().length <= 50 || '备注最长50字'
      }
    )
    remark = res.value?.trim() || ''
  } catch (e) {
    return
  }
  try {
    await updateFollowRemark(follow.id, remark)
    follow.remark = remark || null
    ElMessage.success('备注已保存')
  } catch (e) {
    // 错误已统一提示
  }
}

async function handleUnfollow(follow) {
  try {
    await ElMessageBox.confirm(
      `确定不再关注笔名「${follow.penName}」吗？`,
      '取消关注',
      { type: 'warning', confirmButtonText: '取消关注', cancelButtonText: '再想想' }
    )
  } catch (e) {
    return
  }
  try {
    await unfollowAuthor(follow.id)
    follows.value = follows.value.filter(f => f.id !== follow.id)
    followTotal.value = Math.max(followTotal.value - 1, 0)
    ElMessage.success('已取消关注')
    // 关注列表变化后同步刷新动态
    if (activeTab.value === 'feed') {
      feedPage.current = 1
      fetchFeed()
    }
  } catch (e) {
    // 错误已统一提示
  }
}

function handleFollowPageChange(p) {
  followPage.current = p
  fetchFollows()
}

// ============================ 关注动态 ============================

const feedLoading = ref(false)
const feedList = ref([])
const feedTotal = ref(0)
const feedPage = reactive({ current: 1, size: 10 })

async function fetchFeed() {
  feedLoading.value = true
  try {
    const res = await getFollowingFeed({ page: feedPage.current, size: feedPage.size })
    feedList.value = res.data?.list || []
    feedTotal.value = Number(res.data?.total || 0)
  } catch (e) {
    feedList.value = []
    feedTotal.value = 0
  } finally {
    feedLoading.value = false
  }
}

function handleFeedPageChange(p) {
  feedPage.current = p
  fetchFeed()
}

// ============================ 通用 ============================

const channels = { chat: '杂谈', tech: '技术笔记' }

function openDetail(item) {
  router.push(`/articles/${item.id}`)
}

function formatTime(t) {
  return t ? String(t).replace('T', ' ').substring(0, 16) : ''
}

function handleTabChange(tab) {
  if (tab === 'follows' && !follows.value.length) fetchFollows()
  if (tab === 'feed' && !feedList.value.length) fetchFeed()
}

onMounted(() => {
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录后再操作')
    router.push({ path: '/login', query: { redirect: '/articles/following' } })
    return
  }
  fetchFollows()
})
</script>

<template>
  <div class="following-page">
    <div class="page-header">
      <el-button text :icon="Back" @click="router.push('/articles')">返回广场</el-button>
      <h2 class="page-title">👀 我的关注</h2>
      <p class="page-subtitle">追踪喜欢的笔名作者，第一时间看到他们的新随记</p>
    </div>

    <el-tabs v-model="activeTab" class="follow-tabs" @tab-change="handleTabChange">
      <!-- 关注列表 -->
      <el-tab-pane label="关注列表" name="follows">
        <el-card v-loading="followLoading" shadow="never" class="panel-card">
          <el-empty v-if="!followLoading && follows.length === 0" description="还没有关注任何作者，去文章详情页点击「+ 关注作者」吧" />

          <ul v-else class="follow-list">
            <li v-for="follow in follows" :key="follow.id" class="follow-item">
              <div class="follow-info">
                <div class="pen-line">
                  <span class="pen-name">✍️ {{ follow.penName }}</span>
                  <el-tag v-if="follow.remark" size="small" type="warning" effect="light">
                    {{ follow.remark }}
                  </el-tag>
                </div>
                <div class="sub-line">
                  <span>共 {{ follow.articleCount || 0 }} 篇可见随记</span>
                  <span class="meta-dot">·</span>
                  <span>关注于 {{ formatTime(follow.createdAt) }}</span>
                </div>
              </div>
              <div class="follow-ops">
                <el-button size="small" @click="handleEditRemark(follow)">备注</el-button>
                <el-button size="small" type="danger" plain @click="handleUnfollow(follow)">取关</el-button>
              </div>
            </li>
          </ul>

          <div v-if="followTotal > followPage.size" class="pagination-wrap">
            <el-pagination
              background
              layout="prev, pager, next"
              :current-page="followPage.current"
              :page-size="followPage.size"
              :total="followTotal"
              @current-change="handleFollowPageChange"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 关注动态 -->
      <el-tab-pane label="动态" name="feed">
        <el-card v-loading="feedLoading" shadow="never" class="panel-card">
          <el-empty v-if="!feedLoading && feedList.length === 0" description="关注作者还没有发布新的可见随记" />

          <ul v-else class="feed-list">
            <li
              v-for="item in feedList"
              :key="item.id"
              class="feed-item"
              @click="openDetail(item)"
            >
              <div class="item-head">
                <el-tag size="small" :type="item.channel === 'tech' ? 'success' : 'warning'" effect="plain">
                  {{ channels[item.channel] || '杂谈' }}
                </el-tag>
                <span class="item-title">{{ item.title }}</span>
              </div>
              <p class="item-summary">{{ item.summary }}</p>
              <div class="item-meta">
                <UserAvatar :url="item.authorAvatar" :user-id="item.authorId" :size="18" />
                <span class="meta-author">{{ item.penName }}</span>
                <span class="meta-dot">·</span>
                <span>{{ formatTime(item.createdAt) }}</span>
                <span class="meta-dot">·</span>
                <span class="meta-views"><el-icon><View /></el-icon> {{ item.viewCount || 0 }}</span>
                <span class="meta-dot">·</span>
                <span>❤️ {{ item.likeCount || 0 }}</span>
                <span class="meta-dot">·</span>
                <span>💬 {{ item.commentCount || 0 }}</span>
              </div>
            </li>
          </ul>

          <div v-if="feedTotal > feedPage.size" class="pagination-wrap">
            <el-pagination
              background
              layout="prev, pager, next"
              :current-page="feedPage.current"
              :page-size="feedPage.size"
              :total="feedTotal"
              @current-change="handleFeedPageChange"
            />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.following-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.page-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 4px;
}
.page-title {
  margin: 0;
  font-size: 22px;
  color: #303133;
}
.page-subtitle {
  margin: 0;
  color: #909399;
  font-size: 13px;
}
.follow-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
.panel-card {
  border-radius: 10px;
}
.follow-list,
.feed-list {
  display: flex;
  flex-direction: column;
}
.follow-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 16px 8px;
  border-bottom: 1px solid #f0e6d2;
}
.follow-item:last-child {
  border-bottom: none;
}
.pen-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.pen-name {
  font-size: 15px;
  font-weight: 600;
  color: #b8821f;
}
.sub-line {
  font-size: 12px;
  color: #909399;
}
.meta-dot {
  margin: 0 6px;
  color: #dcdfe6;
}
.follow-ops {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.feed-item {
  padding: 16px 8px;
  border-bottom: 1px solid #f0e6d2;
  cursor: pointer;
  transition: background 0.2s ease;
}
.feed-item:hover {
  background: #fef7ea;
}
.feed-item:last-child {
  border-bottom: none;
}
.item-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.item-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-summary {
  margin: 0 0 8px;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.item-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}
.meta-author {
  color: #b8821f;
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
