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
    class="overflow-hidden rounded-xl border border-[var(--border-default)] bg-[var(--bg-subtle)]/55"
  >
    <div class="flex flex-wrap items-center justify-between gap-2 border-b border-[var(--border-default)] bg-[var(--bg-subtle)] px-4 py-3 sm:px-5">
      <div>
        <div class="flex items-center gap-2">
          <span class="inline-flex items-center rounded border border-[#EFC9BF] bg-[#FBF0ED] px-1.5 py-0.5 font-mono text-[10px] font-semibold tracking-tight text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]">
            交互推演
          </span>
          <h4 class="text-sm font-semibold text-[var(--text-primary)]">{{ entry.title }}</h4>
        </div>
        <p class="mt-0.5 text-xs text-[var(--text-secondary)]">{{ entry.subtitle }}</p>
      </div>
    </div>

    <div class="p-4 sm:p-5">
      <component :is="entry.component" v-if="entry.component" />
      <div v-else-if="loading" class="flex items-center justify-center gap-2 py-12 text-xs text-[var(--text-muted)]">
        <Loader2 class="h-4 w-4 animate-spin text-[#C84B31] dark:text-[#E06246]" /> 加载交互原理推演中...
      </div>
      <template v-else-if="spec">
        <FlowDiagramEngine v-if="spec.kind === 'flow'" :spec="spec" />
        <SequenceDiagramEngine v-else-if="spec.kind === 'sequence'" :spec="spec" />
        <StateMachineEngine v-else-if="spec.kind === 'state'" :spec="spec" />
      </template>
    </div>
  </div>
</template>
