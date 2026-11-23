<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  ChevronLeft,
  ChevronRight,
  Pause,
  Play,
  RotateCcw,
  ShieldCheck,
  Sparkles,
  TriangleAlert,
} from 'lucide-vue-next'

type ScenarioTab = 'normal' | 'missed' | 'fix'
type FixStrategy = 'incremental' | 'satb'
type NodeColor = 'white' | 'gray' | 'black' | 'swept'
type NodeId = 'A' | 'B' | 'C' | 'D'

interface TriStep {
  title: string
  badge: string
  colors: Record<NodeId, NodeColor>
  grayQueue: string[]
  barrierBuffer: string[]
  edgeBC: 'intact' | 'broken' | 'satb-saved'
  edgeAC: 'none' | 'added' | 'barrier-regray'
  activeNode?: NodeId
  danger?: boolean
  speech: string
  tip: string
}

const normalSteps: TriStep[] = [
  {
    title: '0. 初始态：所有堆对象均为白色',
    badge: '初始全白',
    colors: { A: 'white', B: 'white', C: 'white', D: 'white' },
    grayQueue: [],
    barrierBuffer: [],
    edgeBC: 'intact',
    edgeAC: 'none',
    speech: 'GC 标记开始前，堆中所有对象（A、B、C、D）默认标记为白色（尚未被 GC 访问，若结束时仍为白色则判定为不可达垃圾）。',
    tip: '三色含义：白 = 未访问(待回收)；灰 = 自身已访问，子引用未扫描完；黑 = 自身与子引用均已扫描完毕(确定存活)。',
  },
  {
    title: '1. 扫描 GC Roots：直达对象 A 入灰队列',
    badge: 'Root 入队变灰',
    colors: { A: 'gray', B: 'white', C: 'white', D: 'white' },
    grayQueue: ['A'],
    barrierBuffer: [],
    edgeBC: 'intact',
    edgeAC: 'none',
    activeNode: 'A',
    speech: '从 GC Roots 出发（通常需短暂 STW 保证根节点快照一致），发现直接引用的对象 A，将其标为灰色并压入待扫描队列。',
    tip: '灰色对象代表标记波前（Wavefront），GC 线程只需要不断从灰色队列弹出节点向下推进即可。',
  },
  {
    title: '2. 扫描 A：B 变灰入队，A 转为黑色',
    badge: '扫 A 转黑 · B 入灰',
    colors: { A: 'black', B: 'gray', C: 'white', D: 'white' },
    grayQueue: ['B'],
    barrierBuffer: [],
    edgeBC: 'intact',
    edgeAC: 'none',
    activeNode: 'B',
    speech: '从队列弹出 A，遍历 A 的成员变量引用发现 B：将 B 标记为灰色入队；A 的所有子引用已检查完毕，A 转为黑色。',
    tip: '黑色对象在并发标记中通常不会再被重新扫描（这是后续并发修改导致漏标的根源之一）。',
  },
  {
    title: '3. 扫描 B：C 变灰入队，B 转为黑色',
    badge: '扫 B 转黑 · C 入灰',
    colors: { A: 'black', B: 'black', C: 'gray', D: 'white' },
    grayQueue: ['C'],
    barrierBuffer: [],
    edgeBC: 'intact',
    edgeAC: 'none',
    activeNode: 'C',
    speech: '弹出灰色节点 B，遍历其字段 B.c 发现白色对象 C，将 C 标灰入队；随后 B 的字段扫描完成，B 转为黑色。',
    tip: '对象 D 没有任何来自 GC Roots 的路径，因此从未进入过灰色队列，一直保持白色。',
  },
  {
    title: '4. 扫描 C：无后续引用，C 转为黑色',
    badge: '扫 C 转黑 · 标记完成',
    colors: { A: 'black', B: 'black', C: 'black', D: 'white' },
    grayQueue: [],
    barrierBuffer: [],
    edgeBC: 'intact',
    edgeAC: 'none',
    activeNode: 'C',
    speech: '弹出 C，检查其内部没有其他对象引用，C 立即转为黑色。此时灰色队列为空，可达性分析标记阶段结束。',
    tip: '标记结束的判据就是“灰色队列彻底清空”。',
  },
  {
    title: '5. 清除阶段：保留黑色存活对象，回收白色孤岛 D',
    badge: '清除白色孤岛',
    colors: { A: 'black', B: 'black', C: 'black', D: 'swept' },
    grayQueue: [],
    barrierBuffer: [],
    edgeBC: 'intact',
    edgeAC: 'none',
    speech: '清理阶段回收所有仍为白色的不可达对象 D，黑色对象 A、B、C 全部安全保留。整个三色标记正常完成！',
    tip: '如果是整理或复制算法（如 G1 Region 复制），则会直接把黑色存活对象复制到新区域并清空旧区。',
  },
]

