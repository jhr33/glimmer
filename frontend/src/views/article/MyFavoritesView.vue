<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { View, Back, Star, ChatDotRound, Delete, FolderAdd } from '@element-plus/icons-vue'
import {
  getMyCollections,
  getFavoriteFolders,
  createFavoriteFolder,
  deleteFavoriteFolder,
  uncollectArticle
} from '@/api/article'
import { useUserStore } from '@/stores/user'
import UserAvatar from '@/components/UserAvatar.vue'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)
// 当前选中的文件夹：null = 全部
const currentFolderId = ref(null)
const folders = ref([])
const page = reactive({ current: 1, size: 10 })

// 新建文件夹输入
const newFolderVisible = ref(false)
const newFolderName = ref('')
const folderSaving = ref(false)

const channels = { chat: '杂谈', tech: '技术笔记' }

async function fetchFolders() {
  try {
    const res = await getFavoriteFolders()
    folders.value = res.data || []
  } catch (e) {
    folders.value = []
  }
}

async function fetchList() {
  loading.value = true
  try {
    const params = { page: page.current, size: page.size }
    if (currentFolderId.value) {
      params.folderId = currentFolderId.value
    }
    const res = await getMyCollections(params)
    list.value = res.data?.list || []
    total.value = Number(res.data?.total || 0)
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleFolderChange() {
  page.current = 1
  fetchList()
}

function handlePageChange(p) {
  page.current = p
  fetchList()
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
    newFolderName.value = ''
    newFolderVisible.value = false
    ElMessage.success('文件夹已创建')
  } catch (e) {
    // 同名冲突等错误已统一提示
  } finally {
    folderSaving.value = false
  }
}

async function handleDeleteFolder(folder) {
  try {
    await ElMessageBox.confirm(
      `确定删除文件夹「${folder.name}」吗？（仅空文件夹可删除，其中的收藏不会被删除）`,
      '删除文件夹',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteFavoriteFolder(folder.id)
    if (currentFolderId.value === folder.id) {
      currentFolderId.value = null
      page.current = 1
    }
    ElMessage.success('文件夹已删除')
    await Promise.all([fetchFolders(), fetchList()])
  } catch (e) {
    // 非空文件夹等错误已统一提示
  }
}

async function handleUncollect(item) {
  try {
    await uncollectArticle(item.id)
    ElMessage.success('已取消收藏')
    // 删除最后一页唯一一条时回退一页
    if (list.value.length === 1 && page.current > 1) {
      page.current -= 1
    }
    await Promise.all([fetchFolders(), fetchList()])
  } catch (e) {
    // 错误已统一提示
  }
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}

onMounted(() => {
  // 收藏属于个人数据，页面级强制登录
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后查看我的收藏')
    router.replace({ path: '/login', query: { redirect: '/articles/favorites' } })
    return
  }
  fetchFolders()
  fetchList()
})
</script>

<template>
  <div class="my-favorite-page">
    <div class="page-header">
      <div class="header-text">
        <h2 class="page-title">⭐ 我的收藏</h2>
        <p class="page-subtitle">收藏的好文章，按自定义文件夹整理</p>
      </div>
      <el-button :icon="Back" @click="router.push('/articles')">返回广场</el-button>
    </div>

    <!-- 文件夹筛选 + 管理 -->
    <el-card shadow="never" class="folder-card">
      <div class="folder-bar">
        <el-radio-group v-model="currentFolderId" @change="handleFolderChange">
          <el-radio-button :value="null">全部收藏</el-radio-button>
          <el-radio-button v-for="folder in folders" :key="folder.id" :value="folder.id">
            📁 {{ folder.name }}（{{ folder.collectCount }}）
          </el-radio-button>
        </el-radio-group>

        <el-button v-if="!newFolderVisible" text type="primary" :icon="FolderAdd" @click="newFolderVisible = true">
          新建文件夹
        </el-button>
      </div>

      <div v-if="newFolderVisible" class="new-folder-line">
        <el-input
          v-model="newFolderName"
          size="small"
          maxlength="30"
          style="width: 240px"
          placeholder="输入自定义文件夹名称"
          @keyup.enter="handleCreateFolder"
        />
        <el-button size="small" type="primary" :loading="folderSaving" @click="handleCreateFolder">创建</el-button>
        <el-button size="small" @click="newFolderVisible = false; newFolderName = ''">取消</el-button>
      </div>

      <div v-if="folders.length" class="folder-manage-line">
        <span class="manage-hint">文件夹管理：</span>
        <el-popconfirm
          v-for="folder in folders"
          :key="folder.id"
          :title="`删除文件夹「${folder.name}」？仅空文件夹可删除`"
          confirm-button-text="删除"
          cancel-button-text="取消"
          @confirm="handleDeleteFolder(folder)"
        >
          <template #reference>
            <el-tag size="small" closable :disable-transitions="false" class="manage-tag" type="info">
              📁 {{ folder.name }}
            </el-tag>
          </template>
        </el-popconfirm>
      </div>
    </el-card>

    <el-card v-loading="loading" shadow="never" class="list-card">
      <el-empty v-if="!loading && list.length === 0" description="还没有收藏，遇到喜欢的文章点个收藏吧" />

      <ul v-else class="article-list">
        <li v-for="item in list" :key="item.id" class="article-item">
          <div class="item-main" @click="router.push(`/articles/${item.id}`)">
            <div class="item-head">
              <el-tag size="small" :type="item.channel === 'tech' ? 'success' : 'warning'" effect="plain">
                {{ channels[item.channel] || '杂谈' }}
              </el-tag>
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
              <span class="meta-views"><el-icon><View /></el-icon> {{ item.viewCount || 0 }}</span>
              <span class="meta-dot">·</span>
              <span class="meta-likes">❤️ {{ item.likeCount || 0 }}</span>
              <span class="meta-dot">·</span>
              <span class="meta-comments"><el-icon><ChatDotRound /></el-icon> {{ item.commentCount || 0 }}</span>
              <span class="meta-dot">·</span>
              <span class="collect-time">收藏于 {{ formatTime(item.collectedAt) }}</span>
            </div>
          </div>

          <div class="item-ops" @click.stop>
            <el-button text type="warning" :icon="Star">已收藏</el-button>
            <el-button text type="danger" :icon="Delete" @click="handleUncollect(item)">取消收藏</el-button>
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
.my-favorite-page {
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
.folder-card {
  border-radius: 10px;
}
.folder-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.new-folder-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}
.folder-manage-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #ebeef5;
}
.manage-hint {
  font-size: 12px;
  color: #909399;
}
.manage-tag {
  cursor: pointer;
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
  flex-wrap: wrap;
}
.meta-dot {
  color: #dcdfe6;
}
.meta-views,
.meta-likes,
.meta-comments,
.collect-time {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.collect-time {
  color: #b8821f;
}
.item-ops {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: flex-start;
  gap: 4px;
  flex-shrink: 0;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
