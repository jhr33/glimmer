<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { signIn, getSignInStatus } from '@/api/token'

const router = useRouter()
const userStore = useUserStore()

const user = computed(() => userStore.userInfo || {})
const signedInToday = ref(false)
const signInLoading = ref(false)
// 防止重复触发签到流程（onMounted 可能因 HMR/路由切换被调用多次）
const signInTriggered = ref(false)

// 签到结果弹窗
const signInDialogVisible = ref(false)
const signInResult = ref(null)

// 萤火花园亮度等级映射（开发文档 2.7.2 节）
function getBrightnessLevel(totalFirefly) {
  const v = Number(totalFirefly) || 0
  if (v >= 200) return 5
  if (v >= 100) return 4
  if (v >= 60) return 3
  if (v >= 30) return 2
  if (v >= 10) return 1
  return 0
}

const brightnessLevel = computed(() => getBrightnessLevel(user.value.totalFirefly))

// 不同亮度等级对应的花园背景色（从全黑到满园星光）
const gardenStyle = computed(() => {
  const level = brightnessLevel.value
  const bgMap = {
    0: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 100%)',
    1: 'linear-gradient(135deg, #2d2d44 0%, #1f2a4a 100%)',
    2: 'linear-gradient(135deg, #3d3d5c 0%, #2a3a5e 100%)',
    3: 'linear-gradient(135deg, #5a4a6e 0%, #3a4a7e 100%)',
    4: 'linear-gradient(135deg, #8a6e5a 0%, #5a5a8e 100%)',
    5: 'linear-gradient(135deg, #f5a623 0%, #ffd970 100%)'
  }
  const textColor = level >= 4 ? '#3a2a00' : '#ffd970'
  return {
    background: bgMap[level],
    color: textColor
  }
})

const brightnessLabel = computed(() => {
  const labels = ['全黑', '微光', '暗淡', '明亮', '萤光环绕', '满园星光']
  return labels[brightnessLevel.value] || '全黑'
})

const navItems = [
  { name: 'driftBottle', label: '漂流瓶', icon: '🍾', desc: '投放一段心事' },
  { name: 'letter', label: '信件', icon: '✉️', desc: '一封温柔的来信' },
  { name: 'campfire', label: '篝火', icon: '🔥', desc: '围炉夜话' },
  { name: 'ai', label: 'AI 对话', icon: '🤖', desc: '温暖的倾听者' },
  { name: 'garden', label: '花园', icon: '🌷', desc: '种下你的花' },
  { name: 'notifications', label: '通知中心', icon: '🔔', desc: '查看消息' }
]

function goNav(name) {
  router.push({ name })
}

async function doSignIn() {
  signInLoading.value = true
  try {
    const res = await signIn()
    if (res.code === 200) {
      signedInToday.value = true
      signInResult.value = res.data
      await userStore.fetchUserInfo()
      signInDialogVisible.value = true
    }
  } catch (err) {
    if (err.code === 4004) {
      signedInToday.value = true
    }
  } finally {
    signInLoading.value = false
  }
}

async function loadSignInStatus() {
  // 防止重复触发
  if (signInTriggered.value) return
  signInTriggered.value = true
  try {
    const res = await getSignInStatus()
    if (res.code === 200) {
      signedInToday.value = res.data.signedInToday
      // 未签到时自动签到
      if (!res.data.signedInToday) {
        await doSignIn()
      }
    }
  } catch (err) {
    console.error('获取签到状态失败', err)
  }
}

onMounted(() => {
  if (userStore.isLoggedIn && !userStore.userInfo) {
    userStore.fetchUserInfo().catch(() => {})
  }
  loadSignInStatus()
})
</script>

