<script setup lang="ts">
import type { StudyStatus } from '@/types'

defineProps<{ status: StudyStatus }>()

const map: Record<StudyStatus, { text: string; dot: string }> = {
  0: { text: '未背', dot: 'bg-slate-300 dark:bg-slate-600' },
  1: { text: '待巩固', dot: 'bg-amber-500' },
  2: { text: '复习中', dot: 'bg-sky-500' },
  3: { text: '已掌握', dot: 'bg-emerald-500' },
}
</script>

<template>
  <span class="inline-flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
    <span class="h-2 w-2 rounded-full" :class="map[status].dot" />
    {{ map[status].text }}
  </span>
</template>
