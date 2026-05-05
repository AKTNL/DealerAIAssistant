export function createSessionId() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }

  return `session-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
}

const FOLLOW_UP_HEADINGS = [
  /(?:^|\n)\s*FOLLOW_UP_QUESTIONS\s*[:：]?\s*/i,
  /(?:^|\n)\s*FOLLOW[- ]?UP QUESTIONS\s*[:：]?\s*/i,
  /(?:^|\n)\s*跟进问题\s*[:：]?\s*/i,
  /(?:^|\n)\s*后续问题\s*[:：]?\s*/i,
  /(?:^|\n)\s*下一步问题\s*[:：]?\s*/i
];

export function normalizeAssistantPayload(rawText) {
  const text = String(rawText ?? "").trim();
  const { body, followUps } = extractFollowUps(text);

  return {
    content: body,
    followUps
  };
}

function extractFollowUps(text) {
  let headingIndex = -1;
  let headingLength = 0;

  for (const pattern of FOLLOW_UP_HEADINGS) {
    const match = text.match(pattern);
    if (match) {
      const index = match.index ?? -1;
      if (index >= 0 && (headingIndex < 0 || index < headingIndex)) {
        headingIndex = index;
        headingLength = match[0].length;
      }
    }
  }

  if (headingIndex < 0) {
    return {
      body: text,
      followUps: []
    };
  }

  const body = text.slice(0, headingIndex).trim();
  const followUpSection = text.slice(headingIndex + headingLength).trim();
  const followUps = parseFollowUpLines(followUpSection);

  return {
    body,
    followUps
  };
}

function parseFollowUpLines(section) {
  return section
    .split(/\r?\n/)
    .map((line) => line.replace(/^\s*(?:[-*]|\d+\.)\s*/, "").trim())
    .filter(Boolean)
    .slice(0, 2);
}
