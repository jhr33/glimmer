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

// === 聚合举报接口 ===

// 聚合举报列表（按目标资源分组）
export function adminGetReportGroups(params) {
  return request({ url: '/admin/reports/groups', method: 'get', params })
}

// 聚合举报详情（包含所有举报记录）
export function adminGetReportGroupDetail(targetType, targetId) {
  return request({ url: '/admin/reports/groups/detail', method: 'get', params: { targetType, targetId } })
}

// 审核聚合举报（处理同一目标的所有举报）
export function adminReviewReportGroup(data) {
  return request({ url: '/admin/reports/groups/review', method: 'post', data })
}

// 撤销处罚单（管理员操作）
export function adminRevokePunishment(id) {
  return request({ url: `/admin/reports/punishments/${id}/revoke`, method: 'post' })
}
