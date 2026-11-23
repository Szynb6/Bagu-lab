<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft, BookOpen, Check, ChevronLeft, ChevronRight, Clapperboard, Eye, EyeOff, HelpCircle, Image as ImageIcon,
  Keyboard, Lightbulb, Moon, NotebookPen, PartyPopper, RotateCcw, Star, Sun, X,
} from 'lucide-vue-next'
import { knowledgeApi, studyApi } from '@/api'
import { useUiStore } from '@/stores/ui'
import MarkdownView from '@/components/common/MarkdownView.vue'
import QuestionAnimation from '@/components/animations/QuestionAnimation.vue'
import PriorityBadge from '@/components/common/PriorityBadge.vue'
import StatusDot from '@/components/common/StatusDot.vue'
import { previewLabel, schedule } from '@/utils/sm2'
import { relativeTime } from '@/utils/format'
import type { ModuleMeta, QueueMode, QueueRequest, QuestionWithProgress, Rating } from '@/types'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const modules = ref<ModuleMeta[]>([])
const queue = ref<QuestionWithProgress[]>([])
const index = ref(0)
const loading = ref(true)
const revealed = ref(false)
const showHint = ref(false)
const showAnimation = ref(false)
const showShortcuts = ref(false)
const finished = ref(false)

const noteEl = ref<HTMLTextAreaElement>()
const noteText = ref('')
const noteSaving = ref(false)
const noteSaved = ref(false)

// 本轮刷题统计
const sessionStart = ref(Date.now())
const cardStart = ref(Date.now())
const stats = reactive<Record<Rating, number>>({ 1: 0, 2: 0, 3: 0, 4: 0 })

const current = computed(() => queue.value[index.value] ?? null)
const total = computed(() => queue.value.length)
const progressPct = computed(() => (total.value ? Math.round(((finished.value ? total.value : index.value) / total.value) * 100) : 0))

const currentModule = computed(() =>
  current.value ? modules.value.find((m) => m.slug === current.value!.moduleSlug) ?? null : null,
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
    cls: 'border-red-200 bg-red-50/80 text-red-700 hover:bg-red-100 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300 dark:hover:bg-red-500/20',
  },
  {
    value: 2,
    label: '有点模糊',
    key: '2',
    cls: 'border-amber-200 bg-amber-50/80 text-amber-700 hover:bg-amber-100 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-300 dark:hover:bg-amber-500/20',
  },
  {
    value: 3,
    label: '基本掌握',
    key: '3',
    cls: 'border-emerald-200 bg-emerald-50/80 text-emerald-700 hover:bg-emerald-100 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300 dark:hover:bg-emerald-500/20',
  },
  {
    value: 4,
    label: '秒杀跳过',
    key: '4',
    cls: 'border-indigo-200 bg-indigo-50/80 text-indigo-700 hover:bg-indigo-100 dark:border-indigo-500/30 dark:bg-indigo-500/10 dark:text-indigo-300 dark:hover:bg-indigo-500/20',
  },
]

const lastRatingText: Record<Rating, string> = { 1: '完全不会', 2: '有点模糊', 3: '基本掌握', 4: '秒杀' }

