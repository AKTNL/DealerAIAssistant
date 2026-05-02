<script setup>
import LanguageSwitcher from "../components/common/LanguageSwitcher.vue";

defineProps({
  accessKey: {
    type: String,
    default: ""
  },
  dictionary: {
    type: Object,
    required: true
  },
  locale: {
    type: String,
    required: true
  },
  loginError: {
    type: String,
    default: ""
  },
  loginLoading: {
    type: Boolean,
    default: false
  }
});

defineEmits(["submit", "toggle-locale", "update:access-key"]);
</script>

<template>
  <div class="login-shell">
    <header class="login-shell-header">
      <div class="login-brand">
        <p class="eyebrow">{{ dictionary.appName }}</p>
        <p class="login-brand-copy">{{ dictionary.appTagline }}</p>
      </div>

      <LanguageSwitcher :locale="locale" @toggle="$emit('toggle-locale')" />
    </header>

    <section class="login-screen">
      <div class="login-panel">
        <p class="eyebrow">{{ dictionary.loginEyebrow }}</p>
        <h2>{{ dictionary.loginTitle }}</h2>
        <p class="login-copy">{{ dictionary.loginBody }}</p>

        <form class="login-form" @submit.prevent="$emit('submit')">
          <input
            class="text-input"
            :placeholder="dictionary.loginPlaceholder"
            :value="accessKey"
            type="password"
            autocomplete="off"
            @input="$emit('update:access-key', $event.target.value)"
          />

          <button class="primary-button" type="submit" :disabled="loginLoading">
            {{ loginLoading ? dictionary.loginLoading : dictionary.loginButton }}
          </button>
        </form>

        <p v-if="loginError" class="error-text">{{ loginError }}</p>
      </div>
    </section>
  </div>
</template>
