import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, register as registerApi, logout as logoutApi } from '@/api/auth'
import { getUserInfo, updateNickname as updateNicknameApi } from '@/api/user'

const TOKEN_KEY = 'glimmer_token'
const USER_KEY = 'glimmer_user'

/**
 * 登录态持久化：使用 sessionStorage（会话级，按浏览器标签页隔离）
 *
 * 设计目的：同一浏览器的不同标签页可以登录不同账号，会话互不覆盖；
 * 关闭标签页后该标签页的登录态自动清除。
 * 单点登录（同账号仅一处在线）由后端 jti + Redis 会话保证：
 * 账号在新标签页/设备登录后，旧标签页的下一次请求会收到 4024 并被迫下线。
 *
 * 一次性迁移：旧版本 token 存在 localStorage 中，首次加载时搬到当前标签页
 * 的 sessionStorage，并清除旧的跨标签共享数据，避免老用户被强制重登。
 */
;(function migrateFromLocalStorage() {
  if (!sessionStorage.getItem(TOKEN_KEY)) {
    const oldToken = localStorage.getItem(TOKEN_KEY)
    const oldUser = localStorage.getItem(USER_KEY)
    if (oldToken) sessionStorage.setItem(TOKEN_KEY, oldToken)
    if (oldUser) sessionStorage.setItem(USER_KEY, oldUser)
  }
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
})()

export const useUserStore = defineStore('user', () => {
  const token = ref(sessionStorage.getItem(TOKEN_KEY) || '')
  const userInfo = ref(JSON.parse(sessionStorage.getItem(USER_KEY) || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'admin')

  function setToken(t) {
    token.value = t
    if (t) {
      sessionStorage.setItem(TOKEN_KEY, t)
    } else {
      sessionStorage.removeItem(TOKEN_KEY)
    }
  }

  function setUserInfo(info) {
    userInfo.value = info
    if (info) {
      sessionStorage.setItem(USER_KEY, JSON.stringify(info))
    } else {
      sessionStorage.removeItem(USER_KEY)
    }
  }

  async function login(payload) {
    const res = await loginApi(payload)
    const data = res.data
    setToken(data.token)
    setUserInfo(data.user)
    return data
  }

  async function register(payload) {
    const res = await registerApi(payload)
    return res.data
  }

  async function fetchUserInfo() {
    const res = await getUserInfo()
    setUserInfo(res.data)
    return res.data
  }

  async function updateNickname(nickname) {
    const res = await updateNicknameApi({ nickname })
    if (userInfo.value) {
      setUserInfo({ ...userInfo.value, nickname })
    }
    return res.data
  }

  /**
   * 退出登录：先通知后端作废当前会话（best-effort，网络失败不阻塞），
   * 再清除本标签页的登录态。
   */
  async function logout() {
    try {
      await logoutApi()
    } catch (e) {
      // 忽略后端通知失败：本地登录态仍需清除
    }
    setToken('')
    setUserInfo(null)
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    isAdmin,
    login,
    register,
    logout,
    fetchUserInfo,
    updateNickname
  }
})
