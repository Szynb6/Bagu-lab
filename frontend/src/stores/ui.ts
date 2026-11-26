import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { knowledgeApi } from '@/api'
import { resolveThemeId, THEME_MAP, type ThemeId } from '@/constants/themes'
import type { ModuleMeta } from '@/types'

export interface LightboxImage {
  url: string
  caption: string
}

/** 全局 UI 状态：多主题配色、最近阅读模块、共享模块元数据缓存、图片灯箱、侧边栏 */
export const useUiStore = defineStore('ui', () => {
  const initialPrefersDark =
    typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches
  const initialTheme = resolveThemeId(
    document.documentElement.dataset.theme ?? localStorage.getItem('bagu:theme'),
    initialPrefersDark || document.documentElement.classList.contains('dark'),
  )

  const themeId = ref<ThemeId>(initialTheme)
  const currentTheme = computed(() => THEME_MAP[themeId.value])
  const dark = computed(() => currentTheme.value.mode === 'dark')

  const sidebarCollapsed = ref(localStorage.getItem('bagu:sidebar') === '1')
  const lastSlug = ref(localStorage.getItem('bagu:lastSlug') || '01-jvm')
  const lightbox = ref<{ images: LightboxImage[]; index: number } | null>(null)
  const toast = ref<{ text: string; id: number } | null>(null)

  const modules = ref<ModuleMeta[]>([])
  let modulesPromise: Promise<ModuleMeta[]> | null = null

  function applyTheme(id: ThemeId, persist = true) {
    themeId.value = id
    const meta = THEME_MAP[id]
    const root = document.documentElement
    root.dataset.theme = id
    root.classList.toggle('dark', meta.mode === 'dark')
    if (persist) {
      localStorage.setItem('bagu:theme', id)
    }
    const themeColorEl = document.querySelector('meta[name="theme-color"]')
    if (themeColorEl) {
      themeColorEl.setAttribute('content', meta.metaColor)
    }
  }

  function setTheme(id: ThemeId) {
    applyTheme(id, true)
  }

  function toggleTheme() {
    applyTheme(dark.value ? 'paper' : 'graphite', true)
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

  applyTheme(initialTheme, false)

  return {
    themeId,
    currentTheme,
    dark,
    sidebarCollapsed,
    lastSlug,
    modules,
    lightbox,
    toast,
    setTheme,
    toggleTheme,
    toggleSidebar,
    setLastSlug,
    loadModules,
    openLightbox,
    closeLightbox,
    showToast,
  }
})
