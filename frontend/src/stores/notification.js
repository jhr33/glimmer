import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getUnreadCount,
  markAllRead,
  markRead
} from '@/api/notification'

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)

  async function fetchUnreadCount() {
    try {
      const res = await getUnreadCount()
      unreadCount.value = res.data?.count ?? 0
    } catch (e) {
      // 静默失败，避免影响主流程
      console.warn('[glimmer] 获取未读通知数失败：', e)
    }
    return unreadCount.value
  }

  function increment(n = 1) {
    unreadCount.value += n
  }

  function clear() {
    unreadCount.value = 0
  }

  async function markAllAsRead() {
    await markAllRead()
    clear()
  }

  async function markOneAsRead(id) {
    await markRead(id)
    if (unreadCount.value > 0) {
      unreadCount.value -= 1
    }
  }

  return {
    unreadCount,
    fetchUnreadCount,
    increment,
    clear,
    markAllAsRead,
    markOneAsRead
  }
})