<template>
  <div class="home-view">
    <!-- 用户信息卡片 -->
    <el-card class="user-card" shadow="hover">
      <div class="user-info">
        <el-avatar :size="64" class="user-avatar">
          {{ user.username?.charAt(0)?.toUpperCase() || 'U' }}
        </el-avatar>
        <div class="user-meta">
          <h2 class="user-nickname">{{ user.nickname || user.username || '旅人' }}</h2>
          <div class="user-tags">
            <el-tag size="small" type="info">用户名：{{ user.username || '-' }}</el-tag>
            <el-tag size="small" type="warning">匿名：{{ user.anonymousName || '-' }}</el-tag>
            <el-tag v-if="user.role === 'admin'" size="small" type="danger">管理员</el-tag>
          </div>
          <div class="user-stats">
            <span>代币：{{ user.tokenBalance ?? 0 }}</span>
            <el-divider direction="vertical" />
            <span>累计萤火：{{ user.totalFirefly ?? 0 }}</span>
            <el-divider direction="vertical" />
            <span>萤火余额：{{ user.fireflyBalance ?? 0 }}</span>
            <el-divider direction="vertical" />
            <span>连续签到：{{ user.totalSignDays ?? 0 }} 天</span>
          </div>
          <div class="sign-in-btn-wrap">
            <el-tag v-if="signedInToday" type="success" size="large" effect="dark">✅ 今日已签到</el-tag>
            <el-tag v-else type="warning" size="large" effect="dark">⏳ 签到中...</el-tag>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 签到结果弹窗 -->
    <el-dialog
      v-model="signInDialogVisible"
      title="签到成功"
      width="400px"
      :show-close="true"
      center
      class="sign-in-dialog"
    >
      <div class="sign-in-result">
        <div class="sign-in-icon">🎉</div>
        <div class="sign-in-title">签到成功！</div>
        <div class="sign-in-reward" v-if="signInResult">
          <div class="reward-row">
            <span class="reward-label">获得代币</span>
            <span class="reward-value">+{{ signInResult.reward }}</span>
          </div>
          <div class="reward-row">
            <span class="reward-label">累计签到</span>
            <span class="reward-value">{{ signInResult.totalSignDays }} 天</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="signInDialogVisible = false">收下奖励</el-button>
      </template>
    </el-dialog>

    <!-- 萤火花园区域（占位，背景色根据萤火值变化） -->
    <div class="garden-area" :style="gardenStyle">
      <div class="garden-inner">
        <div class="garden-title">萤火花园</div>
        <div class="garden-level">亮度等级 {{ brightnessLevel }} · {{ brightnessLabel }}</div>
        <div class="garden-desc">累计萤火值 {{ user.totalFirefly ?? 0 }}，让花园更明亮一点</div>
      </div>
    </div>

    <!-- 功能导航 -->
    <div class="nav-grid">
      <el-card
        v-for="item in navItems"
        :key="item.name"
        class="nav-card"
        shadow="hover"
        @click="goNav(item.name)"
      >
        <div class="nav-icon">{{ item.icon }}</div>
        <div class="nav-label">{{ item.label }}</div>
        <div class="nav-desc">{{ item.desc }}</div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.home-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 20px;
}
.user-avatar {
  background: #f5a623;
  color: #fff;
  font-size: 24px;
  font-weight: bold;
  flex-shrink: 0;
}
.user-meta {
  flex: 1;
  min-width: 0;
}
.user-nickname {
  margin: 0 0 8px;
  font-size: 20px;
  color: #303133;
}
.user-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.user-stats {
  color: #909399;
  font-size: 14px;
}
.sign-in-btn-wrap {
  margin-top: 12px;
}
.sign-in-dialog .sign-in-result {
  text-align: center;
  padding: 10px 0;
}
.sign-in-dialog .sign-in-icon {
  font-size: 48px;
  margin-bottom: 12px;
}
.sign-in-dialog .sign-in-title {
  font-size: 20px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 20px;
}
.sign-in-dialog .sign-in-reward {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sign-in-dialog .reward-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.sign-in-dialog .reward-label {
  color: #909399;
  font-size: 14px;
}
.sign-in-dialog .reward-value {
  font-size: 18px;
  font-weight: bold;
  color: #f5a623;
}
.garden-area {
  border-radius: 12px;
  padding: 40px 24px;
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.4s ease;
}
.garden-inner {
  text-align: center;
}
.garden-title {
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 8px;
}
.garden-level {
  font-size: 16px;
  margin-bottom: 4px;
}
.garden-desc {
  font-size: 13px;
  opacity: 0.85;
}
.nav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}
.nav-card {
  cursor: pointer;
  text-align: center;
  transition: transform 0.2s ease;
}
.nav-card:hover {
  transform: translateY(-4px);
}
.nav-icon {
  font-size: 36px;
  margin-bottom: 8px;
}
.nav-label {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}
.nav-desc {
  font-size: 12px;
  color: #909399;
}
</style>
