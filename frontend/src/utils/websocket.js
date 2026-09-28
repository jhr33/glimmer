// WebSocket 客户端封装
// 篝火场景使用 STOMP over WebSocket，开发文档 4.8.2 节

import { getWsUrl } from '@/utils/baseUrl'

/**
 * 构建 WebSocket 完整地址
 * 网页版：基于当前页面协议/主机推导
 * 原生 App：使用 VITE_WS_BASE_URL 配置的完整 ws(s) 地址
 * @returns {string} ws(s)://host/ws-campfire
 */
export function buildWsUrl() {
  return getWsUrl()
}

/**
 * 创建原生 WebSocket 连接
 * @param {string} [path] 子路径
 * @returns {WebSocket | null}
 */
export function createWebSocket(path = '') {
  try {
    return new WebSocket(`${buildWsUrl()}${path}`)
  } catch (e) {
    console.error('[glimmer] WebSocket 创建失败：', e)
    return null
  }
}

export default {
  buildWsUrl,
  createWebSocket
}
