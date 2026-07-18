// STOMP 客户端封装（篝火实时聊天）
// 依赖 @stomp/stompjs，使用原生 WebSocket
// 连接地址：/ws-campfire?token=xxx（相对路径，走 Vite 代理）
import { Client } from '@stomp/stompjs'

const WS_BASE_URL = '/ws-campfire'

export function buildBrokerUrl(token) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const base = `${protocol}//${window.location.host}/ws-campfire`
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
