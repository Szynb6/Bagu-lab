<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{ data: { date: string; count: number }[] }>()

interface HeatmapCell {
  date: string
  count: number
}

const selectedCell = ref<HeatmapCell | null>(null)

/** 按周分列（周一为首行），附带月份标签 */
const weeks = computed(() => {
  const cells = [...props.data]
  if (!cells.length) return []
  const first = new Date(cells[0].date)
  const pad = (first.getDay() + 6) % 7
  const padded: (HeatmapCell | null)[] = [...Array(pad).fill(null), ...cells]
  const cols: (HeatmapCell | null)[][] = []
  for (let i = 0; i < padded.length; i += 7) {
    cols.push(padded.slice(i, i + 7))
  }
  return cols
})

const monthLabels = computed(() => {
  let prevMonth = ''
  return weeks.value.map((col) => {
    const firstValid = col.find((c): c is HeatmapCell => c !== null)
    if (!firstValid) return ''
    const month = `${Number(firstValid.date.slice(5, 7))}月`
    if (month !== prevMonth) {
      prevMonth = month
      return month
    }
    return ''
  })
})

const summaryLabel = computed(() => {
  const activeDays = props.data.filter((d) => d.count > 0).length
  const totalCount = props.data.reduce((sum, d) => sum + d.count, 0)
  return `近 ${props.data.length} 天累计复习 ${totalCount} 题，活跃 ${activeDays} 天`
})

function level(c: number) {
  if (c <= 0) return 'bg-[var(--heat-0)]'
  if (c < 10) return 'bg-[var(--heat-1)]'
  if (c < 20) return 'bg-[var(--heat-2)]'
  if (c < 30) return 'bg-[var(--heat-3)]'
  return 'bg-[var(--heat-4)]'
}
</script>

<template>
  <div role="img" :aria-label="summaryLabel">
    <div class="overflow-x-auto pb-1">
      <div class="inline-flex flex-col gap-1">
        <!-- 月份刻度 -->
        <div class="flex gap-[3px] pl-6 text-[10px] font-mono text-[var(--text-muted)]">
          <div
            v-for="(label, idx) in monthLabels"
            :key="idx"
            class="w-3 shrink-0 whitespace-nowrap sm:w-3.5"
          >
            {{ label }}
          </div>
        </div>

        <div class="flex gap-1.5">
          <!-- 星期刻度 -->
          <div class="flex w-4 shrink-0 flex-col justify-between py-0.5 text-[10px] text-[var(--text-muted)]">
            <span>一</span>
            <span>三</span>
            <span>五</span>
          </div>

          <!-- 热力格子 -->
          <div class="flex gap-[3px]">
            <div v-for="(col, i) in weeks" :key="i" class="flex flex-col gap-[3px]">
              <template v-for="(cell, j) in col" :key="j">
                <button
                  v-if="cell"
                  type="button"
                  class="h-3 w-3 rounded-[2px] transition-opacity hover:opacity-80 focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand-500 sm:h-3.5 sm:w-3.5"
                  :class="[
                    level(cell.count),
                    selectedCell?.date === cell.date ? 'ring-1 ring-[var(--text-primary)] ring-offset-1 ring-offset-[var(--bg-surface)]' : '',
                  ]"
                  :title="`${cell.date}：${cell.count} 题`"
                  :aria-label="`${cell.date} 复习 ${cell.count} 题`"
                  @mouseenter="selectedCell = cell"
                  @click="selectedCell = cell"
                />
                <span v-else class="h-3 w-3 sm:h-3.5 sm:w-3.5" />
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部交互读数 + 图例 -->
    <div class="mt-3 flex flex-wrap items-center justify-between gap-2 text-[11px] text-[var(--text-muted)]">
      <span class="font-mono tabular-nums">
        <template v-if="selectedCell">
          {{ selectedCell.date }} · 复习 <strong class="text-[var(--text-primary)]">{{ selectedCell.count }}</strong> 题
        </template>
        <template v-else>{{ summaryLabel }}</template>
      </span>
      <div class="flex items-center gap-1.5">
        <span>少</span>
        <span v-for="c in [0, 5, 15, 25, 35]" :key="c" class="h-2.5 w-2.5 rounded-[2px]" :class="level(c)" />
        <span>多</span>
      </div>
    </div>
  </div>
</template>
