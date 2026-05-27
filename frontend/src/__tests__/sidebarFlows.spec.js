import { describe, expect, test } from "vitest";
import { enSidebarFlows, zhSidebarFlows } from "../constants/sidebarFlows";
import { messages } from "../i18n/messages";

const correctedZhFunnelQuestions = [
  "Vega GT 和 Terra XL 在不同购车周期下的商机阶段分布如何？",
  "最近已战败商机主要集中在哪些车型，核心战败原因是什么？"
];

const correctedEnFunnelQuestions = [
  "How are Vega GT and Terra XL opportunities distributed by purchase horizon and stage?",
  "Which models dominate recent Closed Lost opportunities, and what are the key lost reasons?"
];

function assertFlowModuleShape(modules) {
  expect(Array.isArray(modules)).toBe(true);
  expect(modules).toHaveLength(6);

  for (const module of modules) {
    expect(module.id).toEqual(expect.any(String));
    expect(module.title).toEqual(expect.any(String));
    expect(Array.isArray(module.questions)).toBe(true);
    expect(module.questions).toHaveLength(2);
    module.questions.forEach((question) => {
      expect(question).toEqual(expect.any(String));
    });
    expect(module.summary).toBeUndefined();
    expect(module.logicHint).toBeUndefined();
    expect(module.tools).toBeUndefined();
    expect(module.steps).toBeUndefined();
  }
}

describe("sidebar flow definitions", () => {
  test("ship complete Chinese flow modules", () => {
    assertFlowModuleShape(zhSidebarFlows);
  });

  test("ship complete English flow modules", () => {
    assertFlowModuleShape(enSidebarFlows);
  });

  test("reuse the dedicated locale flow arrays from i18n messages", () => {
    expect(messages.zh.promptModules).toBe(zhSidebarFlows);
    expect(messages.en.promptModules).toBe(enSidebarFlows);
  });

  test("keep locale parity across the shipped flow definitions", () => {
    expect(zhSidebarFlows).toHaveLength(enSidebarFlows.length);

    const zhModuleIds = zhSidebarFlows.map((module) => module.id);
    const enModuleIds = enSidebarFlows.map((module) => module.id);

    expect(new Set(zhModuleIds).size).toBe(zhSidebarFlows.length);
    expect(new Set(enModuleIds).size).toBe(enSidebarFlows.length);
    expect(zhModuleIds).toEqual(enModuleIds);
  });

  test("uses the corrected opportunity funnel questions from the source document", () => {
    const zhFunnelModule = zhSidebarFlows.find((module) => module.id === "funnel-conversion");
    const enFunnelModule = enSidebarFlows.find((module) => module.id === "funnel-conversion");

    expect(zhFunnelModule?.questions).toEqual(correctedZhFunnelQuestions);
    expect(enFunnelModule?.questions).toEqual(correctedEnFunnelQuestions);
  });
});
