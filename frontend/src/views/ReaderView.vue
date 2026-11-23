<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChevronDown, Clapperboard, Eye, EyeOff, Flame, List, NotebookPen, Play, RefreshCw, Star, TriangleAlert, X, Zap,
} from 'lucide-vue-next'
import { knowledgeApi, studyApi } from '@/api'
import { useUiStore } from '@/stores/ui'
import MarkdownView from '@/components/common/MarkdownView.vue'
import QuestionAnimation from '@/components/animations/QuestionAnimation.vue'
import PriorityBadge from '@/components/common/PriorityBadge.vue'
import StatusDot from '@/components/common/StatusDot.vue'
import ModuleIcon from '@/components/common/ModuleIcon.vue'
import type { ModuleMeta, QuestionWithProgress } from '@/types'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const moduleMeta = ref<ModuleMeta | null>(null)
const questions = ref<QuestionWithProgress[]>([])
const loading = ref(true)
const loadError = ref('')

// 阅读控制开关
const p0Only = ref(false)
const hideAnswers = ref(false)
const expandAllAnimations = ref(false)
const revealedIds = ref(new Set<number>())
const expandedAnimIds = ref(new Set<number>())
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

let scrollRaf: number | null = null
let lockActiveUntil = 0
let correctionTimers: number[] = []

function clearCorrectionTimers() {
  correctionTimers.forEach((t) => window.clearTimeout(t))
  correctionTimers = []
}

function getStickyOffset(): number {
  // 顶栏 (56/64px) + 吸顶工具栏 (~54px) + 12px 呼吸间距
  return window.innerWidth >= 1024 ? 130 : 122
}

function updateActiveQuestionFromScroll() {
  if (Date.now() < lockActiveUntil) return
  const cards = Array.from(document.querySelectorAll<HTMLElement>('[data-question-card]'))
  if (!cards.length) return

  const threshold = getStickyOffset() + 36
  let current = cards[0]!
  for (const card of cards) {
    const rect = card.getBoundingClientRect()
    if (rect.top <= threshold) {
      current = card
    } else {
      break
    }
  }
  const id = Number(current.id)
  if (!Number.isNaN(id)) {
    activeId.value = id
  }
}

function onWindowScroll() {
  if (scrollRaf !== null) return
  scrollRaf = window.requestAnimationFrame(() => {
    scrollRaf = null
    updateActiveQuestionFromScroll()
  })
}

function onUserWheelOrTouch() {
  // 用户主动滚轮或触摸滚动时，立即解除目录点击跳转的校准锁
  if (correctionTimers.length) {
    clearCorrectionTimers()
    lockActiveUntil = 0
  }
}

async function loadModule(slug: string) {
  loading.value = true
  loadError.value = ''
  clearCorrectionTimers()
  revealedIds.value = new Set()
  expandedAnimIds.value = new Set()
  ui.setLastSlug(slug)
  try {
    await ui.loadModules().catch(() => {})
    const res = await knowledgeApi.reader(slug)
    moduleMeta.value = res.module
    questions.value = res.questions
    activeId.value = res.questions[0]?.id ?? null
    await nextTick()

    const targetIdStr = (route.query.q as string) || (route.hash ? route.hash.slice(1) : '')
    if (targetIdStr) {
      const id = Number(targetIdStr)
      if (!Number.isNaN(id)) {
        if (hideAnswers.value) revealedIds.value.add(id)
        expandedAnimIds.value.add(id)
        await nextTick()
        scrollToQuestion(id, false)
        return
      }
    }
    updateActiveQuestionFromScroll()
  } catch (e) {
    loadError.value = (e as Error).message || '加载章节内容失败，请检查网络连接'
  } finally {
    loading.value = false
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && tocDrawerOpen.value) {
    tocDrawerOpen.value = false
  }
}

onMounted(() => {
  loadModule(currentSlug.value)
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('scroll', onWindowScroll, { passive: true })
  window.addEventListener('wheel', onUserWheelOrTouch, { passive: true })
  window.addEventListener('touchmove', onUserWheelOrTouch, { passive: true })
})
watch(currentSlug, (slug) => loadModule(slug))
watch(p0Only, async () => {
  await nextTick()
  updateActiveQuestionFromScroll()
})
onBeforeUnmount(() => {
  clearCorrectionTimers()
  if (scrollRaf !== null) window.cancelAnimationFrame(scrollRaf)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('scroll', onWindowScroll)
  window.removeEventListener('wheel', onUserWheelOrTouch)
  window.removeEventListener('touchmove', onUserWheelOrTouch)
})

