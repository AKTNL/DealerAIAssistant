<script setup>
defineProps({
  type: { type: String, required: true },
  dictionary: { type: Object, required: true }
});
</script>

<template>
  <div v-if="type === 'welcome'" class="empty-state">
    <h3>{{ dictionary.welcomeTitle }}</h3>
    <p>{{ dictionary.welcomeBody }}</p>
  </div>
  <div v-else-if="type === 'system-clear'" class="empty-state">
    <h3>{{ dictionary.clearSuccess }}</h3>
    <p>{{ dictionary.welcomeBody }}</p>
  </div>
</template>
