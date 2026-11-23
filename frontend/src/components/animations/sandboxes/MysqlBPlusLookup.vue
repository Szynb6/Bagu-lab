<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  ChevronLeft,
  ChevronRight,
  Database,
  HardDrive,
  Pause,
  Play,
  RotateCcw,
  Sparkles,
  Zap,
} from 'lucide-vue-next'

type QueryMode = 'lookup' | 'covering'

interface LookupStep {
  title: string
  badge: string
  ioCount: number
  treeLookups: number
  bookmarkLookup: 'pending' | 'active' | 'done' | 'skipped'
  activeSecNodes: string[]
  doneSecNodes: string[]
  activeSecEdges: string[]
  activePriNodes: string[]
  donePriNodes: string[]
  activePriEdges: string[]
  showBookmarkArrow: boolean
  showCoveringReturn: boolean
  speech: string
  tip: string
}

const queryMode = ref<QueryMode>('lookup')
const stepIdx = ref(0)
const playing = ref(false)
let playTimer: number | undefined

const lookupSteps: LookupStep[] = [
  {
    title: '0. 解析 SQL：SELECT * 需要整行数据',
    badge: '优化器选 idx_name',
    ioCount: 0,
    treeLookups: 0,
    bookmarkLookup: 'pending',
    activeSecNodes: [],
    doneSecNodes: [],
    activeSecEdges: [],
    activePriNodes: [],
    donePriNodes: [],
    activePriEdges: [],
    showBookmarkArrow: false,
    showCoveringReturn: false,
    speech: '查询条件是 WHERE name = \'Tom\'，SELECT * 需要返回 (id, name, age, city) 全部字段。优化器优先走二级索引 idx_name 缩小范围。',
    tip: '记住：非聚簇索引的叶子节点只存「索引列 + 主键值」，不存其他业务列。',
  },
  {
    title: '1. 读取 idx_name 根节点 [Mia]',
    badge: '二级索引 · 第 1 层',
    ioCount: 1,
    treeLookups: 1,
    bookmarkLookup: 'pending',
    activeSecNodes: ['sec-root'],
    doneSecNodes: [],
    activeSecEdges: ['sec-r-b2'],
    activePriNodes: [],
    donePriNodes: [],
    activePriEdges: [],
    showBookmarkArrow: false,
    showCoveringReturn: false,
    speech: '读入 idx_name 根页（Page I/O +1）：比较 \'Tom\' >= \'Mia\'，顺着右侧指针进入第二层非叶子页。',
    tip: '1 个 B+Tree 节点对应 InnoDB 的 1 个 16KB 数据页，根节点常驻 Buffer Pool。',
  },
  {
    title: '2. 读取 idx_name 非叶子页 [Sam | Tom]',
    badge: '二级索引 · 第 2 层',
    ioCount: 2,
    treeLookups: 1,
    bookmarkLookup: 'pending',
    activeSecNodes: ['sec-b2'],
    doneSecNodes: ['sec-root'],
    activeSecEdges: ['sec-r-b2', 'sec-b2-l4'],
    activePriNodes: [],
    donePriNodes: [],
    activePriEdges: [],
    showBookmarkArrow: false,
    showCoveringReturn: false,
    speech: '读入非叶子页（Page I/O +1）：\'Tom\' >= \'Tom\'，沿最右侧指针下降到第 4 个叶子页。',
    tip: '3 层 B+Tree 通常就能支撑千万级数据，每次检索只需 2~3 次页读取。',
  },
  {
    title: '3. 命中 idx_name 叶子页：拿到 id = 18，但缺字段！',
    badge: '缺少 age / city',
    ioCount: 3,
    treeLookups: 1,
    bookmarkLookup: 'pending',
    activeSecNodes: ['sec-l4'],
    doneSecNodes: ['sec-root', 'sec-b2'],
    activeSecEdges: ['sec-r-b2', 'sec-b2-l4'],
    activePriNodes: [],
    donePriNodes: [],
    activePriEdges: [],
    showBookmarkArrow: false,
    showCoveringReturn: false,
    speech: '读入叶子页（Page I/O +1）：成功找到 Tom -> 主键 id=18。但叶子节点里没有 age 和 city，无法满足 SELECT *，必须回表！',
    tip: '高频追问：为什么不在二级索引里直接存整行？为了避免数据冗余和更新时多棵索引树同步维护的巨大开销。',
  },
  {
    title: '4. 触发「回表」：持 id = 18 再查 PRIMARY 聚簇索引',
    badge: '回表检索第 2 棵树',
    ioCount: 5,
    treeLookups: 2,
    bookmarkLookup: 'active',
    activeSecNodes: [],
    doneSecNodes: ['sec-root', 'sec-b2', 'sec-l4'],
    activeSecEdges: ['sec-r-b2', 'sec-b2-l4'],
    activePriNodes: ['pri-root', 'pri-b2'],
    donePriNodes: [],
    activePriEdges: ['pri-r-b2', 'pri-b2-l4'],
    showBookmarkArrow: true,
    showCoveringReturn: false,
    speech: '拿着 id=18 回到聚簇索引 PRIMARY：从根节点 [12]（18 >= 12 走右枝）读到非叶子页 [18]，再增加 2 次页 I/O！',
    tip: '回表意味着多查一棵 B+Tree，且回表时的主键 id 往往是随机的（随机 I/O 居多）。',
  },
  {
    title: '5. 命中 PRIMARY 叶子页：读出完整行数据返回',
    badge: '完成 · 共 6 次页 I/O',
    ioCount: 6,
    treeLookups: 2,
    bookmarkLookup: 'done',
    activeSecNodes: [],
    doneSecNodes: ['sec-root', 'sec-b2', 'sec-l4'],
    activeSecEdges: ['sec-r-b2', 'sec-b2-l4'],
    activePriNodes: ['pri-l4'],
    donePriNodes: ['pri-root', 'pri-b2'],
    activePriEdges: ['pri-r-b2', 'pri-b2-l4'],
    showBookmarkArrow: true,
    showCoveringReturn: false,
    speech: '读入聚簇索引叶子页（累计 6 次 I/O，遍历 2 棵树）：取得 id=18 的完整行 (18, Tom, 26, SH)，返回给客户端。',
    tip: '优化手段：只查必要列走覆盖索引，或通过「索引下推 (ICP)」「MRR 顺序回表」减少回表次数与随机 I/O。',
  },
]

