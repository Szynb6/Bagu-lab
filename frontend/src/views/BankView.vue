<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BookOpen, ChevronDown, ChevronLeft, ChevronRight, Code2, FileText, Filter, Image, Layers, Loader2, Play, Search, Star, Table2, X,
} from 'lucide-vue-next'
import { knowledgeApi, studyApi } from '@/api'
import { useUiStore } from '@/stores/ui'
import PriorityBadge from '@/components/common/PriorityBadge.vue'
import StatusDot from '@/components/common/StatusDot.vue'
import ModuleIcon from '@/components/common/ModuleIcon.vue'
import { dueLabel } from '@/utils/format'
import type { ModuleMeta, Priority, QuestionFilter, QuestionItem } from '@/types'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const modules = ref<ModuleMeta[]>([])
const questions = ref<QuestionItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const totalPages = ref(0)
const loading = ref(true)
const launchingStudy = ref(false)

const selectedSlug = ref<string>((route.query.slug as string) || '')
const selectedChapter = ref<number | null>(route.query.chapter ? Number(route.query.chapter) : null)
const expandedSlug = ref<string>((route.query.slug as string) || '01-jvm')
const priorities = ref<Priority[]>([])
const status = ref<NonNullable<QuestionFilter['status']>>(
  ((route.query.status as QuestionFilter['status']) || 'all'),
)
const keyword = ref('')

const statusOptions: { value: NonNullable<QuestionFilter['status']>; label: string }[] = [
  { value: 'all', label: '全部状态' },
  { value: 'new', label: '未背' },
  { value: 'weak', label: '待巩固' },
  { value: 'reviewing', label: '复习中' },
  { value: 'mastered', label: '已掌握' },
  { value: 'starred', label: '⭐ 已收藏' },
  { value: 'noted', label: '📝 有助记' },
  { value: 'animated', label: '🎬 有动画' },
]

const priorityOptions: { value: Priority; label: string; active: string }[] = [
  { value: 'P0', label: 'P0 必背', active: 'border-red-300 bg-red-50 text-red-600 dark:border-red-500/40 dark:bg-red-500/15 dark:text-red-300' },
  { value: 'P1', label: 'P1 高频', active: 'border-blue-300 bg-blue-50 text-blue-600 dark:border-blue-500/40 dark:bg-blue-500/15 dark:text-blue-300' },
  { value: 'P2', label: 'P2 了解', active: 'border-slate-400 bg-slate-100 text-slate-700 dark:border-slate-500 dark:bg-slate-700 dark:text-slate-200' },
]

const currentModule = computed(() => modules.value.find((m) => m.slug === selectedSlug.value) ?? null)
const moduleTitleMap = computed(() => Object.fromEntries(modules.value.map((m) => [m.slug, m.title])))

function buildFilter(): QuestionFilter {
  return {
    slug: selectedSlug.value || undefined,
    chapterId: selectedChapter.value ?? undefined,
    priorities: priorities.value.length ? priorities.value : undefined,
    status: status.value,
    keyword: keyword.value.trim() || undefined,
    page: page.value,
    size: pageSize,
  }
}

let fetchSeq = 0
async function fetchQuestions() {
  const my = ++fetchSeq
  loading.value = true
  try {
    const res = await knowledgeApi.questions(buildFilter())
    if (my === fetchSeq) {
      questions.value = res.records
      total.value = res.total
      totalPages.value = res.totalPages
    }
  } finally {
    if (my === fetchSeq) loading.value = false
  }
}

onMounted(async () => {
  modules.value = await knowledgeApi.modules()
  await fetchQuestions()
})

// 监听侧边栏跳转到 /bank?status=starred 或 noted
watch(
  () => route.query,
  (q) => {
    const nextStatus = ((q.status as QuestionFilter['status']) || 'all')
    if (nextStatus !== status.value) status.value = nextStatus
    const nextSlug = (q.slug as string) || ''
    if (nextSlug !== selectedSlug.value) {
      selectedSlug.value = nextSlug
      selectedChapter.value = q.chapter ? Number(q.chapter) : null
    }
  },
)

