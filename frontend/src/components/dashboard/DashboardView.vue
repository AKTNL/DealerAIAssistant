<script setup>
import { computed } from "vue";

const props = defineProps({
  dashboard: {
    type: Object,
    default: null
  },
  dictionary: {
    type: Object,
    required: true
  },
  error: {
    type: String,
    default: ""
  },
  isSending: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  },
  locale: {
    type: String,
    required: true
  }
});

const emit = defineEmits(["analyze", "reload"]);

const numberFormatter = computed(() => new Intl.NumberFormat(props.locale === "zh" ? "zh-CN" : "en-US"));
const overviewCards = computed(() => {
  const overview = props.dashboard?.overview ?? {};
  const prompts = analysisPromptsByKey.value;
  return [
    {
      key: "target",
      icon: "flag",
      label: props.dictionary.dashboardKpiTargetAchievement,
      value: formatPercent(overview.targetAchievementRate),
      detail: ratio(overview.comparableWon, overview.totalTarget),
      prompt: prompts.target?.prompt
    },
    {
      key: "opportunity",
      icon: "filter_alt",
      label: props.dictionary.dashboardKpiOpportunities,
      value: formatNumber(overview.totalOpportunities),
      detail: `${props.dictionary.dashboardWonLabel} ${formatNumber(overview.wonOpportunities)}`,
      prompt: prompts.funnel?.prompt
    },
    {
      key: "lead",
      icon: "travel_explore",
      label: props.dictionary.dashboardKpiLeads,
      value: formatNumber(overview.totalLeads),
      detail: `${props.dictionary.dashboardConversionLabel} ${formatPercent(overview.leadConversionRate)}`,
      prompt: prompts.lead?.prompt
    },
    {
      key: "task",
      icon: "pending_actions",
      label: props.dictionary.dashboardKpiTasks,
      value: formatNumber(overview.totalTasks),
      detail: `${props.dictionary.dashboardOverdueLabel} ${formatPercent(overview.taskOverdueRate)}`,
      prompt: prompts.task?.prompt
    },
    {
      key: "campaign",
      icon: "campaign",
      label: props.dictionary.dashboardKpiCampaigns,
      value: formatNumber(overview.totalCampaigns),
      detail: `${props.dictionary.dashboardAttainmentLabel} ${formatPercent(overview.campaignAttainmentRate)}`,
      prompt: prompts.campaign?.prompt
    }
  ];
});
const analysisPrompts = computed(() => Array.isArray(props.dictionary.dashboardAnalysisPrompts)
  ? props.dictionary.dashboardAnalysisPrompts
  : []);
const analysisPromptsByKey = computed(() => Object.fromEntries(
  analysisPrompts.value.map(prompt => [prompt.key, prompt])
));
const dataStatus = computed(() => props.dashboard?.dataStatus ?? {});
const lowDealers = computed(() => asArray(props.dashboard?.targetAchievement?.lowDealers));
const regionRows = computed(() => asArray(props.dashboard?.targetAchievement?.regions));
const stageRows = computed(() => asArray(props.dashboard?.opportunityFunnel?.stages));
const sourceRows = computed(() => asArray(props.dashboard?.leadSources?.sources));
const backlogRows = computed(() => asArray(props.dashboard?.followUpTasks?.backlogDealers));
const campaignRows = computed(() => asArray(props.dashboard?.campaignEffect?.lowPerformingCampaigns));

function handleAnalyze(prompt) {
  if (!prompt || props.isSending) {
    return;
  }

  emit("analyze", prompt);
}

function formatNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) ? numberFormatter.value.format(number) : "0";
}

function formatPercent(value) {
  const number = Number(value);
  return `${Number.isFinite(number) ? number.toFixed(1) : "0.0"}%`;
}

function ratio(numerator, denominator) {
  return `${formatNumber(numerator)} ${props.dictionary.dashboardRatioSeparator} ${formatNumber(denominator)}`;
}

function barWidth(value) {
  const number = Number(value);
  return `${Math.min(Number.isFinite(number) ? number : 0, 100)}%`;
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}
</script>

