function resolveStorage(scope) {
  if (typeof window === "undefined") {
    return null;
  }

  return scope === "local" ? window.localStorage : window.sessionStorage;
}

export function readStorageValue(scope, key, fallback = "") {
  try {
    const storage = resolveStorage(scope);
    return storage?.getItem(key) ?? fallback;
  } catch {
    return fallback;
  }
}

export function writeStorageValue(scope, key, value) {
  try {
    const storage = resolveStorage(scope);

    if (!storage) {
      return;
    }

    if (value == null) {
      storage.removeItem(key);
      return;
    }

    storage.setItem(key, value);
  } catch {
    // Swallow storage errors in environments where storage is unavailable.
  }
}

export function removeStorageValue(scope, key) {
  writeStorageValue(scope, key, null);
}
