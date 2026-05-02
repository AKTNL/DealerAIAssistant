<script setup>
defineProps({
  dictionary: {
    type: Object,
    required: true
  },
  followUps: {
    type: Array,
    default: () => []
  }
});

defineEmits(["select"]);
</script>

<template>
  <div v-if="followUps.length" class="follow-up-group">
    <p>{{ dictionary.followUpLabel }}</p>

    <div class="follow-up-list">
      <button
        v-for="followUp in followUps"
        :key="followUp"
        class="follow-up-button"
        type="button"
        @click="$emit('select', followUp)"
      >
        {{ followUp }}
      </button>
    </div>
  </div>
</template>
