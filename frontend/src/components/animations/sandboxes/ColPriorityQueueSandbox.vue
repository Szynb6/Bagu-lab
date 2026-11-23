<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  ArrowUpDown,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Minus,
  Pause,
  Play,
  Plus,
  RotateCcw,
  Lightbulb,
} from 'lucide-vue-next'

type Mode = 'guided' | 'sandbox'

interface HeapFrame {
  heap: number[]
  compareIdx: number[]
  swappedIdx: number[]
  highlightIdx: number | null
  formula: string
  log: string
}

interface GuidedHeapStep {
  title: string
  badge: string
  heap: number[]
  compareIdx: number[]
  swappedIdx: number[]
  highlightIdx: number | null
  polled: number[]
  formula: string
  speech: string
  tip: string
}

const MAX_NODES = 15
const PRESET_OFFERS = [3, 12, 5, 18, 8, 2]

const guidedSteps: GuidedHeapStep[] = [
  {
    title: '0. 底层存储：Object[] queue 数组实现完全二叉树（小顶堆）',
    badge: '数组表示完全二叉树',
    heap: [3, 12, 5, 18, 8],
    compareIdx: [],
    swappedIdx: [],
    highlightIdx: null,
    polled: [],
    formula: 'parent=(k-1)>>>1 · left=(k<<1)+1 · right=left+1',
    speech: 'Java PriorityQueue 底层并不是链表树节点，而是一个 Object[] 数组，通过下标公式映射成完全二叉树小顶堆：任意父节点 queue[parent] <= 左右子节点，保证堆顶 queue[0] 永远是最小值。',
    tip: '高频题 Q72：注意看当前数组 [3, 12, 5, 18, 8]——索引 1 是 12，索引 2 是 5！小顶堆只保证父≤子，不保证兄弟间有序！',
  },
  {
    title: '1. offer(2) 入队：先挂数组末尾 [5]，触发 siftUp 上浮',
    badge: 'offer(2) 尾部追加',
    heap: [3, 12, 5, 18, 8, 2],
    compareIdx: [5, 2],
    swappedIdx: [],
    highlightIdx: 5,
    polled: [],
    formula: 'k=5, parent=(5-1)>>>1=2 → 比较 queue[5]=2 vs queue[2]=5',
    speech: '调用 offer(2) 时，先把 2 放到数组末尾 k=5。然后进入 siftUp：用公式 (k-1)>>>1 算出父节点下标 2 (值为 5)，因为 2 < 5，需要交换上浮！',
    tip: '时间复杂度：插入和删除堆顶只需沿着树高比较交换，均为 O(log n)；peek() 取堆顶则是 O(1)。',
  },
  {
    title: '2. siftUp 继续上浮：与新父节点 [0]=3 比较并交换到堆顶',
    badge: 'siftUp 到达堆顶',
    heap: [2, 12, 3, 18, 8, 5],
    compareIdx: [],
    swappedIdx: [0, 2],
    highlightIdx: 0,
    polled: [],
    formula: '先 2↔5，再 k=2, parent=(2-1)>>>1=0 → 2 < 3 → 2↔3 登顶！',
    speech: '2 换到下标 2 后继续算父节点 (2-1)>>>1 = 0 (值为 3)，因为 2 < 3 再次交换！最终 2 来到根下标 0 成为新堆顶。',
    tip: '为何用无符号右移 (k - 1) >>> 1？避免整数除法开销且防止极端大下标相加溢出符号位。',
  },
  {
    title: '3. for-each 遍历 vs 循环 poll()：面试经典坑 (Q72 & Q74)',
    badge: 'for-each 无序陷阱',
    heap: [2, 12, 3, 18, 8, 5],
    compareIdx: [],
    swappedIdx: [],
    highlightIdx: null,
    polled: [],
    formula: 'for-each 输出: [2, 12, 3, 18, 8, 5] ← 底层数组原生顺序(无序!)',
    speech: '如果直接用 for (Integer x : pq) 或迭代器遍历，PriorityQueue 只是按数组下标 0→1→2→... 依次输出 [2, 12, 3, 18, 8, 5]，结果并不是升序！',
    tip: '必须不断调用 poll() 才能按从小到大的有序序列出队，因为每次 poll() 都会重新下沉选出次小值。',
  },
  {
    title: '4. poll() 弹出堆顶 2：末尾元素 [5]=5 补位并 siftDown 下沉',
    badge: 'poll() → 2, siftDown',
    heap: [3, 12, 5, 18, 8],
    compareIdx: [],
    swappedIdx: [0, 2],
    highlightIdx: 0,
    polled: [2],
    formula: '弹出 2 → 末尾 5 移到 [0] → 比较左右子 [1]=12、[2]=3 → 5↔3 下沉',
    speech: '调用 poll() 弹出最小值 2 后，把数组最后一个元素 5 放到堆顶 [0]；然后执行 siftDown：在左子 [1]=12 与右子 [2]=3 中选更小的 3，因 5 > 3，将 3 换上堆顶、5 下沉！',
    tip: '注意：PriorityQueue 非线程安全，且不允许插入 null（也要求元素实现 Comparable 或传入 Comparator）；多线程并发优先队列请用 PriorityBlockingQueue。',
  },
]

