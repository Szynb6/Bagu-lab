<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight, BookOpen, CheckCircle2, Flame, Play, RefreshCw, Star, Target, TriangleAlert, Zap,
} from 'lucide-vue-next'
import { studyApi } from '@/api'
import ModuleIcon from '@/components/common/ModuleIcon.vue'
import CheckinHeatmap from '@/components/common/CheckinHeatmap.vue'
import type { DashboardData } from '@/types'

const router = useRouter()
const data = ref<DashboardData | null>(null)
const loading = ref(true)
const loadError = ref('')

async function fetchDashboard() {
  loading.value = true
  loadError.value = ''
  try {
    data.value = await studyApi.dashboard()
  } catch (e) {
    loadError.value = (e as Error).message || '加载仪表盘数据失败，请检查网络连接'
  } finally {
    loading.value = false
  }
}

onMounted(fetchDashboard)

const todayTotal = computed(() => (data.value ? data.value.dueCount + data.value.newQuota : 0))
const goalPct = computed(() => {
  if (!data.value || !data.value.dailyGoal) return 0
  return Math.min(100, Math.round((data.value.doneToday / data.value.dailyGoal) * 100))
})
const totalPct = computed(() =>
  data.value?.totalQuestions ? Math.round((data.value.totalMastered / data.value.totalQuestions) * 100) : 0,
)
const p0Pct = computed(() =>
  data.value?.p0Total ? Math.round((data.value.p0Mastered / data.value.p0Total) * 100) : 0,
)

const priorityBreakdown = computed(() => {
  if (!data.value) return []
  const mods = data.value.modules
  const p0 = mods.reduce((s, m) => s + m.p0Count, 0)
  const p1 = mods.reduce((s, m) => s + m.p1Count, 0)
  const p2 = mods.reduce((s, m) => s + m.p2Count, 0)
  const total = p0 + p1 + p2 || 1
  return [
    { key: 'P0', label: 'P0 必背核心', count: p0, pct: Math.round((p0 / total) * 100), bar: 'bg-[#C84B31] dark:bg-[#E06246]', desc: '高频必考手写/原理' },
    { key: 'P1', label: 'P1 高频常问', count: p1, pct: Math.round((p1 / total) * 100), bar: 'bg-amber-600 dark:bg-amber-400', desc: '深挖源码与选型对比' },
    { key: 'P2', label: 'P2 拓展了解', count: p2, pct: Math.round((p2 / total) * 100), bar: 'bg-[#8A8478]', desc: '边界场景与加分视野' },
  ] as const
})

function pct(a: number, b: number) {
  return b ? Math.round((a / b) * 100) : 0
}
</script>

