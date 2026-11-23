<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft, BookOpen, Check, ChevronLeft, ChevronRight, Clapperboard, Eye, EyeOff, HelpCircle, Image as ImageIcon,
  Keyboard, Lightbulb, Moon, NotebookPen, PartyPopper, RefreshCw, RotateCcw, Star, Sun, TriangleAlert, Undo2, X,
} from 'lucide-vue-next'
import { studyApi } from '@/api'
import { useUiStore } from '@/stores/ui'
import MarkdownView from '@/components/common/MarkdownView.vue'
import QuestionAnimation from '@/components/animations/QuestionAnimation.vue'
import PriorityBadge from '@/components/common/PriorityBadge.vue'
import StatusDot from '@/components/common/StatusDot.vue'
import { previewLabel, schedule } from '@/utils/sm2'
import { relativeTime } from '@/utils/format'
import type { Progress, QueueMode, QueueRequest, QuestionWithProgress, Rating } from '@/types'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const queue = ref<QuestionWithProgress[]>([])
const index = ref(0)
const loading = ref(true)
const loadError = ref('')
const revealed = ref(false)
const showHint = ref(false)
const showAnimation = ref(false)
const showShortcuts = ref(false)
const finished = ref(false)

const noteEl = ref<HTMLTextAreaElement>()
const noteText = ref('')
const noteSaving = ref(false)
const noteSaved = ref(false)

// 统计与撤销快照
const sessionStart = ref(Date.now())
const finishedAt = ref<number | null>(null)
const cardStart = ref(Date.now())
const stats = reactive<Record<Rating, number>>({ 1: 0, 2: 0, 3: 0, 4: 0 })
const sessionRatings = reactive<Record<number, Rating>>({})

interface UndoSnapshot {
  index: number
  questionId: number
  rating: Rating
  prevProgress: Progress
  wasFinished: boolean
}
const lastRated = ref<UndoSnapshot | null>(null)
let undoTimer: number | undefined

const current = computed(() => queue.value[index.value] ?? null)
const total = computed(() => queue.value.length)
const progressPct = computed(() => (total.value ? Math.round(((finished.value ? total.value : index.value) / total.value) * 100) : 0))

const currentModule = computed(() =>
  current.value ? ui.modules.find((m) => m.slug === current.value!.moduleSlug) ?? null : null,
)
const currentChapterTitle = computed(() => {
  if (!current.value || !currentModule.value) return ''
  return currentModule.value.chapters.find((c) => c.id === current.value!.chapterId)?.title ?? ''
})

const modeLabel = computed(() => {
  const mode = (route.query.mode as QueueMode) || 'today'
  switch (mode) {
    case 'today': return '今日复习队列'
    case 'p0': return 'P0 必背极速冲刺'
    case 'mistakes': return '错题本专练'
    case 'starred': return '收藏夹专练'
    case 'module': return currentModule.value ? `${currentModule.value.title} 专练` : '模块刷题'
    case 'custom': return sessionStorage.getItem('bagu:customLabel') || '筛选专练'
    case 'single': return '单题精背'
    default: return '闪卡背诵'
  }
})

const ratingButtons: { value: Rating; label: string; key: string; cls: string }[] = [
  {
    value: 1,
    label: '完全不会',
    key: '1',
    cls: 'border-[#EFC9BF] bg-[#FBF0ED] text-[#96311D] hover:bg-[#F6DDD6] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3] dark:hover:bg-[#3F231C]',
  },
  {
    value: 2,
    label: '有点模糊',
    key: '2',
    cls: 'border-amber-300/80 bg-amber-50 text-amber-900 hover:bg-amber-100 dark:border-amber-700/50 dark:bg-amber-950/40 dark:text-amber-200 dark:hover:bg-amber-950/70',
  },
  {
    value: 3,
    label: '基本掌握',
    key: '3',
    cls: 'border-[#B8DCD1] bg-[#EBF4F1] text-[#235749] hover:bg-[#D8ECE5] dark:border-[#244E41] dark:bg-[#162923] dark:text-[#86CBB7] dark:hover:bg-[#1E3830]',
  },
  {
    value: 4,
    label: '秒杀跳过',
    key: '4',
    cls: 'border-[var(--border-strong)] bg-[var(--bg-subtle)] text-[var(--text-primary)] hover:bg-[var(--border-default)]',
  },
]

