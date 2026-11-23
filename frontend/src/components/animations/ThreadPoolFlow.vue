<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  CheckCircle2, ChevronLeft, ChevronRight, Lightbulb, Pause, Play, Plus, RotateCcw, TriangleAlert,
} from 'lucide-vue-next'

type Mode = 'guided' | 'sandbox'
type ActivePath = 'none' | 'core' | 'queue' | 'extra' | 'reject' | 'drain'

interface GuidedStep {
  title: string
  badge: string
  activePath: ActivePath
  core: (string | null)[]
  queue: string[]
  extra: (string | null)[]
  rejected: string | null
  speech: string
  tip: string
}

const guidedSteps: GuidedStep[] = [
  {
    title: '0. 线程池就绪（初始参数）',
    badge: '初始态',
    activePath: 'none',
    core: [null, null],
    queue: [],
    extra: [null, null],
    rejected: null,
    speech: '演示参数：corePoolSize = 2（核心线程）、workQueue 容量 = 2（有界阻塞队列）、maximumPoolSize = 4（最多 2 核心 + 2 非核心）、拒绝策略 = AbortPolicy。',
    tip: '面试开口先报 7 大核心参数，再按“核心 → 队列 → 最大 → 拒绝”4 步展开。',
  },
  {
    title: '1. 优先创建核心线程执行',
    badge: 'worker < core',
    activePath: 'core',
    core: ['T1', 'T2'],
    queue: [],
    extra: [null, null],
    rejected: null,
    speech: '提交 T1、T2 时，当前运行线程数 < corePoolSize(2)，线程池直接创建核心线程执行任务（即使有空闲核心线程，默认也会先创建到 corePoolSize）。',
    tip: '核心线程默认不会超时销毁（除非开启 allowCoreThreadTimeOut）。',
  },
  {
    title: '2. 核心已满 → 进入阻塞队列排队',
    badge: '队列缓冲',
    activePath: 'queue',
    core: ['T1', 'T2'],
    queue: ['T3', 'T4'],
    extra: [null, null],
    rejected: null,
    speech: '提交 T3、T4 时，核心线程已满(2/2)，任务不会立刻创建非核心线程，而是先调用 workQueue.offer() 放入阻塞队列等待。',
    tip: '高频考点：如果使用无界队列（如 LinkedBlockingQueue 默认容量 Integer.MAX_VALUE），队列永远不满，maximumPoolSize 参数形同虚设且可能 OOM！',
  },
  {
    title: '3. 队列已满 → 创建非核心线程救急',
    badge: 'worker < max',
    activePath: 'extra',
    core: ['T1', 'T2'],
    queue: ['T3', 'T4'],
    extra: ['T5', 'T6'],
    rejected: null,
    speech: '提交 T5、T6 时，核心与队列均已满，但线程总数 < maximumPoolSize(4)，于是创建非核心线程直接执行刚进来的 T5、T6！',
    tip: '易错细节：新来的 T5/T6 会“插队”交给刚创建的非核心线程先跑，而不是等队列里的 T3/T4！',
  },
  {
    title: '4. 全部饱和 → 触发拒绝策略',
    badge: 'Rejected',
    activePath: 'reject',
    core: ['T1', 'T2'],
    queue: ['T3', 'T4'],
    extra: ['T5', 'T6'],
    rejected: 'T7',
    speech: '提交 T7 时，核心满(2/2) + 队列满(2/2) + 非核心满(2/2)，触发 RejectedExecutionHandler，默认 AbortPolicy 直接抛出 RejectedExecutionException。',
    tip: '4 种内置策略：AbortPolicy(抛异常) / CallerRunsPolicy(调用者线程自己跑) / DiscardPolicy(静默丢弃) / DiscardOldestPolicy(丢弃队头再重试)。',
  },
  {
    title: '5. 任务完成 → 拉取队列 & 非核心回收',
    badge: 'keepAliveTime',
    activePath: 'drain',
    core: ['T3', 'T4'],
    queue: [],
    extra: [null, null],
    rejected: null,
    speech: 'T1/T2 跑完后，核心线程自动从 workQueue 拉取 T3/T4 继续执行；队列变空后，非核心线程空闲超过 keepAliveTime 会被销毁回收，只保留 2 个核心线程常驻。',
    tip: '生产配置经验：CPU 密集型 ≈ N+1；IO 密集型 ≈ 2N（或压测微调 + 动态线程池监控队列堆积）。',
  },
]

