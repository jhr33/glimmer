import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getUnreadCount,
  markAllRead,
  markRead
} from '@/api/notification'

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  const unreadByType = ref({})

  async function fetchUnreadCount() {
    try {
      const res = await getUnreadCount()
      const data = res.data || {}
      unreadByType.value = data
      unreadCount.value = data.total ?? 0
    } catch (e) {
      // 静默失败，避免影响主流程
      console.warn('[glimmer] 获取未读通知数失败：', e)
      unreadByType.value = {}
      unreadCount.value = 0
    }
    return unreadCount.value
  }

  function getTypeUnread(type) {
    if (!type) return unreadCount.value
    return unreadByType.value[type] ?? 0
  }

  function increment(n = 1) {
    unreadCount.value += n
  }

  function clear() {
    unreadCount.value = 0
    unreadByType.value = {}
  }

  async function markAllAsRead() {
    await markAllRead()
    clear()
  }

  async function markOneAsRead(id) {
    await markRead(id)
    await fetchUnreadCount()
  }

  return {
    unreadCount,
    unreadByType,
    fetchUnreadCount,
    getTypeUnread,
    increment,
    clear,
    markAllAsRead,
    markOneAsRead
  }
})
