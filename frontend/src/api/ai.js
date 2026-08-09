import request from '@/utils/request'

// 开启新会话（消耗1代币）
export function createConversation() {
  return request({ url: '/ai/conversations', method: 'post' })
}

// 查询我的会话列表（分页）
export function getConversations(params) {
  return request({ url: '/ai/conversations', method: 'get', params })
}

// 会话详情（含全部消息）
export function getConversation(conversationId) {
  return request({ url: `/ai/conversations/${conversationId}`, method: 'get' })
}

// 发送消息（同步返回AI回复）
export function sendMessage(conversationId, data) {
  return request({ url: `/ai/conversations/${conversationId}/messages`, method: 'post', data })
}

/**
 * 使用 fetch API 实现流式发送（推荐）
 *
 * @param conversationId 会话ID
 * @param content 消息内容
 * @param onDelta 回调函数，每收到一条 SSE 事件调用
 * @param signal 可选的 AbortSignal，用于取消请求（组件卸载时 abort 可释放浏览器连接）
 * @returns {Promise<void>}
 */
export async function sendMessageFetchStream(conversationId, content, onDelta, signal) {
  const token = sessionStorage.getItem('glimmer_token')
  const response = await fetch(`/api/ai/conversations/${conversationId}/messages/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': token ? `Bearer ${token}` : '',
      'Accept': 'text/event-stream'
    },
    body: JSON.stringify({ content }),
    signal // 传入 AbortSignal 支持外部取消
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(errorText || '请求失败')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // SSE 格式：每个事件以 \n\n 分隔，每行格式为 field: value
      const events = buffer.split('\n\n')
      // 最后一个可能不完整，保留在 buffer 中
      buffer = events.pop() || ''

      for (const event of events) {
        let dataStr = ''
        const lines = event.split('\n')
        for (const line of lines) {
          if (line.startsWith('data:')) {
            dataStr += line.substring(5)
          }
        }

        if (dataStr.trim()) {
          try {
            const data = JSON.parse(dataStr)
            if (onDelta) {
              onDelta(data)
            }
          } catch (e) {
            console.warn('解析 SSE 消息失败:', dataStr, e)
          }
        }
      }
    }

    // 处理剩余的 buffer
    if (buffer.trim()) {
      let dataStr = ''
      const lines = buffer.split('\n')
      for (const line of lines) {
        if (line.startsWith('data:')) {
          dataStr += line.substring(5)
        }
      }
      if (dataStr.trim()) {
        try {
          const data = JSON.parse(dataStr)
          if (onDelta) {
            onDelta(data)
          }
        } catch (e) {
          console.warn('解析 SSE 消息失败:', dataStr, e)
        }
      }
    }
  } finally {
    // 确保释放 reader，避免浏览器连接泄漏
    reader.releaseLock()
  }
}

// 关闭会话（主动关闭）
export function closeConversation(conversationId) {
  return request({ url: `/ai/conversations/${conversationId}/close`, method: 'post' })
}

// 解锁对话额度（消耗 1 代币，额度上限 +10）
export function unlockQuota(conversationId) {
  return request({ url: `/ai/conversations/${conversationId}/unlock`, method: 'post' })
}
