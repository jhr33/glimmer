// WebSocket 客户端封装（预留）
// 篝火场景使用 STOMP over WebSocket，开发文档 4.8.2 节
// 当前仅提供基础导出，待篝火模块开发时实现完整客户端

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL || '/ws-campfire'

/**
 * 构建 WebSocket 完整地址
 * @returns {string} ws(s)://host/ws-campfire
 */
export function buildWsUrl() {
  const { protocol, host } = window.location
  const wsProtocol = protocol === 'https:' ? 'wss:' : 'ws:'
  return `${wsProtocol}//${host}${WS_BASE_URL}`
}

/**
 * 创建原生 WebSocket 连接（占位实现）
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
