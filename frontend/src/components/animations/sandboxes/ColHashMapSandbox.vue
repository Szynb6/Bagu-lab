<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  GitBranch,
  Pause,
  Play,
  Plus,
  RotateCcw,
  Sparkles,
  TriangleAlert,
} from 'lucide-vue-next'

type Mode = 'guided' | 'sandbox'

interface MapNode {
  key: string
  val: string
  hashHex: string
  isNew?: boolean
}

interface HashTrace {
  key: string
  rawHash: string
  mixedHash: string
  index: number
  action: string
}

interface GuidedMapStep {
  title: string
  badge: string
  tableLen: number
  buckets: MapNode[][]
  treeifiedBucket2: boolean
  trace: HashTrace
  speech: string
  tip: string
}

const BUCKET_COUNT = 8

function emptyBuckets(): MapNode[][] {
  return Array.from({ length: BUCKET_COUNT }, () => [])
}

const guidedSteps: GuidedMapStep[] = [
  {
    title: '0. 构造与懒加载：new HashMap() 初始并不分配数组',
    badge: '懒加载 table=null',
    tableLen: 8,
    buckets: emptyBuckets(),
    treeifiedBucket2: false,
    trace: {
      key: '-',
      rawHash: '-',
      mixedHash: '-',
      index: -1,
      action: '等待首次 put() 触发 resize() 初始化数组',
    },
    speech: 'JDK 1.8 中 new HashMap() 只会初始化负载因子 loadFactor=0.75，底层数组 table 为 null；直到第一次调用 put() 时才通过 resize() 懒分配数组（默认初始容量 16，此处用 table[8] 演示）。',
    tip: '高频面试点：为什么容量必须是 2 的幂？因为下标公式 (n - 1) & hash 只有当 n 为 2 的幂时，才能等价于取模 hash % n 且高位全 0 低位全 1，位运算极快！',
  },
  {
    title: '1. 扰动函数 + 路由定位：put("java") 落入桶 #1',
    badge: '扰动函数 (h ^ h>>>16)',
    tableLen: 8,
    buckets: (() => {
      const b = emptyBuckets()
      b[1] = [{ key: 'java', val: 'v1', hashHex: '0x003197a1', isNew: true }]
      return b
    })(),
    treeifiedBucket2: false,
    trace: {
      key: 'java',
      rawHash: '0x00319790',
      mixedHash: '0x003197a1',
      index: 1,
      action: '桶 #1 为空 → 直接插入新 Node',
    },
    speech: '计算 hash = (h = key.hashCode()) ^ (h >>> 16)，将高 16 位异或混入低 16 位减少哈希碰撞；再用 (n - 1) & hash 得到桶下标 1，桶为空则直接放入 Node。',
    tip: '为什么要高 16 位异或低 16 位？当数组长度 n 较小时（如 16），(n - 1) & hash 只用到最低几位，混入高 16 位能让高位特征也参与路由！',
  },
  {
    title: '2. 哈希冲突 & JDK 1.8 尾插法：k1 → k2 → k3',
    badge: '1.8 尾插法',
    tableLen: 8,
    buckets: (() => {
      const b = emptyBuckets()
      b[1] = [{ key: 'java', val: 'v1', hashHex: '0x003197a1' }]
      b[2] = [
        { key: 'k1', val: '1', hashHex: '0x00001002' },
        { key: 'k2', val: '2', hashHex: '0x0000200a' },
        { key: 'k3', val: '3', hashHex: '0x00003012', isNew: true },
      ]
      return b
    })(),
    treeifiedBucket2: false,
    trace: {
      key: 'k3',
      rawHash: '0x00003012',
      mixedHash: '0x00003012',
      index: 2,
      action: '发生冲突 → 遍历链表比较 equals，挂到尾部',
    },
    speech: 'k1、k2、k3 低 3 位都是 010（index=2）发生冲突！JDK 1.8 遍历链表逐一比对 hash 和 equals：若 key 相同则覆盖 value，否则使用【尾插法】挂到链表末尾。',
    tip: 'JDK 1.7 采用“头插法”，并发扩容时链表反转容易形成环形链表导致 get() 死循环 CPU 100%；JDK 1.8 改为尾插法彻底解决了扩容死链（但仍非线程安全，并发仍可能丢数据）。',
  },
  {
    title: '3. 冲突链长达到 8 但数组 < 64：先 resize() 扩容不树化！',
    badge: '优先扩容 (len≥8, cap<64)',
    tableLen: 8,
    buckets: (() => {
      const b = emptyBuckets()
      b[1] = [{ key: 'java', val: 'v1', hashHex: '0x003197a1' }]
      b[2] = ['k1', 'k2', 'k3', 'k4', 'k5', 'k6', 'k7', 'k8'].map((k, idx) => ({
        key: k,
        val: `${idx + 1}`,
        hashHex: `0x0000${idx + 1}002`,
        isNew: idx === 7,
      }))
      return b
    })(),
    treeifiedBucket2: false,
    trace: {
      key: 'k8',
      rawHash: '0x00008002',
      mixedHash: '0x00008002',
      index: 2,
      action: 'len=8 ≥ 8，但 table.length=8 < 64 → 优先触发 resize()！',
    },
    speech: '桶 #2 链长已达 8（TREEIFY_THRESHOLD=8）！但源码 treeifyBin() 会先判断 tab.length < MIN_TREEIFY_CAPACITY(64)：此时优先调用 resize() 扩容打散链表，而不会立刻转红黑树！',
    tip: '易漏细节：准确地说，当链表已有 8 个节点再插入第 9 个（binCount >= 7）调用 treeifyBin()，且满足 table.length >= 64 才真正树化。',
  },
  {
    title: '4. 满足 len ≥ 8 且 table.length ≥ 64 → 升级红黑树 (O(log n))',
    badge: '树化成功 TreeNode',
    tableLen: 64,
    buckets: (() => {
      const b = emptyBuckets()
      b[1] = [{ key: 'java', val: 'v1', hashHex: '0x003197a1' }]
      b[2] = ['k1', 'k2', 'k3', 'k4', 'k5', 'k6', 'k7', 'k8'].map((k, idx) => ({
        key: k,
        val: `${idx + 1}`,
        hashHex: `0x0000${idx + 1}002`,
      }))
      return b
    })(),
    treeifiedBucket2: true,
    trace: {
      key: 'k8',
      rawHash: '0x00008002',
      mixedHash: '0x00008002',
      index: 2,
      action: 'len ≥ 8 且 table.length ≥ 64 → Node 转为 TreeNode 红黑树',
    },
    speech: '当数组容量已扩容到 ≥ 64 且单桶链长仍达到树化阈值时，桶内普通 Node 升级为红黑树 TreeNode，将最坏查找时间复杂度从 O(n) 降为 O(log n)！',
    tip: '为什么树化阈值是 8、退化回链表阈值是 6？① 泊松分布显示随机 hash 链长达到 8 概率仅千万分之六；② 留 6~8 缓冲区间防止节点反复增删导致频繁树化/退化抖动。',
  },
]

