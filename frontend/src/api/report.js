import request from '@/utils/request'

// 提交举报
export function createReport(data) {
  return request({ url: '/reports', method: 'post', data })
}

// 我提交的举报（分页）
export function getMyReports(params) {
  return request({ url: '/reports/mine', method: 'get', params })
}

// === 管理员接口（4.15 节） ===

// 举报列表（分页，可按状态筛选）
export function adminGetReports(params) {
  return request({ url: '/admin/reports', method: 'get', params })
}

// 举报详情
export function adminGetReport(id) {
  return request({ url: `/admin/reports/${id}`, method: 'get' })
}

// 审核举报
export function adminReviewReport(id, data) {
  return request({ url: `/admin/reports/${id}/review`, method: 'post', data })
}
