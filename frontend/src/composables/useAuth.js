import { ref } from "vue";
import { verifyAccessKey } from "../api/auth";
import { STORAGE_KEYS } from "../constants/storageKeys";
import { readStorageValue, removeStorageValue, writeStorageValue } from "../utils/storage";

export function useAuth({ dictionary }) {
  const accessKey = ref("");
  const loginError = ref("");
  const loginLoading = ref(false);
  const authVerified = ref(readStorageValue("session", STORAGE_KEYS.auth, "false") === "true");

  async function submitAccessKey() {
    if (!accessKey.value.trim() || loginLoading.value) {
      return;
    }

    loginError.value = "";
    loginLoading.value = true;

    try {
      const result = await verifyAccessKey(accessKey.value.trim());

      if (!result.success) {
        throw new Error(dictionary.value.loginError);
      }

      authVerified.value = true;
      accessKey.value = "";
      writeStorageValue("session", STORAGE_KEYS.auth, "true");
    } catch (error) {
      loginError.value = error.message || dictionary.value.loginError;
    } finally {
      loginLoading.value = false;
    }
  }

  function signOut() {
    authVerified.value = false;
    accessKey.value = "";
    loginError.value = "";
    removeStorageValue("session", STORAGE_KEYS.auth);
  }

  return {
    accessKey,
    authVerified,
    loginError,
    loginLoading,
    signOut,
    submitAccessKey
  };
}
