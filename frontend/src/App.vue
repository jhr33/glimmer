<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowDown, Bell } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'
import { getAutoMode, setAutoMode as setAutoModeApi } from '@/api/echo'
import { useDevice } from '@/composables/useDevice'
import MobileTabBar from '@/components/MobileTabBar.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

// 设备检测：isMobile 为 true 时渲染移动端布局（底部 TabBar）
const { isMobile } = useDevice()

// 当前页面标题（取路由 meta.title，移动端顶部栏展示）
const pageTitle = computed(() => route.meta?.title || 'glimmer 萤光')

let pollInterval = null

const isLoggedIn = computed(() => userStore.isLoggedIn)
const isAdmin = computed(() => userStore.isAdmin)
const unreadCount = computed(() => notificationStore.unreadCount)

// 回音托管开关相关：仅回音账号自身可见可操作
const echoAutoMode = ref(false)
const echoLoading = ref(false)
const canControlEcho = computed(() => {
  const role = userStore.userInfo?.role
  return userStore.userInfo?.username === 'bot_echo' || role === 'bot'
})

async function fetchEchoMode() {
  if (!canControlEcho.value) return
  try {
    const res = await getAutoMode()
    echoAutoMode.value = res.data?.enabled || false
  } catch (e) {
    echoAutoMode.value = false
  }
}

async function toggleEchoMode(val) {
  echoLoading.value = true
  try {
    await setAutoModeApi(val)
    ElMessage.success(val ? '回音托管已开启' : '回音托管已关闭')
  } catch (e) {
    echoAutoMode.value = !val
    ElMessage.error('操作失败')
  } finally {
    echoLoading.value = false
  }
}

// 顶部导航菜单（游客版：不含通知、反馈、管理入口）
const guestMenuItems = computed(() => [
  { index: '/home', label: '首页' },
  { index: '/driftBottle', label: '漂流瓶' },
  { index: '/letter', label: '信件' },
  { index: '/campfire', label: '篝火' },
  { index: '/ai', label: '树洞' },
  { index: '/garden', label: '花园' },
  { index: '/articles', label: '交流会' },
  { index: '/game', label: '小游戏' },
  { index: '/announcements', label: '公告' }
])
// 已登录：显示完整导航菜单
const menuItems = computed(() => {
  const items = [
    { index: '/home', label: '首页' },
    { index: '/driftBottle', label: '漂流瓶' },
    { index: '/letter', label: '信件' },
    { index: '/campfire', label: '篝火' },
    { index: '/ai', label: '树洞' },
    { index: '/garden', label: '花园' },
    { index: '/articles', label: '交流会' },
    { index: '/game', label: '小游戏' },
    { index: '/announcements', label: '公告' },
    { index: '/notifications', label: '通知', isNotification: true },
    { index: '/feedback', label: '意见反馈与申诉' }
  ]
  if (isAdmin.value) {
    items.push({ index: '/admin', label: '管理后台' })
  }
  return items
})

const activeMenu = computed(() => route.path)
const isCampfire = computed(() => route.path === '/campfire')
const isDriftBottle = computed(() => route.path === '/driftBottle')

// 需要沉浸式全屏的页面（篝火、登录、注册）：隐藏顶部栏 + 底部 TabBar
const hideChrome = computed(() => {
  const path = route.path
  return isCampfire.value || path === '/login' || path === '/register'
})

function handleSelect(index) {
  router.push(index)
}

function goLogin() {
  router.push('/login')
}

function goRegister() {
  router.push('/register')
}

async function handleLogout() {
  await userStore.logout()
  notificationStore.clear()
  router.push('/login')
}

function startPolling() {
  if (pollInterval) return
  pollInterval = setInterval(() => {
    if (isLoggedIn.value) {
      notificationStore.fetchUnreadCount()
    }
  }, 10000)
}