const missedSteps: TriStep[] = [
  {
    title: '0. 并发标记中间态：A 已黑、B 为灰、C 仍白',
    badge: '并发中段',
    colors: { A: 'black', B: 'gray', C: 'white', D: 'white' },
    grayQueue: ['B'],
    barrierBuffer: [],
    edgeBC: 'intact',
    edgeAC: 'none',
    speech: '并发标记阶段，GC 线程与业务线程（Mutator）同时运行。此时 A 已扫描完毕成为黑色，B 处于灰色队列中，C 尚未被扫描仍为白色。',
    tip: '并发标记无需长期 STW，但业务线程在后台修改引用关系时，可能打破三色不变式。',
  },
  {
    title: '1. 业务线程并发操作①：黑对象 A 新增引用 A.c = C',
    badge: '条件①：黑→白新引用',
    colors: { A: 'black', B: 'gray', C: 'white', D: 'white' },
    grayQueue: ['B'],
    barrierBuffer: [],
    edgeBC: 'intact',
    edgeAC: 'added',
    activeNode: 'A',
    danger: true,
    speech: '业务线程执行 A.c = C，让黑色对象 A 直接指向白色对象 C。但因为 A 已经是黑色，GC 线程默认不会回头重扫 A！',
    tip: '这是漏标的第一个必要条件：黑色对象插入了指向白色对象的新引用（无写屏障保护）。',
  },
  {
    title: '2. 业务线程并发操作②：灰对象 B 断开引用 B.c = null',
    badge: '条件②：灰→白断引用',
    colors: { A: 'black', B: 'gray', C: 'white', D: 'white' },
    grayQueue: ['B'],
    barrierBuffer: [],
    edgeBC: 'broken',
    edgeAC: 'added',
    activeNode: 'B',
    danger: true,
    speech: '紧接着业务线程执行 B.c = null，删除了灰色对象 B 到白色对象 C 的引用。此时 C 只剩下来自黑色 A 的唯一引用！',
    tip: '这是漏标的第二个必要条件：所有灰色对象都删除了对该白色对象的直接或间接引用。',
  },
  {
    title: '3. GC 扫描 B：找不到 C，B 直接变黑！',
    badge: '漏标形成',
    colors: { A: 'black', B: 'black', C: 'white', D: 'white' },
    grayQueue: [],
    barrierBuffer: [],
    edgeBC: 'broken',
    edgeAC: 'added',
    activeNode: 'C',
    danger: true,
    speech: 'GC 线程弹出 B 进行扫描，发现 B.c 已是 null，于是将 B 标记为黑色。灰色队列清空，标记阶段结束——但可达对象 C 竟然还是白色！',
    tip: '此时从 GC Roots → A → C 明明存在强引用链，却没有任何灰色节点能通向 C。',
  },
  {
    title: '4. 翻车！清除阶段把存活对象 C 当垃圾误回收',
    badge: '灾难：误删存活对象',
    colors: { A: 'black', B: 'black', C: 'swept', D: 'swept' },
    grayQueue: [],
    barrierBuffer: [],
    edgeBC: 'broken',
    edgeAC: 'added',
    danger: true,
    speech: '清除阶段把所有白色对象（包括仍然被 A 引用的 C）回收清空！后续业务访问 A.c 时发生空指针或内存数据损坏，属于致命 GC Bug！',
    tip: '结论：漏标必须【同时满足】条件①与条件②。只需用写屏障破坏其中任意一个条件，就能 100% 消除漏标！',
  },
]

