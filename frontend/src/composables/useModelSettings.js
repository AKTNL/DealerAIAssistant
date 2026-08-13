import { STORAGE_KEYS } from "../constants/storageKeys";

export function isModelSettingsComplete(settings) {
  const normalized = normalizeModelSettings(settings);
  return Boolean(normalized && (normalized.apiKey || settings?.apiKeyConfigured));
}

export function normalizeModelSettings(settings) {
  if (!isModelSettingsRecord(settings)) {
    return null;
  }

  return {
    baseUrl: settings.baseUrl.trim(),
    apiKey: settings.apiKey.trim(),
    model: settings.model.trim(),
    allowedHosts: normalizeAllowedHosts(settings.allowedHosts),
    apiKeyConfigured: settings.apiKeyConfigured === true
  };
}

export function readModelSettings() {
  clearLegacyModelSettings();
  return null;
}

export function writeModelSettings(settings) {
  clearLegacyModelSettings();
  return Boolean(normalizeModelSettings(settings));
}

export function resetModelSettings() {
  clearLegacyModelSettings();
}

function clearLegacyModelSettings() {
  if (typeof window === "undefined") {
    return;
  }

  try {
    window.localStorage.removeItem(STORAGE_KEYS.modelSettings);
    window.sessionStorage.removeItem(STORAGE_KEYS.modelSettings);
  } catch {
    // Ignore storage errors in restricted browser environments.
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
      settings.model.trim() &&
      normalizeAllowedHosts(settings.allowedHosts).length
  );
}

function normalizeAllowedHosts(value) {
  const hosts = Array.isArray(value) ? value : String(value ?? "").split(",");
  return [...new Set(hosts.map(host => String(host).trim().toLowerCase()).filter(Boolean))].sort();
}