function stopPolling() {
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

onMounted(async () => {
  if (isLoggedIn.value) {
    // 拉取未读通知数
    notificationStore.fetchUnreadCount()
    // 每次刷新都重新拉取用户信息，确保代币、萤火数等数据与数据库同步
    try {
      await userStore.fetchUserInfo()
    } catch (e) {
      // 拉取失败时静默处理（可能token过期）
    }
    // 拉取回音托管状态
    fetchEchoMode()
  }
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <el-container class="app-container">
    <!-- ========== 桌面端顶部导航 ========== -->
    <el-header v-if="!isMobile && !isCampfire" class="app-header">
      <div class="header-inner">
        <!-- Logo -->
        <div class="logo" @click="router.push('/')">
          <span class="logo-text">glimmer</span>
          <span class="logo-sub">萤光</span>
        </div>

        <!-- 已登录：显示完整导航菜单 + 通知 + 用户 -->
        <template v-if="isLoggedIn">
          <el-menu
            :default-active="activeMenu"
            mode="horizontal"
            class="nav-menu"
            :ellipsis="false"
            @select="handleSelect"
          >
            <el-menu-item
              v-for="item in menuItems"
              :key="item.index"
              :index="item.index"
            >
              <el-badge v-if="item.isNotification" :value="unreadCount" :hidden="unreadCount === 0" :max="99">
                <el-icon><Bell /></el-icon>
                <span>{{ item.label }}</span>
              </el-badge>
              <template v-else>
                {{ item.label }}
              </template>
            </el-menu-item>
          </el-menu>

          <div class="header-actions">
            <!-- 回音托管开关：仅管理员/机器人可见 -->
            <div v-if="canControlEcho" class="echo-toggle">
              <span class="echo-toggle-label">回音托管</span>
              <el-switch
                v-model="echoAutoMode"
                :loading="echoLoading"
                active-text="开"
                inactive-text="关"
                @change="toggleEchoMode"
              />
            </div>
            <el-dropdown @command="(cmd) => cmd === 'logout' && handleLogout()">
              <span class="user-dropdown-trigger">
                {{ userStore.userInfo?.nickname || '旅人' }}
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>

        <!-- 未登录（游客）：显示浏览用导航菜单 + 登录/注册按钮 -->
        <template v-else>
          <el-menu
            :default-active="activeMenu"
            mode="horizontal"
            class="nav-menu"
            :ellipsis="false"
            @select="handleSelect"
          >
            <el-menu-item
              v-for="item in guestMenuItems"
              :key="item.index"
              :index="item.index"
            >
              {{ item.label }}
            </el-menu-item>
          </el-menu>

          <div class="header-actions">
            <el-button @click="goLogin">登录</el-button>
            <el-button type="primary" @click="goRegister">注册</el-button>
          </div>
        </template>
      </div>
    </el-header>

    <!-- ========== 移动端顶部导航 ========== -->
    <el-header v-if="isMobile && !hideChrome" class="mobile-header">
      <div class="mobile-header-inner">
        <div class="mobile-logo" @click="router.push('/')">
          <span class="logo-text">glimmer</span>
        </div>
        <div class="mobile-title">{{ pageTitle }}</div>
        <div class="mobile-header-right">
          <template v-if="isLoggedIn">
            <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="mobile-notify-badge">
              <el-button text @click="router.push('/notifications')">
                <el-icon :size="20"><Bell /></el-icon>
              </el-button>
            </el-badge>
          </template>
          <template v-else>
            <el-button size="small" type="primary" @click="goLogin">登录</el-button>
          </template>
        </div>
      </div>
    </el-header>

    <el-main
      :class="[
        'app-main',
        isCampfire ? 'campfire-main' : '',
        isDriftBottle ? 'drift-bottle-main' : '',
        isMobile ? 'mobile-main' : ''
      ]"
    >
      <router-view />
    </el-main>

    <!-- ========== 移动端底部 TabBar ========== -->
    <MobileTabBar v-if="isMobile && !hideChrome" />
  </el-container>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
}
.app-header {
  background: #fff;
  border-bottom: 1px solid #f0e6d2;
  padding: 0;
  height: 60px;
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: 0 2px 8px rgba(245, 166, 35, 0.06);
}
.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 20px;
}
.logo {
  cursor: pointer;
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-right: 32px;
  flex-shrink: 0;
}
.logo-text {
  font-size: 22px;
  font-weight: bold;
  color: #f5a623;
  letter-spacing: 1px;
}
.logo-sub {
  font-size: 12px;
  color: #c0c4cc;
}
.nav-menu {
  flex: 1;
  border-bottom: none !important;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}
.echo-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: #fff8e6;
  border-radius: 16px;
  border: 1px solid #ffe6a8;
}
.echo-toggle-label {
  font-size: 12px;
  color: #9a6b1a;
  font-weight: 500;
}
.menu-badge {
  display: inline-flex !important;
  align-items: center;
  gap: 4px;
}
.menu-badge :deep(.el-badge__content) {
  margin-left: 4px;
}
/* 修复通知红点位置，避免被遮挡 */
.nav-menu :deep(.el-badge__content) {
  top: -4px !important;
  right: -8px !important;
  transform: none !important;
}
.user-dropdown-trigger {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  color: #606266;
  font-size: 14px;
  outline: none;
}
.app-main {
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  padding: 24px 20px;
  box-sizing: border-box;
}
.campfire-main {
  max-width: none;
  padding: 0;
}
.drift-bottle-main {
  max-width: none;
  padding: 0;
}

/* ========== 移动端布局样式 ========== */
.mobile-header {
  background: #fff;
  border-bottom: 1px solid #f0e6d2;
  padding: 0;
  height: 48px;
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: 0 2px 8px rgba(245, 166, 35, 0.06);
}
.mobile-header-inner {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  gap: 8px;
}
.mobile-logo {
  cursor: pointer;
  flex-shrink: 0;
}
.mobile-logo .logo-text {
  font-size: 18px;
  font-weight: bold;
  color: #f5a623;
  letter-spacing: 0.5px;
}
.mobile-title {
  flex: 1;
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.mobile-header-right {
  flex-shrink: 0;
  min-width: 40px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
}
.mobile-notify-badge :deep(.el-badge__content) {
  top: 2px;
  right: 2px;
}
.mobile-main {
  /* 移动端内容全宽，去除桌面端的最大宽度限制 */
  max-width: none;
  padding: 12px;
  /* 底部留出 TabBar 高度（56px + 安全区） */
  padding-bottom: calc(72px + env(safe-area-inset-bottom));
}
</style>
