<script setup lang="ts">
import ImageLightbox from '@/components/common/ImageLightbox.vue'
import AppToast from '@/components/common/AppToast.vue'
</script>

<template>
  <RouterView />
  <ImageLightbox />
  <AppToast />
</template>
