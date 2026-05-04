<script setup>
defineProps({
  dictionary: {
    type: Object,
    required: true
  },
  isSending: {
    type: Boolean,
    default: false
  }
});

defineEmits(["select"]);
</script>

<template>
  <section v-if="dictionary.prompts?.length" class="example-prompt-group">
    <div class="example-prompt-header">
      <p class="eyebrow">{{ dictionary.sidebarSection }}</p>
    </div>

    <div class="example-prompt-list">
      <button
        v-for="prompt in dictionary.prompts"
        :key="prompt"
        class="example-prompt-button"
        type="button"
        :disabled="isSending"
        @click="$emit('select', prompt)"
      >
        {{ prompt }}
      </button>
    </div>
  </section>
</template>
