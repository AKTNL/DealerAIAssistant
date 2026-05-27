import { mount } from "@vue/test-utils";
import { describe, expect, test } from "vitest";
import ExampleSidebar from "../layout/ExampleSidebar.vue";

const zhDictionary = {
  appName: "Dealer AI",
  closeMenu: "Close",
  footerNote: "Internal",
  newChat: "New chat",
  sidebarSection: "Suggested flows",
  workspaceTitle: "Workspace",
  promptModules: [
    {
      id: "target-achievement",
      title: "Target Achievement CN",
      questions: [
        "CN: Which dealers have the lowest target achievement this month?",
        "CN: Which stores are most likely to drag down target achievement by region?"
      ]
    },
    {
      id: "funnel-conversion",
      title: "Funnel Conversion CN",
      questions: [
        "CN: How does the opportunity funnel conversion look in Shanghai?",
        "CN: Which dealers show the sharpest drop from leads to test drives?"
      ]
    }
  ]
};

const enDictionary = {
  ...zhDictionary,
  promptModules: [
    {
      id: "funnel-conversion",
      title: "Funnel Conversion",
      questions: [
        "How does the opportunity funnel conversion look in Shanghai?",
        "Which dealers show the sharpest drop from leads to test drives?"
      ]
    },
    {
      id: "target-achievement",
      title: "Target Achievement",
      questions: [
        "Which dealers have the lowest target achievement this month?",
        "Which stores are most likely to drag down target achievement by region?"
      ]
    }
  ]
};

describe("ExampleSidebar", () => {
  test("renders the new chat action in a dedicated sidebar action block", () => {
    const wrapper = mount(ExampleSidebar, {
      props: {
        dictionary: zhDictionary
      }
    });

    expect(wrapper.find(".sidebar-action-block").exists()).toBe(true);
    expect(wrapper.find(".primary-sidebar-button").text()).toContain("New chat");
  });

  test("does not render the removed flat prompt fallback when promptModules are absent", () => {
    const wrapper = mount(ExampleSidebar, {
      props: {
        dictionary: {
          ...zhDictionary,
          promptModules: undefined,
          prompts: ["Legacy prompt"]
        }
      }
    });

    expect(wrapper.text()).not.toContain("Legacy prompt");
    expect(wrapper.findAll(".prompt-card")).toHaveLength(0);
    expect(wrapper.findAll(".sidebar-module-toggle")).toHaveLength(0);
  });

  test("keeps modules collapsed by default and expands one module at a time", async () => {
    const wrapper = mount(ExampleSidebar, {
      props: {
        dictionary: zhDictionary
      }
    });

    expect(wrapper.findAll(".sidebar-module-question")).toHaveLength(0);

    const headers = wrapper.findAll(".sidebar-module-toggle");

    expect(headers).toHaveLength(2);
    expect(headers[0].attributes("aria-expanded")).toBe("false");
    expect(headers[1].attributes("aria-expanded")).toBe("false");

    await headers[0].trigger("click");

    expect(headers[0].attributes("aria-expanded")).toBe("true");
    expect(wrapper.text()).toContain(zhDictionary.promptModules[0].questions[0]);
    expect(wrapper.text()).toContain(zhDictionary.promptModules[0].questions[1]);

    await headers[1].trigger("click");

    expect(headers[0].attributes("aria-expanded")).toBe("false");
    expect(headers[1].attributes("aria-expanded")).toBe("true");
    expect(wrapper.text()).not.toContain(zhDictionary.promptModules[0].questions[0]);
    expect(wrapper.text()).toContain(zhDictionary.promptModules[1].questions[0]);
    expect(wrapper.emitted("submit-prompt")).toBeUndefined();
  });

  test("shows local pending state on the clicked question and clears it when sending finishes", async () => {
    const wrapper = mount(ExampleSidebar, {
      props: {
        dictionary: zhDictionary,
        isSending: false
      }
    });

    await wrapper.findAll(".sidebar-module-toggle")[0].trigger("click");

    const questions = wrapper.findAll(".sidebar-module-question");

    expect(questions).toHaveLength(2);

    await questions[0].trigger("click");
    await questions[0].trigger("click");

    expect(wrapper.emitted("submit-prompt")).toEqual([[zhDictionary.promptModules[0].questions[0]]]);
    expect(wrapper.find(".sidebar-module-question.is-pending").exists()).toBe(true);
    expect(wrapper.find(".sidebar-module-question.is-pending").text()).toContain(
      zhDictionary.promptModules[0].questions[0]
    );
    expect(wrapper.find(".sidebar-question-spinner").exists()).toBe(true);
    expect(wrapper.findAll(".sidebar-module-question").every((question) => question.attributes("disabled") !== undefined)).toBe(true);

    await wrapper.setProps({ isSending: true });

    expect(wrapper.findAll(".sidebar-module-question").every((question) => question.attributes("disabled") !== undefined)).toBe(true);

    await wrapper.setProps({ isSending: false });

    expect(wrapper.find(".sidebar-module-question.is-pending").exists()).toBe(false);
    expect(wrapper.find(".sidebar-question-spinner").exists()).toBe(false);
  });

  test("preserves the expanded module across locale switching", async () => {
    const wrapper = mount(ExampleSidebar, {
      props: {
        dictionary: zhDictionary
      }
    });

    const headers = wrapper.findAll(".sidebar-module-toggle");
    await headers[1].trigger("click");

    expect(headers[1].attributes("aria-expanded")).toBe("true");
    expect(wrapper.text()).toContain(zhDictionary.promptModules[1].title);
    expect(wrapper.text()).toContain(zhDictionary.promptModules[1].questions[0]);

    await wrapper.setProps({ dictionary: enDictionary });

    const updatedHeaders = wrapper.findAll(".sidebar-module-toggle");

    expect(updatedHeaders[0].attributes("aria-expanded")).toBe("true");
    expect(updatedHeaders[1].attributes("aria-expanded")).toBe("false");
    expect(wrapper.text()).toContain(enDictionary.promptModules[0].title);
    expect(wrapper.text()).toContain(enDictionary.promptModules[0].questions[0]);
    expect(wrapper.text()).not.toContain(zhDictionary.promptModules[1].questions[0]);
    expect(wrapper.findAll(".sidebar-module-question")).toHaveLength(2);
  });
});
