<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowRight, Check, Loader2, RefreshCw, ShieldCheck, X } from 'lucide-vue-next'
import { authApi } from '@/api'
import type { SliderCaptchaData } from '@/types'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  close: []
  verified: [captchaToken: string]
}>()

const CANVAS_W = 310
const PIECE_W = 50
const MAX_MOVE = CANVAS_W - PIECE_W

const captcha = ref<SliderCaptchaData | null>(null)
const loading = ref(false)
const verifying = ref(false)
const dragX = ref(0)
const status = ref<'idle' | 'dragging' | 'success' | 'fail'>('idle')
const elapsedSec = ref('')

let dragStartX = 0
let dragStartTime = 0
let activePointerId: number | null = null

async function loadCaptcha() {
  loading.value = true
  status.value = 'idle'
  dragX.value = 0
  elapsedSec.value = ''
  try {
    captcha.value = await authApi.getSliderCaptcha()
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (val) => {
    if (val) loadCaptcha()
  },
  { immediate: true },
)

function onPointerDown(e: PointerEvent) {
  if (loading.value || verifying.value || status.value === 'success' || status.value === 'fail') return
  activePointerId = e.pointerId
  dragStartX = e.clientX - dragX.value
  dragStartTime = Date.now()
  status.value = 'dragging'
  ;(e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (status.value !== 'dragging' || e.pointerId !== activePointerId) return
  const next = Math.max(0, Math.min(MAX_MOVE, Math.round(e.clientX - dragStartX)))
  dragX.value = next
}

async function onPointerUp(e: PointerEvent) {
  if (status.value !== 'dragging' || e.pointerId !== activePointerId) return
  activePointerId = null
  if (dragX.value < 12 || !captcha.value) {
    status.value = 'idle'
    dragX.value = 0
    return
  }

  const durationMs = Math.max(100, Date.now() - dragStartTime)
  verifying.value = true
  try {
    const res = await authApi.verifySliderCaptcha({
      captchaId: captcha.value.captchaId,
      moveX: dragX.value,
      durationMs,
    })
    if (res.passed && res.captchaToken) {
      status.value = 'success'
      elapsedSec.value = (durationMs / 1000).toFixed(1)
      window.setTimeout(() => {
        emit('verified', res.captchaToken!)
      }, 320)
    } else {
      status.value = 'fail'
      window.setTimeout(() => {
        loadCaptcha()
      }, 650)
    }
  } catch {
    status.value = 'fail'
    window.setTimeout(() => {
      loadCaptcha()
    }, 650)
  } finally {
    verifying.value = false
  }
}

function onKeydown(e: KeyboardEvent) {
  if (props.open && e.key === 'Escape') {
    emit('close')
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Transition name="fade">
    <div
      v-if="open"
      role="dialog"
      aria-modal="true"
      aria-labelledby="slider-captcha-title"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4 backdrop-blur-xs"
      @click.self="emit('close')"
    >
      <div class="popover-surface w-full max-w-[350px] rounded-2xl p-4">
        <!-- 顶栏 -->
        <div class="mb-3 flex items-center justify-between">
          <div class="flex items-center gap-1.5">
            <ShieldCheck class="h-4 w-4 text-[var(--accent)]" />
            <h3 id="slider-captcha-title" class="text-sm font-semibold text-[var(--text-primary)]">
              拖动滑块完成拼图验证
            </h3>
          </div>
          <div class="flex items-center gap-1">
            <button
              type="button"
              class="flex h-7 w-7 items-center justify-center rounded-md text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)]"
              title="更换拼图"
              aria-label="更换拼图"
              :disabled="loading || verifying"
              @click="loadCaptcha"
            >
              <RefreshCw class="h-3.5 w-3.5" :class="loading ? 'animate-spin' : ''" />
            </button>
            <button
              type="button"
              class="flex h-7 w-7 items-center justify-center rounded-md text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)]"
              title="关闭 (Esc)"
              aria-label="关闭人机验证弹窗"
              @click="emit('close')"
            >
              <X class="h-4 w-4" />
            </button>
          </div>
        </div>

        <!-- 拼图画布 (310 x 150) -->
        <div class="relative mx-auto h-[150px] w-[310px] overflow-hidden rounded-lg border border-[var(--border-default)] bg-[var(--bg-subtle)] select-none">
          <div v-if="loading || !captcha" class="flex h-full w-full items-center justify-center gap-2 text-xs text-[var(--text-muted)]">
            <Loader2 class="h-4 w-4 animate-spin text-[var(--accent)]" /> 生成工程图纸中...
          </div>
          <template v-else>
            <img
              :src="captcha.backgroundImage"
              alt="拼图验证底图"
              draggable="false"
              class="h-[150px] w-[310px] object-cover"
            />
            <img
              :src="captcha.blockImage"
              alt="拼图滑块"
              draggable="false"
              class="pointer-events-none absolute left-0 h-[50px] w-[50px]"
              :style="{
                top: `${captcha.blockY}px`,
                transform: `translateX(${dragX}px)`,
              }"
            />
          </template>

          <!-- 结果横幅 -->
          <Transition name="fade">
            <div
              v-if="status === 'success'"
              class="absolute inset-x-0 bottom-0 flex items-center justify-center gap-1.5 bg-[#2E6F5E]/95 py-1.5 font-mono text-xs font-medium text-white"
            >
              <Check class="h-3.5 w-3.5" /> 验证通过 · 耗时 {{ elapsedSec }}s
            </div>
            <div
              v-else-if="status === 'fail'"
              class="absolute inset-x-0 bottom-0 flex items-center justify-center bg-[#C84B31]/95 py-1.5 text-xs font-medium text-white"
            >
              位置未对齐，正在刷新拼图...
            </div>
          </Transition>
        </div>

        <!-- 滑动轨道 -->
        <div
          class="relative mx-auto mt-3.5 h-10 w-[310px] overflow-hidden rounded-lg border border-[var(--border-default)] bg-[var(--bg-subtle)] select-none"
        >
          <!-- 已滑过填充条 -->
          <div
            class="absolute inset-y-0 left-0"
            :class="
              status === 'success'
                ? 'bg-[#EBF4F1] dark:bg-[#162923]'
                : status === 'fail'
                  ? 'bg-[#FBF0ED] dark:bg-[#2E1B16]'
                  : 'bg-[var(--accent-soft)]'
            "
            :style="{ width: `${dragX + 22}px` }"
          />

          <p
            v-if="status === 'idle' && dragX === 0"
            class="pointer-events-none absolute inset-0 flex items-center justify-center pl-6 text-xs text-[var(--text-muted)]"
          >
            向右拖动滑块对齐缺口
          </p>

          <!-- 拖拽手柄 -->
          <button
            type="button"
            aria-label="按住并向右拖动滑块完成拼图"
            class="absolute top-0.5 flex h-[34px] w-[44px] touch-none items-center justify-center rounded-md border transition-colors focus-visible:outline-2 focus-visible:outline-[var(--accent)]"
            :class="
              status === 'success'
                ? 'border-[#2E6F5E] bg-[#2E6F5E] text-white'
                : status === 'fail'
                  ? 'border-[#C84B31] bg-[#C84B31] text-white'
                  : 'cursor-grab border-[var(--border-strong)] bg-[var(--bg-surface)] text-[var(--text-primary)] hover:border-[var(--accent)] active:cursor-grabbing'
            "
            :style="{ transform: `translateX(${dragX + 2}px)` }"
            @pointerdown="onPointerDown"
            @pointermove="onPointerMove"
            @pointerup="onPointerUp"
            @pointercancel="onPointerUp"
          >
            <Check v-if="status === 'success'" class="h-4 w-4" />
            <X v-else-if="status === 'fail'" class="h-4 w-4" />
            <ArrowRight v-else class="h-4 w-4 text-[var(--accent)]" />
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>
