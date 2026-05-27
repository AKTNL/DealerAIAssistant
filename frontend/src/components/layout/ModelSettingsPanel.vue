<script setup>
import { reactive, ref, watch } from "vue";

const props = defineProps({
  connectionMessage: {
    type: String,
    default: ""
  },
  connectionStatus: {
    type: String,
    default: ""
  },
  dictionary: {
    type: Object,
    required: true
  },
  modelSettings: {
    type: Object,
    required: true
  },
  isTestingConnection: {
    type: Boolean,
    default: false
  },
  open: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(["cancel", "reset", "save", "test-connection"]);

const draft = reactive(createDraft(props.modelSettings));
const apiKeyInput = ref("");
const apiKeyDirty = ref(false);

watch(
  () => [props.modelSettings, props.open],
  ([modelSettings, open]) => {
    if (!open) {
      return;
    }

    syncDraft(modelSettings);
  },
  { deep: true, immediate: true }
);

function createDraft(modelSettings) {
  return {
    apiKey: modelSettings?.apiKey ?? "",
    baseUrl: modelSettings?.baseUrl ?? "",
    model: modelSettings?.model ?? ""
  };
}

function syncDraft(modelSettings) {
  const nextDraft = createDraft(modelSettings);

  Object.assign(draft, nextDraft);
  apiKeyInput.value = maskApiKey(nextDraft.apiKey);
  apiKeyDirty.value = false;
}

function maskApiKey(value) {
  if (!value) {
    return "";
  }

  const suffix = value.length > 4 ? value.slice(-4) : "";
  return `****${suffix}`;
}

function handleApiKeyFocus() {
  if (!apiKeyDirty.value && draft.apiKey) {
    apiKeyInput.value = "";
  }
}

function handleApiKeyBlur() {
  if (!apiKeyDirty.value) {
    apiKeyInput.value = maskApiKey(draft.apiKey);
  }
}

function handleApiKeyInput(event) {
  apiKeyDirty.value = true;
  apiKeyInput.value = event.target.value;
  draft.apiKey = event.target.value;
}

function emitDraft(eventName) {
  emit(eventName, {
    apiKey: draft.apiKey,
    baseUrl: draft.baseUrl,
    model: draft.model
  });
}
</script>

<template>
  <div v-if="open" class="model-settings-shell">
    <div class="model-settings-backdrop" @click="$emit('cancel')"></div>

    <aside data-testid="model-settings-panel" class="model-settings-panel">
      <div class="model-settings-header">
        <div>
          <p class="model-settings-eyebrow">{{ dictionary.settingsButton }}</p>
          <h3>{{ dictionary.modelSettingsTitle }}</h3>
        </div>

        <button class="ghost-button cancel-button" type="button" @click="$emit('cancel')">
          {{ dictionary.cancelButton }}
        </button>
      </div>

      <p class="model-settings-description">
        {{ dictionary.modelSettingsDescription }}
      </p>

      <p
        v-if="connectionMessage"
        :class="['model-settings-feedback', `is-${connectionStatus || 'info'}`]"
      >
        {{ connectionMessage }}
      </p>

      <label class="model-settings-field">
        <span>{{ dictionary.modelSettingsBaseUrlLabel }}</span>
        <input
          v-model="draft.baseUrl"
          name="baseUrl"
          type="text"
        />
      </label>

      <label class="model-settings-field">
        <span>{{ dictionary.modelSettingsApiKeyLabel }}</span>
        <input
          :value="apiKeyInput"
          autocomplete="off"
          name="apiKey"
          type="text"
          @blur="handleApiKeyBlur"
          @focus="handleApiKeyFocus"
          @input="handleApiKeyInput"
        />
      </label>

      <label class="model-settings-field">
        <span>{{ dictionary.modelSettingsModelLabel }}</span>
        <input
          v-model="draft.model"
          name="model"
          type="text"
        />
      </label>

      <div class="model-settings-actions">
        <button class="ghost-button reset-button" type="button" @click="$emit('reset')">
          {{ dictionary.modelSettingsResetButton }}
        </button>
        <button
          class="ghost-button test-connection-button"
          type="button"
          :disabled="isTestingConnection"
          @click="emitDraft('test-connection')"
        >
          {{ dictionary.modelSettingsTestButton }}
        </button>
        <button class="primary-button save-button" type="button" @click="emitDraft('save')">
          {{ dictionary.modelSettingsSaveButton }}
        </button>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.model-settings-shell {
  inset: 0;
  pointer-events: none;
  position: fixed;
  z-index: 40;
}

.model-settings-backdrop {
  background: rgba(15, 23, 42, 0.28);
  inset: 0;
  pointer-events: auto;
  position: absolute;
}

.model-settings-panel {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 244, 236, 0.98));
  border: 1px solid rgba(120, 132, 158, 0.18);
  border-radius: 24px;
  box-shadow: 0 28px 60px rgba(15, 23, 42, 0.18);
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1.5rem;
  max-width: 32rem;
  padding: 1.5rem;
  pointer-events: auto;
  position: absolute;
  right: 0;
  top: 5.5rem;
  width: calc(100% - 3rem);
}

.model-settings-header {
  align-items: start;
  display: flex;
  gap: 1rem;
  justify-content: space-between;
}

.model-settings-header h3 {
  color: #112033;
  font-size: 1.3rem;
  margin: 0.2rem 0 0;
}

.model-settings-eyebrow {
  color: #7e5b2f;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  margin: 0;
  text-transform: uppercase;
}

.model-settings-description {
  color: #4e5b6d;
  line-height: 1.5;
  margin: 0;
}

.model-settings-feedback {
  border-radius: 14px;
  font-size: 0.92rem;
  line-height: 1.45;
  margin: 0;
  padding: 0.75rem 0.9rem;
}

.model-settings-feedback.is-error {
  background: rgba(190, 24, 39, 0.08);
  color: #991b1b;
}

.model-settings-feedback.is-info {
  background: rgba(14, 116, 144, 0.08);
  color: #155e75;
}

.model-settings-feedback.is-success {
  background: rgba(22, 163, 74, 0.1);
  color: #166534;
}

.model-settings-field {
  color: #112033;
  display: flex;
  flex-direction: column;
  font-size: 0.92rem;
  font-weight: 600;
  gap: 0.45rem;
}

.model-settings-field input {
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(120, 132, 158, 0.24);
  border-radius: 14px;
  color: #112033;
  font: inherit;
  min-width: 0;
  padding: 0.85rem 0.95rem;
}

.model-settings-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  justify-content: flex-end;
  margin-top: 0.25rem;
}

@media (max-width: 720px) {
  .model-settings-panel {
    left: 0;
    margin: 1rem;
    max-width: none;
    top: 4.75rem;
    width: auto;
  }

  .model-settings-header {
    flex-direction: column;
  }

  .model-settings-actions {
    justify-content: stretch;
  }

  .model-settings-actions button {
    width: 100%;
  }
}
</style>
