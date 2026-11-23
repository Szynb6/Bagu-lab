<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  CheckCircle2,
  ChevronRight,
  Cpu,
  Pause,
  Play,
  RotateCcw,
  Sparkles,
  XCircle,
} from 'lucide-vue-next'

type Algo = 'FIFO' | 'LRU' | 'Clock'

interface TraceStep {
  stepNo: number // 0 = 初始空态, 1..12 = 第 k 次访存后
  accessedPage: number | null
  outcome: 'init' | 'hit' | 'miss'
  frames: (number | null)[]
  refBits: (0 | 1)[]
  hand: number
  orderHint: number[] // FIFO 队列顺序或 LRU 最近使用顺序 (从旧到新)
  activeFrameIdx: number | null
  evictedPage: number | null
  faults: number
  hits: number
  clearedSecondChance: number[]
  explanation: string
}

const sequence = [1, 2, 3, 4, 1, 2, 5, 1, 2, 3, 4, 5] as const

const algoDescriptions: Record<Algo, string> = {
  FIFO: '先进先出：总是淘汰最早进入物理内存的页面（只看加载顺序，不看最近是否被访问；可能出现 Belady 异常）。',
  LRU: '最近最少使用：命中时刷新页面为「最新使用」，缺页时淘汰最久未被访问的页面（最贴近局部性原理，开销略高）。',
  Clock: '时钟 / 二次机会算法：维护环形链表与访问位 R（命中置 1）；缺页时指针扫描：R=1 清零放行，遇首个 R=0 才淘汰！',
}

