<script setup>
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import mermaid from "mermaid";
import { createAnalysisEnhancementsFromHtml } from "../../utils/analysisCharts";
import { buildThinkingSummary } from "../../utils/thinkingSummary";
import AnalysisChart from "./AnalysisChart.vue";
import FollowUpButtons from "./FollowUpButtons.vue";

const MermaidChartAdapter = defineAsyncComponent(() => import("./MermaidChartAdapter.vue"));

const props = defineProps({
  dictionary: { type: Object, required: true },
  locale: { type: String, default: "zh" },
  message: { type: Object, required: true },
  streamPhase: { type: String, default: "idle" }
});

defineEmits(["submit-follow-up"]);

const timelineExpanded = ref(false);
const thinkingCollapsed = ref(false);

const messageCard = ref(null);
const chartJsonBlocks = ref([]);
const mermaidViewState = ref(new Map());
const mermaidRetryTimers = new Map();

const MERMAID_RETRY_DELAY_MS = 350;
const EMPTY_ANALYSIS_ENHANCEMENTS = { charts: [], metricCards: [] };

const thinkingSummary = computed(() => buildThinkingSummary(props.message?.steps, props.locale));
const thinkingSummaryTitle = computed(() => props.locale === "zh" ? "证据链" : "Evidence trail");
const progressTitle = computed(() => props.locale === "zh" ? "分析进度" : "Processing progress");
const progressSteps = computed(() => {
  const steps = Array.isArray(props.message?.steps) ? props.message.steps : [];
  return steps.filter(step => step?.type === "progress" || step?.progressPlaceholder === true);
});
const timelineSteps = computed(() => {
  const steps = Array.isArray(props.message?.steps) ? props.message.steps : [];
  const auditSteps = steps.filter(step => step?.type !== "progress" && step?.progressPlaceholder !== true);
  const thoughtSteps = auditSteps.filter(step => step?.type === "model_thought");

  return thoughtSteps.length ? thoughtSteps : auditSteps;
});
const hasThinkingDetails = computed(() =>
  thinkingSummary.value.length > 0 || progressSteps.value.length > 0 || timelineSteps.value.length > 0
);
const showThinkingDetails = computed(() => !thinkingCollapsed.value);

const analysisEnhancements = computed(() => {
  if (!props.message?.html || props.message?.streaming || props.message?.rendered === false) {
    return EMPTY_ANALYSIS_ENHANCEMENTS;
  }

  try {
    return createAnalysisEnhancementsFromHtml(props.message.html);
  } catch (error) {
    if (import.meta.env.DEV && import.meta.env.MODE !== "test") {
      console.warn("Analysis enhancement parsing failed", {
        message: error?.message
      });
    }

    return EMPTY_ANALYSIS_ENHANCEMENTS;
  }
});


watch(() => props.message?.streaming, (streaming) => {
  const expanded = Boolean(streaming);
  timelineExpanded.value = expanded;
  thinkingCollapsed.value = !expanded;
}, { immediate: true });

function toggleThinkingDetails() {
  timelineExpanded.value = !timelineExpanded.value;
  thinkingCollapsed.value = !timelineExpanded.value;
}

function hasActiveModelThought() {
  return props.message?.steps?.some(s => s.type === 'model_thought' && s.status === 'loading');
}

mermaid.initialize({
  startOnLoad: false,
  theme: "base",
  themeVariables: {
    primaryColor: "#e9f2fb",
    primaryTextColor: "#102033",
    primaryBorderColor: "#c9d6e3",
    lineColor: "#aebfce",
    secondaryColor: "#ffffff",
    tertiaryColor: "#a43f3f",
    fontSize: "13px"
  }
});

let observer = null;
let mermaidRenderSequence = 0;
let mermaidSyncQueued = false;
let mermaidForceSyncQueued = false;
let mermaidSyncInFlight = false;
let mermaidSyncPromise = Promise.resolve();

