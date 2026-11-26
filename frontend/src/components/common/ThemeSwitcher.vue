<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Check, Palette } from 'lucide-vue-next'
import { DARK_THEMES, LIGHT_THEMES, type ThemeId } from '@/constants/themes'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const open = ref(false)
const rootEl = ref<HTMLElement>()

function choose(id: ThemeId) {
  ui.setTheme(id)
  open.value = false
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && open.value) {
    open.value = false
  }
}

function onPointerDown(e: PointerEvent) {
  if (!open.value || !rootEl.value) return
  if (!rootEl.value.contains(e.target as Node)) {
    open.value = false
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('pointerdown', onPointerDown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('pointerdown', onPointerDown)
})
</script>

<template>
  <div ref="rootEl" class="relative">
    <button
      type="button"
      class="relative flex h-9 w-9 items-center justify-center rounded-lg border border-transparent text-[var(--text-secondary)] transition-colors hover:border-[var(--border-default)] hover:bg-[var(--bg-surface)] hover:text-[var(--text-primary)] focus-visible:outline-2 focus-visible:outline-[var(--accent)]"
      :class="open ? 'border-[var(--border-default)] bg-[var(--bg-surface)] text-[var(--text-primary)]' : ''"
      :title="`切换配色风格（当前：${ui.currentTheme.name}）`"
      :aria-label="`切换配色风格，当前为${ui.currentTheme.name}`"
      aria-haspopup="menu"
      :aria-expanded="open"
      @click="open = !open"
    >
      <Palette class="h-[18px] w-[18px]" :stroke-width="1.85" />
      <span
        class="absolute bottom-1.5 right-1.5 h-1.5 w-1.5 rounded-full ring-1 ring-[var(--bg-canvas)]"
        :style="{ backgroundColor: ui.currentTheme.swatches[2] }"
      />
    </button>

    <Transition name="slide-up">
      <div
        v-if="open"
        role="menu"
        aria-label="选择配色风格"
        class="popover-surface absolute right-0 z-50 mt-2 w-[264px] max-w-[calc(100vw-2rem)] max-h-[min(78vh,480px)] overflow-y-auto overscroll-contain rounded-2xl p-2 text-xs"
      >
        <div class="px-2.5 pb-1 pt-1 text-[11px] font-semibold tracking-wide text-[var(--text-muted)]">
          浅色纸面（{{ LIGHT_THEMES.length }}）
        </div>
        <div class="space-y-0.5">
          <button
            v-for="t in LIGHT_THEMES"
            :key="t.id"
            type="button"
            role="menuitemradio"
            :aria-checked="ui.themeId === t.id"
            class="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left transition-colors"
            :class="
              ui.themeId === t.id
                ? 'bg-[var(--accent-soft)] text-[var(--accent-fg)]'
                : 'text-[var(--text-primary)] hover:bg-[var(--bg-subtle)]'
            "
            @click="choose(t.id)"
          >
            <span class="flex shrink-0 items-center -space-x-1" aria-hidden="true">
              <span
                v-for="(color, idx) in t.swatches"
                :key="idx"
                class="h-3.5 w-3.5 rounded-full border border-black/15"
                :style="{ backgroundColor: color }"
              />
            </span>
            <span class="min-w-0 flex-1">
              <span class="block truncate text-[12.5px] font-semibold">{{ t.name }}</span>
              <span class="block truncate text-[11px] opacity-75">{{ t.desc }}</span>
            </span>
            <Check v-if="ui.themeId === t.id" class="h-3.5 w-3.5 shrink-0" :stroke-width="2.2" />
          </button>
        </div>

        <div class="my-1.5 h-px bg-[var(--border-default)]" />

        <div class="px-2.5 pb-1 pt-0.5 text-[11px] font-semibold tracking-wide text-[var(--text-muted)]">
          深色护眼（{{ DARK_THEMES.length }}）
        </div>
        <div class="space-y-0.5">
          <button
            v-for="t in DARK_THEMES"
            :key="t.id"
            type="button"
            role="menuitemradio"
            :aria-checked="ui.themeId === t.id"
            class="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left transition-colors"
            :class="
              ui.themeId === t.id
                ? 'bg-[var(--accent-soft)] text-[var(--accent-fg)]'
                : 'text-[var(--text-primary)] hover:bg-[var(--bg-subtle)]'
            "
            @click="choose(t.id)"
          >
            <span class="flex shrink-0 items-center -space-x-1" aria-hidden="true">
              <span
                v-for="(color, idx) in t.swatches"
                :key="idx"
                class="h-3.5 w-3.5 rounded-full border border-white/20"
                :style="{ backgroundColor: color }"
              />
            </span>
            <span class="min-w-0 flex-1">
              <span class="block truncate text-[12.5px] font-semibold">{{ t.name }}</span>
              <span class="block truncate text-[11px] opacity-75">{{ t.desc }}</span>
            </span>
            <Check v-if="ui.themeId === t.id" class="h-3.5 w-3.5 shrink-0" :stroke-width="2.2" />
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>
