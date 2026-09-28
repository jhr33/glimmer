<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElImageViewer } from 'element-plus'
import { View, EditPen, Delete, Back, Star, ChatDotRound } from '@element-plus/icons-vue'
import {
  getArticle,
  deleteArticle,
  getArticleComments,
  createArticleComment,
  deleteArticleComment,
  toggleArticleLike,
  toggleCommentLike,
  followAuthor,
  unfollowAuthor,
  getFavoriteFolders,
  createFavoriteFolder,
  collectArticle,
  uncollectArticle
} from '@/api/article'
import { createReport } from '@/api/report'
import { useUserStore } from '@/stores/user'
import { renderArticleContent, extractInlineImageUrls } from '@/utils/articleMarkdown'
import ArticleEditorDialog from './ArticleEditorDialog.vue'
import UserAvatar from '@/components/UserAvatar.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const article = ref(null)
const notFound = ref(false)
const editorVisible = ref(false)

const channels = { chat: '杂谈', tech: '技术笔记' }
const isOwner = computed(() => article.value?.owner === true)

// 审核状态横幅（作者视角）：待审核 / 已打回
const reviewBanner = computed(() => {
  if (!article.value || !article.value.owner) return null
  if (article.value.reviewStatus === 'PENDING_REVIEW') {
    return { type: 'warning', text: '本文正在等待管理员审核，审核通过前其他用户无法看到。' }
  }
  if (article.value.reviewStatus === 'RETURNED') {
    return {
      type: 'error',
      text: `文章因内容不合适被屏蔽打回：${article.value.reviewReason || '未填写原因'}。修改后重新提交，管理员审核通过后才会重新展示。`
    }
  }
  return null
})

// ============================ 评论 ============================

const commentLoading = ref(false)
const comments = ref([])
const commentContent = ref('')
const commentSubmitting = ref(false)
// 当前回复的评论（一级评论为 null）
const replyTo = ref(null)
// 各主评论下回复是否展开全部（默认折叠，仅展示前2条）
const expandedThreads = ref({})
const DEFAULT_REPLY_VISIBLE = 2

/**
 * 将扁平评论列表组装为二级结构：主评论 + 其下全部回复。
 * 回复的 parentId 可能指向另一条回复，沿链向上找到所属主评论归组。
 */
const threadComments = computed(() => {
  const commentMap = new Map(comments.value.map(c => [c.id, c]))
  // 收集每个主评论下的回复（保持时间正序）
  const replyMap = new Map()
  const rootOf = (comment) => {
    let current = comment
    // 防御性向上查找，避免异常数据造成死循环
    for (let i = 0; i < 10 && current?.parentId; i++) {
      const parent = commentMap.get(current.parentId)
      if (!parent) break
      current = parent
    }
    return current
  }
  comments.value.forEach(c => {
    if (c.parentId) {
      const root = rootOf(c)
      if (root && root.id !== c.id) {
        if (!replyMap.has(root.id)) replyMap.set(root.id, [])
        replyMap.get(root.id).push(c)
      }
    }
  })
  return comments.value
    .filter(c => !c.parentId)
    .map(root => ({ ...root, replies: replyMap.get(root.id) || [] }))
})

/** 主评论下当前可见的回复：折叠时只取前2条 */
function visibleReplies(thread) {
  return expandedThreads.value[thread.id]
    ? thread.replies
    : thread.replies.slice(0, DEFAULT_REPLY_VISIBLE)
}

function toggleThread(rootId) {
  expandedThreads.value[rootId] = !expandedThreads.value[rootId]
}
// 交流会身份偏好：默认匿名，与篝火身份切换保持一致的 localStorage 记忆
const DISPLAY_MODE_KEY = 'glimmer_article_display_mode'
const displayMode = ref(localStorage.getItem(DISPLAY_MODE_KEY) || 'anonymous')

