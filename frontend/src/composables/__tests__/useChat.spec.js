import { defineComponent, nextTick, ref } from "vue";
import { mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useChat } from "../useChat";

const renderMarkdownLiteMock = vi.hoisted(() =>
  vi.fn((source) => `<p>${String(source ?? "")}</p>`)
);
const clearSessionMock = vi.fn();
const streamChatMock = vi.fn();
const openModelSettingsMock = vi.fn();

vi.mock("../../api/chat", () => ({
  clearSession: (...args) => clearSessionMock(...args),
  streamChat: (...args) => streamChatMock(...args)
}));

vi.mock("../../utils/markdown", () => ({
  renderMarkdownLite: (source) => renderMarkdownLiteMock(source)
}));

const dictionary = {
  chatRequiresModelSettings: "Save base URL, API key, and model before sending a chat message.",
  clearBusy: "Clearing...",
  clearChat: "Clear session",
  clearError: "Unable to clear the session right now.",
  clearSuccess: "Session cleared.",
  authExpired: "Your login session has expired. Please sign in again.",
  modelErrorAuth:
    "The API key is invalid or does not have permission. Check the model settings and try again.",
  modelErrorBusy: "The model is busy right now. Please try again shortly.",
  modelErrorGeneric: "The model request failed. Check the settings and try again.",
  modelErrorInvalidConfig: "The model settings are incomplete or invalid. Check them and try again.",
  modelErrorTimeout: "The model response timed out. Please try again shortly.",
  modelErrorUnavailable:
    "The model service is temporarily unavailable. Check the base URL or network connection.",
  prompts: ["Prompt one", "Prompt two"],
  statusGuest: "Enter your access key",
  statusReady: "System connected and ready",
  statusThinking: "Assistant is preparing a reply",
  streamPhaseThinking: "Thinking…",
  streamPhaseGenerating: "Generating…",
  bubbleThinking: "Thinking…",
  thinkingLabel: "Reasoning",
  welcomeBody: "Welcome body",
  welcomeHint: "Welcome hint",
  welcomeTitle: "Welcome"
};

function createDeferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });

  return { promise, reject, resolve };
}

function mountChatHarness(modelSettings, overrides = {}) {
  const Harness = defineComponent({
    setup() {
      return useChat({
        authVerified: ref(true),
        dictionary: ref(dictionary),
        locale: ref("en"),
        modelSettings,
        openModelSettings: openModelSettingsMock,
        ...overrides
      });
    },
    template: "<div />"
  });

  return mount(Harness);
}

