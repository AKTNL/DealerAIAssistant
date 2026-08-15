import { flushPromises, mount } from "@vue/test-utils";
import { ref } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";

const state = vi.hoisted(() => ({ usage: null }));

vi.mock("../../composables/useModelUsage", () => ({
  useModelUsage: () => state.usage
}));

import ModelUsagePanel from "../admin/ModelUsagePanel.vue";

const dictionary = {
  adminRefresh: "Refresh",
  adminRetry: "Retry",
  modelUsageAddPrice: "Add price",
  modelUsageAnomalies: "Anomalies",
  modelUsageBody: "Tenant usage",
  modelUsageBudgetPolicy: "Budget policy",
  modelUsageBudgetStatus: "Budget status",
  modelUsageBudgetUsage: "Budget used",
  modelUsageByModel: "By model",
  modelUsageByScenario: "By scenario",
  modelUsageCalls: "Calls",
  modelUsageCurrency: "Currency",
  modelUsageEffectiveFrom: "Effective from",
  modelUsageEmpty: "No calls",
  modelUsageEstimatedCost: "Estimated cost",
  modelUsageFailOpen: "Fail open",
  modelUsageFailures: "Failures",
  modelUsageFrom: "From",
  modelUsageHardLimit: "Hard limit",
  modelUsageInputPrice: "Input price",
  modelUsageInputTokens: "Input tokens",
  modelUsageLoading: "Loading",
  modelUsageModel: "Model",
  modelUsageMonthSpend: "Month spend",
  modelUsageMonthlyLimit: "Monthly limit",
  modelUsageNoAnomalies: "No anomalies",
  modelUsageNoPrices: "No prices",
  modelUsageOutputPrice: "Output price",
  modelUsageOutputTokens: "Output tokens",
  modelUsagePriceHistory: "Price history",
  modelUsageProvider: "Provider",
  modelUsageReason: "Reason",
  modelUsageRecentEvents: "Recent events",
  modelUsageReservation: "Reservation",
  modelUsageSaveBudget: "Save budget",
  modelUsageSavePrice: "Save price",
  modelUsageScenario: "Scenario",
  modelUsageSoftThreshold: "Threshold",
  modelUsageSource: "Source",
  modelUsageStatus: "Status",
  modelUsageTime: "Time",
  modelUsageTitle: "Model usage",
  modelUsageTo: "To",
  modelUsageTokens: "Tokens",
  modelUsageTrace: "Trace",
  modelUsageUnknown: "Unknown",
  modelUsageUnknownTokens: "Unknown tokens",
  modelUsageVersionKey: "Version key",
  modelUsageStatusLabels: { OK: "OK", SUCCESS: "Success" },
  modelUsageScenarioLabels: { CHAT: "Chat" },
  modelUsageAnomalyLabels: {}
};

describe("ModelUsagePanel", () => {
  beforeEach(() => {
    state.usage = usageState();
  });

  it("loads and renders a read-only usage view without mutation forms", async () => {
    const wrapper = mountPanel(["MODEL_USAGE_READ"]);
    await flushPromises();

    expect(state.usage.load).toHaveBeenCalledOnce();
    expect(wrapper.text()).toContain("12");
    expect(wrapper.find(".model-usage-management").exists()).toBe(false);
    expect(wrapper.text()).toContain("Chat");
  });

  it("shows management forms only with manage permission and submits versions", async () => {
    state.usage = usageState({ canManage: ref(true) });
    const wrapper = mountPanel(["MODEL_USAGE_READ", "MODEL_USAGE_MANAGE"]);
    await flushPromises();

    const forms = wrapper.findAll(".model-usage-management form");
    expect(forms).toHaveLength(2);
    await forms[0].trigger("submit");
    await forms[1].find('input[type="text"][maxlength="128"]').setValue("gpt-test");
    await forms[1].trigger("submit");

    expect(state.usage.saveBudget).toHaveBeenCalledWith(expect.objectContaining({ version: 3 }));
    expect(state.usage.addPrice).toHaveBeenCalledWith(expect.objectContaining({ model: "gpt-test" }));
  });
});

function mountPanel(permissions) {
  return mount(ModelUsagePanel, {
    props: {
      currentUser: { id: 1, permissions },
      dictionary,
      locale: "en"
    }
  });
}

function usageState(overrides = {}) {
  return {
    addPrice: vi.fn().mockResolvedValue(null),
    budget: ref({
      monthlyLimit: 100,
      softThresholdPercent: 80,
      hardLimitEnabled: false,
      failOpen: true,
      reservationAmount: 0,
      currency: "USD",
      monthToDateCost: 3.5,
      usagePercent: 3.5,
      state: "OK",
      version: 3
    }),
    canManage: ref(false),
    canRead: ref(true),
    clearFeedback: vi.fn(),
    events: ref([{ callKey: "call-1", occurredAt: "2026-08-15T00:00:00Z", scenario: "CHAT", provider: "openai-compatible", model: "gpt-test", status: "SUCCESS", tokenState: "KNOWN", totalTokens: 30, estimatedCost: 0.01, currency: "USD", traceId: "trace-1" }]),
    load: vi.fn().mockResolvedValue(undefined),
    loadError: ref(null),
    loading: ref(false),
    operationError: ref(null),
    pendingAction: ref(""),
    prices: ref([]),
    saveBudget: vi.fn().mockResolvedValue(null),
    successMessage: ref(""),
    summary: ref({
      total: { calls: 12, errorCalls: 1, rejectedCalls: 0, unknownTokenCalls: 2, inputTokens: 100, outputTokens: 50, durationMs: 80, costs: [{ currency: "USD", amount: 0.01 }] },
      byScenario: [{ key: "CHAT", calls: 12, costs: [{ currency: "USD", amount: 0.01 }] }],
      byModel: [],
      anomalies: []
    }),
    ...overrides
  };
}