let kwTimer: number | undefined
watch([selectedSlug, selectedChapter, priorities, status], () => {
  page.value = 1
  fetchQuestions()
}, { deep: true })

watch(keyword, () => {
  clearTimeout(kwTimer)
  kwTimer = window.setTimeout(() => {
    page.value = 1
    fetchQuestions()
  }, 220)
})

function goPage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value || nextPage === page.value) return
  page.value = nextPage
  fetchQuestions()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const pageNumbers = computed(() => {
  const tp = totalPages.value
  const cur = page.value
  if (tp <= 7) return Array.from({ length: tp }, (_, i) => i + 1)
  const pages: (number | '...')[] = [1]
  if (cur > 3) pages.push('...')
  for (let i = Math.max(2, cur - 1); i <= Math.min(tp - 1, cur + 1); i++) pages.push(i)
  if (cur < tp - 2) pages.push('...')
  pages.push(tp)
  return pages
})

function selectModule(slug: string) {
  selectedSlug.value = slug
  selectedChapter.value = null
  if (slug) expandedSlug.value = slug
}

function selectChapter(slug: string, chId: number) {
  selectedSlug.value = slug
  selectedChapter.value = chId
  expandedSlug.value = slug
}

function togglePriority(p: Priority) {
  const i = priorities.value.indexOf(p)
  if (i >= 0) priorities.value.splice(i, 1)
  else priorities.value.push(p)
}

function resetFilters() {
  priorities.value = []
  status.value = 'all'
  keyword.value = ''
}

const hasActiveFilter = computed(() => priorities.value.length > 0 || status.value !== 'all' || !!keyword.value.trim())

async function startFilteredStudy() {
  if (!total.value || launchingStudy.value) return
  launchingStudy.value = true
  try {
    const ids = await knowledgeApi.questionIds(buildFilter())
    if (!ids.length) return
    sessionStorage.setItem('bagu:customQueue', JSON.stringify(ids))
    const label = currentModule.value ? `${currentModule.value.title} 筛选专练` : '自定义筛选专练'
    sessionStorage.setItem('bagu:customLabel', label)
    router.push({ name: 'study', query: { mode: 'custom' } })
  } catch {
    ui.showToast('获取刷题队列失败，请重试')
  } finally {
    launchingStudy.value = false
  }
}

async function onToggleStar(q: QuestionItem, e: Event) {
  e.stopPropagation()
  q.progress.isStarred = !q.progress.isStarred
  try {
    q.progress.isStarred = await studyApi.toggleStar(q.id)
  } catch {
    q.progress.isStarred = !q.progress.isStarred
  }
}
</script>

