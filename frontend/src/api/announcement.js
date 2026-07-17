import request from '@/utils/request'

// 公告列表（游客可访问，分页，仅 published）
export function getAnnouncements(params) {
  return request({ url: '/announcements', method: 'get', params })
}

// 公告详情
export function getAnnouncement(id) {
  return request({ url: `/announcements/${id}`, method: 'get' })
}

// === 管理员接口（4.15 节） ===

// 发布公告
export function adminCreateAnnouncement(data) {
  return request({ url: '/admin/announcements', method: 'post', data })
}

// 公告列表（全量，含下架）
export function adminGetAnnouncements(params) {
  return request({ url: '/admin/announcements', method: 'get', params })
}

// 下架公告
export function adminTakeDownAnnouncement(id) {
  return request({ url: `/admin/announcements/${id}/take-down`, method: 'post' })
}
