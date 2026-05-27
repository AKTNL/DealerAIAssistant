import { describe, expect, it, vi, beforeEach } from "vitest";
import { ref } from "vue";
import { useAuth } from "../useAuth";

const verifyAccessKeyMock = vi.fn();

vi.mock("../../api/auth", () => ({
  verifyAccessKey: (...args) => verifyAccessKeyMock(...args)
}));

const dictionary = ref({
  loginError: "The access key is invalid. Please try again."
});

describe("useAuth", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    verifyAccessKeyMock.mockReset();
  });

  it("stores token and expiry in sessionStorage after a successful access-key login", async () => {
    verifyAccessKeyMock.mockResolvedValueOnce({
      success: true,
      sessionToken: "signed-token",
      expiresAt: "2999-01-01T00:00:00.000Z"
    });
    const auth = useAuth({ dictionary });
    auth.accessKey.value = "demo123";

    await auth.submitAccessKey();

    expect(auth.authVerified.value).toBe(true);
    expect(JSON.parse(window.sessionStorage.getItem("agentpoc.authVerified"))).toEqual({
      sessionToken: "signed-token",
      expiresAt: "2999-01-01T00:00:00.000Z"
    });
  });

  it("treats expired stored tokens as logged out", () => {
    window.sessionStorage.setItem(
      "agentpoc.authVerified",
      JSON.stringify({
        sessionToken: "signed-token",
        expiresAt: "2000-01-01T00:00:00.000Z"
      })
    );

    const auth = useAuth({ dictionary });

    expect(auth.authVerified.value).toBe(false);
  });
});
