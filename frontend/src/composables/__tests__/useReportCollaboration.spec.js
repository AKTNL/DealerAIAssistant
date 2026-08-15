import { defineComponent, ref } from "vue";
import { mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  addComment: vi.fn(),
  changeAssignee: vi.fn(),
  changeStatus: vi.fn(),
  getDetail: vi.fn(),
  listAssignees: vi.fn(),
  listReports: vi.fn()
}));

vi.mock("../../api/reportCollaboration", () => ({
  addReportCollaborationComment: (...args) => api.addComment(...args),
  changeReportCollaborationAssignee: (...args) => api.changeAssignee(...args),
  changeReportCollaborationStatus: (...args) => api.changeStatus(...args),
  getReportCollaboration: (...args) => api.getDetail(...args),
  listReportCollaborationAssignees: (...args) => api.listAssignees(...args),
  listReportCollaborations: (...args) => api.listReports(...args)
}));

import { useReportCollaboration } from "../useReportCollaboration";

describe("useReportCollaboration", () => {
  beforeEach(() => {
    Object.values(api).forEach((mockFunction) => mockFunction.mockReset());
    api.listReports.mockResolvedValue([summary()]);
    api.getDetail.mockResolvedValue(detail());
    api.listAssignees.mockResolvedValue([{ userId: 7, displayName: "Analyst", username: "analyst" }]);
  });

  it("loads the queue and keeps list state synchronized after a versioned mutation", async () => {
    const wrapper = mountHarness();
    await wrapper.vm.initialize();
    api.changeStatus.mockResolvedValue(detail({ status: "IN_PROGRESS", version: 1 }));

    await wrapper.vm.changeStatus("IN_PROGRESS");

    expect(api.changeStatus).toHaveBeenCalledWith("report-1", "IN_PROGRESS", 0);
    expect(wrapper.vm.selected.report.status).toBe("IN_PROGRESS");
    expect(wrapper.vm.reports[0].version).toBe(1);
    expect(wrapper.vm.successMessage).toBe("status updated");
  });

  it("exposes the current server version only for optimistic concurrency conflicts", async () => {
    const wrapper = mountHarness();
    await wrapper.vm.initialize();
    api.changeStatus.mockRejectedValue({
      status: 409,
      body: JSON.stringify({ data: { currentVersion: 4 } })
    });

    await wrapper.vm.changeStatus("IN_PROGRESS");

    expect(wrapper.vm.hasConflict).toBe(true);
    expect(wrapper.vm.conflictVersion).toBe(4);
    expect(wrapper.vm.operationError).toBe("version conflict");
    expect(wrapper.vm.selected.report.version).toBe(0);
  });

  it("keeps state-machine rejections distinct from version conflicts", async () => {
    const wrapper = mountHarness();
    await wrapper.vm.initialize();
    api.changeStatus.mockRejectedValue({ status: 409, body: JSON.stringify({ message: "not allowed" }) });

    await wrapper.vm.changeStatus("CLOSED");

    expect(wrapper.vm.hasConflict).toBe(false);
    expect(wrapper.vm.operationError).toBe("state conflict");
  });
});

function mountHarness() {
  const Harness = defineComponent({
    setup() {
      return useReportCollaboration({
        currentUser: ref({ permissions: ["REPORT_READ", "REPORT_COLLABORATE"] }),
        dictionary: ref({
          collaborationStatusUpdated: "status updated",
          collaborationAssigneeUpdated: "assignee updated",
          collaborationCommentAdded: "comment added",
          collaborationForbiddenError: "forbidden",
          collaborationConflictError: "version conflict",
          collaborationStateConflictError: "state conflict",
          collaborationValidationError: "invalid",
          collaborationRequestError: "failed"
        }),
        onAuthExpired: vi.fn()
      });
    },
    template: "<div />"
  });
  return mount(Harness);
}

function summary(overrides = {}) {
  return {
    reportId: "report-1",
    title: "Daily report",
    reportType: "daily",
    language: "en",
    generatedAt: "2026-08-15T01:00:00Z",
    scope: { type: "ORGANIZATION", id: "11" },
    status: "OPEN",
    assignee: null,
    updatedAt: "2026-08-15T01:00:00Z",
    version: 0,
    ...overrides
  };
}

function detail(overrides = {}) {
  return {
    report: summary(overrides),
    markdown: "# Report",
    timeline: []
  };
}