const mode = ref<Mode>('guided')
const stepIdx = ref(0)
const playing = ref(false)
let playTimer: number | undefined

const nextTaskNo = ref(1)
const sbCore = ref<(string | null)[]>([null, null])
const sbQueue = ref<string[]>([])
const sbExtra = ref<(string | null)[]>([null, null])
const sbRejected = ref<string | null>(null)
const sbPath = ref<ActivePath>('none')
const sbLog = ref('点击下方「提交新任务」按钮，亲自观察任务如何流向核心线程、阻塞队列、非核心线程和拒绝策略。')

const cur = computed(() => {
  if (mode.value === 'guided') {
    const s = guidedSteps[stepIdx.value]!
    return {
      core: s.core,
      queue: s.queue,
      extra: s.extra,
      rejected: s.rejected,
      activePath: s.activePath,
    }
  }
  return {
    core: sbCore.value,
    queue: sbQueue.value,
    extra: sbExtra.value,
    rejected: sbRejected.value,
    activePath: sbPath.value,
  }
})

const currentGuided = computed(() => guidedSteps[stepIdx.value]!)

function stopPlay() {
  playing.value = false
  if (playTimer) {
    clearInterval(playTimer)
    playTimer = undefined
  }
}

function togglePlay() {
  if (playing.value) {
    stopPlay()
    return
  }
  if (stepIdx.value >= guidedSteps.length - 1) stepIdx.value = 0
  playing.value = true
  playTimer = window.setInterval(() => {
    if (stepIdx.value < guidedSteps.length - 1) stepIdx.value += 1
    else stopPlay()
  }, 2400)
}

function prevStep() {
  stopPlay()
  if (stepIdx.value > 0) stepIdx.value -= 1
}

function nextStep() {
  stopPlay()
  if (stepIdx.value < guidedSteps.length - 1) stepIdx.value += 1
}

function resetGuided() {
  stopPlay()
  stepIdx.value = 0
}

function switchMode(m: Mode) {
  stopPlay()
  mode.value = m
}

function submitSandboxTask() {
  const name = `T${nextTaskNo.value++}`
  sbRejected.value = null

  const freeCore = sbCore.value.findIndex((t) => t === null)
  if (freeCore !== -1) {
    sbCore.value[freeCore] = name
    sbPath.value = 'core'
    sbLog.value = `${name} 提交 → 核心线程未满，交由【核心线程 #${freeCore + 1}】直接执行。`
    return
  }
  if (sbQueue.value.length < 2) {
    sbQueue.value.push(name)
    sbPath.value = 'queue'
    sbLog.value = `${name} 提交 → 核心线程已满(2/2)，进入【阻塞队列】排队（当前 ${sbQueue.value.length}/2）。`
    return
  }
  const freeExtra = sbExtra.value.findIndex((t) => t === null)
  if (freeExtra !== -1) {
    sbExtra.value[freeExtra] = name
    sbPath.value = 'extra'
    sbLog.value = `${name} 提交 → 队列已满(2/2)，紧急创建【非核心线程 #${freeExtra + 1}】直接执行 ${name}！`
    return
  }
  sbRejected.value = name
  sbPath.value = 'reject'
  sbLog.value = `${name} 提交 → 核心(2/2) + 队列(2/2) + 非核心(2/2) 全满，触发【AbortPolicy】拒绝异常！`
}

