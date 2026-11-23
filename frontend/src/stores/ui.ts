import { defineStore } from 'pinia'
import { ref } from 'vue'
import { knowledgeApi } from '@/api'
import type { ModuleMeta } from '@/types'

export interface LightboxImage {
  url: string
  caption: string
}

/** 全局 UI 状态：主题、最近阅读模块、共享模块元数据缓存、图片灯箱、侧边栏 */
export const useUiStore = defineStore('ui', () => {
  const dark = ref(document.documentElement.classList.contains('dark'))
  const sidebarCollapsed = ref(localStorage.getItem('bagu:sidebar') === '1')
  const lastSlug = ref(localStorage.getItem('bagu:lastSlug') || '01-jvm')
  const lightbox = ref<{ images: LightboxImage[]; index: number } | null>(null)
  const toast = ref<{ text: string; id: number } | null>(null)

  const modules = ref<ModuleMeta[]>([])
  let modulesPromise: Promise<ModuleMeta[]> | null = null

  function syncThemeColorMeta() {
    const meta = document.querySelector('meta[name="theme-color"]')
    if (meta) {
      meta.setAttribute('content', dark.value ? '#111110' : '#F7F6F2')
    }
  }

  function toggleTheme() {
    dark.value = !dark.value
    document.documentElement.classList.toggle('dark', dark.value)
    localStorage.setItem('bagu:theme', dark.value ? 'dark' : 'light')
    syncThemeColorMeta()
  }

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem('bagu:sidebar', sidebarCollapsed.value ? '1' : '0')
  }

  function setLastSlug(slug: string) {
    if (!slug) return
    lastSlug.value = slug
    localStorage.setItem('bagu:lastSlug', slug)
  }

  async function loadModules(force = false): Promise<ModuleMeta[]> {
    if (!force && modules.value.length > 0) return modules.value
    if (!force && modulesPromise) return modulesPromise
    modulesPromise = knowledgeApi
      .modules()
      .then((list) => {
        modules.value = list
        return list
      })
      .finally(() => {
        modulesPromise = null
      })
    return modulesPromise
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

  syncThemeColorMeta()

  return {
    dark,
    sidebarCollapsed,
    lastSlug,
    modules,
    lightbox,
    toast,
    toggleTheme,
    toggleSidebar,
    setLastSlug,
    loadModules,
    openLightbox,
    closeLightbox,
    showToast,
  }
})
