<script setup>
defineProps({
  currentPassword: { type: String, default: "" },
  newPassword: { type: String, default: "" },
  dictionary: { type: Object, required: true },
  loading: { type: Boolean, default: false },
  error: { type: String, default: "" }
});

defineEmits(["submit", "sign-out", "update:current-password", "update:new-password"]);
</script>

<template>
  <div class="login-glass-shell">
    <div class="login-card">
      <div class="login-card-top-bar"></div>
      <div class="login-hero">
        <h2 class="login-title">{{ dictionary.passwordChangeTitle }}</h2>
        <p class="login-subtitle">{{ dictionary.passwordChangeBody }}</p>
      </div>
      <div class="login-input-wrap">
        <input
          class="login-input-field"
          type="password"
          autocomplete="current-password"
          :placeholder="dictionary.currentPasswordPlaceholder"
          :value="currentPassword"
          @input="$emit('update:current-password', $event.target.value)"
        />
      </div>
      <div class="login-input-wrap">
        <input
          class="login-input-field"
          type="password"
          autocomplete="new-password"
          :placeholder="dictionary.newPasswordPlaceholder"
          :value="newPassword"
          @input="$emit('update:new-password', $event.target.value)"
          @keyup.enter="$emit('submit')"
        />
      </div>
      <div class="login-error-row"><p v-if="error" class="login-error-text">{{ error }}</p></div>
      <button
        class="login-submit-button"
        type="button"
        :disabled="!currentPassword || !newPassword || loading"
        @click="$emit('submit')"
      >
        {{ loading ? dictionary.passwordChanging : dictionary.passwordChangeButton }}
      </button>
      <button class="login-lang-toggle" type="button" @click="$emit('sign-out')">{{ dictionary.logoutButton }}</button>
    </div>
  </div>
</template>