function getMermaidLabels() {
  return {
    chart: props.dictionary?.mermaidViewChart ?? (props.locale === "zh" ? "图表" : "Chart"),
    code: props.dictionary?.mermaidViewCode ?? (props.locale === "zh" ? "代码" : "Code"),
    reload: props.dictionary?.mermaidReload ?? (props.locale === "zh" ? "重新加载" : "Reload"),
    error:
      props.dictionary?.mermaidRenderError ??
      (props.locale === "zh"
        ? "图表渲染失败，请检查语法或切换到代码查看源码"
        : "Unable to render this chart. Check the syntax or switch to code view.")
  };
}

function getMermaidChartPanel(block) {
  return block.querySelector('[data-mermaid-role="chart"]');
}

function getMermaidSource(block) {
  return block.querySelector('[data-mermaid-role="source"]')?.textContent?.replace(/\r\n?/g, "\n") ?? "";
}

function findMermaidBlock(blockId) {
  if (!messageCard.value || !blockId) {
    return null;
  }

  return messageCard.value.querySelector(`.mermaid-block[data-mermaid-block-id="${blockId}"]`);
}

function clearMermaidRetry(blockId) {
  if (!mermaidRetryTimers.has(blockId)) {
    return;
  }

  if (typeof window !== "undefined") {
    window.clearTimeout(mermaidRetryTimers.get(blockId));
  }

  mermaidRetryTimers.delete(blockId);
}

function clearAllMermaidRetries() {
  for (const blockId of mermaidRetryTimers.keys()) {
    clearMermaidRetry(blockId);
  }
}

function scheduleMermaidRetry(blockId) {
  if (!blockId || mermaidRetryTimers.has(blockId) || typeof window === "undefined") {
    return;
  }

  const timerId = window.setTimeout(() => {
    mermaidRetryTimers.delete(blockId);
    const block = findMermaidBlock(blockId);

    if (block) {
      void renderMermaidBlock(block, { force: true });
    }
  }, MERMAID_RETRY_DELAY_MS);

  mermaidRetryTimers.set(blockId, timerId);
}

function createMermaidToggleButton(view) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "mermaid-toggle-button";
  button.dataset.mermaidView = view;
  button.setAttribute("aria-pressed", "false");
  return button;
}

function ensureMermaidToolbar(block) {
  let toolbar = block.querySelector(".mermaid-toolbar");

  if (!toolbar) {
    toolbar = document.createElement("div");
    toolbar.className = "mermaid-toolbar";
    toolbar.dataset.mermaidToolbar = "true";

    const toggleGroup = document.createElement("div");
    toggleGroup.className = "mermaid-toggle-group";
    toggleGroup.setAttribute("role", "group");
    toggleGroup.append(createMermaidToggleButton("chart"), createMermaidToggleButton("code"));

    const actions = document.createElement("div");
    actions.className = "mermaid-toolbar-actions";
    actions.setAttribute("aria-hidden", "true");

    toolbar.append(toggleGroup, actions);
    block.prepend(toolbar);
  }

  const labels = getMermaidLabels();
  const chartButton = toolbar.querySelector('[data-mermaid-view="chart"]');
  const codeButton = toolbar.querySelector('[data-mermaid-view="code"]');
  const toggleGroup = toolbar.querySelector(".mermaid-toggle-group");

  if (chartButton && chartButton.textContent !== labels.chart) {
    chartButton.textContent = labels.chart;
  }

  if (codeButton && codeButton.textContent !== labels.code) {
    codeButton.textContent = labels.code;
  }

  if (toggleGroup && toggleGroup.getAttribute("aria-label") !== `${labels.chart} / ${labels.code}`) {
    toggleGroup.setAttribute("aria-label", `${labels.chart} / ${labels.code}`);
  }

  return toolbar;
}

function syncToggleButtons(block, view) {
  for (const button of block.querySelectorAll(".mermaid-toggle-button")) {
    const active = button.dataset.mermaidView === view;
    button.classList.toggle("is-active", active);
    button.setAttribute("aria-pressed", String(active));
  }
}

