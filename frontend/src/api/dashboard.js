import { requestJson } from "./client";
import { getAuthToken } from "./sessionToken";

export async function getDashboardSummary() {
  const token = getAuthToken();
  const response = await requestJson("/api/dashboard", {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  });

  return normalizeDashboardSummary(response?.data);
}

function normalizeDashboardSummary(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    return null;
  }

  return {
    dataStatus: normalizeDataStatus(data.dataStatus),
    overview: normalizeRecord(data.overview),
    targetAchievement: normalizeRecord(data.targetAchievement),
    opportunityFunnel: normalizeRecord(data.opportunityFunnel),
    leadSources: normalizeRecord(data.leadSources),
    followUpTasks: normalizeRecord(data.followUpTasks),
    campaignEffect: normalizeRecord(data.campaignEffect)
  };
}

function normalizeRecord(value) {
  return value && typeof value === "object" && !Array.isArray(value) ? value : {};
}

function normalizeDataStatus(value) {
  const source = normalizeRecord(value);
  return {
    ...source,
    fallbackActive: source.fallbackActive === true,
    simulatedData: source.simulatedData === true,
    lowConfidence: source.lowConfidence === true,
    issueSummaries: Array.isArray(source.issueSummaries) ? source.issueSummaries : []
  };
}
