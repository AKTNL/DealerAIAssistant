<script setup>
defineProps({
  activeSessionLabel: {
    type: String,
    required: true
  },
  dictionary: {
    type: Object,
    required: true
  },
  showMobileSidebar: {
    type: Boolean,
    default: false
  }
});

defineEmits(["close", "new-chat"]);
</script>

<template>
  <aside :class="['sidebar', { 'sidebar-open': showMobileSidebar }]">
    <div class="sidebar-top">
      <div>
        <p class="eyebrow">{{ dictionary.appName }}</p>
        <h1 class="sidebar-title">{{ dictionary.workspaceTitle }}</h1>
      </div>

      <button class="ghost-button mobile-only" type="button" @click="$emit('close')">
        {{ dictionary.closeMenu }}
      </button>
    </div>

    <button class="primary-sidebar-button" type="button" @click="$emit('new-chat')">
      {{ dictionary.newChat }}
    </button>

    <section class="sidebar-section">
      <div class="section-head">
        <span>{{ dictionary.historySection }}</span>
      </div>

      <div class="session-card">
        <strong>{{ activeSessionLabel }}</strong>
        <p>{{ dictionary.historyHint }}</p>
      </div>
    </section>

    <div class="sidebar-footer">
      <p>{{ dictionary.footerNote }}</p>
    </div>
  </aside>
</template>