beforeEach(() => {
  window.localStorage.clear();
  clearSessionMock.mockReset();
  streamChatMock.mockReset();
  openModelSettingsMock.mockReset();
  renderMarkdownLiteMock.mockClear();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("useChat", () => {
  it("allows a send when model settings are missing and forwards an empty model config", async () => {
    streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
      onEvent({
        event: "message",
        data: `Hi! No external model is configured yet. Open Settings and fill in Base URL, API Key, and Model.

FOLLOW_UP_QUESTIONS:
1. Help me configure the model connection
2. Let's start with a sample analytics question`
      });
      onEvent({ event: "done", data: "[DONE]" });
    });
    const wrapper = mountChatHarness(ref(null));

    await wrapper.vm.submitPrompt("Hi");

    expect(openModelSettingsMock).not.toHaveBeenCalled();
    expect(streamChatMock).toHaveBeenCalledWith(
      expect.objectContaining({
        apiKey: "",
        baseUrl: "",
        message: "Hi",
        model: "",
        sessionId: expect.any(String)
      })
    );
    expect(wrapper.vm.messages).toHaveLength(2);
    expect(wrapper.vm.messages[1].content).toContain("No external model is configured yet.");
    expect(wrapper.vm.requestError).toBe("");
  });

  it("sends the saved model config with the chat request", async () => {
    streamChatMock.mockResolvedValue(undefined);
    const wrapper = mountChatHarness(
      ref({
        apiKey: "sk-test",
        baseUrl: "https://api.example.com",
        model: "gpt-4.1-mini"
      })
    );

    await wrapper.vm.submitPrompt("Hello");

    expect(streamChatMock).toHaveBeenCalledWith(
      expect.objectContaining({
        apiKey: "sk-test",
        baseUrl: "https://api.example.com",
        message: "Hello",
        model: "gpt-4.1-mini",
        sessionId: expect.any(String)
      })
    );
  });

  it("replaces raw model rate-limit errors with user-friendly copy", async () => {
    streamChatMock.mockRejectedValueOnce(
      new Error('429 - {"error":{"code":"1305","message":"该模型当前访问量过大，请您稍后再试"}}')
    );
    const wrapper = mountChatHarness(
      ref({
        apiKey: "sk-test",
        baseUrl: "https://api.example.com",
        model: "glm-4"
      })
    );

    await wrapper.vm.submitPrompt("Hello");

    expect(wrapper.vm.requestError).toBe("The model is busy right now. Please try again shortly.");
  });

  it("calls the auth-expired handler when the chat stream returns 401", async () => {
    const onAuthExpired = vi.fn();
    streamChatMock.mockRejectedValueOnce(Object.assign(new Error("Login session expired."), { status: 401 }));
    const wrapper = mountChatHarness(
      ref({
        apiKey: "sk-test",
        baseUrl: "https://api.example.com",
        model: "gpt-4.1-mini"
      }),
      { onAuthExpired }
    );

    await wrapper.vm.submitPrompt("Hello");

    expect(onAuthExpired).toHaveBeenCalledTimes(1);
    expect(wrapper.vm.requestError).toBe(dictionary.authExpired);
  });

  it("handles model error SSE events without hanging the chat request", async () => {
    streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
      onEvent({ event: "progress", data: "Calling the configured model" });
      onEvent({ event: "error", data: "429 busy" });
    });
    const wrapper = mountChatHarness(
      ref({
        apiKey: "sk-test",
        baseUrl: "https://api.example.com",
        model: "glm-4"
      })
    );

    await wrapper.vm.submitPrompt("Hello");

    expect(wrapper.vm.isSending).toBe(false);
    expect(wrapper.vm.messages[1].streaming).toBe(false);
    expect(wrapper.vm.requestError).toBe("The model is busy right now. Please try again shortly.");
  });

  it("keeps progress events as a retained processing history after the stream finishes", async () => {
    streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
      onEvent({ event: "progress", data: "Identifying the analysis theme" });
      onEvent({
        event: "step",
        data: JSON.stringify({
          trace_id: "run-1",
          seq: 1,
          type: "data_load",
          status: "success",
          label: "Loaded data",
          detail: "Loaded 10 rows",
          meta: {}
        })
      });
      onEvent({ event: "progress", data: "Calling the configured model" });
      onEvent({ event: "progress", data: "Validating data consistency" });
      onEvent({ event: "message", data: "Visible reply" });
      onEvent({ event: "done", data: "[DONE]" });
    });
    const wrapper = mountChatHarness(
      ref({
        apiKey: "sk-test",
        baseUrl: "https://api.example.com",
        model: "gpt-4.1-mini"
      })
    );

    await wrapper.vm.submitPrompt("Hello");

    expect(wrapper.vm.messages[1].steps.map(step => step.label)).toEqual([
      "Identifying the analysis theme",
      "Loaded data",
      "Calling the configured model",
      "Validating data consistency"
    ]);
    expect(wrapper.vm.messages[1].steps.filter(step => step.type === "progress").map(step => step.label)).toEqual([
      "Identifying the analysis theme",
      "Calling the configured model",
      "Validating data consistency"
    ]);
    expect(wrapper.vm.messages[1].steps.filter(step => step.type === "progress").every(step => step.status === "success")).toBe(true);
  });

  it("appends multiple streamed message events into one assistant reply", async () => {
    streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
      onEvent({ event: "message", data: "Hello" });
      onEvent({ event: "message", data: " world" });
      onEvent({ event: "done", data: "[DONE]" });
    });
    const wrapper = mountChatHarness(
      ref({
        apiKey: "sk-test",
        baseUrl: "https://api.example.com",
        model: "gpt-4.1-mini"
      })
    );

    await wrapper.vm.submitPrompt("Hello");

    expect(wrapper.vm.messages).toHaveLength(2);
    expect(wrapper.vm.messages[1].content).toBe("Hello world");
    expect(wrapper.vm.messages[1].html).toContain("Hello world");
    expect(wrapper.vm.messages[1].streaming).toBe(false);
  });

  it("coalesces streamed markdown rendering and flushes the final render on done", async () => {
    vi.useFakeTimers();
    const deferred = createDeferred();
    let emitEvent = () => {};
    streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
      emitEvent = onEvent;
      await deferred.promise;
    });
    const wrapper = mountChatHarness(
      ref({
        apiKey: "sk-test",
        baseUrl: "https://api.example.com",
        model: "gpt-4.1-mini"
      })
    );

    const submitPromise = wrapper.vm.submitPrompt("Hello");
    await nextTick();
    const initialRenderCount = renderMarkdownLiteMock.mock.calls.length;

    emitEvent({ event: "message", data: "# Title" });
    emitEvent({ event: "message", data: "\n\nBody" });

    expect(wrapper.vm.messages[1].content).toBe("# Title\n\nBody");
    expect(renderMarkdownLiteMock).toHaveBeenCalledTimes(initialRenderCount);

    vi.advanceTimersByTime(100);

    expect(renderMarkdownLiteMock).toHaveBeenCalledTimes(initialRenderCount + 1);
    expect(wrapper.vm.messages[1].html).toContain("# Title\n\nBody");

    emitEvent({ event: "message", data: "\n\nMore\n\nFOLLOW_UP_QUESTIONS:\n1. A\n2. B" });
    expect(renderMarkdownLiteMock).toHaveBeenCalledTimes(initialRenderCount + 1);

    emitEvent({ event: "done", data: "[DONE]" });
    expect(renderMarkdownLiteMock).toHaveBeenCalledTimes(initialRenderCount + 2);
    expect(wrapper.vm.messages[1].html).toContain("# Title\n\nBody\n\nMore");
    expect(wrapper.vm.messages[1].rendered).toBe(true);

    deferred.resolve();
    await submitPromise;
    vi.useRealTimers();
  });

  it("preserves accumulated assistant content without showing a duplicate error after partial chunks", async () => {
    streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
      onEvent({ event: "message", data: "Partial" });
      onEvent({ event: "message", data: " answer" });
      onEvent({ event: "error", data: "429 busy" });
    });
    const wrapper = mountChatHarness(
      ref({
        apiKey: "sk-test",
        baseUrl: "https://api.example.com",
        model: "gpt-4.1-mini"
      })
    );

    await wrapper.vm.submitPrompt("Hello");

    expect(wrapper.vm.messages).toHaveLength(2);
    expect(wrapper.vm.messages[1].content).toBe("Partial answer");
    expect(wrapper.vm.messages[1].html).toContain("Partial answer");
    expect(wrapper.vm.messages[1].streaming).toBe(false);
    expect(wrapper.vm.isSending).toBe(false);
    expect(wrapper.vm.requestError).toBe("");
  });

  it("suppresses rejected stream errors after visible partial content", async () => {
    streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
      onEvent({ event: "message", data: "Partial answer" });
      throw new Error("429 busy");
    });
    const wrapper = mountChatHarness(
      ref({
        apiKey: "sk-test",
        baseUrl: "https://api.example.com",
        model: "gpt-4.1-mini"
      })
    );

    await wrapper.vm.submitPrompt("Hello");

    expect(wrapper.vm.messages[1].content).toBe("Partial answer");
    expect(wrapper.vm.requestError).toBe("");
  });

  it("marks unread immediately when visible messages are inserted while the viewport is unpinned", async () => {
    const deferred = createDeferred();
    streamChatMock.mockImplementationOnce(async () => {
      await deferred.promise;
    });
    const wrapper = mountChatHarness(
      ref({
        apiKey: "sk-test",
        baseUrl: "https://api.example.com",
        model: "gpt-4.1-mini"
      })
    );

    wrapper.vm.scrollContainer = {
      clientHeight: 100,
      scrollHeight: 400,
      scrollTop: 0
    };
    wrapper.vm.handleScroll();

    const submitPromise = wrapper.vm.submitPrompt("Hello");
    await nextTick();

    expect(wrapper.vm.messages).toHaveLength(2);
    expect(wrapper.vm.hasUnreadContent).toBe(true);

    deferred.resolve();
    await submitPromise;
  });

  it("does not mark unread for progress events after the user scrolls away from the bottom", async () => {
    const deferred = createDeferred();
    let emitEvent = () => {};
    streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
      emitEvent = onEvent;
      await deferred.promise;
    });
    const wrapper = mountChatHarness(
      ref({
        apiKey: "sk-test",
        baseUrl: "https://api.example.com",
        model: "gpt-4.1-mini"
      })
    );

    wrapper.vm.scrollContainer = {
      clientHeight: 100,
      scrollHeight: 100,
      scrollTop: 0
    };

    const submitPromise = wrapper.vm.submitPrompt("Hello");
    await nextTick();

    expect(wrapper.vm.hasUnreadContent).toBe(false);

    wrapper.vm.scrollContainer = {
      clientHeight: 100,
      scrollHeight: 400,
      scrollTop: 0
    };
    wrapper.vm.handleScroll();

    emitEvent({ event: "progress", data: "Calling the configured model" });
    await nextTick();

    expect(wrapper.vm.hasUnreadContent).toBe(false);

    emitEvent({ event: "message", data: "Visible reply" });
    await nextTick();

    expect(wrapper.vm.hasUnreadContent).toBe(true);

    deferred.resolve();
    await submitPromise;
  });

  it("keeps unread false when only non-visible progress is followed by an error after the user unpins", async () => {
    const deferred = createDeferred();
    let emitEvent = () => {};
    streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
      emitEvent = onEvent;
      await deferred.promise;
    });
    const wrapper = mountChatHarness(
      ref({
        apiKey: "sk-test",
        baseUrl: "https://api.example.com",
        model: "gpt-4.1-mini"
      })
    );

    wrapper.vm.scrollContainer = {
      clientHeight: 100,
      scrollHeight: 100,
      scrollTop: 0
    };

    const submitPromise = wrapper.vm.submitPrompt("Hello");
    await nextTick();

    expect(wrapper.vm.hasUnreadContent).toBe(false);

    wrapper.vm.scrollContainer = {
      clientHeight: 100,
      scrollHeight: 400,
      scrollTop: 0
    };
    wrapper.vm.handleScroll();

    emitEvent({ event: "progress", data: "Calling the configured model" });
    emitEvent({ event: "error", data: "429 busy" });
    await nextTick();

    expect(wrapper.vm.hasUnreadContent).toBe(false);
    expect(wrapper.vm.requestError).toBe("The model is busy right now. Please try again shortly.");

    deferred.resolve();
    await submitPromise;
  });

  it("keeps unread false when visible content arrived while pinned and only an error arrives after the user unpins", async () => {
    const deferred = createDeferred();
    let emitEvent = () => {};
    streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
      emitEvent = onEvent;
      await deferred.promise;
    });
    const wrapper = mountChatHarness(
      ref({
        apiKey: "sk-test",
        baseUrl: "https://api.example.com",
        model: "gpt-4.1-mini"
      })
    );

    wrapper.vm.scrollContainer = {
      clientHeight: 100,
      scrollHeight: 100,
      scrollTop: 0
    };

    const submitPromise = wrapper.vm.submitPrompt("Hello");
    await nextTick();

    emitEvent({ event: "message", data: "Visible reply" });
    await nextTick();

    expect(wrapper.vm.hasUnreadContent).toBe(false);

    wrapper.vm.scrollContainer = {
      clientHeight: 100,
      scrollHeight: 400,
      scrollTop: 0
    };
    wrapper.vm.handleScroll();

    emitEvent({ event: "error", data: "429 busy" });
    await nextTick();

    expect(wrapper.vm.hasUnreadContent).toBe(false);
    expect(wrapper.vm.requestError).toBe("");

    deferred.resolve();
    await submitPromise;
  });

  it("initializes messages with a welcome system message", () => {
    const wrapper = mountChatHarness(ref(null));

    expect(wrapper.vm.messages).toHaveLength(1);
    expect(wrapper.vm.messages[0].role).toBe("system");
    expect(wrapper.vm.messages[0].type).toBe("welcome");
  });

  it("replaces messages with system-clear after clearing the session", async () => {
    clearSessionMock.mockResolvedValue(undefined);

    streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
      onEvent({ event: "message", data: "Hello" });
      onEvent({ event: "done", data: "[DONE]" });
    });
    const wrapper = mountChatHarness(ref(null));

    await wrapper.vm.submitPrompt("Hello");
    expect(wrapper.vm.messages[0].role).toBe("user");
    expect(wrapper.vm.messages).toHaveLength(2); // user + assistant

    await wrapper.vm.handleClearSession();

    expect(wrapper.vm.messages).toHaveLength(1);
    expect(wrapper.vm.messages[0].role).toBe("system");
    expect(wrapper.vm.messages[0].type).toBe("system-clear");
  });

  it("switches streamPhase from thinking to generating when </think> is detected", async () => {
    streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
      onEvent({ event: "message", data: "<think>analyzing</think>" });
    });
    const wrapper = mountChatHarness(ref(null));

    expect(wrapper.vm.streamPhase).toBe("idle");
    await wrapper.vm.submitPrompt("Hello");

    expect(wrapper.vm.streamPhase).toBe("generating");
  });

  it("captures thinking when think tags are split across streamed events", async () => {
    streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
      onEvent({ event: "message", data: "<thi" });
      onEvent({ event: "message", data: "nk>step one" });
      onEvent({ event: "message", data: "</thi" });
      onEvent({ event: "message", data: "nk>Final answer" });
      onEvent({ event: "done", data: "[DONE]" });
    });
    const wrapper = mountChatHarness(ref(null));

    await wrapper.vm.submitPrompt("Hello");

    const thoughtSteps = wrapper.vm.messages[1].steps.filter(s => s.type === "model_thought");
    expect(thoughtSteps.length).toBeGreaterThanOrEqual(1);
    expect(thoughtSteps[0].detail).toBe("step one");
    expect(thoughtSteps[0].status).toBe("success");
    expect(wrapper.vm.messages[1].content).toBe("Final answer");
    expect(wrapper.vm.messages[1].content).not.toContain("<think>");
    expect(wrapper.vm.messages[1].streaming).toBe(false);
  });

  it("uses the localized thinking label for model thought steps", async () => {
    streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
      onEvent({ event: "message", data: "<think>analyzing</think>" });
      onEvent({ event: "done", data: "[DONE]" });
    });
    const wrapper = mountChatHarness(ref(null));

    await wrapper.vm.submitPrompt("Hello");

    const thoughtSteps = wrapper.vm.messages[1].steps.filter(s => s.type === "model_thought");
    expect(thoughtSteps).toHaveLength(1);
    expect(thoughtSteps[0].label).toBe(dictionary.thinkingLabel);
  });

  it("switches streamPhase to generating on first body chunk when model skips think", async () => {
    streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
      onEvent({ event: "message", data: "Direct answer text" });
    });
    const wrapper = mountChatHarness(ref(null));

    await wrapper.vm.submitPrompt("Hello");

    expect(wrapper.vm.streamPhase).toBe("generating");
  });

  it("resets streamPhase to idle on done", async () => {
    streamChatMock.mockImplementationOnce(async ({ onEvent }) => {
      onEvent({ event: "message", data: "text" });
      onEvent({ event: "done", data: "[DONE]" });
    });
    const wrapper = mountChatHarness(ref(null));

    await wrapper.vm.submitPrompt("Hello");

    expect(wrapper.vm.streamPhase).toBe("idle");
  });

  it("resets streamPhase to idle on error", async () => {
    streamChatMock.mockRejectedValueOnce(new Error("Network error"));
    const wrapper = mountChatHarness(ref(null));

    await wrapper.vm.submitPrompt("Hello");

    expect(wrapper.vm.streamPhase).toBe("idle");
  });

  it("resets streamPhase to idle on abort", async () => {
    streamChatMock.mockImplementationOnce(
      ({ signal }) =>
        new Promise((_resolve, reject) => {
          signal.addEventListener("abort", () => {
            reject(Object.assign(new Error("Aborted"), { name: "AbortError" }));
          });
        })
    );
    const wrapper = mountChatHarness(ref(null));

    const submitPromise = wrapper.vm.submitPrompt("Hello");
    await nextTick();

    expect(wrapper.vm.streamPhase).toBe("thinking");

    wrapper.vm.stopGenerating();
    await nextTick();
    // submitPromise should settle (rejected) after abort, which triggers catch
    try {
      await submitPromise;
    } catch {
      // expected
    }

    expect(wrapper.vm.streamPhase).toBe("idle");
  });
});
