import request from '@/utils/request'

// 获取回音托管状态
export function getAutoMode() {
  return request({ url: '/echo/auto-mode', method: 'get' })
}

// 设置回音托管开关
export function setAutoMode(enabled) {
  return request({ url: '/echo/auto-mode', method: 'post', data: { enabled } })
}
