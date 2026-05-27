import { describe, expect, it } from "vitest";
import { normalizeAssistantPayload } from "../chat";

describe("normalizeAssistantPayload", () => {
  it("extracts follow-up questions from Chinese marker", () => {
    const result = normalizeAssistantPayload(
      "核心结论内容\n\n追问：\n1. 朝阳店的达成短板是什么？\n2. 要不要对比其他门店？"
    );
    expect(result.content).toBe("核心结论内容");
    expect(result.followUps).toEqual([
      "朝阳店的达成短板是什么？",
      "要不要对比其他门店？"
    ]);
  });

  it("deduplicates identical follow-up questions", () => {
    const result = normalizeAssistantPayload(
      "Some content\n\nFOLLOW_UP_QUESTIONS:\n1. Same question?\n2. Same question?"
    );
    expect(result.followUps).toHaveLength(1);
    expect(result.followUps[0]).toBe("Same question?");
  });

  it("returns empty followUps when no marker present", () => {
    const result = normalizeAssistantPayload("Just a plain reply with no follow-ups.");
    expect(result.content).toBe("Just a plain reply with no follow-ups.");
    expect(result.followUps).toEqual([]);
  });

  it("extracts follow-ups with mixed formatting", () => {
    const result = normalizeAssistantPayload(
      "Content\n\nFOLLOW_UP_QUESTIONS:\n- **First question?**\n- *Second question?*"
    );
    expect(result.followUps).toEqual([
      "First question?",
      "Second question?"
    ]);
  });

  it("loose fallback extracts lines even without proper numbering", () => {
    const result = normalizeAssistantPayload(
      "Content\n\n追问：\n   first question here   \n   second question here   "
    );
    expect(result.followUps).toHaveLength(2);
  });
});
