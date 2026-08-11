import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearAuthSession,
  getAuthToken,
  getStoredUser,
  isAuthSessionValid,
  readAuthSession,
  writeAuthSession
} from "../sessionToken";

describe("sessionToken", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    vi.useRealTimers();
  });

  it("stores the short-lived access token and normalized current user", () => {
    const accessExpiresAt = new Date(Date.now() + 60 * 60 * 1000).toISOString();
    const user = {
      id: 7,
      username: "analyst",
      displayName: "Analyst",
      enabled: true,
      mustChangePassword: false,
      roles: ["ANALYST"],
      permissions: ["CHAT_USE"]
    };
    writeAuthSession({ accessToken: "opaque-token", accessExpiresAt, user });

    expect(readAuthSession()).toEqual({ accessToken: "opaque-token", accessExpiresAt, user });
    expect(getAuthToken()).toBe("opaque-token");
    expect(getStoredUser()).toEqual(user);
  });

  it("treats expired sessions as invalid", () => {
    writeAuthSession({
      accessToken: "opaque-token",
      accessExpiresAt: "2000-01-01T00:00:00.000Z",
      user: { id: 7 }
    });
    expect(isAuthSessionValid()).toBe(false);
    expect(getAuthToken()).toBe("");
  });

  it("clears malformed and explicitly cleared sessions", () => {
    window.sessionStorage.setItem("agentpoc.authVerified", "{bad-json");
    expect(readAuthSession()).toBeNull();
    writeAuthSession({
      accessToken: "opaque-token",
      accessExpiresAt: new Date(Date.now() + 10000).toISOString(),
      user: { id: 7 }
    });
    clearAuthSession();
    expect(readAuthSession()).toBeNull();
  });
});
