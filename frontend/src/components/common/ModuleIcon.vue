<script setup lang="ts">
import { computed } from 'vue'
import { Coffee, Cpu, Database, GitBranch, Globe, Leaf, Monitor, Shapes, Zap, BookOpen } from 'lucide-vue-next'

const props = defineProps<{ icon: string; slug?: string }>()

const icons = { cpu: Cpu, 'git-branch': GitBranch, coffee: Coffee, leaf: Leaf, database: Database, zap: Zap, shapes: Shapes, globe: Globe, monitor: Monitor }
const tones: Record<string, string> = {
  cpu: 'from-violet-500 to-indigo-500',
  'git-branch': 'from-sky-500 to-blue-600',
  coffee: 'from-amber-500 to-orange-500',
  leaf: 'from-emerald-500 to-green-600',
  database: 'from-cyan-500 to-teal-600',
  zap: 'from-rose-500 to-red-500',
  shapes: 'from-fuchsia-500 to-pink-500',
  globe: 'from-blue-500 to-indigo-600',
  monitor: 'from-slate-500 to-slate-700',
}
const comp = computed(() => icons[props.icon as keyof typeof icons] ?? BookOpen)
const tone = computed(() => tones[props.icon] ?? 'from-slate-400 to-slate-600')
</script>

<template>
  <span class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br text-white shadow-sm" :class="tone">
    <component :is="comp" class="h-[18px] w-[18px]" :stroke-width="2.2" />
  </span>
</template>
