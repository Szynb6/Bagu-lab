<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ChevronLeft, ChevronRight, Layers, Pause, Play, RotateCcw, Lightbulb } from 'lucide-vue-next'

interface SkipNode {
  col: number
  score: number
  label: string
  member: string
  height: number // 1..4 (occupies levels 0..height-1)
}

interface ForwardEdge {
  id: string
  level: number
  fromCol: number
  toCol: number
  span: number
}

interface SearchStep {
  curCol: number
  curLevel: number
  hops: number
  drops: number
  rank: number
  found: boolean
  activeEdgeId: string | null
  rejectedEdgeId: string | null
  visitedEdgeIds: string[]
  visitedNodes: string[] // `${col}-${level}`
  actionBadge: string
  speech: string
  tip: string
}

const nodes: SkipNode[] = [
  { col: 0, score: -Infinity, label: 'head', member: '哨兵', height: 4 },
  { col: 1, score: 10, label: '10', member: 'u1', height: 1 },
  { col: 2, score: 20, label: '20', member: 'u2', height: 2 },
  { col: 3, score: 30, label: '30', member: 'u3', height: 3 },
  { col: 4, score: 45, label: '45', member: 'u4', height: 1 },
  { col: 5, score: 60, label: '60', member: 'u5', height: 4 },
  { col: 6, score: 75, label: '75', member: 'u6', height: 2 },
  { col: 7, score: 90, label: '90', member: 'u7', height: 1 },
]

const targets = [20, 45, 60, 75, 90] as const
const targetScore = ref<number>(75)
const stepIdx = ref(0)
const playing = ref(false)
let playTimer: number | undefined

// 根据节点塔高自动推导每层的 forward 指针与 span
const forwardEdges = computed<ForwardEdge[]>(() => {
  const result: ForwardEdge[] = []
  for (let lvl = 0; lvl < 4; lvl++) {
    const levelNodes = nodes.filter((n) => n.height > lvl)
    for (let i = 0; i < levelNodes.length - 1; i++) {
      const a = levelNodes[i]!
      const b = levelNodes[i + 1]!
      result.push({
        id: `e-${lvl}-${a.col}-${b.col}`,
        level: lvl,
        fromCol: a.col,
        toCol: b.col,
        span: b.col - a.col,
      })
    }
  }
  return result
})

function findForwardEdge(col: number, level: number): ForwardEdge | null {
  return forwardEdges.value.find((e) => e.level === level && e.fromCol === col) ?? null
}