const mode = ref<Mode>('guided')
const stepIdx = ref(0)
const playing = ref(false)
let playTimer: number | undefined
let animTimer: number | undefined

// 沙盒状态
const sbHeap = ref<number[]>([3, 12, 5, 18, 8])
const sbCompareIdx = ref<number[]>([])
const sbSwappedIdx = ref<number[]>([])
const sbHighlightIdx = ref<number | null>(null)
const sbPolled = ref<number[]>([])
const sbFormula = ref('parent = (k - 1) >>> 1 · left = (k << 1) + 1')
const sbLog = ref('当前小顶堆底层数组为 [3, 12, 5, 18, 8]（注意 12 > 5，底层数组并非全局有序！）。点击 offer(2) 体验 siftUp 上浮！')
const animating = ref(false)
const customNum = ref<number | null>(1)
const foreachFlash = ref(false)

const cur = computed(() => {
  if (mode.value === 'guided') {
    const s = guidedSteps[stepIdx.value]!
    return {
      heap: s.heap,
      compareIdx: s.compareIdx,
      swappedIdx: s.swappedIdx,
      highlightIdx: s.highlightIdx,
      polled: s.polled,
      formula: s.formula,
    }
  }
  return {
    heap: sbHeap.value,
    compareIdx: sbCompareIdx.value,
    swappedIdx: sbSwappedIdx.value,
    highlightIdx: sbHighlightIdx.value,
    polled: sbPolled.value,
    formula: sbFormula.value,
  }
})

const currentGuided = computed(() => guidedSteps[stepIdx.value]!)

interface TreeNodePos {
  idx: number
  val: number
  x: number
  y: number
  px: number | null
  py: number | null
}

const treeNodes = computed<TreeNodePos[]>(() => {
  const arr = cur.value.heap
  const posList: { x: number; y: number }[] = []
  return arr.map((val, idx) => {
    const depth = Math.floor(Math.log2(idx + 1))
    const rowStart = Math.pow(2, depth) - 1
    const posInRow = idx - rowStart
    const rowCount = Math.pow(2, depth)
    const x = ((posInRow + 0.5) / rowCount) * 440 + 10
    const y = 30 + depth * 58
    posList.push({ x, y })
    const parentIdx = idx > 0 ? (idx - 1) >>> 1 : -1
    const parent = parentIdx >= 0 ? posList[parentIdx]! : null
    return {
      idx,
      val,
      x,
      y,
      px: parent ? parent.x : null,
      py: parent ? parent.y : null,
    }
  })
})

function stopPlay() {
  playing.value = false
  if (playTimer) {
    clearInterval(playTimer)
    playTimer = undefined
  }
}

function stopAnim() {
  animating.value = false
  if (animTimer) {
    clearTimeout(animTimer)
    animTimer = undefined
  }
}

function switchMode(m: Mode) {
  stopPlay()
  stopAnim()
  mode.value = m
}