const coveringSteps: LookupStep[] = [
  {
    title: '0. 解析 SQL：仅查询 (id, name)',
    badge: '覆盖索引候选',
    ioCount: 0,
    treeLookups: 0,
    bookmarkLookup: 'pending',
    activeSecNodes: [],
    doneSecNodes: [],
    activeSecEdges: [],
    activePriNodes: [],
    donePriNodes: [],
    activePriEdges: [],
    showBookmarkArrow: false,
    showCoveringReturn: false,
    speech: 'SQL 改为 SELECT id, name FROM user WHERE name = \'Tom\'。所需列恰好全部存在于 idx_name(name, id) 中！',
    tip: '平时写 SQL 禁止滥用 SELECT *，核心原因之一就是为了让高频查询命中覆盖索引。',
  },
  {
    title: '1. 读取 idx_name 根节点 [Mia]',
    badge: '二级索引 · 第 1 层',
    ioCount: 1,
    treeLookups: 1,
    bookmarkLookup: 'pending',
    activeSecNodes: ['sec-root'],
    doneSecNodes: [],
    activeSecEdges: ['sec-r-b2'],
    activePriNodes: [],
    donePriNodes: [],
    activePriEdges: [],
    showBookmarkArrow: false,
    showCoveringReturn: false,
    speech: '读入 idx_name 根页（Page I/O +1）：判断 \'Tom\' >= \'Mia\'，沿右侧指针向下检索。',
    tip: '检索阶段的前半程与普通回表完全相同。',
  },
  {
    title: '2. 读取 idx_name 非叶子页 [Sam | Tom]',
    badge: '二级索引 · 第 2 层',
    ioCount: 2,
    treeLookups: 1,
    bookmarkLookup: 'pending',
    activeSecNodes: ['sec-b2'],
    doneSecNodes: ['sec-root'],
    activeSecEdges: ['sec-r-b2', 'sec-b2-l4'],
    activePriNodes: [],
    donePriNodes: [],
    activePriEdges: [],
    showBookmarkArrow: false,
    showCoveringReturn: false,
    speech: '读入非叶子页（Page I/O +1）：\'Tom\' >= \'Tom\'，定位到最右侧叶子页。',
    tip: '非叶子页仅存键值与页指针，单页可容纳上千个键值（高扇出、低树高）。',
  },
  {
    title: '3. 命中 idx_name 叶子页：同时拿到 name 与 id=18',
    badge: '数据已齐全',
    ioCount: 3,
    treeLookups: 1,
    bookmarkLookup: 'skipped',
    activeSecNodes: ['sec-l4'],
    doneSecNodes: ['sec-root', 'sec-b2'],
    activeSecEdges: ['sec-r-b2', 'sec-b2-l4'],
    activePriNodes: [],
    donePriNodes: [],
    activePriEdges: [],
    showBookmarkArrow: false,
    showCoveringReturn: false,
    speech: '读入叶子页（Page I/O +1）：记录里直接存着 (name=\'Tom\', id=18)，已经凑齐 SELECT 所需全部列！',
    tip: '二级索引叶子页天然自带主键值 id，所以查 id 无需回表。',
  },
  {
    title: '4. 覆盖索引生效 (Using index) → 直接返回！',
    badge: '跳过回表 · I/O 减半',
    ioCount: 3,
    treeLookups: 1,
    bookmarkLookup: 'skipped',
    activeSecNodes: [],
    doneSecNodes: ['sec-root', 'sec-b2', 'sec-l4'],
    activeSecEdges: ['sec-r-b2', 'sec-b2-l4'],
    activePriNodes: [],
    donePriNodes: [],
    activePriEdges: [],
    showBookmarkArrow: false,
    showCoveringReturn: true,
    speech: 'EXPLAIN 显示 Extra: Using index！直接从 idx_name 叶子节点返回 (18, \'Tom\')，聚簇索引 PRIMARY 全程 0 访问，I/O 直接减半！',
    tip: '面试金句：覆盖索引就是「索引列 + 主键列」完全覆盖查询字段，避免回表导致的额外 B+Tree 检索与随机 I/O。',
  },
]

