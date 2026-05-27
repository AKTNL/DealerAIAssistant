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
  locale: {
    type: String,
    default: "zh"
  },
  modelValue: {
    type: String,
    default: ""
  },
  requestError: {
    type: String,
    default: ""
  },
  toastMessage: {
    type: String,
    default: ""
  }
});

const emit = defineEmits(["submit", "update:modelValue", "stop"]);
const composer = ref(null);

function focusComposer() {
  composer.value?.focus();
}

function handleInput(event) {
  emit("update:modelValue", event.target.value);
  autoResize(event.target);
}

function autoResize(el) {
  el.style.height = "auto";
  const maxHeight = 200; // matches CSS max-height on .composer-input
  el.style.height = Math.min(el.scrollHeight, maxHeight) + "px";
}

function handleKeydown(event) {
  if (
    event.key === "Enter" &&
    !event.isComposing &&
    !event.shiftKey &&
    !props.isSending &&
    props.modelValue.trim()
  ) {
    event.preventDefault();
    emit("submit");
  }
}

defineExpose({
  focusComposer
});
</script>

<template>
  <div class="composer-shell composer-dock">
    <p v-if="requestError" class="error-text">{{ requestError }}</p>
    <p v-if="toastMessage" class="toast-text">{{ toastMessage }}</p>

    <div class="composer-card composer-card-editorial" style="position: relative; align-items: center;">
      <div style="position: relative; flex: 1;">
        <textarea
          ref="composer"
          class="composer-input"
          :disabled="isSending"
          :placeholder="isSending
            ? (locale === 'zh' ? 'AI 正在分析中，您可以提前输入下个问题...' : 'AI is analyzing, draft your next question...')
            : dictionary.inputPlaceholder"
          :aria-label="dictionary.chatInputLabel ?? dictionary.inputPlaceholder"
          :value="modelValue"
          rows="1"
          maxlength="500"
          @input="handleInput"
          @keydown="handleKeydown"
        ></textarea>
        <span class="char-counter">{{ modelValue.length }}/500</span>
      </div>

      <button
        v-if="isSending"
        class="stop-button"
        type="button"
        :title="locale === 'zh' ? '停止生成' : 'Stop generating'"
        @click="$emit('stop')"
      >
        <svg class="w-5 h-5 transition-transform hover:scale-90" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M4 4h12v12H4V4z" clip-rule="evenodd"></path>
        </svg>
      </button>

      <button
        v-else
        class="primary-button send-button"
        type="button"
        :disabled="!modelValue.trim()"
        :title="locale === 'zh' ? '发送' : 'Send'"
        @click="$emit('submit')"
      >
        <span class="material-icons">send</span>
      </button>
    </div>
  </div>
</template>
