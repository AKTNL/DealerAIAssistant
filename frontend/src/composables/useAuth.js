import { computed, ref } from "vue";
import {
  changePassword,
  getCurrentUser,
  loginUser,
  logoutUser,
  refreshSession
} from "../api/auth";
import { clearAuthSession, getStoredUser, isAuthSessionValid, updateStoredUser } from "../api/sessionToken";
import { clearSelectedTenantKey, setSelectedTenantKey } from "../api/tenantContext";

export function useAuth({ dictionary }) {
  const username = ref("");
  const password = ref("");
  const currentPassword = ref("");
  const newPassword = ref("");
  const currentUser = ref(isAuthSessionValid() ? getStoredUser() : null);
  const hasError = ref(false);
  const loginLoading = ref(false);
  const initialized = ref(false);

  const authVerified = computed(() => Boolean(currentUser.value));
  const mustChangePassword = computed(() => currentUser.value?.mustChangePassword === true);
  const loginError = computed(() => (hasError.value ? dictionary.value.loginError : ""));

  async function initialize() {
    try {
      if (isAuthSessionValid()) {
        currentUser.value = await getCurrentUser();
      } else {
        currentUser.value = (await refreshSession()).user;
      }
    } catch {
      resetLocalState();
    } finally {
      initialized.value = true;
    }
  }

  async function submitCredentials() {
    if (!username.value.trim() || !password.value || loginLoading.value) {
      return;
    }
    hasError.value = false;
    loginLoading.value = true;
    try {
      const session = await loginUser(username.value.trim(), password.value);
      currentUser.value = session.user;
      password.value = "";
    } catch {
      resetLocalState();
      hasError.value = true;
    } finally {
      loginLoading.value = false;
    }
  }

  async function submitPasswordChange() {
    if (!currentPassword.value || !newPassword.value || loginLoading.value) {
      return false;
    }
    hasError.value = false;
    loginLoading.value = true;
    try {
      await changePassword(currentPassword.value, newPassword.value);
      resetLocalState();
      return true;
    } catch {
      hasError.value = true;
      return false;
    } finally {
      currentPassword.value = "";
      newPassword.value = "";
      loginLoading.value = false;
    }
  }

  async function signOut() {
    const hadSession = Boolean(currentUser.value);
    if (hadSession) {
      try {
        await logoutUser();
      } catch {
        // Local cleanup still runs when the server session was already revoked or expired.
      }
    }
    resetLocalState();
  }

  async function selectTenant(tenantKey) {
    setSelectedTenantKey(tenantKey);
    try {
      const user = await getCurrentUser();
      if (!user?.currentTenant?.key) {
        throw new Error("Tenant selection was not accepted.");
      }
      currentUser.value = user;
      updateStoredUser(user);
      return true;
    } catch (error) {
      clearSelectedTenantKey();
      throw error;
    }
  }

  function resetLocalState() {
    currentUser.value = null;
    username.value = "";
    password.value = "";
    currentPassword.value = "";
    newPassword.value = "";
    hasError.value = false;
    clearAuthSession();
    clearSelectedTenantKey();
  }

  return {
    authVerified,
    currentPassword,
    currentUser,
    initialize,
    initialized,
    loginError,
    loginLoading,
    mustChangePassword,
    newPassword,
    password,
    signOut,
    selectTenant,
    submitCredentials,
    submitPasswordChange,
    username
  };
}
