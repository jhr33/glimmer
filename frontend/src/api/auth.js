import request from '@/utils/request'

// 用户注册
export function register(data) {
  return request({ url: '/auth/register', method: 'post', data })
}

// 用户登录（skipErrorMessage：登录页自行处理错误，封禁时需要弹出含申诉邮箱的对话框而非普通提示条）
export function login(data) {
  return request({ url: '/auth/login', method: 'post', data, skipErrorMessage: true })
}

// 退出登录（后端作废当前会话，旧 token 立即失效）
export function logout() {
  return request({ url: '/auth/logout', method: 'post' })
}

// 发送短信验证码（scene: login/register/bind）
export function sendSmsCode(data) {
  return request({ url: '/auth/sms-code', method: 'post', data })
}

// 获取图片验证码（发送短信前的人机校验）
export function getCaptcha() {
  return request({ url: '/auth/captcha', method: 'get' })
}

// 手机号验证码登录（未注册自动建号）
export function loginBySms(data) {
  return request({ url: '/auth/login-sms', method: 'post', data, skipErrorMessage: true })
}

// 手机号密码登录
export function loginByPhone(data) {
  return request({ url: '/auth/login-phone', method: 'post', data, skipErrorMessage: true })
}

// 绑定/换绑手机号（需登录）
export function bindPhone(data) {
  return request({ url: '/auth/bind-phone', method: 'post', data })
}
