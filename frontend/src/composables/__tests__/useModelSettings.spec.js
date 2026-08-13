import { beforeEach, describe, expect, it, vi } from "vitest";
import { STORAGE_KEYS } from "../../constants/storageKeys";
import {
  isModelSettingsComplete,
  readModelSettings,
  resetModelSettings,
  writeModelSettings
} from "../useModelSettings";

beforeEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
  resetModelSettings();
});

describe("useModelSettings", () => {
  it("never persists model credentials in browser storage", () => {
    const settings = {
      baseUrl: "https://api.example.com",
      apiKey: "test-key",
      model: "test-model",
      allowedHosts: ["api.example.com"]
    };
    expect(writeModelSettings(settings)).toBe(true);
    expect(window.localStorage.getItem(STORAGE_KEYS.modelSettings)).toBeNull();
    expect(window.sessionStorage.getItem(STORAGE_KEYS.modelSettings)).toBeNull();
    expect(readModelSettings()).toBe(null);
    expect(isModelSettingsComplete(settings)).toBe(true);
  });

  it("normalizes model settings before sending them to the server", async () => {
    const { normalizeModelSettings } = await import("../useModelSettings");
    expect(normalizeModelSettings({
      baseUrl: " https://api.example.com ",
      apiKey: " sk-test ",
      model: " gpt-4.1-mini ",
      allowedHosts: " API.EXAMPLE.COM, *.example.com "
    })).toEqual({
      baseUrl: "https://api.example.com",
      apiKey: "sk-test",
      model: "gpt-4.1-mini",
      allowedHosts: ["*.example.com", "api.example.com"],
      apiKeyConfigured: false
    });
  });

  it("returns null for malformed or incomplete stored values", () => {
    window.localStorage.setItem(STORAGE_KEYS.modelSettings, JSON.stringify("bad"));
    expect(readModelSettings()).toBe(null);

    window.localStorage.setItem(
      STORAGE_KEYS.modelSettings,
      JSON.stringify({ baseUrl: "https://api.example.com", apiKey: "test-key" })
    );
    expect(readModelSettings()).toBe(null);
  });

  it("deletes legacy browser credentials instead of migrating them", () => {
    const settings = {
      baseUrl: "https://api.example.com",
      apiKey: "test-key",
      model: "test-model"
    };

    window.sessionStorage.setItem(STORAGE_KEYS.modelSettings, JSON.stringify(settings));

    window.localStorage.setItem(STORAGE_KEYS.modelSettings, JSON.stringify(settings));
    expect(readModelSettings()).toBeNull();
    expect(window.localStorage.getItem(STORAGE_KEYS.modelSettings)).toBeNull();
    expect(window.sessionStorage.getItem(STORAGE_KEYS.modelSettings)).toBeNull();
  });

  it("does not leak state when window is unavailable", () => {
    const browserWindow = window;
    vi.stubGlobal("window", undefined);

    try {
      expect(writeModelSettings({
        baseUrl: "https://api.example.com",
        apiKey: "test-key",
        model: "test-model",
        allowedHosts: ["api.example.com"]
      })).toBe(true);
      expect(readModelSettings()).toBe(null);
    } finally {
      vi.stubGlobal("window", browserWindow);
    }
  });

  it("does not throw when legacy storage cleanup fails", () => {
    const setItemSpy = vi.spyOn(window.localStorage.__proto__, "setItem")
      .mockImplementation(() => {
        throw new Error("storage disabled");
      });

    expect(writeModelSettings({
      baseUrl: "https://api.example.com",
      apiKey: "test-key",
      model: "test-model",
      allowedHosts: ["api.example.com"]
    })).toBe(true);
    expect(readModelSettings()).toBe(null);

    setItemSpy.mockRestore();
  });
});