const steps = computed(() => (queryMode.value === 'lookup' ? lookupSteps : coveringSteps))
const currentStep = computed(() => steps.value[stepIdx.value] ?? steps.value[0]!)

function stopPlay() {
  playing.value = false
  if (playTimer) {
    clearInterval(playTimer)
    playTimer = undefined
  }
}

function selectMode(mode: QueryMode) {
  stopPlay()
  queryMode.value = mode
  stepIdx.value = 0
}

function prevStep() {
  stopPlay()
  if (stepIdx.value > 0) stepIdx.value -= 1
}

function nextStep() {
  stopPlay()
  if (stepIdx.value < steps.value.length - 1) stepIdx.value += 1
}

function resetSteps() {
  stopPlay()
  stepIdx.value = 0
}

function togglePlay() {
  if (playing.value) {
    stopPlay()
    return
  }
  if (stepIdx.value >= steps.value.length - 1) stepIdx.value = 0
  playing.value = true
  playTimer = window.setInterval(() => {
    if (stepIdx.value < steps.value.length - 1) {
      stepIdx.value += 1
    } else {
      stopPlay()
    }
  }, 2200)
}

function secNodeClass(id: string) {
  const s = currentStep.value
  if (s.activeSecNodes.includes(id)) {
    return 'fill-brand-600 stroke-brand-700 dark:fill-brand-500 dark:stroke-blue-300'
  }
  if (s.doneSecNodes.includes(id)) {
    return 'fill-emerald-500 stroke-emerald-600 dark:fill-emerald-500 dark:stroke-emerald-400'
  }
  return 'fill-white stroke-slate-300 dark:fill-slate-800 dark:stroke-slate-600'
}

