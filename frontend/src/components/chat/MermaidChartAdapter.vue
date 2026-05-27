<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { BarChart, PieChart } from "echarts/charts";
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from "echarts/components";
import { init, use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";

defineOptions({
  name: "MermaidChartAdapter"
});

use([
  BarChart,
  CanvasRenderer,
  GridComponent,
  LegendComponent,
  PieChart,
  TitleComponent,
  TooltipComponent
]);

const props = defineProps({
  rawJson: {
    type: String,
    required: true
  }
});

const chartEl = ref(null);

let chart = null;
let resizeObserver = null;

const chartModel = computed(() => parseChartJson(props.rawJson));
const parseFailed = computed(() => chartModel.value == null);
const isSupportedChart = computed(() => chartModel.value?.type === "bar" || chartModel.value?.type === "pie");
const isEmptyChart = computed(() => {
  const model = chartModel.value;

  if (!model) {
    return false;
  }

  if (model.type === "bar") {
    return !Array.isArray(model.categories) || !Array.isArray(model.values) || model.categories.length === 0 || model.values.length === 0;
  }

  if (model.type === "pie") {
    return !Array.isArray(model.slices) || model.slices.length === 0;
  }

  return false;
});
const canRenderChart = computed(() => Boolean(chartModel.value && isSupportedChart.value && !isEmptyChart.value));

function parseChartJson(raw) {
  try {
    return normalizeChartJson(JSON.parse(raw));
  } catch (error) {
    if (import.meta.env.DEV && import.meta.env.MODE !== "test") {
      console.warn("chart-json parse failed, rendering as code block", error);
    }
    return null;
  }
}

function normalizeChartJson(model) {
  if (!model || typeof model !== "object") {
    return model;
  }

  if (model.categories || model.slices) {
    return model;
  }

  if (!model.labels || !model.datasets) {
    return model;
  }

  if (model.type === "bar") {
    const datasets = Array.isArray(model.datasets) ? model.datasets : [];
    const labels = Array.isArray(model.labels) ? model.labels : [];

    if (labels.length === 1 && datasets.length > 1) {
      return {
        type: "bar",
        title: model.title ?? "",
        categories: datasets.map((ds) => ds.label ?? ""),
        values: datasets.map((ds) => (Array.isArray(ds.data) ? ds.data[0] : ds.data) ?? 0),
        metric: model.title ?? ""
      };
    }

    return {
      type: "bar",
      title: model.title ?? "",
      categories: labels,
      values: datasets.length ? (Array.isArray(datasets[0].data) ? datasets[0].data : []) : [],
      metric: datasets[0]?.label ?? ""
    };
  }

  if (model.type === "pie") {
    const labels = Array.isArray(model.labels) ? model.labels : [];
    const data = Array.isArray(model.datasets) && model.datasets.length
      ? (Array.isArray(model.datasets[0].data) ? model.datasets[0].data : [])
      : [];

    const slices = [];
    for (let i = 0; i < Math.min(labels.length, data.length); i += 1) {
      slices.push({ name: labels[i], value: data[i] });
    }

    return {
      type: "pie",
      title: model.title ?? "",
      slices
    };
  }

  return model;
}

function buildOption(model) {
  if (model.type === "pie") {
    return buildPieOption(model);
  }

  return buildBarOption(model);
}

function buildBarOption(model) {
  const categories = Array.isArray(model.categories) ? model.categories : [];
  const values = Array.isArray(model.values) ? model.values.map(toFiniteNumber) : [];
  const numericValues = values.filter((value) => Number.isFinite(value));
  const maxVal = numericValues.length ? Math.max(...numericValues) : 0;
  const minVal = numericValues.length ? Math.min(...numericValues) : 0;
  const range = maxVal - minVal || 1;
  const isPercentage = model.metricType === "percentage";

  const series = {
    barMaxWidth: 28,
    data: values.map((value, index) => ({
      itemStyle: { color: colorForValue(value, isPercentage, maxVal, minVal, range) },
      name: categories[index],
      value
    })),
    type: "bar"
  };

  if (Number.isFinite(model.averageLine)) {
    series.markLine = {
      data: [{ name: "Average", xAxis: model.averageLine }],
      label: { color: "#516173", formatter: "{b}: {c}" },
      lineStyle: { color: "#6f879d", type: "dashed" },
      symbol: "none"
    };
  }

  return {
    grid: { bottom: 18, containLabel: true, left: 12, right: 44, top: 42 },
    tooltip: {
      axisPointer: { type: "shadow" },
      trigger: "axis",
      formatter(params) {
        const item = params[0]?.data;
        return item ? `${item.name}<br/>${model.metric ?? "Value"}: ${formatValue(item.value, model.metricType)}` : "";
      }
    },
    xAxis: {
      axisLabel: { color: "#516173" },
      splitLine: { lineStyle: { color: "rgba(201, 214, 227, 0.7)" } },
      type: "value"
    },
    yAxis: {
      axisLabel: { color: "#102033", overflow: "truncate", width: 140 },
      data: categories,
      type: "category"
    },
    series: [series],
    title: {
      left: 0,
      text: model.title ?? "",
      textStyle: { color: "#102033", fontSize: 14, fontWeight: 700 }
    }
  };
}

function buildPieOption(model) {
  const slices = Array.isArray(model.slices) ? model.slices : [];

  return {
    legend: {
      bottom: 0,
      textStyle: { color: "#516173" },
      type: "scroll"
    },
    series: [
      {
        avoidLabelOverlap: true,
        center: ["50%", "55%"],
        data: slices.map((slice) => ({
          name: String(slice.name ?? ""),
          value: toFiniteNumber(slice.value)
        })),
        emphasis: {
          label: {
            fontSize: 14,
            fontWeight: "bold"
          }
        },
        label: {
          edgeDistance: 10,
          formatter: "{b}: {d}%",
          minMargin: 5
        },
        labelLine: {
          length: 16,
          length2: 24
        },
        radius: ["45%", "70%"],
        type: "pie"
      }
    ],
    title: {
      left: 0,
      text: model.title ?? "",
      textStyle: { color: "#102033", fontSize: 14, fontWeight: 700 }
    },
    tooltip: { trigger: "item" }
  };
}

function colorForValue(value, isPercentage, maxVal, minVal, range) {
  if (isPercentage) {
    if (value >= 80) return "#2f7da8";
    if (value >= 60) return "#6f879d";
    return "#a43f3f";
  }

  const ratio = (value - minVal) / range;
  if (ratio >= 0.7) return "#2f7da8";
  if (ratio >= 0.3) return "#6f879d";
  return "#a43f3f";
}

function toFiniteNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function formatValue(value, metricType) {
  if (metricType === "percentage") {
    return `${Number(value).toFixed(1)}%`;
  }
  return Number(value).toLocaleString(undefined, { maximumFractionDigits: 1 });
}

async function syncChart() {
  await nextTick();

  if (!canRenderChart.value || !chartEl.value) {
    disposeChart();
    return;
  }

  if (!chart) {
    chart = init(chartEl.value);
    setupResizeObserver();
  }

  chart.setOption(buildOption(chartModel.value), true);
}

function setupResizeObserver() {
  if (typeof ResizeObserver !== "undefined" && chartEl.value) {
    resizeObserver = new ResizeObserver(() => chart?.resize());
    resizeObserver.observe(chartEl.value);
  }

  if (typeof window !== "undefined") {
    window.addEventListener("resize", handleWindowResize);
  }
}

function handleWindowResize() {
  chart?.resize();
}

function disposeChart() {
  resizeObserver?.disconnect();
  resizeObserver = null;

  if (typeof window !== "undefined") {
    window.removeEventListener("resize", handleWindowResize);
  }

  chart?.dispose();
  chart = null;
}

onMounted(syncChart);
watch(chartModel, syncChart, { deep: true });

onBeforeUnmount(() => {
  disposeChart();
});
</script>

<template>
  <pre v-if="parseFailed || !isSupportedChart" class="hljs chart-json-fallback"><code class="hljs language-json">{{ rawJson }}</code></pre>

  <div v-else-if="isEmptyChart" class="analysis-empty-chart chart-json-empty" role="status">
    <div class="analysis-empty-chart-title">{{ chartModel.title }}</div>
    <div class="analysis-empty-chart-body">{{ chartModel.emptyMessage || "No data available" }}</div>
  </div>

  <section v-else class="chart-json-panel" :data-chart-json-type="chartModel.type">
    <div ref="chartEl" class="chart-json-canvas"></div>
  </section>
</template>
