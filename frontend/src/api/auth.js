import { requestJson } from "./client";
import { clearAuthSession, writeAuthSession } from "./sessionToken";
import { clearSelectedTenantKey, setSelectedTenantKey } from "./tenantContext";

let refreshPromise = null;

export async function loginUser(username, password) {
  clearSelectedTenantKey();
  const response = await requestJson("/api/auth/login", {
    method: "POST",
    skipAuthRefresh: true,
    body: JSON.stringify({ username, password })
  });
  return persistResponse(response);
}

export function refreshSession() {
  if (!refreshPromise) {
    refreshPromise = requestJson("/api/auth/refresh", {
      method: "POST",
      skipAuthRefresh: true
    })
      .then(persistResponse)
      .catch((error) => {
        clearAuthSession();
        throw error;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

export async function getCurrentUser() {
  const response = await requestJson("/api/auth/me");
  return response?.data ?? null;
}

export function changePassword(currentPassword, newPassword) {
  return requestJson("/api/auth/password", {
    method: "POST",
    body: JSON.stringify({ currentPassword, newPassword })
  });
}

export async function logoutUser({ all = false } = {}) {
  try {
    await requestJson(all ? "/api/auth/logout-all" : "/api/auth/logout", {
      method: "POST",
      skipAuthRefresh: true
    });
  } finally {
    clearAuthSession();
  }
}

function persistResponse(response) {
  const session = response?.data;
  if (!session || !writeAuthSession(session)) {
    throw new Error("Invalid authentication response.");
  }
  if (session.user?.currentTenant?.key) {
    setSelectedTenantKey(session.user.currentTenant.key);
  }
  return session;
}
