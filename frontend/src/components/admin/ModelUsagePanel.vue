<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useModelUsage } from "../../composables/useModelUsage";

const props = defineProps({
  currentUser: { type: Object, required: true },
  dictionary: { type: Object, required: true },
  locale: { type: String, required: true }
});

const emit = defineEmits(["sign-out"]);
const usage = useModelUsage({
  currentUser: computed(() => props.currentUser),
  dictionary: computed(() => props.dictionary),
  onAuthExpired: () => emit("sign-out")
});
const range = ref(defaultRange());
const budgetForm = ref(emptyBudget());
const priceForm = ref(emptyPrice());

const total = computed(() => usage.summary.value?.total ?? emptyAggregate());
const totalCost = computed(() => formatCosts(total.value.costs));
const highestCostScenarios = computed(() => (usage.summary.value?.byScenario ?? []).slice(0, 8));
const highestCostModels = computed(() => (usage.summary.value?.byModel ?? []).slice(0, 8));

watch(usage.budget, (value) => {
  budgetForm.value = value ? {
    monthlyLimit: value.monthlyLimit,
    softThresholdPercent: value.softThresholdPercent,
    hardLimitEnabled: value.hardLimitEnabled === true,
    failOpen: value.failOpen !== false,
    reservationAmount: value.reservationAmount,
    currency: value.currency,
    version: value.version
  } : emptyBudget();
}, { immediate: true });

onMounted(refresh);

async function refresh() {
  usage.clearFeedback();
  await usage.load(toApiRange());
}

async function submitBudget() {
  const saved = await usage.saveBudget({
    monthlyLimit: Number(budgetForm.value.monthlyLimit),
    softThresholdPercent: Number(budgetForm.value.softThresholdPercent),
    hardLimitEnabled: budgetForm.value.hardLimitEnabled === true,
    failOpen: budgetForm.value.failOpen === true,
    reservationAmount: Number(budgetForm.value.reservationAmount),
    currency: String(budgetForm.value.currency).trim().toUpperCase(),
    version: budgetForm.value.version
  });
  if (saved) {
    budgetForm.value.version = saved.version;
  }
}

async function submitPrice() {
  const saved = await usage.addPrice({
    provider: priceForm.value.provider.trim(),
    model: priceForm.value.model.trim(),
    versionKey: priceForm.value.versionKey.trim() || null,
    inputPricePerMillion: Number(priceForm.value.inputPricePerMillion),
    outputPricePerMillion: Number(priceForm.value.outputPricePerMillion),
    currency: priceForm.value.currency.trim().toUpperCase(),
    source: priceForm.value.source.trim(),
    effectiveFrom: toInstant(priceForm.value.effectiveFrom)
  });
  if (saved) {
    priceForm.value = emptyPrice();
  }
}

function toApiRange() {
  return {
    from: startOfDay(range.value.from),
    to: endExclusive(range.value.to)
  };
}

function formatCosts(costs) {
  if (!Array.isArray(costs) || costs.length === 0) {
    return props.dictionary.modelUsageUnknown;
  }
  return costs.map((cost) => `${formatNumber(cost.amount, 6)} ${cost.currency}`).join(" + ");
}

function formatTokens(value) {
  return new Intl.NumberFormat(props.locale === "zh" ? "zh-CN" : "en").format(Number(value ?? 0));
}

function formatNumber(value, maximumFractionDigits = 2) {
  return new Intl.NumberFormat(props.locale === "zh" ? "zh-CN" : "en", {
    maximumFractionDigits
  }).format(Number(value ?? 0));
}

