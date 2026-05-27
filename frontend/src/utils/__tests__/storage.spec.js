import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { readStorageValue, removeStorageValue, writeStorageValue } from "../storage";

beforeEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
});

afterEach(() => {
  vi.restoreAllMocks();
  window.localStorage.clear();
  window.sessionStorage.clear();
});

describe("storage helpers", () => {
  it("reads and writes local and session scopes independently", () => {
    writeStorageValue("local", "agent.key", "local-value");
    writeStorageValue("session", "agent.key", "session-value");

    expect(readStorageValue("local", "agent.key")).toBe("local-value");
    expect(readStorageValue("session", "agent.key")).toBe("session-value");
  });

  it("returns the fallback when a value is missing", () => {
    expect(readStorageValue("local", "missing", "fallback")).toBe("fallback");
  });

  it("removes values when writing null", () => {
    writeStorageValue("local", "agent.key", "value");
    writeStorageValue("local", "agent.key", null);

    expect(readStorageValue("local", "agent.key", "fallback")).toBe("fallback");
  });

  it("removes values through removeStorageValue", () => {
    writeStorageValue("session", "agent.key", "value");
    removeStorageValue("session", "agent.key");

    expect(readStorageValue("session", "agent.key", "fallback")).toBe("fallback");
  });

  it("returns fallback when storage reads throw", () => {
    vi.spyOn(Object.getPrototypeOf(window.localStorage), "getItem").mockImplementation(() => {
      throw new Error("storage blocked");
    });

    expect(readStorageValue("local", "agent.key", "fallback")).toBe("fallback");
  });

  it("swallows storage write errors", () => {
    vi.spyOn(Object.getPrototypeOf(window.localStorage), "setItem").mockImplementation(() => {
      throw new Error("quota exceeded");
    });

    expect(() => writeStorageValue("local", "agent.key", "value")).not.toThrow();
  });
});
