<script setup>
import { computed, onMounted, ref } from "vue";
import AdminView from "../components/admin/AdminView.vue";
import DashboardView from "../components/dashboard/DashboardView.vue";
import ChatInput from "../components/chat/ChatInput.vue";
import ChatMessageList from "../components/chat/ChatMessageList.vue";
import { testModelConnection } from "../api/modelConfig";
import { getDataStatus } from "../api/dataStatus";
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
import { useDashboard } from "../composables/useDashboard";
import { ADMIN_READ_PERMISSIONS } from "../constants/permissionCatalog";

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
  },
  currentUser: {
    type: Object,
    default: () => ({
      permissions: []
    })
  }
});

const emit = defineEmits(["sign-out", "toggle-locale"]);

const authVerified = computed(() => props.authVerified);
const permissions = computed(() => new Set(props.currentUser?.permissions ?? []));
const canDashboard = computed(() => permissions.value.has("DASHBOARD_READ"));
const canReadData = computed(() => permissions.value.has("DATA_READ"));
const canUseChat = computed(() => permissions.value.has("CHAT_USE"));
const canConfigureModel = computed(() => permissions.value.has("MODEL_CONFIG_TEST"));
const canOpenAdministration = computed(() => ADMIN_READ_PERMISSIONS.some(
  (permission) => permissions.value.has(permission)
));
const connectionMessage = ref("");
const connectionStatus = ref("");
const fallbackDataActive = ref(false);
const activeWorkspace = ref(defaultWorkspace());
const isTestingConnection = ref(false);
const modelSettingsPanelOpen = ref(false);
const sidebarCollapsed = ref(true);
const savedModelSettings = ref(readModelSettings() ?? createEmptyModelSettings());
const {
  closeMobileSidebar,
  handleClearSession,
  handleScroll,
  hasUnreadContent,
  isSending,
  jumpToLatest,
  messages,
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
const {
  dashboard,
  dashboardError,
  dashboardLoading,
  loadDashboard
} = useDashboard({
  onAuthExpired: () => emit("sign-out"),
  enabled: canDashboard
});

onMounted(() => {
  if (canReadData.value) {
    loadDataStatus();
  }
});

function defaultWorkspace() {
  if (canDashboard.value) {
    return "dashboard";
  }
  if (canUseChat.value) {
    return "chat";
  }
  return canOpenAdministration.value ? "admin" : "";
}

async function loadDataStatus() {
  try {
    const status = await getDataStatus();
    fallbackDataActive.value = status.fallbackActive;
  } catch (error) {
    fallbackDataActive.value = false;
    if (error?.status === 401) {
      emit("sign-out");
    }
  }
}

function handleSelectSidebarPrompt(prompt) {
  activeWorkspace.value = "chat";
  promptInput.value = prompt;
  closeMobileSidebar();
}

function handleStartNewChat() {
  activeWorkspace.value = "chat";
  startNewChat();
}

function handleDashboardAnalyze(prompt) {
  if (!canUseChat.value) {
    return;
  }
  activeWorkspace.value = "chat";
  submitPrompt(prompt);
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
    <button v-if="canUseChat" class="sidebar-expand-tab" type="button" :title="dictionary.newChat" @click="sidebarCollapsed = false">
      <span class="material-icons">add_comment</span>
    </button>

    <ExampleSidebar
      v-if="canUseChat"
      :dictionary="dictionary"
      :is-sending="isSending"
      :show-mobile-sidebar="showMobileSidebar"
      @close="closeMobileSidebar"
      @select-prompt="handleSelectSidebarPrompt"
      @new-chat="handleStartNewChat"
      @toggle-sidebar="sidebarCollapsed = !sidebarCollapsed"
    />

    <div v-if="showMobileSidebar" class="sidebar-backdrop" @click="closeMobileSidebar"></div>

    <main class="main-panel workspace-stage">
      <TopNav
        :auth-verified="props.authVerified"
        :can-configure-model="canConfigureModel"
        :can-use-chat="canUseChat"
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

      <div v-if="fallbackDataActive" class="data-source-warning" role="status" aria-live="polite">
        <span class="material-icons" aria-hidden="true">warning_amber</span>
        <span>{{ dictionary.sampleDataWarning }}</span>
      </div>

      <ModelSettingsPanel
        v-if="canConfigureModel"
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

      <div class="workspace-mode-tabs" role="tablist" :aria-label="dictionary.workspaceTitle">
        <button
          v-if="canDashboard"
          :class="['workspace-mode-tab', { active: activeWorkspace === 'dashboard' }]"
          type="button"
          role="tab"
          :aria-selected="activeWorkspace === 'dashboard'"
          @click="activeWorkspace = 'dashboard'"
        >
          <span class="material-icons" aria-hidden="true">dashboard</span>
          <span>{{ dictionary.dashboardTab }}</span>
        </button>
        <button
          v-if="canUseChat"
          :class="['workspace-mode-tab', { active: activeWorkspace === 'chat' }]"
          type="button"
          role="tab"
          :aria-selected="activeWorkspace === 'chat'"
          @click="activeWorkspace = 'chat'"
        >
          <span class="material-icons" aria-hidden="true">forum</span>
          <span>{{ dictionary.chatTab }}</span>
        </button>
        <button
          v-if="canOpenAdministration"
          :class="['workspace-mode-tab', { active: activeWorkspace === 'admin' }]"
          type="button"
          role="tab"
          :aria-selected="activeWorkspace === 'admin'"
          @click="activeWorkspace = 'admin'"
        >
          <span class="material-icons" aria-hidden="true">admin_panel_settings</span>
          <span>{{ dictionary.adminTab }}</span>
        </button>
      </div>

      <DashboardView
        v-if="canDashboard && activeWorkspace === 'dashboard'"
        :dashboard="dashboard"
        :dictionary="dictionary"
        :error="dashboardError"
        :is-sending="isSending"
        :loading="dashboardLoading"
        :locale="locale"
        @analyze="handleDashboardAnalyze"
        @reload="loadDashboard"
      />

      <AdminView
        v-else-if="canOpenAdministration && activeWorkspace === 'admin'"
        :current-user="currentUser"
        :dictionary="dictionary"
        :locale="locale"
        @sign-out="emit('sign-out')"
      />

      <section v-else-if="canUseChat" class="chat-screen">
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
