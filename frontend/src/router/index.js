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
    redirect: '/driftBottle'
  },
  {
    path: '/home',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { requiresAuth: true, title: '首页' }
  },
  {
    path: '/announcements',
    name: 'announcements',
    component: () => import('@/views/announcement/AnnouncementListView.vue'),
    meta: { public: true, title: '公告' }
  },
  {
    path: '/articles',
    name: 'articles',
    component: () => import('@/views/article/ArticleView.vue'),
    meta: { public: true, title: '萤火交流会' }
  },
  {
    // 注意：静态段 /mine、/favorites、/following 必须在 /:id 之前声明
    path: '/articles/mine',
    name: 'myArticles',
    component: () => import('@/views/article/MyArticlesView.vue'),
    meta: { requiresAuth: true, title: '我的随记' }
  },
  {
    path: '/articles/favorites',
    name: 'myFavorites',
    component: () => import('@/views/article/MyFavoritesView.vue'),
    meta: { requiresAuth: true, title: '我的收藏' }
  },
  {
    path: '/articles/following',
    name: 'myFollowing',
    component: () => import('@/views/article/MyFollowingView.vue'),
    meta: { requiresAuth: true, title: '我的关注' }
  },
  {
    path: '/articles/:id',
    name: 'articleDetail',
    component: () => import('@/views/article/ArticleDetailView.vue'),
    meta: { public: true, title: '文章详情' }
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
    meta: { requiresAuth: true, title: '树洞' }
  },
  {
    path: '/garden',
    name: 'garden',
    component: () => import('@/views/garden/GardenView.vue'),
    meta: { requiresAuth: true, title: '萤火花园' }
  },
  {
    path: '/garden/detail',
    name: 'gardenDetail',
    component: () => import('@/views/garden/GardenDetailView.vue'),
    meta: { requiresAuth: true, title: '花园详情' }
  },
  {
    // 小游戏板块：游客可进入试玩，提交成绩需登录（接口层兜底）
    path: '/game',
    name: 'gameCenter',
    component: () => import('@/views/game/GameCenterView.vue'),
    meta: { public: true, title: '小游戏' }
  },
  {
    path: '/game/snake',
    name: 'snakeGame',
    component: () => import('@/views/game/SnakeGameView.vue'),
    meta: { public: true, title: '贪吃蛇' }
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
    path: '/my',
    name: 'my',
    component: () => import('@/views/MyView.vue'),
    meta: { requiresAuth: true, title: '我的' }
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
      },
      {
        path: 'articles',
        name: 'adminArticles',
        component: () => import('@/views/admin/AdminArticleView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, title: '文章审核' }
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
router.beforeEach(async (to, from, next) => {
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
      next('/home')
      return
    }
    next()
    return
  }

  // 已登录但未绑定手机号：强制跳转 /my 完成绑定，唯一出口是绑定或退出登录
  // 例外：/my 自身允许进入（强制绑定弹窗在 MyView 内打开），登录/注册页不拦截
  if (isLoggedIn && to.name !== 'my' && !userStore.userInfo?.phone) {
    ElMessage.warning('请先绑定手机号再继续操作')
    next({ name: 'my' })
    return
  }

  // 游客模式：除管理员路由外，所有其他页面允许游客进入浏览
  // —— 按钮级别的写操作会在各页面内通过 isLoggedIn 禁用 + 401 拦截兜底
  if (to.meta.requiresAdmin) {
    if (!isLoggedIn) {
      next({ name: 'login', query: { redirect: to.fullPath } })
      return
    }
    // 管理员路由需要校验角色
    if (isLoggedIn && !userStore.isAdmin) {
      ElMessage.error('无权限')
      next('/home')
      return
    }
  }

  next()
})

export default router
