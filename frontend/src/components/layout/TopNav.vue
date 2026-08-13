<script setup>
defineProps({
  authVerified: {
    type: Boolean,
    default: true
  },
  canConfigureModel: {
    type: Boolean,
    default: false
  },
  canUseChat: {
    type: Boolean,
    default: false
  },
  currentTenant: {
    type: Object,
    default: null
  },
  dictionary: {
    type: Object,
    required: true
  },
  isSending: {
    type: Boolean,
    default: false
  },
  locale: {
    type: String,
    required: true
  },
  statusMessage: {
    type: String,
    default: ""
  },
  streamPhase: {
    type: String,
    default: "idle"
  },
  tenants: {
    type: Array,
    default: () => []
  }
});

defineEmits(["clear-session", "open-settings", "select-tenant", "sign-out", "toggle-locale"]);
</script>

<template>
  <header class="topbar topbar-product">
    <div class="topbar-left topbar-identity">
      <div class="topbar-brand">
        <div class="topbar-logo-badge">
          <img src="/logo.png" :alt="dictionary.appName" class="topbar-logo-image" />
        </div>

        <div class="topbar-title-group">
          <h2>{{ dictionary.appName }}</h2>
        </div>

        <div class="spring-ai-badge">
          <span class="spring-ai-badge-dot"></span>
          Spring AI
        </div>
      </div>
    </div>

    <div class="topbar-actions topbar-tools">
      <label v-if="tenants.length" class="tenant-selector" :title="dictionary.tenantSelectorLabel">
        <span class="material-icons" aria-hidden="true">domain</span>
        <select
          :aria-label="dictionary.tenantSelectorLabel"
          :value="currentTenant?.key ?? ''"
          @change="$emit('select-tenant', $event.target.value)"
        >
          <option value="" disabled>{{ dictionary.tenantSelectorPlaceholder }}</option>
          <option v-for="tenant in tenants" :key="tenant.id" :value="tenant.key">
            {{ tenant.displayName }}
          </option>
        </select>
      </label>

      <span v-if="statusMessage || streamPhase !== 'idle'" class="status-pill">
        {{ streamPhase === 'thinking' ? dictionary.streamPhaseThinking : streamPhase === 'generating' ? dictionary.streamPhaseGenerating : statusMessage }}
      </span>

      <button
        class="topbar-icon-btn"
        type="button"
        :title="dictionary.switchLanguage"
        @click="$emit('toggle-locale')"
      >
        <span class="material-icons topbar-material-icon">translate</span>
      </button>

      <button
        v-if="authVerified && canConfigureModel"
        class="topbar-icon-btn settings-button"
        type="button"
        :title="dictionary.settingsButton"
        @click="$emit('open-settings')"
      >
        <span class="material-icons topbar-material-icon">settings</span>
      </button>

      <button
        v-if="authVerified && canUseChat"
        class="topbar-icon-btn"
        type="button"
        :title="dictionary.clearChat"
        :disabled="isSending"
        @click="$emit('clear-session')"
      >
        <span class="material-icons topbar-material-icon">delete_outline</span>
      </button>

      <button
        v-if="authVerified"
        class="topbar-icon-btn"
        type="button"
        :title="dictionary.logoutButton"
        @click="$emit('sign-out')"
      >
        <span class="material-icons topbar-material-icon">logout</span>
      </button>
    </div>
  </header>
</template>
