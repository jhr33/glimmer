<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginFormRef = ref()
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

// 首次登录昵称设置弹窗
const nicknameDialogVisible = ref(false)
const nicknameInput = ref('')
const nicknameSubmitting = ref(false)

const validateUsername = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入用户名'))
  } else if (value.length < 2 || value.length > 50) {
    callback(new Error('用户名长度为 2-50 个字符'))
  } else {
    callback()
  }
}

const validatePassword = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入密码'))
  } else if (value.length < 6 || value.length > 50) {
    callback(new Error('密码长度为 6-50 个字符'))
  } else {
    callback()
  }
}

const rules = {
  username: [{ validator: validateUsername, trigger: 'blur' }],
  password: [{ validator: validatePassword, trigger: 'blur' }]
}

async function handleLogin() {
  if (!loginFormRef.value) return
  try {
    await loginFormRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    const data = await userStore.login({
      username: loginForm.username,
      password: loginForm.password
    })
    if (data.nicknameSet === false || !data.user?.nickname) {
      nicknameDialogVisible.value = true
      return
    }
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/'
    router.replace(redirect)
  } catch (e) {
    // 错误已由 request.js 拦截器统一提示
  } finally {
    loading.value = false
  }
}

async function confirmNickname() {
  const name = nicknameInput.value.trim()
  if (!name) {
    ElMessage.warning('请输入昵称')
    return
  }
  if (name.length < 2 || name.length > 20) {
    ElMessage.warning('昵称长度为 2-20 个字符')
    return
  }
  nicknameSubmitting.value = true
  try {
    await userStore.updateNickname(name)
    nicknameDialogVisible.value = false
    ElMessage.success('昵称设置成功')
    const redirect = route.query.redirect || '/'
    router.replace(redirect)
  } catch (e) {
    // 错误已拦截
  } finally {
    nicknameSubmitting.value = false
  }
}

function skipNickname() {
  nicknameDialogVisible.value = false
  const redirect = route.query.redirect || '/'
  router.replace(redirect)
}

function goRegister() {
  router.push('/register')
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <h1 class="auth-title">glimmer</h1>
        <p class="auth-subtitle">萤光，温暖你的每一个夜晚</p>
      </div>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="rules"
        label-position="top"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="loginForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            show-password
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            style="width: 100%"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
        <div class="auth-footer">
          还没有账号？
          <el-link type="primary" underline="never" @click="goRegister">立即注册</el-link>
        </div>
      </el-form>
    </div>

    <!-- 首次登录昵称设置弹窗 -->
    <el-dialog
      v-model="nicknameDialogVisible"
      title="设置你的昵称"
      width="400px"
      :close-on-click-modal="false"
      center
      class="nickname-dialog"
    >
      <div class="nickname-tip">
        给自己取一个温暖的昵称吧，其他旅人会这样称呼你 ☘️
      </div>
      <el-input
        v-model="nicknameInput"
        placeholder="输入你的昵称（2-20字）"
        maxlength="20"
        show-word-limit
        size="large"
        @keyup.enter="confirmNickname"
      />
      <template #footer>
        <el-button @click="skipNickname">跳过</el-button>
        <el-button type="primary" :loading="nicknameSubmitting" @click="confirmNickname">
          确认
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fff5e6 0%, #ffe4b5 100%);
  padding: 20px;
}
.auth-card {
  width: 100%;
  max-width: 400px;
  padding: 40px 32px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(245, 166, 35, 0.15);
}
.auth-header {
  text-align: center;
  margin-bottom: 28px;
}
.auth-title {
  margin: 0;
  font-size: 32px;
  color: #f5a623;
  letter-spacing: 2px;
}
.auth-subtitle {
  margin: 8px 0 0;
  color: #999;
  font-size: 14px;
}
.auth-footer {
  text-align: center;
  font-size: 14px;
  color: #666;
}
.nickname-dialog .nickname-tip {
  text-align: center;
  color: #909399;
  font-size: 14px;
  margin-bottom: 20px;
}
</style>
