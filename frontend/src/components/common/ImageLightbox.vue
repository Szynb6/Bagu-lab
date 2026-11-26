<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ChevronLeft, ChevronRight, RotateCcw, X, ZoomIn, ZoomOut } from 'lucide-vue-next'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const { lightbox } = storeToRefs(ui)
const view = reactive({ scale: 1, x: 0, y: 0 })
const current = computed(() => (lightbox.value ? lightbox.value.images[lightbox.value.index] : null))
const total = computed(() => lightbox.value?.images.length ?? 0)

function reset() {
  view.scale = 1
  view.x = 0
  view.y = 0
}
watch(() => lightbox.value?.index, reset)

function go(step: number) {
  if (!lightbox.value || total.value < 2) return
  lightbox.value.index = (lightbox.value.index + step + total.value) % total.value
}
function zoom(delta: number) {
  view.scale = Math.min(6, Math.max(0.5, +(view.scale + delta).toFixed(2)))
}
function onWheel(e: WheelEvent) {
  zoom(e.deltaY < 0 ? 0.2 : -0.2)
}

// 拖拽平移 + 双指缩放
let drag: { x: number; y: number; ox: number; oy: number } | null = null
let pinch: { d: number; s: number } | null = null
function onPointerDown(e: PointerEvent) {
  drag = { x: e.clientX, y: e.clientY, ox: view.x, oy: view.y }
  ;(e.target as HTMLElement).setPointerCapture?.(e.pointerId)
}
function onPointerMove(e: PointerEvent) {
  if (!drag || pinch) return
  view.x = drag.ox + (e.clientX - drag.x)
  view.y = drag.oy + (e.clientY - drag.y)
}
function onPointerUp() {
  drag = null
}
const dist = (t: TouchList) => Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY)
function onTouchStart(e: TouchEvent) {
  if (e.touches.length === 2) pinch = { d: dist(e.touches), s: view.scale }
}
function onTouchMove(e: TouchEvent) {
  if (pinch && e.touches.length === 2) {
    e.preventDefault()
    view.scale = Math.min(6, Math.max(0.5, pinch.s * (dist(e.touches) / pinch.d)))
  }
}
function onTouchEnd() {
  pinch = null
}

function onKey(e: KeyboardEvent) {
  if (!lightbox.value) return
  e.stopPropagation()
  if (e.key === 'Escape') ui.closeLightbox()
  else if (e.key === 'ArrowLeft') go(-1)
  else if (e.key === 'ArrowRight') go(1)
  else if (e.key === '+' || e.key === '=') zoom(0.25)
  else if (e.key === '-') zoom(-0.25)
  else if (e.key === '0') reset()
}
onMounted(() => window.addEventListener('keydown', onKey, true))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey, true))
</script>

<template>
  <Transition name="fade">
    <div
      v-if="lightbox && current"
      role="dialog"
      aria-modal="true"
      aria-label="原理图大图预览"
      class="fixed inset-0 z-[90] flex flex-col bg-[#111110]/94 backdrop-blur-sm"
      @wheel.prevent="onWheel"
    >
      <div class="flex items-center justify-between px-4 py-3 text-[#ECEAE4]">
        <span class="font-mono text-sm tabular-nums">{{ lightbox.index + 1 }} / {{ total }}</span>
        <div class="flex items-center gap-1">
          <button type="button" class="rounded-lg p-2 hover:bg-white/10" title="缩小 (-)" aria-label="缩小图片" @click="zoom(-0.25)">
            <ZoomOut class="h-5 w-5" />
          </button>
          <span class="w-12 text-center font-mono text-xs tabular-nums">{{ Math.round(view.scale * 100) }}%</span>
          <button type="button" class="rounded-lg p-2 hover:bg-white/10" title="放大 (+)" aria-label="放大图片" @click="zoom(0.25)">
            <ZoomIn class="h-5 w-5" />
          </button>
          <button type="button" class="rounded-lg p-2 hover:bg-white/10" title="还原 (0)" aria-label="重置缩放" @click="reset">
            <RotateCcw class="h-5 w-5" />
          </button>
          <button type="button" class="ml-2 rounded-lg p-2 hover:bg-white/10" title="关闭 (Esc)" aria-label="关闭图片预览" @click="ui.closeLightbox()">
            <X class="h-5 w-5" />
          </button>
        </div>
      </div>

      <div
        class="relative flex flex-1 touch-none items-center justify-center overflow-hidden"
        @click.self="ui.closeLightbox()"
        @touchstart="onTouchStart"
        @touchmove="onTouchMove"
        @touchend="onTouchEnd"
      >
        <img
          :src="current.url"
          :alt="current.caption"
          draggable="false"
          class="max-h-[82vh] max-w-[92vw] cursor-grab select-none rounded-lg bg-[#F7F6F2] object-contain outline-1 -outline-offset-1 outline-black/10 active:cursor-grabbing dark:outline-white/10"
          :style="{ transform: `translate(${view.x}px, ${view.y}px) scale(${view.scale})`, transition: drag ? 'none' : 'transform .15s ease' }"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @dblclick="view.scale === 1 ? (view.scale = 2) : reset()"
        />
        <template v-if="total > 1">
          <button
            type="button"
            class="absolute left-3 rounded-lg bg-white/15 p-2.5 text-white hover:bg-white/25"
            aria-label="上一张图"
            @click.stop="go(-1)"
          >
            <ChevronLeft class="h-6 w-6" />
          </button>
          <button
            type="button"
            class="absolute right-3 rounded-lg bg-white/15 p-2.5 text-white hover:bg-white/25"
            aria-label="下一张图"
            @click.stop="go(1)"
          >
            <ChevronRight class="h-6 w-6" />
          </button>
        </template>
      </div>

      <p v-if="current.caption" class="px-6 pb-6 pt-2 text-center text-sm text-[#ECEAE4]">{{ current.caption }}</p>
      <p class="hidden pb-3 text-center text-[11px] text-[#A8A397] lg:block">滚轮缩放 · 拖拽平移 · 双击放大 · ← → 切换 · Esc 关闭</p>
    </div>
  </Transition>
</template>
