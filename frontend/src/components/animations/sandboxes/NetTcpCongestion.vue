<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  Activity,
  AlertTriangle,
  FastForward,
  Pause,
  Play,
  RotateCcw,
  Lightbulb,
  TrendingUp,
} from 'lucide-vue-next'

type Phase = '慢启动' | '拥塞避免' | '快恢复' | '超时重传'

interface Point {
  rtt: number
  cwnd: number
  ssthresh: number
  phase: Phase
  event?: 'dup-ack' | 'rto'
}

const history = ref<Point[]>([
  { rtt: 0, cwnd: 1, ssthresh: 16, phase: '慢启动' },
])
const logText = ref('初始状态：cwnd = 1 (MSS)，慢启动阈值 ssthresh = 16。点击「+1 RTT」观察指数增长！')
const autoPlaying = ref(false)
let autoTimer: number | undefined

const current = computed(() => history.value[history.value.length - 1]!)

const phaseBadgeClass = computed(() => {
  switch (current.value.phase) {
    case '慢启动':
      return 'bg-brand-100 text-brand-700 dark:bg-brand-500/20 dark:text-blue-300'
    case '拥塞避免':
      return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300'
    case '快恢复':
      return 'bg-amber-100 text-amber-800 dark:bg-amber-500/20 dark:text-amber-300'
    case '超时重传':
      return 'bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-300'
  }
})

// 坐标系计算
const chartLeft = 58
const chartRight = 724
const chartTop = 26
const chartBottom = 238
const chartWidth = chartRight - chartLeft
const chartHeight = chartBottom - chartTop

const maxRtt = computed(() => Math.max(18, current.value.rtt + 2))
const maxCwnd = computed(() => {
  const highest = Math.max(22, current.value.ssthresh + 4, ...history.value.map((p) => p.cwnd + 3))
  return Math.ceil(highest / 4) * 4
})

function px(rtt: number) {
  return chartLeft + (rtt / maxRtt.value) * chartWidth
}

function py(cwnd: number) {
  return chartBottom - (cwnd / maxCwnd.value) * chartHeight
}

const cwndPolyline = computed(() =>
  history.value.map((p) => `${px(p.rtt).toFixed(1)},${py(p.cwnd).toFixed(1)}`).join(' '),
)

// 阶梯状 ssthresh 虚线路径（清晰展示每次拥塞时阈值减半）
const ssthreshPath = computed(() => {
  const pts = history.value
  if (pts.length === 0) return ''
  let d = `M ${px(pts[0]!.rtt).toFixed(1)} ${py(pts[0]!.ssthresh).toFixed(1)}`
  for (let i = 1; i < pts.length; i++) {
    const prev = pts[i - 1]!
    const cur = pts[i]!
    const x = px(cur.rtt).toFixed(1)
    if (cur.ssthresh !== prev.ssthresh) {
      d += ` L ${x} ${py(prev.ssthresh).toFixed(1)} L ${x} ${py(cur.ssthresh).toFixed(1)}`
    } else {
      d += ` L ${x} ${py(cur.ssthresh).toFixed(1)}`
    }
  }
  // 向右延伸到当前轴尾端便于参照
  d += ` L ${chartRight} ${py(current.value.ssthresh).toFixed(1)}`
  return d
})

const yTicks = computed(() => {
  const step = maxCwnd.value <= 28 ? 4 : 8
  const ticks: number[] = []
  for (let v = 0; v <= maxCwnd.value; v += step) ticks.push(v)
  return ticks
})

const xTicks = computed(() => {
  const step = maxRtt.value <= 20 ? 2 : 4
  const ticks: number[] = []
  for (let v = 0; v <= maxRtt.value; v += step) ticks.push(v)
  return ticks
})

function stopAuto() {
  autoPlaying.value = false
  if (autoTimer) {
    clearInterval(autoTimer)
    autoTimer = undefined
  }
}

