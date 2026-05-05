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
  const sessionId = ref(readStorageValue("local", STORAGE_KEYS.session, createSessionId()));
  const messages = ref([]);
  const hasMessages = computed(() => messages.value.length > 0);
  const activeSessionLabel = computed(() => `${dictionary.value.historyLabel} - ${sessionId.value.slice(0, 8)}`);

  let toastTimerId = 0;

  watch(sessionId, (value) => {
    writeStorageValue("local", STORAGE_KEYS.session, value);
  });

  watch(
    () => messages.value.length,
    () => {
      scrollToBottom();
    }
  );

  watch(dictionary, () => {
    syncStatus();
    refreshWelcomeMessage();
  });

  watch(authVerified, () => {
    syncStatus();
  });

  onMounted(() => {
    writeStorageValue("local", STORAGE_KEYS.session, sessionId.value);

    if (!messages.value.length) {
      messages.value = [createWelcomeMessage()];
    }

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
      kind: "user",
      role: "user",
      content,
      html: renderMarkdownLite(content),
      followUps: [],
      thinking: "",
      thinkingExpanded: false,
      status: "",
      steps: [],
      streaming: false
    };
  }

  function createAssistantMessage(kind = "assistant") {
    return {
      id: `assistant-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
      kind,
      role: "assistant",
      content: "",
      html: "",
      followUps: [],
      thinking: "",
      thinkingHtml: "",
      thinkingExpanded: false,
      status: kind === "welcome" ? "" : dictionary.value.statusThinking,
      steps: [],
      streaming: kind !== "welcome"
    };
  }

  function createWelcomeMessage() {
    const welcomeMessage = createAssistantMessage("welcome");
    applyAssistantPayload(welcomeMessage, buildWelcomeReply());
    welcomeMessage.streaming = false;
    return welcomeMessage;
  }

  function buildWelcomeReply() {
    const [promptOne = "", promptTwo = ""] = dictionary.value.prompts ?? [];

    if (locale.value === "zh") {
      return `
## ${dictionary.value.welcomeTitle}

- ${dictionary.value.welcomeBody}
- ${dictionary.value.welcomeHint}

FOLLOW_UP_QUESTIONS:
1. ${promptOne}
2. ${promptTwo}
      `.trim();
    }

    return `
## ${dictionary.value.welcomeTitle}

- ${dictionary.value.welcomeBody}
- ${dictionary.value.welcomeHint}

FOLLOW_UP_QUESTIONS:
1. ${promptOne}
2. ${promptTwo}
    `.trim();
  }

  function refreshWelcomeMessage() {
    if (messages.value.length === 1 && messages.value[0].kind === "welcome") {
      messages.value = [createWelcomeMessage()];
    }
  }

  function applyAssistantPayload(message, rawText) {
    const normalized = normalizeAssistantPayload(rawText);
    message.content = normalized.content;
    message.followUps = normalized.followUps;
    message.html = renderMarkdownLite(normalized.content);
    message.status = message.streaming ? message.status : "";
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
    messages.value = [createWelcomeMessage()];

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

  function toggleThinking(message) {
    message.thinkingExpanded = !message.thinkingExpanded;
  }

  async function submitPrompt(promptOverride = "") {
    const overrideText = promptOverride.trim();
    const text = (overrideText || promptInput.value).trim();

    if (!text || isSending.value || !authVerified.value) {
      return;
    }

    requestError.value = "";
    closeMobileSidebar();

    const userMessage = createUserMessage(text);
    const assistantMessage = createAssistantMessage();

    messages.value = messages.value.filter((message) => message.kind !== "welcome");
    messages.value.push(userMessage, assistantMessage);

    if (!overrideText) {
      promptInput.value = "";
    }

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
          const eventText = normalizeEventText(data);

          if (event === "progress") {
            if (eventText && assistantMessage.steps.at(-1) !== eventText) {
              assistantMessage.steps.push(eventText);
            }
            assistantMessage.status = eventText || dictionary.value.statusThinking;
            scrollToBottom();
            return;
          }

          if (event === "message") {
            if (eventText.startsWith("<think>") && eventText.endsWith("</think>")) {
              const thinking = eventText.slice(7, -8).trim();
              assistantMessage.thinking = thinking;
              assistantMessage.thinkingHtml = renderMarkdownLite(thinking);
              scrollToBottom();
              return;
            }

            applyAssistantPayload(assistantMessage, eventText);
            assistantMessage.status = "";
            scrollToBottom();
            return;
          }

          if (event === "done" || eventText === "[DONE]") {
            assistantMessage.status = "";
            assistantMessage.streaming = false;
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
      assistantMessage.streaming = false;
      currentAbortController.value = null;
      isSending.value = false;
      syncStatus();
    }
  }

  return {
    activeSessionLabel,
    closeMobileSidebar,
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

function normalizeEventText(value) {
  if (typeof value === "string") {
    return value;
  }

  if (value == null) {
    return "";
  }

  if (typeof value === "object") {
    return value.data ?? value.content ?? value.message ?? value.text ?? JSON.stringify(value);
  }

  return String(value);
}
