<script setup>
import { ref } from "vue";

const props = defineProps({
  dictionary: {
    type: Object,
    required: true
  },
  isSending: {
    type: Boolean,
    default: false
  },
  showMobileSidebar: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(["close", "new-chat", "select-prompt", "toggle-sidebar"]);

const expandedModuleId = ref(null);

function getModuleId(module, moduleIndex) {
  return module.id ?? `module-${moduleIndex}`;
}

function toggleModule(module, moduleIndex) {
  const moduleId = getModuleId(module, moduleIndex);
  expandedModuleId.value = expandedModuleId.value === moduleId ? null : moduleId;
}

function handleQuestionClick(question) {
  if (props.isSending) {
    return;
  }

  emit("select-prompt", question);
}
</script>

<template>
  <aside
    :class="['sidebar', 'sidebar-editorial', 'sidebar-rail', { 'sidebar-open': showMobileSidebar }]"
  >
    <div class="sidebar-top">
      <div>
        <p class="eyebrow">{{ dictionary.appName }}</p>
        <h1 class="sidebar-title">{{ dictionary.workspaceTitle }}</h1>
      </div>

      <div class="sidebar-top-actions">
        <button class="sidebar-toggle-btn" type="button" :title="dictionary.closeMenu" @click="$emit('toggle-sidebar')">
          <span class="material-icons sidebar-toggle-icon">menu_open</span>
        </button>
        <button class="ghost-button mobile-only" type="button" @click="$emit('close')">
          {{ dictionary.closeMenu }}
        </button>
      </div>
    </div>

    <div class="sidebar-action-block">
      <button class="primary-sidebar-button" type="button" @click="$emit('new-chat')">
        <span class="material-icons sidebar-btn-icon">add_comment</span>
        {{ dictionary.newChat }}
      </button>
    </div>

    <div class="sidebar-group-list">
      <section v-if="dictionary.promptModules?.length" class="sidebar-section">
        <div class="section-head">
          <span>{{ dictionary.sidebarSection }}</span>
        </div>

        <div
          v-for="(module, moduleIndex) in dictionary.promptModules"
          :key="getModuleId(module, moduleIndex)"
          class="sidebar-module-card"
        >
          <button
            class="sidebar-module-toggle"
            type="button"
            :aria-expanded="String(expandedModuleId === getModuleId(module, moduleIndex))"
            @click="toggleModule(module, moduleIndex)"
          >
            <span>{{ module.title }}</span>
            <span
              :class="[
                'material-icons',
                'sidebar-module-chevron',
                { open: expandedModuleId === getModuleId(module, moduleIndex) }
              ]"
              aria-hidden="true"
            >
              expand_more
            </span>
          </button>

          <div
            v-if="expandedModuleId === getModuleId(module, moduleIndex)"
            class="sidebar-module-questions"
          >
            <button
              v-for="(question, questionIndex) in module.questions"
              :key="questionIndex"
              class="prompt-card sidebar-module-question"
              type="button"
              :disabled="isSending"
              @click="handleQuestionClick(question)"
            >
              <span>{{ question }}</span>
            </button>
          </div>
        </div>
      </section>

    </div>

    <div class="sidebar-footer">
      <p>{{ dictionary.footerNote }}</p>
    </div>
  </aside>
</template>
