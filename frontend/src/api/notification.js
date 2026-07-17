import request from '@/utils/request'

// 通知列表（分页）
export function getNotifications(params) {
  return request({ url: '/notifications', method: 'get', params })
}

// 未读数量
export function getUnreadCount() {
  return request({ url: '/notifications/unread-count', method: 'get' })
}

// 标记单条已读
export function markRead(id) {
  return request({ url: `/notifications/${id}/read`, method: 'put' })
}

// 全部标记已读
export function markAllRead() {
  return request({ url: '/notifications/read-all', method: 'put' })
}
