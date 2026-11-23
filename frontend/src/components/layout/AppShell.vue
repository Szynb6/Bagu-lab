<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BookMarked, BookOpen, ChevronsLeft, ChevronsRight, Home, Layers, LogOut, Moon,
  Star, Sun, TriangleAlert, User, Zap,
} from 'lucide-vue-next'
import { useUiStore } from '@/stores/ui'
import { useAuthStore } from '@/stores/auth'
import { knowledgeApi } from '@/api'
import type { ModuleMeta } from '@/types'

const ui = useUiStore()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const modules = ref<ModuleMeta[]>([])
const menuOpen = ref(false)

onMounted(async () => {
  modules.value = await knowledgeApi.modules()
})

const lastSlug = computed(() => localStorage.getItem('bagu:lastSlug') || '01-jvm')

const nav = computed(() => [
  { name: '复习仪表盘', icon: Home, to: { name: 'dashboard' }, active: route.name === 'dashboard' },
  { name: '题库目录', icon: Layers, to: { name: 'bank' }, active: route.name === 'bank' && route.query.status !== 'starred' },
  { name: '闪卡背诵', icon: Zap, to: { name: 'study', query: { mode: 'today' } }, active: false },
  { name: '沉浸通读', icon: BookOpen, to: { name: 'reader', params: { slug: lastSlug.value } }, active: route.name === 'reader' },
  { name: '错题本', icon: TriangleAlert, to: { name: 'study', query: { mode: 'mistakes' } }, active: false },
  { name: '我的收藏', icon: Star, to: { name: 'bank', query: { status: 'starred' } }, active: route.name === 'bank' && route.query.status === 'starred' },
])

const tabs = computed(() => [
  { name: '首页', icon: Home, to: { name: 'dashboard' }, active: route.name === 'dashboard' },
  { name: '题库', icon: Layers, to: { name: 'bank' }, active: route.name === 'bank' },
  { name: '背诵', icon: Zap, to: { name: 'study', query: { mode: 'today' } }, active: false, primary: true },
  { name: '通读', icon: BookOpen, to: { name: 'reader', params: { slug: lastSlug.value } }, active: route.name === 'reader' },
])

