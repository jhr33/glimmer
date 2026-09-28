import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000
})

// 会话被顶替时的弹框去重标志（并发请求可能同时收到 4024，只弹一次）
let sessionKickedDialogVisible = false
// 禁言申诉弹框去重标志（并发请求可能同时收到 4019，只弹一次）
let mutedDialogVisible = false

// 请求拦截器：注入 JWT（token 按标签页隔离存放在 sessionStorage）
service.interceptors.request.use(
  (config) => {
    const token = sessionStorage.getItem('glimmer_token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一处理错误
service.interceptors.response.use(
  (response) => {
    const res = response.data
    // 业务约定：code === 200 为成功
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code === 200) {
        return res
      }
      // 4024：账号已在其他地方登录（单点登录踢人），弹框提示并回到登录页
      if (res.code === 4024) {
        handleSessionKicked()
        const err = new Error(res.message || '您的账号已在其他地方登录')
        err.code = res.code
        return Promise.reject(err)
      }
      // 401：token 失效，清除登录态并跳转登录页
      if (res.code === 401) {
        handleUnauthorized()
        const err = new Error(res.message || '未登录或登录已失效')
        err.code = res.code
        return Promise.reject(err)
      }
      // 4019：账号被禁言（仍可登录，仅限制发言）→ 弹引导框提供「去申诉」，不走普通错误条
      if (res.code === 4019) {
        handleMuted(res.message)
        const err = new Error(res.message || '账号已被禁言')
        err.code = res.code
        return Promise.reject(err)
      }
      // 调用方可通过 config.skipErrorMessage 接管错误提示（如登录页的封禁弹框）
      if (!response.config?.skipErrorMessage) {
        ElMessage.error(res.message || '请求失败')
      }
      const err = new Error(res.message || 'Error')
      err.code = res.code
      return Promise.reject(err)
    }
    // 非标准业务响应直接返回
    return res
  },
  (error) => {
    const status = error.response?.status
    const resData = error.response?.data
    if (status === 401) {
      // 4015：账号被永久封禁（由 JwtAuthenticationFilter 在 HTTP 401 响应体中返回）
      // 给出明确的封禁提示，而不是笼统的"登录失效"，避免用户误以为是登录态问题反复重登
      const banned = resData?.code === 4015
      handleUnauthorized(banned ? (resData.message || '账号已被永久封禁') : null)
    } else if (resData?.code === 4019) {
      // 禁言：弹「去申诉」引导框，不走普通错误条
      handleMuted(resData.message)
    } else if (resData && resData.message && !error.config?.skipErrorMessage) {
      ElMessage.error(resData.message)
    } else if (!error.config?.skipErrorMessage) {
      ElMessage.error(error.message || '网络异常，请稍后重试')
    }
    // 透传后端业务错误码，便于调用方按 code 做特殊处理
    if (resData && resData.code) {
      error.code = resData.code
    }
    return Promise.reject(error)
  }
)

function handleUnauthorized(customMessage) {
  const token = sessionStorage.getItem('glimmer_token')
  // 游客模式（无 token）：不跳转登录页，静默拒绝
  if (!token) {
    return
  }
  // 已登录用户 token 失效：清除登录态并跳转登录页
  sessionStorage.removeItem('glimmer_token')
  sessionStorage.removeItem('glimmer_user')
  // 避免在登录页重复跳转
  const { pathname } = window.location
  if (pathname !== '/login' && pathname !== '/register') {
    ElMessage.warning(customMessage || '登录已失效，请重新登录')
    window.location.href = '/login'
  }
}

/**
 * 4019 禁言提示：用户仍可登录、仅限制发言。
 * 弹出引导框（只弹一次），提供「去申诉」直达「意见反馈与申诉」页；不暴露联系邮箱。
 */
function handleMuted(message) {
  if (mutedDialogVisible) return
  mutedDialogVisible = true
  ElMessageBox.confirm(
    message || '账号已被禁言，暂不能发言。如有异议可前往「意见反馈与申诉」提交申诉。',
    '禁言提示',
    {
      confirmButtonText: '去申诉',
      cancelButtonText: '我知道了',
      type: 'warning'
    }
  )
    .then(() => {
      window.location.href = '/feedback'
    })
    .catch(() => {})
    .finally(() => {
      mutedDialogVisible = false
    })
}

/**
 * 4024 单点登录踢人：同一账号在新标签页/设备登录后，旧标签页的请求被后端拒绝。
 * 弹出醒目提示（只弹一次），清除本标签页会话后跳回登录页。
 */
function handleSessionKicked() {
  if (sessionKickedDialogVisible) return
  sessionKickedDialogVisible = true
  sessionStorage.removeItem('glimmer_token')
  sessionStorage.removeItem('glimmer_user')
  ElMessageBox.alert(
    '您的账号已在其他地方登录，当前页面已自动退出。如非本人操作，请重新登录并及时修改密码。',
    '下线通知',
    {
      confirmButtonText: '重新登录',
      type: 'warning',
      callback: () => {
        sessionKickedDialogVisible = false
        // 整页跳转，彻底重置 Pinia 与 WebSocket 等运行时状态
        window.location.href = '/login'
      }
    }
  )
}

export default service
