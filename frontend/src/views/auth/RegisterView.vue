<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { sendSmsCode, loginByPhone } from '@/api/auth'
import CaptchaDialog from '@/components/CaptchaDialog.vue'

// 4026 = PHONE_ALREADY_BOUND：手机号已绑定其他账户
const PHONE_ALREADY_BOUND_CODE = 4026

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)

// 手机号注册表单（所有注册均需手机号 + 验证码）
const phoneRegisterForm = reactive({
  phone: '',
  code: '',
  password: '',
  confirmPassword: ''
})
const phoneCodeSending = ref(false)
const phoneCodeCountdown = ref(0)
// 图片验证码弹窗
const captchaDialogVisible = ref(false)

// 首次注册昵称设置弹窗
const nicknameDialogVisible = ref(false)
const nicknameInput = ref('')
const nicknameSubmitting = ref(false)
// 注册成功后回显的 UID
const registeredUid = ref(null)

// 发送注册短信：先弹图片验证码
function handleSendPhoneCode() {
  if (!phoneRegisterForm.phone) {
    ElMessage.warning('请输入手机号')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(phoneRegisterForm.phone)) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  captchaDialogVisible.value = true
}

// 图片验证码校验通过后，发送注册短信
async function onCaptchaSuccess({ captchaId, captcha }) {
  captchaDialogVisible.value = false
  phoneCodeSending.value = true
  try {
    await sendSmsCode({
      phone: phoneRegisterForm.phone,
      scene: 'register',
      captchaId,
      captcha
    })
    ElMessage.success('验证码已发送')
    phoneCodeCountdown.value = 60
    const timer = setInterval(() => {
      phoneCodeCountdown.value--
      if (phoneCodeCountdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (e) {
    // 错误已拦截
  } finally {
    phoneCodeSending.value = false
  }
}

// 手机号注册提交：注册成功后自动登录，回显 UID，弹昵称设置
async function handlePhoneRegister() {
  if (!phoneRegisterForm.phone || !phoneRegisterForm.code) {
    ElMessage.warning('请填写手机号和验证码')
    return
  }
  if (!phoneRegisterForm.password || !phoneRegisterForm.confirmPassword) {
    ElMessage.warning('请设置登录密码')
    return
  }
  if (phoneRegisterForm.password.length < 6 || phoneRegisterForm.password.length > 50) {
    ElMessage.warning('密码长度为 6-50 个字符')
    return
  }
  if (phoneRegisterForm.password !== phoneRegisterForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    // 1. 调注册接口，拿到 userId 与 uid
    const regRes = await userStore.register({
      phone: phoneRegisterForm.phone,
      code: phoneRegisterForm.code,
      password: phoneRegisterForm.password
    })
    const uid = regRes?.uid
    registeredUid.value = uid

    // 2. 自动登录：用刚注册的手机号 + 密码换取 JWT，避免用户再走一次登录
    const loginData = await loginByPhone({
      phone: phoneRegisterForm.phone,
      password: phoneRegisterForm.password
    })
    userStore.setToken(loginData.token)
    userStore.setUserInfo(loginData.user)

    // 3. 弹 UID 提示 + 昵称设置
    ElMessageBox.alert(
      `<div style="line-height:1.8;text-align:center">
         <p style="margin:0 0 8px">🎉 注册成功！</p>
         <p style="margin:0 0 8px">你的账号 UID 是</p>
         <p style="margin:0;font-size:24px;font-weight:700;color:#f5a623;letter-spacing:2px">${uid ?? ''}</p>
         <p style="margin:8px 0 0;color:#909399;font-size:12px">UID 可与手机号同样用于登录，请妥善保管</p>
       </div>`,
      '注册成功',
      {
        dangerouslyUseHTMLString: true,
        confirmButtonText: '去设置昵称',
        callback: () => {
          // 注册时未设置昵称 → 弹昵称设置弹窗
          if (loginData.nicknameSet === false || !loginData.user?.nickname) {
            nicknameDialogVisible.value = true
          } else {
            finishRegister()
          }
        }
      }
    ).catch(() => {
      // 用户关闭弹窗：仍尝试弹昵称
      if (loginData.nicknameSet === false || !loginData.user?.nickname) {
        nicknameDialogVisible.value = true
      } else {
        finishRegister()
      }
    })
  } catch (e) {
    // 4026=PHONE_ALREADY_BOUND：注册时手机号已存在 → 提示去登录旧账户
    // 用户拍板"不实现注销"，所以仅提供去登录选项；旧账户可在登录后通过 BindPhoneDialog 换绑新手机号
    if (e?.code === PHONE_ALREADY_BOUND_CODE) {
      try {
        await ElMessageBox.confirm(
          '该手机号已绑定账户，无法直接注册。\n\n如需用该手机号注册新账户，请先登录已绑定该手机号的账户，更换绑定手机号后再来注册。',
          '该手机号已被注册',
          {
            confirmButtonText: '去登录',
            cancelButtonText: '我知道了',
            type: 'warning'
          }
        )
        // 用户选择"去登录" → 跳登录页，预填手机号
        router.push({ name: 'login' })
      } catch (cancelErr) {
        // 用户点"我知道了"：留在注册页让他改手机号
      }
    }
    // 其他错误已由 request.js 拦截
  } finally {
    loading.value = false
  }
}

// 昵称设置确认
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
    finishRegister()
  } catch (e) {
    // 错误已拦截
  } finally {
    nicknameSubmitting.value = false
  }
}

function skipNickname() {
  nicknameDialogVisible.value = false
  finishRegister()
}

function finishRegister() {
  const redirect = route.query.redirect || '/'
  router.replace(redirect)
}

function goLogin() {
  router.push('/login')
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <h1 class="auth-title">glimmer</h1>
        <p class="auth-subtitle">加入萤光，成为温暖的旅人</p>
      </div>

      <el-form label-position="top" size="large" @keyup.enter="handlePhoneRegister">
        <el-form-item label="手机号">
          <el-input
            v-model="phoneRegisterForm.phone"
            placeholder="请输入手机号"
            clearable
            maxlength="11"
          />
        </el-form-item>
        <el-form-item label="验证码">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-input
              v-model="phoneRegisterForm.code"
              placeholder="请输入验证码"
              clearable
              maxlength="6"
              style="flex: 1"
            />
            <el-button
              :disabled="phoneCodeCountdown > 0"
              :loading="phoneCodeSending"
              @click="handleSendPhoneCode"
            >
              {{ phoneCodeCountdown > 0 ? `${phoneCodeCountdown}秒` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="设置登录密码">
          <el-input
            v-model="phoneRegisterForm.password"
            type="password"
            placeholder="6-50 个字符"
            show-password
            clearable
          />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input
            v-model="phoneRegisterForm.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            show-password
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            style="width: 100%"
            @click="handlePhoneRegister"
          >
            注册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        已有账号？
        <el-link type="primary" underline="never" @click="goLogin">返回登录</el-link>
      </div>
    </div>

    <!-- 首次注册昵称设置弹窗 -->
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
    <CaptchaDialog v-model="captchaDialogVisible" @success="onCaptchaSuccess" />
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
  max-width: 420px;
  padding: 40px 32px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(245, 166, 35, 0.15);
}
.auth-header {
  text-align: center;
  margin-bottom: 24px;
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
  margin-top: 8px;
}
.nickname-dialog .nickname-tip {
  text-align: center;
  color: #909399;
  font-size: 14px;
  margin-bottom: 20px;
}
</style>
