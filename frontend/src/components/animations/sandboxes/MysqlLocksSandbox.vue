<script setup lang="ts">
import { computed, ref } from 'vue'
import { CheckCircle2, Lock, RotateCcw, ShieldAlert, Lightbulb, Unlock } from 'lucide-vue-next'

type ScenarioId = 'unique-hit' | 'unique-miss' | 'nonunique-hit' | 'range'
type GapId = '(-inf,5)' | '(5,10)' | '(10,15)' | '(15,20)' | '(20,+inf)'
type ProbeKey = '3' | '7' | '10' | '12' | '18'

interface ScenarioDef {
  id: ScenarioId
  tabLabel: string
  sql: string
  indexDesc: string
  lockedRecords: number[]
  lockedGaps: GapId[]
  nextKeyGap?: GapId
  lockBadges: string[]
  mechanism: string
  tip: string
}

interface ProbeDef {
  key: ProbeKey
  btnLabel: string
  sql: string
  x: number
  targetType: 'gap' | 'record'
  gapId?: GapId
  recordVal?: number
}

const scenarios: ScenarioDef[] = [
  {
    id: 'unique-hit',
    tabLabel: '1. 唯一索引命中',
    sql: 'SELECT * FROM t WHERE id = 10 FOR UPDATE;',
    indexDesc: 'id 为主键 / 唯一索引（RR 隔离级别）',
    lockedRecords: [10],
    lockedGaps: [],
    lockBadges: ['Record Lock: {10}'],
    mechanism: '唯一索引等值查询且命中记录 10：InnoDB 本应加 Next-Key Lock (5, 10]，但由于唯一索引能保证不会出现第二个 id=10，退化为只锁单行的 Record Lock {10}。',
    tip: '口诀：唯一索引等值命中 → Next-Key Lock 退化为 Record Lock，前后间隙均不加锁，并发插入性能最优。',
  },
  {
    id: 'unique-miss',
    tabLabel: '2. 唯一索引未命中',
    sql: 'SELECT * FROM t WHERE id = 7 FOR UPDATE;',
    indexDesc: 'id 为主键 / 唯一索引（记录 7 不存在）',
    lockedRecords: [],
    lockedGaps: ['(5,10)'],
    lockBadges: ['Gap Lock: (5, 10)'],
    mechanism: '唯一索引等值查询 id=7 未命中：InnoDB 找到首个大于 7 的索引项 10，将 Next-Key Lock (5, 10] 退化为纯 Gap Lock (5, 10)，只锁间隙、不锁记录 10 本身！',
    tip: '高频坑点：Gap Lock 之间互相兼容！两个事务同时对不存在的 id=7 执行 FOR UPDATE 都不会阻塞，但之后各自想 INSERT 7 时才会互相死锁。',
  },
  {
    id: 'nonunique-hit',
    tabLabel: '3. 非唯一索引命中',
    sql: 'SELECT * FROM t WHERE age = 10 FOR UPDATE;',
    indexDesc: 'age 为普通二级索引（允许重复值）',
    lockedRecords: [10],
    lockedGaps: ['(5,10)', '(10,15)'],
    nextKeyGap: '(5,10)',
    lockBadges: ['Next-Key Lock: (5, 10]', 'Gap Lock: (10, 15)'],
    mechanism: '普通索引允许插入多个 age=10，必须防止两侧产生新 10！扫描命中 10 加 Next-Key Lock (5, 10]；继续向右扫描到首个不等于 10 的记录 15，退化为 Gap Lock (10, 15)。',
    tip: '重点区分：非唯一索引等值命中不仅锁左开右闭的 (5, 10]，还会把右侧到下一个值之间的间隙 (10, 15) 一并锁住！',
  },
  {
    id: 'range',
    tabLabel: '4. 范围查询 (8.0)',
    sql: 'SELECT * FROM t WHERE id >= 10 AND id < 15 FOR UPDATE;',
    indexDesc: 'id 为唯一索引（MySQL 8.0 范围加锁规则）',
    lockedRecords: [10],
    lockedGaps: ['(10,15)'],
    lockBadges: ['Record Lock: {10}', 'Gap Lock: (10, 15)'],
    mechanism: 'MySQL 8.0 中：id >= 10 包含等值命中 10，(5, 10] 退化为 Record Lock {10}；向右范围扫描遇到 15 不满足 < 15，(10, 15] 退化为 Gap Lock (10, 15)。（注：MySQL 5.7 未做该两处退化）。',
    tip: '面试官问范围锁记得先报版本：MySQL 8.0 对唯一索引 >= 的起点和 < 的终点有退化优化，5.7 则会锁 (5, 10] 与 (10, 15]。',
  },
]

