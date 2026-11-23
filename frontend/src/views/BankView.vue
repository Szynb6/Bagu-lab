<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BookOpen, ChevronDown, ChevronLeft, ChevronRight, Code2, FileText, Filter, Image, Layers, Loader2, Play,
  RefreshCw, Search, Star, Table2, TriangleAlert, X, Zap,
} from 'lucide-vue-next'
import { knowledgeApi, studyApi } from '@/api'
import { useUiStore } from '@/stores/ui'
import PriorityBadge from '@/components/common/PriorityBadge.vue'
import StatusDot from '@/components/common/StatusDot.vue'
import ModuleIcon from '@/components/common/ModuleIcon.vue'
import { dueLabel } from '@/utils/format'
import type { Priority, QuestionFilter, QuestionItem } from '@/types'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const questions = ref<QuestionItem[]>([])
const total = ref(0)
const page = ref(Number(route.query.page) || 1)
const pageSize = 20
const totalPages = ref(0)
const loading = ref(true)
const loadError = ref('')
const launchingStudy = ref(false)

const selectedSlug = ref<string>((route.query.slug as string) || '')
const selectedChapter = ref<number | null>(route.query.chapter ? Number(route.query.chapter) : null)
const expandedSlug = ref<string>((route.query.slug as string) || '01-jvm')
const priorities = ref<Priority[]>(
  typeof route.query.priorities === 'string' && route.query.priorities
    ? (route.query.priorities.split(',').filter((p): p is Priority => ['P0', 'P1', 'P2'].includes(p)))
    : [],
)
const status = ref<NonNullable<QuestionFilter['status']>>(
  ((route.query.status as QuestionFilter['status']) || 'all'),
)
const keyword = ref((route.query.q as string) || '')

const masteryOptions: { value: NonNullable<QuestionFilter['status']>; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'new', label: '未背' },
  { value: 'weak', label: '待巩固' },
  { value: 'reviewing', label: '复习中' },
  { value: 'mastered', label: '已掌握' },
]

const traitOptions: { value: NonNullable<QuestionFilter['status']>; label: string; icon: typeof Star }[] = [
  { value: 'starred', label: '已收藏', icon: Star },
  { value: 'noted', label: '有助记', icon: FileText },
  { value: 'animated', label: '有动画', icon: Play },
]

const priorityOptions: { value: Priority; label: string; active: string }[] = [
  { value: 'P0', label: 'P0 必背', active: 'border-[#EFC9BF] bg-[#FBF0ED] text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]' },
  { value: 'P1', label: 'P1 高频', active: 'border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-700/60 dark:bg-amber-950/50 dark:text-amber-200' },
  { value: 'P2', label: 'P2 了解', active: 'border-[var(--border-strong)] bg-[var(--bg-subtle)] text-[var(--text-primary)]' },
]

const currentModule = computed(() => ui.modules.find((m) => m.slug === selectedSlug.value) ?? null)
const moduleTitleMap = computed(() => Object.fromEntries(ui.modules.map((m) => [m.slug, m.title])))

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

let syncingRoute = false
function syncQueryToUrl() {
  const nextQuery: Record<string, string> = {}
  if (selectedSlug.value) nextQuery.slug = selectedSlug.value
  if (selectedChapter.value !== null) nextQuery.chapter = String(selectedChapter.value)
  if (priorities.value.length) nextQuery.priorities = priorities.value.join(',')
  if (status.value !== 'all') nextQuery.status = status.value
  if (keyword.value.trim()) nextQuery.q = keyword.value.trim()
  if (page.value > 1) nextQuery.page = String(page.value)

  syncingRoute = true
  router.replace({ query: nextQuery }).finally(() => {
    syncingRoute = false
  })
}