async function logout() {
  menuOpen.value = false
  await auth.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="min-h-screen lg:flex">
    <!-- 桌面侧边栏 -->
    <aside
      class="sticky top-0 hidden h-screen shrink-0 flex-col border-r border-slate-200 bg-white/80 backdrop-blur transition-[width] duration-200 lg:flex dark:border-slate-800 dark:bg-slate-900/60"
      :class="ui.sidebarCollapsed ? 'w-[72px]' : 'w-[248px]'"
    >
      <div class="flex h-16 items-center gap-2.5 px-4">
        <img src="/favicon.svg" class="h-8 w-8" alt="logo" />
        <div v-if="!ui.sidebarCollapsed" class="leading-tight">
          <p class="font-semibold tracking-tight">Bagu Lab</p>
          <p class="text-[11px] text-slate-400">Java 八股记忆引擎</p>
        </div>
      </div>

      <nav class="flex-1 space-y-1 overflow-y-auto px-3 py-2">
        <RouterLink
          v-for="item in nav"
          :key="item.name"
          :to="item.to"
          class="flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition"
          :class="item.active
            ? 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-100'
            : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'"
          :title="item.name"
        >
          <component :is="item.icon" class="h-[18px] w-[18px] shrink-0" />
          <span v-if="!ui.sidebarCollapsed">{{ item.name }}</span>
        </RouterLink>

        <template v-if="!ui.sidebarCollapsed && modules.length">
          <p class="px-3 pb-1 pt-5 text-[11px] font-medium uppercase tracking-wider text-slate-400">九大模块</p>
          <RouterLink
            v-for="m in modules"
            :key="m.slug"
            :to="{ name: 'reader', params: { slug: m.slug } }"
            class="flex items-center gap-2.5 rounded-lg px-3 py-1.5 text-[13px] transition"
            :class="route.params.slug === m.slug
              ? 'font-medium text-brand-700 dark:text-brand-100'
              : 'text-slate-500 hover:bg-slate-100 hover:text-slate-800 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-200'"
          >
            <span class="w-5 text-right font-mono text-[11px] text-slate-400">{{ String(m.order).padStart(2, '0') }}</span>
            <span class="truncate">{{ m.title }}</span>
            <span class="ml-auto text-[11px] text-slate-400">{{ m.questionCount }}</span>
          </RouterLink>
        </template>
      </nav>

      <div class="border-t border-slate-200 p-3 dark:border-slate-800">
        <button
          class="flex w-full items-center gap-3 rounded-xl px-3 py-2 text-sm text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800"
          @click="ui.toggleSidebar()"
        >
          <component :is="ui.sidebarCollapsed ? ChevronsRight : ChevronsLeft" class="h-[18px] w-[18px]" />
          <span v-if="!ui.sidebarCollapsed">收起侧边栏</span>
        </button>
      </div>
    </aside>

    <div class="min-w-0 flex-1">
      <!-- 顶栏 -->
      <header class="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-slate-200 bg-slate-50/80 px-4 backdrop-blur lg:h-16 lg:px-8 dark:border-slate-800 dark:bg-ink-950/80">
        <img src="/favicon.svg" class="h-7 w-7 lg:hidden" alt="logo" />
        <h1 class="text-[15px] font-semibold lg:text-base">{{ route.meta.title }}</h1>

        <button class="ml-auto rounded-xl p-2 text-slate-500 hover:bg-slate-200/60 dark:text-slate-400 dark:hover:bg-slate-800" :title="ui.dark ? '切换浅色' : '切换深色'" @click="ui.toggleTheme()">
          <component :is="ui.dark ? Sun : Moon" class="h-5 w-5" />
        </button>

        <div class="relative">
          <button class="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-brand-500 to-indigo-600 text-sm font-semibold text-white" @click="menuOpen = !menuOpen">
            {{ auth.user?.nickname?.slice(0, 1).toUpperCase() ?? 'U' }}
          </button>
          <Transition name="fade">
            <div v-if="menuOpen" class="fixed inset-0 z-40" @click="menuOpen = false" />
          </Transition>
          <Transition name="slide-up">
            <div v-if="menuOpen" class="card absolute right-0 z-50 mt-2 w-60 p-2 text-sm">
              <div class="flex items-center gap-2 px-3 py-2">
                <User class="h-4 w-4 text-slate-400" />
                <span class="font-medium">{{ auth.user?.nickname }}</span>
                <span class="ml-auto text-xs text-slate-400">每日 {{ auth.user?.dailyGoal }} 题</span>
              </div>
              <div class="my-1 h-px bg-slate-100 dark:bg-slate-800" />
              <RouterLink :to="{ name: 'bank', query: { status: 'noted' } }" class="flex items-center gap-2 rounded-lg px-3 py-2 hover:bg-slate-100 dark:hover:bg-slate-800" @click="menuOpen = false">
                <BookMarked class="h-4 w-4" /> 我的助记笔记
              </RouterLink>
              <div class="my-1 h-px bg-slate-100 dark:bg-slate-800" />
              <button class="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-500/10" @click="logout">
                <LogOut class="h-4 w-4" /> 退出登录
              </button>
            </div>
          </Transition>
        </div>
      </header>

      <main class="mx-auto w-full max-w-[1280px] px-4 pb-28 pt-5 lg:px-8 lg:pb-12 lg:pt-8">
        <RouterView />
      </main>
    </div>

    <!-- 移动端底部 Tab -->
    <nav class="pb-safe fixed inset-x-0 bottom-0 z-40 border-t border-slate-200 bg-white/90 backdrop-blur lg:hidden dark:border-slate-800 dark:bg-slate-900/90">
      <div class="grid h-16 grid-cols-4">
        <RouterLink
          v-for="t in tabs"
          :key="t.name"
          :to="t.to"
          class="flex flex-col items-center justify-center gap-1 text-[11px]"
          :class="t.active ? 'text-brand-600 dark:text-brand-500' : 'text-slate-500 dark:text-slate-400'"
        >
          <span v-if="t.primary" class="-mt-1 flex h-9 w-9 items-center justify-center rounded-full bg-brand-600 text-white shadow-lg shadow-brand-600/30">
            <component :is="t.icon" class="h-5 w-5" />
          </span>
          <component :is="t.icon" v-else class="h-5 w-5" />
          {{ t.name }}
        </RouterLink>
      </div>
    </nav>
  </div>
</template>
