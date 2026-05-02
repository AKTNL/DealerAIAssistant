import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { clearSession, streamChat } from "../api/chat";
import { STORAGE_KEYS } from "../constants/storageKeys";
import { createSessionId, normalizeAssistantPayload } from "../utils/chat";
import { renderMarkdownLite } from "../utils/markdown";
import { readStorageValue, writeStorageValue } from "../utils/storage";

export function useChat({ authVerified, dictionary, locale }) {
  const promptInput = ref("");
  const isSending = ref(false);
  const requestError = ref("");
  const statusMessage = ref("");
  const toastMessage = ref("");
  const showMobileSidebar = ref(false);
  const scrollContainer = ref(null);
  const currentAbortController = ref(null);
  const sessionId = ref(readStorageValue("session", STORAGE_KEYS.session, createSessionId()));
  const messages = ref([]);
  const hasMessages = computed(() => messages.value.length > 0);
  const activeSessionLabel = computed(() => `${dictionary.value.historyLabel} - ${sessionId.value.slice(0, 8)}`);

  let toastTimerId = 0;

  watch(sessionId, (value) => {
    writeStorageValue("session", STORAGE_KEYS.session, value);
  });

  watch(
    () => messages.value.length,
    () => {
      scrollToBottom();
    }
  );

  watch([dictionary, authVerified], () => {
    syncStatus();
  });

  onMounted(() => {
    writeStorageValue("session", STORAGE_KEYS.session, sessionId.value);
    syncStatus();
  });

  onBeforeUnmount(() => {
    currentAbortController.value?.abort();

    if (typeof window !== "undefined") {
      window.clearTimeout(toastTimerId);
    }
  });

  function syncStatus() {
    if (isSending.value) {
      statusMessage.value = dictionary.value.statusThinking;
      return;
    }

    statusMessage.value = authVerified.value
      ? dictionary.value.statusReady
      : dictionary.value.statusGuest;
  }

  async function scrollToBottom() {
    await nextTick();

    if (scrollContainer.value) {
      scrollContainer.value.scrollTop = scrollContainer.value.scrollHeight;
    }
  }

  function openMobileSidebar() {
    showMobileSidebar.value = true;
  }

  function closeMobileSidebar() {
    showMobileSidebar.value = false;
  }

  function createUserMessage(content) {
    return {
      id: `user-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
      role: "user",
      content,
      html: renderMarkdownLite(content),
      followUps: [],
      thinking: "",
      thinkingExpanded: false,
      status: ""
    };
  }

  function createAssistantMessage() {
    return {
      id: `assistant-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
      role: "assistant",
      content: "",
      html: "",
      followUps: [],
      thinking: "",
      thinkingHtml: "",
      thinkingExpanded: false,
      status: dictionary.value.statusThinking
    };
  }

  function pushToast(text) {
    toastMessage.value = text;

    if (typeof window === "undefined") {
      return;
    }

    window.clearTimeout(toastTimerId);
    toastTimerId = window.setTimeout(() => {
      toastMessage.value = "";
    }, 2800);
  }

  function resetChatSession({ keepSessionId = false } = {}) {
    currentAbortController.value?.abort();
    currentAbortController.value = null;
    isSending.value = false;
    requestError.value = "";
    messages.value = [];

    if (!keepSessionId) {
      sessionId.value = createSessionId();
    }

    syncStatus();
  }

  async function handleClearSession() {
    if (isSending.value) {
      currentAbortController.value?.abort();
    }

    requestError.value = "";
    statusMessage.value = dictionary.value.clearBusy;

    try {
      await clearSession(sessionId.value);
      resetChatSession();
      pushToast(dictionary.value.clearSuccess);
    } catch (error) {
      requestError.value = error.message || dictionary.value.clearError;
      syncStatus();
    }
  }

  function startNewChat() {
    resetChatSession();
    promptInput.value = "";
    closeMobileSidebar();
  }

  function fillPrompt(prompt) {
    promptInput.value = prompt;
    closeMobileSidebar();
  }

  function toggleThinking(message) {
    message.thinkingExpanded = !message.thinkingExpanded;
  }

  async function submitPrompt(promptOverride = "") {
    const text = (promptOverride || promptInput.value).trim();

    if (!text || isSending.value || !authVerified.value) {
      return;
    }

    requestError.value = "";
    closeMobileSidebar();

    const userMessage = createUserMessage(text);
    const assistantMessage = createAssistantMessage();

    messages.value.push(userMessage, assistantMessage);
    promptInput.value = "";
    isSending.value = true;
    syncStatus();

    const controller = new AbortController();
    currentAbortController.value = controller;

    try {
      await streamChat({
        sessionId: sessionId.value,
        message: text,
        signal: controller.signal,
        onEvent: ({ event, data }) => {
          if (event === "progress") {
            assistantMessage.status = data;
            scrollToBottom();
            return;
          }

          if (event === "message") {
            if (data.startsWith("<think>") && data.endsWith("</think>")) {
              const thinking = data.slice(7, -8).trim();
              assistantMessage.thinking = thinking;
              assistantMessage.thinkingHtml = renderMarkdownLite(thinking);
              scrollToBottom();
              return;
            }

            const normalized = normalizeAssistantPayload(data);
            assistantMessage.content = normalized.content;
            assistantMessage.followUps = normalized.followUps;
            assistantMessage.html = renderMarkdownLite(normalized.content);
            assistantMessage.status = "";
            scrollToBottom();
            return;
          }

          if (event === "done" || data === "[DONE]") {
            assistantMessage.status = "";
            scrollToBottom();
          }
        }
      });
    } catch (error) {
      if (error.name === "AbortError") {
        requestError.value = locale.value === "zh" ? "本次请求已中断。" : "The request was interrupted.";
      } else {
        requestError.value = error.message || "Request failed";
      }
    } finally {
      currentAbortController.value = null;
      isSending.value = false;
      syncStatus();
    }
  }

  return {
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
  };
}