function finishOneTask() {
  sbRejected.value = null
  const busyExtra = sbExtra.value.findIndex((t) => t !== null)
  if (busyExtra !== -1) {
    const done = sbExtra.value[busyExtra]
    if (sbQueue.value.length > 0) {
      const next = sbQueue.value.shift()!
      sbExtra.value[busyExtra] = next
      sbPath.value = 'drain'
      sbLog.value = `${done} 执行完成 → 非核心线程 #${busyExtra + 1} 从阻塞队列拉取【${next}】继续执行。`
    } else {
      sbExtra.value[busyExtra] = null
      sbPath.value = 'drain'
      sbLog.value = `${done} 执行完成且队列为空 → 非核心线程 #${busyExtra + 1} 超过 keepAliveTime 自动回收销毁。`
    }
    return
  }

  const busyCore = sbCore.value.findIndex((t) => t !== null)
  if (busyCore !== -1) {
    const done = sbCore.value[busyCore]
    if (sbQueue.value.length > 0) {
      const next = sbQueue.value.shift()!
      sbCore.value[busyCore] = next
      sbPath.value = 'drain'
      sbLog.value = `${done} 执行完成 → 核心线程 #${busyCore + 1} 从阻塞队列拉取【${next}】继续执行。`
    } else {
      sbCore.value[busyCore] = null
      sbPath.value = 'none'
      sbLog.value = `${done} 执行完成 → 核心线程 #${busyCore + 1} 回到空闲常驻状态，等待下一个任务。`
    }
    return
  }
  sbLog.value = '当前线程池已全部空闲，点「提交新任务」继续实验吧。'
}

function resetSandbox() {
  nextTaskNo.value = 1
  sbCore.value = [null, null]
  sbQueue.value = []
  sbExtra.value = [null, null]
  sbRejected.value = null
  sbPath.value = 'none'
  sbLog.value = '沙盒已重置，点击「提交新任务」从 T1 开始体验。'
}

const hasBusyThread = computed(() =>
  sbCore.value.some(Boolean) || sbExtra.value.some(Boolean),
)