let fetchSeq = 0
async function fetchQuestions() {
  const my = ++fetchSeq
  loading.value = true
  loadError.value = ''
  try {
    const res = await knowledgeApi.questions(buildFilter())
    if (my === fetchSeq) {
      questions.value = res.records
      total.value = res.total
      totalPages.value = res.totalPages
    }
  } catch (e) {
    if (my === fetchSeq) {
      loadError.value = (e as Error).message || '加载题库列表失败，请重试'
    }
  } finally {
    if (my === fetchSeq) loading.value = false
  }
}

onMounted(async () => {
  await ui.loadModules().catch(() => {})
  await fetchQuestions()
})

// 监听外部导航（如侧边栏我的收藏、仪表盘优先级跳转）
watch(
  () => route.query,
  (q) => {
    if (syncingRoute) return
    const nextStatus = ((q.status as QuestionFilter['status']) || 'all')
    const nextSlug = (q.slug as string) || ''
    const nextChapter = q.chapter ? Number(q.chapter) : null
    const nextPriorities = typeof q.priorities === 'string' && q.priorities
      ? (q.priorities.split(',').filter((p): p is Priority => ['P0', 'P1', 'P2'].includes(p)))
      : []
    const nextKw = (q.q as string) || ''
    const nextPage = Number(q.page) || 1

    let changed = false
    if (nextStatus !== status.value) { status.value = nextStatus; changed = true }
    if (nextSlug !== selectedSlug.value) { selectedSlug.value = nextSlug; changed = true }
    if (nextChapter !== selectedChapter.value) { selectedChapter.value = nextChapter; changed = true }
    if (nextPriorities.join(',') !== priorities.value.join(',')) { priorities.value = nextPriorities; changed = true }
    if (nextKw !== keyword.value) { keyword.value = nextKw; changed = true }
    if (nextPage !== page.value) { page.value = nextPage; changed = true }
    if (changed) fetchQuestions()
  },
)

let kwTimer: number | undefined
watch([selectedSlug, selectedChapter, priorities, status], () => {
  page.value = 1
  syncQueryToUrl()
  fetchQuestions()
}, { deep: true })

watch(keyword, () => {
  clearTimeout(kwTimer)
  kwTimer = window.setTimeout(() => {
    page.value = 1
    syncQueryToUrl()
    fetchQuestions()
  }, 240)
})

function goPage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value || nextPage === page.value) return
  page.value = nextPage
  syncQueryToUrl()
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

