import { beforeEach, describe, expect, it, vi } from "vitest";

const requestJsonMock = vi.hoisted(() => vi.fn());

vi.mock("../client", () => ({
  requestJson: (...args) => requestJsonMock(...args)
}));

import {
  deleteSmtpConfig,
  getSmtpConfig,
  saveSmtpConfig,
  testSmtpConfig
} from "../notificationSmtp";

describe("notification SMTP API", () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    requestJsonMock.mockResolvedValue({ data: null });
  });

  it("uses write-only password and optimistic delete requests", async () => {
    const input = {
      host: "smtp.example.com",
      port: 587,
      securityMode: "STARTTLS",
      username: "smtp-user",
      password: "secret",
      fromAddress: "reports@example.com",
      enabled: true,
      version: 3
    };

    await getSmtpConfig();
    await saveSmtpConfig(input);
    await testSmtpConfig();
    await deleteSmtpConfig(3);

    expect(requestJsonMock).toHaveBeenNthCalledWith(1, "/api/notification/smtp");
    expect(requestJsonMock).toHaveBeenNthCalledWith(2, "/api/notification/smtp", {
      method: "PUT",
      body: JSON.stringify(input)
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(3, "/api/notification/smtp/test", {
      method: "POST"
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(4, "/api/notification/smtp", {
      method: "DELETE",
      body: JSON.stringify({ version: 3 })
    });
  });
});
