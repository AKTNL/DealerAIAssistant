import { defineComponent, ref } from "vue";
import { mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  changeEnabled: vi.fn(),
  create: vi.fn(),
  remove: vi.fn(),
  forceReplay: vi.fn(),
  listDeliveries: vi.fn(),
  listRecipients: vi.fn(),
  listSubscriptions: vi.fn(),
  retryDelivery: vi.fn(),
  update: vi.fn()
}));

vi.mock("../../api/reportSubscriptions", () => ({
  changeReportSubscriptionEnabled: (...args) => api.changeEnabled(...args),
  createReportSubscription: (...args) => api.create(...args),
  deleteReportSubscription: (...args) => api.remove(...args),
  forceReplayReportDelivery: (...args) => api.forceReplay(...args),
  listReportDeliveries: (...args) => api.listDeliveries(...args),
  listReportSubscriptionRecipients: (...args) => api.listRecipients(...args),
  listReportSubscriptions: (...args) => api.listSubscriptions(...args),
  retryReportDelivery: (...args) => api.retryDelivery(...args),
  updateReportSubscription: (...args) => api.update(...args)
}));

import { useReportSubscriptions } from "../useReportSubscriptions";

describe("useReportSubscriptions", () => {
  beforeEach(() => {
    Object.values(api).forEach((mockFunction) => mockFunction.mockReset());
    api.listSubscriptions.mockResolvedValue([{ id: 1, version: 0 }]);
    api.listRecipients.mockResolvedValue([{ userId: 2, displayName: "Analyst", emailConfigured: true }]);
    api.listDeliveries.mockResolvedValue([]);
  });

  it("loads server state and replaces an enabled subscription", async () => {
    const wrapper = mountHarness();
    await wrapper.vm.initialize();
    api.changeEnabled.mockResolvedValue({ id: 1, version: 1, enabled: false });

    await wrapper.vm.setEnabled(wrapper.vm.subscriptions[0], false);

    expect(wrapper.vm.recipients).toHaveLength(1);
    expect(wrapper.vm.subscriptions[0].enabled).toBe(false);
    expect(wrapper.vm.successMessage).toBe("disabled");
  });

  it("classifies conflicts without mutating the list", async () => {
    const wrapper = mountHarness();
    await wrapper.vm.initialize();
    api.update.mockRejectedValue({ status: 409 });

    const result = await wrapper.vm.update(1, { version: 0 });

    expect(result).toBeNull();
    expect(wrapper.vm.operationError).toBe("conflict");
    expect(wrapper.vm.subscriptions[0].version).toBe(0);
  });
});

function mountHarness() {
  const Harness = defineComponent({
    setup() {
      return useReportSubscriptions({
        currentUser: ref({ permissions: ["REPORT_READ", "REPORT_GENERATE"] }),
        dictionary: ref({
          subscriptionCreated: "created",
          subscriptionUpdated: "updated",
          subscriptionEnabled: "enabled",
          subscriptionDisabled: "disabled",
          subscriptionDeleted: "deleted",
          subscriptionForbiddenError: "forbidden",
          subscriptionConflictError: "conflict",
          subscriptionValidationError: "invalid",
          subscriptionRequestError: "failed"
        }),
        onAuthExpired: vi.fn()
      });
    },
    template: "<div />"
  });
  return mount(Harness);
}
