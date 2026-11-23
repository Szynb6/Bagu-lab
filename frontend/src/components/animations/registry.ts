import { defineAsyncComponent, type Component } from 'vue'

export interface AnimationMeta {
  key: string
  title: string
  subtitle: string
  component: Component
}

const registry: Record<string, AnimationMeta> = {
  'thread-pool-flow': {
    key: 'thread-pool-flow',
    title: 'ThreadPoolExecutor 任务提交全流程',
    subtitle: 'corePoolSize(2) → workQueue(2) → maximumPoolSize(4) → 拒绝策略',
    component: defineAsyncComponent(() => import('./ThreadPoolFlow.vue')),
  },
}

export function getAnimationMeta(key?: string | null): AnimationMeta | null {
  if (!key) return null
  return registry[key] ?? null
}