function buildTrace(algo: Algo): TraceStep[] {
  const frames: (number | null)[] = [null, null, null]
  const refBits: (0 | 1)[] = [0, 0, 0]
  let hand = 0
  let orderQueue: number[] = [] // FIFO 加载序 / LRU 访问序（下标 0 最老）
  let faults = 0
  let hits = 0

  const steps: TraceStep[] = [
    {
      stepNo: 0,
      accessedPage: null,
      outcome: 'init',
      frames: [...frames],
      refBits: [...refBits],
      hand,
      orderHint: [...orderQueue],
      activeFrameIdx: null,
      evictedPage: null,
      faults: 0,
      hits: 0,
      clearedSecondChance: [],
      explanation: `当前算法：${algo}，物理内存共有 3 个空闲页框。点击「访问下一页」开始依次访问序列 [${sequence.join(', ')}]。`,
    },
  ]

  sequence.forEach((page, idx) => {
    const hitIdx = frames.indexOf(page)
    if (hitIdx !== -1) {
      hits += 1
      if (algo === 'LRU') {
        orderQueue = orderQueue.filter((i) => i !== hitIdx)
        orderQueue.push(hitIdx)
      }
      if (algo === 'Clock') {
        refBits[hitIdx] = 1
      }
      steps.push({
        stepNo: idx + 1,
        accessedPage: page,
        outcome: 'hit',
        frames: [...frames],
        refBits: [...refBits],
        hand,
        orderHint: [...orderQueue],
        activeFrameIdx: hitIdx,
        evictedPage: null,
        faults,
        hits,
        clearedSecondChance: [],
        explanation:
          algo === 'LRU'
            ? `访问页 ${page} → 命中 Frame #${hitIdx}！LRU 将 Frame #${hitIdx} 提升至「最近刚访问」队尾。`
            : algo === 'Clock'
              ? `访问页 ${page} → 命中 Frame #${hitIdx}！将访问位 R 置为 1（给予二次机会），时钟指针保持在 Frame #${hand}。`
              : `访问页 ${page} → 命中 Frame #${hitIdx}！FIFO 不改变原有加载顺序，无需换页。`,
      })
      return
    }

    // 缺页中断 (Page Fault)
    faults += 1
    const emptyIdx = frames.indexOf(null)
    if (emptyIdx !== -1) {
      frames[emptyIdx] = page
      refBits[emptyIdx] = 1
      orderQueue.push(emptyIdx)
      if (algo === 'Clock') hand = (emptyIdx + 1) % 3
      steps.push({
        stepNo: idx + 1,
        accessedPage: page,
        outcome: 'miss',
        frames: [...frames],
        refBits: [...refBits],
        hand,
        orderHint: [...orderQueue],
        activeFrameIdx: emptyIdx,
        evictedPage: null,
        faults,
        hits,
        clearedSecondChance: [],
        explanation: `访问页 ${page} → 缺页中断！直接装入空闲页框 Frame #${emptyIdx}${algo === 'Clock' ? `（R=1，指针移至 #${hand}）` : ''}。`,
      })
      return
    }

    // 3 个页框已满，执行置换
    if (algo === 'Clock') {
      const cleared: number[] = []
      while (refBits[hand] === 1) {
        cleared.push(hand)
        refBits[hand] = 0
        hand = (hand + 1) % 3
      }
      const victimIdx = hand
      const evicted = frames[victimIdx]
      frames[victimIdx] = page
      refBits[victimIdx] = 1
      hand = (hand + 1) % 3
      steps.push({
        stepNo: idx + 1,
        accessedPage: page,
        outcome: 'miss',
        frames: [...frames],
        refBits: [...refBits],
        hand,
        orderHint: [...orderQueue],
        activeFrameIdx: victimIdx,
        evictedPage: evicted,
        faults,
        hits,
        clearedSecondChance: cleared,
        explanation:
          cleared.length > 0
            ? `访问页 ${page} → 缺页！指针顺时针跳过 R=1 的页框 [${cleared.map((i) => `#${i}`).join(', ')}]（清零 R=0），最终在 Frame #${victimIdx} 遇到 R=0，换出页 ${evicted}、换入页 ${page}（R=1，指针→#${hand}）。`
            : `访问页 ${page} → 缺页！当前指针指向 Frame #${victimIdx} 的 R=0，直接换出页 ${evicted}、换入页 ${page}（R=1，指针→#${hand}）。`,
      })
    } else {
      const victimIdx = orderQueue.shift()!
      const evicted = frames[victimIdx]
      frames[victimIdx] = page
      orderQueue.push(victimIdx)
      steps.push({
        stepNo: idx + 1,
        accessedPage: page,
        outcome: 'miss',
        frames: [...frames],
        refBits: [...refBits],
        hand,
        orderHint: [...orderQueue],
        activeFrameIdx: victimIdx,
        evictedPage: evicted,
        faults,
        hits,
        clearedSecondChance: [],
        explanation:
          algo === 'FIFO'
            ? `访问页 ${page} → 缺页！FIFO 选中最早加载的 Frame #${victimIdx}，换出页 ${evicted}，装入页 ${page}。`
            : `访问页 ${page} → 缺页！LRU 选中最久未使用的 Frame #${victimIdx}，换出页 ${evicted}，装入页 ${page}。`,
      })
    }
  })

  return steps
}

const activeAlgo = ref<Algo>('FIFO')
const cursor = ref(0) // 0..12
const autoPlaying = ref(false)
let playTimer: number | undefined

const trace = computed(() => buildTrace(activeAlgo.value))
const current = computed(() => trace.value[cursor.value] ?? trace.value[0]!)
const faultRateText = computed(() => {
  if (current.value.stepNo === 0) return '0.0% (0/0)'
  const pct = ((current.value.faults / current.value.stepNo) * 100).toFixed(1)
  return `${pct}% (${current.value.faults}/${current.value.stepNo})`
})

// Clock 环形视图坐标（以 (595, 118) 为圆心，半径 64）
const clockCenter = { x: 595, y: 118 }
const clockNodes = [
  { idx: 0, x: 595, y: 54 },
  { idx: 1, x: 650, y: 150 },
  { idx: 2, x: 540, y: 150 },
]
const handTarget = computed(() => clockNodes[current.value.hand] ?? clockNodes[0]!)

function stopPlay() {
  autoPlaying.value = false
  if (playTimer) {
    clearInterval(playTimer)
    playTimer = undefined
  }
}

