<script setup>
import { computed, onMounted, ref } from "vue";
import { useReportSubscriptions } from "../../composables/useReportSubscriptions";

const props = defineProps({
  currentUser: { type: Object, required: true },
  dictionary: { type: Object, required: true },
  locale: { type: String, required: true }
});

const emit = defineEmits(["sign-out"]);

const currentUser = computed(() => props.currentUser);
const dictionary = computed(() => props.dictionary);
const editingId = ref(null);
const form = ref(emptyForm());
const {
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
} = useReportSubscriptions({
  currentUser,
  dictionary,
  onAuthExpired: () => emit("sign-out")
});

onMounted(initialize);

async function submit() {
  const input = normalizedInput();
  const result = editingId.value
    ? await update(editingId.value, input)
    : await create(input);
  if (result) {
    cancelEdit();
  }
}

function beginEdit(subscription) {
  const eligibleRecipientIds = new Set(recipients.value.map((recipient) => recipient.userId));
  editingId.value = subscription.id;
  form.value = {
    reportType: subscription.reportType,
    language: subscription.language,
    topic: subscription.topic ?? "",
    scheduleKind: subscription.scheduleKind,
    localTime: subscription.localTime,
    timeZone: subscription.timeZone,
    dayOfWeek: subscription.dayOfWeek ?? 1,
    dayOfMonth: subscription.dayOfMonth ?? 1,
    channelKey: subscription.channelKey,
    recipientUserIds: Array.isArray(subscription.recipientUserIds)
      ? subscription.recipientUserIds.filter((userId) => eligibleRecipientIds.has(userId))
      : [],
    enabled: subscription.enabled === true,
    version: subscription.version
  };
}

function cancelEdit() {
  editingId.value = null;
  form.value = emptyForm();
}

function toggleRecipient(userId, checked) {
  const selected = new Set(form.value.recipientUserIds);
  if (checked) {
    selected.add(userId);
  } else {
    selected.delete(userId);
  }
  form.value.recipientUserIds = [...selected];
}

async function confirmDelete(subscription) {
  if (typeof window !== "undefined" && !window.confirm(props.dictionary.subscriptionConfirmDelete)) {
    return;
  }
  if (await remove(subscription) && editingId.value === subscription.id) {
    cancelEdit();
  }
}

function normalizedInput() {
  const weekly = form.value.scheduleKind === "WEEKLY";
  const monthly = form.value.scheduleKind === "MONTHLY";
  return {
    reportType: form.value.reportType,
    language: form.value.language,
    topic: form.value.topic.trim(),
    scheduleKind: form.value.scheduleKind,
    localTime: form.value.localTime,
    timeZone: form.value.timeZone.trim(),
    dayOfWeek: weekly ? Number(form.value.dayOfWeek) : null,
    dayOfMonth: monthly ? Number(form.value.dayOfMonth) : null,
    channelKey: form.value.channelKey.trim(),
    recipientUserIds: [...form.value.recipientUserIds],
    enabled: form.value.enabled,
    version: form.value.version
  };
}

function emptyForm() {
  return {
    reportType: "daily",
    language: props.locale || "zh",
    topic: "",
    scheduleKind: "DAILY",
    localTime: "09:00",
    timeZone: detectedTimeZone(),
    dayOfWeek: 1,
    dayOfMonth: 1,
    channelKey: "email",
    recipientUserIds: props.currentUser?.id ? [props.currentUser.id] : [],
    enabled: true,
    version: null
  };
}

function detectedTimeZone() {
  if (typeof Intl === "undefined") {
    return "Asia/Shanghai";
  }
  return Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Shanghai";
}

function scheduleLabel(subscription) {
  if (subscription.scheduleKind === "WEEKLY") {
    return `${props.dictionary.subscriptionScheduleWeekly} · ${weekdayLabel(subscription.dayOfWeek)}`;
  }
  if (subscription.scheduleKind === "MONTHLY") {
    return `${props.dictionary.subscriptionScheduleMonthly} · ${props.dictionary.subscriptionMonthDayPrefix}${subscription.dayOfMonth}`;
  }
  return props.dictionary.subscriptionScheduleDaily;
}

function weekdayLabel(day) {
  return props.dictionary.subscriptionWeekdays?.[Number(day) - 1] ?? String(day ?? "");
}