<template>
  <div class="flex flex-col gap-5 lg:flex-row lg:items-start">
    <!-- 左侧模块 + 篇目树（桌面端） -->
    <aside class="card hidden w-[268px] shrink-0 overflow-hidden lg:sticky lg:top-24 lg:block">
      <div class="border-b border-slate-100 px-4 py-3 dark:border-slate-800">
        <p class="text-xs font-semibold uppercase tracking-wider text-slate-400">知识目录</p>
      </div>
      <div class="max-h-[calc(100vh-11rem)] space-y-0.5 overflow-y-auto p-2">
        <button
          class="flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-left text-sm font-medium transition"
          :class="!selectedSlug ? 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-100' : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'"
          @click="selectModule('')"
        >
          <Layers class="h-4 w-4 shrink-0" />
          <span>全部模块</span>
          <span class="ml-auto text-xs tabular-nums text-slate-400">{{ modules.reduce((s, m) => s + m.questionCount, 0) }}</span>
        </button>

        <div v-for="m in modules" :key="m.slug" class="pt-0.5">
          <div
            class="flex items-center gap-1 rounded-xl px-2 py-1.5 transition"
            :class="selectedSlug === m.slug && selectedChapter === null ? 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-100' : 'text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'"
          >
            <button
              class="rounded p-0.5 text-slate-400 hover:text-slate-600"
              @click="expandedSlug = expandedSlug === m.slug ? '' : m.slug"
            >
              <component :is="expandedSlug === m.slug ? ChevronDown : ChevronRight" class="h-3.5 w-3.5" />
            </button>
            <button class="flex min-w-0 flex-1 items-center gap-2 text-left text-[13.5px] font-medium" @click="selectModule(m.slug)">
              <span class="font-mono text-[11px] text-slate-400">{{ String(m.order).padStart(2, '0') }}</span>
              <span class="truncate">{{ m.title }}</span>
              <span class="ml-auto text-[11px] tabular-nums text-slate-400">{{ m.questionCount }}</span>
            </button>
          </div>

          <div v-if="expandedSlug === m.slug" class="mt-0.5 space-y-0.5 pl-7 pr-1">
            <button
              v-for="ch in m.chapters"
              :key="ch.id"
              class="flex w-full items-center gap-2 rounded-lg px-2.5 py-1.5 text-left text-xs transition"
              :class="selectedChapter === ch.id
                ? 'bg-brand-50 font-medium text-brand-700 dark:bg-brand-500/15 dark:text-brand-100'
                : 'text-slate-500 hover:bg-slate-100 hover:text-slate-800 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-200'"
              @click="selectChapter(m.slug, ch.id)"
            >
              <span class="truncate">{{ ch.title.replace(/^第[一二三四五六七八九十]+(?:篇|部分)[：:]\s*/, '') }}</span>
              <span class="ml-auto shrink-0 tabular-nums text-[11px] text-slate-400">{{ ch.questionCount }}</span>
            </button>
          </div>
        </div>
      </div>
    </aside>

    <!-- 右侧主内容区 -->
    <div class="min-w-0 flex-1 space-y-4">
      <!-- 移动端横向模块选择器 -->
      <div class="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 lg:hidden">
        <button
          class="shrink-0 rounded-full border px-3.5 py-1.5 text-xs font-medium transition"
          :class="!selectedSlug ? 'border-brand-600 bg-brand-600 text-white' : 'border-slate-200 bg-white text-slate-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300'"
          @click="selectModule('')"
        >
          全部
        </button>
        <button
          v-for="m in modules"
          :key="m.slug"
          class="shrink-0 rounded-full border px-3.5 py-1.5 text-xs font-medium transition"
          :class="selectedSlug === m.slug ? 'border-brand-600 bg-brand-600 text-white' : 'border-slate-200 bg-white text-slate-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300'"
          @click="selectModule(m.slug)"
        >
          {{ m.title }}
        </button>
      </div>

      <!-- 筛选控制栏 -->
      <div class="card space-y-3.5 p-4 sm:p-5">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div class="flex items-center gap-3">
            <ModuleIcon v-if="currentModule" :icon="currentModule.icon" />
            <div>
              <h2 class="text-base font-semibold">
                {{ currentModule ? currentModule.fullTitle : '全部八股题库' }}
              </h2>
              <p class="text-xs text-slate-400">
                当前匹配 <strong class="tabular-nums text-slate-700 dark:text-slate-200">{{ total }}</strong> 题
                <template v-if="currentModule && selectedChapter !== null">
                  · {{ currentModule.chapters.find((c) => c.id === selectedChapter)?.title }}
                </template>
              </p>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <RouterLink
              v-if="currentModule"
              :to="{ name: 'reader', params: { slug: currentModule.slug } }"
              class="btn-ghost h-9 px-3 text-xs"
            >
              <BookOpen class="h-3.5 w-3.5" /> 通读本篇
            </RouterLink>
            <button class="btn-primary h-9 px-3.5 text-xs" :disabled="!total || launchingStudy" @click="startFilteredStudy">
              <Loader2 v-if="launchingStudy" class="h-3.5 w-3.5 animate-spin" />
              <Play v-else class="h-3.5 w-3.5 fill-current" /> 刷当前 {{ total }} 题
            </button>
          </div>
        </div>

        <!-- 考频 + 搜索 -->
        <div class="flex flex-wrap items-center gap-2 pt-1">
          <span class="mr-1 inline-flex items-center gap-1 text-xs text-slate-400"><Filter class="h-3.5 w-3.5" /> 考频</span>
          <button
            class="rounded-lg border px-2.5 py-1 text-xs font-medium transition"
            :class="!priorities.length ? 'border-slate-900 bg-slate-900 text-white dark:border-white dark:bg-white dark:text-slate-900' : 'border-slate-200 text-slate-500 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-400 dark:hover:bg-slate-800'"
            @click="priorities = []"
          >
            全部
          </button>
          <button
            v-for="p in priorityOptions"
            :key="p.value"
            class="rounded-lg border px-2.5 py-1 text-xs font-medium transition"
            :class="priorities.includes(p.value) ? p.active : 'border-slate-200 text-slate-500 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-400 dark:hover:bg-slate-800'"
            @click="togglePriority(p.value)"
          >
            {{ p.label }}
          </button>

          <div class="relative ml-auto w-full sm:w-56">
            <Search class="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400" />
            <input
              v-model="keyword"
              class="h-8 w-full rounded-lg border border-slate-200 bg-slate-50 pl-8 pr-7 text-xs outline-none transition focus:border-brand-500 focus:bg-white dark:border-slate-700 dark:bg-slate-800/70 dark:focus:bg-slate-900"
              placeholder="筛选题干关键词..."
            />
            <button v-if="keyword" class="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400" @click="keyword = ''">
              <X class="h-3.5 w-3.5" />
            </button>
          </div>
        </div>

        <!-- 状态胶囊 -->
        <div class="flex flex-wrap items-center gap-1.5">
          <button
            v-for="s in statusOptions"
            :key="s.value"
            class="rounded-full px-3 py-1 text-xs font-medium transition"
            :class="status === s.value
              ? 'bg-brand-600 text-white shadow-xs'
              : 'bg-slate-100 text-slate-600 hover:bg-slate-200/70 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700'"
            @click="status = s.value"
          >
            {{ s.label }}
          </button>
          <button v-if="hasActiveFilter" class="ml-auto text-xs text-slate-400 hover:text-brand-600" @click="resetFilters">
            清空筛选
          </button>
        </div>
      </div>

      <!-- 题目列表 -->
      <div v-if="loading" class="space-y-2.5 animate-pulse">
        <div v-for="i in 6" :key="i" class="h-20 rounded-2xl bg-slate-200/70 dark:bg-slate-800/70" />
      </div>

      <div v-else-if="!questions.length" class="card flex flex-col items-center justify-center py-16 text-center">
        <Layers class="h-10 w-10 text-slate-300 dark:text-slate-600" />
        <p class="mt-3 text-sm font-medium text-slate-600 dark:text-slate-300">当前筛选条件下暂无题目</p>
        <button v-if="hasActiveFilter" class="btn-ghost mt-4 h-9 text-xs" @click="resetFilters">重置筛选条件</button>
      </div>

      <template v-else>
        <div class="card divide-y divide-slate-100 overflow-hidden dark:divide-slate-800">
          <div
            v-for="q in questions"
            :key="q.id"
            class="group flex cursor-pointer items-start gap-3 px-4 py-3.5 transition hover:bg-slate-50/90 sm:px-5 dark:hover:bg-slate-800/40"
            @click="router.push({ name: 'study', query: { mode: 'single', id: String(q.id) } })"
          >
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <span v-if="!selectedSlug" class="rounded bg-slate-100 px-1.5 py-0.5 text-[11px] font-medium text-slate-500 dark:bg-slate-800 dark:text-slate-400">
                  {{ q.moduleTitle || moduleTitleMap[q.moduleSlug] }}
                </span>
                <span class="font-mono text-xs font-semibold text-slate-400">{{ q.code }}</span>
                <PriorityBadge :priority="q.priority" />
                <span v-if="q.imageCount > 0" class="inline-flex items-center gap-0.5 text-[11px] text-slate-400" :title="`${q.imageCount} 张原理图`">
                  <Image class="h-3 w-3" /> {{ q.imageCount }}
                </span>
                <span v-if="q.hasTable" class="inline-flex items-center text-[11px] text-slate-400" title="含对比表格">
                  <Table2 class="h-3 w-3" />
                </span>
                <span v-if="q.hasCode" class="inline-flex items-center text-[11px] text-slate-400" title="含代码示例">
                  <Code2 class="h-3 w-3" />
                </span>
                <span v-if="q.hasAnimation" class="inline-flex items-center gap-0.5 rounded bg-indigo-50 px-1.5 py-0.5 text-[11px] font-medium text-indigo-600 dark:bg-indigo-500/15 dark:text-indigo-300" title="含交互式原理动画">
                  <Play class="h-2.5 w-2.5 fill-current" /> 动画
                </span>
                <span v-if="q.progress.hasNote" class="inline-flex items-center gap-0.5 text-[11px] text-brand-600 dark:text-brand-400" title="有助记备注">
                  <FileText class="h-3 w-3" /> 助记
                </span>
              </div>

              <p class="mt-1.5 text-[14.5px] font-medium leading-snug text-slate-900 group-hover:text-brand-600 dark:text-slate-100 dark:group-hover:text-brand-400">
                {{ q.title }}
              </p>

              <div class="mt-2 flex flex-wrap items-center gap-3 text-xs text-slate-400">
                <StatusDot :status="q.progress.status" />
                <span v-if="q.progress.nextReviewAt">{{ dueLabel(q.progress.nextReviewAt) }}</span>
                <span v-if="q.progress.lapseCount > 0" class="text-amber-600 dark:text-amber-400">遗忘 {{ q.progress.lapseCount }} 次</span>
              </div>
            </div>

            <div class="flex shrink-0 items-center gap-1 pt-0.5">
              <RouterLink
                :to="{ name: 'reader', params: { slug: q.moduleSlug }, hash: `#${q.id}` }"
                class="rounded-lg p-2 text-slate-400 opacity-0 transition hover:bg-slate-200/60 hover:text-slate-700 group-hover:opacity-100 dark:hover:bg-slate-700 dark:hover:text-slate-200"
                title="在通读页定位"
                @click.stop
              >
                <BookOpen class="h-4 w-4" />
              </RouterLink>
              <button
                class="rounded-lg p-2 transition hover:bg-slate-200/60 dark:hover:bg-slate-700"
                :class="q.progress.isStarred ? 'text-amber-400' : 'text-slate-300 hover:text-amber-400 dark:text-slate-600'"
                :title="q.progress.isStarred ? '取消收藏' : '收藏'"
                @click="onToggleStar(q, $event)"
              >
                <Star class="h-4 w-4" :class="q.progress.isStarred ? 'fill-current' : ''" />
              </button>
            </div>
          </div>
        </div>

        <!-- 后端分页栏 -->
        <div v-if="totalPages > 1" class="flex flex-wrap items-center justify-between gap-3 px-1 pt-1 text-xs text-slate-500 dark:text-slate-400">
          <span class="tabular-nums">共 {{ total }} 题 · 第 {{ page }} / {{ totalPages }} 页</span>
          <div class="flex items-center gap-1">
            <button
              class="inline-flex h-8 items-center gap-1 rounded-lg border border-slate-200 bg-white px-2.5 font-medium text-slate-600 transition hover:bg-slate-50 disabled:opacity-40 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800"
              :disabled="page <= 1"
              @click="goPage(page - 1)"
            >
              <ChevronLeft class="h-3.5 w-3.5" /> 上一页
            </button>
            <template v-for="(p, idx) in pageNumbers" :key="idx">
              <span v-if="p === '...'" class="px-1.5 text-slate-400">…</span>
              <button
                v-else
                class="h-8 min-w-8 rounded-lg px-2 font-medium tabular-nums transition"
                :class="p === page
                  ? 'bg-brand-600 text-white'
                  : 'border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800'"
                @click="goPage(p)"
              >
                {{ p }}
              </button>
            </template>
            <button
              class="inline-flex h-8 items-center gap-1 rounded-lg border border-slate-200 bg-white px-2.5 font-medium text-slate-600 transition hover:bg-slate-50 disabled:opacity-40 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800"
              :disabled="page >= totalPages"
              @click="goPage(page + 1)"
            >
              下一页 <ChevronRight class="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>