function formatDate(value) {
  if (!value) {
    return props.dictionary.modelUsageUnknown;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return props.dictionary.modelUsageUnknown;
  }
  return new Intl.DateTimeFormat(props.locale === "zh" ? "zh-CN" : "en", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
}

function formatStatus(value) {
  return props.dictionary.modelUsageStatusLabels?.[value] ?? value;
}

function formatScenario(value) {
  return props.dictionary.modelUsageScenarioLabels?.[value] ?? value;
}

function formatAnomaly(value) {
  return props.dictionary.modelUsageAnomalyLabels?.[value] ?? value;
}

function defaultRange() {
  const to = new Date();
  const from = new Date(to);
  from.setDate(from.getDate() - 29);
  return { from: dateInput(from), to: dateInput(to) };
}

function emptyBudget() {
  return {
    monthlyLimit: 100,
    softThresholdPercent: 80,
    hardLimitEnabled: false,
    failOpen: true,
    reservationAmount: 0,
    currency: "USD",
    version: null
  };
}

function emptyPrice() {
  return {
    provider: "openai-compatible",
    model: "",
    versionKey: "",
    inputPricePerMillion: 0,
    outputPricePerMillion: 0,
    currency: "USD",
    source: "MANUAL",
    effectiveFrom: ""
  };
}

function emptyAggregate() {
  return {
    calls: 0,
    errorCalls: 0,
    rejectedCalls: 0,
    unknownTokenCalls: 0,
    inputTokens: 0,
    outputTokens: 0,
    durationMs: 0,
    costs: []
  };
}

function startOfDay(value) {
  return value ? new Date(`${value}T00:00:00`).toISOString() : null;
}

function endExclusive(value) {
  if (!value) {
    return null;
  }
  const date = new Date(`${value}T00:00:00`);
  date.setDate(date.getDate() + 1);
  return date.toISOString();
}

function toInstant(value) {
  return value ? new Date(value).toISOString() : null;
}

function dateInput(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}
</script>

<template>
  <section class="admin-panel model-usage-panel" aria-labelledby="model-usage-heading">
    <div class="admin-panel-heading model-usage-heading">
      <div>
        <h3 id="model-usage-heading">{{ dictionary.modelUsageTitle }}</h3>
        <p>{{ dictionary.modelUsageBody }}</p>
      </div>
      <form class="model-usage-range" @submit.prevent="refresh">
        <label>
          <span>{{ dictionary.modelUsageFrom }}</span>
          <input v-model="range.from" class="text-input" type="date" required />
        </label>
        <label>
          <span>{{ dictionary.modelUsageTo }}</span>
          <input v-model="range.to" class="text-input" type="date" required />
        </label>
        <button class="ghost-button" type="submit" :disabled="usage.loading.value">
          <span class="material-icons" aria-hidden="true">refresh</span>
          {{ dictionary.adminRefresh }}
        </button>
      </form>
    </div>

    <div v-if="usage.loading.value" class="admin-state-card" role="status">
      <span class="skeleton-spinner-icon" aria-hidden="true"></span>
      <p>{{ dictionary.modelUsageLoading }}</p>
    </div>
    <div v-else-if="usage.loadError.value" class="admin-state-card admin-error-card" role="alert">
      <span class="material-icons" aria-hidden="true">error</span>
      <div>
        <strong>{{ usage.loadError.value.message }}</strong>
        <button class="ghost-button" type="button" @click="refresh">{{ dictionary.adminRetry }}</button>
      </div>
    </div>

    <template v-else>
      <p v-if="usage.operationError.value" class="admin-feedback admin-feedback-error" role="alert">
        <strong>{{ usage.operationError.value.message }}</strong>
        <span v-if="usage.operationError.value.detail">{{ usage.operationError.value.detail }}</span>
      </p>
      <p v-else-if="usage.successMessage.value" class="admin-feedback" role="status">
        {{ usage.successMessage.value }}
      </p>

      <div class="model-usage-metrics">
        <div><span>{{ dictionary.modelUsageCalls }}</span><strong>{{ formatTokens(total.calls) }}</strong></div>
        <div><span>{{ dictionary.modelUsageInputTokens }}</span><strong>{{ formatTokens(total.inputTokens) }}</strong></div>
        <div><span>{{ dictionary.modelUsageOutputTokens }}</span><strong>{{ formatTokens(total.outputTokens) }}</strong></div>
        <div><span>{{ dictionary.modelUsageEstimatedCost }}</span><strong>{{ totalCost }}</strong></div>
        <div><span>{{ dictionary.modelUsageFailures }}</span><strong>{{ formatTokens(total.errorCalls) }}</strong></div>
        <div><span>{{ dictionary.modelUsageUnknownTokens }}</span><strong>{{ formatTokens(total.unknownTokenCalls) }}</strong></div>
      </div>

      <div v-if="usage.budget.value" class="model-usage-budget-status">
        <div>
          <span>{{ dictionary.modelUsageBudgetStatus }}</span>
          <strong>{{ formatStatus(usage.budget.value.state) }}</strong>
        </div>
        <div>
          <span>{{ dictionary.modelUsageMonthSpend }}</span>
          <strong>{{ formatNumber(usage.budget.value.monthToDateCost, 6) }} {{ usage.budget.value.currency }}</strong>
        </div>
        <div>
          <span>{{ dictionary.modelUsageBudgetUsage }}</span>
          <strong>{{ formatNumber(usage.budget.value.usagePercent) }}%</strong>
        </div>
      </div>

      <div class="model-usage-grid">
        <section class="model-usage-section" aria-labelledby="model-usage-scenarios">
          <h4 id="model-usage-scenarios">{{ dictionary.modelUsageByScenario }}</h4>
          <div v-if="highestCostScenarios.length === 0" class="admin-empty-card">
            <p>{{ dictionary.modelUsageEmpty }}</p>
          </div>
          <div v-else class="admin-table-wrap">
            <table class="admin-table">
              <thead><tr><th>{{ dictionary.modelUsageScenario }}</th><th>{{ dictionary.modelUsageCalls }}</th><th>{{ dictionary.modelUsageEstimatedCost }}</th></tr></thead>
              <tbody>
                <tr v-for="item in highestCostScenarios" :key="item.key">
                  <td>{{ formatScenario(item.key) }}</td><td>{{ formatTokens(item.calls) }}</td><td>{{ formatCosts(item.costs) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="model-usage-section" aria-labelledby="model-usage-models">
          <h4 id="model-usage-models">{{ dictionary.modelUsageByModel }}</h4>
          <div v-if="highestCostModels.length === 0" class="admin-empty-card"><p>{{ dictionary.modelUsageEmpty }}</p></div>
          <div v-else class="admin-table-wrap">
            <table class="admin-table">
              <thead><tr><th>{{ dictionary.modelUsageModel }}</th><th>{{ dictionary.modelUsageCalls }}</th><th>{{ dictionary.modelUsageEstimatedCost }}</th></tr></thead>
              <tbody>
                <tr v-for="item in highestCostModels" :key="item.key">
                  <td>{{ item.key }}</td><td>{{ formatTokens(item.calls) }}</td><td>{{ formatCosts(item.costs) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>

      <section class="model-usage-section" aria-labelledby="model-usage-anomalies">
        <h4 id="model-usage-anomalies">{{ dictionary.modelUsageAnomalies }}</h4>
        <div v-if="(usage.summary.value?.anomalies ?? []).length === 0" class="admin-empty-card"><p>{{ dictionary.modelUsageNoAnomalies }}</p></div>
        <div v-else class="admin-table-wrap">
          <table class="admin-table">
            <thead><tr><th>{{ dictionary.modelUsageTime }}</th><th>{{ dictionary.modelUsageReason }}</th><th>{{ dictionary.modelUsageScenario }}</th><th>{{ dictionary.modelUsageModel }}</th><th>{{ dictionary.modelUsageTrace }}</th></tr></thead>
            <tbody>
              <tr v-for="item in usage.summary.value.anomalies" :key="item.event.callKey">
                <td>{{ formatDate(item.event.occurredAt) }}</td><td>{{ formatAnomaly(item.reason) }}</td><td>{{ formatScenario(item.event.scenario) }}</td><td>{{ item.event.model }}</td><td><code>{{ item.event.traceId }}</code></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="model-usage-section" aria-labelledby="model-usage-events">
        <h4 id="model-usage-events">{{ dictionary.modelUsageRecentEvents }}</h4>
        <div v-if="usage.events.value.length === 0" class="admin-empty-card"><p>{{ dictionary.modelUsageEmpty }}</p></div>
        <div v-else class="admin-table-wrap">
          <table class="admin-table model-usage-events-table">
            <thead><tr><th>{{ dictionary.modelUsageTime }}</th><th>{{ dictionary.modelUsageScenario }}</th><th>{{ dictionary.modelUsageModel }}</th><th>{{ dictionary.modelUsageStatus }}</th><th>{{ dictionary.modelUsageTokens }}</th><th>{{ dictionary.modelUsageEstimatedCost }}</th><th>{{ dictionary.modelUsageTrace }}</th></tr></thead>
            <tbody>
              <tr v-for="event in usage.events.value" :key="event.callKey">
                <td>{{ formatDate(event.occurredAt) }}</td><td>{{ formatScenario(event.scenario) }}</td><td>{{ event.provider }}/{{ event.model }}</td><td>{{ formatStatus(event.status) }}</td><td>{{ event.tokenState === 'UNKNOWN' ? dictionary.modelUsageUnknown : formatTokens(event.totalTokens) }}</td><td>{{ event.estimatedCost == null ? dictionary.modelUsageUnknown : `${formatNumber(event.estimatedCost, 6)} ${event.currency}` }}</td><td><code>{{ event.traceId }}</code></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <div v-if="usage.canManage.value" class="model-usage-grid model-usage-management">
        <section class="model-usage-section" aria-labelledby="model-usage-budget-form">
          <h4 id="model-usage-budget-form">{{ dictionary.modelUsageBudgetPolicy }}</h4>
          <form class="admin-inline-form" @submit.prevent="submitBudget">
            <label><span>{{ dictionary.modelUsageMonthlyLimit }}</span><input v-model.number="budgetForm.monthlyLimit" class="text-input" type="number" min="0.00000001" step="0.00000001" required /></label>
            <label><span>{{ dictionary.modelUsageCurrency }}</span><input v-model="budgetForm.currency" class="text-input" type="text" minlength="3" maxlength="3" required /></label>
            <label><span>{{ dictionary.modelUsageSoftThreshold }}</span><input v-model.number="budgetForm.softThresholdPercent" class="text-input" type="number" min="1" max="100" required /></label>
            <label><span>{{ dictionary.modelUsageReservation }}</span><input v-model.number="budgetForm.reservationAmount" class="text-input" type="number" min="0" step="0.00000001" required /></label>
            <label class="subscription-enabled-field"><input v-model="budgetForm.hardLimitEnabled" type="checkbox" /><span>{{ dictionary.modelUsageHardLimit }}</span></label>
            <label class="subscription-enabled-field"><input v-model="budgetForm.failOpen" type="checkbox" /><span>{{ dictionary.modelUsageFailOpen }}</span></label>
            <button class="primary-button" type="submit" :disabled="Boolean(usage.pendingAction.value)">{{ dictionary.modelUsageSaveBudget }}</button>
          </form>
        </section>

        <section class="model-usage-section" aria-labelledby="model-usage-price-form">
          <h4 id="model-usage-price-form">{{ dictionary.modelUsageAddPrice }}</h4>
          <form class="admin-inline-form" @submit.prevent="submitPrice">
            <label><span>{{ dictionary.modelUsageProvider }}</span><input v-model="priceForm.provider" class="text-input" type="text" maxlength="64" required /></label>
            <label><span>{{ dictionary.modelUsageModel }}</span><input v-model="priceForm.model" class="text-input" type="text" maxlength="128" required /></label>
            <label><span>{{ dictionary.modelUsageVersionKey }}</span><input v-model="priceForm.versionKey" class="text-input" type="text" maxlength="128" /></label>
            <label><span>{{ dictionary.modelUsageInputPrice }}</span><input v-model.number="priceForm.inputPricePerMillion" class="text-input" type="number" min="0" step="0.00000001" required /></label>
            <label><span>{{ dictionary.modelUsageOutputPrice }}</span><input v-model.number="priceForm.outputPricePerMillion" class="text-input" type="number" min="0" step="0.00000001" required /></label>
            <label><span>{{ dictionary.modelUsageCurrency }}</span><input v-model="priceForm.currency" class="text-input" type="text" minlength="3" maxlength="3" required /></label>
            <label><span>{{ dictionary.modelUsageSource }}</span><input v-model="priceForm.source" class="text-input" type="text" maxlength="64" required /></label>
            <label><span>{{ dictionary.modelUsageEffectiveFrom }}</span><input v-model="priceForm.effectiveFrom" class="text-input" type="datetime-local" /></label>
            <button class="primary-button" type="submit" :disabled="Boolean(usage.pendingAction.value)">{{ dictionary.modelUsageSavePrice }}</button>
          </form>
        </section>
      </div>

      <section class="model-usage-section" aria-labelledby="model-usage-prices">
        <h4 id="model-usage-prices">{{ dictionary.modelUsagePriceHistory }}</h4>
        <div v-if="usage.prices.value.length === 0" class="admin-empty-card"><p>{{ dictionary.modelUsageNoPrices }}</p></div>
        <div v-else class="admin-table-wrap">
          <table class="admin-table">
            <thead><tr><th>{{ dictionary.modelUsageEffectiveFrom }}</th><th>{{ dictionary.modelUsageModel }}</th><th>{{ dictionary.modelUsageInputPrice }}</th><th>{{ dictionary.modelUsageOutputPrice }}</th><th>{{ dictionary.modelUsageSource }}</th></tr></thead>
            <tbody>
              <tr v-for="price in usage.prices.value" :key="price.id">
                <td>{{ formatDate(price.effectiveFrom) }}</td><td>{{ price.provider }}/{{ price.model }}</td><td>{{ formatNumber(price.inputPricePerMillion, 8) }} {{ price.currency }}</td><td>{{ formatNumber(price.outputPricePerMillion, 8) }} {{ price.currency }}</td><td>{{ price.source }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </section>
</template>