function applyMermaidBlockState(block) {
  const blockId = block.dataset.mermaidBlockId;
  const rememberedView = blockId ? mermaidViewState.value.get(blockId) : null;
  const view = rememberedView ?? block.dataset.view ?? "chart";

  if (blockId && rememberedView == null) {
    mermaidViewState.value.set(blockId, view);
  }

  block.dataset.view = view;
  syncToggleButtons(block, view);
}

function renderMermaidSkeleton(chartPanel) {
  const skeleton = document.createElement("div");
  skeleton.className = "mermaid-skeleton";
  skeleton.setAttribute("aria-hidden", "true");
  skeleton.innerHTML = `
    <div class="skeleton-line mermaid-skeleton-title"></div>
    <div class="mermaid-skeleton-plot">
      <span class="mermaid-skeleton-bar"></span>
      <span class="mermaid-skeleton-bar mermaid-skeleton-bar-md"></span>
      <span class="mermaid-skeleton-bar mermaid-skeleton-bar-lg"></span>
    </div>
    <div class="skeleton-line skeleton-line-sm"></div>
  `;
  chartPanel.replaceChildren(skeleton);
}

function renderMermaidErrorState(chartPanel) {
  const labels = getMermaidLabels();
  const errorState = document.createElement("div");
  errorState.className = "mermaid-error-state";
  errorState.setAttribute("role", "status");

  const message = document.createElement("span");
  message.className = "mermaid-chart-error";
  message.textContent = labels.error;

  const retryButton = document.createElement("button");
  retryButton.className = "mermaid-retry-button";
  retryButton.type = "button";
  retryButton.textContent = labels.reload;

  errorState.append(message, retryButton);
  chartPanel.replaceChildren(errorState);
}

function getChartEmptyBody(reason) {
  const normalized = String(reason ?? "").toUpperCase();

  if (normalized === "NO_DATA") {
    return props.dictionary?.chartEmptyNoDataBody;
  }
  if (normalized === "DENOMINATOR_ZERO") {
    return props.dictionary?.chartEmptyDenominatorZeroBody;
  }
  if (normalized === "ALL_ZERO_SIGNAL") {
    return props.dictionary?.chartEmptyAllZeroBody;
  }
  if (normalized === "INSUFFICIENT_SAMPLE") {
    return props.dictionary?.chartEmptyInsufficientSampleBody;
  }

  return props.dictionary?.chartEmptyBody;
}

function syncChartEmptyStates(root) {
  for (const state of root.querySelectorAll('[data-chart-empty="true"]')) {
    if (!state.querySelector(".analysis-empty-chart-title")) {
      const title = document.createElement("div");
      title.className = "analysis-empty-chart-title";
      title.textContent = props.dictionary?.chartEmptyTitle ?? (props.locale === "zh" ? "暂无可视化信号" : "No visual signal");
      state.prepend(title);
    }

    if (!state.querySelector(".analysis-empty-chart-body")) {
      const body = document.createElement("div");
      body.className = "analysis-empty-chart-body";
      body.textContent = getChartEmptyBody(state.dataset.chartEmptyReason)
        ?? (props.locale === "zh"
          ? "当前数据不足以支持可靠的排名图表，因此已隐藏可视化。"
          : "The chart is hidden because the available data is not reliable enough for a ranked visualization.");
      state.append(body);
    }
  }
}

function syncChartJsonBlocks(root) {
  if (!root) {
    chartJsonBlocks.value = [];
    return;
  }

  chartJsonBlocks.value = Array.from(root.querySelectorAll(".chart-json-block"))
    .map((block, index) => {
      const rawJson = block.dataset.chartJson || "";

      if (!rawJson.trim()) {
        return null;
      }

      if (!block.dataset.chartJsonBlockId) {
        block.dataset.chartJsonBlockId = `chart-json-${index}-${rawJson.length}`;
      }

      return {
        id: block.dataset.chartJsonBlockId,
        rawJson
      };
    })
    .filter(Boolean);
}

