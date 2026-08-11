import { beforeEach, describe, expect, it, vi } from "vitest";

const requestJsonMock = vi.fn();

vi.mock("../client", () => ({
  requestJson: (...args) => requestJsonMock(...args)
}));

describe("auth API", () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    window.sessionStorage.clear();
  });

  it("shares one refresh request across concurrent 401 recovery callers", async () => {
    let resolveRefresh;
    requestJsonMock.mockReturnValueOnce(new Promise((resolve) => {
      resolveRefresh = resolve;
    }));
    const { refreshSession } = await import("../auth");

    const first = refreshSession();
    const second = refreshSession();
    const third = refreshSession();
    expect(requestJsonMock).toHaveBeenCalledOnce();

    resolveRefresh({
      data: {
        accessToken: "rotated-access",
        accessExpiresAt: "2999-01-01T00:00:00.000Z",
        user: { id: 1, username: "admin", permissions: [] }
      }
    });
    await expect(Promise.all([first, second, third])).resolves.toHaveLength(3);
    expect(requestJsonMock).toHaveBeenCalledWith("/api/auth/refresh", {
      method: "POST",
      skipAuthRefresh: true
    });
  });

  it("sends username and password through the login endpoint", async () => {
    requestJsonMock.mockResolvedValueOnce({
      data: {
        accessToken: "access-token",
        accessExpiresAt: "2999-01-01T00:00:00.000Z",
        user: { id: 1, username: "admin", permissions: [] }
      }
    });
    const { loginUser } = await import("../auth");

    await loginUser("admin", "temporary-password");

    expect(requestJsonMock).toHaveBeenCalledWith("/api/auth/login", {
      method: "POST",
      skipAuthRefresh: true,
      body: JSON.stringify({ username: "admin", password: "temporary-password" })
    });
  });

  it("uses the password and logout lifecycle endpoints", async () => {
    requestJsonMock.mockResolvedValue({ data: null });
    const { changePassword, logoutUser } = await import("../auth");

    await changePassword("temporary-password", "permanent-password");
    await logoutUser();
    await logoutUser({ all: true });

    expect(requestJsonMock).toHaveBeenNthCalledWith(1, "/api/auth/password", {
      method: "POST",
      body: JSON.stringify({
        currentPassword: "temporary-password",
        newPassword: "permanent-password"
      })
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(2, "/api/auth/logout", {
      method: "POST",
      skipAuthRefresh: true
    });
    expect(requestJsonMock).toHaveBeenNthCalledWith(3, "/api/auth/logout-all", {
      method: "POST",
      skipAuthRefresh: true
    });
  });
});