<template>
  <section class="dashboard-screen" :aria-busy="loading">
    <div class="dashboard-hero">
      <div>
        <p class="eyebrow">{{ dictionary.dashboardEyebrow }}</p>
        <h1>{{ dictionary.dashboardTitle }}</h1>
        <p>{{ dictionary.dashboardSubtitle }}</p>
      </div>

      <button class="ghost-button dashboard-refresh-button" type="button" :disabled="loading" @click="$emit('reload')">
        <span class="material-icons" aria-hidden="true">refresh</span>
        <span>{{ dictionary.dashboardRefresh }}</span>
      </button>
    </div>

    <div v-if="loading" class="dashboard-grid dashboard-loading-grid">
      <div v-for="item in 6" :key="item" class="dashboard-skeleton">
        <span class="skeleton-line skeleton-line-md"></span>
        <span class="skeleton-line"></span>
        <span class="skeleton-line skeleton-line-sm"></span>
      </div>
    </div>

    <div v-else-if="error" class="dashboard-state dashboard-error-state" role="alert">
      <span class="material-icons" aria-hidden="true">error_outline</span>
      <div>
        <h2>{{ dictionary.dashboardErrorTitle }}</h2>
        <p>{{ error }}</p>
      </div>
      <button class="primary-button" type="button" @click="$emit('reload')">
        {{ dictionary.dashboardRetry }}
      </button>
    </div>

    <div v-else-if="!dashboard" class="dashboard-state">
      <span class="material-icons" aria-hidden="true">inbox</span>
      <div>
        <h2>{{ dictionary.dashboardEmptyTitle }}</h2>
        <p>{{ dictionary.dashboardEmptyBody }}</p>
      </div>
    </div>

    <div v-else class="dashboard-content">
      <section class="dashboard-status-band">
        <div class="dashboard-status-main">
          <span class="material-icons" aria-hidden="true">database</span>
          <div>
            <h2>{{ dictionary.dashboardDataStatusTitle }}</h2>
            <p>
              {{ dictionary.dashboardDataSource }}:
              <strong>{{ dataStatus.source || dictionary.dashboardUnknown }}</strong>
            </p>
          </div>
        </div>

        <dl class="dashboard-status-facts">
          <div>
            <dt>{{ dictionary.dashboardBatch }}</dt>
            <dd>{{ dataStatus.batch?.id || dictionary.dashboardUnknown }}</dd>
          </div>
          <div>
            <dt>{{ dictionary.dashboardImportedRows }}</dt>
            <dd>{{ formatNumber(dataStatus.importedRows) }}</dd>
          </div>
          <div>
            <dt>{{ dictionary.dashboardSkippedRows }}</dt>
            <dd>{{ formatNumber(dataStatus.skippedRows) }}</dd>
          </div>
          <div>
            <dt>{{ dictionary.dashboardQualityIssues }}</dt>
            <dd>{{ formatNumber(dataStatus.issueCount) }}</dd>
          </div>
        </dl>
      </section>

      <div v-if="dataStatus.simulatedData || dataStatus.lowConfidence" class="dashboard-alert-row">
        <p v-if="dataStatus.simulatedData" class="dashboard-alert dashboard-alert-info">
          <span class="material-icons" aria-hidden="true">science</span>
          {{ dictionary.dashboardSimulatedNotice }}
        </p>
        <p v-if="dataStatus.lowConfidence" class="dashboard-alert dashboard-alert-warning">
          <span class="material-icons" aria-hidden="true">warning_amber</span>
          {{ dictionary.dashboardLowConfidenceNotice }}
        </p>
      </div>

      <section class="dashboard-grid dashboard-overview-grid">
        <article v-for="card in overviewCards" :key="card.key" class="dashboard-kpi-card">
          <div class="dashboard-kpi-icon">
            <span class="material-icons" aria-hidden="true">{{ card.icon }}</span>
          </div>
          <div>
            <h2>{{ card.label }}</h2>
            <strong>{{ card.value }}</strong>
            <p>{{ card.detail }}</p>
          </div>
          <button
            class="dashboard-card-action"
            type="button"
            :disabled="isSending || !card.prompt"
            :title="dictionary.dashboardAnalyze"
            @click="handleAnalyze(card.prompt)"
          >
            <span class="material-icons" aria-hidden="true">forum</span>
          </button>
        </article>
      </section>

      <section class="dashboard-grid dashboard-panel-grid">
        <article class="dashboard-panel">
          <header>
            <h2>{{ dictionary.dashboardPanelTarget }}</h2>
            <button type="button" :disabled="isSending" @click="handleAnalyze(analysisPromptsByKey.target?.prompt)">
              {{ dictionary.dashboardAnalyze }}
            </button>
          </header>
          <ol v-if="lowDealers.length" class="dashboard-ranked-list">
            <li v-for="dealer in lowDealers" :key="dealer.dealerCode">
              <span>{{ dealer.dealerName }}</span>
              <strong>{{ formatPercent(dealer.achievementRate) }}</strong>
              <small>{{ dealer.region }} {{ dictionary.dashboardMetricSeparator }} {{ ratio(dealer.wonCount, dealer.targetCount) }}</small>
            </li>
          </ol>
          <p v-else class="dashboard-empty-line">{{ dictionary.dashboardNoData }}</p>
        </article>

        <article class="dashboard-panel">
          <header>
            <h2>{{ dictionary.dashboardPanelRegion }}</h2>
          </header>
          <ol v-if="regionRows.length" class="dashboard-ranked-list">
            <li v-for="region in regionRows" :key="region.region">
              <span>{{ region.region }}</span>
              <strong>{{ formatPercent(region.achievementRate) }}</strong>
              <small>{{ ratio(region.wonCount, region.targetCount) }}</small>
            </li>
          </ol>
          <p v-else class="dashboard-empty-line">{{ dictionary.dashboardNoData }}</p>
        </article>

        <article class="dashboard-panel">
          <header>
            <h2>{{ dictionary.dashboardPanelFunnel }}</h2>
            <button type="button" :disabled="isSending" @click="handleAnalyze(analysisPromptsByKey.funnel?.prompt)">
              {{ dictionary.dashboardAnalyze }}
            </button>
          </header>
          <ul v-if="stageRows.length" class="dashboard-bar-list">
            <li v-for="stage in stageRows" :key="stage.label">
              <span>{{ stage.label }}</span>
              <div class="dashboard-bar-track">
                <span :style="{ width: barWidth(stage.shareRate) }"></span>
              </div>
              <strong>{{ formatNumber(stage.count) }}</strong>
            </li>
          </ul>
          <p v-else class="dashboard-empty-line">{{ dictionary.dashboardNoData }}</p>
        </article>

        <article class="dashboard-panel">
          <header>
            <h2>{{ dictionary.dashboardPanelLead }}</h2>
            <button type="button" :disabled="isSending" @click="handleAnalyze(analysisPromptsByKey.lead?.prompt)">
              {{ dictionary.dashboardAnalyze }}
            </button>
          </header>
          <ol v-if="sourceRows.length" class="dashboard-ranked-list">
            <li v-for="source in sourceRows" :key="source.source">
              <span>{{ source.source }}</span>
              <strong>{{ formatPercent(source.conversionRate) }}</strong>
              <small>{{ ratio(source.convertedCount, source.totalCount) }}</small>
            </li>
          </ol>
          <p v-else class="dashboard-empty-line">{{ dictionary.dashboardNoData }}</p>
        </article>

        <article class="dashboard-panel">
          <header>
            <h2>{{ dictionary.dashboardPanelTask }}</h2>
            <button type="button" :disabled="isSending" @click="handleAnalyze(analysisPromptsByKey.task?.prompt)">
              {{ dictionary.dashboardAnalyze }}
            </button>
          </header>
          <ol v-if="backlogRows.length" class="dashboard-ranked-list">
            <li v-for="dealer in backlogRows" :key="dealer.dealerCode">
              <span>{{ dealer.dealerName }}</span>
              <strong>{{ formatNumber(dealer.totalBacklog) }}</strong>
              <small>{{ dictionary.dashboardOpenLabel }} {{ formatNumber(dealer.openCount) }} {{ dictionary.dashboardMetricSeparator }} {{ dictionary.dashboardOverdueLabel }} {{ formatNumber(dealer.overdueCount) }}</small>
            </li>
          </ol>
          <p v-else class="dashboard-empty-line">{{ dictionary.dashboardNoData }}</p>
        </article>

        <article class="dashboard-panel">
          <header>
            <h2>{{ dictionary.dashboardPanelCampaign }}</h2>
            <button type="button" :disabled="isSending" @click="handleAnalyze(analysisPromptsByKey.campaign?.prompt)">
              {{ dictionary.dashboardAnalyze }}
            </button>
          </header>
          <ol v-if="campaignRows.length" class="dashboard-ranked-list">
            <li v-for="campaign in campaignRows" :key="campaign.campaignId">
              <span>{{ campaign.campaignName }}</span>
              <strong>{{ formatPercent(campaign.attainmentRate) }}</strong>
              <small>{{ campaign.dealerName }} {{ dictionary.dashboardMetricSeparator }} {{ ratio(campaign.actualOpportunities, campaign.targetOpportunities) }}</small>
            </li>
          </ol>
          <p v-else class="dashboard-empty-line">{{ dictionary.dashboardNoData }}</p>
        </article>
      </section>
    </div>
  </section>
</template>
