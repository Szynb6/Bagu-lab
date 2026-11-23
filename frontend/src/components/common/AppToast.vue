<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useUiStore } from '@/stores/ui'

const { toast } = storeToRefs(useUiStore())
</script>

<template>
  <Transition name="slide-up">
    <div
      v-if="toast"
      :key="toast.id"
      class="fixed bottom-24 left-1/2 z-[80] -translate-x-1/2 rounded-full bg-slate-900/90 px-4 py-2 text-sm text-white shadow-lg backdrop-blur lg:bottom-8 dark:bg-white/90 dark:text-slate-900"
    >
      {{ toast.text }}
    </div>
  </Transition>
</template>