async function fetchComments() {
  commentLoading.value = true
  try {
    const res = await getArticleComments(route.params.id)
    comments.value = res.data || []
  } catch (e) {
    comments.value = []
  } finally {
    commentLoading.value = false
  }
}

function changeDisplayMode(mode) {
  displayMode.value = mode
  localStorage.setItem(DISPLAY_MODE_KEY, mode)
}

function startReply(comment) {
  if (!userStore.isLoggedIn) {
    promptLogin()
    return
  }
  replyTo.value = comment
}

function cancelReply() {
  replyTo.value = null
}

async function submitComment() {
  if (!userStore.isLoggedIn) {
    promptLogin()
    return
  }
  const content = commentContent.value.trim()
  if (!content) {
    ElMessage.warning('说点什么再发送吧')
    return
  }
  commentSubmitting.value = true
  try {
    const res = await createArticleComment(article.value.id, {
      content,
      parentId: replyTo.value?.id || null,
      displayMode: displayMode.value
    })
    comments.value.push(res.data)
    // 回复发表后自动展开所属主评论的回复区（被回复的可能本身也是一条回复）
    if (replyTo.value) {
      const commentMap = new Map(comments.value.map(c => [c.id, c]))
      let root = replyTo.value
      for (let i = 0; i < 10 && root.parentId; i++) {
        root = commentMap.get(root.parentId) || root
      }
      expandedThreads.value[root.id] = true
    }
    commentContent.value = ''
    replyTo.value = null
    // 本地维护评论数，避免重新拉详情
    article.value.commentCount = (article.value.commentCount || 0) + 1
    ElMessage.success('评论成功')
  } catch (e) {
    // 错误已统一提示（含禁言/封禁提示与申诉邮箱）
  } finally {
    commentSubmitting.value = false
  }
}

