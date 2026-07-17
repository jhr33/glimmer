import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, register as registerApi } from '@/api/auth'
import { getUserInfo, updateNickname as updateNicknameApi } from '@/api/user'

const TOKEN_KEY = 'glimmer_token'
const USER_KEY = 'glimmer_user'

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

  function logout() {
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
