import { useSseParser } from "../composables/useSseParser";
import { ApiError, extractErrorMessage, request, requestJson } from "./client";

export function clearSession(sessionId) {
  return requestJson(`/api/chat/${sessionId}`, {
    method: "DELETE"
  });
}

export async function streamChat({ sessionId, message, signal, onEvent }) {
  const response = await request("/api/chat/stream", {
    method: "POST",
    body: JSON.stringify({ sessionId, message }),
    signal
  });

  if (!response.ok || !response.body) {
    const body = await response.text();
    throw new ApiError(extractErrorMessage(body) || `Chat request failed with status ${response.status}`, {
      status: response.status,
      body
    });
  }

  const { consume } = useSseParser();
  await consume(response.body, onEvent);
}
