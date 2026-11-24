<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'
import { useUiStore } from '@/stores/ui'

const props = defineProps<{ source: string }>()
const ui = useUiStore()
const html = computed(() => renderMarkdown(props.source))

const copyTimers = new WeakMap<HTMLElement, number>()

function fallbackCopyText(text: string): boolean {
  const selection = window.getSelection()
  const prevRange = selection && selection.rangeCount > 0 ? selection.getRangeAt(0) : null
  const prevActive = document.activeElement instanceof HTMLElement ? document.activeElement : null

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.top = '0'
  textarea.style.left = '-9999px'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)

  let ok = false
  try {
    textarea.focus({ preventScroll: true })
    textarea.select()
    textarea.setSelectionRange(0, text.length)
    ok = document.execCommand('copy')
  } catch {
    ok = false
  } finally {
    document.body.removeChild(textarea)
    if (selection) {
      selection.removeAllRanges()
      if (prevRange) selection.addRange(prevRange)
    }
    prevActive?.focus({ preventScroll: true })
  }
  return ok
}

async function copyText(text: string): Promise<boolean> {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // 安全上下文权限受限或拒绝时回退至 execCommand
    }
  }
  return fallbackCopyText(text)
}

function openImage(el: HTMLImageElement, root: HTMLElement) {
  const imgs = Array.from(root.querySelectorAll<HTMLImageElement>('img[data-zoom]'))
  ui.openLightbox(
    imgs.map((i) => ({ url: i.getAttribute('src') ?? '', caption: i.alt })),
    imgs.indexOf(el),
  )
}

async function onClick(e: MouseEvent) {
  const el = e.target as HTMLElement | null
  const root = e.currentTarget as HTMLElement
  if (!el) return

  if (el.tagName === 'IMG' && el.hasAttribute('data-zoom')) {
    openImage(el as HTMLImageElement, root)
    return
  }

  const copyBtn = el.closest<HTMLButtonElement>('[data-copy]')
  if (copyBtn) {
    const code = (copyBtn.closest('.code-block')?.querySelector('code')?.textContent ?? '').replace(/\n$/, '')
    const ok = await copyText(code)

    const prevTimer = copyTimers.get(copyBtn)
    if (prevTimer !== undefined) {
      window.clearTimeout(prevTimer)
    }

    if (ok) {
      copyBtn.textContent = '已复制'
      copyBtn.classList.add('is-copied')
    } else {
      copyBtn.textContent = '复制失败'
      copyBtn.classList.remove('is-copied')
    }

    const timer = window.setTimeout(() => {
      copyBtn.textContent = '复制'
      copyBtn.classList.remove('is-copied')
      copyTimers.delete(copyBtn)
    }, 1600)
    copyTimers.set(copyBtn, timer)
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Enter' && e.key !== ' ') return
  const el = e.target as HTMLElement
  if (el.tagName === 'IMG' && el.hasAttribute('data-zoom')) {
    e.preventDefault()
    openImage(el as HTMLImageElement, e.currentTarget as HTMLElement)
  }
}
</script>

<template>
  <div class="md" v-html="html" @click="onClick" @keydown="onKeydown" />
</template>