const lastRatingText: Record<Rating, string> = { 1: '完全不会', 2: '有点模糊', 3: '基本掌握', 4: '秒杀' }

async function loadQueue() {
  loading.value = true
  loadError.value = ''
  finished.value = false
  finishedAt.value = null
  index.value = 0
  revealed.value = false
  showHint.value = false
  showAnimation.value = false
  lastRated.value = null
  clearTimeout(undoTimer)
  stats[1] = stats[2] = stats[3] = stats[4] = 0
  for (const k of Object.keys(sessionRatings)) delete sessionRatings[Number(k)]
  sessionStart.value = Date.now()
  cardStart.value = Date.now()
  try {
    await ui.loadModules().catch(() => {})
    const mode = ((route.query.mode as QueueMode) || 'today')
    const req: QueueRequest = {
      mode,
      slug: route.query.slug as string | undefined,
      chapterId: route.query.chapterId as string | undefined,
      id: route.query.id as string | undefined,
    }
    if (mode === 'custom') {
      try {
        req.ids = JSON.parse(sessionStorage.getItem('bagu:customQueue') || '[]')
      } catch {
        req.ids = []
      }
    }
    queue.value = await studyApi.queue(req)
    syncNote()
  } catch (e) {
    loadError.value = (e as Error).message || '加载复习队列失败，请检查网络连接'
  } finally {
    loading.value = false
  }
}

function syncNote() {
  noteText.value = current.value?.progress.personalNote ?? ''
  noteSaved.value = false
  cardStart.value = Date.now()
}

watch(() => route.fullPath, loadQueue)
onMounted(loadQueue)

