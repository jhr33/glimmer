import request from '@/utils/request'

// 篝火列表（系统默认 + 我创建的）
export function getCampfires(params) {
  return request({ url: '/campfires', method: 'get', params })
}

// 创建篝火
export function createCampfire(data) {
  return request({ url: '/campfires', method: 'post', data })
}

// 篝火详情（含成员数）
export function getCampfire(campfireId) {
  return request({ url: `/campfires/${campfireId}`, method: 'get' })
}

// 历史消息（分页查询）
export function getCampfireMessages(campfireId, params) {
  return request({ url: `/campfires/${campfireId}/messages`, method: 'get', params })
}

// 加入篝火（校验人数上限）
export function joinCampfire(campfireId) {
  return request({ url: `/campfires/${campfireId}/join`, method: 'post' })
}

// 退出篝火
export function leaveCampfire(campfireId) {
  return request({ url: `/campfires/${campfireId}/leave`, method: 'post' })
}
