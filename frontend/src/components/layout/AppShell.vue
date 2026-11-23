<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BookMarked, BookOpen, ChevronsLeft, ChevronsRight, Home, Layers, LogOut, Moon,
  Star, Sun, TriangleAlert, User, Zap,
} from 'lucide-vue-next'
import { useUiStore } from '@/stores/ui'
import { useAuthStore } from '@/stores/auth'

const ui = useUiStore()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)

onMounted(() => {
  ui.loadModules().catch(() => {})
  window.addEventListener('keydown', onGlobalKey)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onGlobalKey)
})

function onGlobalKey(e: KeyboardEvent) {
  if (e.key === 'Escape' && menuOpen.value) {
    menuOpen.value = false
  }
}

const nav = computed(() => [
  { name: '复习仪表盘', icon: Home, to: { name: 'dashboard' }, active: route.name === 'dashboard' },
  {
    name: '题库目录',
    icon: Layers,
    to: { name: 'bank' },
    active: route.name === 'bank' && route.query.status !== 'starred' && route.query.status !== 'noted',
  },
  { name: '闪卡背诵', icon: Zap, to: { name: 'study', query: { mode: 'today' } }, active: false },
  { name: '沉浸通读', icon: BookOpen, to: { name: 'reader', params: { slug: ui.lastSlug } }, active: route.name === 'reader' },
  { name: '错题本', icon: TriangleAlert, to: { name: 'study', query: { mode: 'mistakes' } }, active: false },
  {
    name: '我的收藏',
    icon: Star,
    to: { name: 'bank', query: { status: 'starred' } },
    active: route.name === 'bank' && route.query.status === 'starred',
  },
])

const tabs = computed(() => [
  { name: '首页', icon: Home, to: { name: 'dashboard' }, active: route.name === 'dashboard' },
  { name: '题库', icon: Layers, to: { name: 'bank' }, active: route.name === 'bank' },
  { name: '背诵', icon: Zap, to: { name: 'study', query: { mode: 'today' } }, active: false, primary: true },
  { name: '错题', icon: TriangleAlert, to: { name: 'study', query: { mode: 'mistakes' } }, active: false },
  { name: '通读', icon: BookOpen, to: { name: 'reader', params: { slug: ui.lastSlug } }, active: route.name === 'reader' },
])

