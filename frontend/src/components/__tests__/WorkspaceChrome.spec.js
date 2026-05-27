import { flushPromises, mount } from "@vue/test-utils";
import { ref } from "vue";
import { vi } from "vitest";
import ChatInput from "../chat/ChatInput.vue";
import ExampleSidebar from "../layout/ExampleSidebar.vue";
import TopNav from "../layout/TopNav.vue";
import ChatView from "../../views/ChatView.vue";

const testModelConnectionMock = vi.fn();
const useChatMock = vi.fn(() => ({
    closeMobileSidebar: vi.fn(),
    handleClearSession: vi.fn(),
    handleScroll: vi.fn(),
    hasMessages: false,
    hasUnreadContent: false,
    isSending: false,
    jumpToLatest: vi.fn(),
    messages: ref([]),
    openMobileSidebar: vi.fn(),
    promptInput: ref(""),
    requestError: "",
    scrollContainer: ref(null),
    showMobileSidebar: false,
    startNewChat: vi.fn(),
    statusMessage: "",
    stopGenerating: vi.fn(),
    submitPrompt: vi.fn(),
    toastMessage: ""
  }));

vi.mock("../../composables/useChat", () => ({
  useChat: (...args) => useChatMock(...args)
}));

vi.mock("../../api/modelConfig", () => ({
  testModelConnection: (...args) => testModelConnectionMock(...args)
}));

const dictionary = {
  appName: "Dealer AI Assistant",
  appTagline: "Editorial workspace",
  clearChat: "Clear chat",
  closeMenu: "Close",
  footerNote: "Internal use only",
  inputPlaceholder: "Ask a question",
  logoutButton: "Sign out",
  modelSettingsApiKeyLabel: "API key",
  modelSettingsBaseUrlLabel: "Base URL",
  modelSettingsDescription: "Configure the model connection used for the next request.",
  modelErrorAuth:
    "The API key is invalid or does not have permission. Check the model settings and try again.",
  modelErrorBusy: "The model is busy right now. Please try again shortly.",
  modelErrorGeneric: "The model request failed. Check the settings and try again.",
  modelErrorInvalidConfig: "The model settings are incomplete or invalid. Check them and try again.",
  modelErrorTimeout: "The model response timed out. Please try again shortly.",
  modelErrorUnavailable:
    "The model service is temporarily unavailable. Check the base URL or network connection.",
  modelSettingsModelLabel: "Model",
  modelSettingsResetButton: "Reset",
  modelSettingsSaveButton: "Save",
  modelSettingsTestButton: "Test Connection",
  modelSettingsTitle: "Model settings",
  newChat: "New chat",
  openMenu: "Open",
  prompts: ["Summarize account health"],
  sending: "Sending",
  sendButton: "Send",
  settingsButton: "Settings",
  sidebarSection: "Prompts",
  switchLanguage: "Switch language",
  workspaceSubtitle: "Ask and review dealer insights",
  workspaceTitle: "Workspace",
  jumpToLatest: "Jump to latest"
};

beforeEach(() => {
  testModelConnectionMock.mockReset();
});

