import { mount } from "@vue/test-utils";
import { defineComponent } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useDashboard } from "../useDashboard";

const getDashboardSummaryMock = vi.fn();

vi.mock("../../api/dashboard", () => ({
  getDashboardSummary: () => getDashboardSummaryMock()
}));

function mountHarness(options = {}) {
  const Harness = defineComponent({
    setup() {
      return useDashboard(options);
    },
    template: "<div />"
  });
  return mount(Harness);
}

describe("useDashboard", () => {
  beforeEach(() => {
    getDashboardSummaryMock.mockReset();
  });

  it("loads dashboard data on mount", async () => {
    getDashboardSummaryMock.mockResolvedValue({ overview: { dealerCount: 2 } });
    const wrapper = mountHarness();

    await vi.waitFor(() => expect(wrapper.vm.dashboard).toEqual({ overview: { dealerCount: 2 } }));

    expect(wrapper.vm.dashboardLoading).toBe(false);
    expect(wrapper.vm.dashboardError).toBe("");
  });

  it("records non-auth load failures", async () => {
    getDashboardSummaryMock.mockRejectedValue(new Error("Network failed"));
    const wrapper = mountHarness();

    await vi.waitFor(() => expect(wrapper.vm.dashboardError).toBe("Network failed"));

    expect(wrapper.vm.dashboard).toBeNull();
    expect(wrapper.vm.dashboardLoading).toBe(false);
  });

  it("delegates expired auth to the caller", async () => {
    const onAuthExpired = vi.fn();
    getDashboardSummaryMock.mockRejectedValue({ status: 401, message: "Login session expired." });
    mountHarness({ onAuthExpired });

    await vi.waitFor(() => expect(onAuthExpired).toHaveBeenCalledTimes(1));
  });
});
