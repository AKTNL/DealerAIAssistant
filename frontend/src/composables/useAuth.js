import { computed, ref } from "vue";
import { verifyAccessKey } from "../api/auth";
import { clearAuthSession, isAuthSessionValid, writeAuthSession } from "../api/sessionToken";

export function useAuth({ dictionary }) {
  const accessKey = ref("");
  const hasError = ref(false);
  const loginLoading = ref(false);
  const authVerified = ref(isAuthSessionValid());

  const loginError = computed(() =>
    hasError.value ? dictionary.value.loginError : ""
  );

  async function submitAccessKey() {
    if (!accessKey.value.trim() || loginLoading.value) {
      return;
    }

    hasError.value = false;
    loginLoading.value = true;

    try {
      const result = await verifyAccessKey(accessKey.value.trim());

      if (!result.success || !writeAuthSession(result)) {
        throw new Error(dictionary.value.loginError);
      }

      authVerified.value = true;
      accessKey.value = "";
    } catch {
      accessKey.value = "";
      hasError.value = true;
      clearAuthSession();
    } finally {
      loginLoading.value = false;
    }
  }

  function signOut() {
    authVerified.value = false;
    accessKey.value = "";
    hasError.value = false;
    clearAuthSession();
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
