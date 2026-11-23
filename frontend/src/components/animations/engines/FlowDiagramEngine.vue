<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, Lightbulb, Pause, Play, RotateCcw } from 'lucide-vue-next'
import type { FlowEdge, FlowNode, FlowSpec, Tone } from '../types'

const props = defineProps<{ spec: FlowSpec }>()

const scenarioIdx = ref(0)
const stepIdx = ref(0)
const playing = ref(false)
let timer: number | undefined

const scenario = computed(() => props.spec.scenarios[scenarioIdx.value] ?? props.spec.scenarios[0]!)
const step = computed(() => scenario.value.steps[stepIdx.value] ?? scenario.value.steps[0]!)
const nodeMap = computed(() => Object.fromEntries(scenario.value.nodes.map((n) => [n.id, n])))

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
  if (stepIdx.value >= scenario.value.steps.length - 1) stepIdx.value = 0
  playing.value = true
  timer = window.setInterval(() => {
    if (stepIdx.value < scenario.value.steps.length - 1) stepIdx.value += 1
    else stopPlay()
  }, 2400)
}

function selectScenario(idx: number) {
  stopPlay()
  scenarioIdx.value = idx
  stepIdx.value = 0
}

watch(() => props.spec, () => {
  stopPlay()
  scenarioIdx.value = 0
  stepIdx.value = 0
})

onBeforeUnmount(stopPlay)

function zoneClass(tone: Tone = 'slate') {
  const map: Record<Tone, string> = {
    slate: 'fill-[#F4F2EC]/80 stroke-[#D4D0C5] dark:fill-[#22211E]/70 dark:stroke-[#3B3933]',
    brand: 'fill-brand-50/60 stroke-brand-200 dark:fill-brand-950/40 dark:stroke-brand-800',
    emerald: 'fill-emerald-50/50 stroke-emerald-200 dark:fill-emerald-950/35 dark:stroke-emerald-800',
    amber: 'fill-amber-50/50 stroke-amber-200 dark:fill-amber-950/35 dark:stroke-amber-800',
    red: 'fill-red-50/50 stroke-red-200 dark:fill-red-950/35 dark:stroke-red-800',
    indigo: 'fill-slate-100/70 stroke-slate-300 dark:fill-slate-800/40 dark:stroke-slate-700',
    purple: 'fill-stone-100/70 stroke-stone-300 dark:fill-stone-800/40 dark:stroke-stone-700',
    cyan: 'fill-teal-50/50 stroke-teal-200 dark:fill-teal-950/35 dark:stroke-teal-800',
  }
  return map[tone]
}

function nodeBoxClass(n: FlowNode) {
  const s = step.value
  if (s.dangerNodes?.includes(n.id)) return 'fill-red-700 stroke-red-800 dark:fill-red-700 dark:stroke-red-500'
  if (s.warnNodes?.includes(n.id)) return 'fill-amber-700 stroke-amber-800 dark:fill-amber-700 dark:stroke-amber-500'
  if (s.activeNodes?.includes(n.id)) return 'fill-brand-600 stroke-brand-700 dark:fill-brand-500 dark:stroke-brand-300'
  if (s.doneNodes?.includes(n.id)) return 'fill-emerald-700 stroke-emerald-800 dark:fill-emerald-700 dark:stroke-emerald-500'
  if (s.mutedNodes?.includes(n.id)) return 'fill-[#F4F2EC]/70 stroke-[#E5E2DA] opacity-45 dark:fill-[#1A1917]/60 dark:stroke-[#2B2A26]'
  const base: Record<Tone, string> = {
    slate: 'fill-white stroke-[#D4D0C5] dark:fill-[#22211E] dark:stroke-[#3B3933]',
    brand: 'fill-brand-50/80 stroke-brand-300 dark:fill-brand-950/50 dark:stroke-brand-700',
    emerald: 'fill-emerald-50/80 stroke-emerald-300 dark:fill-emerald-950/40 dark:stroke-emerald-700',
    amber: 'fill-amber-50/80 stroke-amber-300 dark:fill-amber-950/40 dark:stroke-amber-700',
    red: 'fill-red-50/80 stroke-red-300 dark:fill-red-950/40 dark:stroke-red-700',
    indigo: 'fill-slate-100 stroke-slate-300 dark:fill-slate-800 dark:stroke-slate-600',
    purple: 'fill-stone-100 stroke-stone-300 dark:fill-stone-800 dark:stroke-stone-600',
    cyan: 'fill-teal-50/80 stroke-teal-300 dark:fill-teal-950/40 dark:stroke-teal-700',
  }
  return base[n.tone ?? 'slate']
}

function isSolidNode(id: string) {
  const s = step.value
  return Boolean(
    s.activeNodes?.includes(id)
    || s.doneNodes?.includes(id)
    || s.warnNodes?.includes(id)
    || s.dangerNodes?.includes(id),
  )
}

