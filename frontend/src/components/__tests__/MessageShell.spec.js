import { mount } from "@vue/test-utils";
import AssistantMessage from "../chat/AssistantMessage.vue";
import UserMessage from "../chat/UserMessage.vue";
import { messages } from "../../i18n/messages";

const dictionary = {
  assistantLabel: "Assistant",
  hideThinking: "Hide thought process",
  showThinking: "Show thought process",
  suggestedReplies: "Suggested replies",
  userLabel: "You"
};

describe("Message shell editorial hooks", () => {
  test("renders stable assistant and user shell hook classes", () => {
    const assistant = mount(AssistantMessage, {
      props: {
        dictionary,
        message: {
          followUps: [],
          html: "<p>Hello</p>"
        }
      },
      global: {
        stubs: {
          FollowUpButtons: {
            template: "<div />"
          }
        }
      }
    });
    const user = mount(UserMessage, {
      props: {
        dictionary,
        message: {
          html: "<p>Hi</p>"
        }
      }
    });

    expect(assistant.find(".avatar-assistant").exists()).toBe(true);
    expect(assistant.find(".message-card-assistant").exists()).toBe(true);
    expect(assistant.find(".assistant-header").exists()).toBe(true);
    expect(user.find(".avatar-user").exists()).toBe(true);
    expect(user.find(".message-card-user").exists()).toBe(true);
  });

  test("renders the timeline panel with model thought when mixed steps are provided", () => {
    const assistant = mount(AssistantMessage, {
      props: {
        dictionary,
        locale: "en",
        message: {
          followUps: [],
          html: "<p>Hello</p>",
          streaming: true,
          steps: [
            { type: "detection", seq: 1, ts: Date.now(), status: "completed", label: "Detection", detail: "Analyzing request" },
            { type: "model_thought", seq: 2, ts: Date.now(), status: "completed", label: "Model thought", detail: "step 1 reasoning" }
          ]
        }
      },
      global: {
        stubs: {
          FollowUpButtons: {
            template: "<div />"
          }
        }
      }
    });

    const panel = assistant.find(".timeline-panel");
    expect(panel.exists()).toBe(true);

    const steps = assistant.findAll(".timeline-step");
    expect(steps).toHaveLength(1);

    const firstStep = steps[0];
    expect(firstStep.classes()).toContain("timeline-step--model_thought");
    expect(firstStep.find(".timeline-step-detail").exists()).toBe(true);
    expect(panel.text()).not.toContain("Detection");
  });

  test("shows the thinking indicator while the assistant is still in the thinking phase", () => {
    const assistant = mount(AssistantMessage, {
      props: {
        dictionary,
        locale: "en",
        streamPhase: "thinking",
        message: {
          followUps: [],
          html: "",
          streaming: true
        }
      },
      global: {
        stubs: {
          FollowUpButtons: {
            template: "<div />"
          }
        }
      }
    });

    expect(assistant.find(".thinking-indicator").exists()).toBe(true);
    expect(assistant.text()).not.toContain("Conclusion");
    expect(assistant.text()).not.toContain("Data");
    expect(assistant.text()).not.toContain("Analysis");
  });

  test("uses thought-process labels in the shipped dictionaries", () => {
    expect(messages.en.showThinking).toBe("Show thought process");
    expect(messages.en.hideThinking).toBe("Hide thought process");
    expect(messages.zh.showThinking).toBe("展开思考过程");
    expect(messages.zh.hideThinking).toBe("收起思考过程");
    expect(messages.zh.statusThinking).toBe("助手正在整理回复");
  });
});
