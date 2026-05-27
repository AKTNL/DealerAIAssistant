<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { BarChart, HeatmapChart, RadarChart } from "echarts/charts";
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  VisualMapComponent
} from "echarts/components";
import { init, use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { getVisibleChartRows } from "../../utils/analysisCharts";

defineOptions({
  name: "AnalysisChart"
});

use([
  BarChart,
  CanvasRenderer,
  GridComponent,
  HeatmapChart,
  LegendComponent,
  RadarChart,
  TitleComponent,
  TooltipComponent,
  VisualMapComponent
]);

const props = defineProps({
  model: {
    type: Object,
    required: true
  }
});

const chartEl = ref(null);
const filterText = ref("");
const sortOrder = ref("default");
const topN = ref("all");

let chart = null;
let resizeObserver = null;

const supportsBarControls = computed(() => props.model.type === "bar");
const visibleRows = computed(() =>
  getVisibleChartRows(props.model.rows ?? [], {
    filterText: filterText.value,
    sortOrder: sortOrder.value,
    topN: topN.value
  })
);

function buildOption() {
  if (props.model.type === "heatmap") {
    return buildHeatmapOption(props.model);
  }

  if (props.model.type === "radar") {
    return buildRadarOption(props.model);
  }

  return buildBarOption(props.model, visibleRows.value);
}

function buildBarOption(model, rows) {
  return {
    grid: { bottom: 18, containLabel: true, left: 12, right: 38, top: 40 },
    tooltip: {
      axisPointer: { type: "shadow" },
      trigger: "axis",
      formatter(params) {
        const item = params[0]?.data;
        return item ? `${item.name}<br/>${model.metric}: ${item.display}` : "";
      }
    },
    xAxis: {
      axisLabel: { color: "#516173" },
      splitLine: { lineStyle: { color: "rgba(201, 214, 227, 0.7)" } },
      type: "value"
    },
    yAxis: {
      axisLabel: {
        color: "#102033",
        overflow: "truncate",
        width: 140
      },
      data: rows.map((row) => row.label),
      type: "category"
    },
    series: [
      {
        barMaxWidth: 28,
        data: rows.map((row) => ({
          display: row.display,
          itemStyle: { color: colorForBar(row, model) },
          label: {
            color: colorForBar(row, model),
            formatter: row.label === model.maxLabel || row.label === model.minLabel ? "{c}" : "",
            fontWeight: 700,
            position: "right",
            show: row.label === model.maxLabel || row.label === model.minLabel
          },
          name: row.label,
          value: row.value
        })),
        type: "bar"
      }
    ],
    title: {
      left: 0,
      text: model.title,
      textStyle: { color: "#102033", fontSize: 14, fontWeight: 700 }
    }
  };
}

function buildHeatmapOption(model) {
  const values = model.values ?? [];
  const numericValues = values.map((entry) => entry[2]).filter((value) => Number.isFinite(value));
  const maxAbs = Math.max(...numericValues.map((value) => Math.abs(value)), 1);

  return {
    grid: { bottom: 28, containLabel: true, left: 12, right: 72, top: 44 },
    tooltip: {
      formatter(params) {
        const [metricIndex, rowIndex, value, display] = params.data;
        return `${model.labels[rowIndex]}<br/>${model.metrics[metricIndex]}: ${display ?? value}`;
      }
    },
    visualMap: {
      calculable: true,
      inRange: { color: ["#a43f3f", "#f8fbff", "#2f7da8"] },
      left: "right",
      max: maxAbs,
      min: -maxAbs
    },
    xAxis: { axisLabel: { color: "#516173" }, data: model.metrics, type: "category" },
    yAxis: { axisLabel: { color: "#102033" }, data: model.labels, type: "category" },
    series: [{ data: values, label: { show: true }, type: "heatmap" }],
    title: {
      left: 0,
      text: model.title,
      textStyle: { color: "#102033", fontSize: 14, fontWeight: 700 }
    }
  };
}

function buildRadarOption(model) {
  return {
    legend: {
      bottom: 0,
      textStyle: { color: "#516173" },
      type: "scroll"
    },
    radar: {
      indicator: model.indicators ?? [],
      radius: "62%",
      splitArea: {
        areaStyle: { color: ["rgba(248, 251, 255, 0.95)", "rgba(233, 242, 251, 0.85)"] }
      }
    },
    series: [{ data: model.series ?? [], type: "radar" }],
    title: {
      left: 0,
      text: model.title,
      textStyle: { color: "#102033", fontSize: 14, fontWeight: 700 }
    },
    tooltip: { trigger: "item" }
  };
}

function colorForBar(row, model) {
  if (row.label === model.maxLabel) {
    return "#2f7da8";
  }

  if (row.label === model.minLabel) {
    return "#a43f3f";
  }

  return "#6f879d";
}

function syncChart() {
  if (!chart || !chartEl.value) {
    return;
  }

  chart.setOption(buildOption(), true);
}

onMounted(async () => {
  await nextTick();

  if (!chartEl.value) {
    return;
  }

  chart = init(chartEl.value);
  syncChart();

  if (typeof ResizeObserver !== "undefined") {
    resizeObserver = new ResizeObserver(() => chart?.resize());
    resizeObserver.observe(chartEl.value);
  }
});

watch(() => props.model, syncChart, { deep: true });
watch([visibleRows, sortOrder, filterText, topN], syncChart);

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
  chart?.dispose();
  chart = null;
});
</script>

<template>
  <section class="analysis-chart-panel" :data-analysis-chart-type="model.type">
    <div class="analysis-chart-header">
      <div>
        <p class="analysis-chart-kicker">{{ model.metric ?? model.type }}</p>
        <h3>{{ model.title }}</h3>
      </div>

      <div v-if="supportsBarControls" class="analysis-chart-controls">
        <input
          v-model="filterText"
          aria-label="Filter chart rows"
          class="analysis-chart-filter"
          data-analysis-chart-control="filter"
          type="search"
        />
        <select
          v-model="sortOrder"
          aria-label="Sort chart"
          class="analysis-chart-select"
          data-analysis-chart-control="sort"
        >
          <option value="default">Default</option>
          <option value="desc">High to low</option>
          <option value="asc">Low to high</option>
        </select>
        <select
          v-model="topN"
          aria-label="Limit chart rows"
          class="analysis-chart-select"
          data-analysis-chart-control="top-n"
        >
          <option value="all">All</option>
          <option value="5">Top 5</option>
          <option value="10">Top 10</option>
        </select>
      </div>
    </div>

    <div ref="chartEl" class="analysis-chart-canvas"></div>
  </section>
</template>
