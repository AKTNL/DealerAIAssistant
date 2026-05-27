<script setup>
import { nextTick, ref, watch } from "vue";

const props = defineProps({
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

const emit = defineEmits(["submit", "toggle-locale", "update:access-key"]);

const accessKeyInput = ref(null);
const isCardShaking = ref(false);

async function replayLoginError() {
  isCardShaking.value = false;
  await nextTick();

  if (accessKeyInput.value) {
    void accessKeyInput.value.offsetWidth;
    accessKeyInput.value.focus();
  }

  isCardShaking.value = true;
}

watch(
  () => props.loginError,
  async (value) => {
    if (!value) {
      isCardShaking.value = false;
      return;
    }

    await replayLoginError();
  }
);

function handleAnimationEnd() {
  isCardShaking.value = false;
}

async function handleSubmit() {
  if (props.loginLoading) {
    return;
  }

  if (props.loginError) {
    await replayLoginError();
  }

  emit("submit");
}
</script>

<template>
  <div class="login-glass-shell">
    <!-- Watermark logo -->
    <div class="login-watermark">
      <img src="/logo.png" alt="Watermark" class="login-watermark-img" />
    </div>

    <!-- Glassmorphism card -->
    <div
      :class="['login-card', { 'login-card-shake': isCardShaking }]"
      @animationend="handleAnimationEnd"
    >
      <div class="login-card-top-bar"></div>

      <div class="login-lang-row">
        <button
          class="login-lang-toggle"
          type="button"
          @click="$emit('toggle-locale')"
        >
          {{ locale === 'zh' ? '中文 / EN' : 'EN / 中文' }}
        </button>
      </div>

      <div class="login-hero">
        <div class="login-logo-wrap">
          <div class="login-logo-glow"></div>
          <img
            src="/logo.png"
            alt="Brand Logo"
            class="login-logo-img"
          />
        </div>

        <h2 class="login-title">{{ dictionary.loginTitle }}</h2>
        <p class="login-subtitle">{{ dictionary.loginEyebrow }}</p>
      </div>

      <div class="login-input-wrap">
        <div class="login-input-icon">
          <svg class="login-input-icon-svg" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
            ></path>
          </svg>
        </div>
        <input
          ref="accessKeyInput"
          :class="['login-input-field', { 'login-input-error': loginError }]"
          :placeholder="dictionary.loginPlaceholder"
          :value="accessKey"
          type="password"
          autocomplete="off"
          :aria-invalid="loginError ? 'true' : 'false'"
          @input="$emit('update:access-key', $event.target.value)"
          @keyup.enter="handleSubmit"
        />
      </div>

      <div class="login-error-row">
        <p v-show="loginError" class="login-error-text">
          <span class="login-error-dot"></span>
          {{ loginError }}
        </p>
      </div>

      <button
        :disabled="!accessKey.trim() || loginLoading"
        class="login-submit-button"
        type="button"
        @click="handleSubmit"
      >
        {{ loginLoading ? dictionary.loginLoading : dictionary.loginButton }}
      </button>

      <p class="login-footer-text">
        {{ dictionary.loginNoticeBody }}
      </p>
    </div>
  </div>
</template>
