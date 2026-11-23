<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ data: { date: string; count: number }[] }>()

/** 按周分列（GitHub 风格），模拟 Redis Bitmap 打卡数据 */
const weeks = computed(() => {
  const cells = [...props.data]
  if (!cells.length) return []
  const first = new Date(cells[0].date)
  const pad = (first.getDay() + 6) % 7 // 周一为第一行
  const padded: ({ date: string; count: number } | null)[] = [...Array(pad).fill(null), ...cells]
  const cols: (typeof padded)[] = []
  for (let i = 0; i < padded.length; i += 7) cols.push(padded.slice(i, i + 7))
  return cols
})

function level(c: number) {
  if (c <= 0) return 'bg-slate-100 dark:bg-slate-800'
  if (c < 10) return 'bg-emerald-200 dark:bg-emerald-900'
  if (c < 20) return 'bg-emerald-400 dark:bg-emerald-700'
  if (c < 30) return 'bg-emerald-500 dark:bg-emerald-500'
  return 'bg-emerald-600 dark:bg-emerald-400'
}
</script>

<template>
  <div class="overflow-x-auto">
    <div class="flex gap-[3px]">
      <div v-for="(col, i) in weeks" :key="i" class="flex flex-col gap-[3px]">
        <div
          v-for="(cell, j) in col"
          :key="j"
          class="h-3 w-3 rounded-[3px] sm:h-3.5 sm:w-3.5"
          :class="cell ? level(cell.count) : 'bg-transparent'"
          :title="cell ? `${cell.date}：${cell.count} 题` : ''"
        />
      </div>
    </div>
    <div class="mt-3 flex items-center justify-end gap-1.5 text-[11px] text-slate-400">
      少
      <span v-for="c in [0, 5, 15, 25, 35]" :key="c" class="h-2.5 w-2.5 rounded-[3px]" :class="level(c)" />
      多
    </div>
  </div>
</template>
