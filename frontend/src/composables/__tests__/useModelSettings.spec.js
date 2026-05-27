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
  it("reads, writes, and resets persistent model settings using localStorage", () => {
    expect(readModelSettings()).toBe(null);
    expect(isModelSettingsComplete(null)).toBe(false);

    const settings = {
      baseUrl: "https://api.example.com",
      apiKey: "test-key",
      model: "test-model"
    };

    expect(writeModelSettings(settings)).toBe(true);
    expect(JSON.parse(window.localStorage.getItem(STORAGE_KEYS.modelSettings))).toEqual(settings);
    expect(window.sessionStorage.getItem(STORAGE_KEYS.modelSettings)).toBeNull();
    expect(readModelSettings()).toEqual(settings);
    expect(isModelSettingsComplete(readModelSettings())).toBe(true);

    resetModelSettings();
    expect(readModelSettings()).toBe(null);
    expect(window.sessionStorage.getItem(STORAGE_KEYS.modelSettings)).toBeNull();
    expect(window.localStorage.getItem(STORAGE_KEYS.modelSettings)).toBeNull();
  });

  it("trims model settings before persisting them", () => {
    expect(writeModelSettings({
      baseUrl: " https://api.example.com ",
      apiKey: " sk-test ",
      model: " gpt-4.1-mini "
    })).toBe(true);

    expect(readModelSettings()).toEqual({
      baseUrl: "https://api.example.com",
      apiKey: "sk-test",
      model: "gpt-4.1-mini"
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

  it("migrates legacy sessionStorage settings into localStorage", () => {
    const settings = {
      baseUrl: "https://api.example.com",
      apiKey: "test-key",
      model: "test-model"
    };

    window.sessionStorage.setItem(STORAGE_KEYS.modelSettings, JSON.stringify(settings));

    expect(readModelSettings()).toEqual(settings);
    expect(window.localStorage.getItem(STORAGE_KEYS.modelSettings)).toBe(JSON.stringify(settings));
    expect(window.sessionStorage.getItem(STORAGE_KEYS.modelSettings)).toBeNull();
  });

  it("does not leak state when window is unavailable", () => {
    const browserWindow = window;
    vi.stubGlobal("window", undefined);

    expect(writeModelSettings({
      baseUrl: "https://api.example.com",
      apiKey: "test-key",
      model: "test-model"
    })).toBe(false);
    expect(readModelSettings()).toBe(null);

    vi.stubGlobal("window", browserWindow);
  });

  it("does not update in-memory state when storage write fails", () => {
    const setItemSpy = vi.spyOn(window.localStorage.__proto__, "setItem")
      .mockImplementation(() => {
        throw new Error("storage disabled");
      });

    expect(writeModelSettings({
      baseUrl: "https://api.example.com",
      apiKey: "test-key",
      model: "test-model"
    })).toBe(false);
    expect(readModelSettings()).toBe(null);

    setItemSpy.mockRestore();
  });
});