function recipientLabel(subscription) {
  const ids = Array.isArray(subscription.recipientUserIds) ? subscription.recipientUserIds : [];
  return ids.map((id) => recipients.value.find((recipient) => recipient.userId === id)?.displayName ?? id)
    .join(", ");
}

function formatDate(value) {
  if (!value) {
    return props.dictionary.subscriptionNotScheduled;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return props.dictionary.subscriptionNotScheduled;
  }
  return new Intl.DateTimeFormat(props.locale === "zh" ? "zh-CN" : "en", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
}
</script>

<template>
  <section class="subscription-view">
    <header class="subscription-header">
      <div>
        <p class="workspace-eyebrow">{{ dictionary.subscriptionEyebrow }}</p>
        <h1>{{ dictionary.subscriptionTitle }}</h1>
        <p>{{ dictionary.subscriptionSubtitle }}</p>
      </div>
      <button type="button" class="secondary-button" :disabled="loading" @click="initialize">
        {{ dictionary.subscriptionRefresh }}
      </button>
    </header>

    <div v-if="loading" class="subscription-state" role="status">
      <strong>{{ dictionary.subscriptionLoadingTitle }}</strong>
      <span>{{ dictionary.subscriptionLoadingBody }}</span>
    </div>
    <div v-else-if="loadError" class="subscription-state subscription-state-error" role="alert">
      <strong>{{ dictionary.subscriptionLoadError }}</strong>
      <span>{{ loadError }}</span>
    </div>

    <template v-else>
      <form v-if="canManage" class="subscription-form" @submit.prevent="submit">
        <div class="subscription-form-heading">
          <div>
            <h2>{{ editingId ? dictionary.subscriptionEditTitle : dictionary.subscriptionCreateTitle }}</h2>
            <p>{{ dictionary.subscriptionScopeHint }}</p>
          </div>
          <button v-if="editingId" type="button" class="text-button" @click="cancelEdit">
            {{ dictionary.subscriptionCancelEdit }}
          </button>
        </div>

        <div class="subscription-form-grid">
          <label>
            <span>{{ dictionary.subscriptionReportType }}</span>
            <select v-model="form.reportType" required>
              <option value="daily">{{ dictionary.subscriptionReportDaily }}</option>
              <option value="weekly">{{ dictionary.subscriptionReportWeekly }}</option>
              <option value="monthly">{{ dictionary.subscriptionReportMonthly }}</option>
              <option value="topic">{{ dictionary.subscriptionReportTopic }}</option>
            </select>
          </label>
          <label>
            <span>{{ dictionary.subscriptionLanguage }}</span>
            <select v-model="form.language" required>
              <option value="zh">{{ dictionary.subscriptionLanguageZh }}</option>
              <option value="en">{{ dictionary.subscriptionLanguageEn }}</option>
            </select>
          </label>
          <label>
            <span>{{ dictionary.subscriptionScheduleKind }}</span>
            <select v-model="form.scheduleKind" required>
              <option value="DAILY">{{ dictionary.subscriptionScheduleDaily }}</option>
              <option value="WEEKLY">{{ dictionary.subscriptionScheduleWeekly }}</option>
              <option value="MONTHLY">{{ dictionary.subscriptionScheduleMonthly }}</option>
            </select>
          </label>
          <label>
            <span>{{ dictionary.subscriptionLocalTime }}</span>
            <input v-model="form.localTime" type="time" required />
          </label>
          <label>
            <span>{{ dictionary.subscriptionTimeZone }}</span>
            <input v-model="form.timeZone" type="text" required />
          </label>
          <label v-if="form.scheduleKind === 'WEEKLY'">
            <span>{{ dictionary.subscriptionWeekday }}</span>
            <select v-model="form.dayOfWeek" required>
              <option v-for="(weekday, index) in dictionary.subscriptionWeekdays" :key="weekday" :value="index + 1">
                {{ weekday }}
              </option>
            </select>
          </label>
          <label v-if="form.scheduleKind === 'MONTHLY'">
            <span>{{ dictionary.subscriptionMonthDay }}</span>
            <input v-model.number="form.dayOfMonth" type="number" min="1" max="28" required />
          </label>
          <label>
            <span>{{ dictionary.subscriptionChannelKey }}</span>
            <input v-model="form.channelKey" type="text" pattern="[a-z][a-z0-9_-]{0,31}" required />
          </label>
          <label class="subscription-form-wide">
            <span>{{ dictionary.subscriptionTopic }}</span>
            <input v-model="form.topic" type="text" maxlength="500" :required="form.reportType === 'topic'" />
          </label>
        </div>

        <fieldset class="subscription-recipients">
          <legend>{{ dictionary.subscriptionRecipients }}</legend>
          <p v-if="!recipients.length">{{ dictionary.subscriptionRecipientsEmpty }}</p>
          <label v-for="recipient in recipients" :key="recipient.userId">
            <input
              type="checkbox"
              :checked="form.recipientUserIds.includes(recipient.userId)"
              @change="toggleRecipient(recipient.userId, $event.target.checked)"
            />
            <span>{{ recipient.displayName }} ({{ recipient.username }})</span>
          </label>
        </fieldset>

        <label v-if="!editingId" class="subscription-enabled-field">
          <input v-model="form.enabled" type="checkbox" />
          <span>{{ dictionary.subscriptionEnableImmediately }}</span>
        </label>
        <button
          class="primary-button"
          type="submit"
          :disabled="Boolean(pendingAction) || form.recipientUserIds.length === 0"
        >
          {{ editingId ? dictionary.subscriptionSave : dictionary.subscriptionCreate }}
        </button>
      </form>

      <p v-if="operationError" class="subscription-feedback subscription-feedback-error" role="alert">
        {{ operationError }}
      </p>
      <p v-if="successMessage" class="subscription-feedback" role="status">
        {{ successMessage }}
      </p>

      <div class="subscription-list-heading">
        <h2>{{ dictionary.subscriptionListTitle }}</h2>
        <span>{{ subscriptions.length }}</span>
      </div>
      <p v-if="!subscriptions.length" class="subscription-empty">{{ dictionary.subscriptionListEmpty }}</p>
      <div v-else class="subscription-list">
        <article v-for="subscription in subscriptions" :key="subscription.id" class="subscription-card">
          <div class="subscription-card-heading">
            <div>
              <span :class="['subscription-status', { inactive: !subscription.enabled }]">
                {{ subscription.enabled ? dictionary.subscriptionEnabledStatus : dictionary.subscriptionDisabledStatus }}
              </span>
              <h3>{{ dictionary[`subscriptionReport${subscription.reportType[0].toUpperCase()}${subscription.reportType.slice(1)}`] }}</h3>
            </div>
            <span :class="['subscription-eligibility', { denied: !subscription.executionEligible }]">
              {{ subscription.executionEligible ? dictionary.subscriptionEligible : dictionary.subscriptionIneligible }}
            </span>
          </div>
          <dl>
            <div><dt>{{ dictionary.subscriptionScheduleKind }}</dt><dd>{{ scheduleLabel(subscription) }}</dd></div>
            <div><dt>{{ dictionary.subscriptionLocalTime }}</dt><dd>{{ subscription.localTime }} · {{ subscription.timeZone }}</dd></div>
            <div><dt>{{ dictionary.subscriptionNextRun }}</dt><dd>{{ formatDate(subscription.nextRunAt) }}</dd></div>
            <div><dt>{{ dictionary.subscriptionRecipients }}</dt><dd>{{ recipientLabel(subscription) }}</dd></div>
            <div><dt>{{ dictionary.subscriptionChannelKey }}</dt><dd>{{ subscription.channelKey }}</dd></div>
            <div><dt>{{ dictionary.subscriptionMisfire }}</dt><dd>{{ subscription.misfirePolicy }} · {{ subscription.misfireGraceMinutes }} {{ dictionary.subscriptionMinutesSuffix }}</dd></div>
          </dl>
          <p v-if="!subscription.executionEligible" class="subscription-reason">
            {{ dictionary.subscriptionEligibilityReasons?.[subscription.eligibilityReason] ?? subscription.eligibilityReason }}
          </p>
          <div v-if="canManage" class="subscription-card-actions">
            <button type="button" class="text-button" @click="beginEdit(subscription)">
              {{ dictionary.subscriptionEdit }}
            </button>
            <button type="button" class="text-button" :disabled="Boolean(pendingAction)" @click="setEnabled(subscription, !subscription.enabled)">
              {{ subscription.enabled ? dictionary.subscriptionDisable : dictionary.subscriptionEnable }}
            </button>
            <button type="button" class="danger-button" :disabled="Boolean(pendingAction)" @click="confirmDelete(subscription)">
              {{ dictionary.subscriptionDelete }}
            </button>
          </div>
        </article>
      </div>
    </template>
  </section>
</template>