function togglePlay() {
  if (playing.value) {
    stopPlay()
    return
  }
  if (stepIdx.value >= guidedSteps.length - 1) stepIdx.value = 0
  playing.value = true
  playTimer = window.setInterval(() => {
    if (stepIdx.value < guidedSteps.length - 1) {
      stepIdx.value += 1
    } else {
      stopPlay()
    }
  }, 2600)
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

onBeforeUnmount(() => {
  stopPlay()
  stopAnim()
})

function playFrames(frames: HeapFrame[]) {
  stopAnim()
  if (frames.length === 0) return
  animating.value = true
  let i = 0

  const stepNext = () => {
    const f = frames[i]!
    sbHeap.value = [...f.heap]
    sbCompareIdx.value = f.compareIdx
    sbSwappedIdx.value = f.swappedIdx
    sbHighlightIdx.value = f.highlightIdx
    sbFormula.value = f.formula
    sbLog.value = f.log
    i += 1
    if (i < frames.length) {
      animTimer = window.setTimeout(stepNext, 540)
    } else {
      animating.value = false
      animTimer = undefined
    }
  }
  stepNext()
}

function offerValue(val: number) {
  if (animating.value) return
  if (sbHeap.value.length >= MAX_NODES) {
    sbLog.value = `演示树最多展示 ${MAX_NODES} 个节点（4 层完全二叉树），请先 poll() 弹出或重置！`
    return
  }
  foreachFlash.value = false
  const arr = [...sbHeap.value, val]
  let k = arr.length - 1
  const frames: HeapFrame[] = [
    {
      heap: [...arr],
      compareIdx: [],
      swappedIdx: [],
      highlightIdx: k,
      formula: `offer(${val}) → 先插入末尾下标 [${k}]`,
      log: `offer(${val})：将新元素追加到数组末尾 [${k}]，准备执行 siftUp 上浮。`,
    },
  ]

  while (k > 0) {
    const parent = (k - 1) >>> 1
    const childVal = arr[k]!
    const parentVal = arr[parent]!
    frames.push({
      heap: [...arr],
      compareIdx: [k, parent],
      swappedIdx: [],
      highlightIdx: k,
      formula: `parent = (${k}-1)>>>1 = ${parent}：比较 [${k}]=${childVal} vs [${parent}]=${parentVal}`,
      log: `siftUp 比较：[${k}]=${childVal} 与父节点 [${parent}]=${parentVal}。`,
    })
    if (childVal < parentVal) {
      arr[k] = parentVal
      arr[parent] = childVal
      frames.push({
        heap: [...arr],
        compareIdx: [],
        swappedIdx: [k, parent],
        highlightIdx: parent,
        formula: `${childVal} < ${parentVal} → 交换 [${k}] ↔ [${parent}]`,
        log: `满足 ${childVal} < ${parentVal}，上浮交换 [${k}] 与 [${parent}]！`,
      })
      k = parent
    } else {
      frames.push({
        heap: [...arr],
        compareIdx: [],
        swappedIdx: [],
        highlightIdx: k,
        formula: `${childVal} ≥ ${parentVal} → siftUp 停止，堆性质已满足`,
        log: `${childVal} ≥ 父节点 ${parentVal}，无需继续上浮，offer(${val}) 完成！`,
      })
      break
    }
  }

  if (k === 0) {
    frames.push({
      heap: [...arr],
      compareIdx: [],
      swappedIdx: [],
      highlightIdx: 0,
      formula: `k = 0 到达堆顶！offer(${val}) 完成`,
      log: `元素 ${val} 一路上浮到达堆顶 [0]，成为当前最小值！`,
    })
  }

  playFrames(frames)
}

function submitCustomOffer() {
  if (customNum.value === null || Number.isNaN(customNum.value)) return
  const n = Math.max(-99, Math.min(999, Math.trunc(customNum.value)))
  offerValue(n)
}

function pollHeap() {
  if (animating.value) return
  if (sbHeap.value.length === 0) {
    sbLog.value = '优先队列为空，无法 poll()！'
    return
  }
  foreachFlash.value = false
  const arr = [...sbHeap.value]
  const popped = arr[0]!
  sbPolled.value.push(popped)

  if (arr.length === 1) {
    sbHeap.value = []
    sbCompareIdx.value = []
    sbSwappedIdx.value = []
    sbHighlightIdx.value = null
    sbFormula.value = `poll() 弹出堆顶 ${popped} → 队列为空`
    sbLog.value = `poll() 弹出最后一个堆顶元素 ${popped}，队列现已清空。`
    return
  }

  const last = arr.pop()!
  arr[0] = last
  const n = arr.length
  const half = n >>> 1
  let k = 0

  const frames: HeapFrame[] = [
    {
      heap: [...arr],
      compareIdx: [],
      swappedIdx: [],
      highlightIdx: 0,
      formula: `poll() 弹出 ${popped} → 末尾 ${last} 移至堆顶 [0]`,
      log: `poll() 弹出最小值 ${popped}，将原末尾元素 ${last} 补到堆顶 [0]，准备 siftDown 下沉。`,
    },
  ]

  while (k < half) {
    let child = (k << 1) + 1
    let cVal = arr[child]!
    const right = child + 1
    if (right < n && arr[right]! < cVal) {
      child = right
      cVal = arr[right]!
    }
    const curVal = arr[k]!
    frames.push({
      heap: [...arr],
      compareIdx: [k, child],
      swappedIdx: [],
      highlightIdx: k,
      formula: `较小子节点 [${child}]=${cVal}，比较 [${k}]=${curVal} vs [${child}]=${cVal}`,
      log: `siftDown 比较：[${k}]=${curVal} 与较小子节点 [${child}]=${cVal}。`,
    })
    if (curVal <= cVal) {
      frames.push({
        heap: [...arr],
        compareIdx: [],
        swappedIdx: [],
        highlightIdx: k,
        formula: `${curVal} ≤ ${cVal} → siftDown 结束，小顶堆恢复`,
        log: `${curVal} ≤ 较小子节点 ${cVal}，siftDown 提前终止，堆序已恢复！`,
      })
      break
    }
    arr[k] = cVal
    arr[child] = curVal
    frames.push({
      heap: [...arr],
      compareIdx: [],
      swappedIdx: [k, child],
      highlightIdx: child,
      formula: `${curVal} > ${cVal} → 下沉交换 [${k}] ↔ [${child}]`,
      log: `${curVal} > ${cVal}，将更小的 ${cVal} 换上去，${curVal} 下沉到 [${child}]！`,
    })
    k = child
  }

  if (k >= half) {
    frames.push({
      heap: [...arr],
      compareIdx: [],
      swappedIdx: [],
      highlightIdx: k,
      formula: `到达叶子层 [${k}]，poll() 下沉完成`,
      log: `${last} 下沉到叶子节点 [${k}]，siftDown 完成，新堆顶为 ${arr[0]}！`,
    })
  }

  playFrames(frames)
}

function demonstrateForEach() {
  if (animating.value) return
  foreachFlash.value = true
  sbFormula.value = `for-each 输出: [${sbHeap.value.join(', ')}] (按数组下标 0..n-1)`
  sbLog.value = sbHeap.value.length
    ? `for-each 直接遍历底层数组 queue[]，输出 [${sbHeap.value.join(', ')}] —— 注意它不是完全升序！只有持续调用 poll() 才会得到有序序列。`
    : '当前队列为空，请先 offer 插入几个元素。'
}

function resetSandbox(empty = false) {
  stopAnim()
  foreachFlash.value = false
  sbHeap.value = empty ? [] : [3, 12, 5, 18, 8]
  sbCompareIdx.value = []
  sbSwappedIdx.value = []
  sbHighlightIdx.value = null
  sbPolled.value = []
  sbFormula.value = 'parent = (k - 1) >>> 1 · left = (k << 1) + 1'
  sbLog.value = empty
    ? '已清空优先队列！点击上方 preset 按钮挨个 offer 入队吧。'
    : '已恢复演示初始堆 [3, 12, 5, 18, 8]，建议先点 offer(2) 或 poll() 观察堆调整过程！'
}

function nodeTone(idx: number) {
  if (cur.value.swappedIdx.includes(idx)) {
    return 'fill-emerald-700 stroke-emerald-600 text-white'
  }
  if (cur.value.compareIdx.includes(idx)) {
    return 'fill-amber-700 stroke-amber-600 text-white'
  }
  if (cur.value.highlightIdx === idx) {
    return 'fill-brand-600 stroke-brand-700 text-white'
  }
  return 'fill-white stroke-slate-300 text-slate-800 dark:fill-slate-800 dark:stroke-slate-600 dark:text-slate-100'
}

function isSolidIdx(idx: number) {
  return (
    cur.value.swappedIdx.includes(idx)
    || cur.value.compareIdx.includes(idx)
    || cur.value.highlightIdx === idx
  )
}

function arrayCellClass(idx: number) {
  if (cur.value.swappedIdx.includes(idx)) {
    return 'border-emerald-500 bg-emerald-700 text-white shadow-xs'
  }
  if (cur.value.compareIdx.includes(idx)) {
    return 'border-amber-500 bg-amber-700 text-white shadow-xs'
  }
  if (cur.value.highlightIdx === idx) {
    return 'border-brand-600 bg-brand-600 text-white shadow-xs'
  }
  if (foreachFlash.value) {
    return 'border-purple-400 bg-purple-50 text-purple-800 dark:border-purple-500/50 dark:bg-purple-500/15 dark:text-purple-200'
  }
  return 'border-slate-300 bg-white text-slate-800 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100'
}
</script>

<template>
  <div class="space-y-4">
    <!-- 模式切换 -->
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div class="inline-flex rounded-xl border border-slate-200 bg-slate-100/80 p-0.5 text-xs dark:border-slate-700 dark:bg-slate-800">
        <button
          class="rounded-lg px-3 py-1.5 font-medium transition"
          :class="mode === 'guided' ? 'bg-white text-brand-700 shadow-xs dark:bg-slate-900 dark:text-brand-300' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'"
          @click="switchMode('guided')"
        >
          小顶堆原理演练 (5 步)
        </button>
        <button
          class="rounded-lg px-3 py-1.5 font-medium transition"
          :class="mode === 'sandbox' ? 'bg-white text-brand-700 shadow-xs dark:bg-slate-900 dark:text-brand-300' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'"
          @click="switchMode('sandbox')"
        >
          动手 PriorityQueue 沙盒
        </button>
      </div>

      <span
        v-if="mode === 'guided'"
        class="rounded-full bg-brand-100/80 px-2.5 py-0.5 font-mono text-[11px] font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-300"
      >
        {{ currentGuided.badge }} · {{ stepIdx + 1 }}/{{ guidedSteps.length }}
      </span>
      <span v-else class="font-mono text-[11px] text-slate-400">
        size = {{ cur.heap.length }} / {{ MAX_NODES }} · 堆顶 peek = {{ cur.heap[0] ?? 'null' }}
      </span>
    </div>

    <!-- 同步双视图：左侧二叉树 SVG + 右侧底层数组 queue[] -->
    <div class="rounded-xl border border-slate-200/80 bg-white p-3.5 dark:border-slate-800 dark:bg-slate-950/70">
      <div class="mb-2.5 flex flex-wrap items-center justify-between gap-2 rounded-lg bg-slate-50 px-3 py-1.5 font-mono text-[11px] text-slate-600 dark:bg-slate-900 dark:text-slate-300">
        <span>{{ cur.formula }}</span>
        <span class="text-slate-400">小顶堆性质: queue[i] ≤ queue[2i+1] &amp;&amp; queue[2i+2]</span>
      </div>

      <div class="grid gap-3 md:grid-cols-12">
        <!-- 二叉树 SVG 视图 (7 列) -->
        <div class="overflow-x-auto rounded-xl border border-slate-200/70 bg-slate-50/40 p-2 md:col-span-7 dark:border-slate-800 dark:bg-slate-900/40">
          <p class="mb-1 px-1 text-[11px] font-semibold text-slate-700 dark:text-slate-200">逻辑视图：完全二叉树 (Min-Heap)</p>
          <svg viewBox="0 0 460 242" class="mx-auto w-full min-w-[340px] select-none" role="img" aria-label="PriorityQueue 二叉堆逻辑树视图">
            <text v-if="treeNodes.length === 0" x="230" y="124" text-anchor="middle" class="fill-slate-400 text-[12px]">
              堆为空 (size = 0)，点击 offer 插入元素
            </text>

            <!-- 边 -->
            <template v-for="node in treeNodes" :key="`edge-${node.idx}`">
              <line
                v-if="node.px !== null && node.py !== null"
                :x1="node.px"
                :y1="node.py"
                :x2="node.x"
                :y2="node.y"
                stroke-width="2"
                class="stroke-slate-300 transition-[fill,stroke,opacity] duration-300 dark:stroke-slate-700"
              />
            </template>

            <!-- 节点 -->
            <g v-for="node in treeNodes" :key="`node-${node.idx}`" :transform="`translate(${node.x}, ${node.y})`">
              <circle
                r="17"
                stroke-width="2"
                class="transition-[fill,stroke,opacity] duration-300"
                :class="nodeTone(node.idx)"
              />
              <text
                y="4"
                text-anchor="middle"
                class="font-mono text-[12px] font-bold"
                :class="isSolidIdx(node.idx) ? 'fill-white' : 'fill-slate-800 dark:fill-slate-100'"
              >
                {{ node.val }}
              </text>
              <text y="28" text-anchor="middle" class="fill-slate-400 font-mono text-[9px]">[{{ node.idx }}]</text>
            </g>
          </svg>
        </div>

        <!-- 底层数组存储 + 遍历对比 (5 列) -->
        <div class="flex flex-col justify-between space-y-3 rounded-xl border border-slate-200/70 bg-slate-50/40 p-3 md:col-span-5 dark:border-slate-800 dark:bg-slate-900/40">
          <div>
            <p class="mb-2 text-[11px] font-semibold text-slate-700 dark:text-slate-200">物理存储：Object[] queue 数组</p>
            <div v-if="cur.heap.length === 0" class="rounded-lg border border-dashed border-slate-300 py-4 text-center font-mono text-xs text-slate-400 dark:border-slate-700">
              [] (空数组)
            </div>
            <div v-else class="grid grid-cols-5 gap-1.5">
              <div
                v-for="(val, idx) in cur.heap"
                :key="`arr-${idx}`"
                class="flex flex-col items-center rounded-lg border py-1.5 font-mono transition-[fill,stroke,opacity] duration-300"
                :class="arrayCellClass(idx)"
              >
                <span class="text-xs font-bold">{{ val }}</span>
                <span class="text-[9px] opacity-75">[{{ idx }}]</span>
              </div>
            </div>
          </div>

          <!-- Q72 / Q74 核心对比卡 -->
          <div class="space-y-2 rounded-lg border border-slate-200 bg-white p-2.5 text-[11px] dark:border-slate-700 dark:bg-slate-900">
            <div>
              <span class="font-semibold text-purple-600 dark:text-purple-400">for-each 遍历输出（原始数组，无序！）：</span>
              <p class="mt-0.5 font-mono text-slate-700 dark:text-slate-200">
                [ {{ cur.heap.join(', ') }} ]
              </p>
            </div>
            <div class="border-t border-slate-100 pt-1.5 dark:border-slate-800">
              <span class="font-semibold text-emerald-600 dark:text-emerald-400">循环 poll() 出队序列（保证升序）：</span>
              <p class="mt-0.5 font-mono text-slate-700 dark:text-slate-200">
                {{ cur.polled.length ? `[ ${cur.polled.join(' → ')} ]` : '尚未 poll 出队' }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Guided 模式控制 -->
    <template v-if="mode === 'guided'">
      <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3.5 text-sm dark:border-slate-800 dark:bg-slate-900/80">
        <p class="font-semibold text-slate-900 dark:text-white">{{ currentGuided.title }}</p>
        <p class="mt-1.5 leading-relaxed text-slate-600 dark:text-slate-300">{{ currentGuided.speech }}</p>
        <p class="mt-2 flex items-start gap-1.5 rounded-lg bg-amber-50/90 px-2.5 py-1.5 text-xs text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
          <Lightbulb class="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" />
          <span>{{ currentGuided.tip }}</span>
        </p>
      </div>

      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-1.5">
          <button
            v-for="(_, i) in guidedSteps"
            :key="i"
            class="h-2 rounded-full transition-[width,background-color] duration-200"
            :class="i === stepIdx ? 'w-6 bg-brand-600' : 'w-2 bg-slate-300 hover:bg-slate-400 dark:bg-slate-700'"
            :title="`第 ${i + 1} 步`"
            @click="stopPlay(); stepIdx = i"
          />
        </div>

        <div class="flex items-center gap-2">
          <button class="btn-ghost h-8 px-2.5 text-xs" :disabled="stepIdx === 0" @click="prevStep">
            <ChevronLeft class="h-3.5 w-3.5" /> 上一步
          </button>
          <button class="btn-primary h-8 px-3 text-xs" @click="togglePlay">
            <component :is="playing ? Pause : Play" class="h-3.5 w-3.5" />
            {{ playing ? '暂停' : '自动播放' }}
          </button>
          <button class="btn-ghost h-8 px-2.5 text-xs" :disabled="stepIdx === guidedSteps.length - 1" @click="nextStep">
            下一步 <ChevronRight class="h-3.5 w-3.5" />
          </button>
          <button class="btn-ghost h-8 px-2 text-xs" title="重置" @click="resetGuided">
            <RotateCcw class="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </template>

    <!-- Sandbox 模式控制 -->
    <template v-else>
      <div class="flex items-start gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-xs leading-relaxed text-slate-600 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300">
        <CheckCircle2 class="mt-0.5 h-4 w-4 shrink-0 text-brand-500" />
        <span>{{ sbLog }}</span>
      </div>

      <!-- 预设 offer + poll -->
      <div class="flex flex-wrap items-center gap-2">
        <button
          v-for="val in PRESET_OFFERS"
          :key="`offer-${val}`"
          class="btn-ghost h-8 px-2.5 font-mono text-xs"
          :disabled="animating"
          @click="offerValue(val)"
        >
          <Plus class="h-3.5 w-3.5 text-brand-500" /> offer({{ val }})
        </button>

        <button class="btn-primary h-8 px-3 text-xs" :disabled="animating || sbHeap.length === 0" @click="pollHeap">
          <Minus class="h-3.5 w-3.5" /> poll() 弹出堆顶
        </button>
      </div>

      <!-- 自定义输入 + for-each + 重置/清空 -->
      <div class="flex flex-wrap items-center gap-2">
        <div class="flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-2.5 py-1 text-xs dark:border-slate-700 dark:bg-slate-900">
          <span class="font-mono text-slate-400">offer(</span>
          <input
            v-model.number="customNum"
            type="number"
            min="-99"
            max="999"
            class="w-14 rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-800 outline-none focus:ring-1 focus:ring-brand-500 dark:bg-slate-800 dark:text-slate-100"
            @keyup.enter="submitCustomOffer"
          />
          <span class="font-mono text-slate-400">)</span>
        </div>
        <button class="btn-ghost h-8 px-2.5 text-xs" :disabled="animating" @click="submitCustomOffer">
          插入
        </button>
        <button class="btn-ghost h-8 px-2.5 text-xs" :disabled="animating" @click="demonstrateForEach">
          <ArrowUpDown class="h-3.5 w-3.5 text-purple-500" /> 模拟 for-each 遍历
        </button>
        <button class="btn-ghost ml-auto h-8 px-2.5 text-xs" @click="resetSandbox(true)">
          清空为空堆
        </button>
        <button class="btn-ghost h-8 px-2.5 text-xs" @click="resetSandbox(false)">
          <RotateCcw class="h-3.5 w-3.5" /> 重置默认堆
        </button>
      </div>
    </template>
  </div>
</template>