const incrementalSteps: TriStep[] = [
  {
    title: '0. 初始并发态：A(黑) → B(灰) → C(白)',
    badge: 'CMS · 准备',
    colors: { A: 'black', B: 'gray', C: 'white', D: 'white' },
    grayQueue: ['B'],
    barrierBuffer: [],
    edgeBC: 'intact',
    edgeAC: 'none',
    speech: 'CMS 采用【增量更新（Incremental Update）】策略，专门针对漏标条件①（黑对象新增白引用）进行拦截修复。',
    tip: '核心思想：当黑色对象插入指向白色对象的新引用时，把黑色对象本身重新变回灰色！',
  },
  {
    title: '1. 写后屏障拦截 A.c = C：将黑对象 A 重新标灰！',
    badge: '破坏条件①：A 黑→灰',
    colors: { A: 'gray', B: 'gray', C: 'white', D: 'white' },
    grayQueue: ['B'],
    barrierBuffer: ['A (增量更新入队)'],
    edgeBC: 'intact',
    edgeAC: 'barrier-regray',
    activeNode: 'A',
    speech: '业务线程执行 A.c = C 后，Post-Write Barrier（写后屏障）触发，记录“黑色对象 A 发生过新引用赋值”，在标记视角等价于把 A 重新置灰！',
    tip: '就算接下来业务线程立即执行 B.c = null 断掉旧链，由于 A 已在脏卡表/写屏障记录中，C 依然有“灰色源头”。',
  },
  {
    title: '2. B.c = null 且扫完 B：C 暂为白，但 A 在重扫队列',
    badge: '并发扫描结束',
    colors: { A: 'gray', B: 'black', C: 'white', D: 'white' },
    grayQueue: [],
    barrierBuffer: ['A (待 Remark 重扫)'],
    edgeBC: 'broken',
    edgeAC: 'barrier-regray',
    speech: 'B 断开 B.c 并扫完变黑，但写屏障队列里保留了被置灰的 A，等待重新标记（Remark）阶段统一处理。',
    tip: 'CMS Remark 阶段需要 STW，重新扫描记录过写入的黑对象（Dirty Card）的整个引用树。',
  },
  {
    title: '3. Remark 重扫 A：发现 C → 转黑存活！',
    badge: 'Remark 修复成功',
    colors: { A: 'black', B: 'black', C: 'black', D: 'swept' },
    grayQueue: [],
    barrierBuffer: [],
    edgeBC: 'broken',
    edgeAC: 'added',
    activeNode: 'C',
    speech: 'Remark（STW）从写屏障记录中弹出 A 并深度重新扫描其字段，顺利发现 A.c -> C，将 C 标灰再转黑！D 被安全回收，绝不漏标！',
    tip: 'CMS 增量更新缺点：Remark 阶段需重扫整个黑对象 A 的引用树（可能耗时较长）；优点：不会放过刚断掉的死对象，几乎无浮动垃圾。',
  },
]

const satbSteps: TriStep[] = [
  {
    title: '0. 初始快照态：A(黑) → B(灰) → C(白)',
    badge: 'G1 SATB · 准备',
    colors: { A: 'black', B: 'gray', C: 'white', D: 'white' },
    grayQueue: ['B'],
    barrierBuffer: [],
    edgeBC: 'intact',
    edgeAC: 'none',
    speech: 'G1 采用【SATB（Snapshot-At-The-Beginning，原始快照）】，在并发标记开始时对存活关系建立逻辑快照，专门破坏条件②！',
    tip: '核心思想：标记开始瞬间还存活的对象，在本轮 GC 中都被视为存活——哪怕中间被切断引用！',
  },
  {
    title: '1. 业务线程执行 A.c = C（写前屏障不拦截新增）',
    badge: 'A.c = C 新增引用',
    colors: { A: 'black', B: 'gray', C: 'white', D: 'white' },
    grayQueue: ['B'],
    barrierBuffer: [],
    edgeBC: 'intact',
    edgeAC: 'added',
    activeNode: 'A',
    speech: 'A.c = C 发生时，G1 的 SATB 写前屏障并不关心新增引用，A 依然保持黑色。此时 B.c 仍指向 C，即将被改写。',
    tip: 'SATB 关注的是“引用消失”而非“引用新增”，因为快照里所有对象原本都有灰节点兜底。',
  },
  {
    title: '2. 写前屏障拦截 B.c = null：旧值 C 压入 SATB 栈变灰！',
    badge: '破坏条件②：旧引用 C 标灰',
    colors: { A: 'black', B: 'gray', C: 'gray', D: 'white' },
    grayQueue: ['B'],
    barrierBuffer: ['C (SATB 旧引用快照)'],
    edgeBC: 'satb-saved',
    edgeAC: 'added',
    activeNode: 'C',
    speech: '执行 B.c = null 覆盖旧指针【之前】，Pre-Write Barrier（写前屏障）把即将消失的旧引用对象 C 压入 SATB 本地缓冲区，直接视 C 为灰色！',
    tip: '无论后面 B 扫完变黑还是谁删了引用，只要 C 在 SATB 队列里，C 就绝对不会漏标！',
  },
  {
    title: '3. Remark 扫 SATB 栈：C 直接转黑存活！',
    badge: 'SATB 修复成功',
    colors: { A: 'black', B: 'black', C: 'black', D: 'swept' },
    grayQueue: [],
    barrierBuffer: [],
    edgeBC: 'satb-saved',
    edgeAC: 'added',
    activeNode: 'C',
    speech: 'Remark 阶段只需扫描轻量级的 SATB 缓冲区，将 C（及其子图）标黑；不需要回头重扫黑色对象 A！D 作为真垃圾被回收。',
    tip: 'G1 SATB 优点：无需重扫黑对象，Remark 极快；缺点：如果 B.c=null 后没有任何人引用 C，C 本轮也会存活成为“浮动垃圾”，留待下次 GC 回收。',
  },
]

