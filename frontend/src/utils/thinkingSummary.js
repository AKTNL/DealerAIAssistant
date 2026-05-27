const SUMMARY_LIMIT = 4;
const VALUE_LIMIT = 180;

const LABELS = {
  zh: {
    calculation: "计算依据",
    conclusion: "结论",
    dataSource: "数据来源",
    matched: "条命中",
    records: "条记录",
    reasoning: "推理",
    sampleRows: "条样本",
    scope: "筛选范围",
    sheet: "Sheet",
    table: "表"
  },
  en: {
    calculation: "Calculation",
    conclusion: "Conclusion",
    dataSource: "Data source",
    matched: "matched",
    records: "records",
    reasoning: "Reasoning",
    sampleRows: "sample rows",
    scope: "Scope",
    sheet: "Sheet",
    table: "Table"
  }
};

const SCOPE_META_KEYS = [
  "city",
  "dealer",
  "dealerName",
  "dealerCode",
  "dealerGroupName",
  "productModel",
  "model",
  "month",
  "year",
  "timeRange",
  "scope"
];

const CALCULATION_META_KEYS = [
  "totalWonCount",
  "totalTargetValue",
  "wonCount",
  "totalOpportunityCount",
  "openCount",
  "overdueCount",
  "dealerCount",
  "campaignCount",
  "totalActualOpportunityCount",
  "totalNewCustomerTarget",
  "sourceCount",
  "leadCount",
  "convertedCount",
  "averageRate",
  "lowestRate",
  "highestRate"
];

export function buildThinkingSummary(steps = [], locale = "zh") {
  const safeSteps = Array.isArray(steps) ? steps : [];
  const labels = getLabels(locale);
  const cards = [
    buildDataSourceCard(findStep(safeSteps, "data_load"), labels, locale),
    buildScopeCard(findStep(safeSteps, "filter"), labels, locale),
    buildCalculationCard(findStep(safeSteps, "calculation"), labels, locale),
    buildInsightCard(findStep(safeSteps, "insight"), findStep(safeSteps, "model_thought"), labels)
  ].filter(Boolean);

  return cards.slice(0, SUMMARY_LIMIT);
}

function buildDataSourceCard(step, labels, locale) {
  if (!step) {
    return null;
  }

  const meta = normalizedMeta(step.meta);
  const value = formatRecordCount(meta.recordCount ?? meta.inputCount ?? meta.totalCount, labels, locale)
    || shorten(cleanText(step.detail) || cleanText(step.label));
  const sourceDetail = formatSourceMeta(meta, labels);
  const detail = sourceDetail || (value === cleanText(step.detail) ? "" : cleanText(step.detail));

  return createCard("data-source", labels.dataSource, value, detail);
}

function buildScopeCard(step, labels, locale) {
  if (!step) {
    return null;
  }

  const meta = normalizedMeta(step.meta);
  const value = formatScopeMeta(meta) || shorten(cleanText(step.detail) || cleanText(step.label));
  const matchDetail = formatMatchCount(meta, labels, locale);
  const detail = matchDetail || (value === cleanText(step.detail) ? "" : cleanText(step.detail));

  return createCard("scope", labels.scope, value, detail);
}

function buildCalculationCard(step, labels, locale) {
  if (!step) {
    return null;
  }

  const meta = normalizedMeta(step.meta);
  const value = shorten(cleanText(meta.formula) || cleanText(step.detail) || cleanText(step.label));
  const detail = joinParts([
    formatSampleRows(meta, labels, locale),
    formatCalculationPairs(meta, locale)
  ]);

  return createCard("calculation", labels.calculation, value, detail);
}

function buildInsightCard(insightStep, modelThoughtStep, labels) {
  const step = insightStep ?? modelThoughtStep;
  if (!step) {
    return null;
  }

  const isReasoning = step === modelThoughtStep && !insightStep;
  const value = shorten(cleanText(step.detail) || cleanText(step.label));
  const detail = value === cleanText(step.label) ? "" : cleanText(step.label);

  return createCard(isReasoning ? "reasoning" : "conclusion", isReasoning ? labels.reasoning : labels.conclusion, value, detail);
}

function createCard(key, label, value, detail = "") {
  const cleanValue = cleanText(value);
  const cleanDetail = cleanText(detail);

  if (!cleanValue && !cleanDetail) {
    return null;
  }

  return {
    detail: cleanDetail,
    key,
    label,
    value: cleanValue || cleanDetail
  };
}

function findStep(steps, type) {
  return steps.find((step) => step?.type === type && (cleanText(step.detail) || cleanText(step.label) || hasMeaningfulMeta(step.meta)));
}

function hasMeaningfulMeta(meta) {
  return meta && typeof meta === "object" && Object.keys(meta).length > 0;
}

function formatSourceMeta(meta, labels) {
  return joinParts([
    cleanText(meta.source_type),
    cleanText(meta.file_name),
    cleanText(meta.table) ? `${labels.table} ${cleanText(meta.table)}` : "",
    cleanText(meta.sheet) ? `${labels.sheet} ${cleanText(meta.sheet)}` : ""
  ]);
}

function formatScopeMeta(meta) {
  return SCOPE_META_KEYS
    .map((key) => cleanText(meta[key]))
    .filter(Boolean)
    .join(" · ");
}

function formatMatchCount(meta, labels, locale) {
  const input = parseFiniteNumber(meta.inputCount);
  const output = parseFiniteNumber(meta.outputCount ?? meta.matchCount ?? meta.matchedCount);

  if (input != null && output != null) {
    return `${formatNumber(input, locale)} -> ${formatNumber(output, locale)} ${labels.matched}`;
  }
  if (output != null) {
    return `${formatNumber(output, locale)} ${labels.matched}`;
  }

  return "";
}

function formatRecordCount(count, labels, locale) {
  const parsed = parseFiniteNumber(count);
  return parsed == null ? "" : `${formatNumber(parsed, locale)} ${labels.records}`;
}

function formatSampleRows(meta, labels, locale) {
  if (!Array.isArray(meta.sampleRows) || meta.sampleRows.length === 0) {
    return "";
  }

  return `${formatNumber(meta.sampleRows.length, locale)} ${labels.sampleRows}`;
}

function formatCalculationPairs(meta, locale) {
  const pairs = CALCULATION_META_KEYS
    .filter((key) => meta[key] !== undefined && meta[key] !== null && meta[key] !== "")
    .map((key) => `${key}=${formatPrimitive(meta[key], locale)}`);

  return pairs.slice(0, 3).join(" · ");
}

function formatPrimitive(value, locale) {
  const parsed = parseFiniteNumber(value);
  if (parsed != null) {
    return formatNumber(parsed, locale);
  }

  return cleanText(value);
}

function formatNumber(value, locale) {
  return new Intl.NumberFormat(locale === "zh" ? "zh-CN" : "en-US").format(value);
}

function parseFiniteNumber(value) {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string" && value.trim() !== "") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  return null;
}

function normalizedMeta(meta) {
  return meta && typeof meta === "object" ? meta : {};
}

function getLabels(locale) {
  return LABELS[locale === "zh" ? "zh" : "en"];
}

function cleanText(value) {
  return String(value ?? "").replace(/\s+/g, " ").trim();
}

function shorten(value) {
  const text = cleanText(value);
  return text.length > VALUE_LIMIT ? `${text.slice(0, VALUE_LIMIT - 3)}...` : text;
}

function joinParts(parts) {
  return parts.map(cleanText).filter(Boolean).join(" · ");
}
