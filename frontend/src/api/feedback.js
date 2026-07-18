import request from '@/utils/request'

// 提交意见
export function createFeedback(data) {
  return request({ url: '/feedbacks', method: 'post', data })
}

// 提交申诉
export function createAppeal(data) {
  return request({ url: '/feedbacks/appeal', method: 'post', data })
}

// 我的意见信（分页）
export function getMyFeedbacks(params) {
  return request({ url: '/feedbacks/mine', method: 'get', params })
}

// 意见信详情（仅提交者可看）
export function getFeedback(feedbackId) {
  return request({ url: `/feedbacks/${feedbackId}`, method: 'get' })
}

// === 管理员接口（4.15 节） ===

// 意见信列表（分页，可按状态筛选）
export function adminGetFeedbacks(params) {
  return request({ url: '/admin/feedbacks', method: 'get', params })
}

// 回复意见信
export function adminReplyFeedback(id, data) {
  return request({ url: `/admin/feedbacks/${id}/reply`, method: 'post', data })
}

// 申诉列表（分页，可按状态筛选）
export function adminGetAppeals(params) {
  return request({ url: '/admin/feedbacks/appeals', method: 'get', params })
}

// 申诉详情
export function adminGetAppeal(id) {
  return request({ url: `/admin/feedbacks/appeals/${id}`, method: 'get' })
}

// 审核申诉
export function adminReviewAppeal(id, data) {
  return request({ url: `/admin/feedbacks/appeals/${id}/review`, method: 'post', data })
}