<template>
  <!-- 骨架屏 -->
  <div v-if="loading" class="space-y-6 animate-pulse">
    <div class="h-36 rounded-xl border border-[var(--border-default)] bg-[var(--bg-surface)]" />
    <div class="h-28 rounded-xl border border-[var(--border-default)] bg-[var(--bg-surface)]" />
    <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <div v-for="i in 6" :key="i" class="h-44 rounded-xl border border-[var(--border-default)] bg-[var(--bg-surface)]" />
    </div>
  </div>

  <!-- 错误态 -->
  <div v-else-if="loadError" class="card flex flex-col items-center justify-center px-6 py-16 text-center">
    <TriangleAlert class="h-9 w-9 text-[#C84B31] dark:text-[#E06246]" />
    <h2 class="mt-3 text-base font-semibold text-[var(--text-primary)]">学习仪表盘暂未加载</h2>
    <p class="mt-1 text-sm text-[var(--text-secondary)]">{{ loadError }}</p>
    <button type="button" class="btn-primary mt-5" @click="fetchDashboard">
      <RefreshCw class="h-4 w-4" /> 重新加载
    </button>
  </div>

  <div v-else-if="data" class="space-y-6">
    <!-- 1. 今日学习调度台（简洁高精度工程台面） -->
    <section class="card p-5 sm:p-7">
      <div class="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
        <div class="space-y-2.5">
          <div class="flex flex-wrap items-center gap-2 text-xs font-medium text-[var(--text-secondary)]">
            <span class="inline-flex items-center gap-1.5 rounded border border-[#EFC9BF] bg-[#FBF0ED] px-2 py-0.5 font-mono text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]">
              SM-2 调度队列
            </span>
            <span class="font-mono tabular-nums text-[var(--text-muted)]">
              已掌握 {{ totalPct }}% · P0 核心 {{ p0Pct }}%
            </span>
          </div>

          <h2 class="text-xl font-semibold tracking-tight text-[var(--text-primary)] sm:text-2xl">
            <template v-if="todayTotal > 0">
              今日待复习
              <span class="font-mono tabular-nums text-[#C84B31] dark:text-[#E06246]">{{ data.dueCount }}</span>
              题 · 新题建议
              <span class="font-mono tabular-nums text-[var(--text-primary)]">{{ data.newQuota }}</span>
              题
            </template>
            <template v-else>今日 SM-2 到期队列已全部清零</template>
          </h2>

          <div class="flex flex-wrap items-center gap-3 pt-0.5 text-sm text-[var(--text-secondary)]">
            <span>
              今日目标完成
              <strong class="font-mono tabular-nums text-[var(--text-primary)]">{{ data.doneToday }}</strong>
              / <span class="font-mono tabular-nums">{{ data.dailyGoal }}</span> 题
            </span>
            <div class="h-1.5 w-36 overflow-hidden rounded-full bg-[var(--bg-subtle)]">
              <div
                class="h-full rounded-full bg-[#C84B31] transition-[width] duration-300 dark:bg-[#E06246]"
                :style="{ width: `${goalPct}%` }"
              />
            </div>
            <span class="font-mono text-xs tabular-nums text-[var(--text-muted)]">{{ goalPct }}%</span>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-2.5">
          <RouterLink :to="{ name: 'study', query: { mode: 'today' } }" class="btn-primary h-10 px-4">
            <Play class="h-4 w-4 fill-current" />
            开始今日复习
            <span v-if="todayTotal > 0" class="font-mono text-xs opacity-90">({{ todayTotal }})</span>
          </RouterLink>
          <RouterLink :to="{ name: 'study', query: { mode: 'p0' } }" class="btn-ghost h-10 px-4">
            <Target class="h-4 w-4 text-[#C84B31] dark:text-[#E06246]" />
            只刷 P0 必背
          </RouterLink>
        </div>
      </div>
    </section>

    <!-- 2. 四栏细线指标账本 -->
    <section class="card grid grid-cols-2 divide-y divide-[var(--border-default)] overflow-hidden lg:grid-cols-4 lg:divide-x lg:divide-y-0">
      <div class="p-4 sm:p-5">
        <div class="flex items-center justify-between text-xs text-[var(--text-secondary)]">
          <span>总题库掌握度</span>
          <CheckCircle2 class="h-4 w-4 text-[#2E6F5E] dark:text-[#4FA58E]" />
        </div>
        <div class="mt-2 flex items-baseline gap-1.5">
          <span class="font-mono text-2xl font-semibold tabular-nums text-[var(--text-primary)]">{{ data.totalMastered }}</span>
          <span class="font-mono text-sm tabular-nums text-[var(--text-muted)]">/ {{ data.totalQuestions }}</span>
          <span class="ml-auto font-mono text-xs font-medium tabular-nums text-[#2E6F5E] dark:text-[#4FA58E]">{{ totalPct }}%</span>
        </div>
        <div class="mt-2.5 h-1 overflow-hidden rounded-full bg-[var(--bg-subtle)]">
          <div class="h-full bg-[#2E6F5E] transition-[width] duration-300 dark:bg-[#4FA58E]" :style="{ width: `${totalPct}%` }" />
        </div>
        <p class="mt-2 text-xs text-[var(--text-muted)]">累计已学习 {{ data.totalLearned }} 题</p>
      </div>

      <button
        type="button"
        class="p-4 text-left transition-colors hover:bg-[var(--bg-subtle)]/60 focus-visible:outline-2 focus-visible:outline-inset focus-visible:outline-brand-500 sm:p-5"
        @click="router.push({ name: 'study', query: { mode: 'p0' } })"
      >
        <div class="flex items-center justify-between text-xs text-[var(--text-secondary)]">
          <span>P0 必背攻坚</span>
          <Target class="h-4 w-4 text-[#C84B31] dark:text-[#E06246]" />
        </div>
        <div class="mt-2 flex items-baseline gap-1.5">
          <span class="font-mono text-2xl font-semibold tabular-nums text-[var(--text-primary)]">{{ data.p0Mastered }}</span>
          <span class="font-mono text-sm tabular-nums text-[var(--text-muted)]">/ {{ data.p0Total }}</span>
          <span class="ml-auto font-mono text-xs font-medium tabular-nums text-[#C84B31] dark:text-[#E06246]">{{ p0Pct }}%</span>
        </div>
        <div class="mt-2.5 h-1 overflow-hidden rounded-full bg-[var(--bg-subtle)]">
          <div class="h-full bg-[#C84B31] transition-[width] duration-300 dark:bg-[#E06246]" :style="{ width: `${p0Pct}%` }" />
        </div>
        <p class="mt-2 text-xs text-[var(--text-muted)]">考前核心必背，点击开刷 →</p>
      </button>

      <div class="p-4 sm:p-5">
        <div class="flex items-center justify-between text-xs text-[var(--text-secondary)]">
          <span>连续打卡</span>
          <Flame class="h-4 w-4 text-amber-600 dark:text-amber-400" />
        </div>
        <div class="mt-2 flex items-baseline gap-1.5">
          <span class="font-mono text-2xl font-semibold tabular-nums text-[var(--text-primary)]">{{ data.streak }}</span>
          <span class="text-sm text-[var(--text-muted)]">天</span>
        </div>
        <p class="mt-4 text-xs text-[var(--text-muted)]">本月累计活跃 {{ data.checkinDays }} 天</p>
      </div>

      <button
        type="button"
        class="p-4 text-left transition-colors hover:bg-[var(--bg-subtle)]/60 focus-visible:outline-2 focus-visible:outline-inset focus-visible:outline-brand-500 sm:p-5"
        @click="router.push({ name: 'study', query: { mode: 'mistakes' } })"
      >
        <div class="flex items-center justify-between text-xs text-[var(--text-secondary)]">
          <span>待巩固错题</span>
          <TriangleAlert class="h-4 w-4 text-amber-600 dark:text-amber-400" />
        </div>
        <div class="mt-2 flex items-baseline gap-1.5">
          <span class="font-mono text-2xl font-semibold tabular-nums text-[var(--text-primary)]">{{ data.mistakeCount }}</span>
          <span class="text-sm text-[var(--text-muted)]">题</span>
        </div>
        <p class="mt-4 text-xs text-[var(--text-muted)]">不会 / 模糊题汇总，点击专练 →</p>
      </button>
    </section>

    <!-- 3. 九大核心知识模块矩阵（克制操作层级，避免重复主按钮） -->
    <section>
      <div class="mb-3.5 flex flex-wrap items-end justify-between gap-2">
        <div>
          <h2 class="text-base font-semibold tracking-tight text-[var(--text-primary)]">九大核心模块掌握度</h2>
          <p class="mt-0.5 text-xs text-[var(--text-secondary)]">
            共 {{ data.totalQuestions }} 道面试真题，按考频与原理图解编排
          </p>
        </div>
        <RouterLink
          :to="{ name: 'bank' }"
          class="inline-flex items-center gap-1 text-sm font-medium text-[#C84B31] transition-colors hover:text-[#96311D] dark:text-[#E06246]"
        >
          进入全量题库 <ArrowRight class="h-4 w-4" />
        </RouterLink>
      </div>

      <div class="grid gap-3.5 md:grid-cols-2 xl:grid-cols-3">
        <article
          v-for="m in data.modules"
          :key="m.slug"
          class="card flex flex-col p-5 transition-colors hover:border-[var(--border-strong)]"
        >
          <div class="flex items-start gap-3">
            <ModuleIcon :icon="m.icon" :slug="m.slug" :order="m.order" :active="m.due > 0" />
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <h3 class="truncate font-semibold text-[var(--text-primary)]">{{ m.title }}</h3>
                <span
                  v-if="m.due > 0"
                  class="ml-auto shrink-0 rounded border border-[#EFC9BF] bg-[#FBF0ED] px-1.5 py-0.5 font-mono text-[11px] font-medium tabular-nums text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]"
                >
                  {{ m.due }} 到期
                </span>
              </div>
              <p class="mt-0.5 font-mono text-xs tabular-nums text-[var(--text-muted)]">
                {{ m.questionCount }} 题 · P0 {{ m.p0Count }}
                <template v-if="m.imageCount"> · {{ m.imageCount }} 图</template>
              </p>
            </div>
          </div>

          <div class="mt-4 space-y-2.5">
            <div>
              <div class="mb-1 flex items-center justify-between text-xs">
                <span class="text-[var(--text-secondary)]">总掌握</span>
                <span class="font-mono font-medium tabular-nums text-[var(--text-primary)]">
                  {{ m.mastered }}/{{ m.questionCount }} ({{ pct(m.mastered, m.questionCount) }}%)
                </span>
              </div>
              <div class="h-1.5 overflow-hidden rounded-full bg-[var(--bg-subtle)]">
                <div
                  class="h-full rounded-full bg-[#2E6F5E] transition-[width] duration-300 dark:bg-[#4FA58E]"
                  :style="{ width: `${pct(m.mastered, m.questionCount)}%` }"
                />
              </div>
            </div>

            <div class="flex items-center justify-between text-xs">
              <span class="text-[var(--text-secondary)]">P0 必背</span>
              <span class="font-mono font-medium tabular-nums text-[#96311D] dark:text-[#EFC0B3]">
                {{ m.p0Mastered }} / {{ m.p0Count }}
              </span>
            </div>
          </div>

          <div class="mt-4 flex items-center justify-between gap-2 border-t border-[var(--border-default)] pt-3 text-xs">
            <RouterLink
              :to="{ name: 'reader', params: { slug: m.slug } }"
              class="inline-flex items-center gap-1.5 rounded-md px-2 py-1 font-medium text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)]"
            >
              <BookOpen class="h-3.5 w-3.5" /> 沉浸通读
            </RouterLink>
            <RouterLink
              :to="{ name: 'study', query: { mode: 'module', slug: m.slug } }"
              class="inline-flex items-center gap-1 rounded-md px-2.5 py-1 font-medium text-[#C84B31] transition-colors hover:bg-[#FBF0ED] dark:text-[#E06246] dark:hover:bg-[#2E1B16]"
            >
              <Zap class="h-3.5 w-3.5" /> 专项闪卡 →
            </RouterLink>
          </div>
        </article>
      </div>
    </section>

    <!-- 4. 底部双栏：12 周打卡热力图 + 考频结构分布 -->
    <section class="grid gap-4 lg:grid-cols-12">
      <div class="card p-5 sm:p-6 lg:col-span-7">
        <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
          <div>
            <h2 class="text-base font-semibold tracking-tight text-[var(--text-primary)]">每日背诵热力图</h2>
            <p class="mt-0.5 text-xs text-[var(--text-secondary)]">近 12 周刷题节奏与复习密度分布</p>
          </div>
          <div class="flex items-center gap-2">
            <RouterLink :to="{ name: 'study', query: { mode: 'mistakes' } }" class="btn-ghost h-8 px-2.5 text-xs">
              <TriangleAlert class="h-3.5 w-3.5 text-amber-600 dark:text-amber-400" />
              错题 ({{ data.mistakeCount }})
            </RouterLink>
            <RouterLink :to="{ name: 'bank', query: { status: 'starred' } }" class="btn-ghost h-8 px-2.5 text-xs">
              <Star class="h-3.5 w-3.5 text-amber-500" />
              收藏夹
            </RouterLink>
          </div>
        </div>
        <CheckinHeatmap :data="data.heatmap" />
      </div>

      <div class="card flex flex-col justify-between p-5 sm:p-6 lg:col-span-5">
        <div>
          <h2 class="text-base font-semibold tracking-tight text-[var(--text-primary)]">考频分级结构</h2>
          <p class="mt-0.5 text-xs text-[var(--text-secondary)]">按实际面试命中频率划分复习重心</p>

          <div class="mt-5 space-y-4">
            <RouterLink
              v-for="row in priorityBreakdown"
              :key="row.key"
              :to="{ name: 'bank', query: { priorities: row.key } }"
              class="block rounded-lg border border-transparent p-2 -mx-2 transition-colors hover:border-[var(--border-default)] hover:bg-[var(--bg-subtle)]/60"
            >
              <div class="flex items-center justify-between text-xs">
                <span class="font-medium text-[var(--text-primary)]">{{ row.label }}</span>
                <span class="font-mono tabular-nums text-[var(--text-secondary)]">{{ row.count }} 题 ({{ row.pct }}%)</span>
              </div>
              <div class="mt-1.5 h-1.5 overflow-hidden rounded-full bg-[var(--bg-subtle)]">
                <div class="h-full rounded-full transition-[width] duration-300" :class="row.bar" :style="{ width: `${row.pct}%` }" />
              </div>
              <p class="mt-1 text-[11px] text-[var(--text-muted)]">{{ row.desc }}</p>
            </RouterLink>
          </div>
        </div>

        <div class="mt-5 flex items-center justify-between border-t border-[var(--border-default)] pt-3 text-xs text-[var(--text-muted)]">
          <span>含 91 组交互式原理动画</span>
          <RouterLink :to="{ name: 'bank', query: { status: 'animated' } }" class="font-medium text-[#C84B31] hover:underline dark:text-[#E06246]">
            浏览全部动画题 →
          </RouterLink>
        </div>
      </div>
    </section>
  </div>
</template>
