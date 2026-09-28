<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'
import { ElMessage } from 'element-plus'
import { getSignInStatus } from '@/api/token'
import { changePassword } from '@/api/user'
import AvatarUploader from '@/components/AvatarUploader.vue'
import BindPhoneDialog from '@/components/BindPhoneDialog.vue'

const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

const user = computed(() => userStore.userInfo || {})
const signedInToday = ref(false)
const unreadCount = computed(() => notificationStore.unreadCount)

// 绑定/换绑手机号弹窗
const bindPhoneDialogVisible = ref(false)
// 强制绑定模式：用户未绑定手机号时自动弹出，禁止关闭
const forceBindPhone = ref(false)

/** 路由进入 /my 时检查：未绑定手机号则强制弹绑定弹窗 */
function checkPhoneBinding() {
  if (userStore.isLoggedIn && !userStore.userInfo?.phone) {
    forceBindPhone.value = true
    bindPhoneDialogVisible.value = true
  } else {
    forceBindPhone.value = false
  }
}

// 修改密码弹窗
const passwordDialogVisible = ref(false)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const passwordSubmitting = ref(false)

/** 功能入口列表（移动端「我的」页聚合二级功能） */
const menuItems = [
  { index: '/letter', label: '信件', icon: '✉️', desc: '一封温柔的来信' },
  { index: '/garden', label: '花园', icon: '🌷', desc: '种下你的花' },
  { index: '/articles', label: '交流会', icon: '📝', desc: '记录与分享' },
  { index: '/articles/favorites', label: '我的收藏', icon: '⭐', desc: '收藏的好文章' },
  { index: '/announcements', label: '公告', icon: '📢', desc: '平台动态' },
  { index: '/notifications', label: '通知中心', icon: '🔔', desc: '查看消息', showBadge: true },
  { index: '/feedback', label: '意见反馈', icon: '💬', desc: '告诉我们' }
]

function go(index) {
  router.push(index)
}

// 打开修改密码弹窗
function openPasswordDialog() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
}

// 提交修改密码
async function handleChangePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (passwordForm.newPassword.length < 6 || passwordForm.newPassword.length > 50) {
    ElMessage.warning('新密码长度为 6-50 个字符')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  passwordSubmitting.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('密码修改成功')
    passwordDialogVisible.value = false
  } catch (e) {
    // 错误已拦截
  } finally {
    passwordSubmitting.value = false
  }
}

async function handleLogout() {
  await userStore.logout()
  notificationStore.clear()
  router.push('/login')
}

async function loadSignInStatus() {
  try {
    const res = await getSignInStatus()
    if (res.code === 200) {
      signedInToday.value = res.data.signedInToday
    }
  } catch (e) {
    // 静默失败
  }
}

onMounted(async () => {
  if (userStore.isLoggedIn) {
    if (!userStore.userInfo) {
      try {
        await userStore.fetchUserInfo()
      } catch (e) {
        // 静默失败：userInfo 拉取失败时不阻塞页面
      }
    }
    checkPhoneBinding()
    loadSignInStatus()
  }
})

// 用户从 BindPhoneDialog 绑定成功后，forceMode 解除
function onBindPhoneVisibleChange(val) {
  bindPhoneDialogVisible.value = val
  if (!val) {
    forceBindPhone.value = false
  }
}
</script>

