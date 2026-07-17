import request from '@/utils/request'

// 花种列表（仅返回 available=1）
export function getFlowerTypes() {
  return request({ url: '/flower-types', method: 'get' })
}

// 兑换花种
export function redeemFlower(data) {
  return request({ url: '/flowers/redeem', method: 'post', data })
}

// 我的花朵列表
export function getMyFlowers() {
  return request({ url: '/flowers', method: 'get' })
}

// 浇水（每日1次）
export function waterFlower(flowerId) {
  return request({ url: `/flowers/${flowerId}/water`, method: 'post' })
}

// 花朵详情（含当前阶段、进度）
export function getFlower(flowerId) {
  return request({ url: `/flowers/${flowerId}`, method: 'get' })
}
