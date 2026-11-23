<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, Lightbulb, Pause, Play, RotateCcw } from 'lucide-vue-next'
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
    slate: 'text-[#5E5A52] stroke-[#5E5A52] dark:text-[#A8A397] dark:stroke-[#A8A397]',
    brand: 'text-brand-600 stroke-brand-600 dark:text-brand-400 dark:stroke-brand-400',
    emerald: 'text-emerald-700 stroke-emerald-700 dark:text-emerald-400 dark:stroke-emerald-400',
    amber: 'text-amber-700 stroke-amber-700 dark:text-amber-400 dark:stroke-amber-400',
    red: 'text-red-700 stroke-red-700 dark:text-red-400 dark:stroke-red-400',
    indigo: 'text-slate-600 stroke-slate-600 dark:text-slate-300 dark:stroke-slate-300',
    purple: 'text-stone-600 stroke-stone-600 dark:text-stone-300 dark:stroke-stone-300',
    cyan: 'text-teal-700 stroke-teal-700 dark:text-teal-400 dark:stroke-teal-400',
  }
  return m[tone]
}

function pillFill(tone: Tone = 'slate') {
  const m: Record<Tone, string> = {
    slate: 'fill-[#F4F2EC] stroke-[#D4D0C5] dark:fill-[#22211E] dark:stroke-[#3B3933]',
    brand: 'fill-brand-50 stroke-brand-300 dark:fill-brand-950/60 dark:stroke-brand-700',
    emerald: 'fill-emerald-50 stroke-emerald-300 dark:fill-emerald-950/50 dark:stroke-emerald-700',
    amber: 'fill-amber-50 stroke-amber-300 dark:fill-amber-950/50 dark:stroke-amber-700',
    red: 'fill-red-50 stroke-red-400 dark:fill-red-950/50 dark:stroke-red-700',
    indigo: 'fill-slate-100 stroke-slate-300 dark:fill-slate-800 dark:stroke-slate-600',
    purple: 'fill-stone-100 stroke-stone-300 dark:fill-stone-800 dark:stroke-stone-600',
    cyan: 'fill-teal-50 stroke-teal-300 dark:fill-teal-950/50 dark:stroke-teal-700',
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
      <span v-else class="text-xs font-medium text-[var(--text-muted)]">时序交互演练</span>

      <span class="rounded border border-[#EFC9BF] bg-[#FBF0ED] px-2 py-0.5 font-mono text-[11px] font-semibold tabular-nums text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]">
        {{ step.badge }} · {{ stepIdx + 1 }}/{{ scenario.steps.length }}
      </span>
    </div>

    <div class="overflow-x-auto rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] p-2.5">
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
            stroke-width="1.4"
            stroke-dasharray="4 4"
            class="stroke-[#D4D0C5] dark:stroke-[#3B3933]"
          />
          <rect
            :x="(actorX[a.id] ?? 0) - 62"
            y="10"
            width="124"
            height="44"
            rx="10"
            stroke-width="1.5"
            :class="pillFill(a.tone ?? 'slate')"
          />
          <text :x="actorX[a.id]" :y="a.sub ? 29 : 36" text-anchor="middle" class="fill-[#161614] text-[11.5px] font-semibold dark:fill-[#ECEAE4]">
            {{ a.label }}
          </text>
          <text v-if="a.sub" :x="actorX[a.id]" y="44" text-anchor="middle" class="fill-[#736E64] text-[9.5px] dark:fill-[#A8A397]">
            {{ a.sub }}
          </text>

          <g v-if="latestStates[a.id]" :transform="`translate(${(actorX[a.id] ?? 0) - 54}, 60)`">
            <rect width="108" height="22" rx="8" stroke-width="1.2" :class="pillFill(latestStates[a.id]!.tone ?? 'brand')" />
            <text x="54" y="14.5" text-anchor="middle" class="fill-[#161614] font-mono text-[9.5px] font-semibold dark:fill-[#ECEAE4]">
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
                class="fill-[#161614] text-[10.5px] font-semibold dark:fill-[#ECEAE4]"
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
                class="fill-red-700 text-[12px] font-bold dark:fill-red-400"
              >
                ✕
              </text>
              <text
                :x="((actorX[item.msg.from] ?? 0) + (actorX[item.msg.to] ?? 0)) / 2"
                :y="106 + rowIdx * 42"
                text-anchor="middle"
                class="text-[10.5px] font-semibold"
                :class="item.msg.lost ? 'fill-red-700 dark:fill-red-400' : 'fill-[#161614] dark:fill-[#ECEAE4]'"
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
            <text x="72" y="15.5" text-anchor="middle" class="fill-[#161614] text-[10px] font-semibold dark:fill-[#ECEAE4]">
              {{ item.callout.text }}
            </text>
          </g>
        </g>
      </svg>
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
