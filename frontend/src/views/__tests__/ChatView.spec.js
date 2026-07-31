import { mount } from "@vue/test-utils";
import { ref } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import TopNav from "../../components/layout/TopNav.vue";
import ChatView from "../ChatView.vue";

const getDataStatusMock = vi.fn();
const useDashboardMock = vi.fn(() => ({
  dashboard: ref(null),
  dashboardError: "",
  dashboardLoading: false,
  loadDashboard: vi.fn()
}));

vi.mock("../../api/dataStatus", () => ({
  getDataStatus: () => getDataStatusMock()
}));

vi.mock("../../composables/useDashboard", () => ({
  useDashboard: (...args) => useDashboardMock(...args)
}));

const useChatMock = vi.fn(() => ({
  closeMobileSidebar: vi.fn(),
  handleClearSession: vi.fn(),
  handleScroll: vi.fn(),
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
  statusMessage: "Guest",
  stopGenerating: vi.fn(),
  streamPhase: ref("idle"),
  submitPrompt: vi.fn(),
  toastMessage: "",
}));

vi.mock("../../composables/useChat", () => ({
  useChat: (...args) => useChatMock(...args)
}));

const dictionary = {
  appName: "Dealer AI Assistant",
  clearChat: "Clear session",
  jumpToLatest: "Jump to latest",
  logoutButton: "Sign out",
  modelSettingsApiKeyLabel: "API key",
  modelSettingsBaseUrlLabel: "Base URL",
  modelSettingsDescription: "Configure the model connection used for the next request.",
  modelSettingsModelLabel: "Model",
  modelSettingsResetButton: "Reset",
  modelSettingsSaveButton: "Save",
  modelSettingsTestButton: "Test Connection",
  modelSettingsTitle: "Model settings",
  newChat: "New chat",
  settingsButton: "Settings",
  sampleDataWarning: "Built-in sample data is active.",
  switchLanguage: "Switch language",
  workspaceTitle: "Dealer workspace",
  dashboardTab: "Dashboard",
  chatTab: "AI analysis"
};

function mountChatView(props = {}) {
  return mount(ChatView, {
    props: {
      authVerified: false,
      dictionary,
      locale: "en",
      ...props
    },
    global: {
      stubs: {
        ChatInput: {
          template: "<div class='chat-input-stub'></div>"
        },
        ChatMessageList: {
          template: "<div class='chat-message-list-stub'></div>"
        },
        DashboardView: {
          template: "<section class='dashboard-view-stub'></section>"
        },
        ExampleSidebar: {
          template: "<aside class='example-sidebar-stub'></aside>"
        },
        ModelSettingsPanel: {
          template: "<div class='model-settings-panel-stub'></div>"
        }
      }
    }
  });
}

beforeEach(() => {
  useChatMock.mockClear();
  useDashboardMock.mockClear();
  getDataStatusMock.mockReset();
  getDataStatusMock.mockResolvedValue({ fallbackActive: false, source: "configured-workbook" });
});

describe("ChatView", () => {
  it("passes the parent auth state to TopNav and useChat", () => {
    const wrapper = mountChatView({ authVerified: false });

    expect(wrapper.findComponent(TopNav).props("authVerified")).toBe(false);
    expect(useChatMock).toHaveBeenCalledTimes(1);
    expect(useChatMock.mock.calls[0][0].authVerified.value).toBe(false);
  });

  it("starts with the example sidebar collapsed", () => {
    const wrapper = mountChatView({ authVerified: true });

    expect(wrapper.find(".workspace-shell").classes()).toContain("sidebar-collapsed");
  });

  it("opens on the dashboard workspace by default", () => {
    const wrapper = mountChatView({ authVerified: true });

    expect(wrapper.find(".dashboard-view-stub").exists()).toBe(true);
    expect(wrapper.find(".chat-screen").exists()).toBe(false);
  });

  it("shows a warning when the backend reports fallback sample data", async () => {
    getDataStatusMock.mockResolvedValueOnce({ fallbackActive: true, source: "built-in-sample" });

    const wrapper = mountChatView({ authVerified: true });
    await vi.waitFor(() => expect(wrapper.find(".data-source-warning").exists()).toBe(true));

    expect(wrapper.find(".data-source-warning").text()).toContain("Built-in sample data is active.");
  });

  it("fills the composer when a sidebar prompt is selected without submitting", async () => {
    const closeMobileSidebar = vi.fn();
    const promptInput = ref("");
    const submitPrompt = vi.fn();
    const question = "Which dealers have low target achievement?";

    useChatMock.mockReturnValueOnce({
      closeMobileSidebar,
      handleClearSession: vi.fn(),
      handleScroll: vi.fn(),
      hasUnreadContent: false,
      isSending: false,
      jumpToLatest: vi.fn(),
      messages: ref([]),
      openMobileSidebar: vi.fn(),
      promptInput,
      requestError: "",
      scrollContainer: ref(null),
      showMobileSidebar: true,
      startNewChat: vi.fn(),
      statusMessage: "Guest",
      stopGenerating: vi.fn(),
      streamPhase: ref("idle"),
      submitPrompt,
      toastMessage: ""
    });

    const wrapper = mount(ChatView, {
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
          DashboardView: {
            template: "<section class='dashboard-view-stub'></section>"
          },
          ExampleSidebar: {
            template: `
              <button class="sidebar-question-stub" type="button" @click="$emit('select-prompt', '${question}')">
                prompt
              </button>
            `
          },
          ModelSettingsPanel: {
            template: "<div class='model-settings-panel-stub'></div>"
          }
        }
      }
    });

    await wrapper.find(".sidebar-question-stub").trigger("click");

    expect(promptInput.value).toBe(question);
    expect(closeMobileSidebar).toHaveBeenCalledTimes(1);
    expect(submitPrompt).not.toHaveBeenCalled();
    expect(wrapper.find(".chat-screen").exists()).toBe(true);
  });

  it("submits dashboard analysis prompts through the existing chat flow", async () => {
    const submitPrompt = vi.fn();

    useChatMock.mockReturnValueOnce({
      closeMobileSidebar: vi.fn(),
      handleClearSession: vi.fn(),
      handleScroll: vi.fn(),
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
      statusMessage: "Ready",
      stopGenerating: vi.fn(),
      streamPhase: ref("idle"),
      submitPrompt,
      toastMessage: ""
    });

    const wrapper = mount(ChatView, {
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
          DashboardView: {
            template: `
              <button class="dashboard-analyze-stub" type="button" @click="$emit('analyze', 'Which dealers are lagging?')">
                analyze
              </button>
            `
          },
          ExampleSidebar: {
            template: "<aside class='example-sidebar-stub'></aside>"
          },
          ModelSettingsPanel: {
            template: "<div class='model-settings-panel-stub'></div>"
          }
        }
      }
    });

    await wrapper.find(".dashboard-analyze-stub").trigger("click");

    expect(submitPrompt).toHaveBeenCalledWith("Which dealers are lagging?");
    expect(wrapper.find(".chat-screen").exists()).toBe(true);
  });
});
