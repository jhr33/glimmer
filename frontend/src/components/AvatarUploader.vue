<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import UserAvatar from './UserAvatar.vue'
import { getOssSignature, uploadToOss } from '@/api/upload'
import { updateAvatar } from '@/api/user'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  /** 头像 URL */
  url: { type: String, default: '' },
  /** 用户 ID（默认头像取模用） */
  userId: { type: [Number, String], default: 0 },
  /** 头像直径 px */
  size: { type: Number, default: 64 }
})

const userStore = useUserStore()
const avatarInputRef = ref(null)
const avatarUploading = ref(false)
const avatarMenuVisible = ref(false)
const MAX_AVATAR_SIZE_MB = 5

/** 点击头像：切换操作菜单 */
function toggleAvatarMenu() {
  avatarMenuVisible.value = !avatarMenuVisible.value
}

/** 点击"更换头像"：打开文件选择器 */
function chooseAvatar() {
  avatarMenuVisible.value = false
  avatarInputRef.value?.click()
}

/** 选择头像文件后：OSS 直传 → 更新 user.avatar_url */
async function handleAvatarFileChange(e) {
  const file = e.target.files?.[0]
  // 清空 value 允许连续选择同一文件
  e.target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.error('只能上传图片文件')
    return
  }
  if (file.size / 1024 / 1024 > MAX_AVATAR_SIZE_MB) {
    ElMessage.error(`头像大小不能超过 ${MAX_AVATAR_SIZE_MB}MB`)
    return
  }
  avatarUploading.value = true
  try {
    const res = await getOssSignature('avatar')
    const avatarUrl = await uploadToOss(file, res.data)
    await updateAvatar({ avatarUrl })
    ElMessage.success('头像已更新')
    await userStore.fetchUserInfo()
  } catch (err) {
    ElMessage.error(err.message || '头像更新失败')
  } finally {
    avatarUploading.value = false
  }
}

/** 恢复系统默认头像（avatarUrl 传空串） */
async function handleResetAvatar() {
  avatarMenuVisible.value = false
  avatarUploading.value = true
  try {
    await updateAvatar({ avatarUrl: '' })
    ElMessage.success('已恢复默认头像')
    await userStore.fetchUserInfo()
  } catch (err) {
    ElMessage.error(err.message || '操作失败')
  } finally {
    avatarUploading.value = false
  }
}
</script>

<template>
  <el-popover
    v-model:visible="avatarMenuVisible"
    placement="bottom"
    :width="160"
    trigger="manual"
  >
    <template #reference>
      <div class="avatar-uploader" v-loading="avatarUploading" @click.stop="toggleAvatarMenu">
        <UserAvatar :url="url" :user-id="userId" :size="size" />
        <span class="avatar-edit-badge">✎</span>
      </div>
    </template>
    <div class="avatar-menu">
      <div class="avatar-menu-item" @click="chooseAvatar">更换头像</div>
      <el-divider style="margin: 4px 0" />
      <div
        class="avatar-menu-item danger"
        :class="{ disabled: !url }"
        @click="url && handleResetAvatar()"
      >恢复默认头像</div>
    </div>
  </el-popover>
  <input
    ref="avatarInputRef"
    type="file"
    accept="image/*"
    style="display: none"
    @change="handleAvatarFileChange"
  />
</template>

<style scoped>
.avatar-uploader {
  position: relative;
  cursor: pointer;
  border-radius: 50%;
  flex-shrink: 0;
  display: inline-block;
}
.avatar-edit-badge {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  border-radius: 50%;
  background: #f5a623;
  color: #fff;
  font-size: 12px;
  border: 2px solid #fff;
}
.avatar-menu {
  text-align: center;
}
.avatar-menu-item {
  padding: 8px 0;
  cursor: pointer;
  font-size: 14px;
  color: #303133;
  transition: color 0.15s;
}
.avatar-menu-item:hover {
  color: #f5a623;
}
.avatar-menu-item.danger {
  color: #909399;
}
.avatar-menu-item.danger:hover {
  color: #f56c6c;
}
.avatar-menu-item.disabled {
  color: #c0c4cc;
  cursor: not-allowed;
}
</style>