async function logout() {
  menuOpen.value = false
  await auth.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="min-h-dvh lg:flex">
    <a
      href="#main-content"
      class="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-50 focus:rounded-lg focus:bg-[#C84B31] focus:px-3 focus:py-2 focus:text-sm focus:font-medium focus:text-white"
    >
      跳转到主内容
    </a>

    <!-- 桌面侧边栏（温暖档案纸边栏） -->
    <aside
      class="sticky top-0 hidden h-screen shrink-0 flex-col border-r border-[var(--border-default)] bg-[var(--bg-sidebar)] transition-[width] duration-200 lg:flex"
      :class="ui.sidebarCollapsed ? 'w-[72px]' : 'w-[248px]'"
    >
      <div class="flex h-16 items-center gap-2.5 border-b border-[var(--border-default)] px-4">
        <img src="/favicon.svg" class="h-8 w-8 shrink-0" alt="Bagu Lab" />
        <div v-if="!ui.sidebarCollapsed" class="min-w-0 leading-tight">
          <p class="font-semibold tracking-tight text-[var(--text-primary)]">Bagu Lab</p>
          <p class="truncate text-[11px] text-[var(--text-muted)]">Java 工程记忆手稿</p>
        </div>
      </div>

      <nav class="flex-1 space-y-1 overflow-y-auto px-3 py-3" aria-label="主导航">
        <RouterLink
          v-for="item in nav"
          :key="item.name"
          :to="item.to"
          class="flex items-center gap-3 rounded-lg border px-3 py-2 text-sm transition-colors focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand-500"
          :class="
            item.active
              ? 'border-[var(--border-strong)] bg-[var(--bg-surface)] font-semibold text-[#C84B31] dark:text-[#E06246]'
              : 'border-transparent font-medium text-[var(--text-secondary)] hover:bg-[var(--bg-surface)]/70 hover:text-[var(--text-primary)]'
          "
          :title="item.name"
        >
          <component :is="item.icon" class="h-[17px] w-[17px] shrink-0" />
          <span v-if="!ui.sidebarCollapsed" class="truncate">{{ item.name }}</span>
        </RouterLink>

        <template v-if="!ui.sidebarCollapsed && ui.modules.length">
          <p class="px-3 pb-1 pt-5 text-xs font-medium text-[var(--text-muted)]">知识卷目</p>
          <RouterLink
            v-for="m in ui.modules"
            :key="m.slug"
            :to="{ name: 'reader', params: { slug: m.slug } }"
            class="flex items-center gap-2 rounded-lg border px-2.5 py-1.5 text-[13px] transition-colors focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand-500"
            :class="
              route.params.slug === m.slug
                ? 'border-[var(--border-default)] bg-[var(--bg-surface)] font-medium text-[#C84B31] dark:text-[#E06246]'
                : 'border-transparent text-[var(--text-secondary)] hover:bg-[var(--bg-surface)]/60 hover:text-[var(--text-primary)]'
            "
          >
            <span class="w-5 shrink-0 font-mono text-[11px] tabular-nums text-[var(--text-muted)]">{{ String(m.order).padStart(2, '0') }}</span>
            <span class="truncate">{{ m.title }}</span>
            <span class="ml-auto font-mono text-[11px] tabular-nums text-[var(--text-muted)]">{{ m.questionCount }}</span>
          </RouterLink>
        </template>
      </nav>

      <div class="border-t border-[var(--border-default)] p-3">
        <button
          type="button"
          class="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-surface)]/70 hover:text-[var(--text-primary)] focus-visible:outline-2 focus-visible:outline-brand-500"
          :aria-label="ui.sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
          @click="ui.toggleSidebar()"
        >
          <component :is="ui.sidebarCollapsed ? ChevronsRight : ChevronsLeft" class="h-[18px] w-[18px] shrink-0" />
          <span v-if="!ui.sidebarCollapsed">收起侧边栏</span>
        </button>
      </div>
    </aside>

    <div class="min-w-0 flex-1">
      <!-- 顶栏 -->
      <header class="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-[var(--border-default)] bg-[var(--bg-canvas)]/92 px-4 backdrop-blur-sm lg:h-16 lg:px-8">
        <img src="/favicon.svg" class="h-7 w-7 lg:hidden" alt="Bagu Lab" />
        <h1 class="text-[15px] font-semibold tracking-tight text-[var(--text-primary)] lg:text-base">{{ route.meta.title }}</h1>

        <button
          type="button"
          class="ml-auto flex h-9 w-9 items-center justify-center rounded-lg border border-transparent text-[var(--text-secondary)] transition-colors hover:border-[var(--border-default)] hover:bg-[var(--bg-surface)] hover:text-[var(--text-primary)] focus-visible:outline-2 focus-visible:outline-brand-500"
          :title="ui.dark ? '切换浅色纸面' : '切换深色石墨'"
          :aria-label="ui.dark ? '切换浅色模式' : '切换深色模式'"
          @click="ui.toggleTheme()"
        >
          <component :is="ui.dark ? Sun : Moon" class="h-[18px] w-[18px]" />
        </button>

        <div class="relative">
          <button
            type="button"
            class="flex h-9 w-9 items-center justify-center rounded-lg border border-[var(--border-strong)] bg-[var(--bg-surface)] font-mono text-sm font-semibold text-[#C84B31] transition-colors hover:border-[#C84B31] focus-visible:outline-2 focus-visible:outline-brand-500 dark:text-[#E06246]"
            aria-label="账号与笔记菜单"
            :aria-expanded="menuOpen"
            @click="menuOpen = !menuOpen"
          >
            {{ auth.user?.nickname?.slice(0, 1).toUpperCase() ?? 'U' }}
          </button>

          <Transition name="fade">
            <div v-if="menuOpen" class="fixed inset-0 z-40" @click="menuOpen = false" />
          </Transition>

          <Transition name="slide-up">
            <div v-if="menuOpen" class="card absolute right-0 z-50 mt-2 w-60 p-2 text-sm shadow-sm">
              <div class="flex items-center gap-2 px-3 py-2">
                <User class="h-4 w-4 text-[var(--text-muted)]" />
                <span class="font-medium text-[var(--text-primary)]">{{ auth.user?.nickname }}</span>
                <span class="ml-auto font-mono text-xs tabular-nums text-[var(--text-muted)]">每日 {{ auth.user?.dailyGoal }} 题</span>
              </div>
              <div class="my-1 h-px bg-[var(--border-default)]" />
              <RouterLink
                :to="{ name: 'bank', query: { status: 'noted' } }"
                class="flex items-center gap-2 rounded-lg px-3 py-2 text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)]"
                @click="menuOpen = false"
              >
                <BookMarked class="h-4 w-4" /> 我的助记笔记
              </RouterLink>
              <div class="my-1 h-px bg-[var(--border-default)]" />
              <button
                type="button"
                class="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-[#96311D] transition-colors hover:bg-[#FBF0ED] dark:text-[#EFC0B3] dark:hover:bg-[#2E1B16]"
                @click="logout"
              >
                <LogOut class="h-4 w-4" /> 退出登录
              </button>
            </div>
          </Transition>
        </div>
      </header>

      <main id="main-content" class="mx-auto w-full max-w-[1240px] px-4 pb-28 pt-5 lg:px-8 lg:pb-14 lg:pt-7">
        <RouterView />
      </main>
    </div>

    <!-- 移动端底部 5 栏导航 -->
    <nav
      class="pb-safe fixed inset-x-0 bottom-0 z-40 border-t border-[var(--border-default)] bg-[var(--bg-surface)]/95 backdrop-blur-sm lg:hidden"
      aria-label="移动端底部导航"
    >
      <div class="grid h-16 grid-cols-5">
        <RouterLink
          v-for="t in tabs"
          :key="t.name"
          :to="t.to"
          class="flex flex-col items-center justify-center gap-1 text-[11px] font-medium transition-colors"
          :class="t.active ? 'text-[#C84B31] dark:text-[#E06246]' : 'text-[var(--text-muted)]'"
        >
          <span
            v-if="t.primary"
            class="flex h-8 w-8 items-center justify-center rounded-lg bg-[#C84B31] text-white"
          >
            <component :is="t.icon" class="h-4 w-4" />
          </span>
          <component :is="t.icon" v-else class="h-[18px] w-[18px]" />
          <span>{{ t.name }}</span>
        </RouterLink>
      </div>
    </nav>
  </div>
</template>