async function renderMermaidBlock(block, { force = false } = {}) {
  const blockId = block.dataset.mermaidBlockId;
  const chartPanel = getMermaidChartPanel(block);
  const source = getMermaidSource(block);

  if (!blockId || !chartPanel || !source.trim()) {
    return;
  }

  if (chartPanel.dataset.mermaidRendering === "true") {
    return;
  }

  if (!force && chartPanel.dataset.mermaidRenderedFor === blockId && block.dataset.renderState === "ready") {
    return;
  }

  block.dataset.renderState = "loading";
  chartPanel.dataset.mermaidRendering = "true";
  chartPanel.classList.add("mermaid-chart-loading");
  chartPanel.classList.remove("mermaid-chart-fallback");
  renderMermaidSkeleton(chartPanel);

  try {
    const renderId = `mermaid-${blockId}-${(mermaidRenderSequence += 1).toString(36)}`;
    const { svg } = await mermaid.render(renderId, source);

    if (!chartPanel.isConnected) {
      return;
    }

    chartPanel.innerHTML = svg;
    chartPanel.classList.add("mermaid-rendered");
    chartPanel.classList.remove("mermaid-chart-fallback");
    chartPanel.dataset.mermaidRenderedFor = blockId;
    block.dataset.renderState = "ready";
    clearMermaidRetry(blockId);
  } catch (error) {
    if (import.meta.env.DEV && import.meta.env.MODE !== "test") {
      console.warn("Mermaid chart render failed", {
        blockId,
        message: error?.message
      });
    }

    if (!chartPanel.isConnected) {
      return;
    }

    chartPanel.replaceChildren();
    chartPanel.classList.remove("mermaid-rendered");
    chartPanel.classList.add("mermaid-chart-fallback");
    chartPanel.dataset.mermaidRenderedFor = "";
    block.dataset.renderState = "retry-pending";
    renderMermaidErrorState(chartPanel);

    if (!props.message?.rendered) {
      scheduleMermaidRetry(blockId);
    }
  } finally {
    if (chartPanel.isConnected) {
      chartPanel.dataset.mermaidRendering = "false";
      chartPanel.classList.remove("mermaid-chart-loading");
    }
  }
}

async function syncMermaidBlocks(root, { force = false } = {}) {
  const blocks = root.querySelectorAll(".mermaid-block");

  for (const block of blocks) {
    ensureMermaidToolbar(block);
    applyMermaidBlockState(block);

    if (!force && block.dataset.renderState === "retry-pending") {
      continue;
    }

    await renderMermaidBlock(block, { force });
  }
}

function queueMermaidSync({ force = false } = {}) {
  mermaidSyncQueued = true;
  mermaidForceSyncQueued = mermaidForceSyncQueued || force;

  if (mermaidSyncInFlight) {
    return;
  }

  mermaidSyncInFlight = true;
  mermaidSyncPromise = nextTick()
    .then(async () => {
      while (mermaidSyncQueued) {
        const shouldForce = mermaidForceSyncQueued;
        mermaidSyncQueued = false;
        mermaidForceSyncQueued = false;

        if (messageCard.value) {
          syncChartJsonBlocks(messageCard.value);
          syncChartEmptyStates(messageCard.value);
          await syncMermaidBlocks(messageCard.value, { force: shouldForce });
        }
      }
    })
    .finally(() => {
      mermaidSyncInFlight = false;
      if (mermaidSyncQueued) {
        queueMermaidSync();
      }
    });
}

function handleMermaidToggleClick(event) {
  const retryButton = event.target.closest?.(".mermaid-retry-button");

  if (retryButton && messageCard.value?.contains(retryButton)) {
    const block = retryButton.closest(".mermaid-block");

    if (block) {
      clearMermaidRetry(block.dataset.mermaidBlockId);
      void renderMermaidBlock(block, { force: true });
    }

    return;
  }

  const button = event.target.closest?.(".mermaid-toggle-button");

  if (!button || !messageCard.value?.contains(button)) {
    return;
  }

  const block = button.closest(".mermaid-block");
  const blockId = block?.dataset?.mermaidBlockId;
  const view = button.dataset.mermaidView;

  if (!block || !blockId || (view !== "chart" && view !== "code")) {
    return;
  }

  mermaidViewState.value.set(blockId, view);
  applyMermaidBlockState(block);
}

