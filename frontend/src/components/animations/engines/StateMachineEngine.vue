<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, Lightbulb, Pause, Play, RotateCcw } from 'lucide-vue-next'
import type { StateNode, StateSpec, StateTransition } from '../types'

const props = defineProps<{ spec: StateSpec }>()

const mode = ref<'guided' | 'explore'>('guided')
const stepIdx = ref(0)
const playing = ref(false)
let timer: number | undefined

const freeNode = ref(props.spec.initialNode ?? props.spec.nodes[0]?.id ?? '')
const freeLastTransition = ref<string | null>(null)
const freeLog = ref('点击下方触发事件按钮，亲自驱动状态跃迁。')

const step = computed(() => props.spec.steps[stepIdx.value] ?? props.spec.steps[0]!)
const nodeMap = computed(() => Object.fromEntries(props.spec.nodes.map((n) => [n.id, n])))

const availableTransitions = computed(() =>
  props.spec.transitions.filter((t) => t.from === freeNode.value),
)

function stopPlay() {
  playing.value = false
  if (timer) {
    clearInterval(timer)
    timer = undefined
  }
}

function togglePlay() {
  if (playing.value) {
    stopPlay()
    return
  }
  if (stepIdx.value >= props.spec.steps.length - 1) stepIdx.value = 0
  playing.value = true
  timer = window.setInterval(() => {
    if (stepIdx.value < props.spec.steps.length - 1) stepIdx.value += 1
    else stopPlay()
  }, 2300)
}

function triggerTransition(t: StateTransition) {
  freeNode.value = t.to
  freeLastTransition.value = t.id
  const fromLabel = nodeMap.value[t.from]?.label ?? t.from
  const toLabel = nodeMap.value[t.to]?.label ?? t.to
  freeLog.value = `触发【${t.action ?? t.label}】：${fromLabel} → ${toLabel}`
}

function resetExplore() {
  freeNode.value = props.spec.initialNode ?? props.spec.nodes[0]?.id ?? ''
  freeLastTransition.value = null
  freeLog.value = '状态机已重置到初始状态，点击事件按钮继续探索。'
}

watch(() => props.spec, () => {
  stopPlay()
  mode.value = 'guided'
  stepIdx.value = 0
  resetExplore()
})
onBeforeUnmount(stopPlay)

function isCurrent(id: string) {
  return mode.value === 'guided' ? step.value.currentNodes.includes(id) : freeNode.value === id
}

function nodeClass(n: StateNode) {
  if (mode.value === 'guided') {
    if (step.value.dangerNodes?.includes(n.id)) return 'fill-red-700 stroke-red-800'
    if (step.value.currentNodes.includes(n.id)) return 'fill-brand-600 stroke-brand-700'
    if (step.value.visitedNodes?.includes(n.id)) return 'fill-emerald-700 stroke-emerald-800'
  } else if (freeNode.value === n.id) {
    return 'fill-brand-600 stroke-brand-700'
  }
  return 'fill-white stroke-[#D4D0C5] dark:fill-[#22211E] dark:stroke-[#3B3933]'
}

function isSolid(id: string) {
  if (mode.value === 'explore') return freeNode.value === id
  return Boolean(
    step.value.currentNodes.includes(id)
    || step.value.visitedNodes?.includes(id)
    || step.value.dangerNodes?.includes(id),
  )
}

function isActiveEdge(id: string) {
  return mode.value === 'guided'
    ? Boolean(step.value.activeTransitions?.includes(id))
    : freeLastTransition.value === id
}

