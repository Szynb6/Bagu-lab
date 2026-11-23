<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, Pause, Play, RotateCcw, Sparkles } from 'lucide-vue-next'
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
    slate: 'fill-slate-50/80 stroke-slate-200 dark:fill-slate-900/60 dark:stroke-slate-700',
    brand: 'fill-brand-50/50 stroke-brand-200 dark:fill-brand-500/10 dark:stroke-brand-500/30',
    emerald: 'fill-emerald-50/50 stroke-emerald-200 dark:fill-emerald-500/10 dark:stroke-emerald-500/30',
    amber: 'fill-amber-50/50 stroke-amber-200 dark:fill-amber-500/10 dark:stroke-amber-500/30',
    red: 'fill-red-50/50 stroke-red-200 dark:fill-red-500/10 dark:stroke-red-500/30',
    indigo: 'fill-indigo-50/50 stroke-indigo-200 dark:fill-indigo-500/10 dark:stroke-indigo-500/30',
    purple: 'fill-purple-50/50 stroke-purple-200 dark:fill-purple-500/10 dark:stroke-purple-500/30',
    cyan: 'fill-cyan-50/50 stroke-cyan-200 dark:fill-cyan-500/10 dark:stroke-cyan-500/30',
  }
  return map[tone]
}

function nodeBoxClass(n: FlowNode) {
  const s = step.value
  if (s.dangerNodes?.includes(n.id)) return 'fill-red-500 stroke-red-600 dark:fill-red-500 dark:stroke-red-400'
  if (s.warnNodes?.includes(n.id)) return 'fill-amber-500 stroke-amber-600 dark:fill-amber-500 dark:stroke-amber-400'
  if (s.activeNodes?.includes(n.id)) return 'fill-brand-600 stroke-brand-700 dark:fill-brand-500 dark:stroke-brand-300'
  if (s.doneNodes?.includes(n.id)) return 'fill-emerald-500 stroke-emerald-600 dark:fill-emerald-500 dark:stroke-emerald-400'
  if (s.mutedNodes?.includes(n.id)) return 'fill-slate-100/70 stroke-slate-200 opacity-45 dark:fill-slate-900/50 dark:stroke-slate-800'
  const base: Record<Tone, string> = {
    slate: 'fill-white stroke-slate-300 dark:fill-slate-800/90 dark:stroke-slate-600',
    brand: 'fill-brand-50/70 stroke-brand-300 dark:fill-brand-500/15 dark:stroke-brand-500/40',
    emerald: 'fill-emerald-50/70 stroke-emerald-300 dark:fill-emerald-500/15 dark:stroke-emerald-500/40',
    amber: 'fill-amber-50/70 stroke-amber-300 dark:fill-amber-500/15 dark:stroke-amber-500/40',
    red: 'fill-red-50/70 stroke-red-300 dark:fill-red-500/15 dark:stroke-red-500/40',
    indigo: 'fill-indigo-50/70 stroke-indigo-300 dark:fill-indigo-500/15 dark:stroke-indigo-500/40',
    purple: 'fill-purple-50/70 stroke-purple-300 dark:fill-purple-500/15 dark:stroke-purple-500/40',
    cyan: 'fill-cyan-50/70 stroke-cyan-300 dark:fill-cyan-500/15 dark:stroke-cyan-500/40',
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
    slate: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-200',
    brand: 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300',
    emerald: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300',
    amber: 'bg-amber-50 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300',
    red: 'bg-red-50 text-red-700 dark:bg-red-500/15 dark:text-red-300',
    indigo: 'bg-indigo-50 text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300',
    purple: 'bg-purple-50 text-purple-700 dark:bg-purple-500/15 dark:text-purple-300',
    cyan: 'bg-cyan-50 text-cyan-700 dark:bg-cyan-500/15 dark:text-cyan-300',
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
        class="inline-flex flex-wrap rounded-xl border border-slate-200 bg-slate-100/80 p-0.5 text-xs dark:border-slate-700 dark:bg-slate-800"
      >
        <button
          v-for="(sc, i) in spec.scenarios"
          :key="sc.id"
          class="rounded-lg px-2.5 py-1 font-medium transition"
          :class="i === scenarioIdx ? 'bg-white text-brand-700 shadow-xs dark:bg-slate-900 dark:text-brand-300' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'"
          @click="selectScenario(i)"
        >
          {{ sc.label }}
        </button>
      </div>
      <span v-else class="text-xs font-medium text-slate-400">分步原理演练</span>

      <span class="rounded-full bg-brand-100/80 px-2.5 py-0.5 font-mono text-[11px] font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-300">
        {{ step.badge }} · {{ stepIdx + 1 }}/{{ scenario.steps.length }}
      </span>
    </div>

    <div class="overflow-x-auto rounded-xl border border-slate-200/80 bg-white p-2.5 dark:border-slate-800 dark:bg-slate-950/70">
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
            rx="14"
            stroke-width="1.3"
            stroke-dasharray="5 3"
            :class="zoneClass(z.tone)"
          />
          <text :x="z.x + 12" :y="z.y + 18" class="fill-slate-500 text-[10.5px] font-semibold dark:fill-slate-400">
            {{ z.label }}
          </text>
        </g>

        <g v-for="e in scenario.edges" :key="e.id">
          <path
            :d="edgeGeom(e).d"
            fill="none"
            :stroke-width="step.activeEdges?.includes(e.id) ? 2.6 : 1.7"
            :marker-end="`url(#${uid}-arr)`"
            class="transition-all duration-300"
            :class="step.activeEdges?.includes(e.id)
              ? 'text-brand-500 stroke-brand-500 [stroke-dasharray:6_4]'
              : 'text-slate-300 stroke-slate-300 dark:text-slate-700 dark:stroke-slate-700'"
          />
          <text
            v-if="e.label"
            :x="edgeGeom(e).lx"
            :y="edgeGeom(e).ly"
            text-anchor="middle"
            class="text-[10px] font-medium transition-colors"
            :class="step.activeEdges?.includes(e.id) ? 'fill-brand-600 font-semibold dark:fill-brand-300' : 'fill-slate-400 dark:fill-slate-500'"
          >
            {{ e.label }}
          </text>
        </g>

        <g v-for="n in scenario.nodes" :key="n.id" :transform="`translate(${n.x}, ${n.y})`">
          <rect
            :width="n.w"
            :height="n.h"
            rx="11"
            stroke-width="1.7"
            class="transition-all duration-300"
            :class="nodeBoxClass(n)"
          />
          <text
            :x="n.w / 2"
            :y="(step.nodeText?.[n.id]?.sub ?? n.sub) ? n.h / 2 - 4 : n.h / 2 + 4"
            text-anchor="middle"
            class="text-[11.5px] font-semibold transition-colors"
            :class="isSolidNode(n.id) ? 'fill-white' : 'fill-slate-800 dark:fill-slate-100'"
          >
            {{ step.nodeText?.[n.id]?.label ?? n.label }}
          </text>
          <text
            v-if="step.nodeText?.[n.id]?.sub ?? n.sub"
            :x="n.w / 2"
            :y="n.h / 2 + 12"
            text-anchor="middle"
            class="text-[9.5px] transition-colors"
            :class="isSolidNode(n.id) ? 'fill-white/85' : 'fill-slate-400 dark:fill-slate-400'"
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
        class="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-medium"
        :class="metricTone(m.tone)"
      >
        <span class="opacity-75">{{ m.label }}:</span>
        <span class="font-mono font-semibold">{{ m.value }}</span>
      </div>
    </div>

    <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3.5 text-sm dark:border-slate-800 dark:bg-slate-900/80">
      <p class="font-semibold text-slate-900 dark:text-white">{{ step.title }}</p>
      <p class="mt-1 leading-relaxed text-slate-600 dark:text-slate-300">{{ step.speech }}</p>
      <p class="mt-2 flex items-start gap-1.5 rounded-lg bg-amber-50/90 px-2.5 py-1.5 text-xs text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
        <Sparkles class="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" />
        <span>{{ step.tip }}</span>
      </p>
    </div>

    <div class="flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-center gap-1.5">
        <button
          v-for="(_, i) in scenario.steps"
          :key="i"
          class="h-2 rounded-full transition-all"
          :class="i === stepIdx ? 'w-6 bg-brand-600' : 'w-2 bg-slate-300 hover:bg-slate-400 dark:bg-slate-700'"
          :title="`第 ${i + 1} 步`"
          @click="stopPlay(); stepIdx = i"
        />
      </div>

      <div class="flex items-center gap-2">
        <button class="btn-ghost h-8 px-2.5 text-xs" :disabled="stepIdx === 0" @click="stopPlay(); stepIdx--">
          <ChevronLeft class="h-3.5 w-3.5" /> 上一步
        </button>
        <button class="btn-primary h-8 px-3 text-xs" @click="togglePlay">
          <component :is="playing ? Pause : Play" class="h-3.5 w-3.5" />
          {{ playing ? '暂停' : '自动播放' }}
        </button>
        <button class="btn-ghost h-8 px-2.5 text-xs" :disabled="stepIdx >= scenario.steps.length - 1" @click="stopPlay(); stepIdx++">
          下一步 <ChevronRight class="h-3.5 w-3.5" />
        </button>
        <button class="btn-ghost h-8 px-2 text-xs" title="重置" @click="stopPlay(); stepIdx = 0">
          <RotateCcw class="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  </div>
</template>
