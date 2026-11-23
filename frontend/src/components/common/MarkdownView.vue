<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'
import { useUiStore } from '@/stores/ui'

const props = defineProps<{ source: string }>()
const ui = useUiStore()
const html = computed(() => renderMarkdown(props.source))

function onClick(e: MouseEvent) {
  const el = e.target as HTMLElement
  const root = e.currentTarget as HTMLElement
  if (el.tagName === 'IMG' && el.hasAttribute('data-zoom')) {
    const imgs = Array.from(root.querySelectorAll<HTMLImageElement>('img[data-zoom]'))
    ui.openLightbox(
      imgs.map((i) => ({ url: i.getAttribute('src') ?? '', caption: i.alt })),
      imgs.indexOf(el as HTMLImageElement),
    )
    return
  }
  if (el.hasAttribute('data-copy')) {
    const code = el.closest('.code-block')?.querySelector('code')?.textContent ?? ''
    navigator.clipboard?.writeText(code).then(() => {
      el.textContent = '已复制 ✓'
      setTimeout(() => (el.textContent = '复制'), 1500)
    })
  }
}
</script>

<template>
  <div class="md" v-html="html" @click="onClick" />
</template>
