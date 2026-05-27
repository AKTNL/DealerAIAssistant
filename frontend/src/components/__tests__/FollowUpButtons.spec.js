import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import FollowUpButtons from "../chat/FollowUpButtons.vue";

const dictionary = {
  followUpLabel: "Follow-up questions"
};

describe("FollowUpButtons", () => {
  it("does not render when there are no follow-ups", () => {
    const wrapper = mount(FollowUpButtons, {
      props: {
        dictionary,
        followUps: []
      }
    });

    expect(wrapper.find(".follow-up-group").exists()).toBe(false);
  });

  it("renders a button for each follow-up", () => {
    const wrapper = mount(FollowUpButtons, {
      props: {
        dictionary,
        followUps: ["First question", "Second question"]
      }
    });

    const buttons = wrapper.findAll("button");
    expect(wrapper.text()).toContain(dictionary.followUpLabel);
    expect(buttons).toHaveLength(2);
    expect(buttons[0].text()).toContain("First question");
    expect(buttons[1].text()).toContain("Second question");
  });

  it("emits the selected follow-up text", async () => {
    const wrapper = mount(FollowUpButtons, {
      props: {
        dictionary,
        followUps: ["First question", "Second question"]
      }
    });

    await wrapper.findAll("button")[1].trigger("click");

    expect(wrapper.emitted("select")).toEqual([["Second question"]]);
  });
});
