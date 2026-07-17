<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createReport } from '@/api/report'

const props = defineProps({
  // 举报目标类型：drift_bottle / bottle_reply / letter / campfire_message
  targetType: {
    type: String,
    required: true
  },
  // 举报目标 ID
  targetId: {
    type: [Number, String],
    default: null
  }
})

const visible = ref(false)
const content = ref('')
const loading = ref(false)

// 允许父组件通过 open(targetId) 临时覆盖 targetId（仅本次打开有效）
const pendingTargetId = ref(null)

const effectiveTargetId = () => {
  if (pendingTargetId.value !== null) return pendingTargetId.value
  return props.targetId
}

// 通过 ref 调用：open() 使用 props.targetId；open(id) 临时使用 id
function open(overrideTargetId) {
  if (overrideTargetId !== undefined && overrideTargetId !== null) {
    pendingTargetId.value = overrideTargetId
  } else {
    pendingTargetId.value = null
  }
  content.value = ''
  visible.value = true
}

async function handleSubmit() {
  const id = effectiveTargetId()
  if (!props.targetType) {
    ElMessage.warning('缺少举报目标类型')
    return
  }
  if (id === null || id === undefined || id === '') {
    ElMessage.warning('缺少举报目标')
    return
  }
  if (!content.value.trim()) {
    ElMessage.warning('请填写举报原因')
    return
  }
  loading.value = true
  try {
    await createReport({
      targetType: props.targetType,
      targetId: id,
      content: content.value.trim()
    })
    ElMessage.success('举报已提交')
    visible.value = false
    content.value = ''
  } catch (e) {
    // 拦截器已统一提示，这里仅做特殊错误码的差异化文案
    if (e?.code === 4017) {
      ElMessage.warning('您已举报过该内容')
      visible.value = false
    } else if (e?.code === 4016) {
      ElMessage.warning('不能举报自己')
      visible.value = false
    } else if (e?.code === 4015) {
      ElMessage.warning('账号已被封禁，无法举报')
    }
    // 其它错误已由 request.js 拦截器统一 ElMessage.error
  } finally {
    loading.value = false
  }
}

function handleCancel() {
  visible.value = false
  content.value = ''
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="举报"
    width="480px"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div class="report-tip">请如实描述举报原因，我们会尽快审核处理。</div>
    <el-input
      v-model="content"
      type="textarea"
      :rows="5"
      maxlength="500"
      show-word-limit
      placeholder="请填写举报原因（必填，最长 500 字）"
    />
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        提交举报
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.report-tip {
  font-size: 13px;
  color: #909399;
  margin-bottom: 10px;
}
</style>
