<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  ChevronLeft,
  ChevronRight,
  Cpu,
  Pause,
  Play,
  Plus,
  RotateCcw,
  Lightbulb,
  Trash2,
  TriangleAlert,
  Zap,
} from 'lucide-vue-next'

type Mode = 'guided' | 'sandbox'
type RegionName = 'S0' | 'S1'

interface HeapObj {
  id: string
  age: number
  isLarge?: boolean
  alive: boolean
  reason?: string
}

interface GuidedHeapStep {
  title: string
  badge: string
  fromRegion: RegionName
  threshold: number
  eden: HeapObj[]
  s0: HeapObj[]
  s1: HeapObj[]
  old: HeapObj[]
  speech: string
  tip: string
}

const EDEN_CAP = 4
const SURV_CAP = 2
const OLD_CAP = 6
const MAX_AGE = 3

const guidedSteps: GuidedHeapStep[] = [
  {
    title: '0. 堆内存分代结构就绪 (8 : 1 : 1 经典比例)',
    badge: '初始堆结构',
    fromRegion: 'S0',
    threshold: 3,
    eden: [],
    s0: [],
    s1: [],
    old: [],
    speech: 'HotSpot 经典分代堆分为新生代（Eden + Survivor From/To）和老年代。为便于观察，这里设 Eden=4 槽、S0=2 槽、S1=2 槽、Old=6 槽，晋升年龄阈值 MaxTenuringThreshold=3。',
    tip: '为什么 Survivor 要有两块 (S0/S1)？因为新生代采用复制算法，必须始终留一块空闲的 To 区用于零碎片复制回收！',
  },
  {
    title: '1. 绝大多数小对象优先在 Eden 区分配',
    badge: 'Eden 分配',
    fromRegion: 'S0',
    threshold: 3,
    eden: [
      { id: 'O1', age: 0, alive: true },
      { id: 'O2', age: 0, alive: false },
      { id: 'O3', age: 0, alive: false },
      { id: 'O4', age: 0, alive: false },
    ],
    s0: [],
    s1: [],
    old: [],
    speech: '新创建的朝生夕死小对象 O1~O4 全部进入 Eden 区（age=0）。其中 O2、O3、O4 很快失去引用成为垃圾，只有 O1 依然存活。',
    tip: 'IBM 研究表明约 98% 的对象“朝生夕死”，所以每次 Minor GC 只需要复制极少数存活对象，效率极高。',
  },
  {
    title: '2. Eden 满触发 Minor GC：存活入 S1，age+1，From/To 互换',
    badge: 'Minor GC #1',
    fromRegion: 'S1',
    threshold: 3,
    eden: [],
    s0: [],
    s1: [{ id: 'O1', age: 1, alive: true, reason: 'Eden存活' }],
    old: [],
    speech: '触发 Minor GC：回收垃圾 O2~O4，唯一存活的 O1 被复制到 To 区 (S1) 且 age 变为 1；随后 Eden 清空，S1 翻转成为新的 From 区。',
    tip: '每次 Minor GC 都会清空 Eden 和旧 From 区，存活对象的分代年龄都会 +1。',
  },
  {
    title: '3. 多次 Minor GC 后达到年龄阈值 (age ≥ 3) → 晋升老年代',
    badge: '年龄阈值晋升',
    fromRegion: 'S0',
    threshold: 3,
    eden: [{ id: 'O5', age: 0, alive: true }],
    s0: [{ id: 'O5', age: 1, alive: true }],
    s1: [],
    old: [{ id: 'O1', age: 3, alive: true, reason: 'age≥3 晋升' }],
    speech: 'O1 在 S0/S1 之间继续熬过两次 Minor GC，年龄涨到 3（达到演示阈值 MaxTenuringThreshold=3，默认实际为 15），正式晋升到老年代！',
    tip: '对象头 Mark Word 中分代年龄占 4 bit，因此最大年龄上限就是 15（CMS 下默认 6）。',
  },
  {
    title: '4. 动态年龄判定 (>50%) 与 空间分配担保',
    badge: '动态年龄 & 担保',
    fromRegion: 'S1',
    threshold: 1,
    eden: [],
    s0: [],
    s1: [
      { id: 'O6', age: 1, alive: true },
      { id: 'O7', age: 1, alive: true },
    ],
    old: [
      { id: 'O1', age: 3, alive: true, reason: 'age≥3 晋升' },
      { id: 'O5', age: 2, alive: true, reason: '担保溢出进老年代' },
    ],
    speech: '若 Survivor 中 age≤k 对象累计大小超过 Survivor 空间的 50%（如 S1 的 O6、O7 占满 2/2=100%），下轮阈值会动态降为 k=1！若 To 区装不下剩余存活对象（如 O5），则通过【空间分配担保】直接进老年代。',
    tip: '面试必考两大提前晋升机制：① 动态年龄判定（累计 age≤k 占比 > 50%）；② Survivor 装不下触发空间分配担保。',
  },
  {
    title: '5. 大对象直接进入老年代 (-XX:PretenureSizeThreshold)',
    badge: '大对象直入老年代',
    fromRegion: 'S1',
    threshold: 1,
    eden: [],
    s0: [],
    s1: [],
    old: [
      { id: 'O1', age: 3, alive: true, reason: 'age≥3 晋升' },
      { id: 'O5', age: 2, alive: true, reason: '担保进老年代' },
      { id: 'O6', age: 2, alive: true, reason: '动态年龄晋升' },
      { id: 'O7', age: 2, alive: true, reason: '动态年龄晋升' },
      { id: 'Big#1', age: 0, isLarge: true, alive: true, reason: '大对象直入' },
    ],
    speech: '需要大量连续内存的数组或长字符串（超过 -XX:PretenureSizeThreshold）会绕过 Eden 直接在老年代分配，避免在 Eden 与 Survivor 间发生巨额内存复制；同时动态年龄对象 O6/O7 也已晋升老年代。',
    tip: '老年代满时会触发 Major GC / Full GC（通常采用标记-整理或标记-清除，STW 时间远长于 Minor GC）。',
  },
]

