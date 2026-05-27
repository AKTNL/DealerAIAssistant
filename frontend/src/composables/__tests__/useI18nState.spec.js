import { nextTick } from "vue";
import { beforeEach, describe, expect, it } from "vitest";
import { STORAGE_KEYS } from "../../constants/storageKeys";
import { useI18nState } from "../useI18nState";

const messages = {
  zh: {
    label: "中文"
  },
  en: {
    label: "English"
  }
};

beforeEach(() => {
  window.localStorage.clear();
});

describe("useI18nState", () => {
  it("uses Chinese as the default locale", () => {
    const state = useI18nState(messages);

    expect(state.locale.value).toBe("zh");
    expect(state.dictionary.value).toBe(messages.zh);
  });

  it("uses the persisted locale", () => {
    window.localStorage.setItem(STORAGE_KEYS.locale, "en");

    const state = useI18nState(messages);

    expect(state.locale.value).toBe("en");
    expect(state.dictionary.value).toBe(messages.en);
  });

  it("toggles locale and persists the new value", async () => {
    const state = useI18nState(messages);

    state.toggleLocale();
    await nextTick();

    expect(state.locale.value).toBe("en");
    expect(state.dictionary.value).toBe(messages.en);
    expect(window.localStorage.getItem(STORAGE_KEYS.locale)).toBe("en");
  });

  it("falls back to Chinese when storage contains an unsupported locale", () => {
    window.localStorage.setItem(STORAGE_KEYS.locale, "fr");

    const state = useI18nState(messages);

    expect(state.locale.value).toBe("zh");
    expect(state.dictionary.value).toBe(messages.zh);
    expect(window.localStorage.getItem(STORAGE_KEYS.locale)).toBe("zh");
  });
});
