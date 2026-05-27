import { mount } from "@vue/test-utils";
import { ref } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import TopNav from "../../components/layout/TopNav.vue";
import ChatView from "../ChatView.vue";

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
  switchLanguage: "Switch language"
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
});

describe("ChatView", () => {
  it("passes the parent auth state to TopNav and useChat", () => {
    const wrapper = mountChatView({ authVerified: false });

    expect(wrapper.findComponent(TopNav).props("authVerified")).toBe(false);
    expect(useChatMock).toHaveBeenCalledTimes(1);
    expect(useChatMock.mock.calls[0][0].authVerified.value).toBe(false);
  });
});
