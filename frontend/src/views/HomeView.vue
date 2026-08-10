<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { Edit, Lock } from '@element-plus/icons-vue'
import { signIn, getSignInStatus, getSignInCalendar } from '@/api/token'
import { changePassword } from '@/api/user'
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

// 签到日历
const calendarYear = ref(new Date().getFullYear())
const calendarMonth = ref(new Date().getMonth() + 1) // 1-12
const signedDates = ref([]) // 签到日期列表 ['2026-08-01', ...]
const calendarLoading = ref(false)

/** 当月日历网格（6行×7列，含上下月填充） */
const calendarCells = computed(() => {
  const year = calendarYear.value
  const month = calendarMonth.value
  const firstDay = new Date(year, month - 1, 1)
  const firstWeekday = firstDay.getDay() // 0=周日
  const daysInMonth = new Date(year, month, 0).getDate()
  const today = new Date()
  const todayStr = formatDate(today)
  const signedSet = new Set(signedDates.value)

  const cells = []
  // 上月填充
  const prevMonthDays = new Date(year, month - 1, 0).getDate()
  for (let i = firstWeekday - 1; i >= 0; i--) {
    cells.push({ day: prevMonthDays - i, inMonth: false, signed: false, isToday: false })
  }
  // 当月
  for (let d = 1; d <= daysInMonth; d++) {
    const dateStr = formatDate(new Date(year, month - 1, d))
    cells.push({
      day: d,
      inMonth: true,
      signed: signedSet.has(dateStr),
      isToday: dateStr === todayStr
    })
  }
  // 下月填充至 42 格（6行）
  const remaining = 42 - cells.length
  for (let d = 1; d <= remaining; d++) {
    cells.push({ day: d, inMonth: false, signed: false, isToday: false })
  }
  return cells
})

const calendarMonthLabel = computed(() => `${calendarYear.value}年${calendarMonth.value}月`)
const monthSignedCount = computed(() => signedDates.value.length)

function formatDate(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

async function loadSignInCalendar() {
  calendarLoading.value = true
  try {
    const res = await getSignInCalendar(calendarYear.value, calendarMonth.value)
    if (res.code === 200 && res.data) {
      signedDates.value = (res.data.signedDates || []).map(d => {
        // 后端返回 LocalDate 序列化为数组 [y, m, d] 或字符串
        if (Array.isArray(d)) return `${d[0]}-${String(d[1]).padStart(2, '0')}-${String(d[2]).padStart(2, '0')}`
        return String(d)
      })
    }
  } catch (e) {
    // 静默失败
  } finally {
    calendarLoading.value = false
  }
}

function prevMonth() {
  if (calendarMonth.value === 1) {
    calendarMonth.value = 12
    calendarYear.value--
  } else {
    calendarMonth.value--
  }
  loadSignInCalendar()
}

function nextMonth() {
  if (calendarMonth.value === 12) {
    calendarMonth.value = 1
    calendarYear.value++
  } else {
    calendarMonth.value++
  }
  loadSignInCalendar()
}

/** 打开签到日历弹窗（清除签到结果，以“日历”模式展示） */
function openSignInCalendar() {
  signInResult.value = null
  signInDialogVisible.value = true
}

// 昵称编辑弹窗
const nicknameDialogVisible = ref(false)
const nicknameInput = ref('')
const nicknameSubmitting = ref(false)

// 修改密码弹窗
const passwordDialogVisible = ref(false)
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordSubmitting = ref(false)

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
  { name: 'ai', label: '树洞', icon: '✨', desc: '今夜星光灿烂' },
  { name: 'garden', label: '花园', icon: '🌷', desc: '种下你的花' },
  { name: 'notifications', label: '通知中心', icon: '🔔', desc: '查看消息' }
]

function goNav(name) {
  router.push({ name })
}

function openNicknameDialog() {
  nicknameInput.value = user.value.nickname || ''
  nicknameDialogVisible.value = true
}

function openPasswordDialog() {
  passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  passwordDialogVisible.value = true
}

