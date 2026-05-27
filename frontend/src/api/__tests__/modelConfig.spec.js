import { beforeEach, describe, expect, it, vi } from "vitest";

const requestJsonMock = vi.fn();

vi.mock("../client", () => ({
  requestJson: (...args) => requestJsonMock(...args)
}));

describe("modelConfig API", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    requestJsonMock.mockReset();
  });

  it("posts model settings to the dedicated connection test endpoint", async () => {
    requestJsonMock.mockResolvedValue({ ok: true });
    window.sessionStorage.setItem(
      "agentpoc.authVerified",
      JSON.stringify({
        sessionToken: "signed-token",
        expiresAt: "2999-01-01T00:00:00.000Z"
      })
    );

    const { testModelConnection } = await import("../modelConfig");

    await testModelConnection({
      apiKey: "sk-test",
      baseUrl: "https://api.example.com",
      model: "gpt-4.1-mini"
    });

    expect(requestJsonMock).toHaveBeenCalledWith("/api/model-config/test", {
      headers: {
        Authorization: "Bearer signed-token"
      },
      body: JSON.stringify({
        apiKey: "sk-test",
        baseUrl: "https://api.example.com",
        model: "gpt-4.1-mini"
      }),
      method: "POST"
    });
  });
});