function selectAlgo(algo: Algo) {
  stopPlay()
  activeAlgo.value = algo
  cursor.value = 0
}

function stepForward() {
  stopPlay()
  if (cursor.value < sequence.length) cursor.value += 1
}

function resetSim() {
  stopPlay()
  cursor.value = 0
}

function toggleAuto() {
  if (autoPlaying.value) {
    stopPlay()
    return
  }
  if (cursor.value >= sequence.length) cursor.value = 0
  autoPlaying.value = true
  playTimer = window.setInterval(() => {
    if (cursor.value < sequence.length) {
      cursor.value += 1
    } else {
      stopPlay()
    }
  }, 1150)
}

function seqItemClass(idx: number) {
  const stepNum = idx + 1
  const s = trace.value[stepNum]
  if (stepNum === cursor.value) {
    return s?.outcome === 'hit'
      ? 'border-emerald-500 bg-emerald-500 text-white ring-2 ring-emerald-300 dark:ring-emerald-500/40'
      : 'border-red-500 bg-red-500 text-white ring-2 ring-red-300 dark:ring-red-500/40'
  }
  if (stepNum < cursor.value) {
    return s?.outcome === 'hit'
      ? 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-500/30 dark:bg-emerald-500/15 dark:text-emerald-300'
      : 'border-red-200 bg-red-50 text-red-700 dark:border-red-500/30 dark:bg-red-500/15 dark:text-red-300'
  }
  if (stepNum === cursor.value + 1) {
    return 'border-brand-400 bg-brand-50 text-brand-700 dark:border-brand-500/50 dark:bg-brand-500/15 dark:text-blue-300'
  }
  return 'border-slate-200 bg-white text-slate-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400'
}

function frameCardClass(idx: number) {
  const cur = current.value
  if (cur.activeFrameIdx === idx) {
    return cur.outcome === 'hit'
      ? 'fill-emerald-500 stroke-emerald-600'
      : 'fill-red-500 stroke-red-600'
  }
  if (cur.frames[idx] === null) {
    return 'fill-slate-50 stroke-slate-300 [stroke-dasharray:4_3] dark:fill-slate-900 dark:stroke-slate-700'
  }
  return 'fill-white stroke-slate-300 dark:fill-slate-800 dark:stroke-slate-600'
}

function frameRankBadge(idx: number): string {
  const cur = current.value
  if (cur.frames[idx] === null) return '空闲页框'
  if (activeAlgo.value === 'Clock') {
    return `R=${cur.refBits[idx]}${cur.hand === idx ? ' ← 指针' : ''}`
  }
  const pos = cur.orderHint.indexOf(idx)
  if (pos === 0) return activeAlgo.value === 'FIFO' ? '下个被淘汰 (最早)' : '下个被淘汰 (最久未用)'
  if (pos === cur.orderHint.length - 1) return activeAlgo.value === 'FIFO' ? '最新装入' : '最近刚访问'
  return '队列第 2 位'
}

onBeforeUnmount(stopPlay)
</script>

