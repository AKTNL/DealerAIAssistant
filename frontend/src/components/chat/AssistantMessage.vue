<script setup>
import FollowUpButtons from "./FollowUpButtons.vue";

defineProps({
  dictionary: {
    type: Object,
    required: true
  },
  message: {
    type: Object,
    required: true
  }
});

defineEmits(["submit-follow-up", "toggle-thinking"]);
</script>

<template>
  <article class="message-row message-assistant">
    <div class="avatar">AI</div>

    <div class="message-card">
      <div class="message-meta">
        <span>{{ dictionary.assistantLabel }}</span>
        <span v-if="message.status">{{ message.status }}</span>
      </div>

      <button
        v-if="message.thinking"
        class="thinking-toggle"
        type="button"
        @click="$emit('toggle-thinking', message)"
      >
        {{ message.thinkingExpanded ? dictionary.hideThinking : dictionary.showThinking }}
      </button>

      <div
        v-if="message.thinking && message.thinkingExpanded"
        class="thinking-panel markdown-body"
        v-html="message.thinkingHtml"
      ></div>

      <div v-if="message.html" class="markdown-body" v-html="message.html"></div>

      <FollowUpButtons
        :dictionary="dictionary"
        :follow-ups="message.followUps"
        @select="$emit('submit-follow-up', $event)"
      />
    </div>
  </article>
</template>
