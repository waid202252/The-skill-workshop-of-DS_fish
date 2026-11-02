import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/services/http'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录 · 大肥鱼的工坊' },
  },
  {
    // 登录后的首页：负责「逛」——分类、热门、我的收藏
    path: '/discover',
    name: 'discover',
    component: () => import('@/views/DiscoverView.vue'),
    meta: { requiresAuth: true, title: '发现 · 大肥鱼的工坊' },
  },
  {
    // 技能广场：负责「搜」——搜索、分类筛选、安装
    path: '/skills',
    name: 'skills',
    component: () => import('@/views/SkillsView.vue'),
    meta: { requiresAuth: true, title: '技能广场 · 大肥鱼的工坊' },
  },
  {
    // 技能详情独立成页：可分享、可前进后退、README 能完整铺开
    path: '/skills/:id',
    name: 'skill-detail',
    component: () => import('@/views/SkillDetailView.vue'),
    meta: { requiresAuth: true, title: '技能详情 · 大肥鱼的工坊' },
  },
  {
    // 社区允许游客只读：浅度用户可以先看配方/指南，再决定是否登录
    path: '/community',
    name: 'community',
    component: () => import('@/views/CommunityView.vue'),
    meta: { title: '社区 · 大肥鱼的工坊' },
  },
  {
    path: '/community/:id',
    name: 'community-guide',
    component: () => import('@/views/CommunityDetailView.vue'),
    meta: { title: '社区内容 · 大肥鱼的工坊' },
  },
  {
    path: '/chat',
    name: 'chat',
    component: () => import('@/views/ChatView.vue'),
    meta: { requiresAuth: true, title: '聊天 · 大肥鱼的工坊' },
  },
  {
    // 产品主视图是「发现」+「技能广场」，聊天降为辅助入口
    path: '/',
    redirect: '/discover',
  },
  {
    // 兜底：地址不存在时明确告知，而不是静默重定向（静默跳转会让用户以为"点了没反应"）
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在 · 大肥鱼的工坊' },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  const requiresAuth = to.meta.requiresAuth === true

  // 未登录访问受保护页面 -> 登录页（记录来源便于登录后跳回）
  if (requiresAuth && !auth.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 已登录访问登录页 -> 首页（发现）
  if (to.path === '/login' && auth.isAuthenticated) {
    return { path: '/discover' }
  }

  // 已登录但内存中没有用户信息（刷新页面 / 从公开社区页进入）-> 拉取。
  // 公开页也要补齐用户信息，否则顶栏会显示“朋友”而不是昵称。
  if (to.path !== '/login' && auth.isAuthenticated && auth.user === null) {
    try {
      await auth.fetchMe()
    } catch (err) {
      // 只有「token 确实失效」（401/40100）才清登录态。
      // 网络抖动、后端重启、5xx 这些临时故障不该把用户踢出去。
      const isAuthError = err instanceof ApiError && (err.status === 401 || err.code === 40100)
      if (isAuthError) {
        auth.clearSession()
        if (requiresAuth) {
          return { path: '/login', query: { redirect: to.fullPath } }
        }
        // 公开页：清掉失效 token，按游客继续浏览
      }
    }
  }

  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : '大肥鱼的工坊'
  document.title = title
})
