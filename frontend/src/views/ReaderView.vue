<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChevronDown, Eye, EyeOff, Flame, List, NotebookPen, Play, Sparkles, Star, X, Zap,
} from 'lucide-vue-next'
import { knowledgeApi, studyApi } from '@/api'
import MarkdownView from '@/components/common/MarkdownView.vue'
import PriorityBadge from '@/components/common/PriorityBadge.vue'
import StatusDot from '@/components/common/StatusDot.vue'
import ModuleIcon from '@/components/common/ModuleIcon.vue'
import type { ModuleMeta, QuestionWithProgress } from '@/types'

const route = useRoute()
const router = useRouter()

const modules = ref<ModuleMeta[]>([])
const moduleMeta = ref<ModuleMeta | null>(null)
const questions = ref<QuestionWithProgress[]>([])
const loading = ref(true)

// 考前冲刺双开关
const p0Only = ref(false)
const hideAnswers = ref(false)
const revealedIds = ref(new Set<number>())
const activeId = ref<number | null>(null)
const tocDrawerOpen = ref(false)

const currentSlug = computed(() => (route.params.slug as string) || '01-jvm')

const filteredQuestions = computed(() =>
  p0Only.value ? questions.value.filter((q) => q.priority === 'P0') : questions.value,
)

const groupedChapters = computed(() => {
  if (!moduleMeta.value) return []
  return moduleMeta.value.chapters
    .map((ch) => ({
      ...ch,
      items: filteredQuestions.value.filter((q) => q.chapterId === ch.id),
    }))
    .filter((ch) => ch.items.length > 0)
})

let observer: IntersectionObserver | null = null

function setupObserver() {
  observer?.disconnect()
  observer = new IntersectionObserver(
    (entries) => {
      const visible = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
      if (visible[0]) activeId.value = Number(visible[0].target.id)
    },
    { rootMargin: '-96px 0px -65% 0px', threshold: 0 },
  )
  document.querySelectorAll<HTMLElement>('[data-question-card]').forEach((el) => observer?.observe(el))
}

