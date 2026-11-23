<script setup lang="ts">
import type { Priority } from '@/types'

withDefaults(defineProps<{ priority: Priority; size?: 'sm' | 'md' }>(), { size: 'sm' })

const label: Record<Priority, string> = { P0: 'P0 必背', P1: 'P1 高频', P2: 'P2 了解' }
const cls: Record<Priority, string> = {
  P0: 'bg-red-50 text-red-600 ring-red-200 dark:bg-red-500/15 dark:text-red-300 dark:ring-red-500/30',
  P1: 'bg-blue-50 text-blue-600 ring-blue-200 dark:bg-blue-500/15 dark:text-blue-300 dark:ring-blue-500/30',
  P2: 'bg-slate-100 text-slate-500 ring-slate-200 dark:bg-slate-700/50 dark:text-slate-400 dark:ring-slate-600',
}
</script>

<template>
  <span
    class="inline-flex shrink-0 items-center rounded-full font-medium ring-1 ring-inset"
    :class="[cls[priority], size === 'sm' ? 'px-2 py-0.5 text-[11px]' : 'px-2.5 py-1 text-xs']"
  >
    {{ label[priority] }}
  </span>
</template>