function isAnswerShown(id: number) {
  return !hideAnswers.value || revealedIds.value.has(id)
}

function toggleAnswer(id: number) {
  const next = new Set(revealedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  revealedIds.value = next
}

function isAnimExpanded(id: number) {
  return expandAllAnimations.value || expandedAnimIds.value.has(id)
}

function toggleAnim(id: number) {
  const next = new Set(expandedAnimIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedAnimIds.value = next
}

function alignElementToTop(id: number, behavior: ScrollBehavior) {
  const el = document.getElementById(String(id))
  if (!el) return
  const offset = getStickyOffset()
  const delta = el.getBoundingClientRect().top - offset
  if (Math.abs(delta) > 4) {
    window.scrollTo({
      top: Math.max(0, window.scrollY + delta),
      behavior,
    })
  }
}

function scrollToQuestion(id: number, smooth = true) {
  tocDrawerOpen.value = false
  clearCorrectionTimers()

  const el = document.getElementById(String(id))
  if (!el) return

  activeId.value = id
  lockActiveUntil = Date.now() + 950

  alignElementToTop(id, smooth ? 'smooth' : 'auto')

  // 滚动途中上方题目若有懒加载图片 (loading="lazy") 展开撑高页面，分阶段自动校准到精确位置
  for (const delay of [180, 380, 620, 880]) {
    const timer = window.setTimeout(() => {
      alignElementToTop(id, 'auto')
      activeId.value = id
    }, delay)
    correctionTimers.push(timer)
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
    <!-- 顶部吸顶手稿工具栏 -->
    <div class="sticky top-14 z-20 -mx-4 border-b border-[var(--border-default)] bg-[var(--bg-canvas)]/92 px-4 py-2.5 backdrop-blur-sm lg:top-16 lg:-mx-8 lg:px-8">
      <div class="mx-auto flex max-w-[1240px] flex-wrap items-center gap-2">
        <div class="relative">
          <select
            :value="currentSlug"
            aria-label="选择知识模块"
            class="h-9 appearance-none rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] pl-3 pr-8 text-sm font-medium text-[var(--text-primary)] outline-none transition-colors focus:border-[#C84B31]"
            @change="switchModule"
          >
            <option v-for="m in ui.modules" :key="m.slug" :value="m.slug">
              {{ String(m.order).padStart(2, '0') }} {{ m.title }} ({{ m.questionCount }})
            </option>
          </select>
          <ChevronDown class="pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--text-muted)]" />
        </div>

        <button
          type="button"
          class="inline-flex h-9 items-center gap-1.5 rounded-lg border px-3 text-xs font-medium transition-colors"
          :class="
            p0Only
              ? 'border-[#EFC9BF] bg-[#FBF0ED] text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]'
              : 'border-[var(--border-default)] bg-[var(--bg-surface)] text-[var(--text-secondary)] hover:bg-[var(--bg-subtle)]'
          "
          @click="p0Only = !p0Only"
        >
          <Flame class="h-3.5 w-3.5" />
          仅看 P0 必背
          <span v-if="moduleMeta" class="font-mono tabular-nums opacity-75">({{ moduleMeta.p0Count }})</span>
        </button>

        <button
          type="button"
          class="inline-flex h-9 items-center gap-1.5 rounded-lg border px-3 text-xs font-medium transition-colors"
          :class="
            hideAnswers
              ? 'border-[#C84B31] bg-[#C84B31] text-white'
              : 'border-[var(--border-default)] bg-[var(--bg-surface)] text-[var(--text-secondary)] hover:bg-[var(--bg-subtle)]'
          "
          @click="hideAnswers = !hideAnswers; revealedIds = new Set()"
        >
          <component :is="hideAnswers ? EyeOff : Eye" class="h-3.5 w-3.5" />
          {{ hideAnswers ? '自测模式（已藏答案）' : '隐藏答案自测' }}
        </button>

        <button
          type="button"
          class="hidden h-9 items-center gap-1.5 rounded-lg border px-3 text-xs font-medium transition-colors sm:inline-flex"
          :class="
            expandAllAnimations
              ? 'border-[#EFC9BF] bg-[#FBF0ED] text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]'
              : 'border-[var(--border-default)] bg-[var(--bg-surface)] text-[var(--text-secondary)] hover:bg-[var(--bg-subtle)]'
          "
          @click="expandAllAnimations = !expandAllAnimations"
        >
          <Clapperboard class="h-3.5 w-3.5" />
          {{ expandAllAnimations ? '收起全部推演' : '展开全部动画' }}
        </button>

        <div class="ml-auto flex items-center gap-2">
          <button
            type="button"
            class="btn-ghost h-9 px-3 text-xs xl:hidden"
            aria-label="打开本篇章节目录"
            @click="tocDrawerOpen = true"
          >
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
      <div class="h-28 rounded-xl border border-[var(--border-default)] bg-[var(--bg-surface)]" />
      <div class="h-96 rounded-xl border border-[var(--border-default)] bg-[var(--bg-surface)]" />
    </div>

    <!-- 错误态 -->
    <div v-else-if="loadError" class="card flex flex-col items-center justify-center px-6 py-16 text-center">
      <TriangleAlert class="h-9 w-9 text-[#C84B31] dark:text-[#E06246]" />
      <p class="mt-3 text-sm font-medium text-[var(--text-primary)]">{{ loadError }}</p>
      <button type="button" class="btn-primary mt-4 h-9 text-xs" @click="loadModule(currentSlug)">
        <RefreshCw class="h-3.5 w-3.5" /> 重试加载
      </button>
    </div>

    <!-- 工程手稿连续正文 + 右侧吸顶目录 -->
    <div v-else-if="moduleMeta" class="flex items-start gap-7">
      <div class="min-w-0 max-w-[820px] flex-1 space-y-6">
        <!-- 卷首导读卡 -->
        <section class="card p-5 sm:p-7">
          <div class="flex items-start gap-3.5">
            <ModuleIcon :icon="moduleMeta.icon" :slug="moduleMeta.slug" :order="moduleMeta.order" active />
            <div class="min-w-0 flex-1">
              <h1 class="text-xl font-semibold tracking-tight text-[var(--text-primary)] sm:text-2xl">{{ moduleMeta.fullTitle }}</h1>
              <p v-if="moduleMeta.description" class="mt-2 text-sm leading-relaxed text-[var(--text-secondary)]">
                {{ moduleMeta.description }}
              </p>
            </div>
          </div>

          <div
            v-if="moduleMeta.sprintGuide"
            class="mt-4 rounded-lg border border-amber-300/80 bg-amber-50/70 px-4 py-3 text-xs leading-relaxed text-amber-950 dark:border-amber-700/40 dark:bg-amber-950/30 dark:text-amber-200"
          >
            <strong class="mr-1.5 font-semibold">考前冲刺建议：</strong>{{ moduleMeta.sprintGuide }}
          </div>
        </section>

        <!-- 按篇目分组的连续手稿纸面（无卡片嵌套） -->
        <section v-for="ch in groupedChapters" :key="ch.id" class="space-y-3">
          <div class="flex items-center justify-between border-b border-[var(--border-strong)] pb-2 pt-2">
            <h2 class="text-base font-semibold text-[var(--text-primary)] sm:text-lg">{{ ch.title }}</h2>
            <span class="font-mono text-xs tabular-nums text-[var(--text-muted)]">{{ ch.items.length }} 题</span>
          </div>

          <div class="card divide-y divide-[var(--border-default)] overflow-hidden">
            <article
              v-for="q in ch.items"
              :id="String(q.id)"
              :key="q.id"
              data-question-card
              class="scroll-mt-32 p-5 sm:p-7"
            >
              <!-- 题头元信息 -->
              <div class="flex items-start gap-3">
                <div class="min-w-0 flex-1">
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="font-mono text-xs font-semibold text-[#C84B31] dark:text-[#E06246]">§ {{ q.code }}</span>
                    <PriorityBadge :priority="q.priority" />
                    <StatusDot :status="q.progress.status" />
                  </div>
                  <h3 class="mt-1.5 text-base font-semibold leading-snug text-[var(--text-primary)] sm:text-[17px]">
                    {{ q.title }}
                  </h3>
                </div>

                <div class="flex shrink-0 items-center gap-1">
                  <RouterLink
                    :to="{ name: 'study', query: { mode: 'single', id: q.id } }"
                    class="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-subtle)] hover:text-[#C84B31] dark:hover:text-[#E06246]"
                    title="以闪卡背诵本题"
                    aria-label="以闪卡背诵本题"
                  >
                    <Zap class="h-4 w-4" />
                  </RouterLink>
                  <button
                    type="button"
                    class="flex h-8 w-8 items-center justify-center rounded-lg transition-colors hover:bg-[var(--bg-subtle)]"
                    :class="q.progress.isStarred ? 'text-amber-500' : 'text-[var(--text-muted)] hover:text-amber-500'"
                    :title="q.progress.isStarred ? '取消收藏' : '收藏'"
                    :aria-label="q.progress.isStarred ? '取消收藏本题' : '收藏本题'"
                    @click="onToggleStar(q)"
                  >
                    <Star class="h-4 w-4" :class="q.progress.isStarred ? 'fill-current' : ''" />
                  </button>
                </div>
              </div>

              <!-- 答案正文区（支持自测折叠） -->
              <div v-if="isAnswerShown(q.id)" class="mt-4">
                <!-- 按需展开交互式原理推演（避免 18+ SVG 同时挂载阻塞页面） -->
                <div v-if="q.animationKey" class="mb-5">
                  <button
                    type="button"
                    class="flex w-full items-center justify-between rounded-lg border px-3.5 py-2.5 text-left text-xs font-medium transition-colors"
                    :class="
                      isAnimExpanded(q.id)
                        ? 'border-[#EFC9BF] bg-[#FBF0ED] text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]'
                        : 'border-[var(--border-default)] bg-[var(--bg-subtle)] text-[var(--text-primary)] hover:border-[var(--border-strong)]'
                    "
                    :aria-expanded="isAnimExpanded(q.id)"
                    @click="toggleAnim(q.id)"
                  >
                    <span class="inline-flex items-center gap-2">
                      <Clapperboard class="h-4 w-4 text-[#C84B31] dark:text-[#E06246]" />
                      <span>交互式原理推演</span>
                    </span>
                    <span class="font-mono text-[11px] text-[var(--text-muted)]">
                      {{ isAnimExpanded(q.id) ? '收起动画 ▲' : '点击展开动画 ▼' }}
                    </span>
                  </button>

                  <div v-if="isAnimExpanded(q.id)" class="mt-2.5">
                    <QuestionAnimation :animation-key="q.animationKey" />
                  </div>
                </div>

                <MarkdownView :source="q.answerMd" />

                <div
                  v-if="q.progress.personalNote"
                  class="mt-5 flex items-start gap-2 rounded-lg border border-[#EFC9BF] bg-[#FBF0ED]/65 px-3.5 py-2.5 text-xs leading-relaxed text-[var(--text-primary)] dark:border-[#572C22] dark:bg-[#2E1B16]/60"
                >
                  <NotebookPen class="mt-0.5 h-3.5 w-3.5 shrink-0 text-[#C84B31] dark:text-[#E06246]" />
                  <span><strong>我的助记：</strong>{{ q.progress.personalNote }}</span>
                </div>

                <div v-if="hideAnswers" class="mt-4 text-right">
                  <button
                    type="button"
                    class="text-xs text-[var(--text-muted)] hover:text-[var(--text-primary)]"
                    @click="toggleAnswer(q.id)"
                  >
                    收起答案
                  </button>
                </div>
              </div>

              <button
                v-else
                type="button"
                class="mt-4 flex w-full items-center justify-center gap-2 rounded-lg border border-dashed border-[var(--border-strong)] bg-[var(--bg-subtle)]/70 py-5 text-sm font-medium text-[var(--text-secondary)] transition-colors hover:border-[#C84B31]/50 hover:text-[#C84B31] dark:hover:text-[#E06246]"
                @click="toggleAnswer(q.id)"
              >
                <Eye class="h-4 w-4" /> 点击展开参考答案自测
              </button>
            </article>
          </div>
        </section>
      </div>

      <!-- 右侧吸顶大纲 TOC（宽屏） -->
      <aside class="card sticky top-32 hidden w-[264px] shrink-0 overflow-hidden xl:block">
        <div class="flex items-center justify-between border-b border-[var(--border-default)] px-4 py-3">
          <span class="text-xs font-semibold text-[var(--text-primary)]">本篇目录</span>
          <span class="font-mono text-[11px] tabular-nums text-[var(--text-muted)]">{{ filteredQuestions.length }} 题</span>
        </div>
        <nav class="max-h-[calc(100vh-12rem)] space-y-3 overflow-y-auto p-3 text-xs" aria-label="章节大纲">
          <div v-for="ch in groupedChapters" :key="ch.id">
            <p class="mb-1 truncate px-2 font-semibold text-[var(--text-primary)]">
              {{ ch.title.replace(/^第[一二三四五六七八九十]+(?:篇|部分)[：:]\s*/, '') }}
            </p>
            <div class="space-y-0.5">
              <button
                v-for="q in ch.items"
                :key="q.id"
                type="button"
                class="flex w-full items-center gap-1.5 rounded-md border px-2 py-1.5 text-left transition-colors"
                :class="
                  activeId === q.id
                    ? 'border-[var(--border-default)] bg-[var(--bg-subtle)] font-medium text-[#C84B31] dark:text-[#E06246]'
                    : 'border-transparent text-[var(--text-secondary)] hover:bg-[var(--bg-subtle)]/60 hover:text-[var(--text-primary)]'
                "
                @click="scrollToQuestion(q.id)"
              >
                <span
                  class="h-1.5 w-1.5 shrink-0 rounded-full"
                  :class="q.priority === 'P0' ? 'bg-[#C84B31] dark:bg-[#E06246]' : q.priority === 'P1' ? 'bg-amber-600 dark:bg-amber-400' : 'bg-[#B0AAA0]'"
                />
                <span class="shrink-0 font-mono text-[11px] opacity-75">{{ q.code }}</span>
                <span class="truncate">{{ q.title }}</span>
              </button>
            </div>
          </div>
        </nav>
      </aside>
    </div>

    <!-- 移动端 / 中屏目录抽屉 -->
    <Transition name="fade">
      <div
        v-if="tocDrawerOpen"
        role="dialog"
        aria-modal="true"
        aria-label="本篇章节目录"
        class="fixed inset-0 z-50 flex justify-end bg-black/45 backdrop-blur-xs xl:hidden"
        @click.self="tocDrawerOpen = false"
      >
        <div class="flex h-full w-80 max-w-[85vw] flex-col border-l border-[var(--border-default)] bg-[var(--bg-surface)]">
          <div class="flex items-center justify-between border-b border-[var(--border-default)] px-4 py-3.5">
            <span class="text-sm font-semibold text-[var(--text-primary)]">本篇目录 ({{ filteredQuestions.length }} 题)</span>
            <button
              type="button"
              class="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--text-muted)] hover:bg-[var(--bg-subtle)]"
              aria-label="关闭目录抽屉"
              @click="tocDrawerOpen = false"
            >
              <X class="h-5 w-5" />
            </button>
          </div>
          <nav class="flex-1 space-y-4 overflow-y-auto p-3 text-xs">
            <div v-for="ch in groupedChapters" :key="ch.id">
              <p class="mb-1.5 px-2 font-semibold text-[var(--text-primary)]">{{ ch.title }}</p>
              <div class="space-y-0.5">
                <button
                  v-for="q in ch.items"
                  :key="q.id"
                  type="button"
                  class="flex w-full items-center gap-2 rounded-md border px-2 py-2 text-left"
                  :class="
                    activeId === q.id
                      ? 'border-[var(--border-default)] bg-[var(--bg-subtle)] font-medium text-[#C84B31] dark:text-[#E06246]'
                      : 'border-transparent text-[var(--text-secondary)]'
                  "
                  @click="scrollToQuestion(q.id)"
                >
                  <span
                    class="h-1.5 w-1.5 shrink-0 rounded-full"
                    :class="q.priority === 'P0' ? 'bg-[#C84B31]' : q.priority === 'P1' ? 'bg-amber-600' : 'bg-[#B0AAA0]'"
                  />
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
