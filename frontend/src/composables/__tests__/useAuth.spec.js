import { beforeEach, describe, expect, it, vi } from "vitest";
import { ref } from "vue";
import { useAuth } from "../useAuth";

const loginUserMock = vi.fn();
const refreshSessionMock = vi.fn();
const getCurrentUserMock = vi.fn();
const changePasswordMock = vi.fn();
const logoutUserMock = vi.fn();

vi.mock("../../api/auth", () => ({
  loginUser: (...args) => loginUserMock(...args),
  refreshSession: (...args) => refreshSessionMock(...args),
  getCurrentUser: (...args) => getCurrentUserMock(...args),
  changePassword: (...args) => changePasswordMock(...args),
  logoutUser: (...args) => logoutUserMock(...args)
}));

const dictionary = ref({ loginError: "Invalid credentials" });
const user = { id: 1, username: "admin", mustChangePassword: true, permissions: [] };

describe("useAuth", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    vi.clearAllMocks();
  });

  it("logs in with username and password and exposes the forced-change state", async () => {
    loginUserMock.mockResolvedValueOnce({ user });
    const auth = useAuth({ dictionary });
    auth.username.value = "admin";
    auth.password.value = "temporary-password";

    await auth.submitCredentials();

    expect(loginUserMock).toHaveBeenCalledWith("admin", "temporary-password");
    expect(auth.authVerified.value).toBe(true);
    expect(auth.mustChangePassword.value).toBe(true);
  });

  it("uses the refresh cookie to restore a page without an access token", async () => {
    refreshSessionMock.mockResolvedValueOnce({ user: { ...user, mustChangePassword: false } });
    const auth = useAuth({ dictionary });
    await auth.initialize();
    expect(refreshSessionMock).toHaveBeenCalledOnce();
    expect(auth.authVerified.value).toBe(true);
  });

  it("clears identity after a successful forced password change", async () => {
    loginUserMock.mockResolvedValueOnce({ user });
    changePasswordMock.mockResolvedValueOnce({});
    const auth = useAuth({ dictionary });
    auth.username.value = "admin";
    auth.password.value = "temporary-password";
    await auth.submitCredentials();
    auth.currentPassword.value = "temporary-password";
    auth.newPassword.value = "permanent-password-2";

    await expect(auth.submitPasswordChange()).resolves.toBe(true);
    expect(auth.authVerified.value).toBe(false);
  });
});
