import { defineComponent, ref } from "vue";
import { mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  addPrice: vi.fn(),
  getBudget: vi.fn(),
  getSummary: vi.fn(),
  listEvents: vi.fn(),
  listPrices: vi.fn(),
  saveBudget: vi.fn()
}));

vi.mock("../../api/modelUsage", () => ({
  addModelPrice: (...args) => api.addPrice(...args),
  getModelBudget: (...args) => api.getBudget(...args),
  getModelUsageSummary: (...args) => api.getSummary(...args),
  listModelPrices: (...args) => api.listPrices(...args),
  listModelUsageEvents: (...args) => api.listEvents(...args),
  saveModelBudget: (...args) => api.saveBudget(...args)
}));

import { useModelUsage } from "../useModelUsage";

describe("useModelUsage", () => {
  beforeEach(() => {
    Object.values(api).forEach((mockFunction) => mockFunction.mockReset());
    api.getSummary.mockResolvedValue({ total: { calls: 3 } });
    api.listEvents.mockResolvedValue([{ callKey: "call-1" }]);
    api.listPrices.mockResolvedValue([{ id: 1 }]);
    api.getBudget.mockResolvedValue({ version: 2, monthlyLimit: 100 });
  });

  it("loads summary, events, prices, and budget for readers", async () => {
    const wrapper = mountHarness();
    const range = { from: "from", to: "to" };

    await wrapper.vm.load(range);

    expect(api.getSummary).toHaveBeenCalledWith(range);
    expect(api.listEvents).toHaveBeenCalledWith(range);
    expect(wrapper.vm.summary.total.calls).toBe(3);
    expect(wrapper.vm.events).toHaveLength(1);
    expect(wrapper.vm.prices).toHaveLength(1);
    expect(wrapper.vm.budget.version).toBe(2);
  });

  it("preserves state and classifies an optimistic budget conflict", async () => {
    const wrapper = mountHarness();
    await wrapper.vm.load();
    api.saveBudget.mockRejectedValue({ status: 409 });

    const result = await wrapper.vm.saveBudget({ monthlyLimit: 200, version: 2 });

    expect(result).toBeNull();
    expect(wrapper.vm.budget.monthlyLimit).toBe(100);
    expect(wrapper.vm.operationError.message).toBe("conflict");
  });

  it("delegates expired sessions during loading", async () => {
    const onAuthExpired = vi.fn();
    api.getSummary.mockRejectedValue({ status: 401 });
    const wrapper = mountHarness(onAuthExpired);

    await wrapper.vm.load();

    expect(onAuthExpired).toHaveBeenCalledOnce();
    expect(wrapper.vm.loadError.message).toBe("failed");
  });
});

function mountHarness(onAuthExpired = vi.fn()) {
  const Harness = defineComponent({
    setup() {
      return useModelUsage({
        currentUser: ref({ permissions: ["MODEL_USAGE_READ", "MODEL_USAGE_MANAGE"] }),
        dictionary: ref({
          modelUsageBudgetSaved: "saved",
          modelUsagePriceSaved: "price saved",
          modelUsageValidationError: "invalid",
          modelUsageForbiddenError: "forbidden",
          modelUsageConflictError: "conflict",
          modelUsageRequestError: "failed"
        }),
        onAuthExpired
      });
    },
    template: "<div />"
  });
  return mount(Harness);
}
