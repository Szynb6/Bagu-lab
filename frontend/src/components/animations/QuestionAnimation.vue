<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import { getAnimationEntry } from './registry'
import FlowDiagramEngine from './engines/FlowDiagramEngine.vue'
import SequenceDiagramEngine from './engines/SequenceDiagramEngine.vue'
import StateMachineEngine from './engines/StateMachineEngine.vue'
import type { DeclarativeSpec } from './types'

const props = defineProps<{
  animationKey?: string | null
}>()

const entry = computed(() => getAnimationEntry(props.animationKey))
const spec = ref<DeclarativeSpec | null>(null)
const loading = ref(false)

watch(
  entry,
  async (next) => {
    spec.value = null
    if (!next?.loadSpec) return
    loading.value = true
    try {
      spec.value = await next.loadSpec()
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)
</script>

<template>
  <div
    v-if="entry"
    class="overflow-hidden rounded-2xl border border-brand-200/80 bg-gradient-to-b from-brand-50/40 to-white dark:border-brand-500/25 dark:from-brand-500/10 dark:to-slate-900"
  >
    <div class="flex flex-wrap items-center justify-between gap-2 border-b border-brand-100 px-4 py-3 sm:px-5 dark:border-brand-500/20">
      <div>
        <div class="flex items-center gap-2">
          <span class="inline-flex items-center rounded-md bg-brand-600 px-1.5 py-0.5 text-[10px] font-semibold tracking-wide text-white">
            交互动画
          </span>
          <h4 class="text-sm font-semibold text-slate-900 dark:text-white">{{ entry.title }}</h4>
        </div>
        <p class="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{{ entry.subtitle }}</p>
      </div>
    </div>

    <div class="p-4 sm:p-5">
      <component :is="entry.component" v-if="entry.component" />
      <div v-else-if="loading" class="flex items-center justify-center gap-2 py-12 text-xs text-slate-400">
        <Loader2 class="h-4 w-4 animate-spin" /> 加载交互动画中...
      </div>
      <template v-else-if="spec">
        <FlowDiagramEngine v-if="spec.kind === 'flow'" :spec="spec" />
        <SequenceDiagramEngine v-else-if="spec.kind === 'sequence'" :spec="spec" />
        <StateMachineEngine v-else-if="spec.kind === 'state'" :spec="spec" />
      </template>
    </div>
  </div>
</template>