function edgeGeom(t: StateTransition) {
  const a = nodeMap.value[t.from]
  const b = nodeMap.value[t.to]
  if (!a || !b) return { d: '', lx: 0, ly: 0 }
  const aw = (a.w ?? 112) / 2
  const ah = (a.h ?? 44) / 2
  const bw = (b.w ?? 112) / 2
  const bh = (b.h ?? 44) / 2
  const dx = b.x - a.x
  const dy = b.y - a.y
  const sx = Math.abs(dx) * ah > Math.abs(dy) * aw ? a.x + Math.sign(dx) * (aw + 2) : a.x + (dx / (Math.abs(dy) || 1)) * (ah + 2)
  const sy = Math.abs(dx) * ah > Math.abs(dy) * aw ? a.y + (dy / (Math.abs(dx) || 1)) * (aw + 2) : a.y + Math.sign(dy) * (ah + 2)
  const ex = Math.abs(dx) * bh > Math.abs(dy) * bw ? b.x - Math.sign(dx) * (bw + 4) : b.x - (dx / (Math.abs(dy) || 1)) * (bh + 4)
  const ey = Math.abs(dx) * bh > Math.abs(dy) * bw ? b.y - (dy / (Math.abs(dx) || 1)) * (bw + 4) : b.y - Math.sign(dy) * (bh + 4)
  const mx = (sx + ex) / 2
  const my = (sy + ey) / 2
  const c = t.curved ?? 0
  if (c) {
    const len = Math.hypot(ex - sx, ey - sy) || 1
    const nx = -(ey - sy) / len
    const ny = (ex - sx) / len
    const cx = mx + nx * c
    const cy = my + ny * c
    return { d: `M ${sx.toFixed(1)} ${sy.toFixed(1)} Q ${cx.toFixed(1)} ${cy.toFixed(1)} ${ex.toFixed(1)} ${ey.toFixed(1)}`, lx: mx + nx * c * 0.56, ly: my + ny * c * 0.56 - 4 }
  }
  return { d: `M ${sx.toFixed(1)} ${sy.toFixed(1)} L ${ex.toFixed(1)} ${ey.toFixed(1)}`, lx: mx, ly: my - 6 }
}

const uid = `sm-${Math.random().toString(36).slice(2, 8)}`
</script>

