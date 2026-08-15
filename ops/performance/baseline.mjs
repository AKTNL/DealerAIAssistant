import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { performance } from "node:perf_hooks";
import { randomUUID } from "node:crypto";

const baseUrl = readUrl("BASE_URL", "http://127.0.0.1:8081");
const accessToken = required("ACCESS_TOKEN");
const tenantKey = process.env.TENANT_KEY?.trim() || "default";
const durationSeconds = positiveInteger("DURATION_SECONDS", 30);
const concurrency = positiveInteger("CONCURRENCY", 4);
const requestTimeoutMs = positiveInteger("REQUEST_TIMEOUT_MS", 60_000);
const outputPath = process.env.OUTPUT_PATH?.trim();

if (process.env.ALLOW_REPORT_WRITES !== "true") {
  throw new Error("ALLOW_REPORT_WRITES=true is required because the report scenario creates durable drafts.");
}

const thresholds = {
  dashboard: positiveInteger("DASHBOARD_P95_MS", 750),
  dataDetails: positiveInteger("DATA_DETAILS_P95_MS", 1_000),
  agentQuery: positiveInteger("AGENT_QUERY_P95_MS", 5_000),
  reportGeneration: positiveInteger("REPORT_GENERATION_P95_MS", 1_500)
};
const maxErrorRate = numberBetweenZeroAndOne("MAX_ERROR_RATE", 0.01);
const runId = randomUUID();
const deadline = performance.now() + durationSeconds * 1_000;
const results = new Map(Object.keys(thresholds).map((name) => [name, emptyResult()]));

const operations = [
  {
    name: "dashboard",
    request: () => request("/api/dashboard", { method: "GET" })
  },
  {
    name: "dataDetails",
    request: () => request("/api/v1/data/opportunities", { method: "GET" })
  },
  {
    name: "agentQuery",
    request: (worker, sequence) => request("/api/chat", {
      method: "POST",
      body: JSON.stringify({
        sessionId: `perf-${runId}-${worker}-${sequence}`,
        message: "Summarize current target achievement using available data."
      })
    })
  },
  {
    name: "reportGeneration",
    request: () => request("/api/reports/drafts", {
      method: "POST",
      body: JSON.stringify({
        reportType: "daily",
        language: "en",
        scopeType: "GLOBAL",
        scopeId: null,
        topic: "performance-baseline"
      })
    })
  }
];

await Promise.all(operations.map((operation, index) => operation.request(-1, index)));
await Promise.all(Array.from({ length: concurrency }, (_, worker) => runWorker(worker)));

const summary = summarize();
const serialized = `${JSON.stringify(summary, null, 2)}\n`;
process.stdout.write(serialized);
if (outputPath) {
  const destination = resolve(outputPath);
  await mkdir(dirname(destination), { recursive: true });
  await writeFile(destination, serialized, { encoding: "utf8" });
}
if (!summary.passed) {
  process.exitCode = 2;
}

async function runWorker(worker) {
  let sequence = 0;
  while (performance.now() < deadline) {
    const operation = operations[(worker + sequence) % operations.length];
    const sample = await operation.request(worker, sequence);
    record(operation.name, sample);
    sequence += 1;
  }
}

async function request(path, options) {
  const startedAt = performance.now();
  try {
    const response = await fetch(new URL(path, baseUrl), {
      ...options,
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
        "X-Tenant-Key": tenantKey
      },
      signal: AbortSignal.timeout(requestTimeoutMs)
    });
    await response.arrayBuffer();
    return {
      durationMs: performance.now() - startedAt,
      status: String(response.status),
      ok: response.ok
    };
  } catch {
    return {
      durationMs: performance.now() - startedAt,
      status: "transport_error",
      ok: false
    };
  }
}

function record(name, sample) {
  const result = results.get(name);
  result.durations.push(sample.durationMs);
  result.statuses.set(sample.status, (result.statuses.get(sample.status) || 0) + 1);
  if (!sample.ok) {
    result.failures += 1;
  }
}

function summarize() {
  const operationsSummary = {};
  for (const [name, result] of results) {
    const sorted = result.durations.toSorted((left, right) => left - right);
    const count = sorted.length;
    const errorRate = count === 0 ? 1 : result.failures / count;
    const p95 = percentile(sorted, 0.95);
    operationsSummary[name] = {
      count,
      errorRate: round(errorRate),
      latencyMs: {
        p50: round(percentile(sorted, 0.50)),
        p95: round(p95),
        p99: round(percentile(sorted, 0.99)),
        max: round(count === 0 ? 0 : sorted[count - 1])
      },
      statuses: Object.fromEntries([...result.statuses.entries()].toSorted()),
      threshold: {
        p95Ms: thresholds[name],
        maxErrorRate
      },
      passed: count > 0 && p95 <= thresholds[name] && errorRate <= maxErrorRate
    };
  }
  const slowestPath = Object.entries(operationsSummary)
    .toSorted((left, right) => right[1].latencyMs.p95 - left[1].latencyMs.p95)[0]?.[0] || null;
  return {
    generatedAt: new Date().toISOString(),
    profile: {
      durationSeconds,
      concurrency,
      requestTimeoutMs
    },
    dataPolicy: "Response bodies, credentials, tenant identifiers, prompts, and report content are not persisted.",
    slowestPath,
    operations: operationsSummary,
    passed: Object.values(operationsSummary).every((operation) => operation.passed)
  };
}

function emptyResult() {
  return { durations: [], failures: 0, statuses: new Map() };
}

function percentile(sorted, ratio) {
  if (sorted.length === 0) {
    return 0;
  }
  return sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * ratio) - 1)];
}

function round(value) {
  return Math.round(value * 1_000) / 1_000;
}

function required(name) {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`${name} is required.`);
  }
  return value;
}

function positiveInteger(name, fallback) {
  const value = Number.parseInt(process.env[name] || String(fallback), 10);
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name} must be a positive integer.`);
  }
  return value;
}

function numberBetweenZeroAndOne(name, fallback) {
  const value = Number.parseFloat(process.env[name] || String(fallback));
  if (!Number.isFinite(value) || value < 0 || value > 1) {
    throw new Error(`${name} must be between 0 and 1.`);
  }
  return value;
}

function readUrl(name, fallback) {
  const value = new URL(process.env[name]?.trim() || fallback);
  if (value.protocol !== "http:" && value.protocol !== "https:") {
    throw new Error(`${name} must use HTTP or HTTPS.`);
  }
  return value;
}
