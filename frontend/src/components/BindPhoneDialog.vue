<script setup>
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { sendSmsCode, bindPhone } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'
import CaptchaDialog from './CaptchaDialog.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  /** 强制模式：路由守卫强制绑定，禁止关闭 */
  forceMode: { type: Boolean, default: false }
})
const emit = defineEmits(['update:visible'])

const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

const form = reactive({ phone: '', code: '' })
const codeSending = ref(false)
const codeCountdown = ref(0)
const submitting = ref(false)
// 图片验证码弹窗
const captchaVisible = ref(false)

// 弹窗打开时：已绑定展示当前号码，输入框留空让用户填新号码
watch(() => props.visible, (val) => {
  if (val) {
    form.phone = ''
    form.code = ''
  }
})

function close() {
  if (props.forceMode) {
    // 强制模式下不允许关闭：唯一出口是绑定成功或退出登录
    ElMessage.warning('请先绑定手机号，或退出登录后重新登录其他账户')
    return
  }
  emit('update:visible', false)
}

// 强制模式下退出登录：唯一兜底出口，让用户切其他账户
async function handleLogout() {
  await userStore.logout()
  notificationStore.clear()
  emit('update:visible', false)
  router.replace('/login')
}

// 点击发送验证码：先弹图片验证码
function handleSendCode() {
  if (!form.phone) {
    ElMessage.warning('请输入手机号')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  captchaVisible.value = true
}

// 图片验证码校验通过后，发送绑定短信
async function onCaptchaSuccess({ captchaId, captcha }) {
  captchaVisible.value = false
  codeSending.value = true
  try {
    await sendSmsCode({
      phone: form.phone,
      scene: 'bind',
      captchaId,
      captcha
    })
    ElMessage.success('验证码已发送')
    codeCountdown.value = 60
    const timer = setInterval(() => {
      codeCountdown.value--
      if (codeCountdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {
    // 错误已拦截
  } finally {
    codeSending.value = false
  }
}

// 提交绑定/换绑：处理"手机号已被其他账户占用"场景
async function handleSubmit() {
  if (!form.phone || !form.code) {
    ElMessage.warning('请填写手机号和验证码')
    return
  }
  submitting.value = true
  try {
    await bindPhone({ phone: form.phone, code: form.code })
    ElMessage.success(userStore.userInfo?.phone ? '换绑成功' : '绑定成功')
    await userStore.fetchUserInfo()
    emit('update:visible', false)
  } catch (e) {
    // 4026=PHONE_ALREADY_BOUND：手机号已被其他账户占用 → 弹确认框"是否解绑旧账户"
    if (e?.code === 4026) {
      try {
        await ElMessageBox.confirm(
          '该手机号已绑定其他账户。\n由于你已通过短信验证码验证对该手机号的所有权，可将手机号从旧账户解绑并绑定到当前账户。\n\n旧账户将失去手机号登录能力，需要重新绑定手机号。是否继续？',
          '手机号已占用',
          {
            confirmButtonText: '解绑并绑定到当前账户',
            cancelButtonText: '取消',
            type: 'warning'
          }
        )
        // 用户确认 → 带 force=true 重新提交
        submitting.value = true
        try {
          await bindPhone({ phone: form.phone, code: form.code, force: true })
          ElMessage.success('解绑并绑定成功')
          await userStore.fetchUserInfo()
          emit('update:visible', false)
        } catch (e2) {
          // 错误已由 request.js 拦截
        } finally {
          submitting.value = false
        }
      } catch (confirmErr) {
        // 用户取消：保持弹窗打开，让他改手机号或退出
      }
    }
    // 其他错误已由 request.js 拦截
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="userStore.userInfo?.phone ? '换绑手机号' : '绑定手机号'"
    width="400px"
    :close-on-click-modal="false"
    :close-on-press-escape="!forceMode"
    :show-close="!forceMode"
    center
    @update:model-value="(v) => { if (!forceMode) emit('update:visible', v) }"
  >
    <!-- 强制绑定提示：登录后未绑定手机号，必须绑定才能继续 -->
    <el-alert
      v-if="forceMode"
      title="检测到当前账户未绑定手机号，请先完成绑定"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />
    <!-- 已绑定展示当前号码，输入新号码 + 验证码完成换绑 -->
    <el-alert
      v-if="userStore.userInfo?.phone"
      :title="`当前绑定手机号：${userStore.userInfo.phone}`"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />
    <el-form label-position="top" size="large">
      <el-form-item :label="userStore.userInfo?.phone ? '新手机号' : '手机号'">
        <el-input
          v-model="form.phone"
          :placeholder="userStore.userInfo?.phone ? '请输入新手机号' : '请输入手机号'"
          clearable
          maxlength="11"
        />
      </el-form-item>
      <el-form-item label="验证码">
        <div style="display: flex; gap: 8px; width: 100%">
          <el-input
            v-model="form.code"
            placeholder="请输入验证码"
            clearable
            maxlength="6"
            style="flex: 1"
          />
          <el-button :disabled="codeCountdown > 0" :loading="codeSending" @click="handleSendCode">
            {{ codeCountdown > 0 ? `${codeCountdown}秒` : '发送验证码' }}
          </el-button>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button v-if="!forceMode" @click="close">取消</el-button>
      <el-button v-else type="info" @click="handleLogout">退出登录</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ userStore.userInfo?.phone ? '确认换绑' : '确认绑定' }}
      </el-button>
    </template>

    <!-- 图片验证码弹窗 -->
    <CaptchaDialog v-model="captchaVisible" @success="onCaptchaSuccess" />
  </el-dialog>
</template>
