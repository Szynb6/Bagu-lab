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
      role="status"
      aria-live="polite"
      class="fixed bottom-24 left-1/2 z-[80] -translate-x-1/2 rounded-lg border border-[#3B3933] bg-[#161614] px-4 py-2 text-sm font-medium text-[#ECEAE4] shadow-[var(--shadow-popover)] lg:bottom-8 dark:border-[#D4D0C5] dark:bg-[#ECEAE4] dark:text-[#161614]"
    >
      {{ toast.text }}
    </div>
  </Transition>
</template>