function secTextClass(id: string) {
  const s = currentStep.value
  return s.activeSecNodes.includes(id) || s.doneSecNodes.includes(id)
    ? 'fill-white'
    : 'fill-slate-700 dark:fill-slate-200'
}

function priNodeClass(id: string) {
  const s = currentStep.value
  if (queryMode.value === 'covering') {
    return 'fill-slate-100/70 stroke-slate-200 opacity-45 dark:fill-slate-900/50 dark:stroke-slate-800'
  }
  if (s.activePriNodes.includes(id)) {
    return 'fill-amber-500 stroke-amber-600 dark:fill-amber-500 dark:stroke-amber-300'
  }
  if (s.donePriNodes.includes(id)) {
    return 'fill-emerald-500 stroke-emerald-600 dark:fill-emerald-500 dark:stroke-emerald-400'
  }
  return 'fill-white stroke-slate-300 dark:fill-slate-800 dark:stroke-slate-600'
}

function priTextClass(id: string) {
  const s = currentStep.value
  if (queryMode.value === 'covering') return 'fill-slate-400 dark:fill-slate-600'
  return s.activePriNodes.includes(id) || s.donePriNodes.includes(id)
    ? 'fill-white'
    : 'fill-slate-700 dark:fill-slate-200'
}

function secEdgeClass(id: string) {
  const active = currentStep.value.activeSecEdges.includes(id)
  return active
    ? 'stroke-brand-500 text-brand-500 [stroke-dasharray:5_3]'
    : 'stroke-slate-300 text-slate-300 dark:stroke-slate-700 dark:text-slate-700'
}

function priEdgeClass(id: string) {
  if (queryMode.value === 'covering') {
    return 'stroke-slate-200 text-slate-200 opacity-40 dark:stroke-slate-800 dark:text-slate-800'
  }
  const active = currentStep.value.activePriEdges.includes(id)
  return active
    ? 'stroke-amber-500 text-amber-500 [stroke-dasharray:5_3]'
    : 'stroke-slate-300 text-slate-300 dark:stroke-slate-700 dark:text-slate-700'
}

onBeforeUnmount(stopPlay)
</script>