async function confirmPassword() {
  const { oldPassword, newPassword, confirmPassword } = passwordForm.value
  if (!oldPassword) {
    ElMessage.warning('请输入原密码')
    return
  }
  if (!newPassword || newPassword.length < 6) {
    ElMessage.warning('新密码长度为 6-50 个字符')
    return
  }
  if (newPassword !== confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  passwordSubmitting.value = true
  try {
    await changePassword({ oldPassword, newPassword })
    passwordDialogVisible.value = false
    ElMessage.success('密码修改成功')
  } catch (e) {
    // 错误已拦截（4022原密码错误、4023一天限制等）
  } finally {
    passwordSubmitting.value = false
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
    ElMessage.success('昵称已更新')
  } catch (e) {
    // 错误已拦截
  } finally {
    nicknameSubmitting.value = false
  }
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
      // 签到成功后刷新当月日历
      loadSignInCalendar()
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
          <h2 class="user-nickname" @click="openNicknameDialog" title="点击修改昵称">
            {{ user.nickname || user.username || '旅人' }}
            <el-icon class="edit-icon"><Edit /></el-icon>
          </h2>
          <div class="user-tags">
            <el-tag size="small" type="info">用户名：{{ user.username || '-' }}</el-tag>
            <el-tag size="small" type="warning">匿名：{{ user.anonymousName || '-' }}</el-tag>
            <el-tag v-if="user.role === 'admin'" size="small" type="danger">管理员</el-tag>
            <el-button
              size="small"
              type="primary"
              plain
              :icon="Lock"
              @click="openPasswordDialog"
              class="change-password-btn"
            >修改密码</el-button>
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
            <el-button text size="small" @click="openSignInCalendar" class="calendar-link-btn">
              📅 签到日历
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 签到结果弹窗（含日历） -->
    <el-dialog
      v-model="signInDialogVisible"
      :title="signInResult ? '签到成功' : '签到日历'"
      width="420px"
      :show-close="true"
      center
      class="sign-in-dialog"
      @open="loadSignInCalendar"
    >
      <!-- 签到成功信息（仅刚签到时显示） -->
      <div v-if="signInResult" class="sign-in-result">
        <div class="sign-in-icon">🎉</div>
        <div class="sign-in-title">签到成功！</div>
        <div class="sign-in-reward">
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

      <!-- 签到日历 -->
      <div class="sign-in-calendar" v-loading="calendarLoading">
        <div class="calendar-header">
          <el-button text size="small" @click="prevMonth">◀</el-button>
          <span class="calendar-month-label">{{ calendarMonthLabel }}</span>
          <el-button text size="small" @click="nextMonth">▶</el-button>
          <span class="calendar-signed-count">当月签到 {{ monthSignedCount }} 天</span>
        </div>
        <div class="calendar-weekdays">
          <span v-for="w in ['日','一','二','三','四','五','六']" :key="w" class="weekday">{{ w }}</span>
        </div>
        <div class="calendar-grid">
          <div
            v-for="(cell, idx) in calendarCells"
            :key="idx"
            class="calendar-cell"
            :class="{
              'not-in-month': !cell.inMonth,
              'signed': cell.signed,
              'is-today': cell.isToday
            }"
          >
            <span class="cell-day">{{ cell.inMonth ? cell.day : '' }}</span>
            <span v-if="cell.signed" class="cell-dot">✓</span>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button type="primary" @click="signInDialogVisible = false">
          {{ signInResult ? '收下奖励' : '关闭' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 昵称编辑弹窗 -->
    <el-dialog
      v-model="nicknameDialogVisible"
      title="修改昵称"
      width="400px"
      center
    >
      <div class="nickname-edit-tip">
        给自己取一个温暖的昵称吧 ☘️
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
        <el-button @click="nicknameDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="nicknameSubmitting" @click="confirmNickname">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 修改密码弹窗 -->
    <el-dialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="420px"
      center
    >
      <div class="password-edit-tip">
        为了账号安全，一天只能修改一次密码 🔒
      </div>
      <el-form label-position="top" size="large">
        <el-form-item label="原密码">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            placeholder="请输入当前密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            placeholder="6-50 个字符"
            show-password
            maxlength="50"
          />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="再次输入新密码"
            show-password
            maxlength="50"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSubmitting" @click="confirmPassword">
          确认修改
        </el-button>
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
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.2s ease;
}
.user-nickname:hover {
  background: #fff5e6;
  color: #f5a623;
}
.user-nickname:hover .edit-icon {
  opacity: 1;
}
.edit-icon {
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.2s ease;
}
.nickname-edit-tip {
  text-align: center;
  color: #909399;
  font-size: 14px;
  margin-bottom: 20px;
}
.password-edit-tip {
  text-align: center;
  color: #909399;
  font-size: 14px;
  margin-bottom: 16px;
}
.user-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}
.change-password-btn {
  margin-left: auto;
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

/* ===== 签到日历 ===== */
.sign-in-calendar {
  margin-top: 12px;
  border-top: 1px solid #ebeef5;
  padding-top: 16px;
}
.calendar-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.calendar-month-label {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  min-width: 100px;
  text-align: center;
}
.calendar-signed-count {
  margin-left: auto;
  font-size: 12px;
  color: #67c23a;
  font-weight: 600;
}
.calendar-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin-bottom: 6px;
}
.calendar-weekdays .weekday {
  text-align: center;
  font-size: 12px;
  color: #909399;
  font-weight: 600;
  padding: 4px 0;
}
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 2px;
}
.calendar-cell {
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
  position: relative;
  background: #f9f9f9;
  min-height: 38px;
}
.calendar-cell.not-in-month {
  color: #c0c4cc;
  background: transparent;
}
.calendar-cell.is-today {
  border: 2px solid #409eff;
}
.calendar-cell.signed {
  background: #f0f9eb;
  color: #67c23a;
  font-weight: 600;
}
.cell-day {
  line-height: 1;
}
.cell-dot {
  font-size: 10px;
  color: #67c23a;
  margin-top: 2px;
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
