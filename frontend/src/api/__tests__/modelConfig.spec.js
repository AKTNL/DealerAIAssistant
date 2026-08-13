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
        accessToken: "signed-token",
        accessExpiresAt: "2999-01-01T00:00:00.000Z",
        user: { id: 1 }
      })
    );

    const { testModelConnection } = await import("../modelConfig");

    await testModelConnection({
      apiKey: "sk-test",
      baseUrl: "https://api.example.com",
      model: "gpt-4.1-mini"
    });

    expect(requestJsonMock).toHaveBeenCalledWith("/api/model-config/test", {
      body: JSON.stringify({
        apiKey: "sk-test",
        baseUrl: "https://api.example.com",
        model: "gpt-4.1-mini"
      }),
      method: "POST"
    });
  });

  it("uses server CRUD endpoints without exposing a stored API key", async () => {
    requestJsonMock.mockResolvedValueOnce({
      apiKeyConfigured: true,
      allowedHosts: ["api.example.com"],
      baseUrl: "https://api.example.com/v1",
      model: "gpt-test"
    });
    requestJsonMock.mockResolvedValueOnce({ apiKeyConfigured: true });
    requestJsonMock.mockResolvedValueOnce(undefined);
    const { deleteModelConfig, getModelConfig, saveModelConfig } = await import("../modelConfig");

    const view = await getModelConfig();
    expect(view).not.toHaveProperty("apiKey");
    await saveModelConfig({
      apiKey: "",
      allowedHosts: ["api.example.com"],
      baseUrl: "https://api.example.com/v1",
      model: "gpt-test"
    });
    await deleteModelConfig();

    expect(requestJsonMock).toHaveBeenNthCalledWith(1, "/api/model-config");
    expect(requestJsonMock).toHaveBeenNthCalledWith(3, "/api/model-config", { method: "DELETE" });
  });
});
