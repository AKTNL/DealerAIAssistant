import { beforeEach, describe, expect, it, vi } from "vitest";
import { clearSession, streamChat } from "../chat";

describe("chat API", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("attaches the auth bearer token to clear-session requests", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ success: true }), {
        headers: { "Content-Type": "application/json" },
        status: 200
      })
    );
    writeStoredSession();

    await clearSession("session-1");

    expect(fetchMock).toHaveBeenCalledWith("/api/chat/session-1", {
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer signed-token"
      },
      method: "DELETE"
    });
  });

  it("attaches the auth bearer token to chat stream requests", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(sseStream("event: done\ndata: [DONE]\n\n"), {
        headers: { "Content-Type": "text/event-stream" },
        status: 200
      })
    );
    const onEvent = vi.fn();
    writeStoredSession();

    await streamChat({
      apiKey: "sk-test",
      baseUrl: "https://api.example.com",
      message: "Hello",
      model: "gpt-test",
      onEvent,
      sessionId: "session-1"
    });

    expect(fetchMock).toHaveBeenCalledWith("/api/chat/stream", {
      body: expect.any(String),
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer signed-token"
      },
      method: "POST",
      signal: undefined
    });
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      apiKey: "sk-test",
      baseUrl: "https://api.example.com",
      message: "Hello",
      model: "gpt-test",
      sessionId: "session-1"
    });
    expect(onEvent).toHaveBeenCalledWith({ event: "done", data: "[DONE]" });
  });

  function writeStoredSession() {
    window.sessionStorage.setItem(
      "agentpoc.authVerified",
      JSON.stringify({
        sessionToken: "signed-token",
        expiresAt: "2999-01-01T00:00:00.000Z"
      })
    );
  }

  function sseStream(text) {
    return new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(text));
        controller.close();
      }
    });
  }
});
