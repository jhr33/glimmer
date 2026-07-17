import request from '@/utils/request'

// 扔漂流瓶
export function throwBottle(data) {
  return request({ url: '/bottles', method: 'post', data })
}

// 捡漂流瓶（随机返回1个瓶子ID）
export function pickBottle() {
  return request({ url: '/bottles/pick', method: 'post' })
}

// 查看漂流瓶内容（仅在已捡到后可查看）
export function getBottle(bottleId) {
  return request({ url: `/bottles/${bottleId}`, method: 'get' })
}

// 放回漂流瓶（不查看内容直接放回）
export function releaseBottle(bottleId) {
  return request({ url: `/bottles/${bottleId}/release`, method: 'post' })
}

// 回复漂流瓶（每人1次）
export function replyBottle(bottleId, data) {
  return request({ url: `/bottles/${bottleId}/replies`, method: 'post', data })
}

// 查看我的瓶子回复（仅瓶主可查看）
export function getBottleReplies(bottleId, params) {
  return request({ url: `/bottles/${bottleId}/replies`, method: 'get', params })
}

// 感谢漂流瓶（每人1次）
export function thankBottle(bottleId) {
  return request({ url: `/bottles/${bottleId}/thank`, method: 'post' })
}

// 感谢瓶子回复（每人1次）
export function thankBottleReply(replyId) {
  return request({ url: `/bottle-replies/${replyId}/thank`, method: 'post' })
}

// 沉底自己的瓶子（仅瓶主）
export function sinkBottle(bottleId) {
  return request({ url: `/bottles/${bottleId}/sink`, method: 'post' })
}

// 我扔出的瓶子列表（分页）
export function getMyBottles(params) {
  return request({ url: '/bottles/mine', method: 'get', params })
}

// 漂流瓶列表（游客可看，仅浏览摘要）
export function getBottles(params) {
  return request({ url: '/bottles', method: 'get', params })
}
