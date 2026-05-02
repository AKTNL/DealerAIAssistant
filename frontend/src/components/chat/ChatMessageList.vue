<script setup>
import AssistantMessage from "./AssistantMessage.vue";
import UserMessage from "./UserMessage.vue";

defineProps({
  dictionary: {
    type: Object,
    required: true
  },
  hasMessages: {
    type: Boolean,
    default: false
  },
  messages: {
    type: Array,
    default: () => []
  }
});

defineEmits(["submit-follow-up", "toggle-thinking"]);
</script>

<template>
  <div>
    <div v-if="!hasMessages" class="empty-state">
      <h3>{{ dictionary.emptyTitle }}</h3>
      <p>{{ dictionary.emptyBody }}</p>
    </div>

    <template v-for="message in messages" :key="message.id">
      <UserMessage
        v-if="message.role === 'user'"
        :dictionary="dictionary"
        :message="message"
      />

      <AssistantMessage
        v-else
        :dictionary="dictionary"
        :message="message"
        @submit-follow-up="$emit('submit-follow-up', $event)"
        @toggle-thinking="$emit('toggle-thinking', $event)"
      />
    </template>
  </div>
</template>