const mode = ref<Mode>('guided')
const stepIdx = ref(0)
const playing = ref(false)
let playTimer: number | undefined

// 沙盒模式状态
const nextSmallNo = ref(1)
const nextLargeNo = ref(1)
const minorGcCount = ref(0)
const fullGcCount = ref(0)
const sbFrom = ref<RegionName>('S0')
const sbThreshold = ref(MAX_AGE)
const sbEden = ref<HeapObj[]>([])
const sbS0 = ref<HeapObj[]>([])
const sbS1 = ref<HeapObj[]>([])
const sbOld = ref<HeapObj[]>([])
const sbWarn = ref(false)
const sbLog = ref('点击「分配小对象(Eden)」或「分配大对象」；点击任意对象卡片可切换【存活 / 垃圾(待回收)】状态！')
const logHistory = ref<string[]>([])

function pushLog(msg: string, warn = false) {
  sbLog.value = msg
  sbWarn.value = warn
  logHistory.value = [msg, ...logHistory.value].slice(0, 4)
}

const cur = computed(() => {
  if (mode.value === 'guided') {
    const s = guidedSteps[stepIdx.value]!
    return {
      fromRegion: s.fromRegion,
      threshold: s.threshold,
      eden: s.eden,
      s0: s.s0,
      s1: s.s1,
      old: s.old,
    }
  }
  return {
    fromRegion: sbFrom.value,
    threshold: sbThreshold.value,
    eden: sbEden.value,
    s0: sbS0.value,
    s1: sbS1.value,
    old: sbOld.value,
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
  }, 2500)
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

function computeDynamicThreshold(survivors: HeapObj[]): number {
  if (survivors.length === 0) return MAX_AGE
  const halfCap = SURV_CAP / 2 // 1 slot; strictly > 50% means >= 2 slots
  for (let age = 1; age <= MAX_AGE; age++) {
    const cumCount = survivors.filter((o) => o.age <= age).length
    if (cumCount > halfCap) {
      return Math.min(age, MAX_AGE)
    }
  }
  return MAX_AGE
}

function toggleObjAlive(region: 'eden' | 's0' | 's1' | 'old', idx: number) {
  if (mode.value !== 'sandbox') return
  const list = region === 'eden'
    ? sbEden.value
    : region === 's0'
      ? sbS0.value
      : region === 's1'
        ? sbS1.value
        : sbOld.value
  const item = list[idx]
  if (!item) return
  item.alive = !item.alive
  pushLog(`${item.id} 状态切换为【${item.alive ? '存活 (GC Roots 可达)' : '失活垃圾 (下次 GC 可回收)'}】。`)
}

function markSomeGarbage() {
  const candidates = [
    ...sbEden.value.filter((o) => o.alive),
    ...(sbFrom.value === 'S0' ? sbS0.value : sbS1.value).filter((o) => o.alive),
    ...sbOld.value.filter((o) => o.alive),
  ]
  if (candidates.length === 0) {
    pushLog('当前没有可标记为垃圾的存活对象，请先分配对象。', true)
    return
  }
  const targetCount = Math.min(2, candidates.length)
  const picked: string[] = []
  for (let i = 0; i < targetCount; i++) {
    const idx = Math.floor(Math.random() * candidates.length)
    const chosen = candidates.splice(idx, 1)[0]!
    chosen.alive = false
    picked.push(chosen.id)
  }
  pushLog(`模拟业务执行结束：${picked.join('、')} 失去引用变成垃圾（灰色虚线），等待 GC 回收。`)
}

function allocateSmall() {
  if (sbEden.value.length >= EDEN_CAP) {
    pushLog('Eden 已满 (4/4)！JVM 自动先触发一次 Minor GC 腾出 Eden 空间...', true)
    triggerMinorGc(true)
    if (sbEden.value.length >= EDEN_CAP) return
  }
  const obj: HeapObj = {
    id: `O${nextSmallNo.value++}`,
    age: 0,
    alive: true,
  }
  sbEden.value.push(obj)
  pushLog(`分配小对象 ${obj.id} (age=0) → 进入 Eden 区 (${sbEden.value.length}/${EDEN_CAP})。`)
}

function allocateLarge() {
  if (sbOld.value.length >= OLD_CAP) {
    pushLog('老年代已满 (6/6)！大对象无法直接分配，请先标记垃圾并执行 Full GC 或重置！', true)
    return
  }
  const obj: HeapObj = {
    id: `Big#${nextLargeNo.value++}`,
    age: 0,
    isLarge: true,
    alive: true,
    reason: '大对象直入',
  }
  sbOld.value.push(obj)
  pushLog(`分配大对象 ${obj.id} (> PretenureSizeThreshold) → 跳过 Eden 直接进入老年代 (${sbOld.value.length}/${OLD_CAP})！`)
}

function triggerMinorGc(autoFromAlloc = false) {
  const fromList = sbFrom.value === 'S0' ? sbS0.value : sbS1.value
  const toName: RegionName = sbFrom.value === 'S0' ? 'S1' : 'S0'

  const totalPending = sbEden.value.length + fromList.length
  if (totalPending === 0) {
    pushLog('新生代 (Eden + From) 为空，无需触发 Minor GC。')
    return
  }

  minorGcCount.value += 1
  const edenDead = sbEden.value.filter((o) => !o.alive).length
  const fromDead = fromList.filter((o) => !o.alive).length
  const edenAlive = sbEden.value.filter((o) => o.alive)
  const fromAlive = fromList.filter((o) => o.alive)

  const newTo: HeapObj[] = []
  const promotedAge: string[] = []
  const promotedDynamic: string[] = []
  const promotedGuarantee: string[] = []
  const oomDropped: string[] = []

  // 1. 先检查 From 区存活对象（含动态年龄阈值判断 + 最大年龄判断）
  for (const obj of fromAlive) {
    const nextAge = obj.age + 1
    if (obj.age >= sbThreshold.value && sbThreshold.value < MAX_AGE) {
      if (sbOld.value.length < OLD_CAP) {
        sbOld.value.push({ ...obj, age: nextAge, reason: `动态年龄≥${sbThreshold.value}` })
        promotedDynamic.push(obj.id)
      } else {
        oomDropped.push(obj.id)
      }
      continue
    }
    if (nextAge >= MAX_AGE) {
      if (sbOld.value.length < OLD_CAP) {
        sbOld.value.push({ ...obj, age: nextAge, reason: `age≥${MAX_AGE} 晋升` })
        promotedAge.push(obj.id)
      } else {
        oomDropped.push(obj.id)
      }
      continue
    }
    if (newTo.length < SURV_CAP) {
      newTo.push({ ...obj, age: nextAge, reason: `${sbFrom.value}→${toName}` })
    } else if (sbOld.value.length < OLD_CAP) {
      sbOld.value.push({ ...obj, age: nextAge, reason: '担保机制晋升' })
      promotedGuarantee.push(obj.id)
    } else {
      oomDropped.push(obj.id)
    }
  }

  // 2. 再复制 Eden 存活对象进入 To（装不下则空间分配担保进老年代）
  for (const obj of edenAlive) {
    const nextAge = obj.age + 1
    if (newTo.length < SURV_CAP) {
      newTo.push({ ...obj, age: nextAge, reason: 'Eden 存活' })
    } else if (sbOld.value.length < OLD_CAP) {
      sbOld.value.push({ ...obj, age: nextAge, reason: '担保机制晋升' })
      promotedGuarantee.push(obj.id)
    } else {
      oomDropped.push(obj.id)
    }
  }

  // 3. 清空 Eden 与旧 From，更新 To，并翻转 From/To 角色
  sbEden.value = []
  if (sbFrom.value === 'S0') {
    sbS0.value = []
    sbS1.value = newTo
  } else {
    sbS1.value = []
    sbS0.value = newTo
  }
  sbFrom.value = toName

  // 4. 基于新 Survivor(From) 统计下轮动态年龄阈值
  const nextThreshold = computeDynamicThreshold(newTo)
  sbThreshold.value = nextThreshold

  const details: string[] = []
  if (edenDead + fromDead > 0) details.push(`回收垃圾 ${edenDead + fromDead} 个`)
  if (newTo.length > 0) details.push(`${newTo.length} 个进入 ${toName}(新From)`)
  if (promotedAge.length) details.push(`满龄晋升 Old: ${promotedAge.join(',')}`)
  if (promotedDynamic.length) details.push(`动态年龄晋升 Old: ${promotedDynamic.join(',')}`)
  if (promotedGuarantee.length) details.push(`空间担保晋升 Old: ${promotedGuarantee.join(',')}`)
  if (nextThreshold < MAX_AGE) details.push(`触发动态年龄判定(累计占比>50%)，下轮阈值降至 ${nextThreshold}`)
  if (oomDropped.length) details.push(`老年代已满导致 OOM 告警(${oomDropped.join(',')})`)

  const prefix = autoFromAlloc ? `自动 Minor GC #${minorGcCount.value}` : `Minor GC #${minorGcCount.value}`
  pushLog(`${prefix} 完成：${details.join('；') || '新生代已全部回收'}。`, oomDropped.length > 0)
}

function triggerFullGc() {
  const deadOld = sbOld.value.filter((o) => !o.alive).length
  const hasYoung = sbEden.value.length + sbS0.value.length + sbS1.value.length > 0
  if (deadOld === 0 && !hasYoung) {
    pushLog('老年代与新生代均无待回收垃圾（可先点击对象将其标记为失活垃圾再试）。')
    return
  }
  fullGcCount.value += 1
  const beforeOld = sbOld.value.length
  sbOld.value = sbOld.value.filter((o) => o.alive)
  const reclaimedOld = beforeOld - sbOld.value.length

  // Full GC 通常顺带清理新生代中的垃圾
  const beforeYoung = sbEden.value.length + sbS0.value.length + sbS1.value.length
  sbEden.value = sbEden.value.filter((o) => o.alive)
  sbS0.value = sbS0.value.filter((o) => o.alive)
  sbS1.value = sbS1.value.filter((o) => o.alive)
  const reclaimedYoung = beforeYoung - (sbEden.value.length + sbS0.value.length + sbS1.value.length)

  pushLog(`Full GC #${fullGcCount.value} 完成（STW）：老年代回收 ${reclaimedOld} 个、新生代清理 ${reclaimedYoung} 个。`)
}

function resetSandbox() {
  nextSmallNo.value = 1
  nextLargeNo.value = 1
  minorGcCount.value = 0
  fullGcCount.value = 0
  sbFrom.value = 'S0'
  sbThreshold.value = MAX_AGE
  sbEden.value = []
  sbS0.value = []
  sbS1.value = []
  sbOld.value = []
  logHistory.value = []
  pushLog('堆内存沙盒已重置！可开始分配对象并观察年龄与晋升机制。')
}

function cardStyle(obj: HeapObj, isOld = false) {
  if (!obj.alive) {
    return 'border-dashed border-slate-300 bg-slate-100/80 text-slate-400 line-through dark:border-slate-600 dark:bg-slate-800/70 dark:text-slate-500'
  }
  if (obj.isLarge) {
    return 'border-purple-400 bg-purple-500 text-white shadow-xs dark:border-purple-400 dark:bg-purple-600'
  }
  if (isOld) {
    return 'border-amber-500 bg-amber-700 text-white shadow-xs dark:border-amber-400 dark:bg-amber-600'
  }
  return 'border-emerald-500 bg-emerald-700 text-white shadow-xs dark:border-emerald-400 dark:bg-emerald-600'
}
</script>

<template>
  <div class="space-y-4">
    <!-- 模式切换与状态标签 -->
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div class="inline-flex rounded-xl border border-slate-200 bg-slate-100/80 p-0.5 text-xs dark:border-slate-700 dark:bg-slate-800">
        <button
          class="rounded-lg px-3 py-1.5 font-medium transition"
          :class="mode === 'guided' ? 'bg-white text-brand-700 shadow-xs dark:bg-slate-900 dark:text-brand-300' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'"
          @click="switchMode('guided')"
        >
          分代回收演练 (6 步)
        </button>
        <button
          class="rounded-lg px-3 py-1.5 font-medium transition"
          :class="mode === 'sandbox' ? 'bg-white text-brand-700 shadow-xs dark:bg-slate-900 dark:text-brand-300' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'"
          @click="switchMode('sandbox')"
        >
          动手堆内存沙盒
        </button>
      </div>

      <span
        v-if="mode === 'guided'"
        class="rounded-full bg-brand-100/80 px-2.5 py-0.5 font-mono text-[11px] font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-300"
      >
        {{ currentGuided.badge }} · {{ stepIdx + 1 }}/{{ guidedSteps.length }}
      </span>
      <div v-else class="flex flex-wrap items-center gap-2 font-mono text-[11px]">
        <span
          class="rounded-full px-2 py-0.5 font-semibold"
          :class="cur.threshold < MAX_AGE
            ? 'bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-300'
            : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'"
        >
          晋升阈值={{ cur.threshold }} (Max={{ MAX_AGE }})
        </span>
        <span class="text-slate-400">MinorGC: {{ minorGcCount }} · FullGC: {{ fullGcCount }}</span>
      </div>
    </div>

    <!-- JVM 堆内存可视化面板 -->
    <div class="rounded-xl border border-slate-200/80 bg-white p-3.5 dark:border-slate-800 dark:bg-slate-950/70">
      <div class="grid gap-3 md:grid-cols-12">
        <!-- 左侧：新生代 Young Gen (占 7 列) -->
        <div class="space-y-2.5 rounded-xl border border-emerald-200/80 bg-emerald-50/40 p-3 md:col-span-7 dark:border-emerald-500/25 dark:bg-emerald-500/5">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <span class="text-xs font-bold text-emerald-800 dark:text-emerald-300">新生代 Young Generation (复制算法)</span>
            <span class="font-mono text-[10.5px] text-emerald-700/80 dark:text-emerald-300/80">
              当前 From={{ cur.fromRegion }} · To={{ cur.fromRegion === 'S0' ? 'S1' : 'S0' }}
            </span>
          </div>

          <!-- Eden 区 (4 槽位) -->
          <div class="rounded-lg border border-emerald-200 bg-white/90 p-2.5 dark:border-emerald-500/30 dark:bg-slate-900/80">
            <div class="mb-2 flex items-center justify-between text-[11px]">
              <span class="font-semibold text-slate-700 dark:text-slate-200">Eden 伊甸园区</span>
              <span class="font-mono text-slate-400">{{ cur.eden.length }} / {{ EDEN_CAP }} 槽位</span>
            </div>
            <div class="grid grid-cols-4 gap-2">
              <button
                v-for="idx in EDEN_CAP"
                :key="`eden-${idx}`"
                type="button"
                class="flex h-13 flex-col items-center justify-center rounded-lg border px-1 text-[11px] font-semibold transition"
                :class="cur.eden[idx - 1]
                  ? [cardStyle(cur.eden[idx - 1]!), mode === 'sandbox' ? 'cursor-pointer hover:scale-[1.03]' : 'cursor-default']
                  : 'cursor-default border-dashed border-slate-200 bg-slate-50/60 text-slate-400 dark:border-slate-700 dark:bg-slate-800/40'"
                :title="mode === 'sandbox' && cur.eden[idx - 1] ? '点击切换 存活 / 垃圾' : undefined"
                @click="toggleObjAlive('eden', idx - 1)"
              >
                <template v-if="cur.eden[idx - 1]">
                  <span>{{ cur.eden[idx - 1]!.id }}</span>
                  <span class="mt-0.5 rounded bg-black/15 px-1 font-mono text-[9.5px]">
                    age:{{ cur.eden[idx - 1]!.age }} · {{ cur.eden[idx - 1]!.alive ? '活' : '垃圾' }}
                  </span>
                </template>
                <span v-else class="text-[10px]">空闲</span>
              </button>
            </div>
          </div>

          <!-- Survivor S0 & S1 (各 2 槽位) -->
          <div class="grid grid-cols-2 gap-2.5">
            <!-- S0 -->
            <div
              class="rounded-lg border p-2.5 transition"
              :class="cur.fromRegion === 'S0'
                ? 'border-brand-300 bg-brand-50/50 dark:border-brand-500/40 dark:bg-brand-500/10'
                : 'border-slate-200 bg-white/90 dark:border-slate-700 dark:bg-slate-900/80'"
            >
              <div class="mb-2 flex items-center justify-between text-[11px]">
                <span class="font-semibold text-slate-700 dark:text-slate-200">
                  S0
                  <span
                    class="ml-1 rounded px-1 py-0.5 font-mono text-[9.5px]"
                    :class="cur.fromRegion === 'S0' ? 'bg-brand-600 text-white' : 'bg-slate-200 text-slate-600 dark:bg-slate-700 dark:text-slate-300'"
                  >
                    {{ cur.fromRegion === 'S0' ? 'From' : 'To(空)' }}
                  </span>
                </span>
                <span class="font-mono text-slate-400">{{ cur.s0.length }}/{{ SURV_CAP }}</span>
              </div>
              <div class="grid grid-cols-2 gap-1.5">
                <button
                  v-for="idx in SURV_CAP"
                  :key="`s0-${idx}`"
                  type="button"
                  class="flex h-13 flex-col items-center justify-center rounded-lg border px-1 text-[11px] font-semibold transition"
                  :class="cur.s0[idx - 1]
                    ? [cardStyle(cur.s0[idx - 1]!), mode === 'sandbox' ? 'cursor-pointer hover:scale-[1.03]' : 'cursor-default']
                    : 'cursor-default border-dashed border-slate-200 bg-slate-50/60 text-slate-400 dark:border-slate-700 dark:bg-slate-800/40'"
                  :title="mode === 'sandbox' && cur.s0[idx - 1] ? '点击切换 存活 / 垃圾' : undefined"
                  @click="toggleObjAlive('s0', idx - 1)"
                >
                  <template v-if="cur.s0[idx - 1]">
                    <span>{{ cur.s0[idx - 1]!.id }}</span>
                    <span class="mt-0.5 rounded bg-black/15 px-1 font-mono text-[9.5px]">
                      age:{{ cur.s0[idx - 1]!.age }} · {{ cur.s0[idx - 1]!.alive ? '活' : '垃圾' }}
                    </span>
                  </template>
                  <span v-else class="text-[10px]">空</span>
                </button>
              </div>
            </div>

            <!-- S1 -->
            <div
              class="rounded-lg border p-2.5 transition"
              :class="cur.fromRegion === 'S1'
                ? 'border-brand-300 bg-brand-50/50 dark:border-brand-500/40 dark:bg-brand-500/10'
                : 'border-slate-200 bg-white/90 dark:border-slate-700 dark:bg-slate-900/80'"
            >
              <div class="mb-2 flex items-center justify-between text-[11px]">
                <span class="font-semibold text-slate-700 dark:text-slate-200">
                  S1
                  <span
                    class="ml-1 rounded px-1 py-0.5 font-mono text-[9.5px]"
                    :class="cur.fromRegion === 'S1' ? 'bg-brand-600 text-white' : 'bg-slate-200 text-slate-600 dark:bg-slate-700 dark:text-slate-300'"
                  >
                    {{ cur.fromRegion === 'S1' ? 'From' : 'To(空)' }}
                  </span>
                </span>
                <span class="font-mono text-slate-400">{{ cur.s1.length }}/{{ SURV_CAP }}</span>
              </div>
              <div class="grid grid-cols-2 gap-1.5">
                <button
                  v-for="idx in SURV_CAP"
                  :key="`s1-${idx}`"
                  type="button"
                  class="flex h-13 flex-col items-center justify-center rounded-lg border px-1 text-[11px] font-semibold transition"
                  :class="cur.s1[idx - 1]
                    ? [cardStyle(cur.s1[idx - 1]!), mode === 'sandbox' ? 'cursor-pointer hover:scale-[1.03]' : 'cursor-default']
                    : 'cursor-default border-dashed border-slate-200 bg-slate-50/60 text-slate-400 dark:border-slate-700 dark:bg-slate-800/40'"
                  :title="mode === 'sandbox' && cur.s1[idx - 1] ? '点击切换 存活 / 垃圾' : undefined"
                  @click="toggleObjAlive('s1', idx - 1)"
                >
                  <template v-if="cur.s1[idx - 1]">
                    <span>{{ cur.s1[idx - 1]!.id }}</span>
                    <span class="mt-0.5 rounded bg-black/15 px-1 font-mono text-[9.5px]">
                      age:{{ cur.s1[idx - 1]!.age }} · {{ cur.s1[idx - 1]!.alive ? '活' : '垃圾' }}
                    </span>
                  </template>
                  <span v-else class="text-[10px]">空</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 右侧：老年代 Old Gen (占 5 列) -->
        <div class="flex flex-col justify-between rounded-xl border border-amber-200/80 bg-amber-50/40 p-3 md:col-span-5 dark:border-amber-500/25 dark:bg-amber-500/5">
          <div>
            <div class="mb-2 flex items-center justify-between">
              <span class="text-xs font-bold text-amber-800 dark:text-amber-300">老年代 Old Generation</span>
              <span class="font-mono text-[10.5px] text-amber-700/80 dark:text-amber-300/80">{{ cur.old.length }} / {{ OLD_CAP }} 槽位</span>
            </div>

            <div class="grid grid-cols-2 gap-2">
              <button
                v-for="idx in OLD_CAP"
                :key="`old-${idx}`"
                type="button"
                class="flex h-13 flex-col items-center justify-center rounded-lg border px-1.5 text-[11px] font-semibold transition"
                :class="cur.old[idx - 1]
                  ? [cardStyle(cur.old[idx - 1]!, true), mode === 'sandbox' ? 'cursor-pointer hover:scale-[1.03]' : 'cursor-default']
                  : 'cursor-default border-dashed border-amber-200/80 bg-white/70 text-slate-400 dark:border-slate-700 dark:bg-slate-900/50'"
                :title="mode === 'sandbox' && cur.old[idx - 1] ? '点击切换 存活 / 垃圾' : undefined"
                @click="toggleObjAlive('old', idx - 1)"
              >
                <template v-if="cur.old[idx - 1]">
                  <span>{{ cur.old[idx - 1]!.id }}</span>
                  <span class="mt-0.5 max-w-full truncate rounded bg-black/15 px-1 font-mono text-[9px]">
                    {{ cur.old[idx - 1]!.reason ?? `age:${cur.old[idx - 1]!.age}` }}
                  </span>
                </template>
                <span v-else class="text-[10px]">老年代空闲</span>
              </button>
            </div>
          </div>

          <!-- 晋升规则速查提示 -->
          <div class="mt-2.5 rounded-lg bg-white/80 px-2.5 py-2 text-[10.5px] leading-relaxed text-slate-600 dark:bg-slate-900/80 dark:text-slate-300">
            <p class="font-semibold text-amber-700 dark:text-amber-300">进入老年代的 4 大条件：</p>
            <p>① age ≥ {{ cur.threshold }} (Max={{ MAX_AGE }}) ② 大对象直入 ③ 动态年龄(&gt;50%降阈值) ④ Survivor 溢出担保</p>
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
      <div
        class="flex items-start gap-2 rounded-xl border px-3.5 py-2.5 text-xs leading-relaxed"
        :class="sbWarn
          ? 'border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-300'
          : 'border-slate-200 bg-slate-50 text-slate-600 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300'"
      >
        <TriangleAlert v-if="sbWarn" class="mt-0.5 h-4 w-4 shrink-0 text-amber-500" />
        <Cpu v-else class="mt-0.5 h-4 w-4 shrink-0 text-brand-500" />
        <div class="space-y-1">
          <p class="font-medium">{{ sbLog }}</p>
          <p class="text-[11px] text-slate-400">小技巧：点击堆中任意对象块可将其在【存活】与【垃圾(灰)】之间切换，从而观察回收与年龄晋升差异。</p>
        </div>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <button class="btn-primary h-9 px-3 text-xs" @click="allocateSmall">
          <Plus class="h-3.5 w-3.5" /> 分配小对象 (Eden)
        </button>
        <button class="btn-ghost h-9 px-3 text-xs" @click="allocateLarge">
          <Plus class="h-3.5 w-3.5 text-purple-500" /> 分配大对象 (直入老年代)
        </button>
        <button class="btn-ghost h-9 px-3 text-xs" @click="triggerMinorGc(false)">
          <Zap class="h-3.5 w-3.5 text-emerald-500" /> 触发 Minor GC
        </button>
        <button class="btn-ghost h-9 px-3 text-xs" @click="markSomeGarbage">
          <Trash2 class="h-3.5 w-3.5 text-slate-500" /> 标记部分垃圾
        </button>
        <button class="btn-ghost h-9 px-3 text-xs" @click="triggerFullGc">
          <Zap class="h-3.5 w-3.5 text-amber-500" /> Full GC
        </button>
        <button class="btn-ghost ml-auto h-9 px-3 text-xs" @click="resetSandbox">
          <RotateCcw class="h-3.5 w-3.5" /> 重置
        </button>
      </div>

      <div v-if="logHistory.length > 1" class="rounded-lg border border-slate-200/70 bg-slate-50/70 px-3 py-2 text-[11px] text-slate-500 dark:border-slate-800 dark:bg-slate-900/50 dark:text-slate-400">
        <div v-for="(item, idx) in logHistory" :key="idx" :class="idx === 0 ? 'font-medium text-slate-700 dark:text-slate-200' : 'opacity-75'">
          • {{ item }}
        </div>
      </div>
    </template>
  </div>
</template>
