import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { clearSession, streamChat } from "../api/chat";
import { STORAGE_KEYS } from "../constants/storageKeys";
import { createSessionId, normalizeAssistantPayload } from "../utils/chat";
import { getModelErrorMessage } from "../utils/modelErrors";
import { renderMarkdownLite } from "../utils/markdown";
import { readStorageValue, writeStorageValue } from "../utils/storage";

const SCROLL_LOCK_THRESHOLD = 40;
const ASSISTANT_RENDER_DELAY_MS = 100;
const STREAM_PHASE = { IDLE: "idle", THINKING: "thinking", GENERATING: "generating" };

export function useChat({ authVerified, dictionary, locale, modelSettings, openModelSettings, onAuthExpired }) {
  const promptInput = ref("");
  const isSending = ref(false);
  const requestError = ref("");
  const statusMessage = ref("");
  const toastMessage = ref("");
  const showMobileSidebar = ref(false);
  const scrollContainer = ref(null);
  const hasUnreadContent = ref(false);
  const isPinnedToBottom = ref(true);
  const currentAbortController = ref(null);
  const sessionId = ref(readStorageValue("local", STORAGE_KEYS.session, createSessionId()));
  const messages = ref([createSystemMessage("welcome")]);
  const streamPhase = ref(STREAM_PHASE.IDLE);

  let assistantRenderMessage = null;
  let assistantRenderPending = false;
  let assistantRenderTimerId = 0;
  let toastTimerId = 0;

  watch(sessionId, (value) => {
    writeStorageValue("local", STORAGE_KEYS.session, value);
  });

  watch(
    () => messages.value.length,
    () => {
      syncViewport();
    }
  );

  watch(dictionary, () => {
    syncStatus();
  });

  watch(authVerified, () => {
    syncStatus();
  });

  onMounted(() => {
    writeStorageValue("local", STORAGE_KEYS.session, sessionId.value);
    syncStatus();
  });

  onBeforeUnmount(() => {
    currentAbortController.value?.abort();

    if (typeof window !== "undefined") {
      window.clearTimeout(assistantRenderTimerId);
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

  function isViewportPinned() {
    if (!scrollContainer.value) {
      return true;
    }

    const { clientHeight, scrollHeight, scrollTop } = scrollContainer.value;
    return scrollHeight - (scrollTop + clientHeight) <= SCROLL_LOCK_THRESHOLD;
  }

  function syncViewport({ markUnread = false } = {}) {
    if (isPinnedToBottom.value) {
      hasUnreadContent.value = false;
      scrollToBottom();
      return;
    }

    if (markUnread) {
      hasUnreadContent.value = true;
    }
  }

  function handleScroll() {
    isPinnedToBottom.value = isViewportPinned();

    if (isPinnedToBottom.value) {
      hasUnreadContent.value = false;
    }
  }

  async function jumpToLatest() {
    hasUnreadContent.value = false;
    isPinnedToBottom.value = true;
    await scrollToBottom();
  }

  function openMobileSidebar() {
    showMobileSidebar.value = true;
  }

  function closeMobileSidebar() {
    showMobileSidebar.value = false;
  }

  function createSystemMessage(type) {
    return {
      id: `${type}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      role: "system",
      type, // 'welcome' | 'system-clear'
      timestamp: Date.now()
    };
  }

  function createUserMessage(content) {
    return {
      id: `user-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
      kind: "user",
      role: "user",
      content,
      html: renderMarkdownLite(content),
      followUps: [],
      status: "",
      steps: [],
      streaming: false,
      rendered: false
    };
  }

  function createAssistantMessage() {
    return {
      id: `assistant-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
      kind: "assistant",
      role: "assistant",
      content: "",
      rawContent: "",
      html: "",
      followUps: [],
      status: dictionary.value.statusThinking,
      steps: [],
      streaming: true,
      rendered: false
    };
  }

  function clearAssistantRenderTimer() {
    if (typeof window !== "undefined" && assistantRenderTimerId) {
      window.clearTimeout(assistantRenderTimerId);
    }

    assistantRenderTimerId = 0;
  }

  function renderAssistantHtml(message) {
    message.html = renderMarkdownLite(message.content);
    syncViewport();
  }

  function flushAssistantRender(message = assistantRenderMessage) {
    const shouldRender = assistantRenderPending && message;

    clearAssistantRenderTimer();
    assistantRenderMessage = null;
    assistantRenderPending = false;

    if (shouldRender) {
      renderAssistantHtml(message);
    }
  }

  function scheduleAssistantRender(message) {
    assistantRenderMessage = message;
    assistantRenderPending = true;

    if (typeof window === "undefined") {
      flushAssistantRender(message);
      return;
    }

    if (assistantRenderTimerId) {
      return;
    }

    assistantRenderTimerId = window.setTimeout(() => {
      const pendingMessage = assistantRenderMessage;

      assistantRenderTimerId = 0;
      assistantRenderMessage = null;
      assistantRenderPending = false;

      if (pendingMessage) {
        renderAssistantHtml(pendingMessage);
      }
    }, ASSISTANT_RENDER_DELAY_MS);
  }

  function applyAssistantPayload(message, rawText, { renderHtml = true } = {}) {
    const normalized = normalizeAssistantPayload(rawText);
    message.rawContent = rawText;
    message.content = normalized.content;
    message.followUps = normalized.followUps;
    if (renderHtml) {
      renderAssistantHtml(message);
    }
    message.status = "";
  }

  function appendAssistantChunk(message, chunk) {
    applyAssistantPayload(message, `${message.rawContent}${chunk}`, { renderHtml: false });
    scheduleAssistantRender(message);
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
    flushAssistantRender();
    currentAbortController.value = null;
    isSending.value = false;
    hasUnreadContent.value = false;
    isPinnedToBottom.value = true;
    requestError.value = "";
    messages.value = [createSystemMessage("system-clear")];

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
      if (isAuthExpiredError(error)) {
        requestError.value = dictionary.value.authExpired;
        onAuthExpired?.();
        syncStatus();
        return;
      }

      requestError.value = error.message || dictionary.value.clearError;
      syncStatus();
    }
  }

  function startNewChat() {
    resetChatSession();
    promptInput.value = "";
    closeMobileSidebar();
  }

  function stopGenerating() {
    if (currentAbortController.value) {
      currentAbortController.value.abort();
      currentAbortController.value = null;
    }

    isSending.value = false;
    syncStatus();
  }

  async function submitPrompt(promptOverride = "") {
    const overrideText = promptOverride.trim();
    const text = (overrideText || promptInput.value).trim();

    if (!text || isSending.value || !authVerified.value) {
      return;
    }

    const savedModelSettings = modelSettings?.value ?? modelSettings ?? null;

    requestError.value = "";
    closeMobileSidebar();

    // 用户开始对话，移除欢迎/清空提示
    messages.value = messages.value.filter(m => m.role !== 'system');

    const userMessage = createUserMessage(text);
    let assistantMessage = createAssistantMessage();
    messages.value.push(userMessage, assistantMessage);
    syncViewport({ markUnread: true });
    // 從響應式陣列中取得 Proxy，確保後續修改會觸發 UI 更新
    assistantMessage = messages.value[messages.value.length - 1];
    let inStreamThinkTag = false;
    let crossChunkBuffer = "";

    if (!overrideText) {
      promptInput.value = "";
    }

    isSending.value = true;
    streamPhase.value = STREAM_PHASE.THINKING;
    syncStatus();

    const controller = new AbortController();
    currentAbortController.value = controller;

    function partialTagLen(text, tag) {
      for (let i = tag.length - 1; i >= 1; i--) {
        if (text.endsWith(tag.slice(0, i))) return i;
      }
      return 0;
    }

    function flushCrossChunk() {
      if (!crossChunkBuffer) return;
      if (inStreamThinkTag) {
        const lastStep = assistantMessage.steps[assistantMessage.steps.length - 1];
        if (lastStep && lastStep.type === "model_thought") {
          lastStep.detail += crossChunkBuffer;
        }
      } else {
        appendAssistantChunk(assistantMessage, crossChunkBuffer);
      }
      crossChunkBuffer = "";
    }

    function clearProgressPlaceholders() {
      assistantMessage.steps = assistantMessage.steps.filter(step => step.progressPlaceholder !== true);
    }

    function finalizeTimelineSteps() {
      clearProgressPlaceholders();
      for (const step of assistantMessage.steps) {
        if (step.status === "loading") {
          step.status = "success";
        }
      }
    }

    function modelThoughtLabel() {
      return dictionary.value.thinkingLabel
        ?? (locale.value === "zh" ? "模型思考中" : "Model reasoning");
    }

    try {
      await streamChat({
        apiKey: savedModelSettings?.apiKey ?? "",
        baseUrl: savedModelSettings?.baseUrl ?? "",
        sessionId: sessionId.value,
        message: text,
        model: savedModelSettings?.model ?? "",
        signal: controller.signal,
        onEvent: ({ event, data }) => {
          const eventText = normalizeEventText(data);

          if (event === "step") {
            let stepData;
            try {
              stepData = typeof eventText === "string" ? JSON.parse(eventText) : eventText;
            } catch {
              return;
            }

            clearProgressPlaceholders();

            // Ensure status field exists
            if (!stepData.status) {
              stepData.status = "success";
            }

            // Ensure type field for rendering
            if (!stepData.type) {
              stepData.type = stepData.status === "loading" ? null : "tool_call";
            }

            assistantMessage.steps.push(stepData);
            syncViewport({ markUnread: true });
            return;
          }

          if (event === "progress") {
            // Push loading placeholder — will be cleared when real step arrives
            assistantMessage.steps.push({
              traceId: null,
              seq: assistantMessage.steps.length + 1,
              type: null,
              status: "loading",
              label: eventText || "",
              detail: "",
              meta: null,
              ts: Date.now(),
              progressPlaceholder: true
            });
            syncViewport();
            return;
          }

          if (event === "message") {
            let remaining = crossChunkBuffer + eventText;
            crossChunkBuffer = "";

            while (remaining.length > 0) {
              if (inStreamThinkTag) {
                const closeIdx = remaining.indexOf("</think>");
                if (closeIdx >= 0) {
                  const lastStep = assistantMessage.steps[assistantMessage.steps.length - 1];
                  if (lastStep && lastStep.type === "model_thought") {
                    lastStep.detail += remaining.slice(0, closeIdx);
                    lastStep.status = "success";
                  }
                  inStreamThinkTag = false;
                  streamPhase.value = STREAM_PHASE.GENERATING;
                  remaining = remaining.slice(closeIdx + 8);
                } else {
                  const partialLen = partialTagLen(remaining, "</think>");
                  if (partialLen > 0) {
                    const lastStep = assistantMessage.steps[assistantMessage.steps.length - 1];
                    if (lastStep && lastStep.type === "model_thought") {
                      lastStep.detail += remaining.slice(0, -partialLen);
                    }
                    crossChunkBuffer = remaining.slice(-partialLen);
                  } else {
                    const lastStep = assistantMessage.steps[assistantMessage.steps.length - 1];
                    if (lastStep && lastStep.type === "model_thought") {
                      lastStep.detail += remaining;
                    }
                  }
                  remaining = "";
                }
              } else {
                const openIdx = remaining.indexOf("<think>");
                if (openIdx >= 0) {
                  if (openIdx > 0) {
                    appendAssistantChunk(assistantMessage, remaining.slice(0, openIdx));
                  }
                  remaining = remaining.slice(openIdx + 7);
                  inStreamThinkTag = true;
                  streamPhase.value = STREAM_PHASE.THINKING;
                  assistantMessage.steps.push({
                    traceId: null,
                    seq: assistantMessage.steps.length + 1,
                    type: "model_thought",
                    status: "loading",
                    label: modelThoughtLabel(),
                    detail: "",
                    meta: null,
                    ts: Date.now()
                  });

                  const closeIdx = remaining.indexOf("</think>");
                  if (closeIdx >= 0) {
                    const lastStep = assistantMessage.steps[assistantMessage.steps.length - 1];
                    if (lastStep && lastStep.type === "model_thought") {
                      lastStep.detail += remaining.slice(0, closeIdx);
                      lastStep.status = "success";
                    }
                    inStreamThinkTag = false;
                    streamPhase.value = STREAM_PHASE.GENERATING;
                    remaining = remaining.slice(closeIdx + 8);
                  } else {
                    const partialLen = partialTagLen(remaining, "</think>");
                    if (partialLen > 0) {
                      const lastStep = assistantMessage.steps[assistantMessage.steps.length - 1];
                      if (lastStep && lastStep.type === "model_thought") {
                        lastStep.detail += remaining.slice(0, -partialLen);
                      }
                      crossChunkBuffer = remaining.slice(-partialLen);
                    } else {
                      const lastStep = assistantMessage.steps[assistantMessage.steps.length - 1];
                      if (lastStep && lastStep.type === "model_thought") {
                        lastStep.detail += remaining;
                      }
                    }
                    remaining = "";
                  }
                } else {
                  const partialLen = partialTagLen(remaining, "<think>");
                  if (partialLen > 0) {
                    const safe = remaining.slice(0, -partialLen);
                    if (safe.length > 0) {
                      if (streamPhase.value === STREAM_PHASE.THINKING && safe.trim().length > 0) {
                        streamPhase.value = STREAM_PHASE.GENERATING;
                      }
                      appendAssistantChunk(assistantMessage, safe);
                    }
                    crossChunkBuffer = remaining.slice(-partialLen);
                  } else {
                    if (streamPhase.value === STREAM_PHASE.THINKING && remaining.trim().length > 0) {
                      streamPhase.value = STREAM_PHASE.GENERATING;
                    }
                    appendAssistantChunk(assistantMessage, remaining);
                  }
                  remaining = "";
                }
              }
            }

            syncViewport({ markUnread: true });
            return;
          }

          if (event === "error") {
            flushCrossChunk();
            inStreamThinkTag = false;
            finalizeTimelineSteps();
            streamPhase.value = STREAM_PHASE.IDLE;
            flushAssistantRender(assistantMessage);
            requestError.value = getModelErrorMessage(eventText, dictionary.value, locale.value);
            assistantMessage.status = "";
            assistantMessage.streaming = false;
            syncViewport();
            return;
          }

          if (event === "done" || eventText === "[DONE]") {
            flushCrossChunk();
            inStreamThinkTag = false;
            finalizeTimelineSteps();
            streamPhase.value = STREAM_PHASE.IDLE;
            flushAssistantRender(assistantMessage);
            assistantMessage.status = "";
            assistantMessage.streaming = false;
            assistantMessage.rendered = true;
            syncViewport();
          }
        }
      });
    } catch (error) {
      flushCrossChunk();
      inStreamThinkTag = false;
      finalizeTimelineSteps();
      streamPhase.value = STREAM_PHASE.IDLE;
      flushAssistantRender(assistantMessage);

      if (isAuthExpiredError(error)) {
        requestError.value = dictionary.value.authExpired;
        onAuthExpired?.();
        return;
      }

      if (error.name === "AbortError") {
        requestError.value = locale.value === "zh" ? "请求已中断。" : "The request was interrupted.";
      } else {
        requestError.value = getModelErrorMessage(error, dictionary.value, locale.value);
      }
    } finally {
      flushAssistantRender(assistantMessage);
      assistantMessage.streaming = false;
      currentAbortController.value = null;
      isSending.value = false;
      syncStatus();
    }
  }

  return {
    closeMobileSidebar,
    handleScroll,
    handleClearSession,
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

function isAuthExpiredError(error) {
  return error?.status === 401 || String(error?.message ?? "").toLowerCase().includes("login session expired");
}
