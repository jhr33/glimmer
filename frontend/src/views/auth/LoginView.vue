<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { sendSmsCode, loginBySms } from '@/api/auth'
import CaptchaDialog from '@/components/CaptchaDialog.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 登录方式切换：account=账户密码（默认）/ sms=手机验证码（忘记密码备用入口）
const activeMode = ref('account')
const loading = ref(false)

// ========== 账户密码登录 ==========
const loginFormRef = ref()
const loginForm = reactive({
  account: '',
  password: ''
})

// 账号校验：手机号或纯数字 UID，二选一
const validateAccount = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入账号'))
    return
  }
  const isPhone = /^1[3-9]\d{9}$/.test(value)
  const isUid = /^\d+$/.test(value)
  if (!isPhone && !isUid) {
    callback(new Error('账号需为手机号或 UID'))
    return
  }
  callback()
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

const accountRules = {
  account: [{ validator: validateAccount, trigger: 'blur' }],
  password: [{ validator: validatePassword, trigger: 'blur' }]
}

// ========== 手机验证码登录（备用入口） ==========
const smsFormRef = ref()
const smsForm = reactive({
  phone: '',
  code: ''
})
const smsCodeSending = ref(false)
const smsCodeCountdown = ref(0)
// 图片验证码弹窗
const captchaDialogVisible = ref(false)

const validatePhone = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入手机号'))
  } else if (!/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('手机号格式不正确'))
  } else {
    callback()
  }
}

const validateCode = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入验证码'))
  } else if (!/^\d{6}$/.test(value)) {
    callback(new Error('验证码为 6 位数字'))
  } else {
    callback()
  }
}

const smsRules = {
  phone: [{ validator: validatePhone, trigger: 'blur' }],
  code: [{ validator: validateCode, trigger: 'blur' }]
}

// 首次登录昵称设置弹窗
const nicknameDialogVisible = ref(false)
const nicknameInput = ref('')
const nicknameSubmitting = ref(false)

// 点击发送验证码：先弹图片验证码，校验通过后才发短信
async function handleSendSmsCode() {
  if (!smsFormRef.value) return
  try {
    await smsFormRef.value.validateField('phone')
  } catch (e) {
    return
  }
  captchaDialogVisible.value = true
}

