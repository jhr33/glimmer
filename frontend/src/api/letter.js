import request from '@/utils/request'

// 写信
export function writeLetter(data) {
  return request({ url: '/letters', method: 'post', data })
}

// 回复信件
export function replyLetter(letterId, data) {
  return request({ url: `/letters/${letterId}/reply`, method: 'post', data })
}

// 收件箱（分页）
export function getInbox(params) {
  return request({ url: '/letters/inbox', method: 'get', params })
}

// 发件箱（分页）
export function getSent(params) {
  return request({ url: '/letters/sent', method: 'get', params })
}

// 信件详情（校验收发双方）
export function getLetter(letterId) {
  return request({ url: `/letters/${letterId}`, method: 'get' })
}

// 感谢信件（每人1次）
export function thankLetter(letterId) {
  return request({ url: `/letters/${letterId}/thank`, method: 'post' })
}

// 标记信件为已读
export function markLetterRead(letterId) {
  return request({ url: `/letters/${letterId}/read`, method: 'post' })
}