describe("Workspace chrome editorial hooks", () => {
  test("renders the reference-inspired workspace shell hooks", () => {
    const topNav = mount(TopNav, {
      props: {
        authVerified: true,
        dictionary,
        locale: "en"
      },
      global: {
        stubs: {
        }
      }
    });
    const sidebar = mount(ExampleSidebar, {
      props: {
        activeSessionLabel: "Current session",
        dictionary
      }
    });
    const chatInput = mount(ChatInput, {
      props: {
        dictionary,
        modelValue: "Hello"
      }
    });
    const chatView = mount(ChatView, {
      props: {
        authVerified: true,
        dictionary,
        locale: "en"
      },
      global: {
        stubs: {
          ChatInput: {
            template: "<div class='chat-input-stub'></div>"
          },
          ChatMessageList: {
            template: "<div class='chat-message-list-stub'></div>"
          },
          ExampleSidebar: {
            template: "<aside class='example-sidebar-stub'></aside>"
          },
          TopNav: {
            template: "<header class='top-nav-stub'></header>"
          }
        }
      }
    });

    expect(topNav.find(".topbar-product").exists()).toBe(true);
    expect(topNav.find(".topbar-editorial").exists()).toBe(false);
    expect(topNav.find(".topbar-tools").exists()).toBe(true);
    expect(sidebar.find(".sidebar-rail").exists()).toBe(true);
    expect(sidebar.find(".sidebar-group-list").exists()).toBe(true);
    expect(chatInput.find(".composer-dock").exists()).toBe(true);
    expect(chatView.find(".workspace-shell").exists()).toBe(true);
    expect(chatView.find(".workspace-stage").exists()).toBe(true);
  });

  test("submits on Enter only when the composer has content and is not sending", async () => {
    const emptyInput = mount(ChatInput, {
      props: {
        dictionary,
        modelValue: "   "
      }
    });
    const sendingInput = mount(ChatInput, {
      props: {
        dictionary,
        isSending: true,
        modelValue: "Hello"
      }
    });
    const readyInput = mount(ChatInput, {
      props: {
        dictionary,
        modelValue: "Hello"
      }
    });
    const composingInput = mount(ChatInput, {
      props: {
        dictionary,
        modelValue: "Hello"
      }
    });

    await emptyInput.find(".composer-input").trigger("keydown", {
      key: "Enter",
      shiftKey: false
    });
    await sendingInput.find(".composer-input").trigger("keydown", {
      key: "Enter",
      shiftKey: false
    });
    await readyInput.find(".composer-input").trigger("keydown", {
      key: "Enter",
      shiftKey: false
    });
    await readyInput.find(".composer-input").trigger("keydown", {
      key: "Enter",
      shiftKey: true
    });
    await composingInput.find(".composer-input").trigger("keydown", {
      isComposing: true,
      key: "Enter",
      shiftKey: false
    });

    expect(emptyInput.emitted("submit")).toBeUndefined();
    expect(sendingInput.emitted("submit")).toBeUndefined();
    expect(readyInput.emitted("submit")).toHaveLength(1);
    expect(composingInput.emitted("submit")).toBeUndefined();
  });

  test("opens the settings panel from the top bar and closes it on cancel", async () => {
    const chatView = mount(ChatView, {
      props: {
        authVerified: true,
        dictionary,
        locale: "en"
      },
      global: {
        stubs: {
          ChatInput: {
            template: "<div class='chat-input-stub'></div>"
          },
          ChatMessageList: {
            template: "<div class='chat-message-list-stub'></div>"
          },
          ExampleSidebar: {
            template: "<aside class='example-sidebar-stub'></aside>"
          },
          LanguageSwitcher: {
            template: "<button type='button'>lang</button>"
          },
          ModelSettingsPanel: {
            props: ["open"],
            template: `
              <div class="model-settings-panel-stub" :data-open="String(open)">
                <button class="panel-cancel-stub" type="button" @click="$emit('cancel')">cancel</button>
              </div>
            `
          }
        }
      }
    });

    expect(chatView.find(".model-settings-panel-stub").attributes("data-open")).toBe("false");

    await chatView.find(".settings-button").trigger("click");
    expect(chatView.find(".model-settings-panel-stub").attributes("data-open")).toBe("true");

    await chatView.find(".panel-cancel-stub").trigger("click");
    expect(chatView.find(".model-settings-panel-stub").attributes("data-open")).toBe("false");
  });

  test("submits a sidebar question through the direct-submit event and closes the mobile sidebar", async () => {
    const closeMobileSidebar = vi.fn();
    const submitPrompt = vi.fn();
    const question = "What changed in conversion this week?";

    useChatMock.mockReturnValueOnce({
      activeSessionLabel: "Current session",
      closeMobileSidebar,
      handleClearSession: vi.fn(),
      handleScroll: vi.fn(),
      hasMessages: false,
      hasUnreadContent: false,
      isSending: false,
      jumpToLatest: vi.fn(),
      messages: ref([]),
      openMobileSidebar: vi.fn(),
      promptInput: ref(""),
      requestError: "",
      scrollContainer: ref(null),
      showMobileSidebar: true,
      startNewChat: vi.fn(),
      statusMessage: "",
      stopGenerating: vi.fn(),
      submitPrompt,
      toastMessage: ""
    });

    const chatView = mount(ChatView, {
      props: {
        authVerified: true,
        dictionary,
        locale: "en"
      },
      global: {
        stubs: {
          ChatInput: {
            template: "<div class='chat-input-stub'></div>"
          },
          ChatMessageList: {
            template: "<div class='chat-message-list-stub'></div>"
          },
          ExampleSidebar: {
            template: `
              <button
                class="sidebar-question-stub"
                type="button"
                @click="$emit('submit-prompt', '${question}')"
              >
                prompt
              </button>
            `
          },
          LanguageSwitcher: {
            template: "<button type='button'>lang</button>"
          },
          ModelSettingsPanel: {
            template: "<div class='model-settings-panel-stub'></div>"
          }
        }
      }
    });

    await chatView.find(".sidebar-question-stub").trigger("click");

    expect(closeMobileSidebar).toHaveBeenCalledTimes(1);
    expect(submitPrompt).toHaveBeenCalledTimes(1);
    expect(submitPrompt).toHaveBeenCalledWith(question);
  });

  test("passes connection test state through to the model settings panel", async () => {
    const chatView = mount(ChatView, {
      props: {
        authVerified: true,
        dictionary,
        locale: "en"
      },
      global: {
        stubs: {
          ChatInput: {
            template: "<div class='chat-input-stub'></div>"
          },
          ChatMessageList: {
            template: "<div class='chat-message-list-stub'></div>"
          },
          ExampleSidebar: {
            template: "<aside class='example-sidebar-stub'></aside>"
          },
          LanguageSwitcher: {
            template: "<button type='button'>lang</button>"
          },
          ModelSettingsPanel: {
            props: ["connectionMessage", "connectionStatus", "open"],
            template: `
              <div
                class="model-settings-panel-stub"
                :data-message="connectionMessage"
                :data-open="String(open)"
                :data-status="connectionStatus"
              >
                <button class="test-connection-stub" type="button" @click="$emit('test-connection', { apiKey: '', baseUrl: '', model: '' })">
                  test
                </button>
              </div>
            `
          }
        }
      }
    });

    await chatView.find(".settings-button").trigger("click");
    await chatView.find(".test-connection-stub").trigger("click");

    expect(chatView.find(".model-settings-panel-stub").attributes("data-status")).toBe("error");
    expect(chatView.find(".model-settings-panel-stub").attributes("data-message")).toBe(
      "Save base URL, API key, and model before testing the connection."
    );
  });

  test("wires the chat viewport scroll handler from useChat", async () => {
    const handleScroll = vi.fn();

    useChatMock.mockReturnValueOnce({
      activeSessionLabel: "Current session",
      closeMobileSidebar: vi.fn(),
      handleClearSession: vi.fn(),
      handleScroll,
      hasMessages: true,
      hasUnreadContent: false,
      isSending: false,
      jumpToLatest: vi.fn(),
      messages: ref([]),
      openMobileSidebar: vi.fn(),
      promptInput: ref(""),
      requestError: "",
      scrollContainer: ref(null),
      showMobileSidebar: false,
      startNewChat: vi.fn(),
      statusMessage: "",
      stopGenerating: vi.fn(),
      submitPrompt: vi.fn(),
      toastMessage: ""
    });

    const chatView = mount(ChatView, {
      props: {
        authVerified: true,
        dictionary,
        locale: "en"
      },
      global: {
        stubs: {
          ChatInput: {
            template: "<div class='chat-input-stub'></div>"
          },
          ChatMessageList: {
            template: "<div class='chat-message-list-stub'></div>"
          },
          ExampleSidebar: {
            template: "<aside class='example-sidebar-stub'></aside>"
          },
          LanguageSwitcher: {
            template: "<button type='button'>lang</button>"
          },
          ModelSettingsPanel: {
            template: "<div class='model-settings-panel-stub'></div>"
          }
        }
      }
    });

    await chatView.find(".chat-scroll").trigger("scroll");

    expect(handleScroll).toHaveBeenCalledTimes(1);
  });

  test("shows a jump-to-latest affordance when unread streamed content accumulates off-screen", async () => {
    const jumpToLatest = vi.fn();

    useChatMock.mockReturnValueOnce({
      activeSessionLabel: "Current session",
      closeMobileSidebar: vi.fn(),
      handleClearSession: vi.fn(),
      handleScroll: vi.fn(),
      hasMessages: true,
      hasUnreadContent: true,
      isSending: false,
      jumpToLatest,
      messages: ref([
        {
          id: "assistant-1",
          role: "assistant",
          html: "<p>Partial answer</p>",
          content: "Partial answer",
          followUps: [],
          thinking: "",
          thinkingExpanded: false,
          status: "",
          steps: [],
          streaming: true
        }
      ]),
      openMobileSidebar: vi.fn(),
      promptInput: ref(""),
      requestError: "",
      scrollContainer: ref(null),
      showMobileSidebar: false,
      startNewChat: vi.fn(),
      statusMessage: "",
      stopGenerating: vi.fn(),
      submitPrompt: vi.fn(),
      toastMessage: ""
    });

    const chatView = mount(ChatView, {
      props: {
        authVerified: true,
        dictionary,
        locale: "en"
      },
      global: {
        stubs: {
          ChatInput: {
            template: "<div class='chat-input-stub'></div>"
          },
          ChatMessageList: {
            template: "<div class='chat-message-list-stub'></div>"
          },
          ExampleSidebar: {
            template: "<aside class='example-sidebar-stub'></aside>"
          },
          LanguageSwitcher: {
            template: "<button type='button'>lang</button>"
          },
          ModelSettingsPanel: {
            template: "<div class='model-settings-panel-stub'></div>"
          }
        }
      }
    });

    const affordance = chatView.find(".jump-latest-button");

    expect(affordance.exists()).toBe(true);
    expect(affordance.text()).toContain("Jump to latest");

    await affordance.trigger("click");

    expect(jumpToLatest).toHaveBeenCalledTimes(1);
  });

  test("opens the settings panel when the composer submit path requests model setup", async () => {
    useChatMock.mockImplementationOnce((options) => ({
      activeSessionLabel: "Current session",
      closeMobileSidebar: vi.fn(),
      handleClearSession: vi.fn(),
      handleScroll: vi.fn(),
      hasMessages: false,
      hasUnreadContent: false,
      isSending: false,
      jumpToLatest: vi.fn(),
      messages: ref([]),
      openMobileSidebar: vi.fn(),
      promptInput: ref(""),
      requestError: "",
      scrollContainer: ref(null),
      showMobileSidebar: false,
      startNewChat: vi.fn(),
      statusMessage: "",
      stopGenerating: vi.fn(),
      submitPrompt: vi.fn(() => {
        options.openModelSettings?.();
      }),
      toastMessage: ""
    }));

    const chatView = mount(ChatView, {
      props: {
        authVerified: true,
        dictionary,
        locale: "en"
      },
      global: {
        stubs: {
          ChatInput: {
            template: "<button class='chat-submit-stub' type='button' @click=\"$emit('submit')\">submit</button>"
          },
          ChatMessageList: {
            template: "<div class='chat-message-list-stub'></div>"
          },
          ExampleSidebar: {
            template: "<aside class='example-sidebar-stub'></aside>"
          },
          LanguageSwitcher: {
            template: "<button type='button'>lang</button>"
          },
          ModelSettingsPanel: {
            props: ["open"],
            template: "<div class='model-settings-panel-stub' :data-open=\"String(open)\"></div>"
          }
        }
      }
    });

    expect(chatView.find(".model-settings-panel-stub").attributes("data-open")).toBe("false");

    await chatView.find(".chat-submit-stub").trigger("click");

    expect(chatView.find(".model-settings-panel-stub").attributes("data-open")).toBe("true");
  });

  test("shows connection test success feedback from the API helper", async () => {
    testModelConnectionMock.mockResolvedValueOnce({ success: true, message: "Connected" });

    const chatView = mount(ChatView, {
      props: {
        authVerified: true,
        dictionary,
        locale: "en"
      },
      global: {
        stubs: {
          ChatInput: {
            template: "<div class='chat-input-stub'></div>"
          },
          ChatMessageList: {
            template: "<div class='chat-message-list-stub'></div>"
          },
          ExampleSidebar: {
            template: "<aside class='example-sidebar-stub'></aside>"
          },
          LanguageSwitcher: {
            template: "<button type='button'>lang</button>"
          },
          ModelSettingsPanel: {
            props: ["connectionMessage", "connectionStatus", "open"],
            template: `
              <div
                class="model-settings-panel-stub"
                :data-message="connectionMessage"
                :data-open="String(open)"
                :data-status="connectionStatus"
              >
                <button class="test-connection-stub" type="button" @click="$emit('test-connection', { apiKey: ' sk-test ', baseUrl: ' https://api.example.com ', model: ' gpt-4.1-mini ' })">
                  test
                </button>
              </div>
            `
          }
        }
      }
    });

    await chatView.find(".settings-button").trigger("click");
    await chatView.find(".test-connection-stub").trigger("click");
    await flushPromises();

    expect(testModelConnectionMock).toHaveBeenCalledWith({
      apiKey: "sk-test",
      baseUrl: "https://api.example.com",
      model: "gpt-4.1-mini"
    });
    expect(chatView.find(".model-settings-panel-stub").attributes("data-status")).toBe("success");
    expect(chatView.find(".model-settings-panel-stub").attributes("data-message")).toBe("Connected");
  });

  test("shows connection test failure feedback from the API helper", async () => {
    testModelConnectionMock.mockRejectedValueOnce(new Error("Auth failed"));

    const chatView = mount(ChatView, {
      props: {
        authVerified: true,
        dictionary,
        locale: "en"
      },
      global: {
        stubs: {
          ChatInput: {
            template: "<div class='chat-input-stub'></div>"
          },
          ChatMessageList: {
            template: "<div class='chat-message-list-stub'></div>"
          },
          ExampleSidebar: {
            template: "<aside class='example-sidebar-stub'></aside>"
          },
          LanguageSwitcher: {
            template: "<button type='button'>lang</button>"
          },
          ModelSettingsPanel: {
            props: ["connectionMessage", "connectionStatus", "open"],
            template: `
              <div
                class="model-settings-panel-stub"
                :data-message="connectionMessage"
                :data-open="String(open)"
                :data-status="connectionStatus"
              >
                <button class="test-connection-stub" type="button" @click="$emit('test-connection', { apiKey: ' sk-test ', baseUrl: ' https://api.example.com ', model: ' gpt-4.1-mini ' })">
                  test
                </button>
              </div>
            `
          }
        }
      }
    });

    await chatView.find(".settings-button").trigger("click");
    await chatView.find(".test-connection-stub").trigger("click");
    await flushPromises();

    expect(chatView.find(".model-settings-panel-stub").attributes("data-status")).toBe("error");
    expect(chatView.find(".model-settings-panel-stub").attributes("data-message")).toBe(
      "The API key is invalid or does not have permission. Check the model settings and try again."
    );
  });

  test("shows user-friendly busy feedback for connection test failures returned by the model API", async () => {
    testModelConnectionMock.mockResolvedValueOnce({
      success: false,
      message: '429 - {"error":{"code":"1305","message":"该模型当前访问量过大，请您稍后再试"}}'
    });

    const chatView = mount(ChatView, {
      props: {
        authVerified: true,
        dictionary,
        locale: "en"
      },
      global: {
        stubs: {
          ChatInput: {
            template: "<div class='chat-input-stub'></div>"
          },
          ChatMessageList: {
            template: "<div class='chat-message-list-stub'></div>"
          },
          ExampleSidebar: {
            template: "<aside class='example-sidebar-stub'></aside>"
          },
          LanguageSwitcher: {
            template: "<button type='button'>lang</button>"
          },
          ModelSettingsPanel: {
            props: ["connectionMessage", "connectionStatus", "open"],
            template: `
              <div
                class="model-settings-panel-stub"
                :data-message="connectionMessage"
                :data-open="String(open)"
                :data-status="connectionStatus"
              >
                <button class="test-connection-stub" type="button" @click="$emit('test-connection', { apiKey: ' sk-test ', baseUrl: ' https://api.example.com ', model: ' glm-4 ' })">
                  test
                </button>
              </div>
            `
          }
        }
      }
    });

    await chatView.find(".settings-button").trigger("click");
    await chatView.find(".test-connection-stub").trigger("click");
    await flushPromises();

    expect(chatView.find(".model-settings-panel-stub").attributes("data-status")).toBe("error");
    expect(chatView.find(".model-settings-panel-stub").attributes("data-message")).toBe(
      "The model is busy right now. Please try again shortly."
    );
  });
});
