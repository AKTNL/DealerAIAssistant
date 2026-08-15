import { requestJson } from "./client";

export async function listReportCollaborations(filters = {}) {
  const query = new URLSearchParams();
  appendQuery(query, "status", filters.status);
  appendQuery(query, "assigneeUserId", filters.assigneeUserId);
  appendQuery(query, "organizationId", filters.organizationId);
  appendQuery(query, "generatedFrom", filters.generatedFrom);
  appendQuery(query, "generatedTo", filters.generatedTo);
  const suffix = query.size ? `?${query.toString()}` : "";
  return readData(await requestJson(`/api/report-collaborations${suffix}`), []);
}

export async function getReportCollaboration(reportId) {
  return readData(await requestJson(`/api/report-collaborations/${encodeURIComponent(reportId)}`), null);
}

export async function listReportCollaborationAssignees(reportId) {
  return readData(await requestJson(
    `/api/report-collaborations/${encodeURIComponent(reportId)}/assignees`
  ), []);
}

export async function changeReportCollaborationStatus(reportId, status, version) {
  return readData(await requestJson(
    `/api/report-collaborations/${encodeURIComponent(reportId)}/status`,
    {
      method: "PATCH",
      body: JSON.stringify({ status, version })
    }
  ), null);
}

export async function changeReportCollaborationAssignee(reportId, assigneeUserId, version) {
  return readData(await requestJson(
    `/api/report-collaborations/${encodeURIComponent(reportId)}/assignee`,
    {
      method: "PATCH",
      body: JSON.stringify({ assigneeUserId, version })
    }
  ), null);
}

export async function addReportCollaborationComment(reportId, body, version) {
  return readData(await requestJson(
    `/api/report-collaborations/${encodeURIComponent(reportId)}/comments`,
    {
      method: "POST",
      body: JSON.stringify({ body, version })
    }
  ), null);
}

function appendQuery(query, key, value) {
  if (value !== null && value !== undefined && String(value).trim()) {
    query.set(key, String(value).trim());
  }
}

function readData(response, fallback) {
  return response?.data ?? fallback;
}
