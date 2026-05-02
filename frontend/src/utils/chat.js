export function createSessionId() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }

  return `session-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
}

export function normalizeAssistantPayload(rawText) {
  const text = rawText.trim();
  const followUpMatch = text.match(/FOLLOW_UP_QUESTIONS:\s*([\s\S]*)$/);
  const followUps = followUpMatch
    ? followUpMatch[1]
        .split(/\r?\n/)
        .map((line) => line.replace(/^\s*(?:[-*]|\d+\.)\s*/, "").trim())
        .filter(Boolean)
    : [];

  const content = followUpMatch
    ? text.slice(0, followUpMatch.index).trim()
    : text;

  return {
    content,
    followUps
  };
}
