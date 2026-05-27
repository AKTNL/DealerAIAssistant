import { mount } from "@vue/test-utils";
import ModelSettingsPanel from "../layout/ModelSettingsPanel.vue";

const dictionary = {
  cancelButton: "Cancel",
  modelSettingsApiKeyLabel: "API key",
  modelSettingsBaseUrlLabel: "Base URL",
  modelSettingsDescription: "Configure the model connection used for the next request.",
  modelSettingsModelLabel: "Model",
  modelSettingsResetButton: "Reset",
  modelSettingsSaveButton: "Save",
  modelSettingsTestButton: "Test Connection",
  modelSettingsTitle: "Model settings"
};

describe("ModelSettingsPanel", () => {
  test("renders the draft settings and emits actions with the current form state", async () => {
    const wrapper = mount(ModelSettingsPanel, {
      props: {
        dictionary,
        modelSettings: {
          apiKey: "sk-test",
          baseUrl: "https://api.openai.com",
          model: "gpt-4o-mini"
        },
        open: true
      }
    });

    expect(wrapper.find('input[name="baseUrl"]').element.value).toBe("https://api.openai.com");
    expect(wrapper.find('input[name="apiKey"]').element.value).toBe("****test");
    expect(wrapper.find('input[name="model"]').element.value).toBe("gpt-4o-mini");

    await wrapper.find('input[name="model"]').setValue("gpt-4.1-mini");
    await wrapper.find(".test-connection-button").trigger("click");
    await wrapper.find(".save-button").trigger("click");
    await wrapper.find(".reset-button").trigger("click");
    await wrapper.find(".cancel-button").trigger("click");

    expect(wrapper.emitted("test-connection")).toEqual([
      [
        {
          apiKey: "sk-test",
          baseUrl: "https://api.openai.com",
          model: "gpt-4.1-mini"
        }
      ]
    ]);
    expect(wrapper.emitted("save")).toEqual([
      [
        {
          apiKey: "sk-test",
          baseUrl: "https://api.openai.com",
          model: "gpt-4.1-mini"
        }
      ]
    ]);
    expect(wrapper.emitted("reset")).toEqual([[]]);
    expect(wrapper.emitted("cancel")).toEqual([[]]);
  });

  test("keeps the original API key when the masked field is not edited", async () => {
    const wrapper = mount(ModelSettingsPanel, {
      props: {
        dictionary,
        modelSettings: {
          apiKey: "sk-live-secret-1234",
          baseUrl: "https://api.openai.com",
          model: "gpt-4o-mini"
        },
        open: true
      }
    });

    const apiKeyInput = wrapper.find('input[name="apiKey"]');

    expect(apiKeyInput.element.value).toBe("****1234");

    await wrapper.find(".save-button").trigger("click");

    expect(wrapper.emitted("save")).toEqual([
      [
        {
          apiKey: "sk-live-secret-1234",
          baseUrl: "https://api.openai.com",
          model: "gpt-4o-mini"
        }
      ]
    ]);
  });

  test("hides the panel content when closed", () => {
    const wrapper = mount(ModelSettingsPanel, {
      props: {
        dictionary,
        modelSettings: {
          apiKey: "",
          baseUrl: "",
          model: ""
        },
        open: false
      }
    });

    expect(wrapper.find('[data-testid="model-settings-panel"]').exists()).toBe(false);
  });

  test("renders prompt and connection status state from the parent", () => {
    const wrapper = mount(ModelSettingsPanel, {
      props: {
        connectionMessage: "Save base URL, API key, and model before sending a chat message.",
        connectionStatus: "error",
        dictionary,
        isTestingConnection: true,
        modelSettings: {
          apiKey: "",
          baseUrl: "",
          model: ""
        },
        open: true
      }
    });

    expect(wrapper.find(".model-settings-feedback").text()).toContain(
      "Save base URL, API key, and model before sending a chat message."
    );
    expect(wrapper.find(".model-settings-feedback").classes()).toContain("is-error");
    expect(wrapper.find(".test-connection-button").attributes("disabled")).toBeDefined();
  });
});
