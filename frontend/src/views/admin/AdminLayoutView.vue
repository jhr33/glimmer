<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Warning,
  ChatDotRound,
  Bell,
  User
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const menus = [
  { index: '/admin/reports', label: '举报管理', icon: Warning },
  { index: '/admin/feedbacks', label: '意见信管理', icon: ChatDotRound },
  { index: '/admin/announcements', label: '公告管理', icon: Bell },
  { index: '/admin/users', label: '用户管理', icon: User }
]

const activeMenu = computed(() => route.path)

function handleSelect(index) {
  router.push(index)
}
</script>

<template>
  <div class="admin-layout">
    <el-container class="admin-container">
      <el-aside width="200px" class="admin-aside">
        <div class="aside-title">🛡️ 管理后台</div>
        <el-menu
          :default-active="activeMenu"
          class="aside-menu"
          @select="handleSelect"
        >
          <el-menu-item
            v-for="m in menus"
            :key="m.index"
            :index="m.index"
          >
            <el-icon><component :is="m.icon" /></el-icon>
            <span>{{ m.label }}</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<style scoped>
.admin-layout {
  min-height: 60vh;
}
.admin-container {
  min-height: 60vh;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #f0e6d2;
}
.admin-aside {
  background: #fffaf0;
  border-right: 1px solid #f0e6d2;
}
.aside-title {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  color: #e89a1a;
  border-bottom: 1px solid #f0e6d2;
  letter-spacing: 1px;
}
.aside-menu {
  border-right: none !important;
  background: transparent !important;
}
.admin-main {
  background: #fff;
  padding: 20px;
}
</style>