// 图片验证码校验通过回调：携带 captchaId + captcha 发送短信
async function onCaptchaSuccess({ captchaId, captcha }) {
  captchaDialogVisible.value = false
  smsCodeSending.value = true
  try {
    await sendSmsCode({
      phone: smsForm.phone,
      scene: 'login',
      captchaId,
      captcha
    })
    ElMessage.success('验证码已发送，请注意查收')
    smsCodeCountdown.value = 60
    const timer = setInterval(() => {
      smsCodeCountdown.value--
      if (smsCodeCountdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (e) {
    // 错误已拦截
  } finally {
    smsCodeSending.value = false
  }
}

// 账户密码登录：account 支持 phone 或 UID，后端智能识别
async function handleAccountLogin() {
  if (!loginFormRef.value) return
  try {
    await loginFormRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    const data = await userStore.login({
      account: loginForm.account.trim(),
      password: loginForm.password
    })
    handleLoginSuccess(data)
  } catch (e) {
    handleLoginError(e)
  } finally {
    loading.value = false
  }
}

// 手机验证码登录（备用入口，未注册自动建号）
async function handleSmsLogin() {
  if (!smsFormRef.value) return
  try {
    await smsFormRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    const data = await loginBySms({ phone: smsForm.phone, code: smsForm.code })
    userStore.setToken(data.token)
    userStore.setUserInfo(data.user)
    handleLoginSuccess(data)
  } catch (e) {
    handleLoginError(e)
  } finally {
    loading.value = false
  }
}

// 登录成功处理
function handleLoginSuccess(data) {
  if (data.nicknameSet === false || !data.user?.nickname) {
    nicknameDialogVisible.value = true
    return
  }
  ElMessage.success('登录成功')
  const redirect = route.query.redirect || '/'
  router.replace(redirect)
}

// 登录失败处理
function handleLoginError(e) {
  if (e?.code === 4015) {
    // 管理员手动封禁：禁止登录，弹出封禁提示与申诉邮箱
    ElMessageBox.alert(
      `<div style="line-height:1.8">
         <p style="margin:0 0 8px">你的账号已被管理员封禁，暂时无法登录。</p>
         <p style="margin:0 0 8px">如对封禁有异议，可联系管理员申诉：</p>
         <p style="margin:0;font-weight:600;color:#b8821f;font-size:15px">📮 1623919525@qq.com</p>
       </div>`,
      '账号已被封禁',
      {
        dangerouslyUseHTMLString: true,
        type: 'error',
        confirmButtonText: '我知道了'
      }
    ).catch(() => {})
  } else {
    ElMessage.error(e?.message || '登录失败，请稍后重试')
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

// 切换登录方式
function switchMode(mode) {
  activeMode.value = mode
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <h1 class="auth-title">glimmer</h1>
        <p class="auth-subtitle">萤光，温暖你的每一个夜晚</p>
      </div>

      <!-- 账户密码登录（默认） -->
      <el-form
        v-if="activeMode === 'account'"
        ref="loginFormRef"
        :model="loginForm"
        :rules="accountRules"
        label-position="top"
        size="large"
        @keyup.enter="handleAccountLogin"
      >
        <el-form-item label="账号" prop="account">
          <el-input
            v-model="loginForm.account"
            placeholder="手机号或 UID"
            clearable
          />
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
            @click="handleAccountLogin"
          >
            登录
          </el-button>
        </el-form-item>
        <!-- 忘记密码备用入口：切换到验证码登录 -->
        <div class="auth-aux-link">
          <el-link type="primary" underline="never" @click="switchMode('sms')">
            忘记密码？用验证码登录
          </el-link>
        </div>
      </el-form>

      <!-- 手机验证码登录（备用入口） -->
      <el-form
        v-else
        ref="smsFormRef"
        :model="smsForm"
        :rules="smsRules"
        label-position="top"
        size="large"
        @keyup.enter="handleSmsLogin"
      >
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="smsForm.phone" placeholder="请输入手机号" clearable maxlength="11" />
        </el-form-item>
        <el-form-item label="验证码" prop="code">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-input v-model="smsForm.code" placeholder="请输入验证码" clearable maxlength="6" style="flex: 1" />
            <el-button
              :disabled="smsCodeCountdown > 0"
              :loading="smsCodeSending"
              @click="handleSendSmsCode"
            >
              {{ smsCodeCountdown > 0 ? `${smsCodeCountdown}秒` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            style="width: 100%"
            @click="handleSmsLogin"
          >
            登录 / 注册
          </el-button>
        </el-form-item>
        <!-- 返回账户密码登录 -->
        <div class="auth-aux-link">
          <el-link type="primary" underline="never" @click="switchMode('account')">
            ← 返回账号登录
          </el-link>
        </div>
      </el-form>

      <div class="auth-footer">
        还没有账号？
        <el-link type="primary" underline="never" @click="goRegister">立即注册</el-link>
      </div>
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

    <!-- 图片验证码弹窗 -->
    <CaptchaDialog
      v-model="captchaDialogVisible"
      @success="onCaptchaSuccess"
    />
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
.auth-aux-link {
  text-align: center;
  margin-top: -4px;
  margin-bottom: 8px;
  font-size: 14px;
}
.auth-footer {
  text-align: center;
  font-size: 14px;
  color: #666;
  margin-top: 16px;
}
.nickname-dialog .nickname-tip {
  text-align: center;
  color: #909399;
  font-size: 14px;
  margin-bottom: 20px;
}
</style>
