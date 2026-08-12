import { afterEach, describe, expect, it, vi } from "vitest";
import { createTemporaryPassword } from "../temporaryPassword";

describe("temporary password generation", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("uses secure randomness and satisfies every password character class", () => {
    let nextValue = 0;
    vi.spyOn(globalThis.crypto, "getRandomValues").mockImplementation((values) => {
      values[0] = nextValue;
      nextValue += 1;
      return values;
    });

    const password = createTemporaryPassword();

    expect(password).toHaveLength(18);
    expect(password).toMatch(/[a-z]/);
    expect(password).toMatch(/[A-Z]/);
    expect(password).toMatch(/[0-9]/);
    expect(password).toMatch(/[!@#$%*_-]/);
  });
});