async function loadQueue() {
  loading.value = true
  finished.value = false
  index.value = 0
  revealed.value = false
  showHint.value = false
  showAnimation.value = false
  stats[1] = stats[2] = stats[3] = stats[4] = 0
  sessionStart.value = Date.now()
  cardStart.value = Date.now()
  try {
    if (!modules.value.length) modules.value = await knowledgeApi.modules()
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
  // 乐观更新：本地立即计算新进度并丝滑切到下一题
  q.progress = schedule(q.progress, r)
  stats[r] += 1

  studyApi.review(q.id, r, durationSec).catch(() => ui.showToast('同步进度失败，请稍后重试'))

  if (index.value + 1 < total.value) {
    goCard(index.value + 1)
  } else {
    finished.value = true
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

async function toggleStar() {
  const q = current.value
  if (!q) return
  q.progress.isStarred = !q.progress.isStarred
  ui.showToast(q.progress.isStarred ? '已加入收藏 ⭐' : '已取消收藏')
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

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push({ name: 'dashboard' })
}

const totalReviewed = computed(() => stats[1] + stats[2] + stats[3] + stats[4])
const elapsedMin = computed(() => Math.max(1, Math.round((Date.now() - sessionStart.value) / 60000)))

function onKeydown(e: KeyboardEvent) {
  if (ui.searchOpen || ui.lightbox) return
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
    if (e.key === 'Escape') goBack()
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
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <!-- 顶部极简沉浸栏 -->
    <header class="sticky top-0 z-30 border-b border-slate-200 bg-slate-50/85 backdrop-blur dark:border-slate-800 dark:bg-ink-950/85">
      <div class="mx-auto flex h-14 max-w-[920px] items-center gap-3 px-4">
        <button class="inline-flex items-center gap-1.5 rounded-xl px-2.5 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-200/60 dark:text-slate-300 dark:hover:bg-slate-800" @click="goBack">
          <ArrowLeft class="h-4 w-4" />
          <span class="hidden sm:inline">退出</span>
        </button>

        <div class="min-w-0 flex-1 text-center">
          <p class="truncate text-xs font-medium text-slate-500 dark:text-slate-400">
            {{ modeLabel }}
            <template v-if="currentModule && !finished"> · {{ currentModule.title }}</template>
          </p>
          <div v-if="total > 0" class="mx-auto mt-1 flex max-w-xs items-center gap-2">
            <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800">
              <div class="h-full rounded-full bg-brand-600 transition-all duration-300" :style="{ width: `${progressPct}%` }" />
            </div>
            <span class="font-mono text-[11px] tabular-nums text-slate-400">{{ finished ? total : index + 1 }}/{{ total }}</span>
          </div>
        </div>

        <div class="flex items-center gap-1">
          <button
            v-if="current && !finished"
            class="rounded-xl p-2 transition hover:bg-slate-200/60 dark:hover:bg-slate-800"
            :class="current.progress.isStarred ? 'text-amber-400' : 'text-slate-400 hover:text-amber-400'"
            title="收藏 (S)"
            @click="toggleStar"
          >
            <Star class="h-4 w-4" :class="current.progress.isStarred ? 'fill-current' : ''" />
          </button>
          <RouterLink
            v-if="current && !finished"
            :to="{ name: 'reader', params: { slug: current.moduleSlug }, hash: `#${current.id}` }"
            class="hidden rounded-xl p-2 text-slate-400 hover:bg-slate-200/60 hover:text-slate-700 sm:inline-flex dark:hover:bg-slate-800 dark:hover:text-slate-200"
            title="在通读模式中查看"
          >
            <BookOpen class="h-4 w-4" />
          </RouterLink>
          <button
            class="hidden rounded-xl p-2 text-slate-400 hover:bg-slate-200/60 hover:text-slate-700 sm:inline-flex dark:hover:bg-slate-800 dark:hover:text-slate-200"
            title="快捷键说明 (?)"
            @click="showShortcuts = true"
          >
            <Keyboard class="h-4 w-4" />
          </button>
          <button
            class="rounded-xl p-2 text-slate-400 hover:bg-slate-200/60 hover:text-slate-700 dark:hover:bg-slate-800 dark:hover:text-slate-200"
            :title="ui.dark ? '浅色模式' : '深色模式'"
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
        <div class="h-5 w-40 rounded bg-slate-200 dark:bg-slate-800" />
        <div class="h-8 w-3/4 rounded bg-slate-200 dark:bg-slate-800" />
        <div class="h-32 rounded-xl bg-slate-100 dark:bg-slate-800/60" />
      </div>

      <!-- 空队列 -->
      <div v-else-if="!total" class="card my-auto flex flex-col items-center py-16 text-center">
        <span class="flex h-16 w-16 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-600 dark:bg-emerald-500/15 dark:text-emerald-300">
          <PartyPopper class="h-8 w-8" />
        </span>
        <h2 class="mt-5 text-xl font-semibold">当前队列没有待复习的题目啦 🎉</h2>
        <p class="mt-2 max-w-sm text-sm text-slate-500 dark:text-slate-400">
          今天安排的题目已经清空，可以去冲刺 P0 必背题或在题库目录自由挑题巩固。
        </p>
        <div class="mt-7 flex flex-wrap justify-center gap-3">
          <RouterLink :to="{ name: 'dashboard' }" class="btn-ghost">返回仪表盘</RouterLink>
          <RouterLink :to="{ name: 'study', query: { mode: 'p0' } }" class="btn-primary">刷 P0 必背题</RouterLink>
        </div>
      </div>

      <!-- 本轮结算页 -->
      <div v-else-if="finished" class="card my-auto p-7 text-center sm:p-10">
        <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-brand-50 text-brand-600 dark:bg-brand-500/15 dark:text-brand-400">
          <PartyPopper class="h-8 w-8" />
        </span>
        <h2 class="mt-5 text-2xl font-semibold">本轮背诵完成！</h2>
        <p class="mt-1.5 text-sm text-slate-500 dark:text-slate-400">
          共完成 <strong class="tabular-nums text-slate-900 dark:text-white">{{ totalReviewed }}</strong> 题 · 耗时约 {{ elapsedMin }} 分钟 · 已自动记录今日打卡
        </p>

        <div class="mx-auto mt-7 grid max-w-md grid-cols-4 gap-2.5">
          <div v-for="btn in ratingButtons" :key="btn.value" class="rounded-xl border p-3" :class="btn.cls">
            <p class="text-xl font-semibold tabular-nums">{{ stats[btn.value] }}</p>
            <p class="mt-0.5 text-[11px]">{{ btn.label }}</p>
          </div>
        </div>

        <div class="mt-8 flex flex-wrap justify-center gap-3">
          <RouterLink :to="{ name: 'dashboard' }" class="btn-primary">返回复习仪表盘</RouterLink>
          <RouterLink :to="{ name: 'study', query: { mode: 'mistakes' } }" class="btn-ghost">复习错题本</RouterLink>
          <button class="btn-ghost" @click="loadQueue"><RotateCcw class="h-4 w-4" /> 再刷一轮</button>
        </div>
      </div>

      <!-- 闪卡主体 -->
      <div v-else-if="current" class="space-y-4">
        <article class="card overflow-hidden">
          <!-- 题干区 -->
          <div class="p-5 sm:p-8">
            <div class="flex flex-wrap items-center gap-2 text-xs">
              <span class="font-mono font-semibold text-slate-400">{{ current.code }}</span>
              <PriorityBadge :priority="current.priority" size="md" />
              <span v-if="currentModule" class="rounded-full bg-slate-100 px-2.5 py-0.5 font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                {{ currentModule.title }}
              </span>
              <span v-if="currentChapterTitle" class="hidden truncate text-slate-400 sm:inline">
                {{ currentChapterTitle.replace(/^第[一二三四五六七八九十]+(?:篇|部分)[：:]\s*/, '') }}
              </span>

              <span class="ml-auto flex items-center gap-2">
                <StatusDot :status="current.progress.status" />
                <span v-if="current.progress.lastRating" class="hidden text-slate-400 sm:inline">
                  · 上次：{{ lastRatingText[current.progress.lastRating] }} ({{ relativeTime(current.progress.lastReviewedAt) }})
                </span>
              </span>
            </div>

            <h1 class="mt-4 text-xl font-semibold leading-snug tracking-tight text-slate-900 sm:text-[23px] dark:text-white">
              {{ current.title }}
            </h1>

            <!-- 关键词提示（Hint） -->
            <div v-if="current.hints.length" class="mt-5">
              <button
                class="inline-flex items-center gap-1.5 rounded-xl border border-amber-200/80 bg-amber-50/70 px-3 py-1.5 text-xs font-medium text-amber-700 transition hover:bg-amber-100/80 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-300"
                @click="showHint = !showHint"
              >
                <Lightbulb class="h-3.5 w-3.5" />
                {{ showHint ? '收起关键词提示' : '关键词提示（卡壳先偷看要点）' }}
                <span class="kbd ml-1 hidden sm:inline-flex">H</span>
              </button>

              <Transition name="slide-up">
                <ul
                  v-if="showHint"
                  class="mt-3 space-y-1.5 rounded-xl border border-amber-200/70 bg-amber-50/50 px-4 py-3 text-sm text-amber-900 dark:border-amber-500/20 dark:bg-amber-500/10 dark:text-amber-200"
                >
                  <li v-for="(h, i) in current.hints" :key="i" class="flex items-start gap-2">
                    <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-amber-500" />
                    <span>{{ h }}</span>
                  </li>
                </ul>
              </Transition>
            </div>

            <!-- 状态 A：回忆态操作按钮 -->
            <div v-if="!revealed" class="mt-8 flex flex-col items-center rounded-2xl border border-dashed border-slate-200 bg-slate-50/70 px-4 py-10 text-center dark:border-slate-700 dark:bg-slate-800/30">
              <p class="text-sm text-slate-500 dark:text-slate-400">先在脑中尝试口述核心要点，再核对参考答案</p>
              <button class="btn-primary mt-5 h-11 px-7 text-[15px]" @click="revealed = true">
                <Eye class="h-4 w-4" />
                显示完整答案
                <span class="ml-1 hidden rounded bg-white/20 px-1.5 py-0.5 font-mono text-[11px] sm:inline">Space</span>
              </button>
              <p v-if="current.images.length" class="mt-3 text-xs text-slate-400">
                本题包含 {{ current.images.length }} 张高清原理图解
              </p>
            </div>
          </div>

          <!-- 状态 B：答案展开态 -->
          <Transition name="fade">
            <div v-if="revealed" class="border-t border-slate-100 bg-white px-5 pb-7 pt-6 sm:px-8 dark:border-slate-800 dark:bg-slate-900">
              <div class="mb-4 flex items-center justify-between">
                <span class="text-xs font-semibold uppercase tracking-wider text-slate-400">参考答案</span>
                <div class="flex items-center gap-2">
                  <button
                    v-if="current.animationKey"
                    class="inline-flex items-center gap-1 rounded-lg border px-2.5 py-1 text-xs font-medium transition"
                    :class="showAnimation
                      ? 'border-indigo-300 bg-indigo-50 text-indigo-700 dark:border-indigo-500/40 dark:bg-indigo-500/20 dark:text-indigo-200'
                      : 'border-indigo-200 bg-indigo-50/60 text-indigo-600 hover:bg-indigo-100/80 dark:border-indigo-500/30 dark:bg-indigo-500/10 dark:text-indigo-300'"
                    @click="showAnimation = !showAnimation"
                  >
                    <Clapperboard class="h-3.5 w-3.5" /> {{ showAnimation ? '收起动画' : '原理动画' }}
                  </button>
                  <button
                    v-if="current.images.length"
                    class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800"
                    @click="openDiagrams"
                  >
                    <ImageIcon class="h-3.5 w-3.5" /> 放大图解 ({{ current.images.length }}) <span class="kbd ml-1 hidden sm:inline-flex">I</span>
                  </button>
                  <button
                    class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800"
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
              <div class="mt-7 rounded-2xl border border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-950/60">
                <div class="mb-2 flex items-center justify-between">
                  <label class="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-600 dark:text-slate-300">
                    <NotebookPen class="h-3.5 w-3.5 text-brand-500" />
                    我的口述助记 / 面试追问备注
                    <span class="kbd ml-1 hidden sm:inline-flex">N</span>
                  </label>
                  <span v-if="noteSaving" class="text-[11px] text-slate-400">保存中...</span>
                  <span v-else-if="noteSaved" class="inline-flex items-center gap-1 text-[11px] text-emerald-600 dark:text-emerald-400">
                    <Check class="h-3 w-3" /> 已保存
                  </span>
                  <span v-else class="hidden text-[11px] text-slate-400 sm:inline">失焦或 Ctrl+Enter 自动保存</span>
                </div>
                <textarea
                  ref="noteEl"
                  v-model="noteText"
                  rows="2"
                  class="w-full resize-none rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm leading-relaxed outline-none transition focus:border-brand-500 focus:ring-4 focus:ring-brand-500/10 dark:border-slate-700 dark:bg-slate-900"
                  placeholder="记一句自己的背诵口诀、易错点或面试官常追问的问题..."
                  @blur="saveNote"
                />
              </div>
            </div>
          </Transition>
        </article>

        <!-- 上一题 / 下一题 轻导航 -->
        <div class="flex items-center justify-between px-1 text-xs text-slate-400">
          <button
            class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 hover:bg-slate-200/60 hover:text-slate-700 disabled:opacity-35 dark:hover:bg-slate-800 dark:hover:text-slate-200"
            :disabled="index === 0"
            @click="goCard(index - 1)"
          >
            <ChevronLeft class="h-4 w-4" /> 上一题 <span class="kbd ml-1 hidden sm:inline-flex">←</span>
          </button>
          <span class="hidden sm:inline">按 <span class="kbd">?</span> 查看全部快捷键</span>
          <button
            class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 hover:bg-slate-200/60 hover:text-slate-700 disabled:opacity-35 dark:hover:bg-slate-800 dark:hover:text-slate-200"
            :disabled="index >= total - 1"
            @click="goCard(index + 1)"
          >
            <span class="kbd mr-1 hidden sm:inline-flex">→</span> 跳过下一题 <ChevronRight class="h-4 w-4" />
          </button>
        </div>
      </div>
    </main>

    <!-- 底部固定自评 Dock（拇指热区） -->
    <Transition name="slide-up">
      <div
        v-if="current && !finished && !loading"
        class="pb-safe fixed inset-x-0 bottom-0 z-30 border-t border-slate-200 bg-white/92 backdrop-blur-md dark:border-slate-800 dark:bg-slate-900/92"
      >
        <div class="mx-auto max-w-[840px] px-4 py-3">
          <div v-if="!revealed" class="flex items-center gap-2.5">
            <button
              v-if="current.hints.length"
              class="btn-ghost h-11 shrink-0 px-3.5 text-sm"
              @click="showHint = !showHint"
            >
              <Lightbulb class="h-4 w-4 text-amber-500" />
              <span class="hidden sm:inline">{{ showHint ? '隐藏提示' : '提示' }}</span>
            </button>
            <button class="btn-primary h-11 flex-1 text-[15px]" @click="revealed = true">
              <Eye class="h-4 w-4" /> 显示完整答案
            </button>
          </div>

          <div v-else class="grid grid-cols-4 gap-2 sm:gap-3">
            <button
              v-for="btn in ratingButtons"
              :key="btn.value"
              class="flex flex-col items-center justify-center rounded-xl border px-2 py-2.5 transition active:scale-[0.97]"
              :class="btn.cls"
              @click="rate(btn.value)"
            >
              <div class="flex items-center gap-1.5 text-xs font-semibold sm:text-sm">
                <span class="hidden rounded border border-current/25 px-1 font-mono text-[10px] sm:inline">{{ btn.key }}</span>
                <span>{{ btn.label }}</span>
              </div>
              <span class="mt-0.5 text-[11px] opacity-75">{{ previewLabel(current.progress, btn.value) }}</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 快捷键弹窗 -->
    <Transition name="fade">
      <div v-if="showShortcuts" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 px-4 backdrop-blur-sm" @click.self="showShortcuts = false">
        <div class="card w-full max-w-md p-6">
          <div class="mb-4 flex items-center justify-between">
            <h3 class="flex items-center gap-2 font-semibold"><HelpCircle class="h-4 w-4 text-brand-500" /> 闪卡快捷键</h3>
            <button class="rounded-lg p-1 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800" @click="showShortcuts = false">
              <X class="h-4 w-4" />
            </button>
          </div>
          <div class="space-y-2.5 text-sm">
            <div class="flex items-center justify-between"><span>展开 / 收起答案</span><span class="kbd">Space</span></div>
            <div class="flex items-center justify-between"><span>显示 / 隐藏关键词提示</span><span class="kbd">H</span></div>
            <div class="flex items-center justify-between"><span>自评打分并切下一题</span><span><span class="kbd">1</span> <span class="kbd">2</span> <span class="kbd">3</span> <span class="kbd">4</span></span></div>
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
