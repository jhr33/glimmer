<template>
  <div class="image-uploader">
    <el-upload
      v-model:file-list="fileList"
      :http-request="handleUpload"
      :limit="limit"
      :on-exceed="handleExceed"
      :on-remove="handleRemove"
      :before-upload="beforeUpload"
      list-type="picture-card"
      accept="image/*"
    >
      <el-icon><Plus /></el-icon>
      <template #tip>
        <div class="el-upload__tip">
          最多上传 {{ limit }} 张图片，单张不超过 5MB
        </div>
      </template>
    </el-upload>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getOssSignature, uploadToOss } from '@/api/upload'

const props = defineProps({
  /** 已上传的图片URL列表（v-model） */
  modelValue: {
    type: Array,
    default: () => []
  },
  /** 最多上传张数 */
  limit: {
    type: Number,
    default: 9
  },
  /** 上传场景：article 文章 / campfire 篝火 */
  scene: {
    type: String,
    default: 'common'
  }
})

const emit = defineEmits(['update:modelValue'])

const fileList = ref([])

// 初始化：将 modelValue 转为 el-upload 的 fileList 格式
watch(() => props.modelValue, (newVal) => {
  fileList.value = newVal.map((url, index) => ({
    name: `image_${index}`,
    url,
    status: 'success'
  }))
}, { immediate: true })

// 上传前校验
function beforeUpload(file) {
  const isImage = file.type.startsWith('image/')
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isImage) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

// 自定义上传：先获取 OSS 签名，再直传
async function handleUpload({ file }) {
  try {
    const res = await getOssSignature(props.scene)
    const url = await uploadToOss(file, res.data)
    // 上传成功，更新 fileList 和 modelValue
    const newUrls = [...props.modelValue, url]
    emit('update:modelValue', newUrls)
    ElMessage.success('上传成功')
  } catch (error) {
    ElMessage.error(error.message || '上传失败')
    throw error
  }
}

// 超出限制提示
function handleExceed() {
  ElMessage.warning(`最多只能上传 ${props.limit} 张图片`)
}

// 删除图片
function handleRemove(file) {
  const newUrls = props.modelValue.filter(url => url !== file.url)
  emit('update:modelValue', newUrls)
}
</script>

<style scoped>
.image-uploader :deep(.el-upload--picture-card) {
  width: 100px;
  height: 100px;
}
.image-uploader :deep(.el-upload-list--picture-card .el-upload-list__item) {
  width: 100px;
  height: 100px;
}
</style>