async function handleDeleteComment(comment) {
  try {
    await ElMessageBox.confirm('确定删除这条评论吗？', '删除评论', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await deleteArticleComment(comment.id)
    comments.value = comments.value.filter(c => c.id !== comment.id)
    article.value.commentCount = Math.max((article.value.commentCount || 1) - 1, 0)
    ElMessage.success('已删除')
  } catch (e) {
    // 错误已统一提示
  }
}

// ============================ 图片预览 ============================

const imageViewerVisible = ref(false)
const imageViewerIndex = ref(0)
// 当前预览的图片列表（内嵌图与历史九宫格共用一个预览器）
const viewerUrlList = ref([])

function openViewer(urlList, index) {
  viewerUrlList.value = urlList
  imageViewerIndex.value = index
  imageViewerVisible.value = true
}

function previewImage(index) {
  openViewer(article.value?.images || [], index)
}

// 正文中内嵌的图片（Markdown 标记渲染而来）
const inlineImages = computed(() =>
  article.value ? extractInlineImageUrls(article.value.content) : []
)

// 渲染后的正文 HTML（已在工具内做 HTML 转义，仅放行 img/strong/br，防 XSS）
const renderedContent = computed(() =>
  article.value ? renderArticleContent(article.value.content) : ''
)

// 历史文章（图片仍走独立 images 字段、正文无内嵌标记）才显示九宫格
const showLegacyGrid = computed(() =>
  inlineImages.value.length === 0 && (article.value?.images?.length || 0) > 0
)

// 点击正文区域：点到内嵌图片则打开预览（事件委托）
function handleContentClick(event) {
  const target = event.target
  if (target.tagName === 'IMG') {
    const src = target.getAttribute('src')
    const index = inlineImages.value.indexOf(src)
    openViewer(inlineImages.value, index >= 0 ? index : 0)
  }
}

// ============================ 点赞 ============================

const likePending = ref(false)

async function handleLike() {
  if (!userStore.isLoggedIn) {
    promptLogin()
    return
  }
  // 防双击：请求进行中忽略后续点击，最终状态以后端返回为准
  if (likePending.value) return
  likePending.value = true
  try {
    const res = await toggleArticleLike(article.value.id)
    article.value.liked = res.data.liked
    article.value.likeCount = res.data.likeCount
    ElMessage.success(res.data.liked ? '点赞成功' : '已取消点赞')
  } catch (e) {
    // 错误已统一提示
  } finally {
    likePending.value = false
  }
}

// ============================ 评论点赞 ============================

const commentLikePending = ref(false)

/**
 * 评论点赞切换（主评论与二级回复通用）。
 * threadComments 计算属性对主评论做了浅拷贝，因此必须通过 id
 * 找到 comments 源数组中的原对象修改，响应式才能传导到视图。
 */
async function handleCommentLike(comment) {
  if (!userStore.isLoggedIn) {
    promptLogin()
    return
  }
  if (commentLikePending.value) return
  commentLikePending.value = true
  try {
    const res = await toggleCommentLike(comment.id)
    const target = comments.value.find(c => c.id === comment.id)
    if (target) {
      target.liked = res.data.liked
      target.likeCount = res.data.likeCount
    }
    ElMessage.success(res.data.liked ? '点赞成功' : '已取消点赞')
  } catch (e) {
    // 错误已统一提示
  } finally {
    commentLikePending.value = false
  }
}

// ============================ 关注作者 ============================

const followPending = ref(false)

/** 关注/取关当前文章作者（关注目标为「账户+笔名」组合） */
async function toggleFollowAuthor() {
  if (!userStore.isLoggedIn) {
    promptLogin()
    return
  }
  if (followPending.value) return
  followPending.value = true
  try {
    if (article.value.following) {
      await unfollowAuthor(article.value.followId)
      article.value.following = false
      article.value.followId = null
      ElMessage.success('已取消关注')
    } else {
      await followAuthor({
        followeeId: article.value.userId,
        penName: article.value.penName
      })
      article.value.following = true
      ElMessage.success(`已关注笔名「${article.value.penName}」，可在"我的关注"查看其动态`)
    }
  } catch (e) {
    // 错误已统一提示（重复关注冲突等）
  } finally {
    followPending.value = false
  }
}

// ============================ 举报 ============================

/** 举报当前文章：提交后文章进入管理员审核流程 */
async function handleReport() {
  if (!userStore.isLoggedIn) {
    promptLogin()
    return
  }
  let reason
  try {
    const res = await ElMessageBox.prompt('请填写举报理由，管理员将审核该文章', '举报文章', {
      confirmButtonText: '提交举报',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：包含违规内容 / 人身攻击等',
      inputValidator: (v) => (v && v.trim().length >= 2) || '请输入至少2个字的举报理由'
    })
    reason = res.value.trim()
  } catch (e) {
    return
  }
  try {
    await createReport({
      targetType: 'article',
      targetId: article.value.id,
      content: reason
    })
    ElMessage.success('举报已提交，文章将进入审核流程，感谢你维护社区环境')
  } catch (e) {
    // 错误已统一提示（重复举报等）
  }
}

// 举报评论（处罚期间点击会收到后端 4019 提示"你在禁言中，当前不可举报他人"）
async function handleReportComment(comment) {
  if (!userStore.isLoggedIn) {
    promptLogin()
    return
  }
  let reason
  try {
    const res = await ElMessageBox.prompt('请填写举报理由，管理员将审核该评论', '举报评论', {
      confirmButtonText: '提交举报',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：包含违规内容 / 人身攻击 / 广告引流等',
      inputValidator: (v) => (v && v.trim().length >= 2) || '请输入至少2个字的举报理由'
    })
    reason = res.value.trim()
  } catch (e) {
    return
  }
  try {
    await createReport({
      targetType: 'article_comment',
      targetId: comment.id,
      content: reason
    })
    ElMessage.success('举报已提交，管理员将尽快审核，感谢你维护社区环境')
  } catch (e) {
    // 错误已统一提示（禁言限制/重复举报等）
  }
}

// ============================ 收藏 ============================

const collectDialogVisible = ref(false)
const folders = ref([])
const selectedFolderId = ref(null)
const newFolderName = ref('')
const folderSaving = ref(false)
const collectPending = ref(false)

async function openCollectDialog() {
  if (!userStore.isLoggedIn) {
    promptLogin()
    return
  }
  collectDialogVisible.value = true
  newFolderName.value = ''
  await loadFolders()
}

async function loadFolders() {
  try {
    const res = await getFavoriteFolders()
    folders.value = res.data || []
    // 默认选中当前收藏所在文件夹，否则选第一个
    if (article.value.folderId) {
      selectedFolderId.value = article.value.folderId
    } else if (!selectedFolderId.value && folders.value.length) {
      selectedFolderId.value = folders.value[0].id
    }
  } catch (e) {
    folders.value = []
  }
}

async function handleCreateFolder() {
  const name = newFolderName.value.trim()
  if (!name) {
    ElMessage.warning('请输入文件夹名称')
    return
  }
  folderSaving.value = true
  try {
    const res = await createFavoriteFolder(name)
    folders.value.push(res.data)
    selectedFolderId.value = res.data.id
    newFolderName.value = ''
    ElMessage.success('文件夹已创建')
  } catch (e) {
    // 同名冲突等错误已统一提示
  } finally {
    folderSaving.value = false
  }
}

async function confirmCollect() {
  if (collectPending.value) return
  collectPending.value = true
  try {
    // 未选文件夹时不传 folderId，后端自动选用/创建"默认收藏"
    const res = await collectArticle(article.value.id, selectedFolderId.value || null)
    article.value.collected = res.data.collected
    article.value.folderId = res.data.folderId
    article.value.favoriteCount = res.data.favoriteCount
    collectDialogVisible.value = false
    ElMessage.success('已收藏')
  } catch (e) {
    // 错误已统一提示
  } finally {
    collectPending.value = false
  }
}

async function handleUncollect() {
  if (collectPending.value) return
  collectPending.value = true
  try {
    const res = await uncollectArticle(article.value.id)
    article.value.collected = res.data.collected
    article.value.folderId = null
    article.value.favoriteCount = res.data.favoriteCount
    collectDialogVisible.value = false
    ElMessage.success('已取消收藏')
  } catch (e) {
    // 错误已统一提示
  } finally {
    collectPending.value = false
  }
}

// ============================ 通用 ============================

function promptLogin() {
  ElMessage.info('请先登录后再操作')
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}

async function fetchDetail() {
  loading.value = true
  notFound.value = false
  try {
    const res = await getArticle(route.params.id)
    article.value = res.data
    fetchComments()
  } catch (e) {
    // 私密文章非作者访问 / 文章不存在：后端统一返回404
    article.value = null
    notFound.value = true
  } finally {
    loading.value = false
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm(`确定删除文章「${article.value.title}」吗？删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await deleteArticle(article.value.id)
    ElMessage.success('已删除')
    router.push('/articles/mine')
  } catch (e) {
    // 错误已统一提示
  }
}

function handleEditorSuccess() {
  editorVisible.value = false
  fetchDetail()
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}

onMounted(() => {
  fetchDetail()
})
</script>

<template>
  <div class="article-detail-page" v-loading="loading">
    <el-result v-if="notFound" icon="warning" title="文章不存在或未公开">
      <template #extra>
        <el-button type="primary" @click="router.push('/articles')">返回交流会广场</el-button>
      </template>
    </el-result>

    <template v-else-if="article">
      <el-card shadow="never" class="detail-card">
        <div class="top-bar">
          <el-button text :icon="Back" @click="router.push('/articles')">返回广场</el-button>
          <div class="top-bar-right">
            <el-button
              v-if="!isOwner && userStore.isLoggedIn"
              size="small"
              type="warning"
              plain
              @click="handleReport"
            >举报</el-button>
            <template v-if="isOwner">
              <el-button size="small" :icon="EditPen" @click="editorVisible = true">编辑</el-button>
              <el-button size="small" type="danger" plain :icon="Delete" @click="handleDelete">删除</el-button>
            </template>
          </div>
        </div>

        <div class="channel-line">
          <el-tag size="small" :type="article.channel === 'tech' ? 'success' : 'warning'" effect="plain">
            {{ channels[article.channel] || '杂谈' }}
          </el-tag>
          <el-tag v-if="!article.isPublic" size="small" type="info" effect="plain">私密</el-tag>
          <!-- 审核状态（仅作者可见）：待审核 / 已打回 -->
          <el-tag v-if="isOwner && article.reviewStatus === 'PENDING_REVIEW'" size="small" type="warning">
            待审核
          </el-tag>
          <el-tag v-if="isOwner && article.reviewStatus === 'RETURNED'" size="small" type="danger">
            已打回
          </el-tag>
        </div>

        <!-- 审核状态横幅（仅作者可见） -->
        <el-alert
          v-if="reviewBanner"
          :title="reviewBanner.text"
          :type="reviewBanner.type"
          show-icon
          :closable="false"
          class="review-banner"
        />

        <h1 class="article-title">{{ article.title }}</h1>

        <div class="article-meta">
          <UserAvatar :url="article.authorAvatar" :user-id="article.userId" :size="28" />
          <span class="pen-name">{{ article.penName }}</span>
          <el-button
            v-if="!isOwner"
            class="follow-btn"
            size="small"
            round
            :type="article.following ? 'info' : 'primary'"
            :plain="article.following"
            :loading="followPending"
            @click="toggleFollowAuthor"
          >
            {{ article.following ? '已关注' : '+ 关注作者' }}
          </el-button>
          <span class="meta-dot">·</span>
          <span>{{ article.createdAt }}</span>
          <span class="meta-dot">·</span>
          <span class="meta-views"><el-icon><View /></el-icon> {{ article.viewCount || 0 }} 次阅读</span>
        </div>

        <div v-if="article.tags?.length" class="tag-list">
          <el-tag v-for="tag in article.tags" :key="tag" effect="light" class="tag-chip"># {{ tag }}</el-tag>
        </div>

        <el-divider />

        <!-- 正文：Markdown 渲染（内嵌图片按插入位置显示，点击可预览） -->
        <div class="article-content" v-html="renderedContent" @click="handleContentClick"></div>

        <!-- 历史文章兼容：旧版图片独立字段、正文无内嵌标记时才展示九宫格 -->
        <div v-if="showLegacyGrid" class="image-grid">
          <div
            v-for="(img, index) in article.images"
            :key="index"
            class="image-item"
            @click="previewImage(index)"
          >
            <el-image :src="img" fit="cover" lazy />
          </div>
        </div>

        <!-- 互动操作条：点赞 / 收藏 / 评论数 -->
        <div class="action-bar">
          <el-button
            round
            :type="article.liked ? 'danger' : 'default'"
            :plain="!article.liked"
            @click="handleLike"
          >
            <span class="action-inner">
              {{ article.liked ? '❤️ 已赞' : '🤍 点赞' }}
              <span class="action-count">{{ article.likeCount || 0 }}</span>
            </span>
          </el-button>
          <el-button
            round
            :type="article.collected ? 'warning' : 'default'"
            :plain="!article.collected"
            @click="openCollectDialog"
          >
            <span class="action-inner">
              <el-icon class="star-icon"><Star /></el-icon>
              {{ article.collected ? '已收藏' : '收藏' }}
              <span class="action-count">{{ article.favoriteCount || 0 }}</span>
            </span>
          </el-button>
          <span class="comment-count">
            <el-icon><ChatDotRound /></el-icon>
            {{ article.commentCount || 0 }} 条评论
          </span>
        </div>
      </el-card>

      <!-- 评论区 -->
      <el-card shadow="never" class="comment-card">
        <template #header>
          <span class="comment-header-title">评论区（{{ comments.length }}）</span>
        </template>

        <!-- 评论输入区 -->
        <div class="comment-editor">
          <div class="identity-line">
            <span class="identity-label">我的身份：</span>
            <el-radio-group :model-value="displayMode" size="small" @change="changeDisplayMode">
              <el-radio-button value="anonymous">匿名昵称</el-radio-button>
              <el-radio-button value="nickname">我的昵称</el-radio-button>
            </el-radio-group>
            <span class="identity-tip">
              {{ displayMode === 'anonymous'
                ? '将以统一匿名昵称展示（24小时内保持同一个）'
                : `将以昵称「${userStore.userInfo?.nickname || '未设置昵称'}」展示` }}
            </span>
          </div>
          <div v-if="replyTo" class="reply-tip">
            回复 @{{ replyTo.displayName }}
            <el-button text type="primary" size="small" @click="cancelReply">取消回复</el-button>
          </div>
          <el-input
            v-model="commentContent"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            :placeholder="userStore.isLoggedIn ? '友善交流，从一句评论开始～' : '登录后参与评论'"
          />
          <div class="comment-submit-line">
            <el-button
              type="primary"
              size="small"
              :loading="commentSubmitting"
              @click="submitComment"
            >发表评论</el-button>
          </div>
        </div>

        <el-divider class="comment-divider" />

        <!-- 评论列表（二级结构：主评论 + 缩进回复，回复默认折叠只显2条） -->
        <div v-loading="commentLoading" class="comment-list">
          <div v-if="!threadComments.length" class="comment-empty">还没有评论，来抢沙发吧～</div>

          <div v-for="thread in threadComments" :key="thread.id" class="comment-thread">
            <!-- 主评论 -->
            <div class="comment-item">
              <UserAvatar class="comment-avatar" :url="thread.avatarUrl" :user-id="thread.userId" :size="32" />
              <div class="comment-body">
                <div class="comment-meta">
                  <span class="comment-name" :class="{ 'author-name': thread.isAuthor }">{{ thread.displayName }}</span>
                  <el-tag v-if="thread.isAuthor" size="small" type="warning" effect="dark" class="author-tag">作者</el-tag>
                  <el-tag v-else-if="thread.isAnonymous" size="small" type="info" effect="plain" class="anon-tag">匿名</el-tag>
                  <span class="comment-time">{{ formatTime(thread.createdAt) }}</span>
                </div>
                <div class="comment-content">{{ thread.content }}</div>
                <div class="comment-ops">
                  <el-button
                    text
                    size="small"
                    :type="thread.liked ? 'danger' : 'default'"
                    class="comment-like-btn"
                    @click="handleCommentLike(thread)"
                  >
                    {{ thread.liked ? '❤️' : '🤍' }} {{ thread.likeCount || 0 }}
                  </el-button>
                  <el-button text size="small" type="primary" @click="startReply(thread)">回复</el-button>
                  <el-button
                    v-if="!thread.owner && userStore.isLoggedIn"
                    text
                    size="small"
                    type="warning"
                    @click="handleReportComment(thread)"
                  >举报</el-button>
                  <el-button
                    v-if="thread.owner || userStore.isAdmin"
                    text
                    size="small"
                    type="danger"
                    @click="handleDeleteComment(thread)"
                  >删除</el-button>
                </div>
              </div>
            </div>

            <!-- 二级回复（缩进展示） -->
            <div v-if="thread.replies.length" class="reply-list">
              <div v-for="reply in visibleReplies(thread)" :key="reply.id" class="comment-item reply-item">
                <UserAvatar class="comment-avatar" :url="reply.avatarUrl" :user-id="reply.userId" :size="26" />
                <div class="comment-body">
                  <div class="comment-meta">
                    <span class="comment-name" :class="{ 'author-name': reply.isAuthor }">{{ reply.displayName }}</span>
                    <el-tag v-if="reply.isAuthor" size="small" type="warning" effect="dark" class="author-tag">作者</el-tag>
                    <el-tag v-else-if="reply.isAnonymous" size="small" type="info" effect="plain" class="anon-tag">匿名</el-tag>
                    <span v-if="reply.parentId" class="reply-target">回复 @{{ reply.replyToName }}</span>
                    <span class="comment-time">{{ formatTime(reply.createdAt) }}</span>
                  </div>
                  <div class="comment-content">{{ reply.content }}</div>
                  <div class="comment-ops">
                    <el-button
                      text
                      size="small"
                      :type="reply.liked ? 'danger' : 'default'"
                      class="comment-like-btn"
                      @click="handleCommentLike(reply)"
                    >
                      {{ reply.liked ? '❤️' : '🤍' }} {{ reply.likeCount || 0 }}
                    </el-button>
                    <el-button text size="small" type="primary" @click="startReply(reply)">回复</el-button>
                    <el-button
                      v-if="!reply.owner && userStore.isLoggedIn"
                      text
                      size="small"
                      type="warning"
                      @click="handleReportComment(reply)"
                    >举报</el-button>
                    <el-button
                      v-if="reply.owner || userStore.isAdmin"
                      text
                      size="small"
                      type="danger"
                      @click="handleDeleteComment(reply)"
                    >删除</el-button>
                  </div>
                </div>
              </div>

              <!-- 折叠/展开：超过2条回复时显示 -->
              <div v-if="thread.replies.length > DEFAULT_REPLY_VISIBLE" class="reply-toggle">
                <el-button text size="small" type="primary" @click="toggleThread(thread.id)">
                  {{ expandedThreads[thread.id]
                    ? '收起回复'
                    : `展开全部 ${thread.replies.length} 条回复` }}
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 收藏文件夹选择对话框 -->
      <el-dialog v-model="collectDialogVisible" title="收藏到文件夹" width="420px">
        <div v-if="!folders.length" class="folder-empty">
          还没有收藏文件夹，输入名称创建一个，或直接确认收藏到「默认收藏」。
        </div>
        <el-radio-group v-else v-model="selectedFolderId" class="folder-radio-group">
          <el-radio
            v-for="folder in folders"
            :key="folder.id"
            :value="folder.id"
            class="folder-radio"
          >
            <span class="folder-name">📁 {{ folder.name }}</span>
            <span class="folder-count">{{ folder.collectCount }} 篇</span>
          </el-radio>
        </el-radio-group>

        <div class="new-folder-line">
          <el-input
            v-model="newFolderName"
            size="small"
            maxlength="30"
            placeholder="新建文件夹（自定义名称）"
            @keyup.enter="handleCreateFolder"
          />
          <el-button size="small" :loading="folderSaving" @click="handleCreateFolder">新建</el-button>
        </div>

        <template #footer>
          <el-button v-if="article.collected" type="danger" plain :loading="collectPending" @click="handleUncollect">
            取消收藏
          </el-button>
          <el-button @click="collectDialogVisible = false">关闭</el-button>
          <el-button type="primary" :loading="collectPending" @click="confirmCollect">
            {{ article.collected ? '保存到该文件夹' : '确认收藏' }}
          </el-button>
        </template>
      </el-dialog>

      <ArticleEditorDialog
        v-model:visible="editorVisible"
        :article="article"
        @success="handleEditorSuccess"
      />
    </template>

    <!-- 图片预览 -->
    <el-image-viewer
      v-if="imageViewerVisible"
      :url-list="viewerUrlList"
      :initial-index="imageViewerIndex"
      @close="imageViewerVisible = false"
    />
  </div>
</template>

<style scoped>
/* 图片九宫格 */
.image-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin: 16px 0;
}
.image-item {
  aspect-ratio: 1;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  background: #f5f5f5;
}
.image-item :deep(.el-image) {
  width: 100%;
  height: 100%;
}

.detail-card {
  border-radius: 10px;
}
.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.top-bar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}
.review-banner {
  margin-bottom: 12px;
}
.follow-btn {
  margin-left: 10px;
}
.comment-like-btn {
  min-width: 0;
}
.comment-ops {
  margin-top: 2px;
}
.channel-line {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}
.article-title {
  margin: 0 0 12px;
  font-size: 26px;
  font-weight: 700;
  color: #303133;
  line-height: 1.4;
}
.article-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #909399;
}
.pen-name {
  color: #b8821f;
  font-weight: 600;
}
.meta-dot {
  color: #dcdfe6;
}
.meta-views {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.tag-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
}
.article-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 2;
  color: #303133;
  font-size: 15px;
}
/* v-html 渲染出的内嵌图片样式（scoped 需用 :deep 穿透） */
.article-content :deep(.inline-article-img) {
  display: block;
  max-width: 100%;
  margin: 12px auto;
  border-radius: 8px;
  cursor: zoom-in;
}

/* 互动操作条 */
.action-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 28px;
  padding-top: 18px;
  border-top: 1px solid #f0f0f0;
}
.action-inner {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.star-icon {
  margin-right: 2px;
}
.action-count {
  font-size: 13px;
  color: #909399;
}
.comment-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #909399;
}

/* 评论区 */
.comment-card {
  margin-top: 16px;
  border-radius: 10px;
}
.comment-header-title {
  font-weight: 600;
  color: #303133;
}
.identity-line {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.identity-label {
  font-size: 13px;
  color: #606266;
}
.identity-tip {
  font-size: 12px;
  color: #909399;
}
.reply-tip {
  font-size: 13px;
  color: #b8821f;
  margin-bottom: 8px;
}
.comment-submit-line {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
.comment-divider {
  margin: 16px 0;
}
.comment-empty {
  text-align: center;
  color: #909399;
  font-size: 13px;
  padding: 24px 0;
}
.comment-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
}
/* 二级回复：整段缩进 + 左侧引导线，视觉上从属于主评论 */
.comment-thread + .comment-thread {
  border-top: 1px dashed #f0eef5;
}
.reply-list {
  margin-left: 26px;
  padding-left: 14px;
  border-left: 2px solid #f0ecf7;
}
.reply-item {
  padding: 8px 0;
}
.reply-item .comment-content,
.reply-item .comment-name {
  font-size: 13px;
}
.reply-toggle {
  padding: 2px 0 6px 36px;
}
/* 评论头像：昵称评论显示作者头像，匿名评论显示系统默认头像 */
.comment-avatar {
  margin-top: 2px;
}
.comment-body {
  flex: 1;
  min-width: 0;
}
.comment-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.comment-name {
  font-size: 13px;
  font-weight: 600;
  color: #4a6fa5;
}
.anon-tag {
  transform: scale(0.9);
}
/* 作者标记：橙色描边，与"匿名"灰标签区分 */
.author-tag {
  transform: scale(0.9);
}
.author-name {
  color: #f5a623;
  font-weight: 700;
}
.reply-target {
  font-size: 12px;
  color: #b8821f;
}
.comment-time {
  font-size: 12px;
  color: #c0c4cc;
}
.comment-content {
  margin-top: 4px;
  font-size: 14px;
  line-height: 1.7;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}
.comment-ops {
  margin-top: 2px;
}

/* 收藏对话框 */
.folder-empty {
  font-size: 13px;
  color: #909399;
  background: #faf7f0;
  padding: 10px 12px;
  border-radius: 8px;
  margin-bottom: 14px;
  line-height: 1.6;
}
.folder-radio-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
}
.folder-radio {
  display: flex;
  align-items: center;
  height: auto;
  padding: 8px 12px;
  margin-right: 0;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}
.folder-name {
  font-size: 14px;
  color: #303133;
}
.folder-count {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
.new-folder-line {
  display: flex;
  gap: 8px;
}
</style>
