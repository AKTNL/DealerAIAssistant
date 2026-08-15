import { mount } from "@vue/test-utils";
import { ref } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AdminView from "../admin/AdminView.vue";

const state = vi.hoisted(() => ({
  administration: null
}));

vi.mock("../../composables/useAdministration", () => ({
  useAdministration: () => state.administration
}));

vi.mock("../admin/NotificationSmtpPanel.vue", () => ({
  default: {
    name: "NotificationSmtpPanel",
    props: ["dictionary"],
    template: '<div class="smtp-panel-stub">SMTP panel</div>'
  }
}));

vi.mock("../admin/ModelUsagePanel.vue", () => ({
  default: {
    name: "ModelUsagePanel",
    props: ["currentUser", "dictionary", "locale"],
    template: '<div class="model-usage-panel-stub">Model usage panel</div>'
  }
}));

const dictionary = {
  adminAssignedRoles: "Assigned roles",
  adminAuditTab: "Audit",
  adminCreateUser: "Create user",
  adminDisplayNameLabel: "Display name",
  adminEmailLabel: "Email",
  adminDisabled: "Disabled",
  adminEnabled: "Enabled",
  adminErrorTitle: "Error",
  adminEyebrow: "Security",
  adminForbiddenTitle: "Forbidden",
  adminLoadingBody: "Loading",
  adminLoadingTitle: "Loading administration",
  modelUsageTab: "Model usage",
  adminRefresh: "Refresh",
  adminRetry: "Retry",
  adminRolesLabel: "Roles",
  adminRolesTab: "Roles",
  adminSectionNavigation: "Sections",
  adminSessionsBody: "Safe metadata",
  adminSessionsEmpty: "No sessions",
  adminSessionsTitle: "Sessions",
  adminSubtitle: "Manage access",
  adminTemporaryPasswordBody: "Shown once",
  adminTemporaryPasswordDismiss: "Close",
  adminTemporaryPasswordRequired: "Change required",
  adminTemporaryPasswordTitle: "One-time password",
  adminTemporaryPasswordWarning: "Not persisted",
  adminTitle: "Administration",
  adminUsernameLabel: "Username",
  adminUsersBody: "Manage users",
  adminUsersEmpty: "No users",
  adminUsersTab: "Users",
  adminUsersTitle: "Users",
  smtpTab: "Email notifications"
};

function administration(overrides = {}) {
  return {
    auditEvents: ref([]),
    canManageGrants: ref(false),
    canManageOrganization: ref(false),
    canManageRoles: ref(false),
    canManageUsers: ref(false),
    canReadOrganization: ref(false),
    canReadModelUsage: ref(false),
    canReadRoles: ref(false),
    canReadUsers: ref(true),
    changeUserEmail: vi.fn(),
    changeUserEnabled: vi.fn(),
    clearFeedback: vi.fn(),
    createOrganizationNode: vi.fn(),
    createRole: vi.fn(),
    createUser: vi.fn(),
    dealerMappings: ref([]),
    dismissOneTimePassword: vi.fn(),
    initialize: vi.fn().mockResolvedValue(undefined),
    loadError: ref(null),
    loadGrants: vi.fn(),
    loadSessions: vi.fn().mockResolvedValue([]),
    loading: ref(false),
    mapDealer: vi.fn(),
    oneTimePassword: ref(null),
    operationError: ref(null),
    organizationNodes: ref([]),
    pendingAction: ref(""),
    refreshAuditEvents: vi.fn(),
    replaceGrants: vi.fn(),
    resetUserPassword: vi.fn(),
    revokeSessions: vi.fn(),
    roles: ref([]),
    subjectGrants: ref([]),
    successMessage: ref(""),
    updateOrganizationNode: vi.fn(),
    updateRolePermissions: vi.fn(),
    userSessions: ref([]),
    users: ref([]),
    assignUserRoles: vi.fn(),
    ...overrides
  };
}

function mountView() {
  return mount(AdminView, {
    props: {
      currentUser: { id: 1, permissions: ["USER_READ"] },
      dictionary,
      locale: "en"
    }
  });
}

describe("AdminView", () => {
  beforeEach(() => {
    state.administration = administration();
  });

  it("renders a readable empty state for an authorized empty resource", async () => {
    const wrapper = mountView();
    await vi.waitFor(() => expect(wrapper.find(".admin-panel").exists()).toBe(true));

    expect(wrapper.text()).toContain("No users");
    expect(wrapper.find("form.admin-inline-form").exists()).toBe(false);
  });

  it("renders conflict feedback separately from validation errors", async () => {
    state.administration = administration({
      operationError: ref({ status: 409, message: "Refresh after conflict", detail: "" })
    });
    const wrapper = mountView();
    await vi.waitFor(() => expect(wrapper.find(".admin-feedback-error").exists()).toBe(true));

    expect(wrapper.find(".admin-feedback-error").text()).toContain("Refresh after conflict");
  });

  it("shows and dismisses the one-time password dialog", async () => {
    const dismissOneTimePassword = vi.fn();
    state.administration = administration({
      dismissOneTimePassword,
      oneTimePassword: ref({ label: "Viewer", password: "Temporary-2!" })
    });
    const wrapper = mountView();
    await vi.waitFor(() => expect(wrapper.find(".admin-secret-dialog").exists()).toBe(true));

    expect(wrapper.find(".admin-secret-value").text()).toBe("Temporary-2!");
    await wrapper.find(".admin-secret-dialog button").trigger("click");
    expect(dismissOneTimePassword).toHaveBeenCalledOnce();
  });

  it("opens tenant SMTP settings only for user managers", async () => {
    state.administration = administration({ canManageUsers: ref(true) });
    const wrapper = mountView();
    await vi.waitFor(() => expect(wrapper.text()).toContain("Email notifications"));

    const smtpTab = wrapper.findAll("button").find((button) => button.text().includes("Email notifications"));
    await smtpTab.trigger("click");

    expect(wrapper.find(".smtp-panel-stub").exists()).toBe(true);
  });

  it("opens model usage for a usage reader", async () => {
    state.administration = administration({ canReadModelUsage: ref(true) });
    const wrapper = mountView();
    await vi.waitFor(() => expect(wrapper.text()).toContain("Model usage"));

    const usageTab = wrapper.findAll("button").find((button) => button.text().includes("Model usage"));
    await usageTab.trigger("click");

    expect(wrapper.find(".model-usage-panel-stub").exists()).toBe(true);
  });
});
