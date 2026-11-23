import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true, title: '登录' } },
    {
      path: '/',
      component: () => import('@/components/layout/AppShell.vue'),
      children: [
        { path: '', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '复习仪表盘' } },
        { path: 'bank', name: 'bank', component: () => import('@/views/BankView.vue'), meta: { title: '题库目录' } },
        { path: 'read/:slug?', name: 'reader', component: () => import('@/views/ReaderView.vue'), meta: { title: '沉浸通读' } },
      ],
    },
    // 闪卡背诵为沉浸式全屏页面，不包裹侧边栏
    { path: '/study', name: 'study', component: () => import('@/views/StudyView.vue'), meta: { title: '闪卡背诵' } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior(to, _from, saved) {
    if (saved) return saved
    if (to.hash) return { el: to.hash, top: 80, behavior: 'smooth' }
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isLoggedIn) return { name: 'dashboard' }
})

router.afterEach((to) => {
  document.title = `${(to.meta.title as string) ?? ''} · Bagu Lab`
})

export default router
