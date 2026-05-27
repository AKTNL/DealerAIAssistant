import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearAuthSession,
  getAuthToken,
  isAuthSessionValid,
  readAuthSession,
  writeAuthSession
} from "../sessionToken";

describe("sessionToken", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    vi.useRealTimers();
  });

  it("stores and reads auth session tokens from sessionStorage", () => {
    const expiresAt = new Date(Date.now() + 60 * 60 * 1000).toISOString();

    writeAuthSession({
      sessionToken: "signed-token",
      expiresAt
    });

    expect(readAuthSession()).toEqual({
      sessionToken: "signed-token",
      expiresAt
    });
    expect(getAuthToken()).toBe("signed-token");
  });

  it("treats expired sessions as invalid", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-21T17:00:00.000Z"));

    writeAuthSession({
      sessionToken: "signed-token",
      expiresAt: "2026-05-21T16:00:00.000Z"
    });

    expect(isAuthSessionValid()).toBe(false);
    expect(getAuthToken()).toBe("");
  });

  it("clears malformed stored sessions", () => {
    window.sessionStorage.setItem("agentpoc.authVerified", "{bad-json");

    expect(readAuthSession()).toBeNull();
    expect(window.sessionStorage.getItem("agentpoc.authVerified")).toBeNull();
  });

  it("clears auth sessions", () => {
    writeAuthSession({
      sessionToken: "signed-token",
      expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString()
    });

    clearAuthSession();

    expect(readAuthSession()).toBeNull();
  });
});
