<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'

const route = useRoute()
const router = useRouter()
const notificationStore = useNotificationStore()

const unreadCount = computed(() => notificationStore.unreadCount)

/**
 * 移动端底部导航配置
 * 仅展示 5 个核心入口，其余功能收敛到「我的」页面
 */
const tabs = [
  { index: '/home', label: '首页', icon: '🏠' },
  { index: '/driftBottle', label: '漂流瓶', icon: '🍾' },
  { index: '/campfire', label: '篝火', icon: '🔥' },
  { index: '/ai', label: '树洞', icon: '✨' },
  { index: '/my', label: '我的', icon: '👤' }
]

const activeIndex = computed(() => route.path)

function handleSelect(index) {
  if (index === activeIndex.value) return
  router.push(index)
}
</script>

<template>
  <nav class="mobile-tabbar">
    <div
      v-for="tab in tabs"
      :key="tab.index"
      class="tabbar-item"
      :class="{ active: activeIndex === tab.index }"
      @click="handleSelect(tab.index)"
    >
      <div class="tabbar-icon">
        <!-- 通知红点：「我的」入口聚合所有未读 -->
        <el-badge
          v-if="tab.index === '/my' && unreadCount > 0"
          :value="unreadCount"
          :max="99"
          class="tabbar-badge"
        >
          <span class="icon-text">{{ tab.icon }}</span>
        </el-badge>
        <template v-else>
          <span class="icon-text">{{ tab.icon }}</span>
        </template>
      </div>
      <span class="tabbar-label">{{ tab.label }}</span>
    </div>
  </nav>
</template>

<style scoped>
.mobile-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
  display: flex;
  height: 56px;
  background: #ffffff;
  border-top: 1px solid #f0e6d2;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.04);
  /* 适配 iPhone 底部安全区 */
  padding-bottom: env(safe-area-inset-bottom);
}

.tabbar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #909399;
  transition: color 0.2s ease;
  user-select: none;
}

.tabbar-item.active {
  color: #f5a623;
}

.tabbar-icon {
  position: relative;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.icon-text {
  font-size: 22px;
  line-height: 1;
}

.tabbar-label {
  margin-top: 2px;
  font-size: 11px;
  line-height: 1;
}

.tabbar-badge :deep(.el-badge__content) {
  top: -6px;
  right: -10px;
}
</style>
