<script setup>
import ChatView from "./views/ChatView.vue";
import LoginView from "./views/LoginView.vue";
import { messages } from "./i18n/messages";
import { useAuth } from "./composables/useAuth";
import { useI18nState } from "./composables/useI18nState";
import { STORAGE_KEYS } from "./constants/storageKeys";
import { removeStorageValue } from "./utils/storage";

const { locale, dictionary, toggleLocale } = useI18nState(messages);
const {
  accessKey,
  authVerified,
  loginError,
  loginLoading,
  signOut,
  submitAccessKey
} = useAuth({ dictionary });

function handleSignOut() {
  removeStorageValue("session", STORAGE_KEYS.session);
  signOut();
}
</script>

<template>
  <LoginView
    v-if="!authVerified"
    :dictionary="dictionary"
    :locale="locale"
    :access-key="accessKey"
    :login-error="loginError"
    :login-loading="loginLoading"
    @toggle-locale="toggleLocale"
    @update:access-key="accessKey = $event"
    @submit="submitAccessKey"
  />

  <ChatView
    v-else
    :dictionary="dictionary"
    :locale="locale"
    @toggle-locale="toggleLocale"
    @sign-out="handleSignOut"
  />
</template>