function setupObserver() {
  if (observer) {
    observer.disconnect();
  }

  if (!messageCard.value) {
    return;
  }

  observer = new MutationObserver(() => {
    queueMermaidSync();
  });

  observer.observe(messageCard.value, {
    characterData: true,
    childList: true,
    subtree: true
  });
}

onMounted(() => {
  messageCard.value?.addEventListener("click", handleMermaidToggleClick);
  setupObserver();
  queueMermaidSync({ force: true });
});

watch(() => props.message?.html, () => {
  queueMermaidSync({ force: true });
});

watch(() => props.message?.rendered, (done) => {
  if (done) {
    queueMermaidSync({ force: true });
  }
});

watch(
  () => [
    props.dictionary?.mermaidRenderError,
    props.dictionary?.mermaidViewChart,
    props.dictionary?.mermaidViewCode,
    props.dictionary?.chartEmptyTitle,
    props.dictionary?.chartEmptyBody,
    props.dictionary?.chartEmptyNoDataBody,
    props.dictionary?.chartEmptyDenominatorZeroBody,
    props.dictionary?.chartEmptyAllZeroBody,
    props.dictionary?.chartEmptyInsufficientSampleBody,
    props.locale
  ],
  () => {
    queueMermaidSync();
  }
);

onBeforeUnmount(() => {
  messageCard.value?.removeEventListener("click", handleMermaidToggleClick);

  if (observer) {
    observer.disconnect();
    observer = null;
  }

  mermaidViewState.value.clear();
  chartJsonBlocks.value = [];
  clearAllMermaidRetries();
});
</script>

