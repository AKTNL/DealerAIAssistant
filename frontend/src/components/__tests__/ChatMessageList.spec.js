import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import ChatMessageList from "../chat/ChatMessageList.vue";

const dictionary = {
  assistantLabel: "Assistant",
  userLabel: "You",
  welcomeTitle: "Welcome",
  welcomeBody: "Start with a prompt.",
  clearSuccess: "Session cleared."
};

const UserMessageStub = {
  props: ["dictionary", "message"],
  template: `
    <article data-test="user-message">
      <span>{{ dictionary.userLabel }}</span>
      <div v-html="message.html"></div>
    </article>
  `
};

const AssistantMessageStub = {
  props: ["dictionary", "locale", "message"],
  emits: ["submit-follow-up"],
  template: `
    <article data-test="assistant-message">
      <span>{{ dictionary.assistantLabel }}</span>
      <span data-test="assistant-locale">{{ locale }}</span>
      <div v-html="message.html"></div>
      <button data-test="follow-up" type="button" @click="$emit('submit-follow-up', 'Next question')">Follow up</button>
    </article>
  `
};

const SystemMessageStub = {
  props: ["dictionary", "type"],
  template: `
    <article data-test="system-message" :data-type="type">
      <h3 v-if="type === 'welcome'">{{ dictionary.welcomeTitle }}</h3>
      <h3 v-else-if="type === 'system-clear'">{{ dictionary.clearSuccess }}</h3>
      <p>{{ dictionary.welcomeBody }}</p>
    </article>
  `
};

function mountList(props = {}) {
  return mount(ChatMessageList, {
    props: {
      dictionary,
      locale: "en",
      messages: [],
      ...props
    },
    global: {
      stubs: {
        AssistantMessage: AssistantMessageStub,
        SystemMessage: SystemMessageStub,
        UserMessage: UserMessageStub
      }
    }
  });
}

describe("ChatMessageList", () => {
  it("renders welcome system message when messages has welcome entry", () => {
    const wrapper = mountList({
      messages: [{ id: "welcome-1", role: "system", type: "welcome" }]
    });

    const systemEl = wrapper.find('[data-test="system-message"]');
    expect(systemEl.exists()).toBe(true);
    expect(systemEl.attributes("data-type")).toBe("welcome");
    expect(systemEl.text()).toContain("Welcome");
  });

  it("renders system-clear message after session clear", () => {
    const wrapper = mountList({
      messages: [{ id: "system-clear-1", role: "system", type: "system-clear" }]
    });

    const systemEl = wrapper.find('[data-test="system-message"]');
    expect(systemEl.exists()).toBe(true);
    expect(systemEl.attributes("data-type")).toBe("system-clear");
    expect(systemEl.text()).toContain("Session cleared.");
  });

  it("renders user messages", () => {
    const wrapper = mountList({
      messages: [
        { id: "user-1", role: "user", html: "<p>Hello</p>" }
      ]
    });

    expect(wrapper.find('[data-test="user-message"]').text()).toContain("You");
    expect(wrapper.find('[data-test="user-message"]').html()).toContain("<p>Hello</p>");
  });

  it("renders assistant messages with locale", () => {
    const wrapper = mountList({
      locale: "zh",
      messages: [
        { id: "assistant-1", role: "assistant", html: "<p>Reply</p>" }
      ]
    });

    expect(wrapper.find('[data-test="assistant-message"]').text()).toContain("Assistant");
    expect(wrapper.find('[data-test="assistant-locale"]').text()).toBe("zh");
    expect(wrapper.find('[data-test="assistant-message"]').html()).toContain("<p>Reply</p>");
  });

  it("passes follow-up events through from assistant messages", async () => {
    const wrapper = mountList({
      messages: [
        { id: "assistant-1", role: "assistant", html: "<p>Reply</p>" }
      ]
    });

    await wrapper.find('[data-test="follow-up"]').trigger("click");

    expect(wrapper.emitted("submit-follow-up")).toEqual([["Next question"]]);
  });

});
