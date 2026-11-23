<script setup lang="ts">
import { computed } from 'vue'
import { getAnimationMeta } from './registry'

const props = defineProps<{
  animationKey?: string | null
}>()

const meta = computed(() => getAnimationMeta(props.animationKey))
</script>

<template>
  <div
    v-if="meta"
    class="overflow-hidden rounded-2xl border border-brand-200/80 bg-gradient-to-b from-brand-50/40 to-white dark:border-brand-500/25 dark:from-brand-500/10 dark:to-slate-900"
  >
    <div class="flex flex-wrap items-center justify-between gap-2 border-b border-brand-100 px-4 py-3 sm:px-5 dark:border-brand-500/20">
      <div>
        <div class="flex items-center gap-2">
          <span class="inline-flex items-center rounded-md bg-brand-600 px-1.5 py-0.5 text-[10px] font-semibold tracking-wide text-white">
            交互动画
          </span>
          <h4 class="text-sm font-semibold text-slate-900 dark:text-white">{{ meta.title }}</h4>
        </div>
        <p class="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{{ meta.subtitle }}</p>
      </div>
    </div>

    <div class="p-4 sm:p-5">
      <component :is="meta.component" />
    </div>
  </div>
</template>
