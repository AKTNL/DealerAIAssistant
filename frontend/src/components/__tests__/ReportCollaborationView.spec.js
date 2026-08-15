import { flushPromises, mount } from "@vue/test-utils";
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

import ReportCollaborationView from "../reporting/ReportCollaborationView.vue";

describe("ReportCollaborationView", () => {
  beforeEach(() => {
    Object.values(api).forEach((mockFunction) => mockFunction.mockReset());
    api.listReports.mockResolvedValue([summary()]);
    api.getDetail.mockResolvedValue(detail());
    api.listAssignees.mockResolvedValue([{ userId: 7, username: "analyst", displayName: "Analyst" }]);
    api.addComment.mockResolvedValue(detail({ version: 1 }));
  });

  it("renders report content and submits immutable comments for collaborators", async () => {
    const wrapper = mountView(["REPORT_READ", "REPORT_COLLABORATE"]);
    await flushPromises();

    expect(wrapper.find(".collaboration-report-content h1").text()).toBe("Report body");
    await wrapper.find(".collaboration-comment-form textarea").setValue("Reviewed with dealer");
    await wrapper.find(".collaboration-comment-form").trigger("submit");
    await flushPromises();

    expect(api.addComment).toHaveBeenCalledWith("report-1", "Reviewed with dealer", 0);
    expect(wrapper.find(".collaboration-comment-form textarea").element.value).toBe("");
  });

  it("keeps REPORT_READ-only users in a complete read-only view", async () => {
    const wrapper = mountView(["REPORT_READ"]);
    await flushPromises();

    expect(wrapper.find(".collaboration-read-only").exists()).toBe(true);
    expect(wrapper.find(".collaboration-controls").exists()).toBe(false);
    expect(wrapper.find(".collaboration-comment-form").exists()).toBe(false);
    expect(wrapper.text()).toContain("Created collaboration");
  });

  it("suppresses all mutation controls for terminal reports", async () => {
    api.listReports.mockResolvedValue([summary({ status: "CLOSED" })]);
    api.getDetail.mockResolvedValue(detail({ status: "CLOSED" }));
    const wrapper = mountView(["REPORT_READ", "REPORT_COLLABORATE"]);
    await flushPromises();

    expect(wrapper.find(".collaboration-notice").exists()).toBe(true);
    expect(wrapper.find(".collaboration-controls").exists()).toBe(false);
    expect(wrapper.find(".collaboration-comment-form").exists()).toBe(false);
  });
});

function mountView(permissions) {
  return mount(ReportCollaborationView, {
    props: {
      currentUser: { id: 2, permissions },
      dictionary: dictionary(),
      locale: "en"
    }
  });
}

function summary(overrides = {}) {
  return {
    reportId: "report-1",
    title: "Daily performance",
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
    markdown: "# Report body",
    timeline: [{
      id: 1,
      type: "CREATED",
      actorUsername: "system",
      actorDisplayName: "System",
      createdAt: "2026-08-15T01:00:00Z"
    }]
  };
}

function dictionary() {
  return {
    collaborationEyebrow: "Workflow",
    collaborationTitle: "Collaboration",
    collaborationSubtitle: "Reports",
    collaborationRefresh: "Refresh",
    collaborationFilterStatus: "Status",
    collaborationFilterAssignee: "Assignee",
    collaborationFilterOrganization: "Organization",
    collaborationFilterAll: "All",
    collaborationGeneratedFrom: "From",
    collaborationGeneratedTo: "To",
    collaborationApplyFilters: "Apply",
    collaborationClearFilters: "Clear",
    collaborationOrganization: "Organization",
    collaborationGlobalScope: "Global",
    collaborationLoadingTitle: "Loading",
    collaborationLoadingBody: "Loading reports",
    collaborationLoadError: "Load failed",
    collaborationEmptyTitle: "Empty",
    collaborationEmptyBody: "No reports",
    collaborationQueueTitle: "Queue",
    collaborationDetailLoading: "Loading detail",
    collaborationGeneratedAt: "Generated",
    collaborationReadOnly: "Read only",
    collaborationControlsTitle: "Controls",
    collaborationStatusLabel: "Status",
    collaborationAssigneeLabel: "Assignee",
    collaborationUnassigned: "Unassigned",
    collaborationTerminalNotice: "Terminal report",
    collaborationReloadConflict: "Reload",
    collaborationReportContent: "Report content",
    collaborationTimelineTitle: "History",
    collaborationCommentLabel: "Comment",
    collaborationCommentPlaceholder: "Add note",
    collaborationAddComment: "Add comment",
    collaborationCommentPending: "Submitting",
    collaborationNotAvailable: "N/A",
    collaborationSystemActor: "System",
    collaborationStatusEvent: "{from} to {to}",
    collaborationAssigneeEvent: "Assignee changed",
    collaborationStatusUpdated: "Status updated",
    collaborationAssigneeUpdated: "Assignee updated",
    collaborationCommentAdded: "Comment added",
    collaborationForbiddenError: "Forbidden",
    collaborationConflictError: "Conflict",
    collaborationStateConflictError: "State conflict",
    collaborationValidationError: "Invalid",
    collaborationRequestError: "Failed",
    collaborationStatuses: {
      OPEN: "Open", IN_PROGRESS: "In progress", RESOLVED: "Resolved", CLOSED: "Closed"
    },
    collaborationReportTypes: { daily: "Daily" },
    collaborationEvents: { CREATED: "Created collaboration" }
  };
}
