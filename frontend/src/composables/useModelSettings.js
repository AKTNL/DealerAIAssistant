import { STORAGE_KEYS } from "../constants/storageKeys";

let modelSettingsState = null;

export function isModelSettingsComplete(settings) {
  return normalizeModelSettings(settings) !== null;
}

export function normalizeModelSettings(settings) {
  if (!isModelSettingsRecord(settings)) {
    return null;
  }

  return {
    baseUrl: settings.baseUrl.trim(),
    apiKey: settings.apiKey.trim(),
    model: settings.model.trim()
  };
}

export function readModelSettings() {
  if (typeof window === "undefined") {
    return null;
  }

  const raw = window.localStorage.getItem(STORAGE_KEYS.modelSettings);

  if (raw) {
    removeLegacyModelSettings();
    return parseStoredModelSettings(raw);
  }

  const legacyRaw = readLegacyModelSettings();

  if (!legacyRaw) {
    modelSettingsState = null;
    return null;
  }

  return migrateLegacyModelSettings(legacyRaw);
}

function parseStoredModelSettings(raw) {
  try {
    modelSettingsState = normalizeModelSettings(JSON.parse(raw));
    return modelSettingsState;
  } catch {
    modelSettingsState = null;
    return null;
  }
}

function migrateLegacyModelSettings(raw) {
  const normalized = parseStoredModelSettings(raw);

  if (!normalized) {
    removeLegacyModelSettings();
    return null;
  }

  try {
    window.localStorage.setItem(STORAGE_KEYS.modelSettings, JSON.stringify(normalized));
    removeLegacyModelSettings();
  } catch {
    // Keep the in-memory value even if the one-time migration cannot persist.
  }

  return normalized;
}

export function writeModelSettings(settings) {
  const normalized = normalizeModelSettings(settings);

  if (!normalized) {
    resetModelSettings();
    return false;
  }

  if (typeof window === "undefined") {
    return false;
  }

  try {
    window.localStorage.setItem(STORAGE_KEYS.modelSettings, JSON.stringify(normalized));
    removeLegacyModelSettings();
    modelSettingsState = normalized;
    return true;
  } catch {
    return false;
  }
}

export function resetModelSettings() {
  modelSettingsState = null;

  if (typeof window === "undefined") {
    return;
  }

  try {
    window.localStorage.removeItem(STORAGE_KEYS.modelSettings);
  } catch {
    // Ignore storage errors in restricted browser environments.
  }

  removeLegacyModelSettings();
}

function removeLegacyModelSettings() {
  if (typeof window === "undefined") {
    return;
  }

  try {
    window.sessionStorage.removeItem(STORAGE_KEYS.modelSettings);
  } catch {
    // Ignore storage errors in restricted browser environments.
  }
}

function readLegacyModelSettings() {
  try {
    return window.sessionStorage.getItem(STORAGE_KEYS.modelSettings);
  } catch {
    return null;
  }
}

function isModelSettingsRecord(settings) {
  return Boolean(
    settings &&
      typeof settings === "object" &&
      !Array.isArray(settings) &&
      typeof settings.baseUrl === "string" &&
      typeof settings.apiKey === "string" &&
      typeof settings.model === "string" &&
      settings.baseUrl.trim() &&
      settings.apiKey.trim() &&
      settings.model.trim()
  );
}
