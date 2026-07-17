import request from '@/utils/request'

// 获取当前登录用户信息
export function getUserInfo() {
  return request({ url: '/user/me', method: 'get' })
}

// 修改昵称
export function updateNickname(data) {
  return request({ url: '/user/nickname', method: 'put', data })
}

// 获取萤火花园数据
export function getGarden(userId) {
  return request({ url: `/user/${userId}/garden`, method: 'get' })
}

// 查看他人主页
export function getUserProfile(userId) {
  return request({ url: `/user/${userId}/profile`, method: 'get' })
}

// === 管理员接口（4.15 节） ===

// 用户列表（分页，可按状态/角色筛选）
export function getUserList(params) {
  return request({ url: '/admin/users', method: 'get', params })
}

// 封禁/解封用户
export function updateUserStatus(userId, data) {
  return request({ url: `/admin/users/${userId}/status`, method: 'post', data })
}
