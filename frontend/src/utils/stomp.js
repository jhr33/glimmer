// STOMP 客户端封装（篝火实时聊天）
// 依赖 @stomp/stompjs，使用原生 WebSocket
// 网页版走相对路径 /ws-campfire；原生 App 使用 VITE_WS_BASE_URL 完整地址
import { Client } from '@stomp/stompjs'
import { getWsUrl } from '@/utils/baseUrl'

export function buildBrokerUrl(token) {
  const base = getWsUrl()
  return token ? `${base}?token=${encodeURIComponent(token)}` : base
}

export function createStompClient(options = {}) {
  const {
    token,
    onConnect,
    onDisconnect,
    onError,
    onWebSocketError,
    reconnectDelay = 5000
  } = options

  const url = buildBrokerUrl(token)

  const client = new Client({
    webSocketFactory: () => {
      console.log('创建WebSocket连接: url=', url)
      const ws = new WebSocket(url)
      ws.onerror = (evt) => {
        console.error('WebSocket原生错误:', evt, 'evt.message:', evt.message)
      }
      ws.onclose = (evt) => {
        console.error('WebSocket连接关闭:', evt.code, evt.reason)
      }
      ws.onopen = () => {
        console.log('WebSocket连接已打开')
      }
      return ws
    },
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    reconnectDelay,
    onConnect: () => {
      console.log('STOMP连接成功')
      if (typeof onConnect === 'function') onConnect(client)
    },
    onDisconnect: () => {
      console.log('STOMP连接断开')
      if (typeof onDisconnect === 'function') onDisconnect()
    },
    onStompError: (frame) => {
      console.error('STOMP错误:', frame)
      if (typeof onError === 'function') onError(frame)
    },
    onWebSocketError: (evt) => {
      console.error('WebSocket错误:', evt)
      if (typeof onWebSocketError === 'function') onWebSocketError(evt)
    }
  })

  return client
}

export default {
  buildBrokerUrl,
  createStompClient
}
