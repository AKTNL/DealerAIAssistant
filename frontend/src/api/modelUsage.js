import { requestJson } from "./client";

export async function getModelUsageSummary(range = {}) {
  return readData(await requestJson(withRange("/api/admin/model-usage/summary", range)), null);
}

export async function listModelUsageEvents(range = {}) {
  return readData(await requestJson(withRange("/api/admin/model-usage/events", range)), []);
}

export async function listModelPrices() {
  return readData(await requestJson("/api/admin/model-usage/prices"), []);
}

export async function addModelPrice(input) {
  return readData(await requestJson("/api/admin/model-usage/prices", {
    method: "POST",
    body: JSON.stringify(input)
  }), null);
}

export async function getModelBudget() {
  return readData(await requestJson("/api/admin/model-usage/budget"), null);
}

export async function saveModelBudget(input) {
  return readData(await requestJson("/api/admin/model-usage/budget", {
    method: "PUT",
    body: JSON.stringify(input)
  }), null);
}

export async function getPlatformModelUsageSummary(range = {}) {
  return readData(await requestJson(withRange("/api/platform/model-usage/summary", range)), null);
}

function withRange(path, { from, to } = {}) {
  const query = new URLSearchParams();
  if (from) {
    query.set("from", from);
  }
  if (to) {
    query.set("to", to);
  }
  const suffix = query.toString();
  return suffix ? `${path}?${suffix}` : path;
}

function readData(response, fallback) {
  return response?.data ?? fallback;
}