const PRESET_DEFS: Record<string, { rawHash: string; mixedHash: string; bucket: number; defaultVal: string }> = {
  java: { rawHash: '0x00319790', mixedHash: '0x003197a1', bucket: 1, defaultVal: 'JDK21' },
  spring: { rawHash: '0x699b9d3d', mixedHash: '0x699bf4a5', bucket: 5, defaultVal: 'Boot3' },
  go: { rawHash: '0x00000ce4', mixedHash: '0x00000ce4', bucket: 4, defaultVal: '1.22' },
  k1: { rawHash: '0x00001002', mixedHash: '0x00001002', bucket: 2, defaultVal: 'v1' },
  k2: { rawHash: '0x0000200a', mixedHash: '0x0000200a', bucket: 2, defaultVal: 'v2' },
  k3: { rawHash: '0x00003012', mixedHash: '0x00003012', bucket: 2, defaultVal: 'v3' },
  k4: { rawHash: '0x0000401a', mixedHash: '0x0000401a', bucket: 2, defaultVal: 'v4' },
}

const mode = ref<Mode>('guided')
const stepIdx = ref(0)
const playing = ref(false)
let playTimer: number | undefined

// 沙盒模式状态
const sbBuckets = ref<MapNode[][]>(emptyBuckets())
const simulateCap64 = ref(false)
const sbPutCount = ref(0)
const sbTrace = ref<HashTrace>({
  key: '-',
  rawHash: '-',
  mixedHash: '-',
  index: -1,
  action: '等待 put(key, val) 操作',
})
const sbLog = ref('点击下方预设 put 按钮向 table[8] 插入键值；k1~k4 故意哈希碰撞到桶 #2，点击「模拟高冲突满8」可触发树化判定！')

