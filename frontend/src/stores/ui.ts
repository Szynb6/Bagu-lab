import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface LightboxImage {
  url: string
  caption: string
}

/** 全局 UI 状态：主题、Ctrl+K 搜索、图片灯箱、侧边栏 */
export const useUiStore = defineStore('ui', () => {
  const dark = ref(document.documentElement.classList.contains('dark'))
  const searchOpen = ref(false)
  const sidebarCollapsed = ref(localStorage.getItem('bagu:sidebar') === '1')
  const lightbox = ref<{ images: LightboxImage[]; index: number } | null>(null)
  const toast = ref<{ text: string; id: number } | null>(null)

  function toggleTheme() {
    dark.value = !dark.value
    document.documentElement.classList.toggle('dark', dark.value)
    localStorage.setItem('bagu:theme', dark.value ? 'dark' : 'light')
  }

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem('bagu:sidebar', sidebarCollapsed.value ? '1' : '0')
  }

  function openLightbox(images: LightboxImage[], index = 0) {
    if (images.length) lightbox.value = { images, index }
  }

  function closeLightbox() {
    lightbox.value = null
  }

  let timer: number | undefined
  function showToast(text: string) {
    toast.value = { text, id: Date.now() }
    clearTimeout(timer)
    timer = window.setTimeout(() => (toast.value = null), 2200)
  }

  return { dark, searchOpen, sidebarCollapsed, lightbox, toast, toggleTheme, toggleSidebar, openLightbox, closeLightbox, showToast }
})
