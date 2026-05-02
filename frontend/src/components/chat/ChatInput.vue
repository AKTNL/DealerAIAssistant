<script setup>
import { nextTick, onMounted, ref, watch } from "vue";

const props = defineProps({
  dictionary: {
    type: Object,
    required: true
  },
  isSending: {
    type: Boolean,
    default: false
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

const emit = defineEmits(["submit", "update:modelValue"]);
const composer = ref(null);

function resizeComposer(target = composer.value) {
  if (!target) {
    return;
  }

  target.style.height = "auto";
  target.style.height = `${Math.min(target.scrollHeight, 200)}px`;
}

function focusComposer() {
  composer.value?.focus();
}

function handleInput(event) {
  emit("update:modelValue", event.target.value);
  resizeComposer(event.target);
}

function handleKeydown(event) {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    emit("submit");
  }
}

watch(
  () => props.modelValue,
  () => {
    nextTick(() => resizeComposer());
  }
);

onMounted(() => {
  nextTick(() => resizeComposer());
});

defineExpose({
  focusComposer,
  resizeComposer
});
</script>

<template>
  <div class="composer-shell">
    <p v-if="requestError" class="error-text">{{ requestError }}</p>
    <p v-if="toastMessage" class="toast-text">{{ toastMessage }}</p>

    <div class="composer-card">
      <textarea
        ref="composer"
        class="composer-input"
        :placeholder="dictionary.inputPlaceholder"
        :value="modelValue"
        rows="1"
        @input="handleInput"
        @keydown="handleKeydown"
      ></textarea>

      <button
        class="primary-button send-button"
        type="button"
        :disabled="isSending || !modelValue.trim()"
        @click="$emit('submit')"
      >
        {{ isSending ? dictionary.sending : dictionary.sendButton }}
      </button>
    </div>
  </div>
</template>
