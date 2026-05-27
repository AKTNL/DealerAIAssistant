import { mount } from "@vue/test-utils";
import { describe, expect, test } from "vitest";

import ChatInput from "../chat/ChatInput.vue";

function mountChatInput(dictionary = { inputPlaceholder: "Ask a question" }) {
  return mount(ChatInput, {
    props: {
      dictionary,
      modelValue: ""
    }
  });
}

describe("ChatInput accessibility", () => {
  test("uses the explicit chat input label when provided", () => {
    const wrapper = mountChatInput({
      chatInputLabel: "Message the assistant",
      inputPlaceholder: "Ask a question"
    });

    expect(wrapper.find("textarea").attributes("aria-label")).toBe("Message the assistant");
  });

  test("falls back to the placeholder for its accessible name", () => {
    const wrapper = mountChatInput();

    expect(wrapper.find("textarea").attributes("aria-label")).toBe("Ask a question");
  });
});

test("caps height at max-height when scrollHeight exceeds 200px", async () => {
  const wrapper = mountChatInput();

  const textarea = wrapper.find("textarea").element;

  Object.defineProperty(textarea, "scrollHeight", {
    configurable: true,
    get() {
      return 350; // exceeds 200px cap
    }
  });

  await wrapper.find("textarea").setValue("a\n".repeat(50));

  expect(textarea.style.height).toBe("200px");
});

test("auto-resizes textarea height on input", async () => {
  const wrapper = mountChatInput();

  const textarea = wrapper.find("textarea").element;

  // Simulate content exceeding default height
  Object.defineProperty(textarea, "scrollHeight", {
    configurable: true,
    get() {
      return 80;
    }
  });

  await wrapper.find("textarea").setValue("Line 1\nLine 2\nLine 3");

  // JS should set height to scrollHeight pixel value
  expect(textarea.style.height).toBe("80px");
});
