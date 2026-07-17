<script setup>
import { computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowDown, Bell } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

const isLoggedIn = computed(() => userStore.isLoggedIn)
const isAdmin = computed(() => userStore.isAdmin)
const unreadCount = computed(() => notificationStore.unreadCount)

// 顶部导航菜单
const menuItems = computed(() => {
  const items = [
    { index: '/', label: '首页' },
    { index: '/driftBottle', label: '漂流瓶' },
    { index: '/letter', label: '信件' },
    { index: '/campfire', label: '篝火' },
    { index: '/ai', label: 'AI 对话' },
    { index: '/garden', label: '花园' },
    { index: '/announcements', label: '公告' },
    { index: '/feedback', label: '意见反馈' }
  ]
  if (isAdmin.value) {
    items.push({ index: '/admin', label: '管理后台' })
  }
  return items
})

const activeMenu = computed(() => route.path)
const isCampfire = computed(() => route.path === '/campfire')

function handleSelect(index) {
  router.push(index)
}

function goNotifications() {
  router.push('/notifications')
}

function goLogin() {
  router.push('/login')
}

function goRegister() {
  router.push('/register')
}

function handleLogout() {
  userStore.logout()
  notificationStore.clear()
  router.push('/login')
}

onMounted(async () => {
  if (isLoggedIn.value) {
    // 拉取未读通知数
    notificationStore.fetchUnreadCount()
    // 若用户信息缺失，尝试拉取
    if (!userStore.userInfo) {
      try {
        await userStore.fetchUserInfo()
      } catch (e) {
        // 拉取失败时静默处理
      }
    }
  }
})
</script>

<template>
  <el-container class="app-container">
    <el-header v-if="!isCampfire" class="app-header">
      <div class="header-inner">
        <!-- Logo -->
        <div class="logo" @click="router.push('/')">
          <span class="logo-text">glimmer</span>
          <span class="logo-sub">萤光</span>
        </div>

        <!-- 已登录：显示导航菜单 + 通知 + 用户 -->
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
              {{ item.label }}
            </el-menu-item>
          </el-menu>

          <div class="header-actions">
            <el-badge
              :value="unreadCount"
              :hidden="unreadCount === 0"
              :max="99"
              class="notification-badge"
            >
              <el-button circle @click="goNotifications">
                <el-icon><Bell /></el-icon>
              </el-button>
            </el-badge>
            <el-dropdown @command="(cmd) => cmd === 'logout' && handleLogout()">
              <span class="user-dropdown-trigger">
                {{ userStore.userInfo?.nickname || userStore.userInfo?.username || '旅人' }}
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

        <!-- 未登录：显示登录/注册按钮 -->
        <template v-else>
          <div class="header-actions">
            <el-button text @click="router.push('/announcements')">公告</el-button>
            <el-button @click="goLogin">登录</el-button>
            <el-button type="primary" @click="goRegister">注册</el-button>
          </div>
        </template>
      </div>
    </el-header>

    <el-main :class="['app-main', isCampfire ? 'campfire-main' : '']">
      <router-view />
    </el-main>
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
.notification-badge {
  line-height: 1;
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
</style>