<template>
  <div class="space-y-3.5">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div
        v-if="spec.interactive"
        class="inline-flex rounded-lg border border-[var(--border-default)] bg-[var(--bg-subtle)] p-0.5 text-xs"
      >
        <button
          type="button"
          class="rounded-md px-2.5 py-1 font-medium transition-colors"
          :class="mode === 'guided' ? 'bg-[var(--bg-surface)] text-[#C84B31] dark:text-[#E06246]' : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'"
          @click="stopPlay(); mode = 'guided'"
        >
          标准演练
        </button>
        <button
          type="button"
          class="rounded-md px-2.5 py-1 font-medium transition-colors"
          :class="mode === 'explore' ? 'bg-[var(--bg-surface)] text-[#C84B31] dark:text-[#E06246]' : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'"
          @click="stopPlay(); mode = 'explore'"
        >
          自由触发跃迁
        </button>
      </div>
      <span v-else class="text-xs font-medium text-[var(--text-muted)]">状态机演练</span>

      <span class="rounded border border-[#EFC9BF] bg-[#FBF0ED] px-2 py-0.5 font-mono text-[11px] font-semibold tabular-nums text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]">
        {{ mode === 'guided' ? `${step.badge} · ${stepIdx + 1}/${spec.steps.length}` : `当前：${nodeMap[freeNode]?.label}` }}
      </span>
    </div>

    <div class="overflow-x-auto rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] p-2.5">
      <svg :viewBox="spec.viewBox ?? '0 0 760 280'" class="mx-auto w-full min-w-[560px] max-w-[760px] select-none">
        <defs>
          <marker :id="`${uid}-arr`" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="currentColor" />
          </marker>
        </defs>

        <g v-for="t in spec.transitions" :key="t.id">
          <path
            :d="edgeGeom(t).d"
            fill="none"
            :stroke-width="isActiveEdge(t.id) ? 2.5 : 1.5"
            :marker-end="`url(#${uid}-arr)`"
            class="transition-[stroke,opacity] duration-300"
            :class="
              isActiveEdge(t.id)
                ? 'text-brand-500 stroke-brand-500 [stroke-dasharray:6_4]'
                : 'text-[#C8C3B8] stroke-[#C8C3B8] dark:text-[#3B3933] dark:stroke-[#3B3933]'
            "
          />
          <text
            :x="edgeGeom(t).lx"
            :y="edgeGeom(t).ly"
            text-anchor="middle"
            class="text-[9.5px] font-medium"
            :class="isActiveEdge(t.id) ? 'fill-[#96311D] font-semibold dark:fill-[#EFC0B3]' : 'fill-[#736E64] dark:fill-[#8F8A7E]'"
          >
            {{ t.label }}
          </text>
        </g>

        <g v-for="n in spec.nodes" :key="n.id" :transform="`translate(${n.x - (n.w ?? 112) / 2}, ${n.y - (n.h ?? 44) / 2})`">
          <rect
            :width="n.w ?? 112"
            :height="n.h ?? 44"
            :rx="n.r ?? 12"
            stroke-width="1.6"
            class="transition-[fill,stroke] duration-300"
            :class="nodeClass(n)"
          />
          <text
            :x="(n.w ?? 112) / 2"
            :y="n.sub ? (n.h ?? 44) / 2 - 3 : (n.h ?? 44) / 2 + 4"
            text-anchor="middle"
            class="text-[11px] font-semibold"
            :class="isSolid(n.id) ? 'fill-white' : 'fill-[#161614] dark:fill-[#ECEAE4]'"
          >
            {{ n.label }}
          </text>
          <text
            v-if="n.sub"
            :x="(n.w ?? 112) / 2"
            :y="(n.h ?? 44) / 2 + 12"
            text-anchor="middle"
            class="text-[9px]"
            :class="isSolid(n.id) ? 'fill-white/90' : 'fill-[#5E5A52] dark:fill-[#A8A397]'"
          >
            {{ n.sub }}
          </text>
          <g v-if="mode === 'guided' && step.nodeTags?.[n.id]" :transform="`translate(${(n.w ?? 112) / 2 - 30}, -12)`">
            <rect width="60" height="16" rx="6" class="fill-amber-700" />
            <text x="30" y="11" text-anchor="middle" class="fill-white text-[9px] font-bold">
              {{ step.nodeTags[n.id] }}
            </text>
          </g>
          <circle v-if="isCurrent(n.id)" :cx="(n.w ?? 112) - 8" cy="8" r="3.5" class="fill-amber-300" />
        </g>
      </svg>
    </div>

    <template v-if="mode === 'guided'">
      <div class="rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] p-3.5 text-sm">
        <p class="font-semibold text-[var(--text-primary)]">{{ step.title }}</p>
        <p class="mt-1 leading-relaxed text-[var(--text-secondary)]">{{ step.speech }}</p>
        <p class="mt-2.5 flex items-start gap-1.5 rounded-md border border-amber-300/70 bg-amber-50/70 px-2.5 py-1.5 text-xs text-amber-900 dark:border-amber-700/40 dark:bg-amber-950/35 dark:text-amber-200">
          <Lightbulb class="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-600 dark:text-amber-400" />
          <span>{{ step.tip }}</span>
        </p>
      </div>

      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-1.5">
          <button
            v-for="(_, i) in spec.steps"
            :key="i"
            type="button"
            class="h-2 rounded-full transition-[width,background-color] duration-200"
            :class="i === stepIdx ? 'w-6 bg-[#C84B31] dark:bg-[#E06246]' : 'w-2 bg-[var(--border-strong)] hover:bg-[var(--text-muted)]'"
            :aria-label="`跳转到第 ${i + 1} 步`"
            @click="stopPlay(); stepIdx = i"
          />
        </div>
        <div class="flex items-center gap-2">
          <button type="button" class="btn-ghost h-8 px-2.5 text-xs" :disabled="stepIdx === 0" @click="stopPlay(); stepIdx--">
            <ChevronLeft class="h-3.5 w-3.5" /> 上一步
          </button>
          <button type="button" class="btn-primary h-8 px-3 text-xs" @click="togglePlay">
            <component :is="playing ? Pause : Play" class="h-3.5 w-3.5" />
            {{ playing ? '暂停' : '自动播放' }}
          </button>
          <button type="button" class="btn-ghost h-8 px-2.5 text-xs" :disabled="stepIdx >= spec.steps.length - 1" @click="stopPlay(); stepIdx++">
            下一步 <ChevronRight class="h-3.5 w-3.5" />
          </button>
          <button type="button" class="btn-ghost h-8 px-2 text-xs" title="重置" aria-label="重置动画到第一步" @click="stopPlay(); stepIdx = 0">
            <RotateCcw class="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] px-3.5 py-2.5 text-xs text-[var(--text-secondary)]">
        {{ freeLog }}
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <button
          v-for="t in availableTransitions"
          :key="t.id"
          type="button"
          class="btn-primary h-8 px-3 text-xs"
          @click="triggerTransition(t)"
        >
          {{ t.action ?? t.label }} → {{ nodeMap[t.to]?.label }}
        </button>
        <span v-if="!availableTransitions.length" class="text-xs text-[var(--text-muted)]">已到达终态，点击右侧重置重新探索。</span>
        <button type="button" class="btn-ghost ml-auto h-8 px-3 text-xs" @click="resetExplore">
          <RotateCcw class="h-3.5 w-3.5" /> 重置状态
        </button>
      </div>
    </template>
  </div>
</template>