onBeforeUnmount(stopPlay)
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div class="inline-flex rounded-lg border border-[var(--border-default)] bg-[var(--bg-subtle)] p-0.5 text-xs">
        <button
          type="button"
          class="rounded-md px-3 py-1.5 font-medium transition-colors"
          :class="mode === 'guided' ? 'bg-[var(--bg-surface)] text-[#C84B31] dark:text-[#E06246]' : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'"
          @click="switchMode('guided')"
        >
          标准流程演练 (6 步)
        </button>
        <button
          type="button"
          class="rounded-md px-3 py-1.5 font-medium transition-colors"
          :class="mode === 'sandbox' ? 'bg-[var(--bg-surface)] text-[#C84B31] dark:text-[#E06246]' : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'"
          @click="switchMode('sandbox')"
        >
          动手提交沙盒
        </button>
      </div>

      <span
        v-if="mode === 'guided'"
        class="rounded border border-[#EFC9BF] bg-[#FBF0ED] px-2 py-0.5 font-mono text-[11px] font-semibold tabular-nums text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]"
      >
        {{ currentGuided.badge }} · {{ stepIdx + 1 }}/{{ guidedSteps.length }}
      </span>
      <span v-else class="font-mono text-[11px] tabular-nums text-[var(--text-muted)]">
        核心 {{ cur.core.filter(Boolean).length }}/2 · 队列 {{ cur.queue.length }}/2 · 非核心 {{ cur.extra.filter(Boolean).length }}/2
      </span>
    </div>

    <div class="overflow-x-auto rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] p-3">
      <svg viewBox="0 0 760 285" class="mx-auto w-full min-w-[580px] max-w-[740px] select-none" role="img" aria-label="ThreadPoolExecutor 执行流程动画">
        <defs>
          <marker id="tp-arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="currentColor" />
          </marker>
        </defs>

        <path
          d="M 115 142 C 160 142, 165 56, 225 56"
          fill="none"
          stroke-width="2.4"
          marker-end="url(#tp-arrow)"
          class="transition-[stroke,opacity] duration-300"
          :class="cur.activePath === 'core' ? 'text-emerald-700 stroke-emerald-700 [stroke-dasharray:6_4]' : 'text-[#D4D0C5] stroke-[#D4D0C5] dark:text-[#3B3933] dark:stroke-[#3B3933]'"
        />
        <path
          d="M 115 142 L 225 142"
          fill="none"
          stroke-width="2.4"
          marker-end="url(#tp-arrow)"
          class="transition-[stroke,opacity] duration-300"
          :class="cur.activePath === 'queue' ? 'text-brand-500 stroke-brand-500 [stroke-dasharray:6_4]' : 'text-[#D4D0C5] stroke-[#D4D0C5] dark:text-[#3B3933] dark:stroke-[#3B3933]'"
        />
        <path
          d="M 115 142 C 160 142, 165 228, 225 228"
          fill="none"
          stroke-width="2.4"
          marker-end="url(#tp-arrow)"
          class="transition-[stroke,opacity] duration-300"
          :class="cur.activePath === 'extra' ? 'text-amber-700 stroke-amber-700 [stroke-dasharray:6_4]' : 'text-[#D4D0C5] stroke-[#D4D0C5] dark:text-[#3B3933] dark:stroke-[#3B3933]'"
        />
        <path
          d="M 355 110 L 355 92"
          fill="none"
          stroke-width="2"
          marker-end="url(#tp-arrow)"
          class="transition-[stroke,opacity] duration-300"
          :class="cur.activePath === 'drain' ? 'text-emerald-700 stroke-emerald-700' : 'text-[#D4D0C5] stroke-[#D4D0C5] dark:text-[#3B3933] dark:stroke-[#3B3933]'"
        />
        <path
          d="M 485 142 L 560 142"
          fill="none"
          stroke-width="2.4"
          marker-end="url(#tp-arrow)"
          class="transition-[stroke,opacity] duration-300"
          :class="cur.activePath === 'reject' ? 'text-red-700 stroke-red-700 [stroke-dasharray:6_4]' : 'text-[#D4D0C5] stroke-[#D4D0C5] dark:text-[#3B3933] dark:stroke-[#3B3933]'"
        />

        <g transform="translate(15, 108)">
          <rect width="100" height="68" rx="12" class="fill-[#F4F2EC] stroke-[#D4D0C5] dark:fill-[#22211E] dark:stroke-[#3B3933]" stroke-width="1.5" />
          <text x="50" y="30" text-anchor="middle" class="fill-[#161614] text-[12px] font-semibold dark:fill-[#ECEAE4]">execute()</text>
          <text x="50" y="48" text-anchor="middle" class="fill-[#736E64] text-[10px] dark:fill-[#A8A397]">提交任务入口</text>
        </g>

        <g transform="translate(230, 14)">
          <rect
            width="250"
            height="76"
            rx="12"
            stroke-width="1.6"
            class="transition-[fill,stroke] duration-300"
            :class="cur.activePath === 'core' || cur.activePath === 'drain'
              ? 'fill-emerald-50 stroke-emerald-400 dark:fill-emerald-950/40 dark:stroke-emerald-600'
              : 'fill-[#F4F2EC] stroke-[#E5E2DA] dark:fill-[#1A1917] dark:stroke-[#2B2A26]'"
          />
          <text x="14" y="22" class="fill-emerald-800 text-[11px] font-semibold dark:fill-emerald-300">① 核心线程 corePoolSize = 2</text>
          <g v-for="(slot, idx) in cur.core" :key="`core-${idx}`" :transform="`translate(${16 + idx * 112}, 32)`">
            <rect
              width="102"
              height="32"
              rx="8"
              stroke-width="1.5"
              class="transition-[fill,stroke] duration-300"
              :class="slot
                ? 'fill-emerald-700 stroke-emerald-800'
                : 'fill-white stroke-[#D4D0C5] dark:fill-[#22211E] dark:stroke-[#3B3933]'"
              :stroke-dasharray="slot ? undefined : '4 3'"
            />
            <text
              x="51"
              y="20"
              text-anchor="middle"
              class="text-[11px] font-semibold"
              :class="slot ? 'fill-white' : 'fill-[#736E64] dark:fill-[#A8A397]'"
            >
              {{ slot ? `Core#${idx + 1} · ${slot}` : `Core#${idx + 1} 空闲` }}
            </text>
          </g>
        </g>

        <g transform="translate(230, 108)">
          <rect
            width="250"
            height="68"
            rx="12"
            stroke-width="1.6"
            class="transition-[fill,stroke] duration-300"
            :class="cur.activePath === 'queue'
              ? 'fill-brand-50 stroke-brand-400 dark:fill-brand-950/40 dark:stroke-brand-600'
              : 'fill-[#F4F2EC] stroke-[#E5E2DA] dark:fill-[#1A1917] dark:stroke-[#2B2A26]'"
          />
          <text x="14" y="21" class="fill-brand-700 text-[11px] font-semibold dark:fill-brand-300">② 阻塞队列 workQueue (容量 2)</text>
          <g v-for="idx in 2" :key="`q-${idx}`" :transform="`translate(${16 + (idx - 1) * 112}, 29)`">
            <rect
              width="102"
              height="28"
              rx="8"
              stroke-width="1.5"
              class="transition-[fill,stroke] duration-300"
              :class="cur.queue[idx - 1]
                ? 'fill-brand-600 stroke-brand-700'
                : 'fill-white stroke-[#D4D0C5] dark:fill-[#22211E] dark:stroke-[#3B3933]'"
              :stroke-dasharray="cur.queue[idx - 1] ? undefined : '4 3'"
            />
            <text
              x="51"
              y="18"
              text-anchor="middle"
              class="text-[11px] font-semibold"
              :class="cur.queue[idx - 1] ? 'fill-white' : 'fill-[#736E64] dark:fill-[#A8A397]'"
            >
              {{ cur.queue[idx - 1] ? `排队中 · ${cur.queue[idx - 1]}` : `空槽位 #${idx}` }}
            </text>
          </g>
        </g>

        <g transform="translate(230, 194)">
          <rect
            width="250"
            height="76"
            rx="12"
            stroke-width="1.6"
            class="transition-[fill,stroke] duration-300"
            :class="cur.activePath === 'extra'
              ? 'fill-amber-50 stroke-amber-400 dark:fill-amber-950/40 dark:stroke-amber-600'
              : 'fill-[#F4F2EC] stroke-[#E5E2DA] dark:fill-[#1A1917] dark:stroke-[#2B2A26]'"
          />
          <text x="14" y="22" class="fill-amber-800 text-[11px] font-semibold dark:fill-amber-300">③ 非核心线程 (max - core = 2)</text>
          <g v-for="(slot, idx) in cur.extra" :key="`extra-${idx}`" :transform="`translate(${16 + idx * 112}, 32)`">
            <rect
              width="102"
              height="32"
              rx="8"
              stroke-width="1.5"
              class="transition-[fill,stroke] duration-300"
              :class="slot
                ? 'fill-amber-700 stroke-amber-800'
                : 'fill-white stroke-[#D4D0C5] dark:fill-[#22211E] dark:stroke-[#3B3933]'"
              :stroke-dasharray="slot ? undefined : '4 3'"
            />
            <text
              x="51"
              y="20"
              text-anchor="middle"
              class="text-[11px] font-semibold"
              :class="slot ? 'fill-white' : 'fill-[#736E64] dark:fill-[#A8A397]'"
            >
              {{ slot ? `Temp#${idx + 1} · ${slot}` : '未创建 / 已回收' }}
            </text>
          </g>
        </g>

        <g transform="translate(566, 98)">
          <rect
            width="178"
            height="88"
            rx="12"
            stroke-width="1.6"
            class="transition-[fill,stroke] duration-300"
            :class="cur.rejected
              ? 'fill-red-50 stroke-red-600 dark:fill-red-950/40 dark:stroke-red-500'
              : 'fill-[#F4F2EC] stroke-[#E5E2DA] dark:fill-[#1A1917] dark:stroke-[#2B2A26]'"
          />
          <text x="89" y="24" text-anchor="middle" class="text-[11px] font-semibold" :class="cur.rejected ? 'fill-red-700 dark:fill-red-300' : 'fill-[#5E5A52] dark:fill-[#A8A397]'">
            ④ 拒绝策略
          </text>
          <text x="89" y="42" text-anchor="middle" class="fill-[#736E64] text-[10px] dark:fill-[#8F8A7E]">RejectedExecutionHandler</text>
          <rect
            x="24"
            y="50"
            width="130"
            height="26"
            rx="8"
            class="transition-[fill] duration-300"
            :class="cur.rejected ? 'fill-red-700' : 'fill-[#E5E2DA] dark:fill-[#2B2A26]'"
          />
          <text
            x="89"
            y="67"
            text-anchor="middle"
            class="text-[11px] font-semibold"
            :class="cur.rejected ? 'fill-white' : 'fill-[#5E5A52] dark:fill-[#A8A397]'"
          >
            {{ cur.rejected ? `拒绝 ${cur.rejected} (Abort)` : '暂未触发' }}
          </text>
        </g>
      </svg>
    </div>

    <template v-if="mode === 'guided'">
      <div class="rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] p-3.5 text-sm">
        <p class="font-semibold text-[var(--text-primary)]">{{ currentGuided.title }}</p>
        <p class="mt-1.5 leading-relaxed text-[var(--text-secondary)]">{{ currentGuided.speech }}</p>
        <p class="mt-2.5 flex items-start gap-1.5 rounded-md border border-amber-300/70 bg-amber-50/70 px-2.5 py-1.5 text-xs text-amber-900 dark:border-amber-700/40 dark:bg-amber-950/35 dark:text-amber-200">
          <Lightbulb class="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-600 dark:text-amber-400" />
          <span>{{ currentGuided.tip }}</span>
        </p>
      </div>

      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-1.5">
          <button
            v-for="(_, i) in guidedSteps"
            :key="i"
            type="button"
            class="h-2 rounded-full transition-[width,background-color] duration-200"
            :class="i === stepIdx ? 'w-6 bg-[#C84B31] dark:bg-[#E06246]' : 'w-2 bg-[var(--border-strong)] hover:bg-[var(--text-muted)]'"
            :title="`第 ${i + 1} 步`"
            :aria-label="`跳转到第 ${i + 1} 步`"
            @click="stopPlay(); stepIdx = i"
          />
        </div>

        <div class="flex items-center gap-2">
          <button type="button" class="btn-ghost h-8 px-2.5 text-xs" :disabled="stepIdx === 0" @click="prevStep">
            <ChevronLeft class="h-3.5 w-3.5" /> 上一步
          </button>
          <button type="button" class="btn-primary h-8 px-3 text-xs" @click="togglePlay">
            <component :is="playing ? Pause : Play" class="h-3.5 w-3.5" />
            {{ playing ? '暂停' : '自动播放' }}
          </button>
          <button type="button" class="btn-ghost h-8 px-2.5 text-xs" :disabled="stepIdx === guidedSteps.length - 1" @click="nextStep">
            下一步 <ChevronRight class="h-3.5 w-3.5" />
          </button>
          <button type="button" class="btn-ghost h-8 px-2 text-xs" title="重置" aria-label="重置动画" @click="resetGuided">
            <RotateCcw class="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </template>

    <template v-else>
      <div
        class="flex items-start gap-2 rounded-lg border px-3.5 py-2.5 text-xs leading-relaxed"
        :class="
          sbRejected
            ? 'border-red-300 bg-red-50 text-red-800 dark:border-red-700/50 dark:bg-red-950/40 dark:text-red-200'
            : 'border-[var(--border-default)] bg-[var(--bg-surface)] text-[var(--text-secondary)]'
        "
      >
        <TriangleAlert v-if="sbRejected" class="mt-0.5 h-4 w-4 shrink-0 text-red-600 dark:text-red-400" />
        <CheckCircle2 v-else class="mt-0.5 h-4 w-4 shrink-0 text-[#C84B31] dark:text-[#E06246]" />
        <span>{{ sbLog }}</span>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <button type="button" class="btn-primary h-9 px-3.5 text-xs" @click="submitSandboxTask">
          <Plus class="h-3.5 w-3.5" /> 提交新任务 (T{{ nextTaskNo }})
        </button>
        <button type="button" class="btn-ghost h-9 px-3 text-xs" :disabled="!hasBusyThread" @click="finishOneTask">
          <CheckCircle2 class="h-3.5 w-3.5 text-[#2E6F5E] dark:text-[#4FA58E]" /> 模拟完成 1 个任务
        </button>
        <button type="button" class="btn-ghost ml-auto h-9 px-3 text-xs" @click="resetSandbox">
          <RotateCcw class="h-3.5 w-3.5" /> 重置沙盒
        </button>
      </div>
    </template>
  </div>
</template>
