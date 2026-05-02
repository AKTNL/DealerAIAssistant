<script setup>
import { computed, nextTick, ref } from "vue";
import ChatInput from "../components/chat/ChatInput.vue";
import ChatMessageList from "../components/chat/ChatMessageList.vue";
import ExampleSidebar from "../components/layout/ExampleSidebar.vue";
import TopNav from "../components/layout/TopNav.vue";
import { useChat } from "../composables/useChat";

const props = defineProps({
  dictionary: {
    type: Object,
    required: true
  },
  locale: {
    type: String,
    required: true
  }
});

defineEmits(["sign-out", "toggle-locale"]);

const authVerified = computed(() => true);
const {
  activeSessionLabel,
  closeMobileSidebar,
  fillPrompt,
  handleClearSession,
  hasMessages,
  isSending,
  messages,
  openMobileSidebar,
  promptInput,
  requestError,
  scrollContainer,
  showMobileSidebar,
  startNewChat,
  statusMessage,
  submitPrompt,
  toastMessage,
  toggleThinking
} = useChat({
  authVerified,
  dictionary: computed(() => props.dictionary),
  locale: computed(() => props.locale)
});

const composerPanel = ref(null);

async function handlePromptFill(prompt) {
  fillPrompt(prompt);
  await nextTick();
  composerPanel.value?.focusComposer();
}
</script>

<template>
  <div class="app-shell">
    <ExampleSidebar
      :active-session-label="activeSessionLabel"
      :dictionary="dictionary"
      :show-mobile-sidebar="showMobileSidebar"
      @close="closeMobileSidebar"
      @fill-prompt="handlePromptFill"
      @new-chat="startNewChat"
    />

    <div v-if="showMobileSidebar" class="sidebar-backdrop" @click="closeMobileSidebar"></div>

    <main class="main-panel">
      <TopNav
        :auth-verified="authVerified"
        :dictionary="dictionary"
        :is-sending="isSending"
        :locale="locale"
        :status-message="statusMessage"
        @clear-session="handleClearSession"
        @open-sidebar="openMobileSidebar"
        @sign-out="$emit('sign-out')"
        @toggle-locale="$emit('toggle-locale')"
      />

      <section class="chat-screen">
        <div class="chat-copy">
          <p class="eyebrow">{{ dictionary.workspaceTitle }}</p>
          <h2>{{ dictionary.workspaceSubtitle }}</h2>
        </div>

        <div ref="scrollContainer" class="chat-scroll">
          <ChatMessageList
            :dictionary="dictionary"
            :has-messages="hasMessages"
            :messages="messages"
            @submit-follow-up="submitPrompt"
            @toggle-thinking="toggleThinking"
          />
        </div>

        <ChatInput
          ref="composerPanel"
          v-model="promptInput"
          :dictionary="dictionary"
          :is-sending="isSending"
          :request-error="requestError"
          :toast-message="toastMessage"
          @submit="submitPrompt"
        />
      </section>
    </main>
  </div>
</template>