function goCard(nextIdx: number) {
  if (nextIdx < 0 || nextIdx >= total.value) return
  index.value = nextIdx
  revealed.value = false
  showHint.value = false
  showAnimation.value = false
  syncNote()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function rate(r: Rating) {
  const q = current.value
  if (!q) return
  const durationSec = Math.max(1, Math.round((Date.now() - cardStart.value) / 1000))
  const prevProgress: Progress = { ...q.progress }
  const prevIdx = index.value

  q.progress = schedule(q.progress, r)
  stats[r] += 1
  sessionRatings[q.id] = r

  lastRated.value = {
    index: prevIdx,
    questionId: q.id,
    rating: r,
    prevProgress,
    wasFinished: prevIdx + 1 >= total.value,
  }
  clearTimeout(undoTimer)
  undoTimer = window.setTimeout(() => {
    lastRated.value = null
  }, 6000)

  studyApi.review(q.id, r, durationSec).catch(() => ui.showToast('同步进度失败，请稍后重试'))

  if (index.value + 1 < total.value) {
    goCard(index.value + 1)
  } else {
    finished.value = true
    finishedAt.value = Date.now()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

function undoLastRating() {
  const snap = lastRated.value
  if (!snap) return
  const target = queue.value[snap.index]
  if (!target || target.id !== snap.questionId) return

  target.progress = { ...snap.prevProgress }
  if (stats[snap.rating] > 0) stats[snap.rating] -= 1
  delete sessionRatings[snap.questionId]

  finished.value = false
  finishedAt.value = null
  index.value = snap.index
  revealed.value = true
  lastRated.value = null
  clearTimeout(undoTimer)
  syncNote()
  ui.showToast('已撤回上一题评分，请重新选择')
}

async function toggleStar() {
  const q = current.value
  if (!q) return
  q.progress.isStarred = !q.progress.isStarred
  ui.showToast(q.progress.isStarred ? '已加入收藏' : '已取消收藏')
  try {
    q.progress.isStarred = await studyApi.toggleStar(q.id)
  } catch {
    q.progress.isStarred = !q.progress.isStarred
  }
}

async function saveNote() {
  const q = current.value
  if (!q || noteText.value === q.progress.personalNote) return
  noteSaving.value = true
  try {
    await studyApi.saveNote(q.id, noteText.value)
    q.progress.personalNote = noteText.value
    noteSaved.value = true
    setTimeout(() => (noteSaved.value = false), 1800)
  } finally {
    noteSaving.value = false
  }
}

function openDiagrams() {
  const q = current.value
  if (!q || !q.images.length) {
    ui.showToast('本题暂无原理图解')
    return
  }
  ui.openLightbox(q.images, 0)
}

function goBack(force = false) {
  if (!force && totalReviewed.value > 0 && !finished.value) {
    if (!window.confirm('当前已有背诵进度，确定结束本轮闪卡返回吗？')) return
  }
  if (window.history.length > 1) router.back()
  else router.push({ name: 'dashboard' })
}

const totalReviewed = computed(() => stats[1] + stats[2] + stats[3] + stats[4])
const elapsedMin = computed(() => {
  const end = finishedAt.value ?? Date.now()
  return Math.max(1, Math.round((end - sessionStart.value) / 60000))
})

const weakQuestions = computed(() =>
  queue.value.filter((q) => {
    const r = sessionRatings[q.id]
    return r === 1 || r === 2
  }),
)

function retryWeakQuestions() {
  if (!weakQuestions.value.length) return
  const ids = weakQuestions.value.map((q) => q.id)
  sessionStorage.setItem('bagu:customQueue', JSON.stringify(ids))
  sessionStorage.setItem('bagu:customLabel', '本轮薄弱题回炉重刷')
  router.push({ name: 'study', query: { mode: 'custom', t: String(Date.now()) } })
}

function onKeydown(e: KeyboardEvent) {
  if (ui.lightbox) return
  const target = e.target as HTMLElement | null
  const inInput = target && ['INPUT', 'TEXTAREA'].includes(target.tagName)
  if (inInput) {
    if (e.key === 'Escape') {
      target.blur()
      e.preventDefault()
    } else if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault()
      saveNote()
      target.blur()
    }
    return
  }
  if (showShortcuts.value) {
    if (e.key === 'Escape' || e.key === '?') showShortcuts.value = false
    return
  }
  if (finished.value || !current.value) {
    if (e.key === 'Escape') goBack(true)
    if (e.key.toLowerCase() === 'z' && lastRated.value) {
      e.preventDefault()
      undoLastRating()
    }
    return
  }

  const k = e.key.toLowerCase()
  if (e.code === 'Space' || e.key === 'Enter') {
    e.preventDefault()
    revealed.value = !revealed.value
  } else if (k === 'h') {
    e.preventDefault()
    if (current.value.hints.length) showHint.value = !showHint.value
  } else if (['1', '2', '3', '4'].includes(e.key) && revealed.value) {
    e.preventDefault()
    rate(Number(e.key) as Rating)
  } else if (k === 'z' && lastRated.value) {
    e.preventDefault()
    undoLastRating()
  } else if (k === 's') {
    e.preventDefault()
    toggleStar()
  } else if (k === 'n') {
    e.preventDefault()
    revealed.value = true
    nextTick(() => noteEl.value?.focus())
  } else if (k === 'i') {
    e.preventDefault()
    openDiagrams()
  } else if (e.key === 'ArrowLeft') {
    e.preventDefault()
    goCard(index.value - 1)
  } else if (e.key === 'ArrowRight') {
    e.preventDefault()
    goCard(index.value + 1)
  } else if (e.key === '?') {
    showShortcuts.value = true
  } else if (e.key === 'Escape') {
    goBack()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  clearTimeout(undoTimer)
})
</script>

<template>
  <div class="flex min-h-dvh flex-col">
    <!-- 顶部沉浸工作台栏 -->
    <header class="sticky top-0 z-30 border-b border-[var(--border-default)] bg-[var(--bg-canvas)]/92 backdrop-blur-sm">
      <div class="mx-auto flex h-14 max-w-[920px] items-center gap-3 px-4">
        <button
          type="button"
          class="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm font-medium text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)]"
          @click="goBack()"
        >
          <ArrowLeft class="h-4 w-4" />
          <span class="hidden sm:inline">退出</span>
        </button>

        <div class="min-w-0 flex-1 text-center">
          <p class="truncate text-xs font-medium text-[var(--text-secondary)]">
            {{ modeLabel }}
            <template v-if="currentModule && !finished"> · {{ currentModule.title }}</template>
          </p>
          <div v-if="total > 0" class="mx-auto mt-1 flex max-w-xs items-center gap-2">
            <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-[var(--bg-subtle)]">
              <div class="h-full rounded-full bg-[#C84B31] transition-[width] duration-300 dark:bg-[#E06246]" :style="{ width: `${progressPct}%` }" />
            </div>
            <span class="font-mono text-[11px] tabular-nums text-[var(--text-muted)]">{{ finished ? total : index + 1 }}/{{ total }}</span>
          </div>
        </div>

        <div class="flex items-center gap-1">
          <button
            v-if="current && !finished"
            type="button"
            class="flex h-9 w-9 items-center justify-center rounded-lg transition-colors hover:bg-[var(--bg-subtle)]"
            :class="current.progress.isStarred ? 'text-amber-500' : 'text-[var(--text-muted)] hover:text-amber-500'"
            title="收藏 (S)"
            :aria-label="current.progress.isStarred ? '取消收藏该题' : '收藏该题'"
            @click="toggleStar"
          >
            <Star class="h-4 w-4" :class="current.progress.isStarred ? 'fill-current' : ''" />
          </button>
          <RouterLink
            v-if="current && !finished"
            :to="{ name: 'reader', params: { slug: current.moduleSlug }, query: { q: String(current.id) }, hash: `#${current.id}` }"
            class="hidden h-9 w-9 items-center justify-center rounded-lg text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)] sm:inline-flex"
            title="在通读模式中查看"
            aria-label="在通读模式中查看"
          >
            <BookOpen class="h-4 w-4" />
          </RouterLink>
          <button
            type="button"
            class="hidden h-9 w-9 items-center justify-center rounded-lg text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)] sm:inline-flex"
            title="快捷键说明 (?)"
            aria-label="快捷键说明"
            @click="showShortcuts = true"
          >
            <Keyboard class="h-4 w-4" />
          </button>
          <button
            type="button"
            class="flex h-9 w-9 items-center justify-center rounded-lg text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)]"
            :title="ui.dark ? '浅色模式' : '深色模式'"
            :aria-label="ui.dark ? '切换浅色模式' : '切换深色模式'"
            @click="ui.toggleTheme()"
          >
            <component :is="ui.dark ? Sun : Moon" class="h-4 w-4" />
          </button>
        </div>
      </div>
    </header>

    <!-- 主内容区 -->
    <main class="mx-auto flex w-full max-w-[840px] flex-1 flex-col px-4 pb-36 pt-6 sm:pt-8">
      <!-- 加载态 -->
      <div v-if="loading" class="card space-y-5 p-8 animate-pulse">
        <div class="h-5 w-40 rounded bg-[var(--bg-subtle)]" />
        <div class="h-8 w-3/4 rounded bg-[var(--bg-subtle)]" />
        <div class="h-32 rounded-lg bg-[var(--bg-subtle)]" />
      </div>

      <!-- 错误态 -->
      <div v-else-if="loadError" class="card my-auto flex flex-col items-center px-6 py-16 text-center">
        <TriangleAlert class="h-10 w-10 text-[#C84B31] dark:text-[#E06246]" />
        <h2 class="mt-4 text-lg font-semibold text-[var(--text-primary)]">加载队列失败</h2>
        <p class="mt-1.5 text-sm text-[var(--text-secondary)]">{{ loadError }}</p>
        <div class="mt-6 flex gap-3">
          <RouterLink :to="{ name: 'dashboard' }" class="btn-ghost">返回仪表盘</RouterLink>
          <button type="button" class="btn-primary" @click="loadQueue">
            <RefreshCw class="h-4 w-4" /> 重试加载
          </button>
        </div>
      </div>

      <!-- 空队列 -->
      <div v-else-if="!total" class="card my-auto flex flex-col items-center px-6 py-16 text-center">
        <span class="flex h-14 w-14 items-center justify-center rounded-xl border border-[#B8DCD1] bg-[#EBF4F1] text-[#235749] dark:border-[#244E41] dark:bg-[#162923] dark:text-[#86CBB7]">
          <PartyPopper class="h-7 w-7" />
        </span>
        <h2 class="mt-5 text-xl font-semibold text-[var(--text-primary)]">当前队列没有待复习的题目</h2>
        <p class="mt-2 max-w-sm text-sm text-[var(--text-secondary)]">
          今天安排的题目已经清空，可以去冲刺 P0 必背题或在题库目录自由挑题巩固。
        </p>
        <div class="mt-7 flex flex-wrap justify-center gap-3">
          <RouterLink :to="{ name: 'dashboard' }" class="btn-ghost">返回仪表盘</RouterLink>
          <RouterLink :to="{ name: 'study', query: { mode: 'p0' } }" class="btn-primary">刷 P0 必背题</RouterLink>
        </div>
      </div>

      <!-- 本轮结算页 -->
      <div v-else-if="finished" class="card my-auto space-y-6 p-7 sm:p-10">
        <div class="text-center">
          <span class="mx-auto flex h-14 w-14 items-center justify-center rounded-xl border border-[#EFC9BF] bg-[#FBF0ED] text-[#C84B31] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#E06246]">
            <PartyPopper class="h-7 w-7" />
          </span>
          <h2 class="mt-4 text-2xl font-semibold text-[var(--text-primary)]">本轮背诵完成</h2>
          <p class="mt-1.5 text-sm text-[var(--text-secondary)]">
            共完成 <strong class="font-mono tabular-nums text-[var(--text-primary)]">{{ totalReviewed }}</strong> 题 · 耗时约
            <strong class="font-mono tabular-nums text-[var(--text-primary)]">{{ elapsedMin }}</strong> 分钟 · 已记录今日学习数据
          </p>

          <div class="mx-auto mt-6 grid max-w-md grid-cols-2 gap-2.5 sm:grid-cols-4">
            <div v-for="btn in ratingButtons" :key="btn.value" class="rounded-lg border p-3" :class="btn.cls">
              <p class="font-mono text-xl font-semibold tabular-nums">{{ stats[btn.value] }}</p>
              <p class="mt-0.5 text-[11px]">{{ btn.label }}</p>
            </div>
          </div>
        </div>

        <!-- 本轮薄弱题复盘清单 -->
        <div v-if="weakQuestions.length" class="rounded-lg border border-[var(--border-default)] bg-[var(--bg-subtle)] p-4">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <p class="text-sm font-semibold text-[var(--text-primary)]">
              本轮薄弱题复盘（{{ weakQuestions.length }} 题记为不会/模糊）
            </p>
            <button type="button" class="btn-primary h-8 px-3 text-xs" @click="retryWeakQuestions">
              <RotateCcw class="h-3.5 w-3.5" /> 立即重刷这 {{ weakQuestions.length }} 题
            </button>
          </div>
          <ul class="mt-3 divide-y divide-[var(--border-default)] rounded-md border border-[var(--border-default)] bg-[var(--bg-surface)]">
            <li v-for="wq in weakQuestions" :key="wq.id" class="flex items-center justify-between gap-3 px-3 py-2 text-xs">
              <div class="flex min-w-0 items-center gap-2">
                <span class="font-mono text-[var(--text-muted)]">{{ wq.code }}</span>
                <span class="truncate font-medium text-[var(--text-primary)]">{{ wq.title }}</span>
              </div>
              <RouterLink
                :to="{ name: 'reader', params: { slug: wq.moduleSlug }, query: { q: String(wq.id) }, hash: `#${wq.id}` }"
                class="shrink-0 text-[#C84B31] hover:underline dark:text-[#E06246]"
              >
                看解析
              </RouterLink>
            </li>
          </ul>
        </div>

        <div class="flex flex-wrap justify-center gap-3">
          <RouterLink :to="{ name: 'dashboard' }" class="btn-primary">返回复习仪表盘</RouterLink>
          <RouterLink :to="{ name: 'study', query: { mode: 'mistakes' } }" class="btn-ghost">复习错题本</RouterLink>
          <button type="button" class="btn-ghost" @click="loadQueue"><RotateCcw class="h-4 w-4" /> 再刷一轮</button>
        </div>
      </div>

      <!-- 闪卡主体 -->
      <div v-else-if="current" class="space-y-4">
        <!-- 撤销上次评分提示条 -->
        <Transition name="fade">
          <div
            v-if="lastRated"
            class="flex items-center justify-between rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] px-3.5 py-2 text-xs text-[var(--text-secondary)]"
          >
            <span>
              上一题已记为 <strong class="text-[var(--text-primary)]">「{{ lastRatingText[lastRated.rating] }}」</strong>
            </span>
            <button
              type="button"
              class="inline-flex items-center gap-1 font-medium text-[#C84B31] hover:underline dark:text-[#E06246]"
              @click="undoLastRating"
            >
              <Undo2 class="h-3.5 w-3.5" /> 撤销重评 <span class="kbd ml-0.5">Z</span>
            </button>
          </div>
        </Transition>

        <article class="card overflow-hidden">
          <!-- 题干区 -->
          <div class="p-5 sm:p-8">
            <div class="flex flex-wrap items-center gap-2 text-xs">
              <span class="font-mono font-semibold text-[var(--text-muted)]">{{ current.code }}</span>
              <PriorityBadge :priority="current.priority" size="md" />
              <span
                v-if="currentModule"
                class="rounded border border-[var(--border-default)] bg-[var(--bg-subtle)] px-2 py-0.5 font-medium text-[var(--text-secondary)]"
              >
                {{ currentModule.title }}
              </span>
              <span v-if="currentChapterTitle" class="hidden truncate text-[var(--text-muted)] sm:inline">
                {{ currentChapterTitle.replace(/^第[一二三四五六七八九十]+(?:篇|部分)[：:]\s*/, '') }}
              </span>

              <span class="ml-auto flex items-center gap-2">
                <StatusDot :status="current.progress.status" />
                <span v-if="current.progress.lastRating" class="hidden text-[var(--text-muted)] sm:inline">
                  · 上次：{{ lastRatingText[current.progress.lastRating] }} ({{ relativeTime(current.progress.lastReviewedAt) }})
                </span>
              </span>
            </div>

            <h1 class="mt-4 text-xl font-semibold leading-snug tracking-tight text-[var(--text-primary)] sm:text-[22px]">
              {{ current.title }}
            </h1>

            <!-- 关键词提示（Hint） -->
            <div v-if="current.hints.length" class="mt-5">
              <button
                type="button"
                class="inline-flex items-center gap-1.5 rounded-lg border border-amber-300/80 bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-900 transition-colors hover:bg-amber-100 dark:border-amber-700/50 dark:bg-amber-950/40 dark:text-amber-200"
                @click="showHint = !showHint"
              >
                <Lightbulb class="h-3.5 w-3.5" />
                {{ showHint ? '收起关键词提示' : '关键词提示（卡壳先看要点）' }}
                <span class="kbd ml-1 hidden sm:inline-flex">H</span>
              </button>

              <Transition name="slide-up">
                <ul
                  v-if="showHint"
                  class="mt-3 space-y-1.5 rounded-lg border border-amber-300/70 bg-amber-50/60 px-4 py-3 text-sm text-amber-950 dark:border-amber-700/40 dark:bg-amber-950/30 dark:text-amber-200"
                >
                  <li v-for="(h, i) in current.hints" :key="i" class="flex items-start gap-2">
                    <span class="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-amber-600 dark:bg-amber-400" />
                    <span>{{ h }}</span>
                  </li>
                </ul>
              </Transition>
            </div>

            <!-- 回忆态指引区（无重复大按钮，统一由底部 Dock 或键盘 Space 触发） -->
            <button
              v-if="!revealed"
              type="button"
              class="mt-7 flex w-full flex-col items-center rounded-xl border border-dashed border-[var(--border-strong)] bg-[var(--bg-subtle)]/70 px-4 py-10 text-center transition-colors hover:border-[#C84B31]/50 hover:bg-[var(--bg-subtle)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
              @click="revealed = true"
            >
              <Eye class="h-5 w-5 text-[#C84B31] dark:text-[#E06246]" />
              <p class="mt-2.5 text-sm font-medium text-[var(--text-primary)]">先尝试在脑中复述要点，点击卡片或按 Space 核对答案</p>
              <p class="mt-1.5 text-xs text-[var(--text-muted)]">
                <template v-if="current.animationKey">本题含交互式原理推演</template>
                <template v-if="current.animationKey && current.images.length"> · </template>
                <template v-if="current.images.length">{{ current.images.length }} 张原理图解</template>
                <template v-if="!current.animationKey && !current.images.length">主动回忆比直接阅读效率高 3 倍</template>
              </p>
            </button>
          </div>

          <!-- 答案展开态 -->
          <Transition name="fade">
            <div v-if="revealed" class="border-t border-[var(--border-default)] bg-[var(--bg-surface)] px-5 pb-7 pt-6 sm:px-8">
              <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
                <span class="text-xs font-semibold text-[var(--text-muted)]">参考解析</span>
                <div class="flex flex-wrap items-center gap-2">
                  <button
                    v-if="current.animationKey"
                    type="button"
                    class="inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1 text-xs font-medium transition-colors"
                    :class="
                      showAnimation
                        ? 'border-[#C84B31] bg-[#C84B31] text-white'
                        : 'border-[#EFC9BF] bg-[#FBF0ED] text-[#96311D] hover:bg-[#F6DDD6] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]'
                    "
                    @click="showAnimation = !showAnimation"
                  >
                    <Clapperboard class="h-3.5 w-3.5" /> {{ showAnimation ? '收起推演动画' : '交互式原理推演' }}
                  </button>
                  <button
                    v-if="current.images.length"
                    type="button"
                    class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)]"
                    @click="openDiagrams"
                  >
                    <ImageIcon class="h-3.5 w-3.5" /> 放大图解 ({{ current.images.length }}) <span class="kbd ml-1 hidden sm:inline-flex">I</span>
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)]"
                    @click="revealed = false"
                  >
                    <EyeOff class="h-3.5 w-3.5" /> 收起答案
                  </button>
                </div>
              </div>

              <div class="mx-auto max-w-[780px]">
                <Transition name="slide-up">
                  <QuestionAnimation v-if="showAnimation && current.animationKey" :animation-key="current.animationKey" class="mb-6" />
                </Transition>
                <MarkdownView :source="current.answerMd" />
              </div>

              <!-- 个人口述助记 / 批注 -->
              <div class="mt-7 rounded-lg border border-[var(--border-default)] bg-[var(--bg-subtle)] p-4">
                <div class="mb-2 flex items-center justify-between">
                  <label for="study-note-input" class="inline-flex items-center gap-1.5 text-xs font-semibold text-[var(--text-primary)]">
                    <NotebookPen class="h-3.5 w-3.5 text-[#C84B31] dark:text-[#E06246]" />
                    我的口述助记 / 面试追问备注
                    <span class="kbd ml-1 hidden sm:inline-flex">N</span>
                  </label>
                  <span v-if="noteSaving" class="text-[11px] text-[var(--text-muted)]">保存中...</span>
                  <span v-else-if="noteSaved" class="inline-flex items-center gap-1 text-[11px] text-[#2E6F5E] dark:text-[#4FA58E]">
                    <Check class="h-3 w-3" /> 已保存
                  </span>
                  <span v-else class="hidden text-[11px] text-[var(--text-muted)] sm:inline">失焦或 Ctrl+Enter 自动保存</span>
                </div>
                <textarea
                  id="study-note-input"
                  ref="noteEl"
                  v-model="noteText"
                  rows="2"
                  class="w-full resize-none rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] px-3 py-2 text-sm leading-relaxed text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-muted)] focus:border-[#C84B31]"
                  placeholder="记一句自己的背诵口诀、易错点或面试官常追问的问题..."
                  @blur="saveNote"
                />
              </div>
            </div>
          </Transition>
        </article>

        <!-- 上一题 / 下一题 轻导航 -->
        <div class="flex items-center justify-between px-1 text-xs text-[var(--text-muted)]">
          <button
            type="button"
            class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 transition-colors hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)] disabled:opacity-35"
            :disabled="index === 0"
            @click="goCard(index - 1)"
          >
            <ChevronLeft class="h-4 w-4" /> 上一题 <span class="kbd ml-1 hidden sm:inline-flex">←</span>
          </button>
          <span class="hidden sm:inline">按 <span class="kbd">?</span> 查看全部快捷键</span>
          <button
            type="button"
            class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 transition-colors hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)] disabled:opacity-35"
            :disabled="index >= total - 1"
            @click="goCard(index + 1)"
          >
            <span class="kbd mr-1 hidden sm:inline-flex">→</span> 跳过下一题 <ChevronRight class="h-4 w-4" />
          </button>
        </div>
      </div>
    </main>

    <!-- 底部固定操作 Dock -->
    <Transition name="slide-up">
      <div
        v-if="current && !finished && !loading && !loadError"
        class="pb-safe fixed inset-x-0 bottom-0 z-30 border-t border-[var(--border-default)] bg-[var(--bg-surface)]/95 backdrop-blur-sm"
      >
        <div class="mx-auto max-w-[840px] px-4 py-3">
          <div v-if="!revealed" class="flex items-center gap-2.5">
            <button
              v-if="current.hints.length"
              type="button"
              class="btn-ghost h-11 shrink-0 px-3.5 text-sm"
              @click="showHint = !showHint"
            >
              <Lightbulb class="h-4 w-4 text-amber-600 dark:text-amber-400" />
              <span class="hidden sm:inline">{{ showHint ? '隐藏提示' : '提示' }}</span>
            </button>
            <button type="button" class="btn-primary h-11 flex-1 text-[15px]" @click="revealed = true">
              <Eye class="h-4 w-4" /> 显示完整答案
              <span class="ml-1 hidden rounded bg-white/20 px-1.5 py-0.5 font-mono text-[11px] sm:inline">Space</span>
            </button>
          </div>

          <div v-else class="grid grid-cols-2 gap-2 sm:grid-cols-4 sm:gap-3">
            <button
              v-for="btn in ratingButtons"
              :key="btn.value"
              type="button"
              class="flex min-h-[48px] flex-col items-center justify-center rounded-lg border px-2 py-2 transition-colors active:scale-[0.99]"
              :class="btn.cls"
              @click="rate(btn.value)"
            >
              <div class="flex items-center gap-1.5 text-xs font-semibold sm:text-sm">
                <span class="hidden rounded border border-current/25 px-1 font-mono text-[10px] sm:inline">{{ btn.key }}</span>
                <span>{{ btn.label }}</span>
              </div>
              <span class="mt-0.5 font-mono text-[11px] opacity-80">{{ previewLabel(current.progress, btn.value) }}</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 快捷键弹窗 -->
    <Transition name="fade">
      <div
        v-if="showShortcuts"
        role="dialog"
        aria-modal="true"
        aria-labelledby="shortcuts-title"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 px-4 backdrop-blur-xs"
        @click.self="showShortcuts = false"
      >
        <div class="card w-full max-w-md p-6">
          <div class="mb-4 flex items-center justify-between">
            <h3 id="shortcuts-title" class="flex items-center gap-2 font-semibold text-[var(--text-primary)]">
              <HelpCircle class="h-4 w-4 text-[#C84B31] dark:text-[#E06246]" /> 闪卡快捷键
            </h3>
            <button
              type="button"
              class="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--text-muted)] hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)]"
              aria-label="关闭快捷键说明"
              @click="showShortcuts = false"
            >
              <X class="h-4 w-4" />
            </button>
          </div>
          <div class="space-y-2.5 text-sm text-[var(--text-secondary)]">
            <div class="flex items-center justify-between"><span>展开 / 收起答案</span><span class="kbd">Space</span></div>
            <div class="flex items-center justify-between"><span>显示 / 隐藏关键词提示</span><span class="kbd">H</span></div>
            <div class="flex items-center justify-between"><span>自评打分并切下一题</span><span><span class="kbd">1</span> <span class="kbd">2</span> <span class="kbd">3</span> <span class="kbd">4</span></span></div>
            <div class="flex items-center justify-between"><span>撤销上一题评分</span><span class="kbd">Z</span></div>
            <div class="flex items-center justify-between"><span>收藏 / 取消收藏</span><span class="kbd">S</span></div>
            <div class="flex items-center justify-between"><span>编辑个人助记备注</span><span class="kbd">N</span></div>
            <div class="flex items-center justify-between"><span>灯箱放大原理图</span><span class="kbd">I</span></div>
            <div class="flex items-center justify-between"><span>切换上一题 / 下一题</span><span><span class="kbd">←</span> <span class="kbd">→</span></span></div>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>
