<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight, BookOpen, CheckCircle2, Flame, Play, Rocket, Sparkles, Star, Target, TriangleAlert, Zap,
} from 'lucide-vue-next'
import { studyApi } from '@/api'
import ModuleIcon from '@/components/common/ModuleIcon.vue'
import CheckinHeatmap from '@/components/common/CheckinHeatmap.vue'
import type { DashboardData } from '@/types'

const router = useRouter()
const data = ref<DashboardData | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    data.value = await studyApi.dashboard()
  } finally {
    loading.value = false
  }
})

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

function pct(a: number, b: number) {
  return b ? Math.round((a / b) * 100) : 0
}
</script>

<template>
  <!-- 骨架屏 -->
  <div v-if="loading" class="space-y-6 animate-pulse">
    <div class="h-44 rounded-2xl bg-slate-200/70 dark:bg-slate-800/70" />
    <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <div v-for="i in 4" :key="i" class="h-28 rounded-2xl bg-slate-200/70 dark:bg-slate-800/70" />
    </div>
    <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <div v-for="i in 6" :key="i" class="h-44 rounded-2xl bg-slate-200/70 dark:bg-slate-800/70" />
    </div>
  </div>

  <div v-else-if="data" class="space-y-7">
    <!-- 今日冲刺 Hero 卡片 -->
    <section class="relative overflow-hidden rounded-2xl bg-gradient-to-br from-brand-600 via-blue-600 to-indigo-600 p-6 text-white shadow-lg shadow-brand-600/20 sm:p-7">
      <div class="pointer-events-none absolute -right-16 -top-16 h-56 w-56 rounded-full bg-white/10 blur-2xl" />
      <div class="pointer-events-none absolute -bottom-20 left-1/3 h-48 w-48 rounded-full bg-indigo-400/20 blur-2xl" />

      <div class="relative flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
        <div class="space-y-2.5">
          <div class="inline-flex items-center gap-1.5 rounded-full bg-white/15 px-3 py-1 text-xs font-medium backdrop-blur">
            <Sparkles class="h-3.5 w-3.5" />
            SM-2 抗遗忘队列已就绪
          </div>
          <h2 class="text-2xl font-semibold tracking-tight sm:text-[26px]">
            <template v-if="todayTotal > 0">
              今日待复习 <span class="tabular-nums underline decoration-white/40 underline-offset-4">{{ data.dueCount }}</span> 题 · 新题配额 <span class="tabular-nums">{{ data.newQuota }}</span> 题
            </template>
            <template v-else>今日计划已全部清零 🎉</template>
          </h2>
          <div class="flex flex-wrap items-center gap-3 pt-1 text-sm text-blue-100">
            <span>今日已刷 <strong class="tabular-nums text-white">{{ data.doneToday }}</strong> / {{ data.dailyGoal }} 题</span>
            <div class="h-2 w-36 overflow-hidden rounded-full bg-white/20">
              <div class="h-full rounded-full bg-white transition-all duration-500" :style="{ width: `${goalPct}%` }" />
            </div>
            <span class="tabular-nums text-xs">{{ goalPct }}%</span>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-3">
          <RouterLink
            :to="{ name: 'study', query: { mode: 'today' } }"
            class="btn bg-white text-brand-700 shadow-sm hover:bg-blue-50"
          >
            <Play class="h-4 w-4 fill-current" />
            开始今日复习
          </RouterLink>
          <RouterLink
            :to="{ name: 'study', query: { mode: 'p0' } }"
            class="btn border border-white/25 bg-white/10 text-white backdrop-blur hover:bg-white/20"
          >
            <Rocket class="h-4 w-4" />
            只刷 P0 必背
          </RouterLink>
        </div>
      </div>
    </section>

    <!-- 4 个核心 KPI -->
    <section class="grid grid-cols-2 gap-3.5 lg:grid-cols-4 lg:gap-4">
      <div class="card p-4 sm:p-5">
        <div class="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
          <span>总掌握进度</span>
          <CheckCircle2 class="h-4 w-4 text-emerald-500" />
        </div>
        <div class="mt-2 flex items-baseline gap-1.5">
          <span class="text-2xl font-semibold tabular-nums">{{ data.totalMastered }}</span>
          <span class="text-sm text-slate-400">/ {{ data.totalQuestions }}</span>
          <span class="ml-auto rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-600 dark:bg-emerald-500/15 dark:text-emerald-300">{{ totalPct }}%</span>
        </div>
        <p class="mt-2 text-xs text-slate-400">累计已学 {{ data.totalLearned }} 题</p>
      </div>

      <button class="card p-4 text-left transition hover:border-red-300 sm:p-5 dark:hover:border-red-500/40" @click="router.push({ name: 'study', query: { mode: 'p0' } })">
        <div class="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
          <span>P0 必背掌握</span>
          <Target class="h-4 w-4 text-red-500" />
        </div>
        <div class="mt-2 flex items-baseline gap-1.5">
          <span class="text-2xl font-semibold tabular-nums">{{ data.p0Mastered }}</span>
          <span class="text-sm text-slate-400">/ {{ data.p0Total }}</span>
          <span class="ml-auto rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-600 dark:bg-red-500/15 dark:text-red-300">{{ p0Pct }}%</span>
        </div>
        <p class="mt-2 text-xs text-slate-400">考前冲刺核心，点击开刷 →</p>
      </button>

      <div class="card p-4 sm:p-5">
        <div class="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
          <span>连续打卡</span>
          <Flame class="h-4 w-4 text-orange-500" />
        </div>
        <div class="mt-2 flex items-baseline gap-1.5">
          <span class="text-2xl font-semibold tabular-nums">{{ data.streak }}</span>
          <span class="text-sm text-slate-400">天</span>
        </div>
        <p class="mt-2 text-xs text-slate-400">本月累计打卡 {{ data.checkinDays }} 天</p>
      </div>

      <button class="card p-4 text-left transition hover:border-amber-300 sm:p-5 dark:hover:border-amber-500/40" @click="router.push({ name: 'study', query: { mode: 'mistakes' } })">
        <div class="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
          <span>待巩固错题</span>
          <TriangleAlert class="h-4 w-4 text-amber-500" />
        </div>
        <div class="mt-2 flex items-baseline gap-1.5">
          <span class="text-2xl font-semibold tabular-nums">{{ data.mistakeCount }}</span>
          <span class="text-sm text-slate-400">题</span>
        </div>
        <p class="mt-2 text-xs text-slate-400">不会 / 模糊题汇总，点击清零 →</p>
      </button>
    </section>

    <!-- 九大核心模块掌握度 -->
    <section>
      <div class="mb-4 flex items-center justify-between">
        <div>
          <h3 class="text-base font-semibold">九大核心模块掌握度</h3>
          <p class="text-xs text-slate-500 dark:text-slate-400">覆盖 375 道精选面试题与 102 张高清原理图</p>
        </div>
        <RouterLink :to="{ name: 'bank' }" class="inline-flex items-center gap-1 text-sm font-medium text-brand-600 hover:text-brand-700 dark:text-brand-500">
          全部题库 <ArrowRight class="h-4 w-4" />
        </RouterLink>
      </div>

      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <div
          v-for="m in data.modules"
          :key="m.slug"
          class="card group flex flex-col p-5 transition hover:border-slate-300 dark:hover:border-slate-700"
        >
          <div class="flex items-start gap-3">
            <ModuleIcon :icon="m.icon" :slug="m.slug" />
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <span class="font-mono text-xs text-slate-400">{{ String(m.order).padStart(2, '0') }}</span>
                <h4 class="truncate font-semibold">{{ m.title }}</h4>
                <span
                  v-if="m.due > 0"
                  class="ml-auto shrink-0 rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700 dark:bg-amber-500/15 dark:text-amber-300"
                >
                  {{ m.due }} 到期
                </span>
              </div>
              <p class="mt-0.5 text-xs text-slate-400">
                共 {{ m.questionCount }} 题 · P0 必背 {{ m.p0Count }} 题
                <template v-if="m.imageCount"> · {{ m.imageCount }} 图</template>
              </p>
            </div>
          </div>

          <div class="mt-4 space-y-2">
            <div>
              <div class="mb-1 flex items-center justify-between text-xs">
                <span class="text-slate-500 dark:text-slate-400">总掌握</span>
                <span class="font-medium tabular-nums">{{ m.mastered }}/{{ m.questionCount }} ({{ pct(m.mastered, m.questionCount) }}%)</span>
              </div>
              <div class="h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                <div class="h-full rounded-full bg-brand-600 transition-all duration-500" :style="{ width: `${pct(m.mastered, m.questionCount)}%` }" />
              </div>
            </div>

            <div class="flex items-center justify-between text-xs">
              <span class="text-slate-500 dark:text-slate-400">P0 必背</span>
              <span class="tabular-nums text-red-600 dark:text-red-400">{{ m.p0Mastered }} / {{ m.p0Count }}</span>
            </div>
          </div>

          <div class="mt-4 flex items-center gap-2 border-t border-slate-100 pt-3.5 dark:border-slate-800">
            <RouterLink
              :to="{ name: 'study', query: { mode: 'module', slug: m.slug } }"
              class="btn-primary h-8 flex-1 px-3 text-xs"
            >
              <Zap class="h-3.5 w-3.5" /> 闪卡刷题
            </RouterLink>
            <RouterLink
              :to="{ name: 'reader', params: { slug: m.slug } }"
              class="btn-ghost h-8 flex-1 px-3 text-xs"
            >
              <BookOpen class="h-3.5 w-3.5" /> 沉浸通读
            </RouterLink>
          </div>
        </div>
      </div>
    </section>

    <!-- 打卡热力图 + 快捷专练 -->
    <section class="card p-5 sm:p-6">
      <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 class="text-base font-semibold">每日背诵热力图</h3>
          <p class="text-xs text-slate-500 dark:text-slate-400">近 90 天刷题活跃记录（Redis Bitmap 驱动）</p>
        </div>
        <div class="flex items-center gap-2">
          <RouterLink :to="{ name: 'study', query: { mode: 'mistakes' } }" class="btn-ghost h-8 px-3 text-xs">
            <TriangleAlert class="h-3.5 w-3.5 text-amber-500" /> 错题本 ({{ data.mistakeCount }})
          </RouterLink>
          <RouterLink :to="{ name: 'bank', query: { status: 'starred' } }" class="btn-ghost h-8 px-3 text-xs">
            <Star class="h-3.5 w-3.5 text-amber-400" /> 我的收藏
          </RouterLink>
        </div>
      </div>
      <CheckinHeatmap :data="data.heatmap" />
    </section>
  </div>
</template>