<template>
  <article ref="messageCard" class="message-row message-assistant">
    <div class="avatar avatar-assistant">AI</div>

    <div class="message-card message-card-assistant message-bubble-assistant">
      <div class="assistant-header">
        <div class="message-meta">
          <span>{{ dictionary.assistantLabel }}</span>
          <span v-if="message.status && !(message.streaming && !message.html)">{{ message.status }}</span>
        </div>

        <button
          v-if="hasThinkingDetails"
          class="timeline-toggle timeline-toggle-inline"
          type="button"
          :aria-expanded="String(Boolean(timelineExpanded))"
          @click="toggleThinkingDetails"
        >
          {{ timelineExpanded ? dictionary.hideThinking : dictionary.showThinking }}
        </button>
      </div>

      <section
        v-if="thinkingSummary.length && showThinkingDetails"
        class="thinking-summary"
        :aria-label="thinkingSummaryTitle"
      >
        <div class="thinking-summary-header">
          <span>{{ thinkingSummaryTitle }}</span>
          <span class="thinking-summary-count">{{ thinkingSummary.length }}</span>
        </div>

        <div class="thinking-summary-grid">
          <article
            v-for="card in thinkingSummary"
            :key="card.key"
            class="thinking-summary-card"
          >
            <span class="thinking-summary-label">{{ card.label }}</span>
            <strong class="thinking-summary-value">{{ card.value }}</strong>
            <span v-if="card.detail" class="thinking-summary-detail">{{ card.detail }}</span>
          </article>
        </div>
      </section>

      <section v-if="progressSteps.length && showThinkingDetails" class="processing-progress" :aria-label="progressTitle">
        <div class="processing-progress-header">
          <span>{{ progressTitle }}</span>
          <span class="processing-progress-count">{{ progressSteps.length }}</span>
        </div>

        <ol class="processing-progress-list">
          <li
            v-for="step in progressSteps"
            :key="`${step.seq}-${step.ts}-${step.label}`"
            class="processing-progress-item"
            :class="`processing-progress-item--${step.status || 'loading'}`"
          >
            <span class="processing-progress-dot" aria-hidden="true"></span>
            <span class="processing-progress-label">{{ step.label }}</span>
          </li>
        </ol>
      </section>

      <!-- Timeline panel: model thought first, structured audit fallback -->
      <div v-if="timelineSteps.length && timelineExpanded" class="timeline-panel">
        <div
          v-for="step in timelineSteps"
          :key="`${step.type ?? 'loading'}-${step.seq}-${step.ts}`"
          class="timeline-step"
          :class="[
            `timeline-step--${step.status || 'loading'}`,
            step.type ? `timeline-step--${step.type}` : ''
          ]"
        >
          <!-- Status icon -->
          <span class="timeline-step-icon" aria-hidden="true">
            <span v-if="step.status === 'loading'" class="timeline-step-spinner"></span>
            <span v-else-if="step.status === 'failed'" class="timeline-step-cross">&#10007;</span>
            <span v-else-if="step.status === 'skipped'" class="timeline-step-dash">&#8212;</span>
            <span v-else class="timeline-step-check">&#10003;</span>
          </span>

          <!-- Step body -->
          <div class="timeline-step-body">
            <div class="timeline-step-header">
              <span class="timeline-step-label">{{ step.label }}</span>
              <span v-if="step.status === 'failed'" class="timeline-step-badge badge-error">
                {{ locale === 'zh' ? '失败' : 'Failed' }}
              </span>
              <span v-else-if="step.status === 'loading'" class="timeline-step-badge badge-loading">
                {{ locale === 'zh' ? '进行中' : 'Running' }}
              </span>
            </div>

            <!-- model_thought: expand during streaming, collapse after done -->
            <details
              v-if="step.type === 'model_thought'"
              class="timeline-step-detail"
              :open="message.streaming || timelineExpanded"
            >
              <summary>{{ dictionary.showThinking || (locale === 'zh' ? '模型思考过程' : 'Model reasoning') }}</summary>
              <div class="markdown-body">{{ step.detail }}</div>
            </details>

            <!-- insight: highlighted blockquote -->
            <blockquote v-else-if="step.type === 'insight' && step.detail" class="timeline-step-insight">
              {{ step.detail }}
            </blockquote>

            <!-- Other types: detail text + collapsible meta audit -->
            <template v-else>
              <div v-if="step.detail" class="timeline-step-detail-text">{{ step.detail }}</div>
              <details v-if="step.meta && Object.keys(step.meta).length" class="timeline-step-meta">
                <summary>{{ locale === 'zh' ? '查看审计数据' : 'Audit details' }}</summary>
                <pre><code>{{ JSON.stringify(step.meta, null, 2) }}</code></pre>
              </details>
            </template>
          </div>
        </div>
      </div>

      <!-- Thinking indicator: shown when streaming but no steps with model_thought yet -->
      <div v-if="message.streaming && streamPhase === 'thinking' && !hasActiveModelThought()" class="thinking-indicator">
        <span class="thinking-dot"></span>
        {{ dictionary.bubbleThinking }}
      </div>

      <div v-if="message.html" class="analysis-response-body">
        <div
          v-if="analysisEnhancements.metricCards.length"
          aria-label="Analysis summary"
          class="analysis-metric-grid"
        >
          <article
            v-for="card in analysisEnhancements.metricCards"
            :key="`${card.key}-${card.value}-${card.body}`"
            class="analysis-metric-card"
          >
            <span class="analysis-metric-label">{{ card.label }}</span>
            <strong class="analysis-metric-value">{{ card.value }}</strong>
            <span v-if="card.body" class="analysis-metric-body">{{ card.body }}</span>
          </article>
        </div>

        <div class="markdown-body" v-html="message.html"></div>

        <MermaidChartAdapter
          v-for="chart in chartJsonBlocks"
          :key="chart.id"
          :raw-json="chart.rawJson"
        />

        <div v-if="analysisEnhancements.charts.length && !chartJsonBlocks.length" class="analysis-chart-stack">
          <AnalysisChart
            v-for="chart in analysisEnhancements.charts"
            :key="chart.id"
            :model="chart"
          />
        </div>
      </div>
      <span v-if="message.streaming && message.html" class="message-cursor" aria-hidden="true"></span>

      <FollowUpButtons
        :dictionary="dictionary"
        :follow-ups="message.followUps"
        @select="$emit('submit-follow-up', $event)"
      />
    </div>
  </article>
</template>