function buildSteps(target: number): SearchStep[] {
  const list: SearchStep[] = []
  let curCol = 0
  let lvl = 3
  let hops = 0
  let drops = 0
  let rank = 0
  const visitedEdges: string[] = []
  const visitedNodes: string[] = ['0-3']

  list.push({
    curCol,
    curLevel: lvl,
    hops,
    drops,
    rank,
    found: false,
    activeEdgeId: null,
    rejectedEdgeId: null,
    visitedEdgeIds: [...visitedEdges],
    visitedNodes: [...visitedNodes],
    actionBadge: '从 L3 head 出发',
    speech: `查找 target score = ${target}：从最顶层索引 L3 的哨兵节点 head 开始，遵循「能右跳且不大于目标就右跳，否则降层」原则。`,
    tip: 'Redis zskiplist 最高层数限制为 32 (Redis 7.0 之前是 64)，节点插入时按幂次定律 p=0.25 随机决定层高。',
  })

  while (lvl >= 0) {
    const edge = findForwardEdge(curCol, lvl)
    const nextNode = edge ? nodes[edge.toCol]! : null

    if (edge && nextNode && nextNode.score <= target) {
      hops += 1
      rank += edge.span
      curCol = nextNode.col
      visitedEdges.push(edge.id)
      visitedNodes.push(`${curCol}-${lvl}`)

      const isHit = nextNode.score === target
      list.push({
        curCol,
        curLevel: lvl,
        hops,
        drops,
        rank,
        found: isHit,
        activeEdgeId: edge.id,
        rejectedEdgeId: null,
        visitedEdgeIds: [...visitedEdges],
        visitedNodes: [...visitedNodes],
        actionBadge: isHit ? `命中 ${target}！` : `L${lvl} 右跳 → ${nextNode.label}`,
        speech: isHit
          ? `L${lvl} 前向指针指向 ${nextNode.label} (${nextNode.score} <= ${target})，右跳跨越 span=${edge.span} 直接命中目标 (${nextNode.label}, ${nextNode.member})！`
          : `L${lvl} 下一个节点是 ${nextNode.label} (${nextNode.score} <= ${target})，直接沿 L${lvl} 右跳过去（跳过中间 ${edge.span - 1} 个节点，累计 span = ${rank}）。`,
        tip: isHit
          ? `对比结果：跳表仅右跳 ${hops} 次（总操作 ${hops + drops} 步），单链表从 head 需遍历 ${curCol} 次！同时累计 span 直接算出 ZRANK 排名 = ${rank}。`
          : '每条 forward 指针都记录了 span（跨越的 L0 节点数），因此 ZRANK 排名统计也是 O(log N) 完成。',
      })

      if (isHit) break
    } else {
      if (lvl === 0) break
      const nextLvl = lvl - 1
      drops += 1
      visitedNodes.push(`${curCol}-${nextLvl}`)

      const whyText = nextNode
        ? `L${lvl} 下一节点 ${nextNode.label} > ${target}（会越界）`
        : `L${lvl} 前向指针为 NULL`

      list.push({
        curCol,
        curLevel: nextLvl,
        hops,
        drops,
        rank,
        found: false,
        activeEdgeId: null,
        rejectedEdgeId: edge?.id ?? null,
        visitedEdgeIds: [...visitedEdges],
        visitedNodes: [...visitedNodes],
        actionBadge: `降层 L${lvl} → L${nextLvl}`,
        speech: `停在 ${nodes[curCol]!.label}：因为 ${whyText}，不能继续右跳，下降到 L${nextLvl} 继续试探！`,
        tip: '降层操作在同一节点塔内通过数组下标 level[i-1] 直接切换，O(1) 零指针跳转开销。',
      })

      lvl = nextLvl
    }
  }

  return list
}

const steps = computed(() => buildSteps(targetScore.value))
const currentStep = computed(() => steps.value[stepIdx.value] ?? steps.value[0]!)
const linearHops = computed(() => nodes.find((n) => n.score === targetScore.value)?.col ?? 0)

function colX(col: number) {
  return 56 + col * 84
}

function levelY(level: number) {
  // L3 在顶部 y=46, L0 在底部 y=214
  return 46 + (3 - level) * 56
}

function stopPlay() {
  playing.value = false
  if (playTimer) {
    clearInterval(playTimer)
    playTimer = undefined
  }
}

function selectTarget(score: number) {
  stopPlay()
  targetScore.value = score
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
  }, 1700)
}

function nodeKey(col: number, lvl: number) {
  return `${col}-${lvl}`
}

function cellRectClass(col: number, lvl: number) {
  const s = currentStep.value
  const isCurrent = s.curCol === col && s.curLevel === lvl
  if (isCurrent && s.found) return 'fill-emerald-700 stroke-emerald-600'
  if (isCurrent) return 'fill-brand-600 stroke-brand-700 dark:fill-brand-500 dark:stroke-blue-300'
  if (s.visitedNodes.includes(nodeKey(col, lvl))) {
    return 'fill-emerald-50 stroke-emerald-400 dark:fill-emerald-700/20 dark:stroke-emerald-400'
  }
  if (nodes[col]!.score === targetScore.value) {
    return 'fill-amber-50 stroke-amber-400 dark:fill-amber-700/15 dark:stroke-amber-400'
  }
  return 'fill-white stroke-slate-300 dark:fill-slate-800 dark:stroke-slate-600'
}

function cellTextClass(col: number, lvl: number) {
  const s = currentStep.value
  if (s.curCol === col && s.curLevel === lvl) return 'fill-white'
  if (s.visitedNodes.includes(nodeKey(col, lvl))) return 'fill-emerald-700 dark:fill-emerald-300'
  return 'fill-slate-700 dark:fill-slate-200'
}

function edgeLineClass(edge: ForwardEdge) {
  const s = currentStep.value
  if (s.activeEdgeId === edge.id) return 'stroke-brand-500 text-brand-500'
  if (s.rejectedEdgeId === edge.id) return 'stroke-red-400 text-red-400 [stroke-dasharray:4_3]'
  if (s.visitedEdgeIds.includes(edge.id)) return 'stroke-emerald-500 text-emerald-500'
  return 'stroke-slate-300 text-slate-300 dark:stroke-slate-700 dark:text-slate-700'
}

