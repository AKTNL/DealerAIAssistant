import { useSseParser } from "../composables/useSseParser";
import { buildUrl, requestJson } from "./client";

export function clearSession(sessionId) {
  return requestJson(`/api/chat/${sessionId}`, {
    method: "DELETE"
  });
}

export async function streamChat({ sessionId, message, signal, onEvent }) {
  const response = await fetch(buildUrl("/api/chat/stream"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ sessionId, message }),
    signal
  });

  if (!response.ok || !response.body) {
    const messageText = await response.text();
    throw new Error(messageText || `Chat request failed with status ${response.status}`);
  }

  const { consume } = useSseParser();
  await consume(response.body, onEvent);
}
