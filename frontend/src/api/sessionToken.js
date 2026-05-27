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
  return session && isFuture(session.expiresAt) ? session.sessionToken : "";
}

export function isAuthSessionValid() {
  return Boolean(getAuthToken());
}

function normalizeAuthSession(session) {
  if (
    !session ||
    typeof session !== "object" ||
    Array.isArray(session) ||
    typeof session.sessionToken !== "string" ||
    typeof session.expiresAt !== "string" ||
    !session.sessionToken.trim() ||
    !isFiniteDate(session.expiresAt)
  ) {
    return null;
  }

  return {
    sessionToken: session.sessionToken.trim(),
    expiresAt: session.expiresAt
  };
}

function isFiniteDate(value) {
  return Number.isFinite(Date.parse(value));
}

function isFuture(value) {
  return isFiniteDate(value) && Date.parse(value) > Date.now();
}