const probes: ProbeDef[] = [
  { key: '3', btnLabel: '尝试 INSERT 3', sql: 'INSERT INTO t VALUES (3);', x: 120, targetType: 'gap', gapId: '(-inf,5)' },
  { key: '7', btnLabel: '尝试 INSERT 7', sql: 'INSERT INTO t VALUES (7);', x: 255, targetType: 'gap', gapId: '(5,10)' },
  { key: '10', btnLabel: '尝试 UPDATE 10', sql: 'UPDATE t SET v=1 WHERE id/age = 10;', x: 325, targetType: 'record', recordVal: 10 },
  { key: '12', btnLabel: '尝试 INSERT 12', sql: 'INSERT INTO t VALUES (12);', x: 395, targetType: 'gap', gapId: '(10,15)' },
  { key: '18', btnLabel: '尝试 INSERT 18', sql: 'INSERT INTO t VALUES (18);', x: 535, targetType: 'gap', gapId: '(15,20)' },
]

const gapBoxes: { id: GapId; x1: number; x2: number; label: string }[] = [
  { id: '(-inf,5)', x1: 55, x2: 185, label: '(-∞, 5)' },
  { id: '(5,10)', x1: 185, x2: 325, label: '(5, 10)' },
  { id: '(10,15)', x1: 325, x2: 465, label: '(10, 15)' },
  { id: '(15,20)', x1: 465, x2: 605, label: '(15, 20)' },
  { id: '(20,+inf)', x1: 605, x2: 705, label: '(20, +∞)' },
]

const recordNodes = [
  { val: 5, x: 185 },
  { val: 10, x: 325 },
  { val: 15, x: 465 },
  { val: 20, x: 605 },
]

const activeScenarioId = ref<ScenarioId>('unique-hit')
const selectedProbeKey = ref<ProbeKey | null>(null)

const scenario = computed(
  () => scenarios.find((s) => s.id === activeScenarioId.value) ?? scenarios[0]!,
)

const activeProbe = computed(() =>
  selectedProbeKey.value ? probes.find((p) => p.key === selectedProbeKey.value) ?? null : null,
)

const probeResult = computed(() => {
  const probe = activeProbe.value
  if (!probe) return null
  const s = scenario.value

  if (probe.targetType === 'record' && probe.recordVal !== undefined) {
    const blocked = s.lockedRecords.includes(probe.recordVal)
    if (blocked) {
      return {
        blocked: true,
        headline: `阻塞等待 (BLOCKED) · ${probe.sql}`,
        reason: s.nextKeyGap
          ? `记录 ${probe.recordVal} 已被事务 A 的 Next-Key Lock ${s.nextKeyGap.replace(')', ']')} 加上排他行锁，事务 B 请求 X 行锁必须阻塞等待！`
          : `记录 ${probe.recordVal} 已被事务 A 的 Record Lock {${probe.recordVal}} 锁定，事务 B 修改该行时发生行锁冲突！`,
      }
    }
    return {
      blocked: false,
      headline: `执行成功 (ALLOWED) · ${probe.sql}`,
      reason: `事务 A 当前仅持有间隙锁 ${s.lockBadges.join(' + ')}，并未对记录 ${probe.recordVal} 加 Record Lock，因此 UPDATE ${probe.recordVal} 可立即成功！`,
    }
  }

  if (probe.targetType === 'gap' && probe.gapId) {
    const blocked = s.lockedGaps.includes(probe.gapId)
    if (blocked) {
      const lockName = s.nextKeyGap === probe.gapId
        ? `Next-Key Lock ${probe.gapId.replace(')', ']')}`
        : `Gap Lock ${probe.gapId}`
      return {
        blocked: true,
        headline: `阻塞等待 (BLOCKED) · ${probe.sql}`,
        reason: `插入值 ${probe.key} 落在区间 ${probe.gapId} 内，INSERT 会先申请「插入意向锁 (Insert Intention Lock)」，它与事务 A 持有的 ${lockName} 冲突而被阻塞！`,
      }
    }
    return {
      blocked: false,
      headline: `插入成功 (ALLOWED) · ${probe.sql}`,
      reason: `值 ${probe.key} 位于区间 ${probe.gapId}，未被事务 A 的间隙锁覆盖，插入意向锁申请通过，INSERT 正常落库！`,
    }
  }

  return null
})

