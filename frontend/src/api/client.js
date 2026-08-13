import { clearAuthSession, getAuthToken } from "./sessionToken";
import { getSelectedTenantKey } from "./tenantContext";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export class ApiError extends Error {
  constructor(message, { status = 0, body = "" } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

export function buildUrl(path) {
  return `${API_BASE_URL}${path}`;
}

export async function request(path, options = {}) {
  const response = await execute(path, options);
  if (response.status !== 401 || options.skipAuthRefresh || isAuthBootstrapPath(path)) {
    return response;
  }

  try {
    const { refreshSession } = await import("./auth");
    await refreshSession();
  } catch {
    clearAuthSession();
    return response;
  }
  return execute(path, { ...options, skipAuthRefresh: true });
}

export async function requestJson(path, options = {}) {
  const response = await request(path, options);
  if (!response.ok) {
    const body = await response.text();
    throw new ApiError(extractErrorMessage(body) || `Request failed with status ${response.status}`, {
      status: response.status,
      body
    });
  }
  return response.json();
}

export function extractErrorMessage(body) {
  if (!body) {
    return "";
  }
  try {
    const parsed = JSON.parse(body);
    return parsed?.message ?? parsed?.error ?? body;
  } catch {
    return body;
  }
}

function execute(path, options) {
  const { headers, ...fetchOptions } = options;
  delete fetchOptions.skipAuthRefresh;
  const token = getAuthToken();
  const tenantKey = getSelectedTenantKey();
  return fetch(buildUrl(path), {
    ...fetchOptions,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(headers ?? {}),
      ...(token && !isAuthBootstrapPath(path) ? { Authorization: `Bearer ${token}` } : {}),
      ...(tenantKey && path !== "/api/auth/login" ? { "X-Tenant-Key": tenantKey } : {})
    }
  });
}

function isAuthBootstrapPath(path) {
  return path === "/api/auth/login" || path === "/api/auth/refresh";
}
