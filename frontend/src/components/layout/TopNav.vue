<script setup>
import LanguageSwitcher from "../common/LanguageSwitcher.vue";

defineProps({
  authVerified: {
    type: Boolean,
    default: true
  },
  dictionary: {
    type: Object,
    required: true
  },
  isSending: {
    type: Boolean,
    default: false
  },
  locale: {
    type: String,
    required: true
  },
  statusMessage: {
    type: String,
    default: ""
  }
});

defineEmits(["clear-session", "open-sidebar", "sign-out", "toggle-locale"]);
</script>

<template>
  <header class="topbar">
    <div class="topbar-left">
      <button class="ghost-button mobile-only" type="button" @click="$emit('open-sidebar')">
        {{ dictionary.openMenu }}
      </button>

      <div>
        <p class="eyebrow">{{ dictionary.appTagline }}</p>
        <h2>{{ dictionary.appName }}</h2>
      </div>
    </div>

    <div class="topbar-actions">
      <span class="status-pill">{{ statusMessage }}</span>

      <LanguageSwitcher :locale="locale" @toggle="$emit('toggle-locale')" />

      <button
        v-if="authVerified"
        class="ghost-button"
        type="button"
        :disabled="isSending"
        @click="$emit('clear-session')"
      >
        {{ dictionary.clearChat }}
      </button>

      <button
        v-if="authVerified"
        class="ghost-button"
        type="button"
        @click="$emit('sign-out')"
      >
        {{ dictionary.logoutButton }}
      </button>
    </div>
  </header>
</template>
