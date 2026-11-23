<script setup lang="ts">
import type { Priority } from '@/types'

withDefaults(defineProps<{ priority: Priority; size?: 'sm' | 'md' }>(), { size: 'sm' })

const label: Record<Priority, string> = { P0: 'P0 必背', P1: 'P1 高频', P2: 'P2 了解' }
const cls: Record<Priority, string> = {
  P0: 'border-[#EFC9BF] bg-[#FBF0ED] text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]',
  P1: 'border-amber-300/80 bg-amber-50 text-amber-900 dark:border-amber-700/50 dark:bg-amber-950/50 dark:text-amber-200',
  P2: 'border-[var(--border-default)] bg-[var(--bg-subtle)] text-[var(--text-secondary)]',
}
</script>

<template>
  <span
    class="inline-flex shrink-0 items-center rounded border font-mono font-medium tracking-tight"
    :class="[cls[priority], size === 'sm' ? 'px-1.5 py-0.5 text-[11px] leading-4' : 'px-2 py-0.5 text-xs']"
  >
    {{ label[priority] }}
  </span>
</template>