const scenarioTab = ref<ScenarioTab>('normal')
const fixStrategy = ref<FixStrategy>('incremental')
const stepIdx = ref(0)
const playing = ref(false)
let playTimer: number | undefined

const activeSteps = computed<TriStep[]>(() => {
  if (scenarioTab.value === 'normal') return normalSteps
  if (scenarioTab.value === 'missed') return missedSteps
  return fixStrategy.value === 'incremental' ? incrementalSteps : satbSteps
})

const curStep = computed(() => activeSteps.value[stepIdx.value] ?? activeSteps.value[0]!)

function stopPlay() {
  playing.value = false
  if (playTimer) {
    clearInterval(playTimer)
    playTimer = undefined
  }
}

function selectTab(tab: ScenarioTab) {
  stopPlay()
  scenarioTab.value = tab
  stepIdx.value = 0
}

function selectFix(strategy: FixStrategy) {
  stopPlay()
  fixStrategy.value = strategy
  stepIdx.value = 0
}

function togglePlay() {
  if (playing.value) {
    stopPlay()
    return
  }
  if (stepIdx.value >= activeSteps.value.length - 1) stepIdx.value = 0
  playing.value = true
  playTimer = window.setInterval(() => {
    if (stepIdx.value < activeSteps.value.length - 1) {
      stepIdx.value += 1
    } else {
      stopPlay()
    }
  }, 2400)
}

function prevStep() {
  stopPlay()
  if (stepIdx.value > 0) stepIdx.value -= 1
}

function nextStep() {
  stopPlay()
  if (stepIdx.value < activeSteps.value.length - 1) stepIdx.value += 1
}

function resetSteps() {
  stopPlay()
  stepIdx.value = 0
}

onBeforeUnmount(stopPlay)

function circleClass(id: NodeId) {
  const c = curStep.value.colors[id]
  if (c === 'white') {
    return 'fill-white stroke-slate-400 dark:fill-slate-800 dark:stroke-slate-500'
  }
  if (c === 'gray') {
    return 'fill-slate-400 stroke-slate-600 dark:fill-slate-500 dark:stroke-slate-300'
  }
  if (c === 'black') {
    return 'fill-slate-900 stroke-slate-950 dark:fill-slate-950 dark:stroke-brand-400'
  }
  return 'fill-red-50 stroke-red-400 opacity-55 dark:fill-red-950/40 dark:stroke-red-500/60'
}

function nodeTextClass(id: NodeId) {
  const c = curStep.value.colors[id]
  if (c === 'white') return 'fill-slate-800 dark:fill-slate-100'
  if (c === 'gray') return 'fill-white'
  if (c === 'black') return 'fill-white'
  return 'fill-red-500 dark:fill-red-400 line-through'
}

