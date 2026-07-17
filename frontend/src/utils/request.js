import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000
})

// 请求拦截器：注入 JWT
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
      // 401：token 失效，清除登录态并跳转登录页
      if (res.code === 401) {
        handleUnauthorized()
        const err = new Error(res.message || '未登录或登录已失效')
        err.code = res.code
        return Promise.reject(err)
      }
      ElMessage.error(res.message || '请求失败')
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
      handleUnauthorized()
    } else if (resData && resData.message) {
      ElMessage.error(resData.message)
    } else {
      ElMessage.error(error.message || '网络异常，请稍后重试')
    }
    // 透传后端业务错误码，便于调用方按 code 做特殊处理
    if (resData && resData.code) {
      error.code = resData.code
    }
    return Promise.reject(error)
  }
)

function handleUnauthorized() {
  sessionStorage.removeItem('glimmer_token')
  sessionStorage.removeItem('glimmer_user')
  // 避免在登录页重复跳转
  const { pathname } = window.location
  if (pathname !== '/login' && pathname !== '/register') {
    ElMessage.warning('登录已失效，请重新登录')
    window.location.href = '/login'
  }
}

export default service
