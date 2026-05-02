<script setup>
defineProps({
  locale: {
    type: String,
    required: true
  }
});

defineEmits(["toggle"]);
</script>

<template>
  <button class="ghost-button" type="button" @click="$emit('toggle')">
    {{ locale === "zh" ? "EN" : "ZH" }}
  </button>
</template>
