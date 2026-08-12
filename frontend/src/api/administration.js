import { requestJson } from "./client";

export async function listUsers() {
  return readData(await requestJson("/api/admin/users"), []);
}

export async function createUser({ username, displayName, temporaryPassword, roles }) {
  return readData(await requestJson("/api/admin/users", {
    method: "POST",
    body: JSON.stringify({ username, displayName, temporaryPassword, roles })
  }), null);
}

export async function changeUserEnabled(userId, enabled, version) {
  return readData(await requestJson(`/api/admin/users/${userId}/enabled`, {
    method: "PATCH",
    body: JSON.stringify({ enabled, version })
  }), null);
}

export async function assignUserRoles(userId, roles, version) {
  return readData(await requestJson(`/api/admin/users/${userId}/roles`, {
    method: "PUT",
    body: JSON.stringify({ roles, version })
  }), null);
}

export async function resetUserPassword(userId, temporaryPassword, version) {
  return readData(await requestJson(`/api/admin/users/${userId}/reset-password`, {
    method: "POST",
    body: JSON.stringify({ temporaryPassword, version })
  }), null);
}

export async function listUserSessions(userId) {
  return readData(await requestJson(`/api/admin/users/${userId}/sessions`), []);
}

export async function revokeUserSessions(userId) {
  return readData(await requestJson(`/api/admin/users/${userId}/sessions/revoke`, {
    method: "POST"
  }), []);
}

export async function listRoles() {
  return readData(await requestJson("/api/admin/roles"), []);
}

export async function createRole({ roleKey, displayName, permissions }) {
  return readData(await requestJson("/api/admin/roles", {
    method: "POST",
    body: JSON.stringify({ roleKey, displayName, permissions })
  }), null);
}

export async function updateRolePermissions(roleId, permissions, version) {
  return readData(await requestJson(`/api/admin/roles/${roleId}/permissions`, {
    method: "PUT",
    body: JSON.stringify({ permissions, version })
  }), null);
}

export async function listOrganizationNodes() {
  return readData(await requestJson("/api/admin/organizations/nodes"), []);
}

export async function createOrganizationNode(node) {
  return readData(await requestJson("/api/admin/organizations/nodes", {
    method: "POST",
    body: JSON.stringify(node)
  }), null);
}

export async function updateOrganizationNode(nodeId, node) {
  return readData(await requestJson(`/api/admin/organizations/nodes/${nodeId}`, {
    method: "PUT",
    body: JSON.stringify(node)
  }), null);
}

export async function listDealerMappings() {
  return readData(await requestJson("/api/admin/organizations/dealer-mappings"), []);
}

export async function mapDealer(dealerCode, organizationNodeId) {
  return readData(await requestJson(
    `/api/admin/organizations/dealer-mappings/${encodeURIComponent(dealerCode)}`,
    {
      method: "PUT",
      body: JSON.stringify({ organizationNodeId })
    }
  ), null);
}

export async function listSubjectGrants(subjectType, subjectId) {
  return readData(await requestJson(grantPath(subjectType, subjectId)), []);
}

export async function replaceSubjectGrants(subjectType, subjectId, grants) {
  return readData(await requestJson(grantPath(subjectType, subjectId), {
    method: "PUT",
    body: JSON.stringify({ grants })
  }), []);
}

export async function listAuditEvents() {
  return readData(await requestJson("/api/admin/audit-events"), []);
}

function grantPath(subjectType, subjectId) {
  const segment = subjectType === "role" ? "role-grants" : "user-grants";
  return `/api/admin/organizations/${segment}/${subjectId}`;
}

function readData(response, fallback) {
  return response?.data ?? fallback;
}
