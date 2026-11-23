<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, Pause, Play, RotateCcw, Sparkles } from 'lucide-vue-next'
import type { SequenceSpec, Tone } from '../types'

const props = defineProps<{ spec: SequenceSpec }>()

const scenarioIdx = ref(0)
const stepIdx = ref(0)
const playing = ref(false)
let timer: number | undefined

const scenario = computed(() => props.spec.scenarios[scenarioIdx.value] ?? props.spec.scenarios[0]!)
const step = computed(() => scenario.value.steps[stepIdx.value] ?? scenario.value.steps[0]!)

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

const W = 760
const actorCount = computed(() => scenario.value.actors.length)
const actorX = computed(() => {
  const n = actorCount.value
  const pad = n <= 2 ? 170 : n === 3 ? 115 : 85
  const span = n > 1 ? (W - pad * 2) / (n - 1) : 0
  return Object.fromEntries(scenario.value.actors.map((a, i) => [a.id, pad + i * span]))
})

const visibleMessages = computed(() =>
  scenario.value.steps
    .slice(0, stepIdx.value + 1)
    .map((s, idx) => ({ idx, msg: s.message, callout: s.callout }))
    .filter((item) => item.msg || item.callout),
)

const svgHeight = computed(() => Math.max(240, 108 + visibleMessages.value.length * 42 + 24))

const latestStates = computed(() => {
  const acc: Record<string, { text: string; tone?: Tone }> = {}
  for (let i = 0; i <= stepIdx.value; i++) {
    const st = scenario.value.steps[i]?.actorStates
    if (st) Object.assign(acc, st)
  }
  return acc
})

function strokeColor(tone: Tone = 'brand') {
  const m: Record<Tone, string> = {
    slate: 'text-slate-500 stroke-slate-500',
    brand: 'text-brand-600 stroke-brand-600 dark:text-brand-400 dark:stroke-brand-400',
    emerald: 'text-emerald-600 stroke-emerald-600 dark:text-emerald-400 dark:stroke-emerald-400',
    amber: 'text-amber-600 stroke-amber-600 dark:text-amber-400 dark:stroke-amber-400',
    red: 'text-red-600 stroke-red-600 dark:text-red-400 dark:stroke-red-400',
    indigo: 'text-indigo-600 stroke-indigo-600 dark:text-indigo-400 dark:stroke-indigo-400',
    purple: 'text-purple-600 stroke-purple-600 dark:text-purple-400 dark:stroke-purple-400',
    cyan: 'text-cyan-600 stroke-cyan-600 dark:text-cyan-400 dark:stroke-cyan-400',
  }
  return m[tone]
}

function pillFill(tone: Tone = 'slate') {
  const m: Record<Tone, string> = {
    slate: 'fill-slate-100 stroke-slate-300 dark:fill-slate-800 dark:stroke-slate-600',
    brand: 'fill-brand-50 stroke-brand-300 dark:fill-brand-500/20 dark:stroke-brand-500/50',
    emerald: 'fill-emerald-50 stroke-emerald-300 dark:fill-emerald-500/20 dark:stroke-emerald-500/50',
    amber: 'fill-amber-50 stroke-amber-300 dark:fill-amber-500/20 dark:stroke-amber-500/50',
    red: 'fill-red-50 stroke-red-400 dark:fill-red-500/20 dark:stroke-red-500/50',
    indigo: 'fill-indigo-50 stroke-indigo-300 dark:fill-indigo-500/20 dark:stroke-indigo-500/50',
    purple: 'fill-purple-50 stroke-purple-300 dark:fill-purple-500/20 dark:stroke-purple-500/50',
    cyan: 'fill-cyan-50 stroke-cyan-300 dark:fill-cyan-500/20 dark:stroke-cyan-500/50',
  }
  return m[tone]
}