async function loadModule(slug: string) {
  loading.value = true
  revealedIds.value = new Set()
  localStorage.setItem('bagu:lastSlug', slug)
  try {
    if (!modules.value.length) modules.value = await knowledgeApi.modules()
    const res = await knowledgeApi.reader(slug)
    moduleMeta.value = res.module
    questions.value = res.questions
    activeId.value = res.questions[0]?.id ?? null
    await nextTick()
    setupObserver()
    if (route.hash) {
      const target = document.getElementById(route.hash.slice(1))
      if (target) {
        const id = Number(target.id)
        if (hideAnswers.value && !Number.isNaN(id)) revealedIds.value.add(id)
        target.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => loadModule(currentSlug.value))
watch(currentSlug, (slug) => loadModule(slug))
watch(p0Only, async () => {
  await nextTick()
  setupObserver()
})
onBeforeUnmount(() => observer?.disconnect())

function isAnswerShown(id: number) {
  return !hideAnswers.value || revealedIds.value.has(id)
}

function toggleAnswer(id: number) {
  const next = new Set(revealedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  revealedIds.value = next
}

function scrollToQuestion(id: number) {
  tocDrawerOpen.value = false
  const el = document.getElementById(String(id))
  if (el) {
    const top = el.getBoundingClientRect().top + window.scrollY - 124
    window.scrollTo({ top, behavior: 'smooth' })
    activeId.value = id
  }
}

function switchModule(e: Event) {
  const slug = (e.target as HTMLSelectElement).value
  router.push({ name: 'reader', params: { slug } })
}

async function onToggleStar(q: QuestionWithProgress) {
  q.progress.isStarred = !q.progress.isStarred
  try {
    q.progress.isStarred = await studyApi.toggleStar(q.id)
  } catch {
    q.progress.isStarred = !q.progress.isStarred
  }
}
</script>

<template>
  <div class="space-y-5">
    <!-- 顶部吸顶考前冲刺工具栏 -->
    <div class="sticky top-14 z-20 -mx-4 border-b border-slate-200 bg-slate-50/90 px-4 py-2.5 backdrop-blur lg:top-16 lg:-mx-8 lg:px-8 dark:border-slate-800 dark:bg-ink-950/90">
      <div class="mx-auto flex max-w-[1280px] flex-wrap items-center gap-2.5">
        <div class="relative">
          <select
            :value="currentSlug"
            class="h-9 appearance-none rounded-xl border border-slate-200 bg-white pl-3 pr-8 text-sm font-medium outline-none transition focus:border-brand-500 dark:border-slate-700 dark:bg-slate-900"
            @change="switchModule"
          >
            <option v-for="m in modules" :key="m.slug" :value="m.slug">
              {{ String(m.order).padStart(2, '0') }} {{ m.title }} ({{ m.questionCount }})
            </option>
          </select>
          <ChevronDown class="pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
        </div>

        <!-- 仅看 P0 必背开关 -->
        <button
          class="inline-flex h-9 items-center gap-1.5 rounded-xl border px-3 text-xs font-medium transition"
          :class="p0Only
            ? 'border-red-300 bg-red-50 text-red-600 dark:border-red-500/40 dark:bg-red-500/15 dark:text-red-300'
            : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300'"
          @click="p0Only = !p0Only"
        >
          <Flame class="h-3.5 w-3.5" />
          仅看 P0 必背
          <span v-if="moduleMeta" class="tabular-nums opacity-75">({{ moduleMeta.p0Count }})</span>
        </button>

        <!-- 自测隐藏答案开关 -->
        <button
          class="inline-flex h-9 items-center gap-1.5 rounded-xl border px-3 text-xs font-medium transition"
          :class="hideAnswers
            ? 'border-brand-300 bg-brand-50 text-brand-700 dark:border-brand-500/40 dark:bg-brand-500/15 dark:text-brand-200'
            : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300'"
          @click="hideAnswers = !hideAnswers; revealedIds = new Set()"
        >
          <component :is="hideAnswers ? EyeOff : Eye" class="h-3.5 w-3.5" />
          {{ hideAnswers ? '自测模式（已藏答案）' : '隐藏答案自测' }}
        </button>

        <div class="ml-auto flex items-center gap-2">
          <button class="btn-ghost h-9 px-3 text-xs xl:hidden" @click="tocDrawerOpen = true">
            <List class="h-4 w-4" /> 目录
          </button>
          <RouterLink
            :to="{ name: 'study', query: { mode: 'module', slug: currentSlug } }"
            class="btn-primary h-9 px-3.5 text-xs"
          >
            <Play class="h-3.5 w-3.5 fill-current" /> 刷本篇闪卡
          </RouterLink>
        </div>
      </div>
    </div>

    <!-- 骨架屏 -->
    <div v-if="loading" class="space-y-4 animate-pulse">
      <div class="h-32 rounded-2xl bg-slate-200/70 dark:bg-slate-800/70" />
      <div v-for="i in 3" :key="i" class="h-64 rounded-2xl bg-slate-200/70 dark:bg-slate-800/70" />
    </div>

    <!-- 主体 + 右侧吸顶大纲 -->
    <div v-else-if="moduleMeta" class="flex items-start gap-7">
      <!-- 左侧通读正文 -->
      <div class="min-w-0 max-w-[820px] flex-1 space-y-6">
        <!-- 模块头说明卡 -->
        <section class="card p-5 sm:p-7">
          <div class="flex items-start gap-3.5">
            <ModuleIcon :icon="moduleMeta.icon" />
            <div class="min-w-0 flex-1">
              <h1 class="text-xl font-semibold tracking-tight sm:text-2xl">{{ moduleMeta.fullTitle }}</h1>
              <p v-if="moduleMeta.description" class="mt-2 text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                {{ moduleMeta.description }}
              </p>
            </div>
          </div>

          <div
            v-if="moduleMeta.sprintGuide"
            class="mt-4 flex items-start gap-2.5 rounded-xl border border-amber-200/80 bg-amber-50/70 px-4 py-3 text-xs leading-relaxed text-amber-800 dark:border-amber-500/25 dark:bg-amber-500/10 dark:text-amber-200"
          >
            <Sparkles class="mt-0.5 h-4 w-4 shrink-0 text-amber-500" />
            <span>{{ moduleMeta.sprintGuide }}</span>
          </div>
        </section>

        <!-- 按篇目分组的题目列表 -->
        <section v-for="ch in groupedChapters" :key="ch.id" class="space-y-4">
          <div class="flex items-center gap-3 pt-2">
            <span class="h-5 w-1.5 rounded-full bg-brand-600" />
            <h2 class="text-base font-semibold text-slate-900 sm:text-lg dark:text-white">{{ ch.title }}</h2>
            <span class="shrink-0 text-xs text-slate-400">{{ ch.items.length }} 题</span>
          </div>

          <article
            v-for="q in ch.items"
            :id="String(q.id)"
            :key="q.id"
            data-question-card
            class="card scroll-mt-32 overflow-hidden"
          >
            <div class="flex items-start gap-3 border-b border-slate-100 px-5 py-4 sm:px-7 dark:border-slate-800">
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <span class="font-mono text-xs font-semibold text-brand-600 dark:text-brand-400">{{ q.code }}</span>
                  <PriorityBadge :priority="q.priority" />
                  <StatusDot :status="q.progress.status" />
                </div>
                <h3 class="mt-1.5 text-base font-semibold leading-snug text-slate-900 sm:text-[17px] dark:text-white">
                  {{ q.title }}
                </h3>
              </div>

              <div class="flex shrink-0 items-center gap-1">
                <RouterLink
                  :to="{ name: 'study', query: { mode: 'single', id: q.id } }"
                  class="rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-brand-600 dark:hover:bg-slate-800 dark:hover:text-brand-400"
                  title="以闪卡背诵本题"
                >
                  <Zap class="h-4 w-4" />
                </RouterLink>
                <button
                  class="rounded-lg p-2 transition hover:bg-slate-100 dark:hover:bg-slate-800"
                  :class="q.progress.isStarred ? 'text-amber-400' : 'text-slate-300 hover:text-amber-400 dark:text-slate-600'"
                  title="收藏"
                  @click="onToggleStar(q)"
                >
                  <Star class="h-4 w-4" :class="q.progress.isStarred ? 'fill-current' : ''" />
                </button>
              </div>
            </div>

            <!-- 答案区（支持自测折叠） -->
            <div v-if="isAnswerShown(q.id)" class="px-5 py-5 sm:px-7">
              <MarkdownView :source="q.answerMd" />

              <div
                v-if="q.progress.personalNote"
                class="mt-5 flex items-start gap-2 rounded-xl border border-brand-100 bg-brand-50/50 px-3.5 py-2.5 text-xs leading-relaxed text-slate-700 dark:border-brand-500/20 dark:bg-brand-500/10 dark:text-slate-300"
              >
                <NotebookPen class="mt-0.5 h-3.5 w-3.5 shrink-0 text-brand-500" />
                <span><strong>我的助记：</strong>{{ q.progress.personalNote }}</span>
              </div>

              <div v-if="hideAnswers" class="mt-4 text-right">
                <button class="text-xs text-slate-400 hover:text-slate-600 dark:hover:text-slate-200" @click="toggleAnswer(q.id)">
                  收起答案
                </button>
              </div>
            </div>

            <button
              v-else
              class="flex w-full items-center justify-center gap-2 bg-slate-50/70 py-5 text-sm font-medium text-slate-500 transition hover:bg-brand-50/60 hover:text-brand-600 dark:bg-slate-800/30 dark:text-slate-400 dark:hover:bg-brand-500/10 dark:hover:text-brand-300"
              @click="toggleAnswer(q.id)"
            >
              <Eye class="h-4 w-4" /> 点击展开参考答案自测
            </button>
          </article>
        </section>
      </div>

      <!-- 右侧吸顶大纲 TOC（宽屏） -->
      <aside class="card sticky top-32 hidden w-[264px] shrink-0 overflow-hidden xl:block">
        <div class="flex items-center justify-between border-b border-slate-100 px-4 py-3 dark:border-slate-800">
          <span class="text-xs font-semibold uppercase tracking-wider text-slate-400">本篇目录</span>
          <span class="text-[11px] tabular-nums text-slate-400">{{ filteredQuestions.length }} 题</span>
        </div>
        <nav class="max-h-[calc(100vh-12rem)] space-y-3 overflow-y-auto p-3 text-xs">
          <div v-for="ch in groupedChapters" :key="ch.id">
            <p class="mb-1 truncate px-2 font-semibold text-slate-700 dark:text-slate-200">
              {{ ch.title.replace(/^第[一二三四五六七八九十]+(?:篇|部分)[：:]\s*/, '') }}
            </p>
            <div class="space-y-0.5">
              <button
                v-for="q in ch.items"
                :key="q.id"
                class="flex w-full items-center gap-1.5 rounded-lg px-2 py-1.5 text-left transition"
                :class="activeId === q.id
                  ? 'bg-brand-50 font-medium text-brand-700 dark:bg-brand-500/15 dark:text-brand-200'
                  : 'text-slate-500 hover:bg-slate-100 hover:text-slate-800 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-200'"
                @click="scrollToQuestion(q.id)"
              >
                <span class="h-1.5 w-1.5 shrink-0 rounded-full" :class="q.priority === 'P0' ? 'bg-red-500' : q.priority === 'P1' ? 'bg-blue-500' : 'bg-slate-300'" />
                <span class="shrink-0 font-mono text-[11px] opacity-70">{{ q.code }}</span>
                <span class="truncate">{{ q.title }}</span>
              </button>
            </div>
          </div>
        </nav>
      </aside>
    </div>

    <!-- 移动端 / 中屏目录抽屉 -->
    <Transition name="fade">
      <div v-if="tocDrawerOpen" class="fixed inset-0 z-50 flex justify-end bg-slate-900/40 backdrop-blur-sm xl:hidden" @click.self="tocDrawerOpen = false">
        <div class="flex h-full w-80 max-w-[85vw] flex-col bg-white shadow-2xl dark:bg-slate-900">
          <div class="flex items-center justify-between border-b border-slate-200 px-4 py-3.5 dark:border-slate-800">
            <span class="text-sm font-semibold">本篇目录 ({{ filteredQuestions.length }} 题)</span>
            <button class="rounded-lg p-1 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800" @click="tocDrawerOpen = false">
              <X class="h-5 w-5" />
            </button>
          </div>
          <nav class="flex-1 space-y-4 overflow-y-auto p-3 text-xs">
            <div v-for="ch in groupedChapters" :key="ch.id">
              <p class="mb-1.5 px-2 font-semibold text-slate-700 dark:text-slate-200">{{ ch.title }}</p>
              <div class="space-y-0.5">
                <button
                  v-for="q in ch.items"
                  :key="q.id"
                  class="flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left"
                  :class="activeId === q.id ? 'bg-brand-50 font-medium text-brand-700 dark:bg-brand-500/15 dark:text-brand-200' : 'text-slate-500 dark:text-slate-400'"
                  @click="scrollToQuestion(q.id)"
                >
                  <span class="h-1.5 w-1.5 shrink-0 rounded-full" :class="q.priority === 'P0' ? 'bg-red-500' : q.priority === 'P1' ? 'bg-blue-500' : 'bg-slate-300'" />
                  <span class="shrink-0 font-mono text-[11px]">{{ q.code }}</span>
                  <span class="truncate">{{ q.title }}</span>
                </button>
              </div>
            </div>
          </nav>
        </div>
      </div>
    </Transition>
  </div>
</template>
