/**
 * 统一的后端地址构建工具
 *
 * 设计目标：同一份前端代码同时支持「网页版」和「APK 原生版」
 *
 * 网页版（Nginx 部署）：
 *   VITE_API_BASE_URL=/api          （相对路径，由 Nginx 反向代理到后端）
 *   VITE_WS_BASE_URL=/ws-campfire   （相对路径，由 Nginx 代理升级为 WebSocket）
 *
 * APK 原生版（Capacitor WebView）：
 *   页面加载自 capacitor://localhost，相对路径无法解析，必须使用完整地址：
 *   VITE_API_BASE_URL=https://glimmer.wang/api
 *   VITE_WS_BASE_URL=wss://glimmer.wang/ws-campfire
 *   （打包时通过 .env 或构建命令注入）
 */

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api'
const WS_BASE = import.meta.env.VITE_WS_BASE_URL || '/ws-campfire'

/**
 * 获取 REST API 基础地址
 */
export function getApiBaseUrl() {
  return API_BASE
}

/**
 * 拼接完整的 REST API 地址
 * @param {string} path 接口路径，如 /ai/conversations
 */
export function buildApiUrl(path) {
  const base = API_BASE.endsWith('/') ? API_BASE.slice(0, -1) : API_BASE
  const p = path.startsWith('/') ? path : `/${path}`
  return `${base}${p}`
}

/**
 * 获取 WebSocket 完整地址
 * - 若 VITE_WS_BASE_URL 已是 ws(s):// 开头，直接使用（原生 App 场景）
 * - 否则基于当前页面协议/主机拼接（网页版场景）
 */
export function getWsUrl() {
  // 原生 App：直接使用配置的完整 ws(s) 地址
  if (/^wss?:\/\//i.test(WS_BASE)) {
    return WS_BASE
  }
  // 网页版：基于当前页面地址推导
  const { protocol, host } = window.location
  const wsProtocol = protocol === 'https:' ? 'wss:' : 'ws:'
  return `${wsProtocol}//${host}${WS_BASE}`
}