const uid = `sq-${Math.random().toString(36).slice(2, 8)}`
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
      <span v-else class="text-xs font-medium text-slate-400">时序交互演练</span>

      <span class="rounded-full bg-brand-100/80 px-2.5 py-0.5 font-mono text-[11px] font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-300">
        {{ step.badge }} · {{ stepIdx + 1 }}/{{ scenario.steps.length }}
      </span>
    </div>

    <div class="overflow-x-auto rounded-xl border border-slate-200/80 bg-white p-2.5 dark:border-slate-800 dark:bg-slate-950/70">
      <svg :viewBox="`0 0 ${W} ${svgHeight}`" class="mx-auto w-full min-w-[540px] max-w-[760px] select-none">
        <defs>
          <marker :id="`${uid}-arr`" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="currentColor" />
          </marker>
        </defs>

        <g v-for="a in scenario.actors" :key="a.id">
          <line
            :x1="actorX[a.id]"
            y1="62"
            :x2="actorX[a.id]"
            :y2="svgHeight - 14"
            stroke-width="1.5"
            stroke-dasharray="4 4"
            class="stroke-slate-200 dark:stroke-slate-700"
          />
          <rect
            :x="(actorX[a.id] ?? 0) - 62"
            y="10"
            width="124"
            height="44"
            rx="11"
            stroke-width="1.6"
            :class="pillFill(a.tone ?? 'slate')"
          />
          <text :x="actorX[a.id]" :y="a.sub ? 29 : 36" text-anchor="middle" class="fill-slate-800 text-[11.5px] font-semibold dark:fill-slate-100">
            {{ a.label }}
          </text>
          <text v-if="a.sub" :x="actorX[a.id]" y="44" text-anchor="middle" class="fill-slate-400 text-[9.5px]">
            {{ a.sub }}
          </text>

          <g v-if="latestStates[a.id]" :transform="`translate(${(actorX[a.id] ?? 0) - 54}, 60)`">
            <rect width="108" height="22" rx="11" stroke-width="1.2" :class="pillFill(latestStates[a.id]!.tone ?? 'brand')" />
            <text x="54" y="14.5" text-anchor="middle" class="fill-slate-700 font-mono text-[9.5px] font-semibold dark:fill-slate-100">
              {{ latestStates[a.id]!.text }}
            </text>
          </g>
        </g>

        <g
          v-for="(item, rowIdx) in visibleMessages"
          :key="item.idx"
          :opacity="item.idx === stepIdx ? 1 : 0.45"
          class="transition-opacity duration-300"
        >
          <template v-if="item.msg">
            <template v-if="item.msg.from === item.msg.to">
              <path
                :d="`M ${actorX[item.msg.from]} ${108 + rowIdx * 42} C ${(actorX[item.msg.from] ?? 0) + 52} ${102 + rowIdx * 42}, ${(actorX[item.msg.from] ?? 0) + 52} ${128 + rowIdx * 42}, ${(actorX[item.msg.from] ?? 0) + 4} ${124 + rowIdx * 42}`"
                fill="none"
                stroke-width="2.2"
                :marker-end="`url(#${uid}-arr)`"
                :class="strokeColor(item.msg.tone)"
              />
              <text
                :x="(actorX[item.msg.from] ?? 0) + 48"
                :y="119 + rowIdx * 42"
                class="fill-slate-700 text-[10.5px] font-semibold dark:fill-slate-200"
              >
                {{ item.msg.label }}
              </text>
            </template>
            <template v-else>
              <line
                :x1="actorX[item.msg.from]"
                :y1="114 + rowIdx * 42"
                :x2="item.msg.lost
                  ? ((actorX[item.msg.from] ?? 0) + (actorX[item.msg.to] ?? 0)) / 2
                  : (actorX[item.msg.to] ?? 0) - Math.sign((actorX[item.msg.to] ?? 0) - (actorX[item.msg.from] ?? 0)) * 5"
                :y2="114 + rowIdx * 42"
                :stroke-width="item.idx === stepIdx ? 2.6 : 1.8"
                :stroke-dasharray="item.msg.dashed || item.msg.lost ? '6 4' : undefined"
                :marker-end="item.msg.lost ? undefined : `url(#${uid}-arr)`"
                :class="strokeColor(item.msg.lost ? 'red' : item.msg.tone)"
              />
              <text
                v-if="item.msg.lost"
                :x="((actorX[item.msg.from] ?? 0) + (actorX[item.msg.to] ?? 0)) / 2 + 8"
                :y="118 + rowIdx * 42"
                class="fill-red-600 text-[12px] font-bold"
              >
                ✕
              </text>
              <text
                :x="((actorX[item.msg.from] ?? 0) + (actorX[item.msg.to] ?? 0)) / 2"
                :y="106 + rowIdx * 42"
                text-anchor="middle"
                class="text-[10.5px] font-semibold"
                :class="item.msg.lost ? 'fill-red-600 dark:fill-red-400' : 'fill-slate-700 dark:fill-slate-200'"
              >
                {{ item.msg.label }}
              </text>
            </template>
          </template>

          <g
            v-if="item.callout"
            :transform="`translate(${(actorX[item.callout.actor] ?? 100) - 72}, ${100 + rowIdx * 42})`"
          >
            <rect width="144" height="24" rx="8" stroke-width="1.3" :class="pillFill(item.callout.tone ?? 'amber')" />
            <text x="72" y="15.5" text-anchor="middle" class="fill-slate-800 text-[10px] font-semibold dark:fill-slate-100">
              {{ item.callout.text }}
            </text>
          </g>
        </g>
      </svg>
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
