<script setup>
import { useRoute } from 'vue-router'
import { computed } from 'vue'

const route = useRoute()

const titleMap = {
  driftBottle: '漂流瓶',
  letter: '信件',
  campfire: '篝火',
  ai: 'AI 对话',
  garden: '花园',
  notification: '通知中心',
  admin: '管理后台'
}

const title = computed(() => titleMap[route.name] || '页面')
</script>

<template>
  <div class="placeholder-view">
    <el-empty :description="`${title} - 敬请期待`">
      <template #image>
        <el-icon :size="64" color="#f5a623">
          <svg viewBox="0 0 1024 1024" width="1em" height="1em">
            <path
              fill="currentColor"
              d="M512 64a448 448 0 1 0 0 896 448 448 0 0 0 0-896zm0 832a384 384 0 1 1 0-768 384 384 0 0 1 0 768zm-32-544a32 32 0 0 1 64 0v224a32 32 0 0 1-64 0V352zm0 320a32 32 0 1 1 64 0 32 32 0 0 1-64 0z"
            />
          </svg>
        </el-icon>
      </template>
    </el-empty>
  </div>
</template>

<style scoped>
.placeholder-view {
  padding: 60px 20px;
  text-align: center;
}
</style>
