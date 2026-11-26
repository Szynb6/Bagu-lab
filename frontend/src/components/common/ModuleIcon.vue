<script setup lang="ts">
import { computed } from 'vue'
import { BookOpen, Coffee, Cpu, Database, GitBranch, Globe, Leaf, Monitor, Shapes, Sparkles, Zap } from 'lucide-vue-next'

const props = defineProps<{
  icon: string
  slug?: string
  order?: number
  active?: boolean
}>()

const icons = {
  cpu: Cpu,
  'git-branch': GitBranch,
  coffee: Coffee,
  leaf: Leaf,
  database: Database,
  zap: Zap,
  shapes: Shapes,
  globe: Globe,
  monitor: Monitor,
  sparkles: Sparkles,
}

const codeNum = computed(() => {
  if (typeof props.order === 'number' && props.order > 0) {
    return String(props.order).padStart(2, '0')
  }
  const match = props.slug?.match(/^(\d{2})/)
  return match ? match[1] : null
})

const comp = computed(() => icons[props.icon as keyof typeof icons] ?? BookOpen)
</script>

<template>
  <span
    class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border font-mono text-xs font-semibold tracking-tight transition-colors"
    :class="
      active
        ? 'border-[var(--accent-border)] bg-[var(--accent-soft)] text-[var(--accent-fg)]'
        : 'border-[var(--border-default)] bg-[var(--bg-subtle)] text-[var(--text-primary)]'
    "
  >
    <template v-if="codeNum">{{ codeNum }}</template>
    <component :is="comp" v-else class="h-4 w-4" :stroke-width="1.9" />
  </span>
</template>