async function onToggleStar(q: QuestionItem) {
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
    <!-- 左侧模块 + 篇目索引（桌面端） -->
    <aside class="card hidden w-[264px] shrink-0 overflow-hidden lg:sticky lg:top-24 lg:block">
      <div class="border-b border-[var(--border-default)] px-4 py-3">
        <p class="text-xs font-semibold text-[var(--text-primary)]">知识卷目索引</p>
      </div>
      <div class="max-h-[calc(100vh-11rem)] space-y-0.5 overflow-y-auto p-2">
        <button
          type="button"
          class="flex w-full items-center gap-2.5 rounded-lg border px-3 py-2 text-left text-sm font-medium transition-colors"
          :class="
            !selectedSlug
              ? 'border-[var(--border-strong)] bg-[var(--bg-subtle)] text-[#C84B31] dark:text-[#E06246]'
              : 'border-transparent text-[var(--text-secondary)] hover:bg-[var(--bg-subtle)]/70 hover:text-[var(--text-primary)]'
          "
          @click="selectModule('')"
        >
          <Layers class="h-4 w-4 shrink-0" />
          <span>全部模块</span>
          <span class="ml-auto font-mono text-xs tabular-nums text-[var(--text-muted)]">
            {{ ui.modules.reduce((s, m) => s + m.questionCount, 0) }}
          </span>
        </button>

        <div v-for="m in ui.modules" :key="m.slug" class="pt-0.5">
          <div
            class="flex items-center gap-1 rounded-lg border px-2 py-1.5 transition-colors"
            :class="
              selectedSlug === m.slug && selectedChapter === null
                ? 'border-[var(--border-strong)] bg-[var(--bg-subtle)] text-[#C84B31] dark:text-[#E06246]'
                : 'border-transparent text-[var(--text-primary)] hover:bg-[var(--bg-subtle)]/60'
            "
          >
            <button
              type="button"
              class="rounded p-1 text-[var(--text-muted)] hover:text-[var(--text-primary)]"
              :aria-label="expandedSlug === m.slug ? `折叠 ${m.title}` : `展开 ${m.title}`"
              @click="expandedSlug = expandedSlug === m.slug ? '' : m.slug"
            >
              <component :is="expandedSlug === m.slug ? ChevronDown : ChevronRight" class="h-3.5 w-3.5" />
            </button>
            <button type="button" class="flex min-w-0 flex-1 items-center gap-2 text-left text-[13.5px] font-medium" @click="selectModule(m.slug)">
              <span class="font-mono text-[11px] tabular-nums text-[var(--text-muted)]">{{ String(m.order).padStart(2, '0') }}</span>
              <span class="truncate">{{ m.title }}</span>
              <span class="ml-auto font-mono text-[11px] tabular-nums text-[var(--text-muted)]">{{ m.questionCount }}</span>
            </button>
          </div>

          <div v-if="expandedSlug === m.slug" class="mt-0.5 space-y-0.5 pl-7 pr-1">
            <button
              v-for="ch in m.chapters"
              :key="ch.id"
              type="button"
              class="flex w-full items-center gap-2 rounded-md border px-2.5 py-1.5 text-left text-xs transition-colors"
              :class="
                selectedChapter === ch.id
                  ? 'border-[var(--border-strong)] bg-[var(--bg-subtle)] font-medium text-[#C84B31] dark:text-[#E06246]'
                  : 'border-transparent text-[var(--text-secondary)] hover:bg-[var(--bg-subtle)]/60 hover:text-[var(--text-primary)]'
              "
              @click="selectChapter(m.slug, ch.id)"
            >
              <span class="truncate">{{ ch.title.replace(/^第[一二三四五六七八九十]+(?:篇|部分)[：:]\s*/, '') }}</span>
              <span class="ml-auto shrink-0 font-mono text-[11px] tabular-nums text-[var(--text-muted)]">{{ ch.questionCount }}</span>
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
          type="button"
          class="shrink-0 rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors"
          :class="!selectedSlug ? 'border-[#C84B31] bg-[#C84B31] text-white' : 'border-[var(--border-default)] bg-[var(--bg-surface)] text-[var(--text-secondary)]'"
          @click="selectModule('')"
        >
          全部
        </button>
        <button
          v-for="m in ui.modules"
          :key="m.slug"
          type="button"
          class="shrink-0 rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors"
          :class="selectedSlug === m.slug ? 'border-[#C84B31] bg-[#C84B31] text-white' : 'border-[var(--border-default)] bg-[var(--bg-surface)] text-[var(--text-secondary)]'"
          @click="selectModule(m.slug)"
        >
          {{ m.title }}
        </button>
      </div>

      <!-- 筛选控制栏 -->
      <div class="card space-y-3.5 p-4 sm:p-5">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div class="flex items-center gap-3">
            <ModuleIcon v-if="currentModule" :icon="currentModule.icon" :slug="currentModule.slug" :order="currentModule.order" />
            <div>
              <h2 class="text-base font-semibold tracking-tight text-[var(--text-primary)]">
                {{ currentModule ? currentModule.fullTitle : '全部八股题库' }}
              </h2>
              <p class="text-xs text-[var(--text-secondary)]">
                当前匹配 <strong class="font-mono tabular-nums text-[var(--text-primary)]">{{ total }}</strong> 题
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
            <button type="button" class="btn-primary h-9 px-3.5 text-xs" :disabled="!total || launchingStudy" @click="startFilteredStudy">
              <Loader2 v-if="launchingStudy" class="h-3.5 w-3.5 animate-spin" />
              <Play v-else class="h-3.5 w-3.5 fill-current" /> 刷当前 {{ total }} 题
            </button>
          </div>
        </div>

        <!-- 考频 + 关键词检索 -->
        <div class="flex flex-wrap items-center gap-2 pt-0.5">
          <span class="mr-1 inline-flex items-center gap-1 text-xs text-[var(--text-muted)]">
            <Filter class="h-3.5 w-3.5" /> 考频
          </span>
          <button
            type="button"
            class="rounded-md border px-2.5 py-1 text-xs font-medium transition-colors"
            :class="!priorities.length ? 'border-[var(--text-primary)] bg-[var(--text-primary)] text-[var(--bg-surface)]' : 'border-[var(--border-default)] text-[var(--text-secondary)] hover:bg-[var(--bg-subtle)]'"
            @click="priorities = []"
          >
            全部
          </button>
          <button
            v-for="p in priorityOptions"
            :key="p.value"
            type="button"
            class="rounded-md border px-2.5 py-1 font-mono text-xs font-medium transition-colors"
            :class="priorities.includes(p.value) ? p.active : 'border-[var(--border-default)] text-[var(--text-secondary)] hover:bg-[var(--bg-subtle)]'"
            @click="togglePriority(p.value)"
          >
            {{ p.label }}
          </button>

          <div class="relative ml-auto w-full sm:w-60">
            <label for="bank-keyword" class="sr-only">筛选题干关键词</label>
            <Search class="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-[var(--text-muted)]" />
            <input
              id="bank-keyword"
              v-model="keyword"
              class="h-8 w-full rounded-lg border border-[var(--border-default)] bg-[var(--bg-subtle)] pl-8 pr-7 text-xs text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-muted)] focus:border-[#C84B31] focus:bg-[var(--bg-surface)]"
              placeholder="筛选题干关键词..."
            />
            <button
              v-if="keyword"
              type="button"
              class="absolute right-1.5 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded text-[var(--text-muted)] hover:text-[var(--text-primary)]"
              aria-label="清空搜索关键词"
              @click="keyword = ''"
            >
              <X class="h-3.5 w-3.5" />
            </button>
          </div>
        </div>

        <!-- 掌握状态分段 + 内容特征标签（彻底分组、零 Emoji） -->
        <div class="flex flex-wrap items-center gap-3 border-t border-[var(--border-default)] pt-3">
          <div class="inline-flex flex-wrap rounded-lg border border-[var(--border-default)] bg-[var(--bg-subtle)] p-0.5" role="group" aria-label="按掌握状态筛选">
            <button
              v-for="s in masteryOptions"
              :key="s.value"
              type="button"
              class="rounded-md px-2.5 py-1 text-xs font-medium transition-colors"
              :class="
                status === s.value
                  ? 'bg-[var(--bg-surface)] text-[#C84B31] shadow-2xs dark:text-[#E06246]'
                  : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
              "
              @click="status = s.value"
            >
              {{ s.label }}
            </button>
          </div>

          <div class="flex flex-wrap items-center gap-1.5" role="group" aria-label="按内容特征筛选">
            <button
              v-for="t in traitOptions"
              :key="t.value"
              type="button"
              class="inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-xs font-medium transition-colors"
              :class="
                status === t.value
                  ? 'border-[#EFC9BF] bg-[#FBF0ED] text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]'
                  : 'border-[var(--border-default)] bg-[var(--bg-surface)] text-[var(--text-secondary)] hover:bg-[var(--bg-subtle)]'
              "
              @click="status = status === t.value ? 'all' : t.value"
            >
              <component :is="t.icon" class="h-3 w-3" />
              {{ t.label }}
            </button>
          </div>

          <button
            v-if="hasActiveFilter"
            type="button"
            class="ml-auto text-xs font-medium text-[var(--text-muted)] underline-offset-2 hover:text-[#C84B31] hover:underline dark:hover:text-[#E06246]"
            @click="resetFilters"
          >
            清空筛选
          </button>
        </div>
      </div>

      <!-- 题目列表加载态 -->
      <div v-if="loading" class="space-y-2.5 animate-pulse">
        <div v-for="i in 6" :key="i" class="h-20 rounded-xl border border-[var(--border-default)] bg-[var(--bg-surface)]" />
      </div>

      <!-- 错误态 -->
      <div v-else-if="loadError" class="card flex flex-col items-center justify-center px-6 py-16 text-center">
        <TriangleAlert class="h-9 w-9 text-[#C84B31] dark:text-[#E06246]" />
        <p class="mt-3 text-sm font-medium text-[var(--text-primary)]">{{ loadError }}</p>
        <button type="button" class="btn-primary mt-4 h-9 text-xs" @click="fetchQuestions">
          <RefreshCw class="h-3.5 w-3.5" /> 重试加载
        </button>
      </div>

      <!-- 空列表 -->
      <div v-else-if="!questions.length" class="card flex flex-col items-center justify-center py-16 text-center">
        <Layers class="h-9 w-9 text-[var(--text-muted)]" />
        <p class="mt-3 text-sm font-medium text-[var(--text-primary)]">当前筛选条件下暂无题目</p>
        <button v-if="hasActiveFilter" type="button" class="btn-ghost mt-4 h-9 text-xs" @click="resetFilters">重置筛选条件</button>
      </div>

      <template v-else>
        <!-- 题目列表账本 -->
        <div class="card divide-y divide-[var(--border-default)] overflow-hidden">
          <article
            v-for="q in questions"
            :key="q.id"
            class="group flex items-start gap-3 px-4 py-3.5 transition-colors hover:bg-[var(--bg-subtle)]/55 sm:px-5"
          >
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <span
                  v-if="!selectedSlug"
                  class="rounded border border-[var(--border-default)] bg-[var(--bg-subtle)] px-1.5 py-0.5 text-[11px] font-medium text-[var(--text-secondary)]"
                >
                  {{ q.moduleTitle || moduleTitleMap[q.moduleSlug] }}
                </span>
                <span class="font-mono text-xs font-semibold text-[var(--text-muted)]">{{ q.code }}</span>
                <PriorityBadge :priority="q.priority" />
                <span v-if="q.imageCount > 0" class="inline-flex items-center gap-0.5 font-mono text-[11px] text-[var(--text-muted)]" :title="`${q.imageCount} 张原理图`">
                  <Image class="h-3 w-3" /> {{ q.imageCount }}
                </span>
                <span v-if="q.hasTable" class="inline-flex items-center text-[11px] text-[var(--text-muted)]" title="含对比表格">
                  <Table2 class="h-3 w-3" />
                </span>
                <span v-if="q.hasCode" class="inline-flex items-center text-[11px] text-[var(--text-muted)]" title="含代码示例">
                  <Code2 class="h-3 w-3" />
                </span>
                <span
                  v-if="q.hasAnimation"
                  class="inline-flex items-center gap-1 rounded border border-[#EFC9BF] bg-[#FBF0ED] px-1.5 py-0.5 text-[11px] font-medium text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]"
                  title="含交互式原理推演"
                >
                  <Play class="h-2.5 w-2.5 fill-current" /> 交互推演
                </span>
                <span v-if="q.progress.hasNote" class="inline-flex items-center gap-0.5 text-[11px] font-medium text-[#2E6F5E] dark:text-[#4FA58E]" title="有助记备注">
                  <FileText class="h-3 w-3" /> 助记
                </span>
              </div>

              <RouterLink
                :to="{ name: 'study', query: { mode: 'single', id: String(q.id) } }"
                class="mt-1.5 block text-[15px] font-medium leading-snug text-[var(--text-primary)] transition-colors hover:text-[#C84B31] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 dark:hover:text-[#E06246]"
              >
                {{ q.title }}
              </RouterLink>

              <div class="mt-2 flex flex-wrap items-center gap-3 text-xs text-[var(--text-muted)]">
                <StatusDot :status="q.progress.status" />
                <span v-if="q.progress.nextReviewAt" class="font-mono">{{ dueLabel(q.progress.nextReviewAt) }}</span>
                <span v-if="q.progress.lapseCount > 0" class="font-mono text-[#96311D] dark:text-[#EFC0B3]">遗忘 {{ q.progress.lapseCount }} 次</span>
              </div>
            </div>

            <div class="flex shrink-0 items-center gap-1 pt-0.5">
              <RouterLink
                :to="{ name: 'reader', params: { slug: q.moduleSlug }, query: { q: String(q.id) }, hash: `#${q.id}` }"
                class="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)] lg:opacity-0 lg:group-hover:opacity-100 lg:group-focus-within:opacity-100 focus-visible:opacity-100"
                title="在通读页定位"
                aria-label="在通读页定位该题"
              >
                <BookOpen class="h-4 w-4" />
              </RouterLink>
              <RouterLink
                :to="{ name: 'study', query: { mode: 'single', id: String(q.id) } }"
                class="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--text-muted)] transition-colors hover:bg-[#FBF0ED] hover:text-[#C84B31] lg:opacity-0 lg:group-hover:opacity-100 lg:group-focus-within:opacity-100 focus-visible:opacity-100 dark:hover:bg-[#2E1B16] dark:hover:text-[#E06246]"
                title="单题闪卡"
                aria-label="进入单题闪卡背诵"
              >
                <Zap class="h-4 w-4" />
              </RouterLink>
              <button
                type="button"
                class="flex h-8 w-8 items-center justify-center rounded-lg transition-colors hover:bg-[var(--bg-subtle)]"
                :class="q.progress.isStarred ? 'text-amber-500' : 'text-[var(--text-muted)] hover:text-amber-500'"
                :title="q.progress.isStarred ? '取消收藏' : '收藏'"
                :aria-label="q.progress.isStarred ? '取消收藏该题' : '收藏该题'"
                @click="onToggleStar(q)"
              >
                <Star class="h-4 w-4" :class="q.progress.isStarred ? 'fill-current' : ''" />
              </button>
            </div>
          </article>
        </div>

        <!-- 分页栏 -->
        <div v-if="totalPages > 1" class="flex flex-wrap items-center justify-between gap-3 px-1 pt-1 text-xs text-[var(--text-secondary)]">
          <span class="font-mono tabular-nums">共 {{ total }} 题 · 第 {{ page }} / {{ totalPages }} 页</span>
          <div class="flex items-center gap-1">
            <button
              type="button"
              class="btn-ghost h-8 px-2.5 text-xs"
              :disabled="page <= 1"
              @click="goPage(page - 1)"
            >
              <ChevronLeft class="h-3.5 w-3.5" /> 上一页
            </button>
            <div class="hidden items-center gap-1 sm:flex">
              <template v-for="(p, idx) in pageNumbers" :key="idx">
                <span v-if="p === '...'" class="px-1.5 text-[var(--text-muted)]">…</span>
                <button
                  v-else
                  type="button"
                  class="h-8 min-w-8 rounded-lg px-2 font-mono font-medium tabular-nums transition-colors"
                  :class="
                    p === page
                      ? 'bg-[#C84B31] text-white'
                      : 'border border-[var(--border-default)] bg-[var(--bg-surface)] text-[var(--text-secondary)] hover:bg-[var(--bg-subtle)]'
                  "
                  @click="goPage(p)"
                >
                  {{ p }}
                </button>
              </template>
            </div>
            <button
              type="button"
              class="btn-ghost h-8 px-2.5 text-xs"
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