onBeforeUnmount(stopPlay)
</script>

<template>
  <div class="space-y-4">
    <!-- 目标 score 选择 + 复杂度实时对比徽章 -->
    <div class="flex flex-wrap items-center justify-between gap-2.5">
      <div class="flex flex-wrap items-center gap-1.5">
        <span class="text-xs font-medium text-slate-500 dark:text-slate-400">目标 score:</span>
        <button
          v-for="t in targets"
          :key="t"
          class="h-7 rounded-lg border px-2.5 font-mono text-xs font-semibold transition"
          :class="targetScore === t
            ? 'border-brand-500 bg-brand-600 text-white'
            : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300'"
          @click="selectTarget(t)"
        >
          {{ t }}
        </button>
      </div>

      <div class="flex flex-wrap items-center gap-2 font-mono text-[11px]">
        <span class="rounded-full bg-brand-100/80 px-2.5 py-0.5 font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-blue-300">
          跳表右跳: {{ currentStep.hops }} 次 (降层 {{ currentStep.drops }} 次)
        </span>
        <span class="rounded-full border border-slate-200 bg-white px-2.5 py-0.5 font-semibold text-slate-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">
          单链表 O(N): {{ linearHops }} 次
        </span>
        <span class="rounded-full bg-indigo-50 px-2.5 py-0.5 font-semibold text-indigo-700 dark:bg-indigo-500/20 dark:text-indigo-300">
          累计 span (Rank): {{ currentStep.rank }}
        </span>
      </div>
    </div>

    <!-- SkipList 4 层结构 SVG -->
    <div class="overflow-x-auto rounded-xl border border-slate-200/80 bg-white p-3 dark:border-slate-800 dark:bg-slate-950/70">
      <svg viewBox="0 0 760 288" class="mx-auto w-full min-w-[600px] max-w-[740px] select-none" role="img" aria-label="Redis ZSet 跳表 4 层查询路径动画">
        <defs>
          <marker id="sl-arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="5.5" markerHeight="5.5" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="currentColor" />
          </marker>
        </defs>

        <!-- 左侧层级标尺 L3..L0 -->
        <g v-for="lvl in [3, 2, 1, 0]" :key="`row-${lvl}`">
          <line x1="26" :y1="levelY(lvl)" x2="736" :y2="levelY(lvl)" stroke-width="1" class="stroke-slate-100 dark:stroke-slate-900" />
          <text x="12" :y="levelY(lvl) + 4" class="font-mono text-[11px] font-bold fill-slate-400">L{{ lvl }}</text>
        </g>

        <!-- 节点塔纵向脊线 -->
        <g v-for="node in nodes" :key="`spine-${node.col}`">
          <rect
            v-if="node.height > 1"
            :x="colX(node.col) - 29"
            :y="levelY(node.height - 1) - 18"
            width="58"
            :height="(node.height - 1) * 56 + 36"
            rx="10"
            stroke-width="1"
            class="fill-slate-50/70 stroke-slate-200 dark:fill-slate-900/40 dark:stroke-slate-800"
          />
          <line
            v-if="node.height > 1"
            :x1="colX(node.col)"
            :y1="levelY(node.height - 1) + 14"
            :x2="colX(node.col)"
            :y2="levelY(0) - 14"
            stroke-width="1.5"
            class="stroke-slate-200 [stroke-dasharray:3_3] dark:stroke-slate-700"
          />
        </g>

        <!-- Forward 指针连线 + span 标签 -->
        <g v-for="edge in forwardEdges" :key="edge.id">
          <line
            :x1="colX(edge.fromCol) + 26"
            :y1="levelY(edge.level)"
            :x2="colX(edge.toCol) - 27"
            :y2="levelY(edge.level)"
            :stroke-width="currentStep.activeEdgeId === edge.id || currentStep.visitedEdgeIds.includes(edge.id) ? 2.4 : 1.6"
            marker-end="url(#sl-arrow)"
            class="transition-[fill,stroke,opacity] duration-300"
            :class="edgeLineClass(edge)"
          />
          <!-- 仅在跨节点跳跃 (span > 1) 或当前激活边上展示 span 标签避免拥挤 -->
          <g v-if="edge.span > 1 || currentStep.activeEdgeId === edge.id || currentStep.rejectedEdgeId === edge.id">
            <rect
              :x="(colX(edge.fromCol) + colX(edge.toCol)) / 2 - 20"
              :y="levelY(edge.level) - 17"
              width="40"
              height="14"
              rx="5"
              class="fill-white/95 dark:fill-slate-950/95"
            />
            <text
              :x="(colX(edge.fromCol) + colX(edge.toCol)) / 2"
              :y="levelY(edge.level) - 7"
              text-anchor="middle"
              class="font-mono text-[9.5px] font-semibold"
              :class="currentStep.rejectedEdgeId === edge.id
                ? 'fill-red-500'
                : currentStep.activeEdgeId === edge.id || currentStep.visitedEdgeIds.includes(edge.id)
                  ? 'fill-brand-600 dark:fill-blue-300'
                  : 'fill-slate-400'"
            >
              span={{ edge.span }}
            </text>
          </g>
        </g>

        <!-- NULL 终点指向线 (每层最后一个节点向右) -->
        <g v-for="lvl in [3, 2, 1, 0]" :key="`null-${lvl}`">
          <text
            x="728"
            :y="levelY(lvl) + 4"
            text-anchor="middle"
            class="font-mono text-[9.5px] fill-slate-300 dark:fill-slate-600"
          >
            NULL
          </text>
        </g>

        <!-- 每层节点格子 -->
        <template v-for="node in nodes" :key="`node-${node.col}`">
          <g v-for="lvl in Array.from({ length: node.height }, (_, i) => i)" :key="nodeKey(node.col, lvl)">
            <rect
              :x="colX(node.col) - 24"
              :y="levelY(lvl) - 13"
              width="48"
              height="26"
              rx="7"
              stroke-width="1.6"
              class="transition-[fill,stroke,opacity] duration-300"
              :class="cellRectClass(node.col, lvl)"
            />
            <text
              :x="colX(node.col)"
              :y="levelY(lvl) + 4"
              text-anchor="middle"
              class="font-mono text-[11px] font-bold"
              :class="cellTextClass(node.col, lvl)"
            >
              {{ node.label }}
            </text>
          </g>
        </template>

        <!-- 底部 (score, member) 说明 -->
        <g v-for="node in nodes" :key="`meta-${node.col}`">
          <text
            :x="colX(node.col)"
            y="252"
            text-anchor="middle"
            class="font-mono text-[10px] font-semibold"
            :class="node.score === targetScore ? 'fill-brand-600 dark:fill-blue-300' : 'fill-slate-500 dark:fill-slate-400'"
          >
            {{ node.col === 0 ? 'head' : `${node.member}(${node.score})` }}
          </text>
          <text
            v-if="node.col > 0"
            :x="colX(node.col)"
            y="268"
            text-anchor="middle"
            class="font-mono text-[9.5px] fill-slate-400"
          >
            Rank #{{ node.col }}
          </text>
        </g>
      </svg>
    </div>

    <!-- 解说卡片 -->
    <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3.5 text-sm dark:border-slate-800 dark:bg-slate-900/80">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div class="flex items-center gap-1.5 font-semibold text-slate-900 dark:text-white">
          <Layers class="h-4 w-4 text-brand-500" />
          <span>查找路径追踪：{{ currentStep.actionBadge }}</span>
        </div>
        <span class="rounded-full bg-brand-100/80 px-2.5 py-0.5 font-mono text-[11px] font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-blue-300">
          步骤 {{ stepIdx + 1 }}/{{ steps.length }}
        </span>
      </div>
      <p class="mt-1.5 leading-relaxed text-slate-600 dark:text-slate-300">{{ currentStep.speech }}</p>
      <p class="mt-2 flex items-start gap-1.5 rounded-lg bg-amber-50/90 px-2.5 py-1.5 text-xs text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
        <Lightbulb class="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" />
        <span>{{ currentStep.tip }}</span>
      </p>
    </div>

    <!-- 控制栏 -->
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-center gap-1.5">
        <button
          v-for="(_, i) in steps"
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
          {{ playing ? '暂停' : '自动查找' }}
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
