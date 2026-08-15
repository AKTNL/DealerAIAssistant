import { computed, ref } from "vue";
import {
  addModelPrice as addModelPriceRequest,
  getModelBudget,
  getModelUsageSummary,
  listModelPrices,
  listModelUsageEvents,
  saveModelBudget as saveModelBudgetRequest
} from "../api/modelUsage";

export function useModelUsage({ currentUser, dictionary, onAuthExpired }) {
  const summary = ref(null);
  const events = ref([]);
  const prices = ref([]);
  const budget = ref(null);
  const loading = ref(false);
  const loadError = ref(null);
  const operationError = ref(null);
  const successMessage = ref("");
  const pendingAction = ref("");

  const permissions = computed(() => new Set(currentUser.value?.permissions ?? []));
  const canRead = computed(() => permissions.value.has("MODEL_USAGE_READ"));
  const canManage = computed(() => permissions.value.has("MODEL_USAGE_MANAGE"));

  async function load(range = {}) {
    if (!canRead.value) {
      return;
    }
    loading.value = true;
    loadError.value = null;
    try {
      const [nextSummary, nextEvents, nextPrices, nextBudget] = await Promise.all([
        getModelUsageSummary(range),
        listModelUsageEvents(range),
        listModelPrices(),
        getModelBudget()
      ]);
      summary.value = nextSummary;
      events.value = normalizeList(nextEvents);
      prices.value = normalizeList(nextPrices);
      budget.value = nextBudget;
    } catch (error) {
      loadError.value = normalizeError(error);
      handleExpiredSession(error);
    } finally {
      loading.value = false;
    }
  }

  async function addPrice(input) {
    const saved = await perform("add-price", () => addModelPriceRequest(input),
      dictionary.value.modelUsagePriceSaved);
    if (saved) {
      prices.value = [saved, ...prices.value];
    }
    return saved;
  }

  async function saveBudget(input) {
    const saved = await perform("save-budget", () => saveModelBudgetRequest(input),
      dictionary.value.modelUsageBudgetSaved);
    if (saved) {
      budget.value = saved;
      if (summary.value) {
        summary.value = { ...summary.value, budget: saved };
      }
    }
    return saved;
  }

  function clearFeedback() {
    operationError.value = null;
    successMessage.value = "";
  }

  async function perform(actionKey, action, notice) {
    pendingAction.value = actionKey;
    clearFeedback();
    try {
      const result = await action();
      successMessage.value = notice ?? "";
      return result;
    } catch (error) {
      operationError.value = normalizeError(error);
      handleExpiredSession(error);
      return null;
    } finally {
      pendingAction.value = "";
    }
  }

  function normalizeError(error) {
    const status = Number(error?.status ?? 0);
    const messages = {
      400: dictionary.value.modelUsageValidationError,
      403: dictionary.value.modelUsageForbiddenError,
      409: dictionary.value.modelUsageConflictError
    };
    return {
      status,
      message: messages[status] ?? dictionary.value.modelUsageRequestError,
      detail: status === 400 ? String(error?.message ?? "") : ""
    };
  }

  function handleExpiredSession(error) {
    if (error?.status === 401) {
      onAuthExpired?.();
    }
  }

  return {
    addPrice,
    budget,
    canManage,
    canRead,
    clearFeedback,
    events,
    load,
    loadError,
    loading,
    operationError,
    pendingAction,
    prices,
    saveBudget,
    successMessage,
    summary
  };
}

function normalizeList(value) {
  return Array.isArray(value) ? value : [];
}
