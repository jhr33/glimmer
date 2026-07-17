import request from '@/utils/request'

// 签到（每日1次）
export function signIn() {
  return request({ url: '/token/sign-in', method: 'post' })
}

// 查询今日签到状态
export function getSignInStatus() {
  return request({ url: '/token/sign-in/today', method: 'get' })
}

// 代币流水查询（分页，可按类型/来源筛选）
export function getTransactions(params) {
  return request({ url: '/token/transactions', method: 'get', params })
}