const cur = computed(() => {
  if (mode.value === 'guided') {
    const s = guidedSteps[stepIdx.value]!
    return {
      tableLen: s.tableLen,
      buckets: s.buckets,
      treeifiedBucket2: s.treeifiedBucket2,
      trace: s.trace,
    }
  }
  const len2 = sbBuckets.value[2]!.length
  return {
    tableLen: simulateCap64.value ? 64 : 8,
    buckets: sbBuckets.value,
    treeifiedBucket2: len2 >= 8 && simulateCap64.value,
    trace: sbTrace.value,
  }
})

const currentGuided = computed(() => guidedSteps[stepIdx.value]!)

const bucket2Len = computed(() => cur.value.buckets[2]?.length ?? 0)

function stopPlay() {
  playing.value = false
  if (playTimer) {
    clearInterval(playTimer)
    playTimer = undefined
  }
}

function switchMode(m: Mode) {
  stopPlay()
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

onBeforeUnmount(stopPlay)

function clearNewFlags() {
  sbBuckets.value.forEach((chain) => {
    chain.forEach((n) => {
      n.isNew = false
    })
  })
}

function putKey(key: string) {
  clearNewFlags()
  sbPutCount.value += 1
  const def = PRESET_DEFS[key] ?? {
    rawHash: `0x0000${sbPutCount.value}002`,
    mixedHash: `0x0000${sbPutCount.value}002`,
    bucket: 2,
    defaultVal: `v${sbPutCount.value}`,
  }
  const chain = sbBuckets.value[def.bucket]!
  const existing = chain.find((n) => n.key === key)

  if (existing) {
    existing.val = `${def.defaultVal}#${sbPutCount.value}`
    existing.isNew = true
    sbTrace.value = {
      key,
      rawHash: def.rawHash,
      mixedHash: def.mixedHash,
      index: def.bucket,
      action: `桶 #${def.bucket} 命中相同 key="${key}" → 原地覆盖 value`,
    }
    sbLog.value = `put("${key}")：经扰动与 & (8-1) 定位到桶 #${def.bucket}，equals 命中旧节点，直接覆盖更新 value！`
    return
  }

  chain.push({
    key,
    val: def.defaultVal,
    hashHex: def.mixedHash,
    isNew: true,
  })

  const len = chain.length
  let action = len === 1
    ? `桶 #${def.bucket} 为空 → 直接放入头节点`
    : `桶 #${def.bucket} 碰撞 → JDK 1.8 尾插法追加 (len=${len})`

  if (def.bucket === 2 && len >= 8) {
    action = simulateCap64.value
      ? `len=${len} ≥ 8 且 table.length=64 ≥ 64 → 桶 #2 升级为红黑树！`
      : `len=${len} ≥ 8，但 table.length=8 < 64 → 需先 resize() 扩容打散！`
  }

  sbTrace.value = {
    key,
    rawHash: def.rawHash,
    mixedHash: def.mixedHash,
    index: def.bucket,
    action,
  }
  sbLog.value = `put("${key}") → 计算下标 ${def.bucket}：${action}`
}

function fillHighCollision8() {
  clearNewFlags()
  const targetKeys = ['k1', 'k2', 'k3', 'k4', 'k5', 'k6', 'k7', 'k8']
  const chain = sbBuckets.value[2]!
  for (const k of targetKeys) {
    if (chain.length >= 8) break
    if (!chain.some((n) => n.key === k)) {
      chain.push({
        key: k,
        val: `v${chain.length + 1}`,
        hashHex: `0x0000${chain.length + 1}002`,
        isNew: true,
      })
    }
  }
  const len = chain.length
  sbTrace.value = {
    key: 'k8',
    rawHash: '0x00008002',
    mixedHash: '0x00008002',
    index: 2,
    action: simulateCap64.value
      ? `桶 #2 len=${len} ≥ 8 且 table.length ≥ 64 → 红黑树 TreeNode 生效！`
      : `桶 #2 len=${len} ≥ 8，但 table.length=8 < 64 → 应先扩容；可开启右侧「table.length≥64」看树化！`,
  }
  sbLog.value = simulateCap64.value
    ? `高冲突注入完成：桶 #2 链长达到 ${len} 且模拟容量 ≥ 64，桶 #2 已自动升级为红黑树（TreeNode）！`
    : `高冲突注入完成：桶 #2 链长达到 ${len}（达到 TREEIFY_THRESHOLD=8），但当前 table.length=8 < 64，先触发 resize()；切换「模拟容量 ≥ 64」即可观察升级红黑树！`
}

function toggleCap64() {
  simulateCap64.value = !simulateCap64.value
  const len = sbBuckets.value[2]!.length
  if (simulateCap64.value && len >= 8) {
    sbLog.value = `已切换为 table.length = 64（≥ MIN_TREEIFY_CAPACITY）：桶 #2 当前 len=${len} ≥ 8，立即升级为红黑树！`
  } else if (!simulateCap64.value && len >= 8) {
    sbLog.value = `已切回 table.length = 8（< 64）：桶 #2 虽然 len=${len} ≥ 8，但优先触发 resize() 扩容而非直接树化。`
  } else {
    sbLog.value = `当前模拟数组容量切换为 table.length = ${simulateCap64.value ? 64 : 8}。`
  }
}

function resetSandbox() {
  sbBuckets.value = emptyBuckets()
  simulateCap64.value = false
  sbPutCount.value = 0
  sbTrace.value = {
    key: '-',
    rawHash: '-',
    mixedHash: '-',
    index: -1,
    action: '已重置为空数组 table[8]',
  }
  sbLog.value = 'HashMap 沙盒已重置！点击下方按钮继续 put 实验。'
}
</script>

<template>
  <div class="space-y-4">
    <!-- 顶部模式切换 + 树化指示 -->
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div class="inline-flex rounded-xl border border-slate-200 bg-slate-100/80 p-0.5 text-xs dark:border-slate-700 dark:bg-slate-800">
        <button
          class="rounded-lg px-3 py-1.5 font-medium transition"
          :class="mode === 'guided' ? 'bg-white text-brand-700 shadow-xs dark:bg-slate-900 dark:text-brand-300' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'"
          @click="switchMode('guided')"
        >
          put &amp; 树化演练 (5 步)
        </button>
        <button
          class="rounded-lg px-3 py-1.5 font-medium transition"
          :class="mode === 'sandbox' ? 'bg-white text-brand-700 shadow-xs dark:bg-slate-900 dark:text-brand-300' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'"
          @click="switchMode('sandbox')"
        >
          动手 HashMap 沙盒
        </button>
      </div>

      <span
        v-if="mode === 'guided'"
        class="rounded-full bg-brand-100/80 px-2.5 py-0.5 font-mono text-[11px] font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-300"
      >
        {{ currentGuided.badge }} · {{ stepIdx + 1 }}/{{ guidedSteps.length }}
      </span>
      <div v-else class="flex items-center gap-2 font-mono text-[11px]">
        <span class="rounded-full bg-slate-100 px-2.5 py-0.5 text-slate-600 dark:bg-slate-800 dark:text-slate-300">
          table.length = {{ cur.tableLen }}
        </span>
        <span
          class="rounded-full px-2.5 py-0.5 font-semibold"
          :class="cur.treeifiedBucket2
            ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300'
            : bucket2Len >= 8
              ? 'bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-300'
              : 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400'"
        >
          桶#2 len={{ bucket2Len }} {{ cur.treeifiedBucket2 ? '· 已树化' : bucket2Len >= 8 ? '· 待扩容' : '' }}
        </span>
      </div>
    </div>

    <!-- Hash 计算分解条 -->
    <div class="rounded-xl border border-slate-200/80 bg-slate-50/70 p-3 dark:border-slate-800 dark:bg-slate-900/70">
      <div class="flex flex-wrap items-center justify-between gap-2 text-xs">
        <div class="flex flex-wrap items-center gap-2 font-mono">
          <span class="rounded-md bg-white px-2 py-0.5 font-semibold text-slate-700 shadow-2xs dark:bg-slate-800 dark:text-slate-200">
            key: "{{ cur.trace.key }}"
          </span>
          <span class="text-slate-400">→</span>
          <span class="text-slate-600 dark:text-slate-300">h={{ cur.trace.rawHash }}</span>
          <span class="text-slate-400">→</span>
          <span class="rounded bg-brand-50 px-1.5 py-0.5 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300">
            (h ^ h&gt;&gt;&gt;16)={{ cur.trace.mixedHash }}
          </span>
          <span class="text-slate-400">→</span>
          <span class="rounded bg-emerald-50 px-1.5 py-0.5 font-bold text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300">
            (n-1)&amp;hash = {{ cur.trace.index >= 0 ? `#${cur.trace.index}` : '-' }}
          </span>
        </div>
        <span class="text-[11px] font-medium text-slate-500 dark:text-slate-400">{{ cur.trace.action }}</span>
      </div>
    </div>

    <!-- table[8] 桶数组 + 链表/红黑树视图 -->
    <div class="rounded-xl border border-slate-200/80 bg-white p-3.5 dark:border-slate-800 dark:bg-slate-950/70">
      <div class="mb-2.5 flex flex-wrap items-center justify-between gap-2 text-xs">
        <span class="font-semibold text-slate-800 dark:text-slate-100">JDK 1.8 HashMap 哈希桶阵列 (table[0..7])</span>
        <span
          class="inline-flex items-center gap-1 rounded-md px-2 py-0.5 font-mono text-[10.5px]"
          :class="cur.treeifiedBucket2
            ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300'
            : bucket2Len >= 8
              ? 'bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-300'
              : 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400'"
        >
          <GitBranch class="h-3 w-3" />
          树化条件: len≥8 ({{ bucket2Len }}/8) &amp;&amp; length≥64 ({{ cur.tableLen }}/64)
        </span>
      </div>

      <div class="space-y-1.5">
        <div
          v-for="(chain, idx) in cur.buckets"
          :key="`bucket-${idx}`"
          class="flex flex-wrap items-center gap-2 rounded-lg border px-2.5 py-1.5 transition"
          :class="cur.trace.index === idx
            ? 'border-brand-300 bg-brand-50/40 dark:border-brand-500/40 dark:bg-brand-500/10'
            : 'border-slate-100 bg-slate-50/50 dark:border-slate-800/80 dark:bg-slate-900/40'"
        >
          <!-- 桶索引 -->
          <div class="flex w-14 shrink-0 items-center justify-between">
            <span class="rounded bg-slate-200/80 px-1.5 py-0.5 font-mono text-[11px] font-bold text-slate-700 dark:bg-slate-800 dark:text-slate-200">
              [{{ idx }}]
            </span>
            <span class="font-mono text-[10px] text-slate-400">len={{ chain.length }}</span>
          </div>

          <!-- 桶内容：空 / 链表 / 红黑树 -->
          <div v-if="chain.length === 0" class="font-mono text-[11px] text-slate-400">
            null
          </div>

          <!-- 桶 #2 树化视图 -->
          <div v-else-if="idx === 2 && cur.treeifiedBucket2" class="flex flex-1 flex-wrap items-center gap-2">
            <span class="inline-flex items-center gap-1 rounded-md bg-gradient-to-r from-red-600 to-slate-900 px-2 py-1 font-mono text-[10.5px] font-bold text-white shadow-2xs">
              <GitBranch class="h-3 w-3" /> 红黑树 TreeNode (O(log n))
            </span>
            <div class="flex flex-wrap items-center gap-1">
              <span
                v-for="(node, nIdx) in chain"
                :key="node.key"
                class="inline-flex items-center gap-1 rounded-md border px-2 py-0.5 font-mono text-[11px] font-semibold"
                :class="nIdx % 2 === 0
                  ? 'border-slate-700 bg-slate-900 text-white dark:border-slate-500'
                  : 'border-red-500 bg-red-600 text-white'"
              >
                {{ node.key }}:{{ node.val }}
              </span>
            </div>
          </div>

          <!-- 普通链表尾插视图 -->
          <div v-else class="flex flex-1 flex-wrap items-center gap-1.5">
            <template v-for="(node, nIdx) in chain" :key="node.key">
              <span
                class="inline-flex items-center gap-1 rounded-md border px-2 py-0.5 font-mono text-[11px] font-semibold transition"
                :class="node.isNew
                  ? 'border-brand-500 bg-brand-600 text-white shadow-xs'
                  : 'border-slate-300 bg-white text-slate-700 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200'"
              >
                {{ node.key }}={{ node.val }}
              </span>
              <span v-if="nIdx < chain.length - 1" class="font-mono text-[11px] text-slate-400">→</span>
            </template>
            <span class="font-mono text-[10.5px] text-slate-400">→ null</span>

            <span
              v-if="idx === 2 && chain.length >= 8 && !cur.treeifiedBucket2"
              class="ml-auto inline-flex items-center gap-1 rounded-md bg-amber-100 px-2 py-0.5 text-[10.5px] font-semibold text-amber-800 dark:bg-amber-500/20 dark:text-amber-300"
            >
              <TriangleAlert class="h-3 w-3" /> len≥8 但 cap&lt;64：先 resize() 扩容！
            </span>
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
          <Sparkles class="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" />
          <span>{{ currentGuided.tip }}</span>
        </p>
      </div>

      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-1.5">
          <button
            v-for="(_, i) in guidedSteps"
            :key="i"
            class="h-2 rounded-full transition-all"
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

      <div class="flex flex-wrap items-center gap-2">
        <button class="btn-ghost h-8 px-2.5 font-mono text-xs" @click="putKey('java')">
          <Plus class="h-3.5 w-3.5 text-brand-500" /> put("java")
        </button>
        <button class="btn-ghost h-8 px-2.5 font-mono text-xs" @click="putKey('spring')">
          <Plus class="h-3.5 w-3.5 text-brand-500" /> put("spring")
        </button>
        <button
          v-for="k in ['k1', 'k2', 'k3', 'k4']"
          :key="k"
          class="btn-ghost h-8 px-2.5 font-mono text-xs"
          @click="putKey(k)"
        >
          <Plus class="h-3.5 w-3.5 text-amber-500" /> put("{{ k }}")
        </button>
        <button class="btn-primary h-8 px-3 text-xs" @click="fillHighCollision8">
          模拟高冲突满8 (桶#2)
        </button>
        <button
          class="btn-ghost h-8 px-3 text-xs"
          :class="simulateCap64 ? 'border-emerald-500 text-emerald-700 dark:border-emerald-400 dark:text-emerald-300' : ''"
          @click="toggleCap64"
        >
          <GitBranch class="h-3.5 w-3.5" />
          {{ simulateCap64 ? '已假设 length≥64 (可树化)' : '假设 table.length ≥ 64' }}
        </button>
        <button class="btn-ghost ml-auto h-8 px-2.5 text-xs" @click="resetSandbox">
          <RotateCcw class="h-3.5 w-3.5" /> 重置
        </button>
      </div>
    </template>
  </div>
</template>
