<script setup lang="ts">
import AppHeader from '@/components/AppHeader.vue'
import AuthDialog from '@/components/AuthDialog.vue'
</script>

<template>
  <AppHeader />
  <RouterView />
  <AuthDialog />
</template>