<template>
  <div class="space-y-4">
    <!-- 算法切换 + 缺页率统计徽章 -->
    <div class="flex flex-wrap items-center justify-between gap-2.5">
      <div class="inline-flex rounded-xl border border-slate-200 bg-slate-100/80 p-0.5 text-xs dark:border-slate-700 dark:bg-slate-800">
        <button
          v-for="algo in (['FIFO', 'LRU', 'Clock'] as Algo[])"
          :key="algo"
          class="rounded-lg px-3 py-1.5 font-mono font-semibold transition"
          :class="activeAlgo === algo
            ? 'bg-white text-brand-700 shadow-xs dark:bg-slate-900 dark:text-blue-300'
            : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'"
          @click="selectAlgo(algo)"
        >
          {{ algo === 'Clock' ? 'Clock (二次机会)' : algo }}
        </button>
      </div>

      <div class="flex flex-wrap items-center gap-2 font-mono text-[11px]">
        <span
          v-if="current.outcome !== 'init'"
          class="inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 font-bold"
          :class="current.outcome === 'hit'
            ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300'
            : 'bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-300'"
        >
          <CheckCircle2 v-if="current.outcome === 'hit'" class="h-3 w-3" />
          <XCircle v-else class="h-3 w-3" />
          {{ current.outcome === 'hit' ? `命中 HIT (页 ${current.accessedPage})` : `缺页 MISS (页 ${current.accessedPage})` }}
        </span>
        <span class="rounded-full border border-slate-200 bg-white px-2.5 py-0.5 font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200">
          缺页率: {{ faultRateText }}
        </span>
      </div>
    </div>

    <!-- 页面访问序列进度条 -->
    <div class="rounded-xl border border-slate-200/80 bg-slate-50/70 p-2.5 dark:border-slate-800 dark:bg-slate-900/60">
      <div class="mb-1.5 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400">
        <span>预设访存序列 (点击任意步跳转)：</span>
        <span class="font-mono">进度 {{ cursor }}/{{ sequence.length }}</span>
      </div>
      <div class="grid grid-cols-6 gap-1.5 sm:grid-cols-12">
        <button
          v-for="(pg, i) in sequence"
          :key="i"
          class="flex flex-col items-center rounded-lg border py-1 font-mono text-xs font-bold transition"
          :class="seqItemClass(i)"
          @click="stopPlay(); cursor = i + 1"
        >
          <span>{{ pg }}</span>
          <span class="text-[9px] font-normal opacity-80">
            {{ i + 1 <= cursor ? (trace[i + 1]?.outcome === 'hit' ? 'H' : 'M') : `#${i + 1}` }}
          </span>
        </button>
      </div>
    </div>

    <!-- 3 页框物理内存 SVG（Clock 算法附带环形指针视图） -->
    <div class="overflow-x-auto rounded-xl border border-slate-200/80 bg-white p-3 dark:border-slate-800 dark:bg-slate-950/70">
      <svg viewBox="0 0 760 228" class="mx-auto w-full min-w-[580px] max-w-[740px] select-none" role="img" aria-label="物理内存 3 页框页面置换模拟">
        <defs>
          <marker id="clk-arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="currentColor" />
          </marker>
        </defs>

        <!-- 左侧：3 个物理页框槽位 -->
        <text x="20" y="24" class="fill-slate-700 text-[12px] font-semibold dark:fill-slate-200">
          物理内存页框 (3 Frames) · {{ activeAlgo }}
        </text>

        <g v-for="idx in 3" :key="`frame-${idx - 1}`" :transform="`translate(${20 + (idx - 1) * 145}, 42)`">
          <rect
            width="128"
            height="126"
            rx="14"
            stroke-width="2"
            class="transition-all duration-300"
            :class="frameCardClass(idx - 1)"
          />
          <text
            x="64"
            y="24"
            text-anchor="middle"
            class="font-mono text-[11px] font-semibold"
            :class="current.activeFrameIdx === idx - 1 ? 'fill-white/90' : 'fill-slate-400'"
          >
            Frame #{{ idx - 1 }}
          </text>
          <text
            x="64"
            y="78"
            text-anchor="middle"
            class="font-mono text-[30px] font-bold"
            :class="current.activeFrameIdx === idx - 1
              ? 'fill-white'
              : current.frames[idx - 1] !== null
                ? 'fill-slate-800 dark:fill-white'
                : 'fill-slate-300 dark:fill-slate-600'"
          >
            {{ current.frames[idx - 1] !== null ? current.frames[idx - 1] : '—' }}
          </text>
          <rect
            x="10"
            y="94"
            width="108"
            height="22"
            rx="6"
            :class="current.activeFrameIdx === idx - 1
              ? 'fill-black/20'
              : 'fill-slate-100 dark:fill-slate-900'"
          />
          <text
            x="64"
            y="108.5"
            text-anchor="middle"
            class="text-[10px] font-semibold"
            :class="current.activeFrameIdx === idx - 1 ? 'fill-white' : 'fill-slate-500 dark:fill-slate-400'"
          >
            {{ frameRankBadge(idx - 1) }}
          </text>
        </g>

        <!-- 置换提示标签 -->
        <g v-if="current.evictedPage !== null" transform="translate(20, 184)">
          <rect width="418" height="30" rx="10" class="fill-red-50 stroke-red-200 dark:fill-red-500/15 dark:stroke-red-500/30" stroke-width="1.2" />
          <text x="209" y="19" text-anchor="middle" class="font-mono text-[11.5px] font-semibold fill-red-700 dark:fill-red-300">
            换出 Page {{ current.evictedPage }} → 换入 Page {{ current.accessedPage }} (Frame #{{ current.activeFrameIdx }})
          </text>
        </g>
        <g v-else transform="translate(20, 184)">
          <rect width="418" height="30" rx="10" class="fill-slate-50 stroke-slate-200 dark:fill-slate-900 dark:stroke-slate-800" stroke-width="1.2" />
          <text x="209" y="19" text-anchor="middle" class="text-[11px] fill-slate-500 dark:fill-slate-400">
            {{ current.outcome === 'hit' ? '页面已在物理内存中，本次无需换出任何页' : '未发生页面换出（尚有空闲页框或初始态）' }}
          </text>
        </g>

        <!-- 右侧：Clock 环形表盘 OR FIFO/LRU 队列顺序对比卡 -->
        <g v-if="activeAlgo === 'Clock'">
          <rect x="466" y="12" width="274" height="204" rx="14" class="fill-slate-50/70 stroke-slate-200 dark:fill-slate-900/50 dark:stroke-slate-800" stroke-width="1.4" />
          <text x="603" y="32" text-anchor="middle" class="fill-brand-700 text-[11px] font-semibold dark:fill-blue-300">
            Clock 环形时钟指针 (R=0/1)
          </text>
          <circle
            :cx="clockCenter.x"
            :cy="clockCenter.y"
            r="64"
            fill="none"
            stroke-width="2"
            class="stroke-slate-300 [stroke-dasharray:5_4] dark:stroke-slate-700"
          />
          <!-- 指针箭头 -->
          <line
            :x1="clockCenter.x"
            :y1="clockCenter.y"
            :x2="clockCenter.x + (handTarget.x - clockCenter.x) * 0.58"
            :y2="clockCenter.y + (handTarget.y - clockCenter.y) * 0.58"
            stroke-width="3"
            marker-end="url(#clk-arrow)"
            class="stroke-amber-500 text-amber-500 transition-all duration-300"
          />
          <circle :cx="clockCenter.x" :cy="clockCenter.y" r="5" class="fill-amber-500" />
          <!-- 3 个环形节点 -->
          <g v-for="cn in clockNodes" :key="`cn-${cn.idx}`">
            <circle
              :cx="cn.x"
              :cy="cn.y"
              r="22"
              stroke-width="2"
              class="transition-all duration-300"
              :class="current.hand === cn.idx
                ? 'fill-amber-50 stroke-amber-500 dark:fill-amber-500/20'
                : 'fill-white stroke-slate-300 dark:fill-slate-800 dark:stroke-slate-600'"
            />
            <text :x="cn.x" :y="cn.y - 2" text-anchor="middle" class="font-mono text-[11px] font-bold fill-slate-800 dark:fill-white">
              {{ current.frames[cn.idx] !== null ? `P${current.frames[cn.idx]}` : '空' }}
            </text>
            <text
              :x="cn.x"
              :y="cn.y + 12"
              text-anchor="middle"
              class="font-mono text-[9.5px] font-bold"
              :class="current.refBits[cn.idx] === 1 ? 'fill-emerald-600 dark:fill-emerald-400' : 'fill-slate-400'"
            >
              R={{ current.refBits[cn.idx] }}
            </text>
          </g>
          <text x="603" y="205" text-anchor="middle" class="font-mono text-[10px] fill-slate-500">
            指针当前停在 Frame #{{ current.hand }}
          </text>
        </g>

        <g v-else>
          <rect x="466" y="12" width="274" height="204" rx="14" class="fill-slate-50/70 stroke-slate-200 dark:fill-slate-900/50 dark:stroke-slate-800" stroke-width="1.4" />
          <text x="603" y="34" text-anchor="middle" class="fill-brand-700 text-[11px] font-semibold dark:fill-blue-300">
            {{ activeAlgo === 'FIFO' ? 'FIFO 淘汰优先级队列' : 'LRU 淘汰优先级队列' }}
          </text>
          <text x="603" y="52" text-anchor="middle" class="fill-slate-400 text-[10px]">
            {{ activeAlgo === 'FIFO' ? '左(队头先出) ────────→ 右(新装入)' : '左(最久未用) ────────→ 右(刚访问)' }}
          </text>
          <g v-for="(frameIdx, pos) in current.orderHint" :key="`q-${frameIdx}`" :transform="`translate(${492 + pos * 78}, 74)`">
            <rect
              width="66"
              height="68"
              rx="10"
              stroke-width="1.8"
              :class="pos === 0 ? 'fill-amber-50 stroke-amber-400 dark:fill-amber-500/15' : 'fill-white stroke-slate-300 dark:fill-slate-800 dark:stroke-slate-600'"
            />
            <text x="33" y="22" text-anchor="middle" class="font-mono text-[9.5px] fill-slate-400">F#{{ frameIdx }}</text>
            <text x="33" y="46" text-anchor="middle" class="font-mono text-[18px] font-bold fill-slate-800 dark:fill-white">
              {{ current.frames[frameIdx] }}
            </text>
            <text v-if="pos === 0" x="33" y="61" text-anchor="middle" class="text-[9px] font-semibold fill-amber-600 dark:fill-amber-300">
              待淘汰
            </text>
          </g>
          <text v-if="current.orderHint.length === 0" x="603" y="114" text-anchor="middle" class="text-[11px] fill-slate-400">
            队列为空（等待首次访存）
          </text>
          <text x="603" y="182" text-anchor="middle" class="font-mono text-[10.5px] fill-slate-500 dark:fill-slate-400">
            累计命中 {{ current.hits }} 次 · 缺页 {{ current.faults }} 次
          </text>
        </g>
      </svg>
    </div>

    <!-- 控制按钮栏 -->
    <div class="flex flex-wrap items-center gap-2">
      <button class="btn-primary h-9 px-3.5 text-xs" :disabled="cursor >= sequence.length" @click="stepForward">
        <ChevronRight class="h-3.5 w-3.5" /> 访问下一页 ({{ cursor < sequence.length ? `Page ${sequence[cursor]}` : '已完成' }})
      </button>
      <button class="btn-ghost h-9 px-3 text-xs" @click="toggleAuto">
        <component :is="autoPlaying ? Pause : Play" class="h-3.5 w-3.5 text-brand-500" />
        {{ autoPlaying ? '暂停演示' : '自动演示' }}
      </button>
      <button class="btn-ghost ml-auto h-9 px-3 text-xs" @click="resetSim">
        <RotateCcw class="h-3.5 w-3.5" /> 重置
      </button>
    </div>

    <!-- 当前步解说与知识点提示 -->
    <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3.5 text-sm dark:border-slate-800 dark:bg-slate-900/80">
      <div class="flex items-center gap-1.5 font-semibold text-slate-900 dark:text-white">
        <Cpu class="h-4 w-4 text-brand-500" />
        <span>执行细节 · {{ activeAlgo }}</span>
      </div>
      <p class="mt-1.5 leading-relaxed text-slate-600 dark:text-slate-300">{{ current.explanation }}</p>
      <p class="mt-2 flex items-start gap-1.5 rounded-lg bg-amber-50/90 px-2.5 py-1.5 text-xs text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
        <Sparkles class="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" />
        <span>{{ algoDescriptions[activeAlgo] }}（小知识：本序列在 FIFO 下若把页框增加到 4 个，缺页次数反而从 9 次升到 10 次，即著名的 Belady 异常！）</span>
      </p>
    </div>
  </div>
</template>
