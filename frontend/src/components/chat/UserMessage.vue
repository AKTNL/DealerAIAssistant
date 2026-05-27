<script setup>
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
</script>

<template>
  <article class="message-row message-user">
    <div class="avatar avatar-user">ME</div>

    <div class="message-card message-card-user message-bubble-user">
      <div class="message-meta">
        <span>{{ dictionary.userLabel }}</span>
      </div>

      <div v-if="message.html" class="markdown-body" v-html="message.html"></div>
    </div>
  </article>
</template>
