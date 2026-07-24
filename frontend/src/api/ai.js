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

// 发送消息（流式返回AI回复，边收边发）
export function sendMessageStream(conversationId, data) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `/api/ai/conversations/${conversationId}/messages/stream`, true)
    xhr.setRequestHeader('Content-Type', 'application/json')
    xhr.setRequestHeader('Accept', 'text/event-stream')
    
    // 获取 token
    const token = localStorage.getItem('token')
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    }
    
    xhr.onprogress = function() {
      const text = xhr.responseText
      const lines = text.split('\n').filter(line => line.trim())
      const lastLine = lines[lines.length - 1]
      if (lastLine && lastLine.startsWith('data:')) {
        try {
          const jsonStr = lastLine.substring(5).trim()
          const data = JSON.parse(jsonStr)
          resolve({ type: 'delta', data })
        } catch (e) {
          // 忽略解析错误，可能是不完整的数据
        }
      }
    }
    
    xhr.onload = function() {
      const text = xhr.responseText
      const lines = text.split('\n').filter(line => line.trim())
      let finalData = null
      for (let line of lines) {
        if (line.startsWith('data:')) {
          try {
            const jsonStr = line.substring(5).trim()
            const data = JSON.parse(jsonStr)
            if (data.type === 'final') {
              finalData = data
            }
          } catch (e) {
            // 忽略解析错误
          }
        }
      }
      if (finalData) {
        resolve({ type: 'final', data: finalData })
      } else if (xhr.status === 200) {
        resolve({ type: 'complete' })
      } else {
        reject(new Error('请求失败'))
      }
    }
    
    xhr.onerror = function() {
      reject(new Error('网络错误'))
    }
    
    xhr.send(JSON.stringify(data))
  })
}

// 发送消息（SSE流式，支持实时回调）
export function sendMessageSSE(conversationId, data, onMessage, onError) {
  const token = localStorage.getItem('token')
  const eventSource = new EventSource(
    `/api/ai/conversations/${conversationId}/messages/stream`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : ''
      },
      body: JSON.stringify(data),
      withCredentials: true
    }
  )
  
  eventSource.onmessage = function(event) {
    try {
      const data = JSON.parse(event.data)
      if (onMessage) {
        onMessage(data)
      }
    } catch (e) {
      console.error('解析 SSE 消息失败:', e)
    }
  }
  
  eventSource.onerror = function(err) {
    if (onError) {
      onError(err)
    }
    eventSource.close()
  }
  
  return eventSource
}

// 使用 fetch API 实现流式发送（推荐）
export async function sendMessageFetchStream(conversationId, content, onDelta) {
  // 与项目保持一致，从 sessionStorage 获取 token
  const token = sessionStorage.getItem('glimmer_token')
  const response = await fetch(`/api/ai/conversations/${conversationId}/messages/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': token ? `Bearer ${token}` : '',
      'Accept': 'text/event-stream'
    },
    body: JSON.stringify({ content })
  })
  
  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(errorText || '请求失败')
  }
  
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    
    buffer += decoder.decode(value, { stream: true })
    
    // SSE 格式：每个事件以 \n\n 分隔，每行格式为 field: value
    // 我们需要按完整的事件边界来解析
    const events = buffer.split('\n\n')
    // 最后一个可能不完整，保留在 buffer 中
    buffer = events.pop() || ''
    
    for (const event of events) {
      // 解析单个 SSE 事件
      let dataStr = ''
      const lines = event.split('\n')
      for (const line of lines) {
        if (line.startsWith('data:')) {
          // 去掉 'data:' 前缀，保留后面的内容（包括换行）
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
}

// 关闭会话（主动关闭）
export function closeConversation(conversationId) {
  return request({ url: `/ai/conversations/${conversationId}/close`, method: 'post' })
}
