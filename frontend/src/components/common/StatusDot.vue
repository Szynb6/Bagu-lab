<script setup lang="ts">
import type { StudyStatus } from '@/types'

defineProps<{ status: StudyStatus }>()

const map: Record<StudyStatus, { text: string; dot: string; textCls: string }> = {
  0: {
    text: '未背',
    dot: 'bg-[#C4BFB4] dark:bg-[#57544B]',
    textCls: 'text-[var(--text-muted)]',
  },
  1: {
    text: '待巩固',
    dot: 'bg-[#C84B31] dark:bg-[#E06246]',
    textCls: 'text-[#96311D] dark:text-[#EFC0B3]',
  },
  2: {
    text: '复习中',
    dot: 'bg-amber-600 dark:bg-amber-400',
    textCls: 'text-amber-800 dark:text-amber-300',
  },
  3: {
    text: '已掌握',
    dot: 'bg-[#2E6F5E] dark:bg-[#4FA58E]',
    textCls: 'text-[#235749] dark:text-[#86CBB7]',
  },
}
</script>

<template>
  <span class="inline-flex items-center gap-1.5 text-xs font-medium" :class="map[status].textCls">
    <span class="h-1.5 w-1.5 rounded-full" :class="map[status].dot" />
    {{ map[status].text }}
  </span>
</template>
