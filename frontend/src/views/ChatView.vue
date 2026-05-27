<script setup>
import { computed, ref } from "vue";
import ChatInput from "../components/chat/ChatInput.vue";
import ChatMessageList from "../components/chat/ChatMessageList.vue";
import { testModelConnection } from "../api/modelConfig";
import ExampleSidebar from "../components/layout/ExampleSidebar.vue";
import ModelSettingsPanel from "../components/layout/ModelSettingsPanel.vue";
import TopNav from "../components/layout/TopNav.vue";
import { getModelErrorMessage } from "../utils/modelErrors";
import {
  isModelSettingsComplete,
  normalizeModelSettings,
  readModelSettings,
  resetModelSettings,
  writeModelSettings
} from "../composables/useModelSettings";
import { useChat } from "../composables/useChat";

const props = defineProps({
  dictionary: {
    type: Object,
    required: true
  },
  locale: {
    type: String,
    required: true
  },
  authVerified: {
    type: Boolean,
    required: true
  }
});

const emit = defineEmits(["sign-out", "toggle-locale"]);

const authVerified = computed(() => props.authVerified);
const connectionMessage = ref("");
const connectionStatus = ref("");
const isTestingConnection = ref(false);
const modelSettingsPanelOpen = ref(false);
const sidebarCollapsed = ref(false);
const savedModelSettings = ref(readModelSettings() ?? createEmptyModelSettings());
const {
  closeMobileSidebar,
  handleClearSession,
  handleScroll,
  hasUnreadContent,
  isSending,
  jumpToLatest,
  messages,
  openMobileSidebar,
  promptInput,
  requestError,
  scrollContainer,
  showMobileSidebar,
  startNewChat,
  statusMessage,
  streamPhase,
  stopGenerating,
  submitPrompt,
  toastMessage
} = useChat({
  authVerified,
  dictionary: computed(() => props.dictionary),
  locale: computed(() => props.locale),
  modelSettings: savedModelSettings,
  openModelSettings: handleOpenSettings,
  onAuthExpired: () => emit("sign-out")
});

async function handleSubmitSidebarPrompt(prompt) {
  closeMobileSidebar();
  await submitPrompt(prompt);
}

function createEmptyModelSettings() {
  return {
    apiKey: "",
    baseUrl: "",
    model: ""
  };
}

function handleOpenSettings() {
  connectionMessage.value = "";
  connectionStatus.value = "";
  modelSettingsPanelOpen.value = true;
}

function handleCancelSettings() {
  modelSettingsPanelOpen.value = false;
}

function handleSaveSettings(settings) {
  const normalized = normalizeModelSettings(settings);

  if (!normalized || !writeModelSettings(normalized)) {
    connectionMessage.value =
      props.dictionary.modelSettingsSaveError ?? "Save base URL, API key, and model before continuing.";
    connectionStatus.value = "error";
    return;
  }

  savedModelSettings.value = normalized;
  connectionMessage.value = "";
  connectionStatus.value = "";
  modelSettingsPanelOpen.value = false;
}

function handleResetSettings() {
  resetModelSettings();
  savedModelSettings.value = createEmptyModelSettings();
  connectionMessage.value = "";
  connectionStatus.value = "";
  modelSettingsPanelOpen.value = false;
}

async function handleTestConnection(settings) {
  const normalized = normalizeModelSettings(settings);

  if (!normalized || !isModelSettingsComplete(normalized)) {
    connectionMessage.value =
      props.dictionary.modelSettingsTestRequired ??
      "Save base URL, API key, and model before testing the connection.";
    connectionStatus.value = "error";
    return;
  }

  connectionMessage.value =
    props.dictionary.modelSettingsTestPending ?? "Testing model connection...";
  connectionStatus.value = "info";
  isTestingConnection.value = true;

  try {
    const result = await testModelConnection(normalized);
    const success = result?.success === true;
    connectionMessage.value = success
      ? (result?.message ?? "")
      : getModelErrorMessage(result?.message, props.dictionary, props.locale);
    connectionStatus.value = success ? "success" : "error";
  } catch (error) {
    if (error?.status === 401) {
      connectionMessage.value = props.dictionary.authExpired;
      connectionStatus.value = "error";
      emit("sign-out");
      return;
    }

    connectionMessage.value = getModelErrorMessage(error, props.dictionary, props.locale);
    connectionStatus.value = "error";
  } finally {
    isTestingConnection.value = false;
  }
}
</script>

<template>
  <div :class="['app-shell', 'workspace-shell', { 'sidebar-collapsed': sidebarCollapsed }]">
    <button class="sidebar-expand-tab" type="button" :title="dictionary.newChat" @click="sidebarCollapsed = false">
      <span class="material-icons">add_comment</span>
    </button>

    <ExampleSidebar
      :dictionary="dictionary"
      :is-sending="isSending"
      :show-mobile-sidebar="showMobileSidebar"
      @close="closeMobileSidebar"
      @submit-prompt="handleSubmitSidebarPrompt"
      @new-chat="startNewChat"
      @toggle-sidebar="sidebarCollapsed = !sidebarCollapsed"
    />

    <div v-if="showMobileSidebar" class="sidebar-backdrop" @click="closeMobileSidebar"></div>

    <main class="main-panel workspace-stage">
      <TopNav
        :auth-verified="props.authVerified"
        :dictionary="dictionary"
        :is-sending="isSending"
        :locale="locale"
        :status-message="statusMessage"
        :stream-phase="streamPhase"
        @clear-session="handleClearSession"
        @open-settings="handleOpenSettings"

        @sign-out="emit('sign-out')"
        @toggle-locale="emit('toggle-locale')"
      />

      <ModelSettingsPanel
        :connection-message="connectionMessage"
        :connection-status="connectionStatus"
        :dictionary="dictionary"
        :is-testing-connection="isTestingConnection"
        :model-settings="savedModelSettings"
        :open="modelSettingsPanelOpen"
        @cancel="handleCancelSettings"
        @reset="handleResetSettings"
        @save="handleSaveSettings"
        @test-connection="handleTestConnection"
      />

      <section class="chat-screen">
        <div ref="scrollContainer" class="chat-scroll" @scroll="handleScroll">
          <ChatMessageList
            :dictionary="dictionary"
            :locale="locale"
            :messages="messages"
            :stream-phase="streamPhase"
            @submit-follow-up="submitPrompt"
          />
        </div>

        <button
          v-if="hasUnreadContent"
          class="jump-latest-button"
          type="button"
          @click="jumpToLatest"
        >
          {{ dictionary.jumpToLatest }}
        </button>

        <ChatInput
          v-model="promptInput"
          :dictionary="dictionary"
          :is-sending="isSending"
          :locale="locale"
          :request-error="requestError"
          :toast-message="toastMessage"
          @submit="submitPrompt"
          @stop="stopGenerating"
        />
      </section>
    </main>
  </div>
</template>