<template>
  <div class="space-y-4">
    <!-- 顶部 SQL 模式切换 + 实时 I/O 计数器 -->
    <div class="flex flex-wrap items-center justify-between gap-2.5">
      <div class="inline-flex flex-wrap rounded-xl border border-slate-200 bg-slate-100/80 p-0.5 text-xs dark:border-slate-700 dark:bg-slate-800">
        <button
          class="rounded-lg px-3 py-1.5 font-mono transition"
          :class="queryMode === 'lookup'
            ? 'bg-white font-semibold text-amber-700 shadow-xs dark:bg-slate-900 dark:text-amber-300'
            : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'"
          @click="selectMode('lookup')"
        >
          SELECT * (触发回表)
        </button>
        <button
          class="rounded-lg px-3 py-1.5 font-mono transition"
          :class="queryMode === 'covering'
            ? 'bg-white font-semibold text-emerald-700 shadow-xs dark:bg-slate-900 dark:text-emerald-300'
            : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'"
          @click="selectMode('covering')"
        >
          SELECT id, name (覆盖索引)
        </button>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <span class="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-2.5 py-0.5 font-mono text-[11px] font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200">
          <HardDrive class="h-3 w-3 text-brand-500" />
          页 I/O: {{ currentStep.ioCount }} 次
        </span>
        <span class="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-2.5 py-0.5 font-mono text-[11px] font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200">
          <Database class="h-3 w-3 text-indigo-500" />
          检索 B+Tree: {{ currentStep.treeLookups }} 棵
        </span>
        <span
          class="inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 font-mono text-[11px] font-semibold"
          :class="{
            'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400': currentStep.bookmarkLookup === 'pending',
            'bg-amber-100 text-amber-800 dark:bg-amber-500/20 dark:text-amber-300': currentStep.bookmarkLookup === 'active' || currentStep.bookmarkLookup === 'done',
            'bg-emerald-100 text-emerald-800 dark:bg-emerald-500/20 dark:text-emerald-300': currentStep.bookmarkLookup === 'skipped',
          }"
        >
          <Zap class="h-3 w-3" />
          {{
            currentStep.bookmarkLookup === 'skipped'
              ? '回表: 已跳过 (Using index)'
              : currentStep.bookmarkLookup === 'pending'
                ? '回表: 待判断'
                : '回表: 已触发'
          }}
        </span>
      </div>
    </div>

    <!-- 当前 SQL 预览栏 -->
    <div class="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-slate-200/80 bg-slate-900 px-3.5 py-2 font-mono text-xs text-slate-100 dark:border-slate-700">
      <span>
        <span class="text-purple-300">SELECT</span>
        <span class="mx-1 font-semibold" :class="queryMode === 'lookup' ? 'text-amber-300' : 'text-emerald-300'">
          {{ queryMode === 'lookup' ? '*' : 'id, name' }}
        </span>
        <span class="text-purple-300">FROM</span> user
        <span class="text-purple-300">WHERE</span> name = <span class="text-emerald-300">'Tom'</span>;
      </span>
      <span class="text-[11px] text-slate-400">
        EXPLAIN Extra:
        <span :class="queryMode === 'covering' ? 'text-emerald-300' : 'text-amber-300'">
          {{ queryMode === 'covering' ? 'Using index' : 'NULL (需回表补齐 age, city)' }}
        </span>
      </span>
    </div>

    <!-- 双 B+Tree SVG 视图 -->
    <div class="overflow-x-auto rounded-xl border border-slate-200/80 bg-white p-3 dark:border-slate-800 dark:bg-slate-950/70">
      <svg viewBox="0 0 760 392" class="mx-auto w-full min-w-[600px] max-w-[740px] select-none" role="img" aria-label="MySQL 二级索引回表与覆盖索引双 B+Tree 演示">
        <defs>
          <marker id="bp-arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="currentColor" />
          </marker>
        </defs>

        <!-- 上半区：二级索引 idx_name(name -> id) -->
        <rect
          x="10"
          y="8"
          width="740"
          height="168"
          rx="14"
          stroke-width="1.5"
          class="fill-brand-50/40 stroke-blue-200 dark:fill-brand-500/10 dark:stroke-brand-500/30"
        />
        <text x="24" y="28" class="fill-brand-700 text-[11px] font-semibold dark:fill-blue-300">
          ① 二级索引 B+Tree · idx_name (name → id)
        </text>

        <!-- 二级索引连线 -->
        <path d="M 335 46 L 195 66" fill="none" stroke-width="2" marker-end="url(#bp-arrow)" class="transition-all duration-300" :class="secEdgeClass('sec-r-b1')" />
        <path d="M 425 46 L 565 66" fill="none" stroke-width="2" marker-end="url(#bp-arrow)" class="transition-all duration-300" :class="secEdgeClass('sec-r-b2')" />

        <path d="M 155 94 L 98 116" fill="none" stroke-width="2" marker-end="url(#bp-arrow)" class="transition-all duration-300" :class="secEdgeClass('sec-b1-l1')" />
        <path d="M 235 94 L 285 116" fill="none" stroke-width="2" marker-end="url(#bp-arrow)" class="transition-all duration-300" :class="secEdgeClass('sec-b1-l2')" />
        <path d="M 525 94 L 472 116" fill="none" stroke-width="2" marker-end="url(#bp-arrow)" class="transition-all duration-300" :class="secEdgeClass('sec-b2-l3')" />
        <path d="M 605 94 L 656 116" fill="none" stroke-width="2" marker-end="url(#bp-arrow)" class="transition-all duration-300" :class="secEdgeClass('sec-b2-l4')" />

        <!-- 叶子双向链表连接 -->
        <path d="M 176 139 L 196 139" stroke-width="1.5" class="stroke-slate-300 dark:stroke-slate-600" />
        <path d="M 364 139 L 384 139" stroke-width="1.5" class="stroke-slate-300 dark:stroke-slate-600" />
        <path d="M 552 139 L 572 139" stroke-width="1.5" class="stroke-slate-300 dark:stroke-slate-600" />

        <!-- idx_name Root -->
        <g>
          <rect x="315" y="18" width="130" height="28" rx="8" stroke-width="1.6" class="transition-all duration-300" :class="secNodeClass('sec-root')" />
          <text x="380" y="36" text-anchor="middle" class="font-mono text-[11px] font-semibold" :class="secTextClass('sec-root')">Root: [ Mia ]</text>
        </g>

        <!-- idx_name Branch -->
        <g>
          <rect x="125" y="66" width="140" height="28" rx="8" stroke-width="1.6" class="transition-all duration-300" :class="secNodeClass('sec-b1')" />
          <text x="195" y="84" text-anchor="middle" class="font-mono text-[11px] font-semibold" :class="secTextClass('sec-b1')">[ Amy | Bob ]</text>

          <rect x="495" y="66" width="140" height="28" rx="8" stroke-width="1.6" class="transition-all duration-300" :class="secNodeClass('sec-b2')" />
          <text x="565" y="84" text-anchor="middle" class="font-mono text-[11px] font-semibold" :class="secTextClass('sec-b2')">[ Sam | Tom ]</text>
        </g>

        <!-- idx_name Leaf Pages -->
        <g>
          <rect x="24" y="116" width="152" height="46" rx="9" stroke-width="1.6" class="transition-all duration-300" :class="secNodeClass('sec-l1')" />
          <text x="100" y="135" text-anchor="middle" class="font-mono text-[10.5px] font-semibold" :class="secTextClass('sec-l1')">Amy → id=4</text>
          <text x="100" y="152" text-anchor="middle" class="font-mono text-[10px] opacity-80" :class="secTextClass('sec-l1')">(叶子页 #1)</text>

          <rect x="196" y="116" width="168" height="46" rx="9" stroke-width="1.6" class="transition-all duration-300" :class="secNodeClass('sec-l2')" />
          <text x="280" y="135" text-anchor="middle" class="font-mono text-[10.5px] font-semibold" :class="secTextClass('sec-l2')">Bob→9 | Jack→11</text>
          <text x="280" y="152" text-anchor="middle" class="font-mono text-[10px] opacity-80" :class="secTextClass('sec-l2')">(叶子页 #2)</text>

          <rect x="384" y="116" width="168" height="46" rx="9" stroke-width="1.6" class="transition-all duration-300" :class="secNodeClass('sec-l3')" />
          <text x="468" y="135" text-anchor="middle" class="font-mono text-[10.5px] font-semibold" :class="secTextClass('sec-l3')">Mia→12 | Sam→15</text>
          <text x="468" y="152" text-anchor="middle" class="font-mono text-[10px] opacity-80" :class="secTextClass('sec-l3')">(叶子页 #3)</text>

          <rect x="572" y="116" width="164" height="46" rx="9" stroke-width="1.8" class="transition-all duration-300" :class="secNodeClass('sec-l4')" />
          <text x="654" y="135" text-anchor="middle" class="font-mono text-[10.5px] font-bold" :class="secTextClass('sec-l4')">Tom→18 | Zoe→25</text>
          <text x="654" y="152" text-anchor="middle" class="font-mono text-[10px]" :class="secTextClass('sec-l4')">仅含 name + id</text>
        </g>

        <!-- 中间过渡区：回表连线 OR 覆盖索引直接返回 -->
        <g v-if="currentStep.showBookmarkArrow">
          <path
            d="M 654 162 C 654 192, 460 176, 425 224"
            fill="none"
            stroke-width="2.5"
            marker-end="url(#bp-arrow)"
            class="stroke-amber-500 text-amber-500 [stroke-dasharray:6_4]"
          />
          <rect x="444" y="178" width="194" height="22" rx="11" class="fill-amber-500" />
          <text x="541" y="193" text-anchor="middle" class="fill-white text-[10.5px] font-semibold">
            回表：携 id=18 查 PRIMARY
          </text>
        </g>

        <g v-if="currentStep.showCoveringReturn">
          <rect x="468" y="177" width="268" height="24" rx="12" class="fill-emerald-500" />
          <text x="602" y="193" text-anchor="middle" class="fill-white text-[10.5px] font-semibold">
            ✓ 覆盖索引命中！直接返回 (18, 'Tom')，跳过回表
          </text>
        </g>

        <!-- 下半区：聚簇索引 PRIMARY(id -> full row) -->
        <rect
          x="10"
          y="208"
          width="740"
          height="176"
          rx="14"
          stroke-width="1.5"
          class="transition-all duration-300"
          :class="queryMode === 'covering'
            ? 'fill-slate-100/60 stroke-slate-200 opacity-60 dark:fill-slate-900/40 dark:stroke-slate-800'
            : 'fill-amber-50/35 stroke-amber-200 dark:fill-amber-500/10 dark:stroke-amber-500/30'"
        />
        <text
          x="24"
          y="228"
          class="text-[11px] font-semibold"
          :class="queryMode === 'covering' ? 'fill-slate-400' : 'fill-amber-700 dark:fill-amber-300'"
        >
          ② 聚簇索引 B+Tree · PRIMARY (id → 完整行数据)
        </text>
        <text
          v-if="queryMode === 'covering'"
          x="734"
          y="228"
          text-anchor="end"
          class="fill-emerald-600 text-[11px] font-semibold dark:fill-emerald-400"
        >
          本次查询全程未访问该树
        </text>

        <!-- PRIMARY 连线 -->
        <path d="M 335 246 L 195 266" fill="none" stroke-width="2" marker-end="url(#bp-arrow)" class="transition-all duration-300" :class="priEdgeClass('pri-r-b1')" />
        <path d="M 425 246 L 565 266" fill="none" stroke-width="2" marker-end="url(#bp-arrow)" class="transition-all duration-300" :class="priEdgeClass('pri-r-b2')" />

        <path d="M 155 294 L 98 316" fill="none" stroke-width="2" marker-end="url(#bp-arrow)" class="transition-all duration-300" :class="priEdgeClass('pri-b1-l1')" />
        <path d="M 235 294 L 285 316" fill="none" stroke-width="2" marker-end="url(#bp-arrow)" class="transition-all duration-300" :class="priEdgeClass('pri-b1-l2')" />
        <path d="M 525 294 L 472 316" fill="none" stroke-width="2" marker-end="url(#bp-arrow)" class="transition-all duration-300" :class="priEdgeClass('pri-b2-l3')" />
        <path d="M 605 294 L 656 316" fill="none" stroke-width="2" marker-end="url(#bp-arrow)" class="transition-all duration-300" :class="priEdgeClass('pri-b2-l4')" />

        <path d="M 176 342 L 196 342" stroke-width="1.5" class="stroke-slate-300 dark:stroke-slate-700" />
        <path d="M 364 342 L 384 342" stroke-width="1.5" class="stroke-slate-300 dark:stroke-slate-700" />
        <path d="M 552 342 L 572 342" stroke-width="1.5" class="stroke-slate-300 dark:stroke-slate-700" />

        <!-- PRIMARY Root -->
        <g>
          <rect x="315" y="218" width="130" height="28" rx="8" stroke-width="1.6" class="transition-all duration-300" :class="priNodeClass('pri-root')" />
          <text x="380" y="236" text-anchor="middle" class="font-mono text-[11px] font-semibold" :class="priTextClass('pri-root')">Root: [ 12 ]</text>
        </g>

        <!-- PRIMARY Branch -->
        <g>
          <rect x="125" y="266" width="140" height="28" rx="8" stroke-width="1.6" class="transition-all duration-300" :class="priNodeClass('pri-b1')" />
          <text x="195" y="284" text-anchor="middle" class="font-mono text-[11px] font-semibold" :class="priTextClass('pri-b1')">[ 4 | 9 ]</text>

          <rect x="495" y="266" width="140" height="28" rx="8" stroke-width="1.6" class="transition-all duration-300" :class="priNodeClass('pri-b2')" />
          <text x="565" y="284" text-anchor="middle" class="font-mono text-[11px] font-semibold" :class="priTextClass('pri-b2')">[ 15 | 18 ]</text>
        </g>

        <!-- PRIMARY Leaf Pages -->
        <g>
          <rect x="24" y="316" width="152" height="54" rx="9" stroke-width="1.6" class="transition-all duration-300" :class="priNodeClass('pri-l1')" />
          <text x="100" y="337" text-anchor="middle" class="font-mono text-[10.5px] font-semibold" :class="priTextClass('pri-l1')">id=4 (Amy)</text>
          <text x="100" y="354" text-anchor="middle" class="font-mono text-[9.5px] opacity-85" :class="priTextClass('pri-l1')">age=21, city='BJ'</text>

          <rect x="196" y="316" width="168" height="54" rx="9" stroke-width="1.6" class="transition-all duration-300" :class="priNodeClass('pri-l2')" />
          <text x="280" y="337" text-anchor="middle" class="font-mono text-[10.5px] font-semibold" :class="priTextClass('pri-l2')">id=9, id=11</text>
          <text x="280" y="354" text-anchor="middle" class="font-mono text-[9.5px] opacity-85" :class="priTextClass('pri-l2')">完整行数据 (Bob/Jack)</text>

          <rect x="384" y="316" width="168" height="54" rx="9" stroke-width="1.6" class="transition-all duration-300" :class="priNodeClass('pri-l3')" />
          <text x="468" y="337" text-anchor="middle" class="font-mono text-[10.5px] font-semibold" :class="priTextClass('pri-l3')">id=12, id=15</text>
          <text x="468" y="354" text-anchor="middle" class="font-mono text-[9.5px] opacity-85" :class="priTextClass('pri-l3')">完整行数据 (Mia/Sam)</text>

          <rect x="572" y="316" width="164" height="54" rx="9" stroke-width="1.8" class="transition-all duration-300" :class="priNodeClass('pri-l4')" />
          <text x="654" y="337" text-anchor="middle" class="font-mono text-[10.5px] font-bold" :class="priTextClass('pri-l4')">id=18 (Tom) | id=25</text>
          <text x="654" y="354" text-anchor="middle" class="font-mono text-[9.5px]" :class="priTextClass('pri-l4')">18, Tom, 26, 'SH'</text>
        </g>
      </svg>
    </div>

    <!-- 步骤口述卡 -->
    <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3.5 text-sm dark:border-slate-800 dark:bg-slate-900/80">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <p class="font-semibold text-slate-900 dark:text-white">{{ currentStep.title }}</p>
        <span class="rounded-full bg-brand-100/80 px-2.5 py-0.5 font-mono text-[11px] font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-blue-300">
          {{ currentStep.badge }} · {{ stepIdx + 1 }}/{{ steps.length }}
        </span>
      </div>
      <p class="mt-1.5 leading-relaxed text-slate-600 dark:text-slate-300">{{ currentStep.speech }}</p>
      <p class="mt-2 flex items-start gap-1.5 rounded-lg bg-amber-50/90 px-2.5 py-1.5 text-xs text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
        <Sparkles class="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" />
        <span>{{ currentStep.tip }}</span>
      </p>
    </div>

    <!-- 底部控制条 -->
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-center gap-1.5">
        <button
          v-for="(_, i) in steps"
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
        <button class="btn-ghost h-8 px-2.5 text-xs" :disabled="stepIdx === steps.length - 1" @click="nextStep">
          下一步 <ChevronRight class="h-3.5 w-3.5" />
        </button>
        <button class="btn-ghost h-8 px-2 text-xs" title="重置" @click="resetSteps">
          <RotateCcw class="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  </div>
</template>