function edgeGeom(e: FlowEdge) {
  const a = nodeMap.value[e.from]
  const b = nodeMap.value[e.to]
  if (!a || !b) return { d: e.path ?? '', lx: e.lx ?? 0, ly: e.ly ?? 0 }

  if (e.path) {
    return {
      d: e.path,
      lx: e.lx ?? (a.x + a.w / 2 + b.x + b.w / 2) / 2,
      ly: e.ly ?? (a.y + a.h / 2 + b.y + b.h / 2) / 2 - 8,
    }
  }

  const ax = a.x + a.w / 2
  const ay = a.y + a.h / 2
  const bx = b.x + b.w / 2
  const by = b.y + b.h / 2
  const dx = bx - ax
  const dy = by - ay

  const sx = Math.abs(dx) * a.h > Math.abs(dy) * a.w
    ? ax + Math.sign(dx) * (a.w / 2 + 2)
    : ax + (dx / (Math.abs(dy) || 1)) * (a.h / 2 + 2)
  const sy = Math.abs(dx) * a.h > Math.abs(dy) * a.w
    ? ay + (dy / (Math.abs(dx) || 1)) * (a.w / 2 + 2)
    : ay + Math.sign(dy) * (a.h / 2 + 2)

  const ex = Math.abs(dx) * b.h > Math.abs(dy) * b.w
    ? bx - Math.sign(dx) * (b.w / 2 + 4)
    : bx - (dx / (Math.abs(dy) || 1)) * (b.h / 2 + 4)
  const ey = Math.abs(dx) * b.h > Math.abs(dy) * b.w
    ? by - (dy / (Math.abs(dx) || 1)) * (b.w / 2 + 4)
    : by - Math.sign(dy) * (b.h / 2 + 4)

  const mx = (sx + ex) / 2
  const my = (sy + ey) / 2

  if (e.curved) {
    const len = Math.hypot(ex - sx, ey - sy) || 1
    const nx = -(ey - sy) / len
    const ny = (ex - sx) / len
    const cx = mx + nx * e.curved
    const cy = my + ny * e.curved
    return {
      d: `M ${sx.toFixed(1)} ${sy.toFixed(1)} Q ${cx.toFixed(1)} ${cy.toFixed(1)} ${ex.toFixed(1)} ${ey.toFixed(1)}`,
      lx: e.lx ?? (mx + nx * e.curved * 0.55),
      ly: e.ly ?? (my + ny * e.curved * 0.55 - 5),
    }
  }

  return {
    d: `M ${sx.toFixed(1)} ${sy.toFixed(1)} L ${ex.toFixed(1)} ${ey.toFixed(1)}`,
    lx: e.lx ?? mx,
    ly: e.ly ?? (my - 7),
  }
}

function metricTone(tone: Tone = 'slate') {
  const map: Record<Tone, string> = {
    slate: 'border-[var(--border-default)] bg-[var(--bg-surface)] text-[var(--text-primary)]',
    brand: 'border-[#EFC9BF] bg-[#FBF0ED] text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]',
    emerald: 'border-[#B8DCD1] bg-[#EBF4F1] text-[#235749] dark:border-[#244E41] dark:bg-[#162923] dark:text-[#86CBB7]',
    amber: 'border-amber-300/80 bg-amber-50 text-amber-900 dark:border-amber-700/50 dark:bg-amber-950/40 dark:text-amber-200',
    red: 'border-red-300/80 bg-red-50 text-red-800 dark:border-red-800/50 dark:bg-red-950/40 dark:text-red-200',
    indigo: 'border-slate-300 bg-slate-100 text-slate-800 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200',
    purple: 'border-stone-300 bg-stone-100 text-stone-800 dark:border-stone-700 dark:bg-stone-800 dark:text-stone-200',
    cyan: 'border-teal-300/80 bg-teal-50 text-teal-900 dark:border-teal-800/50 dark:bg-teal-950/40 dark:text-teal-200',
  }
  return map[tone]
}

const uid = `fl-${Math.random().toString(36).slice(2, 8)}`
</script>

