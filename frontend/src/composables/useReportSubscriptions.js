import { computed, ref } from "vue";
import {
  changeReportSubscriptionEnabled,
  createReportSubscription,
  deleteReportSubscription,
  listReportSubscriptionRecipients,
  listReportSubscriptions,
  updateReportSubscription
} from "../api/reportSubscriptions";

export function useReportSubscriptions({ currentUser, dictionary, onAuthExpired }) {
  const subscriptions = ref([]);
  const recipients = ref([]);
  const loading = ref(false);
  const loadError = ref("");
  const operationError = ref("");
  const successMessage = ref("");
  const pendingAction = ref("");

  const permissions = computed(() => new Set(currentUser.value?.permissions ?? []));
  const canManage = computed(() => permissions.value.has("REPORT_GENERATE"));

  async function initialize() {
    loading.value = true;
    loadError.value = "";
    try {
      const [subscriptionData, recipientData] = await Promise.all([
        listReportSubscriptions(),
        listReportSubscriptionRecipients()
      ]);
      subscriptions.value = normalizeList(subscriptionData);
      recipients.value = normalizeList(recipientData);
    } catch (error) {
      loadError.value = errorMessage(error);
      handleExpiredSession(error);
    } finally {
      loading.value = false;
    }
  }

  async function create(input) {
    const created = await perform("create", () => createReportSubscription(input),
      dictionary.value.subscriptionCreated);
    if (created) {
      subscriptions.value = [created, ...subscriptions.value];
    }
    return created;
  }

  async function update(subscriptionId, input) {
    const updated = await perform(`update-${subscriptionId}`,
      () => updateReportSubscription(subscriptionId, input),
      dictionary.value.subscriptionUpdated);
    if (updated) {
      replaceById(updated);
    }
    return updated;
  }

  async function setEnabled(subscription, enabled) {
    const updated = await perform(`enabled-${subscription.id}`,
      () => changeReportSubscriptionEnabled(subscription.id, enabled, subscription.version),
      enabled ? dictionary.value.subscriptionEnabled : dictionary.value.subscriptionDisabled);
    if (updated) {
      replaceById(updated);
    }
    return updated;
  }

  async function remove(subscription) {
    const removed = await perform(`delete-${subscription.id}`, async () => {
      await deleteReportSubscription(subscription.id, subscription.version);
      return subscription;
    }, dictionary.value.subscriptionDeleted);
    if (removed) {
      subscriptions.value = subscriptions.value.filter((item) => item.id !== subscription.id);
      return true;
    }
    return false;
  }

  async function perform(action, operation, message) {
    if (!canManage.value || pendingAction.value) {
      return null;
    }
    pendingAction.value = action;
    operationError.value = "";
    successMessage.value = "";
    try {
      const result = await operation();
      successMessage.value = message;
      return result;
    } catch (error) {
      operationError.value = errorMessage(error);
      handleExpiredSession(error);
      return null;
    } finally {
      pendingAction.value = "";
    }
  }

  function replaceById(updated) {
    subscriptions.value = subscriptions.value.map((item) =>
      item.id === updated.id ? updated : item);
  }

  function handleExpiredSession(error) {
    if (error?.status === 401) {
      onAuthExpired?.();
    }
  }

  function errorMessage(error) {
    if (error?.status === 403) {
      return dictionary.value.subscriptionForbiddenError;
    }
    if (error?.status === 409) {
      return dictionary.value.subscriptionConflictError;
    }
    if (error?.status === 400) {
      return error?.message || dictionary.value.subscriptionValidationError;
    }
    return dictionary.value.subscriptionRequestError;
  }

  return {
    canManage,
    create,
    initialize,
    loadError,
    loading,
    operationError,
    pendingAction,
    recipients,
    remove,
    setEnabled,
    subscriptions,
    successMessage,
    update
  };
}

function normalizeList(value) {
  return Array.isArray(value) ? value : [];
}
