<script setup>
import AssistantMessage from "./AssistantMessage.vue";
import SystemMessage from "./SystemMessage.vue";
import UserMessage from "./UserMessage.vue";

defineProps({
  dictionary: {
    type: Object,
    required: true
  },
  locale: {
    type: String,
    default: "zh"
  },
  messages: {
    type: Array,
    default: () => []
  },
  streamPhase: { type: String, default: "idle" }
});

defineEmits(["submit-follow-up"]);
</script>

<template>
  <div>
    <template v-for="message in messages" :key="message.id">
      <SystemMessage
        v-if="message.role === 'system'"
        :type="message.type"
        :dictionary="dictionary"
      />

      <UserMessage
        v-else-if="message.role === 'user'"
        :dictionary="dictionary"
        :message="message"
      />

      <AssistantMessage
        v-else
        :dictionary="dictionary"
        :locale="locale"
        :message="message"
        :stream-phase="streamPhase"
        @submit-follow-up="$emit('submit-follow-up', $event)"
      />
    </template>
  </div>
</template>