<template>
  <div class="my-view">
    <!-- 用户信息头部 -->
    <div class="profile-header">
      <AvatarUploader :url="user.avatarUrl" :user-id="user.id" :size="64" />
      <div class="profile-meta">
        <div class="profile-name">{{ user.nickname || '旅人' }}</div>
        <div class="profile-sub">UID：{{ user.uid ?? user.id + 10000 }}</div>
      </div>
      <el-button v-if="signedInToday" type="success" size="small" effect="dark" disabled>
        已签到
      </el-button>
      <el-button v-else type="warning" size="small" effect="dark" @click="router.push('/home')">
        去签到
      </el-button>
    </div>

    <!-- 资产概览 -->
    <div class="asset-grid">
      <div class="asset-item">
        <div class="asset-value">{{ user.tokenBalance ?? 0 }}</div>
        <div class="asset-label">代币</div>
      </div>
      <div class="asset-item">
        <div class="asset-value">{{ user.fireflyBalance ?? 0 }}</div>
        <div class="asset-label">萤火余额</div>
      </div>
      <div class="asset-item">
        <div class="asset-value">{{ user.totalFirefly ?? 0 }}</div>
        <div class="asset-label">累计萤火</div>
      </div>
    </div>

    <!-- 功能入口 -->
    <div class="menu-section">
      <!-- 修改密码入口 -->
      <div class="menu-item" @click="openPasswordDialog">
        <span class="menu-icon">🔒</span>
        <div class="menu-text">
          <div class="menu-label">修改密码</div>
          <div class="menu-desc">定期更换更安全</div>
        </div>
        <span class="menu-arrow">›</span>
      </div>
      <!-- 手机号绑定/换绑入口：已绑定展示当前号码并提示换绑，未绑定提示去绑定 -->
      <div class="menu-item" @click="bindPhoneDialogVisible = true">
        <span class="menu-icon">📱</span>
        <div class="menu-text">
          <div class="menu-label">{{ user.phone ? '修改手机号' : '绑定手机号' }}</div>
          <div class="menu-desc">{{ user.phone || '未绑定，点击立即绑定' }}</div>
        </div>
        <span class="menu-arrow">›</span>
      </div>
      <div class="menu-item" v-for="item in menuItems" :key="item.index" @click="go(item.index)">
        <span class="menu-icon">{{ item.icon }}</span>
        <div class="menu-text">
          <div class="menu-label">{{ item.label }}</div>
          <div class="menu-desc">{{ item.desc }}</div>
        </div>
        <el-badge
          v-if="item.showBadge && unreadCount > 0"
          :value="unreadCount"
          :max="99"
          class="menu-badge"
        />
        <span class="menu-arrow">›</span>
      </div>
    </div>

    <!-- 退出登录 -->
    <div class="logout-wrap">
      <el-button type="danger" plain size="large" @click="handleLogout">退出登录</el-button>
    </div>

    <!-- 绑定/换绑手机号弹窗 -->
    <BindPhoneDialog
      :visible="bindPhoneDialogVisible"
      :force-mode="forceBindPhone"
      @update:visible="onBindPhoneVisibleChange"
    />

    <!-- 修改密码弹窗 -->
    <el-dialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="400px"
      :close-on-click-modal="false"
      center
    >
      <el-form label-position="top" size="large">
        <el-form-item label="原密码">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            placeholder="请输入原密码"
            show-password
            clearable
          />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            placeholder="6-50 个字符"
            show-password
            clearable
          />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
            clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSubmitting" @click="handleChangePassword">
          确认修改
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.my-view {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 用户信息头部 */
.profile-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: linear-gradient(135deg, #fff8e6 0%, #fffaf0 100%);
  border-radius: 12px;
}
/* 头像容器：悬停显示编辑角标，点击弹出更换/恢复默认菜单 */
.avatar-wrap {
  position: relative;
  cursor: pointer;
  border-radius: 50%;
  flex-shrink: 0;
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
/* 头像操作菜单（popover 内容） */
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
.profile-meta {
  flex: 1;
  min-width: 0;
}
.profile-name {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.profile-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

/* 资产概览 */
.asset-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  background: #fff;
  border-radius: 12px;
  padding: 16px 8px;
}
.asset-item {
  text-align: center;
}
.asset-value {
  font-size: 20px;
  font-weight: bold;
  color: #f5a623;
}
.asset-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

/* 功能菜单 */
.menu-section {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
  transition: background 0.15s ease;
}
.menu-item:last-child {
  border-bottom: none;
}
.menu-item:active {
  background: #fafafa;
}
.menu-icon {
  font-size: 22px;
  width: 28px;
  text-align: center;
}
.menu-text {
  flex: 1;
  min-width: 0;
}
.menu-label {
  font-size: 15px;
  color: #303133;
}
.menu-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.menu-arrow {
  color: #c0c4cc;
  font-size: 20px;
}

/* 退出登录 */
.logout-wrap {
  padding: 8px 0 24px;
}
.logout-wrap .el-button {
  width: 100%;
}
</style>
