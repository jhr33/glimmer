import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/announcements',
    name: 'announcements',
    component: () => import('@/views/announcement/AnnouncementListView.vue'),
    meta: { public: true, title: '公告' }
  },
  {
    path: '/driftBottle',
    name: 'driftBottle',
    component: () => import('@/views/driftBottle/DriftBottleView.vue'),
    meta: { requiresAuth: true, title: '漂流瓶' }
  },
  {
    path: '/letter',
    name: 'letter',
    component: () => import('@/views/letter/LetterListView.vue'),
    meta: { requiresAuth: true, title: '信件' }
  },
  {
    path: '/letter/:id',
    name: 'letterDetail',
    component: () => import('@/views/letter/LetterDetailView.vue'),
    meta: { requiresAuth: true, title: '信件详情' }
  },
  {
    path: '/campfire',
    name: 'campfire',
    component: () => import('@/views/campfire/CampfireView.vue'),
    meta: { requiresAuth: true, title: '小篝火' }
  },
  {
    path: '/ai',
    name: 'ai',
    component: () => import('@/views/ai/AiChatView.vue'),
    meta: { requiresAuth: true, title: 'AI 对话' }
  },
  {
    path: '/garden',
    name: 'garden',
    component: () => import('@/views/garden/GardenView.vue'),
    meta: { requiresAuth: true, title: '萤火花园' }
  },
  {
    path: '/notifications',
    name: 'notifications',
    component: () => import('@/views/notification/NotificationListView.vue'),
    meta: { requiresAuth: true, title: '通知中心' }
  },
  {
    path: '/feedback',
    name: 'feedback',
    component: () => import('@/views/feedback/FeedbackView.vue'),
    meta: { requiresAuth: true, title: '意见反馈' }
  },
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayoutView.vue'),
    redirect: '/admin/reports',
    meta: { requiresAuth: true, requiresAdmin: true, title: '管理后台' },
    children: [
      {
        path: 'reports',
        name: 'adminReports',
        component: () => import('@/views/admin/AdminReportView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, title: '举报管理' }
      },
      {
        path: 'feedbacks',
        name: 'adminFeedbacks',
        component: () => import('@/views/admin/AdminFeedbackView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, title: '意见信管理' }
      },
      {
        path: 'announcements',
        name: 'adminAnnouncements',
        component: () => import('@/views/admin/AdminAnnouncementView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, title: '公告管理' }
      },
      {
        path: 'users',
        name: 'adminUsers',
        component: () => import('@/views/admin/AdminUserView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, title: '用户管理' }
      }
    ]
  },
  // 404 重定向到首页
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const isLoggedIn = userStore.isLoggedIn

  // 公开路由（如公告列表，游客可访问）
  if (to.meta.public) {
    next()
    return
  }

  if (to.meta.requiresAuth === false) {
    // 已登录用户访问登录/注册页时跳转首页
    if (isLoggedIn && (to.name === 'login' || to.name === 'register')) {
      next({ name: 'home' })
      return
    }
    next()
    return
  }

  // 受保护路由
  if (!isLoggedIn) {
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }

  // 管理员路由：非 admin 角色访问 /admin/** 时跳转首页并提示
  if (to.meta.requiresAdmin && !userStore.isAdmin) {
    ElMessage.error('无权限')
    next({ name: 'home' })
    return
  }

  next()
})

export default router
