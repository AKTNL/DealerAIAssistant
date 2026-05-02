import { computed, ref, watch } from "vue";
import { STORAGE_KEYS } from "../constants/storageKeys";
import { readStorageValue, writeStorageValue } from "../utils/storage";

export function useI18nState(messageCatalog) {
  const locale = ref(readStorageValue("local", STORAGE_KEYS.locale, "zh"));
  const dictionary = computed(() => messageCatalog[locale.value] ?? messageCatalog.zh);

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
