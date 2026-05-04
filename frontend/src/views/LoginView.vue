<script setup>
import { nextTick, ref, watch } from "vue";
import LanguageSwitcher from "../components/common/LanguageSwitcher.vue";

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

defineEmits(["submit", "toggle-locale", "update:access-key"]);

const accessKeyInput = ref(null);
const isInputShaking = ref(false);

watch(
  () => props.loginError,
  async (value) => {
    if (!value) {
      isInputShaking.value = false;
      return;
    }

    isInputShaking.value = false;
    await nextTick();

    if (accessKeyInput.value) {
      // Force a reflow so the same animation can replay on repeated failures.
      void accessKeyInput.value.offsetWidth;
      accessKeyInput.value.focus();
    }

    isInputShaking.value = true;
  }
);

function handleShakeEnd() {
  isInputShaking.value = false;
}
</script>

<template>
  <div class="login-shell">
    <header class="login-shell-header">
      <LanguageSwitcher :locale="locale" @toggle="$emit('toggle-locale')" />
    </header>

    <section class="login-screen">
      <div class="login-panel">
        <div class="login-panel-top">
          <div class="login-hero-copy">
            <div class="login-hero-heading">
              <div class="login-hero-logo-badge">
                <img src="/logo.png" alt="Brand logo" class="login-hero-logo-image" />
              </div>

              <div class="login-hero-text">
                <h2>{{ dictionary.loginTitle }}</h2>
                <p class="login-copy">{{ dictionary.loginBody }}</p>
              </div>
            </div>
          </div>
        </div>

        <form class="login-form" @submit.prevent="$emit('submit')">
          <input
            ref="accessKeyInput"
            :class="[
              'text-input',
              { 'text-input-error': loginError, 'text-input-shake': isInputShaking }
            ]"
            :placeholder="dictionary.loginPlaceholder"
            :value="accessKey"
            type="password"
            autocomplete="off"
            :aria-invalid="loginError ? 'true' : 'false'"
            @animationend="handleShakeEnd"
            @input="$emit('update:access-key', $event.target.value)"
          />

          <button class="primary-button" type="submit" :disabled="loginLoading">
            {{ loginLoading ? dictionary.loginLoading : dictionary.loginButton }}
          </button>
        </form>

        <p v-if="loginError" class="error-text">{{ loginError }}</p>
      </div>

      <div class="login-screen-footer">
        <p>{{ dictionary.loginNoticeBody }}</p>
      </div>
    </section>
  </div>
</template>
