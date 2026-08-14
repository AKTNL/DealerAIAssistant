import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  changeEnabled: vi.fn(),
  create: vi.fn(),
  remove: vi.fn(),
  listRecipients: vi.fn(),
  listSubscriptions: vi.fn(),
  update: vi.fn()
}));

vi.mock("../../api/reportSubscriptions", () => ({
  changeReportSubscriptionEnabled: (...args) => api.changeEnabled(...args),
  createReportSubscription: (...args) => api.create(...args),
  deleteReportSubscription: (...args) => api.remove(...args),
  listReportSubscriptionRecipients: (...args) => api.listRecipients(...args),
  listReportSubscriptions: (...args) => api.listSubscriptions(...args),
  updateReportSubscription: (...args) => api.update(...args)
}));

import ReportSubscriptionsView from "../reporting/ReportSubscriptionsView.vue";

describe("ReportSubscriptionsView", () => {
  beforeEach(() => {
    Object.values(api).forEach((mockFunction) => mockFunction.mockReset());
    api.listRecipients.mockResolvedValue([{ userId: 2, username: "analyst", displayName: "Analyst" }]);
    api.listSubscriptions.mockResolvedValue([subscription()]);
    api.create.mockResolvedValue({ ...subscription(), id: 10 });
  });

  it("renders current schedules and submits the controlled preset form", async () => {
    const wrapper = mountView(["REPORT_READ", "REPORT_GENERATE"]);
    await flushPromises();

    expect(wrapper.text()).toContain("Daily report");
    expect(wrapper.text()).toContain("Eligible");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(api.create).toHaveBeenCalledWith(expect.objectContaining({
      reportType: "daily",
      scheduleKind: "DAILY",
      recipientUserIds: [2]
    }));
  });

  it("keeps report-read users in a read-only view", async () => {
    const wrapper = mountView(["REPORT_READ"]);
    await flushPromises();

    expect(wrapper.find("form").exists()).toBe(false);
    expect(wrapper.find(".subscription-card").exists()).toBe(true);
    expect(wrapper.find(".subscription-card-actions").exists()).toBe(false);
  });

  it("drops revoked recipients from edit state and keeps enablement as a separate action", async () => {
    api.listSubscriptions.mockResolvedValue([
      { ...subscription(), enabled: false, recipientUserIds: [99] }
    ]);
    const wrapper = mountView(["REPORT_READ", "REPORT_GENERATE"]);
    await flushPromises();

    await wrapper.find(".subscription-card-actions .text-button").trigger("click");

    expect(wrapper.find(".subscription-enabled-field").exists()).toBe(false);
    expect(wrapper.find(".subscription-recipients input").element.checked).toBe(false);
    expect(wrapper.find("form .primary-button").attributes("disabled")).toBeDefined();
  });
});

function mountView(permissions) {
  return mount(ReportSubscriptionsView, {
    props: {
      currentUser: { id: 2, permissions },
      dictionary: dictionary(),
      locale: "en"
    }
  });
}

function subscription() {
  return {
    id: 9,
    reportType: "daily",
    language: "en",
    topic: "",
    scope: { type: "ORGANIZATION", id: "10" },
    scheduleKind: "DAILY",
    localTime: "09:00",
    timeZone: "Asia/Shanghai",
    dayOfWeek: null,
    dayOfMonth: null,
    channelKey: "email",
    recipientUserIds: [2],
    enabled: true,
    nextRunAt: "2026-08-14T01:00:00Z",
    misfirePolicy: "SKIP",
    misfireGraceMinutes: 60,
    executionEligible: true,
    eligibilityReason: "eligible",
    version: 0
  };
}

function dictionary() {
  return {
    subscriptionEyebrow: "Automated reporting",
    subscriptionTitle: "Report subscriptions",
    subscriptionSubtitle: "Schedules",
    subscriptionRefresh: "Refresh",
    subscriptionLoadingTitle: "Loading",
    subscriptionLoadingBody: "Loading schedules",
    subscriptionLoadError: "Load error",
    subscriptionCreateTitle: "Create",
    subscriptionEditTitle: "Edit",
    subscriptionScopeHint: "Current scope",
    subscriptionCancelEdit: "Cancel",
    subscriptionReportType: "Report type",
    subscriptionReportDaily: "Daily report",
    subscriptionReportWeekly: "Weekly report",
    subscriptionReportMonthly: "Monthly report",
    subscriptionReportTopic: "Topic report",
    subscriptionLanguage: "Language",
    subscriptionLanguageZh: "Chinese",
    subscriptionLanguageEn: "English",
    subscriptionScheduleKind: "Schedule",
    subscriptionScheduleDaily: "Daily",
    subscriptionScheduleWeekly: "Weekly",
    subscriptionScheduleMonthly: "Monthly",
    subscriptionLocalTime: "Local time",
    subscriptionTimeZone: "Time zone",
    subscriptionWeekday: "Weekday",
    subscriptionWeekdays: ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"],
    subscriptionMonthDay: "Month day",
    subscriptionMonthDayPrefix: "Day ",
    subscriptionChannelKey: "Channel",
    subscriptionTopic: "Topic",
    subscriptionRecipients: "Recipients",
    subscriptionRecipientsEmpty: "No recipients",
    subscriptionEnableImmediately: "Enable",
    subscriptionCreate: "Create",
    subscriptionSave: "Save",
    subscriptionListTitle: "My subscriptions",
    subscriptionListEmpty: "Empty",
    subscriptionEnabledStatus: "Enabled",
    subscriptionDisabledStatus: "Disabled",
    subscriptionEligible: "Eligible",
    subscriptionIneligible: "Ineligible",
    subscriptionNextRun: "Next run",
    subscriptionNotScheduled: "Not scheduled",
    subscriptionMisfire: "Misfire",
    subscriptionMinutesSuffix: "minutes",
    subscriptionEdit: "Edit",
    subscriptionEnable: "Enable",
    subscriptionDisable: "Disable",
    subscriptionDelete: "Delete",
    subscriptionConfirmDelete: "Delete?",
    subscriptionCreated: "Created",
    subscriptionUpdated: "Updated",
    subscriptionEnabled: "Enabled",
    subscriptionDisabled: "Disabled",
    subscriptionDeleted: "Deleted",
    subscriptionForbiddenError: "Forbidden",
    subscriptionConflictError: "Conflict",
    subscriptionValidationError: "Invalid",
    subscriptionRequestError: "Failed",
    subscriptionEligibilityReasons: {}
  };
}
