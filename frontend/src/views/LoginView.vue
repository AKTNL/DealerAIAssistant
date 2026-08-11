<script setup>
import { nextTick, ref, watch } from "vue";

const props = defineProps({
  username: { type: String, default: "" },
  password: { type: String, default: "" },
  dictionary: { type: Object, required: true },
  locale: { type: String, required: true },
  loginError: { type: String, default: "" },
  loginLoading: { type: Boolean, default: false }
});

const emit = defineEmits(["submit", "toggle-locale", "update:username", "update:password"]);
const usernameInput = ref(null);
const isCardShaking = ref(false);

async function replayLoginError() {
  isCardShaking.value = false;
  await nextTick();
  usernameInput.value?.focus();
  isCardShaking.value = true;
}

watch(() => props.loginError, async (value) => {
  if (!value) {
    isCardShaking.value = false;
    return;
  }
  await replayLoginError();
});

function handleSubmit() {
  if (!props.loginLoading) {
    emit("submit");
  }
}
</script>

<template>
  <div class="login-glass-shell">
    <div class="login-watermark"><img src="/logo.png" alt="Watermark" class="login-watermark-img" /></div>
    <div :class="['login-card', { 'login-card-shake': isCardShaking }]" @animationend="isCardShaking = false">
      <div class="login-card-top-bar"></div>
      <div class="login-lang-row">
        <button class="login-lang-toggle" type="button" @click="$emit('toggle-locale')">
          {{ locale === 'zh' ? '中文 / EN' : 'EN / 中文' }}
        </button>
      </div>
      <div class="login-hero">
        <div class="login-logo-wrap"><div class="login-logo-glow"></div><img src="/logo.png" alt="Brand Logo" class="login-logo-img" /></div>
        <h2 class="login-title">{{ dictionary.loginTitle }}</h2>
        <p class="login-subtitle">{{ dictionary.loginEyebrow }}</p>
      </div>
      <div class="login-input-wrap">
        <input
          ref="usernameInput"
          class="login-input-field"
          :placeholder="dictionary.loginUsernamePlaceholder"
          :value="username"
          autocomplete="username"
          @input="$emit('update:username', $event.target.value)"
        />
      </div>
      <div class="login-input-wrap">
        <input
          :class="['login-input-field', { 'login-input-error': loginError }]"
          :placeholder="dictionary.loginPasswordPlaceholder"
          :value="password"
          type="password"
          autocomplete="current-password"
          :aria-invalid="loginError ? 'true' : 'false'"
          @input="$emit('update:password', $event.target.value)"
          @keyup.enter="handleSubmit"
        />
      </div>
      <div class="login-error-row"><p v-if="loginError" class="login-error-text">{{ loginError }}</p></div>
      <button
        :disabled="!username.trim() || !password || loginLoading"
        class="login-submit-button"
        type="button"
        @click="handleSubmit"
      >
        {{ loginLoading ? dictionary.loginLoading : dictionary.loginButton }}
      </button>
      <p class="login-footer-text">{{ dictionary.loginNoticeBody }}</p>
    </div>
  </div>
</template>
