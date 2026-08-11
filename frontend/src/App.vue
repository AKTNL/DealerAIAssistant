<script setup>
import { onMounted } from "vue";
import ChatView from "./views/ChatView.vue";
import LoginView from "./views/LoginView.vue";
import PasswordChangeView from "./views/PasswordChangeView.vue";
import { messages } from "./i18n/messages";
import { useAuth } from "./composables/useAuth";
import { useI18nState } from "./composables/useI18nState";
import { STORAGE_KEYS } from "./constants/storageKeys";
import { removeStorageValue } from "./utils/storage";

const { locale, dictionary, toggleLocale } = useI18nState(messages);
const auth = useAuth({ dictionary });

onMounted(auth.initialize);

async function handleSignOut() {
  removeStorageValue("local", STORAGE_KEYS.session);
  await auth.signOut();
}
</script>

<template>
  <LoginView
    v-if="auth.initialized.value && !auth.authVerified.value"
    :dictionary="dictionary"
    :locale="locale"
    :username="auth.username.value"
    :password="auth.password.value"
    :login-error="auth.loginError.value"
    :login-loading="auth.loginLoading.value"
    @toggle-locale="toggleLocale"
    @update:username="auth.username.value = $event"
    @update:password="auth.password.value = $event"
    @submit="auth.submitCredentials"
  />

  <PasswordChangeView
    v-else-if="auth.initialized.value && auth.mustChangePassword.value"
    :current-password="auth.currentPassword.value"
    :new-password="auth.newPassword.value"
    :dictionary="dictionary"
    :loading="auth.loginLoading.value"
    :error="auth.loginError.value"
    @update:current-password="auth.currentPassword.value = $event"
    @update:new-password="auth.newPassword.value = $event"
    @submit="auth.submitPasswordChange"
    @sign-out="handleSignOut"
  />

  <ChatView
    v-else-if="auth.initialized.value"
    :auth-verified="auth.authVerified.value"
    :current-user="auth.currentUser.value"
    :dictionary="dictionary"
    :locale="locale"
    @toggle-locale="toggleLocale"
    @sign-out="handleSignOut"
  />
</template>