function colorLabel(c: NodeColor) {
  if (c === 'white') return '白色·未访'
  if (c === 'gray') return '灰色·待扫'
  if (c === 'black') return '黑色·存活'
  return '已回收'
}
</script>

<template>
  <div class="space-y-4">
    <!-- 顶部场景 Tab + 策略切换 -->
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div class="inline-flex flex-wrap rounded-xl border border-slate-200 bg-slate-100/80 p-0.5 text-xs dark:border-slate-700 dark:bg-slate-800">
        <button
          class="rounded-lg px-2.5 py-1.5 font-medium transition"
          :class="scenarioTab === 'normal' ? 'bg-white text-brand-700 shadow-xs dark:bg-slate-900 dark:text-brand-300' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'"
          @click="selectTab('normal')"
        >
          正常三色标记 (6步)
        </button>
        <button
          class="rounded-lg px-2.5 py-1.5 font-medium transition"
          :class="scenarioTab === 'missed' ? 'bg-white text-red-600 shadow-xs dark:bg-slate-900 dark:text-red-400' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'"
          @click="selectTab('missed')"
        >
          漏标翻车现场
        </button>
        <button
          class="rounded-lg px-2.5 py-1.5 font-medium transition"
          :class="scenarioTab === 'fix' ? 'bg-white text-emerald-700 shadow-xs dark:bg-slate-900 dark:text-emerald-300' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'"
          @click="selectTab('fix')"
        >
          CMS 增量更新 vs G1 SATB
        </button>
      </div>

      <span
        class="rounded-full px-2.5 py-0.5 font-mono text-[11px] font-semibold"
        :class="curStep.danger
          ? 'bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-300'
          : 'bg-brand-100/80 text-brand-700 dark:bg-brand-500/20 dark:text-brand-300'"
      >
        {{ curStep.badge }} · {{ stepIdx + 1 }}/{{ activeSteps.length }}
      </span>
    </div>

    <!-- 写屏障修复对比开关（仅在 fix Tab 下显示） -->
    <div
      v-if="scenarioTab === 'fix'"
      class="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-emerald-200/80 bg-emerald-50/50 px-3 py-2 text-xs dark:border-emerald-500/25 dark:bg-emerald-500/10"
    >
      <div class="flex items-center gap-1.5 font-medium text-emerald-800 dark:text-emerald-300">
        <ShieldCheck class="h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
        <span>写屏障切换对比：</span>
      </div>
      <div class="inline-flex rounded-lg border border-emerald-200 bg-white p-0.5 dark:border-emerald-500/30 dark:bg-slate-900">
        <button
          class="rounded-md px-2.5 py-1 text-[11px] font-semibold transition"
          :class="fixStrategy === 'incremental'
            ? 'bg-brand-600 text-white'
            : 'text-slate-600 hover:text-slate-900 dark:text-slate-300'"
          @click="selectFix('incremental')"
        >
          CMS · 增量更新 (A.c=C 写后屏障)
        </button>
        <button
          class="rounded-md px-2.5 py-1 text-[11px] font-semibold transition"
          :class="fixStrategy === 'satb'
            ? 'bg-emerald-600 text-white'
            : 'text-slate-600 hover:text-slate-900 dark:text-slate-300'"
          @click="selectFix('satb')"
        >
          G1 · SATB 快照 (B.c=null 写前屏障)
        </button>
      </div>
    </div>

    <!-- SVG 三色对象引用图 -->
    <div class="overflow-x-auto rounded-xl border border-slate-200/80 bg-white p-3 dark:border-slate-800 dark:bg-slate-950/70">
      <svg viewBox="0 0 760 275" class="mx-auto w-full min-w-[580px] max-w-[740px] select-none" role="img" aria-label="JVM 三色标记并发过程示意图">
        <defs>
          <marker id="tri-arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="currentColor" />
          </marker>
        </defs>

        <!-- 标题图例 -->
        <g transform="translate(20, 14)">
          <circle cx="8" cy="8" r="6" class="fill-white stroke-slate-400 dark:fill-slate-800 dark:stroke-slate-500" stroke-width="1.5" />
          <text x="19" y="12" class="fill-slate-500 text-[10px] dark:fill-slate-400">白色 (未访问)</text>
          <circle cx="105" cy="8" r="6" class="fill-slate-400 stroke-slate-600 dark:fill-slate-500 dark:stroke-slate-300" stroke-width="1.5" />
          <text x="116" y="12" class="fill-slate-500 text-[10px] dark:fill-slate-400">灰色 (待扫子引用)</text>
          <circle cx="225" cy="8" r="6" class="fill-slate-900 stroke-slate-950 dark:fill-slate-950 dark:stroke-brand-400" stroke-width="1.5" />
          <text x="236" y="12" class="fill-slate-500 text-[10px] dark:fill-slate-400">黑色 (扫描完成·存活)</text>
          <circle cx="362" cy="8" r="6" class="fill-red-50 stroke-red-400 dark:fill-red-950/40 dark:stroke-red-500" stroke-width="1.5" />
          <text x="373" y="12" class="fill-red-500 text-[10px]">已清理 / 误删</text>
        </g>

        <!-- 边：GC Roots -> A -->
        <path
          d="M 142 124 L 214 124"
          fill="none"
          stroke-width="2.2"
          marker-end="url(#tri-arrow)"
          class="text-brand-500 stroke-brand-500 transition-all duration-300"
        />

        <!-- 边：A -> B -->
        <path
          d="M 288 124 L 388 124"
          fill="none"
          stroke-width="2.2"
          marker-end="url(#tri-arrow)"
          class="text-slate-400 stroke-slate-400 transition-all duration-300 dark:text-slate-500 dark:stroke-slate-500"
        />
        <text x="338" y="116" text-anchor="middle" class="fill-slate-400 font-mono text-[10px]">A.b</text>

        <!-- 边：B -> C (正常 / 断开 / SATB拦截) -->
        <path
          d="M 462 124 L 564 124"
          fill="none"
          stroke-width="2.2"
          :marker-end="curStep.edgeBC === 'intact' ? 'url(#tri-arrow)' : undefined"
          class="transition-all duration-300"
          :class="curStep.edgeBC === 'intact'
            ? 'text-slate-400 stroke-slate-400 dark:text-slate-500 dark:stroke-slate-500'
            : curStep.edgeBC === 'satb-saved'
              ? 'text-emerald-500 stroke-emerald-500 [stroke-dasharray:5_4]'
              : 'text-red-400 stroke-red-400 [stroke-dasharray:5_4]'"
        />
        <text
          x="513"
          y="116"
          text-anchor="middle"
          class="font-mono text-[10px] font-semibold"
          :class="curStep.edgeBC === 'broken' ? 'fill-red-500' : curStep.edgeBC === 'satb-saved' ? 'fill-emerald-600 dark:fill-emerald-400' : 'fill-slate-400'"
        >
          {{ curStep.edgeBC === 'intact' ? 'B.c' : 'B.c = null' }}
        </text>
        <!-- B->C 断开红叉 -->
        <g v-if="curStep.edgeBC !== 'intact'" transform="translate(507, 118)">
          <line x1="0" y1="0" x2="12" y2="12" stroke-width="2.2" :class="curStep.edgeBC === 'satb-saved' ? 'stroke-emerald-500' : 'stroke-red-500'" />
          <line x1="12" y1="0" x2="0" y2="12" stroke-width="2.2" :class="curStep.edgeBC === 'satb-saved' ? 'stroke-emerald-500' : 'stroke-red-500'" />
        </g>
        <!-- SATB 写前屏障提示 -->
        <g v-if="curStep.edgeBC === 'satb-saved'" transform="translate(454, 146)">
          <rect width="128" height="22" rx="6" class="fill-emerald-500/15 stroke-emerald-500" stroke-width="1" />
          <text x="64" y="14" text-anchor="middle" class="fill-emerald-700 text-[10px] font-semibold dark:fill-emerald-300">写前屏障: 旧值 C 标灰</text>
        </g>

        <!-- 边：A -> C 新增并发弧线 -->
        <g v-if="curStep.edgeAC !== 'none'">
          <path
            d="M 262 92 Q 425 20 584 90"
            fill="none"
            stroke-width="2.4"
            marker-end="url(#tri-arrow)"
            class="transition-all duration-300 [stroke-dasharray:6_4]"
            :class="curStep.edgeAC === 'barrier-regray' ? 'text-brand-600 stroke-brand-600' : 'text-amber-500 stroke-amber-500'"
          />
          <rect
            x="355"
            y="36"
            width="142"
            height="22"
            rx="6"
            :class="curStep.edgeAC === 'barrier-regray' ? 'fill-brand-50 stroke-brand-400 dark:fill-brand-500/20 dark:stroke-brand-400' : 'fill-amber-50 stroke-amber-400 dark:fill-amber-500/20 dark:stroke-amber-400'"
            stroke-width="1"
          />
          <text
            x="426"
            y="50"
            text-anchor="middle"
            class="font-mono text-[10px] font-semibold"
            :class="curStep.edgeAC === 'barrier-regray' ? 'fill-brand-700 dark:fill-brand-300' : 'fill-amber-700 dark:fill-amber-300'"
          >
            {{ curStep.edgeAC === 'barrier-regray' ? 'A.c = C (写后屏障: A重灰)' : '并发新增: A.c = C' }}
          </text>
        </g>

        <!-- 节点：GC Roots -->
        <g transform="translate(26, 98)">
          <rect width="114" height="52" rx="12" class="fill-brand-600 stroke-brand-700" stroke-width="1.5" />
          <text x="57" y="23" text-anchor="middle" class="fill-white text-[12px] font-bold">GC Roots</text>
          <text x="57" y="40" text-anchor="middle" class="fill-brand-100 text-[10px]">栈帧 / 静态引用</text>
        </g>

        <!-- 节点：A -->
        <g transform="translate(252, 124)">
          <circle
            r="32"
            stroke-width="2.5"
            class="transition-all duration-300"
            :class="circleClass('A')"
          />
          <circle v-if="curStep.activeNode === 'A'" r="38" fill="none" class="stroke-brand-500/60" stroke-width="2" stroke-dasharray="4 3" />
          <text y="-2" text-anchor="middle" class="text-[15px] font-bold" :class="nodeTextClass('A')">A</text>
          <text y="14" text-anchor="middle" class="text-[9px]" :class="nodeTextClass('A')">{{ colorLabel(curStep.colors.A) }}</text>
        </g>

        <!-- 节点：B -->
        <g transform="translate(425, 124)">
          <circle r="32" stroke-width="2.5" class="transition-all duration-300" :class="circleClass('B')" />
          <circle v-if="curStep.activeNode === 'B'" r="38" fill="none" class="stroke-brand-500/60" stroke-width="2" stroke-dasharray="4 3" />
          <text y="-2" text-anchor="middle" class="text-[15px] font-bold" :class="nodeTextClass('B')">B</text>
          <text y="14" text-anchor="middle" class="text-[9px]" :class="nodeTextClass('B')">{{ colorLabel(curStep.colors.B) }}</text>
        </g>

        <!-- 节点：C -->
        <g transform="translate(600, 124)">
          <circle r="32" stroke-width="2.5" class="transition-all duration-300" :class="circleClass('C')" />
          <circle v-if="curStep.activeNode === 'C'" r="38" fill="none" class="stroke-brand-500/60" stroke-width="2" stroke-dasharray="4 3" />
          <text y="-2" text-anchor="middle" class="text-[15px] font-bold" :class="nodeTextClass('C')">C</text>
          <text y="14" text-anchor="middle" class="text-[9px]" :class="nodeTextClass('C')">{{ colorLabel(curStep.colors.C) }}</text>
          <text
            v-if="curStep.colors.C === 'swept'"
            y="48"
            text-anchor="middle"
            class="fill-red-600 text-[10px] font-bold dark:fill-red-400"
          >
            存活却被误回收！
          </text>
        </g>

        <!-- 节点：D (孤岛垃圾) -->
        <g transform="translate(120, 224)">
          <circle r="24" stroke-width="2" class="transition-all duration-300" :class="circleClass('D')" />
          <text y="-1" text-anchor="middle" class="text-[13px] font-bold" :class="nodeTextClass('D')">D</text>
          <text y="12" text-anchor="middle" class="text-[8.5px]" :class="nodeTextClass('D')">{{ curStep.colors.D === 'swept' ? '已回收' : '白色孤岛' }}</text>
          <text x="36" y="4" class="fill-slate-400 text-[10px]">← 无 Root 引用 (真垃圾)</text>
        </g>

        <!-- 右下角实时队列面板 -->
        <g transform="translate(340, 198)">
          <rect width="398" height="62" rx="10" class="fill-slate-50 stroke-slate-200 dark:fill-slate-900 dark:stroke-slate-700" stroke-width="1.2" />
          <text x="14" y="23" class="fill-slate-500 text-[10.5px] font-semibold dark:fill-slate-400">灰色待扫描队列 (Gray Queue):</text>
          <text x="188" y="23" class="fill-slate-800 font-mono text-[11px] font-bold dark:fill-slate-200">
            {{ curStep.grayQueue.length ? `[ ${curStep.grayQueue.join(', ')} ]` : '[ 空 ]' }}
          </text>
          <text x="14" y="46" class="fill-slate-500 text-[10.5px] font-semibold dark:fill-slate-400">写屏障缓冲 (Barrier Buffer):</text>
          <text
            x="188"
            y="46"
            class="font-mono text-[11px] font-bold"
            :class="curStep.barrierBuffer.length ? 'fill-emerald-600 dark:fill-emerald-400' : 'fill-slate-400'"
          >
            {{ curStep.barrierBuffer.length ? curStep.barrierBuffer.join(', ') : '无记录' }}
          </text>
        </g>
      </svg>
    </div>

    <!-- 步骤口述讲解卡 -->
    <div
      class="rounded-xl border p-3.5 text-sm"
      :class="curStep.danger
        ? 'border-red-200 bg-red-50/70 dark:border-red-500/30 dark:bg-red-500/10'
        : 'border-slate-200 bg-slate-50/80 dark:border-slate-800 dark:bg-slate-900/80'"
    >
      <p class="flex items-center gap-1.5 font-semibold" :class="curStep.danger ? 'text-red-700 dark:text-red-300' : 'text-slate-900 dark:text-white'">
        <TriangleAlert v-if="curStep.danger" class="h-4 w-4 shrink-0 text-red-500" />
        <span>{{ curStep.title }}</span>
      </p>
      <p class="mt-1.5 leading-relaxed text-slate-600 dark:text-slate-300">{{ curStep.speech }}</p>
      <p class="mt-2 flex items-start gap-1.5 rounded-lg bg-amber-50/90 px-2.5 py-1.5 text-xs text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
        <Sparkles class="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" />
        <span>{{ curStep.tip }}</span>
      </p>
    </div>

    <!-- 对比速查条 (在 fix 模式下展示) -->
    <div
      v-if="scenarioTab === 'fix'"
      class="grid gap-2.5 text-xs sm:grid-cols-2"
    >
      <div
        class="rounded-xl border p-3 transition"
        :class="fixStrategy === 'incremental'
          ? 'border-brand-500/50 bg-brand-50/60 dark:border-brand-500/40 dark:bg-brand-500/10'
          : 'border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900'"
      >
        <p class="font-semibold text-brand-700 dark:text-brand-300">CMS：增量更新 (Incremental Update)</p>
        <p class="mt-1 text-slate-600 dark:text-slate-300">破坏条件①（黑→白新引用）：写后屏障记录黑对象 A，Remark STW 阶段重新深度扫描 A 及其子树，耗时略长但无浮动垃圾。</p>
      </div>
      <div
        class="rounded-xl border p-3 transition"
        :class="fixStrategy === 'satb'
          ? 'border-emerald-500/50 bg-emerald-50/60 dark:border-emerald-500/40 dark:bg-emerald-500/10'
          : 'border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900'"
      >
        <p class="font-semibold text-emerald-700 dark:text-emerald-300">G1：SATB 原始快照 (Snapshot-At-The-Beginning)</p>
        <p class="mt-1 text-slate-600 dark:text-slate-300">破坏条件②（灰→白旧引用删除）：写前屏障保留旧引用 C 并标灰，Remark 仅需扫 SATB 栈极快，但可能产生少量浮动垃圾。</p>
      </div>
    </div>

    <!-- 播控栏 -->
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-center gap-1.5">
        <button
          v-for="(_, i) in activeSteps"
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
        <button class="btn-ghost h-8 px-2.5 text-xs" :disabled="stepIdx === activeSteps.length - 1" @click="nextStep">
          下一步 <ChevronRight class="h-3.5 w-3.5" />
        </button>
        <button class="btn-ghost h-8 px-2 text-xs" title="重置" @click="resetSteps">
          <RotateCcw class="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  </div>
</template>
