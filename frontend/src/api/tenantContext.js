import { STORAGE_KEYS } from "../constants/storageKeys";
import { readStorageValue, removeStorageValue, writeStorageValue } from "../utils/storage";

export function getSelectedTenantKey() {
  return readStorageValue("session", STORAGE_KEYS.tenant, "").trim();
}

export function setSelectedTenantKey(tenantKey) {
  const normalized = String(tenantKey ?? "").trim().toLowerCase();
  if (!normalized) {
    clearSelectedTenantKey();
    return;
  }
  writeStorageValue("session", STORAGE_KEYS.tenant, normalized);
}

export function clearSelectedTenantKey() {
  removeStorageValue("session", STORAGE_KEYS.tenant);
}