function selectScenario(id: ScenarioId) {
  activeScenarioId.value = id
  selectedProbeKey.value = null
}

function runProbe(key: ProbeKey) {
  selectedProbeKey.value = key
}

function isGapLocked(gapId: GapId) {
  return scenario.value.lockedGaps.includes(gapId)
}

function isGapConflict(gapId: GapId) {
  const probe = activeProbe.value
  return Boolean(probe && probe.gapId === gapId && probeResult.value?.blocked)
}

function isRecordLocked(val: number) {
  return scenario.value.lockedRecords.includes(val)
}

function isRecordConflict(val: number) {
  const probe = activeProbe.value
  return Boolean(probe && probe.recordVal === val && probeResult.value?.blocked)
}
</script>

<template>
  <div class="space-y-4">
    <!-- 场景切换 Tabs -->
    <div class="grid grid-cols-2 gap-1.5 sm:grid-cols-4">
      <button
        v-for="item in scenarios"
        :key="item.id"
        class="rounded-xl border px-2.5 py-2 text-left text-xs font-medium transition"
        :class="activeScenarioId === item.id
          ? 'border-brand-500 bg-brand-50 text-brand-700 shadow-xs dark:border-brand-500/60 dark:bg-brand-500/15 dark:text-blue-300'
          : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800'"
        @click="selectScenario(item.id)"
      >
        {{ item.tabLabel }}
      </button>
    </div>

    <!-- 当前加锁 SQL & 锁集合徽章 -->
    <div class="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-slate-200/80 bg-slate-900 px-3.5 py-2.5 text-xs text-slate-100 dark:border-slate-700">
      <div>
        <div class="font-mono text-emerald-300">事务 A: {{ scenario.sql }}</div>
        <div class="mt-0.5 text-[11px] text-slate-400">{{ scenario.indexDesc }}</div>
      </div>
      <div class="flex flex-wrap items-center gap-1.5">
        <span
          v-for="badge in scenario.lockBadges"
          :key="badge"
          class="inline-flex items-center gap-1 rounded-full bg-amber-500/20 px-2.5 py-0.5 font-mono text-[11px] font-semibold text-amber-300"
        >
          <Lock class="h-3 w-3" /> {{ badge }}
        </span>
      </div>
    </div>

    <!-- InnoDB 有序索引数轴 SVG -->
    <div class="overflow-x-auto rounded-xl border border-slate-200/80 bg-white p-3 dark:border-slate-800 dark:bg-slate-950/70">
      <svg viewBox="0 0 760 218" class="mx-auto w-full min-w-[580px] max-w-[740px] select-none" role="img" aria-label="InnoDB 索引数轴与 Record/Gap/Next-Key Lock 可视化">
        <!-- 主数轴线 -->
        <line x1="40" y1="126" x2="720" y2="126" stroke-width="2.5" class="stroke-slate-300 dark:stroke-slate-700" />

        <!-- 间隙区间高亮条 -->
        <g v-for="gap in gapBoxes" :key="gap.id">
          <rect
            :x="gap.x1 + 14"
            y="102"
            :width="gap.x2 - gap.x1 - 28"
            height="48"
            rx="8"
            stroke-width="1.6"
            class="transition-[fill,stroke,opacity] duration-300"
            :class="isGapConflict(gap.id)
              ? 'fill-red-500/20 stroke-red-500 [stroke-dasharray:5_3]'
              : isGapLocked(gap.id)
                ? 'fill-amber-400/20 stroke-amber-500 [stroke-dasharray:5_3] dark:fill-amber-700/15'
                : 'fill-slate-100/50 stroke-transparent dark:fill-slate-900/40'"
          />
          <text
            :x="(gap.x1 + gap.x2) / 2"
            y="172"
            text-anchor="middle"
            class="font-mono text-[10.5px] font-medium"
            :class="isGapConflict(gap.id)
              ? 'fill-red-600 dark:fill-red-400'
              : isGapLocked(gap.id)
                ? 'fill-amber-700 dark:fill-amber-300'
                : 'fill-slate-400'"
          >
            {{ gap.label }}
          </text>
          <text
            v-if="isGapLocked(gap.id)"
            :x="(gap.x1 + gap.x2) / 2"
            y="95"
            text-anchor="middle"
            class="text-[10px] font-semibold"
            :class="isGapConflict(gap.id) ? 'fill-red-600 dark:fill-red-400' : 'fill-amber-600 dark:fill-amber-400'"
          >
            {{ scenario.nextKeyGap === gap.id ? 'Next-Key 间隙' : 'Gap Lock' }}
          </text>
        </g>

        <!-- -∞ 与 +∞ 哨兵端点 -->
        <g>
          <circle cx="55" cy="126" r="16" class="fill-slate-100 stroke-slate-300 dark:fill-slate-800 dark:stroke-slate-600" stroke-width="1.5" />
          <text x="55" y="130" text-anchor="middle" class="font-mono text-[12px] font-semibold fill-slate-500 dark:fill-slate-300">-∞</text>

          <circle cx="705" cy="126" r="16" class="fill-slate-100 stroke-slate-300 dark:fill-slate-800 dark:stroke-slate-600" stroke-width="1.5" />
          <text x="705" y="130" text-anchor="middle" class="font-mono text-[12px] font-semibold fill-slate-500 dark:fill-slate-300">+∞</text>
        </g>

        <!-- 已有索引记录节点 (5, 10, 15, 20) -->
        <g v-for="rec in recordNodes" :key="rec.val">
          <circle
            :cx="rec.x"
            cy="126"
            r="20"
            stroke-width="2.2"
            class="transition-[fill,stroke,opacity] duration-300"
            :class="isRecordConflict(rec.val)
              ? 'fill-red-500 stroke-red-700'
              : isRecordLocked(rec.val)
                ? 'fill-amber-700 stroke-amber-600'
                : 'fill-white stroke-slate-400 dark:fill-slate-800 dark:stroke-slate-500'"
          />
          <text
            :x="rec.x"
            y="131"
            text-anchor="middle"
            class="font-mono text-[13px] font-bold"
            :class="isRecordLocked(rec.val) || isRecordConflict(rec.val) ? 'fill-white' : 'fill-slate-800 dark:fill-slate-100'"
          >
            {{ rec.val }}
          </text>
          <text
            v-if="isRecordLocked(rec.val)"
            :x="rec.x"
            y="196"
            text-anchor="middle"
            class="text-[10px] font-semibold"
            :class="isRecordConflict(rec.val) ? 'fill-red-600 dark:fill-red-400' : 'fill-amber-600 dark:fill-amber-400'"
          >
            Record Lock
          </text>
        </g>

        <!-- 并发事务 B 试探探针 -->
        <g v-if="activeProbe && probeResult">
          <line
            :x1="activeProbe.x"
            y1="38"
            :x2="activeProbe.x"
            y2="104"
            stroke-width="2.2"
            :class="probeResult.blocked ? 'stroke-red-500 [stroke-dasharray:4_3]' : 'stroke-emerald-500'"
          />
          <rect
            :x="activeProbe.x - 56"
            y="12"
            width="112"
            height="28"
            rx="14"
            :class="probeResult.blocked ? 'fill-red-500' : 'fill-emerald-700'"
          />
          <text :x="activeProbe.x" y="30" text-anchor="middle" class="font-mono text-[11px] font-bold fill-white">
            {{ probeResult.blocked ? `✗ 阻塞 (${activeProbe.key})` : `✓ 放行 (${activeProbe.key})` }}
          </text>
          <circle
            :cx="activeProbe.x"
            cy="126"
            r="7"
            :class="probeResult.blocked ? 'fill-red-500 stroke-white' : 'fill-emerald-700 stroke-white'"
            stroke-width="2"
          />
        </g>

        <!-- 图例 -->
        <g transform="translate(20, 12)">
          <rect x="0" y="0" width="12" height="12" rx="3" class="fill-amber-700" />
          <text x="17" y="10" class="fill-slate-500 text-[10px] dark:fill-slate-400">行锁 Record Lock</text>
          <rect x="115" y="0" width="14" height="12" rx="3" class="fill-amber-400/25 stroke-amber-500 [stroke-dasharray:3_2]" />
          <text x="134" y="10" class="fill-slate-500 text-[10px] dark:fill-slate-400">间隙锁 Gap / Next-Key</text>
        </g>
      </svg>
    </div>

    <!-- 并发事务 B 尝试操作按钮区 -->
    <div class="space-y-2">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <span class="text-xs font-semibold text-slate-700 dark:text-slate-200">
          事务 B 并发试探（点击查看是否被事务 A 阻塞）：
        </span>
        <button
          v-if="selectedProbeKey"
          class="inline-flex items-center gap-1 text-xs text-slate-500 hover:text-slate-700 dark:text-slate-400"
          @click="selectedProbeKey = null"
        >
          <RotateCcw class="h-3 w-3" /> 清除探针
        </button>
      </div>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="p in probes"
          :key="p.key"
          class="h-8 rounded-xl border px-3 font-mono text-xs font-medium transition"
          :class="selectedProbeKey === p.key
            ? probeResult?.blocked
              ? 'border-red-500 bg-red-50 text-red-700 dark:bg-red-500/15 dark:text-red-300'
              : 'border-emerald-500 bg-emerald-50 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300'
            : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800'"
          @click="runProbe(p.key)"
        >
          {{ p.btnLabel }}
        </button>
      </div>
    </div>

    <!-- 探针结果反馈框 -->
    <div
      v-if="probeResult"
      class="flex items-start gap-2.5 rounded-xl border px-3.5 py-2.5 text-xs leading-relaxed"
      :class="probeResult.blocked
        ? 'border-red-200 bg-red-50 text-red-800 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300'
        : 'border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300'"
    >
      <ShieldAlert v-if="probeResult.blocked" class="mt-0.5 h-4 w-4 shrink-0 text-red-500" />
      <Unlock v-else class="mt-0.5 h-4 w-4 shrink-0 text-emerald-500" />
      <div>
        <p class="font-mono font-semibold">{{ probeResult.headline }}</p>
        <p class="mt-0.5">{{ probeResult.reason }}</p>
      </div>
    </div>

    <!-- 加锁原理解析卡 -->
    <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3.5 text-sm dark:border-slate-800 dark:bg-slate-900/80">
      <div class="flex items-center gap-1.5 font-semibold text-slate-900 dark:text-white">
        <CheckCircle2 class="h-4 w-4 text-brand-500" />
        <span>当前场景加锁原理解析</span>
      </div>
      <p class="mt-1.5 leading-relaxed text-slate-600 dark:text-slate-300">{{ scenario.mechanism }}</p>
      <p class="mt-2 flex items-start gap-1.5 rounded-lg bg-amber-50/90 px-2.5 py-1.5 text-xs text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
        <Lightbulb class="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" />
        <span>{{ scenario.tip }}</span>
      </p>
    </div>
  </div>
</template>
