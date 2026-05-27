import { useSseParser } from "../composables/useSseParser";
import { ApiError, buildUrl, extractErrorMessage, requestJson } from "./client";
import { getAuthToken } from "./sessionToken";

export function clearSession(sessionId) {
  return requestJson(`/api/chat/${sessionId}`, {
    method: "DELETE",
    headers: authHeaders()
  });
}

export async function streamChat({ sessionId, message, baseUrl, apiKey, model, signal, onEvent }) {
  const response = await fetch(buildUrl("/api/chat/stream"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...authHeaders()
    },
    body: JSON.stringify({ sessionId, message, baseUrl, apiKey, model }),
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

function authHeaders() {
  const token = getAuthToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}
