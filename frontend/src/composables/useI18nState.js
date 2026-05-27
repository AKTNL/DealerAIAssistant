import { computed, ref, watch } from "vue";
import { STORAGE_KEYS } from "../constants/storageKeys";
import { readStorageValue, writeStorageValue } from "../utils/storage";

export function useI18nState(messageCatalog) {
  const fallbackLocale = messageCatalog.zh ? "zh" : Object.keys(messageCatalog)[0];
  const storedLocale = readStorageValue("local", STORAGE_KEYS.locale, fallbackLocale);
  const locale = ref(normalizeLocale(storedLocale, messageCatalog, fallbackLocale));
  const dictionary = computed(() => messageCatalog[locale.value] ?? messageCatalog[fallbackLocale]);

  if (locale.value !== storedLocale) {
    writeStorageValue("local", STORAGE_KEYS.locale, locale.value);
  }

  watch(locale, (value) => {
    writeStorageValue("local", STORAGE_KEYS.locale, value);
  });

  function toggleLocale() {
    locale.value = locale.value === "zh" ? "en" : "zh";
  }

  return {
    locale,
    dictionary,
    toggleLocale
  };
}

function normalizeLocale(value, messageCatalog, fallbackLocale) {
  return Object.prototype.hasOwnProperty.call(messageCatalog, value) ? value : fallbackLocale;
}
