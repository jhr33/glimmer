<template>
  <img class="user-avatar" :src="src" :style="avatarStyle" alt="头像" />
</template>

<script setup>
import { computed } from 'vue'
import { resolveAvatar } from '../utils/avatar'

const props = defineProps({
  /** 自定义头像URL（空 = 系统默认头像） */
  url: { type: String, default: '' },
  /** 用户ID（用于按取模分配默认头像） */
  userId: { type: [Number, String], default: 0 },
  /** 头像直径（px） */
  size: { type: Number, default: 36 }
})

const src = computed(() => resolveAvatar(props.url, props.userId))
const avatarStyle = computed(() => ({ width: `${props.size}px`, height: `${props.size}px` }))
</script>

<style scoped>
.user-avatar {
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  display: block;
}
</style>
