import { STORAGE_KEYS } from "../constants/storageKeys";
import { readStorageValue, removeStorageValue, writeStorageValue } from "../utils/storage";

export function writeAuthSession(session) {
  const normalized = normalizeAuthSession(session);
  if (!normalized) {
    clearAuthSession();
    return false;
  }
  writeStorageValue("session", STORAGE_KEYS.auth, JSON.stringify(normalized));
  return true;
}

export function readAuthSession() {
  const raw = readStorageValue("session", STORAGE_KEYS.auth, "");
  if (!raw) {
    return null;
  }
  try {
    const normalized = normalizeAuthSession(JSON.parse(raw));
    if (!normalized) {
      clearAuthSession();
      return null;
    }
    return normalized;
  } catch {
    clearAuthSession();
    return null;
  }
}

export function clearAuthSession() {
  removeStorageValue("session", STORAGE_KEYS.auth);
}

export function getAuthToken() {
  const session = readAuthSession();
  return session && isFuture(session.accessExpiresAt) ? session.accessToken : "";
}

export function getStoredUser() {
  return readAuthSession()?.user ?? null;
}

export function isAuthSessionValid() {
  return Boolean(getAuthToken());
}

function normalizeAuthSession(session) {
  if (
    !session ||
    typeof session !== "object" ||
    Array.isArray(session) ||
    typeof session.accessToken !== "string" ||
    typeof session.accessExpiresAt !== "string" ||
    !session.accessToken.trim() ||
    !isFiniteDate(session.accessExpiresAt)
  ) {
    return null;
  }
  return {
    accessToken: session.accessToken.trim(),
    accessExpiresAt: session.accessExpiresAt,
    user: normalizeUser(session.user)
  };
}

function normalizeUser(user) {
  if (!user || typeof user !== "object" || !Number.isFinite(Number(user.id))) {
    return null;
  }
  return {
    id: Number(user.id),
    username: String(user.username ?? ""),
    displayName: String(user.displayName ?? ""),
    enabled: user.enabled === true,
    mustChangePassword: user.mustChangePassword === true,
    roles: Array.isArray(user.roles) ? user.roles.map(String) : [],
    permissions: Array.isArray(user.permissions) ? user.permissions.map(String) : []
  };
}

function isFiniteDate(value) {
  return Number.isFinite(Date.parse(value));
}

function isFuture(value) {
  return isFiniteDate(value) && Date.parse(value) > Date.now();
}