<template>
  <div class="space-y-3.5">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div
        v-if="spec.scenarios.length > 1"
        class="inline-flex flex-wrap rounded-lg border border-[var(--border-default)] bg-[var(--bg-subtle)] p-0.5 text-xs"
      >
        <button
          v-for="(sc, i) in spec.scenarios"
          :key="sc.id"
          type="button"
          class="rounded-md px-2.5 py-1 font-medium transition-colors"
          :class="i === scenarioIdx ? 'bg-[var(--bg-surface)] text-[#C84B31] dark:text-[#E06246]' : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'"
          @click="selectScenario(i)"
        >
          {{ sc.label }}
        </button>
      </div>
      <span v-else class="text-xs font-medium text-[var(--text-muted)]">分步原理演练</span>

      <span class="rounded border border-[#EFC9BF] bg-[#FBF0ED] px-2 py-0.5 font-mono text-[11px] font-semibold tabular-nums text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]">
        {{ step.badge }} · {{ stepIdx + 1 }}/{{ scenario.steps.length }}
      </span>
    </div>

    <div class="overflow-x-auto rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] p-2.5">
      <svg :viewBox="spec.viewBox ?? '0 0 760 280'" class="mx-auto w-full min-w-[560px] max-w-[760px] select-none">
        <defs>
          <marker :id="`${uid}-arr`" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="currentColor" />
          </marker>
        </defs>

        <g v-for="z in scenario.zones ?? []" :key="z.id">
          <rect
            :x="z.x"
            :y="z.y"
            :width="z.w"
            :height="z.h"
            rx="12"
            stroke-width="1.2"
            stroke-dasharray="5 3"
            :class="zoneClass(z.tone)"
          />
          <text :x="z.x + 12" :y="z.y + 18" class="fill-[#5E5A52] text-[10.5px] font-semibold dark:fill-[#A8A397]">
            {{ z.label }}
          </text>
        </g>

        <g v-for="e in scenario.edges" :key="e.id">
          <path
            :d="edgeGeom(e).d"
            fill="none"
            :stroke-width="step.activeEdges?.includes(e.id) ? 2.5 : 1.6"
            :marker-end="`url(#${uid}-arr)`"
            class="transition-[stroke,opacity] duration-300"
            :class="
              step.activeEdges?.includes(e.id)
                ? 'text-brand-500 stroke-brand-500 [stroke-dasharray:6_4]'
                : 'text-[#C8C3B8] stroke-[#C8C3B8] dark:text-[#3B3933] dark:stroke-[#3B3933]'
            "
          />
          <text
            v-if="e.label"
            :x="edgeGeom(e).lx"
            :y="edgeGeom(e).ly"
            text-anchor="middle"
            class="text-[10px] font-medium transition-colors"
            :class="step.activeEdges?.includes(e.id) ? 'fill-[#96311D] font-semibold dark:fill-[#EFC0B3]' : 'fill-[#736E64] dark:fill-[#8F8A7E]'"
          >
            {{ e.label }}
          </text>
        </g>

        <g v-for="n in scenario.nodes" :key="n.id" :transform="`translate(${n.x}, ${n.y})`">
          <rect
            :width="n.w"
            :height="n.h"
            rx="10"
            stroke-width="1.6"
            class="transition-[fill,stroke,opacity] duration-300"
            :class="nodeBoxClass(n)"
          />
          <text
            :x="n.w / 2"
            :y="(step.nodeText?.[n.id]?.sub ?? n.sub) ? n.h / 2 - 4 : n.h / 2 + 4"
            text-anchor="middle"
            class="text-[11.5px] font-semibold transition-colors"
            :class="isSolidNode(n.id) ? 'fill-white' : 'fill-[#161614] dark:fill-[#ECEAE4]'"
          >
            {{ step.nodeText?.[n.id]?.label ?? n.label }}
          </text>
          <text
            v-if="step.nodeText?.[n.id]?.sub ?? n.sub"
            :x="n.w / 2"
            :y="n.h / 2 + 12"
            text-anchor="middle"
            class="text-[9.5px] transition-colors"
            :class="isSolidNode(n.id) ? 'fill-white/90' : 'fill-[#5E5A52] dark:fill-[#A8A397]'"
          >
            {{ step.nodeText?.[n.id]?.sub ?? n.sub }}
          </text>
        </g>
      </svg>
    </div>

    <div v-if="step.metrics?.length" class="flex flex-wrap gap-2">
      <div
        v-for="m in step.metrics"
        :key="m.label"
        class="inline-flex items-center gap-1.5 rounded border px-2.5 py-1 text-xs font-medium"
        :class="metricTone(m.tone)"
      >
        <span class="opacity-80">{{ m.label }}:</span>
        <span class="font-mono font-semibold tabular-nums">{{ m.value }}</span>
      </div>
    </div>

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
          v-for="(_, i) in scenario.steps"
          :key="i"
          type="button"
          class="h-2 rounded-full transition-[width,background-color] duration-200"
          :class="i === stepIdx ? 'w-6 bg-[#C84B31] dark:bg-[#E06246]' : 'w-2 bg-[var(--border-strong)] hover:bg-[var(--text-muted)]'"
          :title="`第 ${i + 1} 步`"
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
        <button type="button" class="btn-ghost h-8 px-2.5 text-xs" :disabled="stepIdx >= scenario.steps.length - 1" @click="stopPlay(); stepIdx++">
          下一步 <ChevronRight class="h-3.5 w-3.5" />
        </button>
        <button type="button" class="btn-ghost h-8 px-2 text-xs" title="重置" aria-label="重置动画到第一步" @click="stopPlay(); stepIdx = 0">
          <RotateCcw class="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  </div>
</template>