function stepNextRtt(fromAuto = false) {
  if (!fromAuto) stopAuto()
  const cur = current.value
  const nextRtt = cur.rtt + 1
  if (cur.cwnd < cur.ssthresh) {
    const doubled = cur.cwnd * 2
    const nextCwnd = Math.min(doubled, cur.ssthresh)
    const hitThresh = nextCwnd === cur.ssthresh
    history.value.push({
      rtt: nextRtt,
      cwnd: nextCwnd,
      ssthresh: cur.ssthresh,
      phase: '慢启动',
    })
    logText.value = hitThresh
      ? `RTT ${nextRtt}：慢启动指数翻倍 ${cur.cwnd} → ${nextCwnd}，恰好触及 ssthresh=${cur.ssthresh}，下个 RTT 起切入「拥塞避免」！`
      : `RTT ${nextRtt}：cwnd(${cur.cwnd}) < ssthresh(${cur.ssthresh})，处于慢启动阶段，每收到 1 个 ACK cwnd+1，本轮指数翻倍为 ${nextCwnd}。`
  } else {
    const nextCwnd = cur.cwnd + 1
    history.value.push({
      rtt: nextRtt,
      cwnd: nextCwnd,
      ssthresh: cur.ssthresh,
      phase: '拥塞避免',
    })
    logText.value = `RTT ${nextRtt}：cwnd(${cur.cwnd}) >= ssthresh(${cur.ssthresh})，进入拥塞避免，每个 RTT 仅线性 +1 → ${nextCwnd}。`
  }
}

function triggerDupAck(fromAuto = false) {
  if (!fromAuto) stopAuto()
  const cur = current.value
  const nextRtt = cur.rtt + 1
  const newThresh = Math.max(2, Math.floor(cur.cwnd / 2))
  history.value.push({
    rtt: nextRtt,
    cwnd: newThresh,
    ssthresh: newThresh,
    phase: '快恢复',
    event: 'dup-ack',
  })
  logText.value = `RTT ${nextRtt}：连续收到 3 个冗余 ACK（轻度拥塞）！触发快重传/快恢复：ssthresh = max(2, ⌊${cur.cwnd}/2⌋) = ${newThresh}，cwnd 降为 ${newThresh}（不回落到 1），后续直接拥塞避免线性增长！`
}

function triggerRto(fromAuto = false) {
  if (!fromAuto) stopAuto()
  const cur = current.value
  const nextRtt = cur.rtt + 1
  const newThresh = Math.max(2, Math.floor(cur.cwnd / 2))
  history.value.push({
    rtt: nextRtt,
    cwnd: 1,
    ssthresh: newThresh,
    phase: '超时重传',
    event: 'rto',
  })
  logText.value = `RTT ${nextRtt}：发生 RTO 超时重传（严重拥塞）！ssthresh = max(2, ⌊${cur.cwnd}/2⌋) = ${newThresh}，cwnd 狠狠重置为 1，重新从慢启动爬坡！`
}

function resetChart(fromAuto = false) {
  if (!fromAuto) stopAuto()
  history.value = [{ rtt: 0, cwnd: 1, ssthresh: 16, phase: '慢启动' }]
  logText.value = '已重置到初始状态：cwnd = 1，ssthresh = 16（慢启动）。'
}

function toggleAutoDemo() {
  if (autoPlaying.value) {
    stopAuto()
    return
  }
  resetChart(true)
  autoPlaying.value = true
  // 预设经典锯齿演练剧本: 慢启动 -> 拥塞避免 -> 3冗余ACK快恢复 -> 线性增长 -> RTO超时 -> 慢启动恢复
  const script: Array<() => void> = [
    () => stepNextRtt(true), // 1 -> 2
    () => stepNextRtt(true), // 2 -> 4
    () => stepNextRtt(true), // 4 -> 8
    () => stepNextRtt(true), // 8 -> 16
    () => stepNextRtt(true), // 16 -> 17
    () => stepNextRtt(true), // 17 -> 18
    () => stepNextRtt(true), // 18 -> 19
    () => stepNextRtt(true), // 19 -> 20
    () => triggerDupAck(true), // ssthresh=10, cwnd=10
    () => stepNextRtt(true), // 10 -> 11
    () => stepNextRtt(true), // 11 -> 12
    () => triggerRto(true), // ssthresh=6, cwnd=1
    () => stepNextRtt(true), // 1 -> 2
    () => stepNextRtt(true), // 2 -> 4
    () => stepNextRtt(true), // 4 -> 6
    () => stepNextRtt(true), // 6 -> 7
  ]
  let idx = 0
  autoTimer = window.setInterval(() => {
    const action = script[idx]
    if (!action) {
      stopAuto()
      return
    }
    action()
    idx += 1
  }, 680)
}

