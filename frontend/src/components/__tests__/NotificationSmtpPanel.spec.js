import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  deleteConfig: vi.fn(),
  getConfig: vi.fn(),
  saveConfig: vi.fn(),
  testConfig: vi.fn()
}));

vi.mock("../../api/notificationSmtp", () => ({
  deleteSmtpConfig: (...args) => api.deleteConfig(...args),
  getSmtpConfig: (...args) => api.getConfig(...args),
  saveSmtpConfig: (...args) => api.saveConfig(...args),
  testSmtpConfig: (...args) => api.testConfig(...args)
}));

import NotificationSmtpPanel from "../admin/NotificationSmtpPanel.vue";

const dictionary = {
  adminRefresh: "Refresh",
  smtpBody: "Tenant SMTP settings",
  smtpDelete: "Delete",
  smtpDeleteConfirm: "Delete SMTP settings?",
  smtpDeleted: "Deleted",
  smtpEnabled: "Enabled",
  smtpFromAddress: "From address",
  smtpFromDisplayName: "From display name",
  smtpHost: "Host",
  smtpPassword: "Password",
  smtpPort: "Port",
  smtpRequestError: "Request failed",
  smtpSave: "Save",
  smtpSaved: "Saved",
  smtpSecurityMode: "TLS mode",
  smtpTest: "Test",
  smtpTestAccepted: "Accepted",
  smtpTestFailed: "Not accepted",
  smtpTitle: "SMTP",
  smtpUsername: "Username"
};

describe("NotificationSmtpPanel", () => {
  beforeEach(() => {
    Object.values(api).forEach((mockFunction) => mockFunction.mockReset());
    api.getConfig.mockResolvedValue(config());
    api.saveConfig.mockResolvedValue({ ...config(), version: 4 });
    api.testConfig.mockResolvedValue({ accepted: true, code: "SMTP_ACCEPTED" });
    api.deleteConfig.mockResolvedValue(undefined);
  });

  it("keeps the stored password redacted and preserves it on save", async () => {
    const wrapper = mount(NotificationSmtpPanel, { props: { dictionary } });
    await flushPromises();

    expect(wrapper.find('input[type="password"]').element.value).toBe("");
    expect(wrapper.find('input[type="password"]').attributes("required")).toBeUndefined();

    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(api.saveConfig).toHaveBeenCalledWith({
      host: "smtp.example.com",
      port: 587,
      securityMode: "STARTTLS",
      username: "smtp-user",
      password: null,
      fromAddress: "reports@example.com",
      fromDisplayName: "Dealer AI",
      enabled: true,
      version: 3
    });
    expect(wrapper.text()).toContain("Saved");
  });

  it("shows safe test results and delegates expired sessions", async () => {
    const wrapper = mount(NotificationSmtpPanel, { props: { dictionary } });
    await flushPromises();

    const testButton = wrapper.findAll("button").find((button) => button.text() === "Test");
    await testButton.trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("Accepted");

    api.getConfig.mockRejectedValueOnce({ status: 401 });
    const refreshButton = wrapper.findAll("button").find((button) => button.text().includes("Refresh"));
    await refreshButton.trigger("click");
    await flushPromises();
    expect(wrapper.emitted("sign-out")).toHaveLength(1);
  });
});

function config() {
  return {
    host: "smtp.example.com",
    port: 587,
    securityMode: "STARTTLS",
    username: "smtp-user",
    fromAddress: "reports@example.com",
    fromDisplayName: "Dealer AI",
    enabled: true,
    passwordConfigured: true,
    version: 3
  };
}
