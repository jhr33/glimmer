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

// 关闭会话（主动关闭）
export function closeConversation(conversationId) {
  return request({ url: `/ai/conversations/${conversationId}/close`, method: 'post' })
}
