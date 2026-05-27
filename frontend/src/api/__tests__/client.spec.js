import { afterEach, describe, expect, it, vi } from "vitest";

async function importClient({ apiBaseUrl } = {}) {
  vi.resetModules();
  vi.unstubAllEnvs();

  if (apiBaseUrl !== undefined) {
    vi.stubEnv("VITE_API_BASE_URL", apiBaseUrl);
  }

  return import("../client");
}

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.resetModules();
});

describe("api client", () => {
  it("builds URLs from VITE_API_BASE_URL", async () => {
    const { buildUrl } = await importClient({ apiBaseUrl: "https://api.example.test" });

    expect(buildUrl("/api/chat")).toBe("https://api.example.test/api/chat");
  });

  it("returns parsed JSON for 2xx responses", async () => {
    const responseBody = { reply: "hello" };
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue(responseBody)
    });
    vi.stubGlobal("fetch", fetchMock);

    const { requestJson } = await importClient();

    await expect(requestJson("/api/chat", { method: "POST" })).resolves.toEqual(responseBody);
    expect(fetchMock).toHaveBeenCalledWith("/api/chat", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      }
    });
  });

  it("throws ApiError with status and body for non-2xx responses", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: false,
      status: 503,
      text: vi.fn().mockResolvedValue("service unavailable")
    }));

    const { ApiError, requestJson } = await importClient();

    let thrown;
    try {
      await requestJson("/api/chat");
    } catch (error) {
      thrown = error;
    }

    expect(thrown).toBeInstanceOf(ApiError);
    expect(thrown).toMatchObject({
      message: "service unavailable",
      status: 503,
      body: "service unavailable"
    });
  });

  it("uses message from JSON error bodies", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      text: vi.fn().mockResolvedValue('{"message":"Invalid API key"}')
    }));

    const { requestJson } = await importClient();

    await expect(requestJson("/api/chat")).rejects.toMatchObject({
      message: "Invalid API key",
      status: 400,
      body: '{"message":"Invalid API key"}'
    });
  });
});
