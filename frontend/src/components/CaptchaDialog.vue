<template>
  <el-dialog
    v-model="visible"
    title="图片验证"
    width="360px"
    :close-on-click-modal="false"
    append-to-body
    center
  >
    <div class="captcha-box">
      <img v-if="captchaImage" :src="captchaImage" class="captcha-img" @click="loadCaptcha" title="点击刷新" />
      <el-input
        v-model="inputCode"
        placeholder="请输入图片验证码"
        maxlength="4"
        size="large"
        style="margin-top: 12px"
        @keyup.enter="confirm"
      />
      <div class="captcha-tip">看不清楚？点击图片刷新</div>
    </div>
    <template #footer>
      <el-button @click="cancel">取消</el-button>
      <el-button type="primary" :loading="loading" @click="confirm">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getCaptcha } from '@/api/auth'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'success'])

const visible = ref(props.modelValue)
const captchaImage = ref('')
const captchaId = ref('')
const inputCode = ref('')
const loading = ref(false)

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) {
    inputCode.value = ''
    loadCaptcha()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

async function loadCaptcha() {
  try {
    const res = await getCaptcha()
    captchaId.value = res.data.captchaId
    captchaImage.value = res.data.image
  } catch (e) {
    ElMessage.error('验证码加载失败')
  }
}

function confirm() {
  const code = inputCode.value.trim()
  if (!code) {
    ElMessage.warning('请输入图片验证码')
    return
  }
  // 成功回调，由父组件用 captchaId + code 调用 sendSmsCode
  emit('success', { captchaId: captchaId.value, captcha: code })
}

function cancel() {
  visible.value = false
}

defineExpose({ loadCaptcha })
</script>

<style scoped>
.captcha-box {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.captcha-img {
  width: 240px;
  height: 60px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid #e4e7ed;
}
.captcha-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
</style>