function pointColorClass(p: Point) {
  if (p.event === 'rto') return 'fill-red-500 stroke-white'
  if (p.event === 'dup-ack') return 'fill-amber-700 stroke-white'
  if (p.phase === '拥塞避免') return 'fill-emerald-700 stroke-white'
  return 'fill-brand-600 stroke-white'
}

onBeforeUnmount(stopAuto)
</script>

<template>
  <div class="space-y-4">
    <!-- 顶部实时状态徽章 -->
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div class="flex flex-wrap items-center gap-2">
        <span class="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold" :class="phaseBadgeClass">
          <Activity class="h-3.5 w-3.5" />
          当前阶段：{{ current.phase }}
        </span>
        <span class="rounded-full border border-slate-200 bg-white px-2.5 py-0.5 font-mono text-xs font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200">
          RTT = {{ current.rtt }}
        </span>
        <span class="rounded-full border border-slate-200 bg-white px-2.5 py-0.5 font-mono text-xs font-semibold text-brand-600 dark:border-slate-700 dark:bg-slate-900 dark:text-blue-300">
          cwnd = {{ current.cwnd }}
        </span>
        <span class="rounded-full border border-slate-200 bg-white px-2.5 py-0.5 font-mono text-xs font-semibold text-amber-600 dark:border-slate-700 dark:bg-slate-900 dark:text-amber-300">
          ssthresh = {{ current.ssthresh }}
        </span>
      </div>

      <span class="font-mono text-[11px] text-slate-400">
        {{ current.cwnd < current.ssthresh ? '规律: ×2 指数增长' : '规律: +1 线性增长' }}
      </span>
    </div>

    <!-- 动态折线图 SVG -->
    <div class="overflow-x-auto rounded-xl border border-slate-200/80 bg-white p-3 dark:border-slate-800 dark:bg-slate-950/70">
      <svg viewBox="0 0 760 278" class="mx-auto w-full min-w-[580px] max-w-[740px] select-none" role="img" aria-label="TCP 拥塞控制 cwnd 变化折线图">
        <!-- 水平网格线与 Y 轴刻度 -->
        <g v-for="yVal in yTicks" :key="`y-${yVal}`">
          <line
            :x1="chartLeft"
            :y1="py(yVal)"
            :x2="chartRight"
            :y2="py(yVal)"
            stroke-width="1"
            class="stroke-slate-100 dark:stroke-slate-800/80"
          />
          <text :x="chartLeft - 8" :y="py(yVal) + 3.5" text-anchor="end" class="font-mono text-[10px] fill-slate-400">
            {{ yVal }}
          </text>
        </g>

        <!-- 垂直网格线与 X 轴刻度 -->
        <g v-for="xVal in xTicks" :key="`x-${xVal}`">
          <line
            :x1="px(xVal)"
            :y1="chartTop"
            :x2="px(xVal)"
            :y2="chartBottom"
            stroke-width="1"
            class="stroke-slate-100 dark:stroke-slate-800/80"
          />
          <text :x="px(xVal)" :y="chartBottom + 16" text-anchor="middle" class="font-mono text-[10px] fill-slate-400">
            {{ xVal }}
          </text>
        </g>

        <!-- 坐标轴线与轴标题 -->
        <line :x1="chartLeft" :y1="chartTop" :x2="chartLeft" :y2="chartBottom" stroke-width="1.5" class="stroke-slate-300 dark:stroke-slate-700" />
        <line :x1="chartLeft" :y1="chartBottom" :x2="chartRight" :y2="chartBottom" stroke-width="1.5" class="stroke-slate-300 dark:stroke-slate-700" />
        <text x="16" y="135" text-anchor="middle" transform="rotate(-90 16 135)" class="font-mono text-[11px] font-semibold fill-slate-500">
          cwnd (MSS)
        </text>
        <text :x="(chartLeft + chartRight) / 2" y="270" text-anchor="middle" class="font-mono text-[11px] font-semibold fill-slate-500">
          传输轮次 (RTT)
        </text>

        <!-- 动态 ssthresh 虚线 -->
        <path
          :d="ssthreshPath"
          fill="none"
          stroke-width="2"
          class="stroke-amber-500 [stroke-dasharray:6_4] transition-[fill,stroke,opacity] duration-300"
        />
        <rect
          :x="chartRight - 96"
          :y="py(current.ssthresh) - 18"
          width="94"
          height="16"
          rx="4"
          class="fill-amber-700/90"
        />
        <text
          :x="chartRight - 49"
          :y="py(current.ssthresh) - 7"
          text-anchor="middle"
          class="font-mono text-[9.5px] font-bold fill-white"
        >
          ssthresh = {{ current.ssthresh }}
        </text>

        <!-- cwnd 折线 -->
        <polyline
          :points="cwndPolyline"
          fill="none"
          stroke-width="2.6"
          stroke-linejoin="round"
          stroke-linecap="round"
          class="stroke-brand-500 transition-[fill,stroke,opacity] duration-300"
        />

        <!-- 数据点与拥塞事件标记 -->
        <g v-for="p in history" :key="`pt-${p.rtt}`">
          <circle
            :cx="px(p.rtt)"
            :cy="py(p.cwnd)"
            :r="p.rtt === current.rtt ? 5.5 : 4"
            stroke-width="1.8"
            :class="pointColorClass(p)"
          />
          <text
            v-if="p.event === 'dup-ack'"
            :x="px(p.rtt)"
            :y="py(p.cwnd) + 16"
            text-anchor="middle"
            class="font-mono text-[9.5px] font-bold fill-amber-600 dark:fill-amber-400"
          >
            3-Dup
          </text>
          <text
            v-if="p.event === 'rto'"
            :x="px(p.rtt)"
            :y="py(p.cwnd) - 10"
            text-anchor="middle"
            class="font-mono text-[9.5px] font-bold fill-red-600 dark:fill-red-400"
          >
            RTO
          </text>
        </g>

        <!-- 当前点数值气泡 -->
        <g>
          <rect
            :x="Math.min(chartRight - 38, px(current.rtt) - 16)"
            :y="Math.max(chartTop, py(current.cwnd) - 24)"
            width="34"
            height="16"
            rx="5"
            class="fill-brand-600"
          />
          <text
            :x="Math.min(chartRight - 21, px(current.rtt) + 1)"
            :y="Math.max(chartTop + 11.5, py(current.cwnd) - 12.5)"
            text-anchor="middle"
            class="font-mono text-[10px] font-bold fill-white"
          >
            {{ current.cwnd }}
          </text>
        </g>
      </svg>
    </div>

    <!-- 交互控制按钮组 -->
    <div class="flex flex-wrap items-center gap-2">
      <button class="btn-primary h-9 px-3.5 text-xs" @click="stepNextRtt(false)">
        <TrendingUp class="h-3.5 w-3.5" /> +1 RTT (收到 ACK)
      </button>
      <button
        class="btn-ghost h-9 border-amber-300 px-3 text-xs text-amber-700 hover:bg-amber-50 dark:border-amber-500/40 dark:text-amber-300 dark:hover:bg-amber-500/10"
        @click="triggerDupAck(false)"
      >
        <FastForward class="h-3.5 w-3.5" /> 3 个冗余 ACK (快恢复)
      </button>
      <button
        class="btn-ghost h-9 border-red-300 px-3 text-xs text-red-700 hover:bg-red-50 dark:border-red-500/40 dark:text-red-300 dark:hover:bg-red-500/10"
        @click="triggerRto(false)"
      >
        <AlertTriangle class="h-3.5 w-3.5" /> RTO 超时重传
      </button>
      <button class="btn-ghost ml-auto h-9 px-3 text-xs" @click="toggleAutoDemo">
        <component :is="autoPlaying ? Pause : Play" class="h-3.5 w-3.5 text-brand-500" />
        {{ autoPlaying ? '暂停演练' : '自动演练' }}
      </button>
      <button class="btn-ghost h-9 px-2.5 text-xs" title="重置" @click="resetChart(false)">
        <RotateCcw class="h-3.5 w-3.5" /> 重置
      </button>
    </div>

    <!-- 实时演练解说卡片 -->
    <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3.5 text-sm dark:border-slate-800 dark:bg-slate-900/80">
      <p class="leading-relaxed text-slate-700 dark:text-slate-200">{{ logText }}</p>
      <p class="mt-2 flex items-start gap-1.5 rounded-lg bg-amber-50/90 px-2.5 py-1.5 text-xs text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
        <Lightbulb class="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" />
        <span>核心区别：RTO 超时意味着网络丢包严重，cwnd 暴力打回 1；3 个冗余 ACK 说明后续报文仍在到达，触发快重传/快恢复，cwnd 直接降为 ssthresh 避免吞吐断崖跌落。</span>
      </p>
    </div>
  </div>
</template>
