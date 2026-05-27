export function createSessionId() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }

  return `session-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
}

const FOLLOW_UP_HEADINGS = [
  /(?:^|\n)\s*\*{0,2}FOLLOW_UP_QUESTIONS\*{0,2}\s*[:：]?\s*/i,
  /(?:^|\n)\s*\*{0,2}FOLLOW[- ]?UP QUESTIONS\*{0,2}\s*[:：]?\s*/i,
  /(?:^|\n)\s*\*{0,2}追问\*{0,2}\s*[:：]?\s*/i,
  /(?:^|\n)\s*\*{0,2}跟进问题\*{0,2}\s*[:：]?\s*/i,
  /(?:^|\n)\s*\*{0,2}后续问题\*{0,2}\s*[:：]?\s*/i,
  /(?:^|\n)\s*\*{0,2}下一步问题\*{0,2}\s*[:：]?\s*/i
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

  // Fallback: if regex parsing produced nothing but section has content, try looser extraction
  if (followUps.length === 0 && followUpSection.trim()) {
    const looseLines = followUpSection
      .split(/\r?\n/)
      .map(line => line.trim())
      .filter(Boolean)
      .slice(0, 2);
    return { body, followUps: looseLines };
  }

  return {
    body,
    followUps
  };
}

function parseFollowUpLines(section) {
  return section
    .split(/\r?\n/)
    .map((line) => line
      .replace(/^\s*(?:\d+\.\s*|[-*·•]\s*)/, "")
      .replace(/\*{1,3}([^*]+)\*{1,3}/g, "$1")
      .replace(/_{1,2}([^_]+)_{1,2}/g, "$1")
      .replace(/~{2}([^~]+)~{2}/g, "$1")
      .replace(/^[*·•]+|[*·•]+$/g, "")
      .trim()
    )
    .filter(Boolean)
    .filter((item, index, array) => array.indexOf(item) === index)
    .slice(0, 2);
}
