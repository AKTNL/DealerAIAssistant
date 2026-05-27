# ECharts Chat Analysis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a frontend-only ECharts enhancement layer that makes chat analysis replies easier to scan and explore.

**Architecture:** Keep the backend response format unchanged. Parse already-rendered safe assistant HTML in focused frontend utilities, render metric cards and ECharts panels in `AssistantMessage.vue`, and keep Mermaid/table output intact as the fallback. Use conservative chart detection so ambiguous replies keep the existing view.

**Tech Stack:** Vue 3, Vite, Vitest/jsdom, ECharts, existing Markdown/Mermaid renderer.

---

## File Structure

- Create: `frontend/src/utils/analysisCharts.js`
  - Owns numeric parsing, table extraction, metric-card extraction, chart model generation, and chart row sorting/filtering helpers.
- Create: `frontend/src/utils/__tests__/analysisCharts.spec.js`
  - Unit coverage for parser and chart model decisions.
- Create: `frontend/src/components/chat/AnalysisChart.vue`
  - Owns one ECharts instance, chart controls, resize handling, option generation, and disposal.
- Create: `frontend/src/components/__tests__/AnalysisChart.spec.js`
  - Mocks ECharts and verifies init, option updates, controls, and disposal.
- Modify: `frontend/src/components/chat/AssistantMessage.vue`
  - Imports `AnalysisChart`, computes enhancement data from `message.html`, skips enhancement while streaming, and renders metric cards plus chart panels without removing original Markdown/Mermaid output.
- Modify: `frontend/src/components/__tests__/AssistantMessage.spec.js`
  - Adds tests for metric cards, chart mounting, streaming skip, and unmount cleanup.
- Modify: `frontend/src/style.css`
  - Adds chat background image, metric-card grid, table emphasis hooks, chart panel sizing, chart controls, and high/low annotation styles.
- Modify: `frontend/src/__tests__/styleTokens.spec.js`
  - Adds style assertions for background, cards, chart size, and high/low emphasis.
- Modify: `frontend/package.json`
  - Adds `echarts`.
- Modify: `frontend/package-lock.json`
  - Lockfile update from `npm install`.
- Create: `frontend/public/background.jpg`
  - Copy of root `background.jpg` so Vite can serve it at `/background.jpg`.

## Task 0: Dependency And Asset Setup

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Create: `frontend/public/background.jpg`

- [ ] **Step 1: Install ECharts**

Run from repository root:

```powershell
Push-Location frontend
npm install echarts
Pop-Location
```

Expected: `frontend/package.json` and `frontend/package-lock.json` include `echarts`.

- [ ] **Step 2: Copy the background asset into Vite public assets**

Run from repository root:

```powershell
Copy-Item -LiteralPath 'background.jpg' -Destination 'frontend/public/background.jpg' -Force
```

Expected: `frontend/public/background.jpg` exists and matches the root image used by the user.

- [ ] **Step 3: Commit setup**

```powershell
git add -- frontend/package.json frontend/package-lock.json frontend/public/background.jpg
git commit -m "chore: add echarts and chat background asset"
```

## Task 1: Numeric And Table Parsing Utility

**Files:**
- Create: `frontend/src/utils/analysisCharts.js`
- Create: `frontend/src/utils/__tests__/analysisCharts.spec.js`

- [ ] **Step 1: Write failing numeric parsing tests**

Add to `frontend/src/utils/__tests__/analysisCharts.spec.js`:

```js
import { describe, expect, it } from "vitest";
import {
  extractTableDataset,
  parseNumericValue
} from "../analysisCharts";

describe("parseNumericValue", () => {
  it("parses percentages, signed gaps, commas, and plain numbers", () => {
    expect(parseNumericValue("112.6%")).toEqual({
      display: "112.6%",
      isNumeric: true,
      isPercent: true,
      value: 112.6
    });
    expect(parseNumericValue("-81.2%")).toMatchObject({ isNumeric: true, isPercent: true, value: -81.2 });
    expect(parseNumericValue("1,240")).toMatchObject({ isNumeric: true, isPercent: false, value: 1240 });
    expect(parseNumericValue("Shanghai")).toMatchObject({ isNumeric: false, isPercent: false, value: null });
  });
});

describe("extractTableDataset", () => {
  it("detects the dealer label column and numeric achievement column", () => {
    const host = document.createElement("div");
    host.innerHTML = `
      <table>
        <thead>
          <tr><th>门店</th><th>达成率</th><th>差距</th></tr>
        </thead>
        <tbody>
          <tr><td>杭州A店</td><td>112.6%</td><td>21.5%</td></tr>
          <tr><td>上海B店</td><td>91.1%</td><td>-2.4%</td></tr>
        </tbody>
      </table>
    `;

    const dataset = extractTableDataset(host.querySelector("table"));

    expect(dataset.labelHeader).toBe("门店");
    expect(dataset.rows.map((row) => row.label)).toEqual(["杭州A店", "上海B店"]);
    expect(dataset.numericColumns.map((column) => column.header)).toEqual(["达成率", "差距"]);
    expect(dataset.numericColumns[0].values.map((entry) => entry.value)).toEqual([112.6, 91.1]);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
Push-Location frontend
npm test -- src/utils/__tests__/analysisCharts.spec.js
Pop-Location
```

Expected: FAIL because `analysisCharts.js` does not exist.

- [ ] **Step 3: Implement numeric and table parsing**

Create `frontend/src/utils/analysisCharts.js`:

```js
const LABEL_HEADER_PATTERNS = [
  /dealer/i,
  /store/i,
  /region/i,
  /campaign/i,
  /source/i,
  /name/i,
  /门店/,
  /经销商/,
  /区域/,
  /活动/,
  /来源/,
  /名称/
];

export function parseNumericValue(rawValue) {
  const display = String(rawValue ?? "").trim();
  const normalized = display.replace(/,/g, "");
  const match = normalized.match(/[+-]?\d+(?:\.\d+)?/);

  if (!match) {
    return { display, isNumeric: false, isPercent: false, value: null };
  }

  return {
    display,
    isNumeric: true,
    isPercent: normalized.includes("%"),
    value: Number.parseFloat(match[0])
  };
}

export function extractTableDataset(table) {
  if (!table) {
    return emptyDataset();
  }

  const headers = Array.from(table.querySelectorAll("thead th")).map((cell) => cleanText(cell.textContent));
  const bodyRows = Array.from(table.querySelectorAll("tbody tr"));
  const rows = bodyRows.map((row) => Array.from(row.children).map((cell) => cleanText(cell.textContent)));
  const width = Math.max(headers.length, ...rows.map((row) => row.length), 0);
  const normalizedHeaders = Array.from({ length: width }, (_, index) => headers[index] || `Column ${index + 1}`);
  const labelIndex = findLabelColumn(normalizedHeaders, rows);

  const normalizedRows = rows
    .map((cells, rowIndex) => ({
      cells,
      index: rowIndex,
      label: cells[labelIndex] || `Row ${rowIndex + 1}`
    }))
    .filter((row) => row.cells.length > 0);

  const numericColumns = normalizedHeaders
    .map((header, columnIndex) => {
      if (columnIndex === labelIndex) {
        return null;
      }

      const values = normalizedRows.map((row) => ({
        display: row.cells[columnIndex] || "",
        label: row.label,
        rowIndex: row.index,
        ...parseNumericValue(row.cells[columnIndex])
      }));
      const numericValues = values.filter((entry) => entry.isNumeric);

      if (numericValues.length < Math.max(2, Math.ceil(normalizedRows.length * 0.5))) {
        return null;
      }

      return { header, index: columnIndex, values: numericValues };
    })
    .filter(Boolean);

  return {
    headers: normalizedHeaders,
    labelHeader: normalizedHeaders[labelIndex],
    labelIndex,
    numericColumns,
    rows: normalizedRows
  };
}

function cleanText(value) {
  return String(value ?? "").replace(/\s+/g, " ").trim();
}

function emptyDataset() {
  return {
    headers: [],
    labelHeader: "",
    labelIndex: 0,
    numericColumns: [],
    rows: []
  };
}

function findLabelColumn(headers, rows) {
  const matchedHeader = headers.findIndex((header) =>
    LABEL_HEADER_PATTERNS.some((pattern) => pattern.test(header))
  );

  if (matchedHeader >= 0) {
    return matchedHeader;
  }

  const columnCount = Math.max(headers.length, ...rows.map((row) => row.length), 0);
  for (let columnIndex = 0; columnIndex < columnCount; columnIndex += 1) {
    const values = rows.map((row) => row[columnIndex] || "");
    const numericCount = values.filter((value) => parseNumericValue(value).isNumeric).length;

    if (numericCount < values.length / 2) {
      return columnIndex;
    }
  }

  return 0;
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
Push-Location frontend
npm test -- src/utils/__tests__/analysisCharts.spec.js
Pop-Location
```

Expected: PASS.

- [ ] **Step 5: Commit parser baseline**

```powershell
git add -- frontend/src/utils/analysisCharts.js frontend/src/utils/__tests__/analysisCharts.spec.js
git commit -m "feat: parse analysis table values"
```

## Task 2: Metric Cards And Chart Models

**Files:**
- Modify: `frontend/src/utils/analysisCharts.js`
- Modify: `frontend/src/utils/__tests__/analysisCharts.spec.js`

- [ ] **Step 1: Write failing enhancement model tests**

First update the existing `analysisCharts.spec.js` import from `../analysisCharts` so it includes all four named exports:

```js
import {
  createAnalysisEnhancementsFromHtml,
  extractTableDataset,
  getVisibleChartRows,
  parseNumericValue
} from "../analysisCharts";
```

Then append these tests to `frontend/src/utils/__tests__/analysisCharts.spec.js`:

```js

describe("createAnalysisEnhancementsFromHtml", () => {
  it("extracts metric cards from current content without hard-coded example values", () => {
    const result = createAnalysisEnhancementsFromHtml(`
      <h2>核心结论</h2>
      <ul>
        <li>平均达成率 98.4%，最低门店 上海B店 76.2%，最高门店 杭州A店 130.0%，差距 53.8%</li>
      </ul>
    `);

    expect(result.metricCards.map((card) => card.value)).toEqual(["98.4%", "76.2%", "130.0%", "53.8%"]);
    expect(result.metricCards.map((card) => card.value)).not.toContain("112.6%");
  });

  it("creates a bar chart model with highest and lowest annotations", () => {
    const result = createAnalysisEnhancementsFromHtml(`
      <table>
        <thead><tr><th>门店</th><th>达成率</th></tr></thead>
        <tbody>
          <tr><td>杭州A店</td><td>130.0%</td></tr>
          <tr><td>上海B店</td><td>76.2%</td></tr>
          <tr><td>成都C店</td><td>98.4%</td></tr>
        </tbody>
      </table>
    `);

    expect(result.charts).toHaveLength(1);
    expect(result.charts[0]).toMatchObject({
      type: "bar",
      metric: "达成率",
      maxLabel: "杭州A店",
      minLabel: "上海B店"
    });
  });

  it("creates heatmap and radar candidates only when table shape is suitable", () => {
    const result = createAnalysisEnhancementsFromHtml(`
      <table>
        <thead><tr><th>区域</th><th>达成率</th><th>转化率</th><th>完成率</th><th>差距</th></tr></thead>
        <tbody>
          <tr><td>华东</td><td>120%</td><td>31%</td><td>88%</td><td>8%</td></tr>
          <tr><td>华南</td><td>95%</td><td>25%</td><td>80%</td><td>-5%</td></tr>
        </tbody>
      </table>
    `);

    expect(result.charts.map((chart) => chart.type)).toContain("heatmap");
    expect(result.charts.map((chart) => chart.type)).toContain("radar");
  });
});

describe("getVisibleChartRows", () => {
  it("filters, sorts, and limits rows", () => {
    const rows = [
      { label: "A店", value: 30, display: "30%" },
      { label: "B店", value: 10, display: "10%" },
      { label: "C店", value: 20, display: "20%" }
    ];

    expect(getVisibleChartRows(rows, { filterText: "店", sortOrder: "desc", topN: 2 }).map((row) => row.label))
      .toEqual(["A店", "C店"]);
    expect(getVisibleChartRows(rows, { filterText: "B", sortOrder: "default", topN: "all" }).map((row) => row.label))
      .toEqual(["B店"]);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
Push-Location frontend
npm test -- src/utils/__tests__/analysisCharts.spec.js
Pop-Location
```

Expected: FAIL because model helpers are not implemented.

- [ ] **Step 3: Implement enhancement models**

Add these exports and helpers to `frontend/src/utils/analysisCharts.js`:

```js
const CONCLUSION_HEADINGS = [/核心结论/, /^conclusion$/i];
const METRIC_PATTERNS = [
  { key: "average-achievement", label: "平均达成率", pattern: /平均(?:达成率|achievement)[^\d+-]*([+-]?\d+(?:\.\d+)?%?)/i },
  { key: "lowest-dealer", label: "最低门店", pattern: /最低(?:门店|dealer)?[^\d+-]*([+-]?\d+(?:\.\d+)?%?)/i },
  { key: "highest-dealer", label: "最高门店", pattern: /最高(?:门店|dealer)?[^\d+-]*([+-]?\d+(?:\.\d+)?%?)/i },
  { key: "gap", label: "差距", pattern: /(?:差距|gap)[^\d+-]*([+-]?\d+(?:\.\d+)?%?)/i }
];
const BAR_METRIC_PATTERNS = [/达成/, /完成/, /转化/, /比率/, /率$/, /achievement/i, /completion/i, /conversion/i, /rate/i];
const GAP_METRIC_PATTERNS = [/差距/, /差值/, /排名/, /gap/i, /difference/i, /rank/i];

export function createAnalysisEnhancementsFromHtml(html) {
  const root = document.createElement("div");
  root.innerHTML = String(html ?? "");
  const tables = Array.from(root.querySelectorAll("table"));
  const datasets = tables.map((table) => extractTableDataset(table));
  const metricCards = extractMetricCards(root, datasets);
  const charts = datasets.flatMap((dataset, tableIndex) => buildChartModels(dataset, tableIndex));

  return {
    charts,
    metricCards
  };
}

export function getVisibleChartRows(rows, { filterText = "", sortOrder = "default", topN = "all" } = {}) {
  const normalizedFilter = String(filterText).trim().toLowerCase();
  let visible = rows.filter((row) => row.label.toLowerCase().includes(normalizedFilter));

  if (sortOrder === "asc") {
    visible = [...visible].sort((left, right) => left.value - right.value);
  } else if (sortOrder === "desc") {
    visible = [...visible].sort((left, right) => right.value - left.value);
  }

  if (topN !== "all") {
    visible = visible.slice(0, Number(topN));
  }

  return visible;
}

function extractMetricCards(root, datasets) {
  const cards = extractMetricCardsFromConclusion(root);

  if (cards.length > 0) {
    return cards.slice(0, 4);
  }

  return extractMetricCardsFromTables(datasets).slice(0, 4);
}

function extractMetricCardsFromConclusion(root) {
  const heading = Array.from(root.querySelectorAll("h1,h2,h3")).find((candidate) =>
    CONCLUSION_HEADINGS.some((pattern) => pattern.test(cleanText(candidate.textContent)))
  );
  const text = heading?.nextElementSibling?.textContent ?? "";

  return METRIC_PATTERNS
    .map((definition) => {
      const match = text.match(definition.pattern);
      return match ? { key: definition.key, label: definition.label, value: match[1], body: cleanText(text) } : null;
    })
    .filter(Boolean);
}

function extractMetricCardsFromTables(datasets) {
  const cards = [];

  for (const dataset of datasets) {
    for (const column of dataset.numericColumns) {
      const values = column.values;
      if (values.length < 2) {
        continue;
      }

      const sorted = [...values].sort((left, right) => left.value - right.value);
      const min = sorted[0];
      const max = sorted[sorted.length - 1];
      const average = values.reduce((sum, entry) => sum + entry.value, 0) / values.length;
      const suffix = values.some((entry) => entry.isPercent) ? "%" : "";

      cards.push({ key: "average-achievement", label: "平均达成率", value: `${average.toFixed(1)}${suffix}`, body: column.header });
      cards.push({ key: "lowest-dealer", label: "最低门店", value: min.display, body: min.label });
      cards.push({ key: "highest-dealer", label: "最高门店", value: max.display, body: max.label });
      cards.push({ key: "gap", label: "差距", value: `${(max.value - min.value).toFixed(1)}${suffix}`, body: `${max.label} - ${min.label}` });
      break;
    }
  }

  return cards;
}

function buildChartModels(dataset, tableIndex) {
  if (dataset.rows.length < 2 || dataset.numericColumns.length === 0) {
    return [];
  }

  const models = [];
  const primaryColumn = choosePrimaryColumn(dataset.numericColumns);

  if (primaryColumn) {
    const rows = primaryColumn.values.map((entry) => ({
      display: entry.display,
      label: entry.label,
      rowIndex: entry.rowIndex,
      value: entry.value
    }));
    const sorted = [...rows].sort((left, right) => left.value - right.value);

    models.push({
      id: `analysis-chart-${tableIndex}-bar-${primaryColumn.index}`,
      type: "bar",
      title: `${primaryColumn.header} 对比`,
      metric: primaryColumn.header,
      rows,
      maxLabel: sorted[sorted.length - 1].label,
      minLabel: sorted[0].label
    });
  }

  if (dataset.numericColumns.length >= 2 && dataset.numericColumns.some((column) => GAP_METRIC_PATTERNS.some((pattern) => pattern.test(column.header)))) {
    models.push({
      id: `analysis-chart-${tableIndex}-heatmap`,
      type: "heatmap",
      title: "差距热力图",
      labels: dataset.rows.map((row) => row.label),
      metrics: dataset.numericColumns.map((column) => column.header),
      values: dataset.numericColumns.flatMap((column, columnIndex) =>
        column.values.map((entry) => [columnIndex, entry.rowIndex, entry.value, entry.display])
      )
    });
  }

  if (dataset.numericColumns.length >= 3 && dataset.rows.length >= 2) {
    models.push({
      id: `analysis-chart-${tableIndex}-radar`,
      type: "radar",
      title: "区域/门店雷达对比",
      indicators: dataset.numericColumns.map((column) => ({
        max: Math.max(...column.values.map((entry) => entry.value), 1),
        name: column.header
      })),
      series: dataset.rows.map((row) => ({
        name: row.label,
        value: dataset.numericColumns.map((column) =>
          column.values.find((entry) => entry.rowIndex === row.index)?.value ?? 0
        )
      }))
    });
  }

  return models;
}

function choosePrimaryColumn(columns) {
  return columns.find((column) => BAR_METRIC_PATTERNS.some((pattern) => pattern.test(column.header))) ?? columns[0] ?? null;
}
```

- [ ] **Step 4: Run utility tests**

```powershell
Push-Location frontend
npm test -- src/utils/__tests__/analysisCharts.spec.js
Pop-Location
```

Expected: PASS.

- [ ] **Step 5: Commit enhancement models**

```powershell
git add -- frontend/src/utils/analysisCharts.js frontend/src/utils/__tests__/analysisCharts.spec.js
git commit -m "feat: build analysis chart models"
```

## Task 3: ECharts Component

**Files:**
- Create: `frontend/src/components/chat/AnalysisChart.vue`
- Create: `frontend/src/components/__tests__/AnalysisChart.spec.js`

- [ ] **Step 1: Write failing component tests**

Create `frontend/src/components/__tests__/AnalysisChart.spec.js`:

```js
import { mount } from "@vue/test-utils";
import { beforeEach, describe, expect, test, vi } from "vitest";

const { disposeMock, initMock, resizeMock, setOptionMock } = vi.hoisted(() => ({
  disposeMock: vi.fn(),
  initMock: vi.fn(),
  resizeMock: vi.fn(),
  setOptionMock: vi.fn()
}));

vi.mock("echarts", () => ({
  default: {
    init: initMock
  },
  init: initMock
}));

import AnalysisChart from "../chat/AnalysisChart.vue";

const chartInstance = {
  dispose: disposeMock,
  resize: resizeMock,
  setOption: setOptionMock
};

function barModel() {
  return {
    id: "chart-1",
    maxLabel: "A店",
    metric: "达成率",
    minLabel: "B店",
    rows: [
      { display: "130.0%", label: "A店", value: 130 },
      { display: "76.2%", label: "B店", value: 76.2 },
      { display: "98.4%", label: "C店", value: 98.4 }
    ],
    title: "达成率 对比",
    type: "bar"
  };
}

beforeEach(() => {
  disposeMock.mockClear();
  initMock.mockReset();
  resizeMock.mockClear();
  setOptionMock.mockClear();
  initMock.mockReturnValue(chartInstance);
});

describe("AnalysisChart", () => {
  test("initializes echarts and marks highest and lowest bars", async () => {
    mount(AnalysisChart, {
      props: { model: barModel() },
      attachTo: document.body
    });

    expect(initMock).toHaveBeenCalledTimes(1);
    expect(setOptionMock).toHaveBeenCalled();
    const option = setOptionMock.mock.calls.at(-1)[0];
    expect(option.series[0].data[0].itemStyle.color).toBe("#2f7da8");
    expect(option.series[0].data[1].itemStyle.color).toBe("#a43f3f");
  });

  test("updates options when sort and filter controls change", async () => {
    const wrapper = mount(AnalysisChart, {
      props: { model: barModel() },
      attachTo: document.body
    });

    await wrapper.find('[data-analysis-chart-control="sort"]').setValue("desc");
    await wrapper.find('[data-analysis-chart-control="filter"]').setValue("C");

    const option = setOptionMock.mock.calls.at(-1)[0];
    expect(option.yAxis.data).toEqual(["C店"]);
  });

  test("disposes chart on unmount", () => {
    const wrapper = mount(AnalysisChart, {
      props: { model: barModel() },
      attachTo: document.body
    });

    wrapper.unmount();

    expect(disposeMock).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
Push-Location frontend
npm test -- src/components/__tests__/AnalysisChart.spec.js
Pop-Location
```

Expected: FAIL because `AnalysisChart.vue` does not exist.

- [ ] **Step 3: Implement `AnalysisChart.vue`**

Create `frontend/src/components/chat/AnalysisChart.vue`:

```vue
<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as echarts from "echarts";
import { getVisibleChartRows } from "../../utils/analysisCharts";

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
    color: ["#6f879d"],
    grid: { bottom: 24, containLabel: true, left: 16, right: 28, top: 36 },
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter(params) {
        const item = params[0]?.data;
        return item ? `${item.name}<br/>${model.metric}: ${item.display}` : "";
      }
    },
    xAxis: { type: "value" },
    yAxis: { data: rows.map((row) => row.label), type: "category" },
    series: [
      {
        barMaxWidth: 28,
        data: rows.map((row) => ({
          display: row.display,
          itemStyle: { color: colorForBar(row, model) },
          label: {
            formatter: row.label === model.maxLabel ? "最高 {c}" : row.label === model.minLabel ? "最低 {c}" : "{c}",
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
  return {
    grid: { bottom: 24, containLabel: true, left: 12, right: 24, top: 42 },
    tooltip: {
      formatter(params) {
        const [metricIndex, rowIndex, value, display] = params.data;
        return `${model.labels[rowIndex]}<br/>${model.metrics[metricIndex]}: ${display ?? value}`;
      }
    },
    xAxis: { data: model.metrics, type: "category" },
    yAxis: { data: model.labels, type: "category" },
    series: [{ data: model.values, label: { show: true }, type: "heatmap" }],
    title: { left: 0, text: model.title, textStyle: { color: "#102033", fontSize: 14, fontWeight: 700 } },
    visualMap: { calculable: true, inRange: { color: ["#a43f3f", "#f8fbff", "#2f7da8"] }, left: "right", min: -100, max: 100 }
  };
}

function buildRadarOption(model) {
  return {
    legend: { bottom: 0 },
    radar: { indicator: model.indicators, radius: "62%" },
    series: [{ data: model.series, type: "radar" }],
    title: { left: 0, text: model.title, textStyle: { color: "#102033", fontSize: 14, fontWeight: 700 } },
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

  chart = echarts.init(chartEl.value);
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
          class="analysis-chart-filter"
          data-analysis-chart-control="filter"
          type="search"
          aria-label="Filter stores"
        />
        <select v-model="sortOrder" class="analysis-chart-select" data-analysis-chart-control="sort" aria-label="Sort chart">
          <option value="default">默认</option>
          <option value="desc">降序</option>
          <option value="asc">升序</option>
        </select>
        <select v-model="topN" class="analysis-chart-select" data-analysis-chart-control="top-n" aria-label="Top N">
          <option value="all">全部</option>
          <option value="5">Top 5</option>
          <option value="10">Top 10</option>
        </select>
      </div>
    </div>

    <div ref="chartEl" class="analysis-chart-canvas"></div>
  </section>
</template>
```

- [ ] **Step 4: Run component test**

```powershell
Push-Location frontend
npm test -- src/components/__tests__/AnalysisChart.spec.js
Pop-Location
```

Expected: PASS.

- [ ] **Step 5: Commit chart component**

```powershell
git add -- frontend/src/components/chat/AnalysisChart.vue frontend/src/components/__tests__/AnalysisChart.spec.js
git commit -m "feat: add interactive analysis chart component"
```

## Task 4: Assistant Message Integration

**Files:**
- Modify: `frontend/src/components/chat/AssistantMessage.vue`
- Modify: `frontend/src/components/__tests__/AssistantMessage.spec.js`

- [ ] **Step 1: Extend AssistantMessage tests**

Add this mock near the existing Mermaid mock in `frontend/src/components/__tests__/AssistantMessage.spec.js`:

```js
vi.mock("echarts", () => ({
  default: {
    init: vi.fn(() => ({
      dispose: vi.fn(),
      resize: vi.fn(),
      setOption: vi.fn()
    }))
  },
  init: vi.fn(() => ({
    dispose: vi.fn(),
    resize: vi.fn(),
    setOption: vi.fn()
  }))
}));
```

Append tests:

```js
describe("AssistantMessage analysis enhancements", () => {
  test("renders metric cards from current reply values and not fixed examples", async () => {
    const html = renderMarkdownLite(`## 核心结论

- 平均达成率 98.4%，最低门店 上海B店 76.2%，最高门店 杭州A店 130.0%，差距 53.8%`);
    const wrapper = mountAssistant(buildMessage(html));

    await flushPromises();

    expect(wrapper.findAll(".analysis-metric-card")).toHaveLength(4);
    expect(wrapper.text()).toContain("98.4%");
    expect(wrapper.text()).toContain("53.8%");
    expect(wrapper.text()).not.toContain("112.6%");
  });

  test("renders analysis chart panels for supported completed tables", async () => {
    const html = renderMarkdownLite(`<table>
      <thead><tr><th>门店</th><th>达成率</th></tr></thead>
      <tbody><tr><td>A店</td><td>130%</td></tr><tr><td>B店</td><td>76%</td></tr></tbody>
    </table>`);
    const wrapper = mountAssistant(buildMessage(html));

    await flushPromises();

    expect(wrapper.findComponent({ name: "AnalysisChart" }).exists()).toBe(true);
  });

  test("skips analysis charts while message is streaming", async () => {
    const html = renderMarkdownLite(`<table>
      <thead><tr><th>门店</th><th>达成率</th></tr></thead>
      <tbody><tr><td>A店</td><td>130%</td></tr><tr><td>B店</td><td>76%</td></tr></tbody>
    </table>`);
    const wrapper = mountAssistant(buildMessage(html, { rendered: false, streaming: true }));

    await flushPromises();

    expect(wrapper.findComponent({ name: "AnalysisChart" }).exists()).toBe(false);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
Push-Location frontend
npm test -- src/components/__tests__/AssistantMessage.spec.js
Pop-Location
```

Expected: FAIL because `AssistantMessage.vue` does not render analysis enhancements.

- [ ] **Step 3: Implement integration**

Modify `frontend/src/components/chat/AssistantMessage.vue`:

```vue
<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import mermaid from "mermaid";
import FollowUpButtons from "./FollowUpButtons.vue";
import AnalysisChart from "./AnalysisChart.vue";
import { createAnalysisEnhancementsFromHtml } from "../../utils/analysisCharts";

const analysisEnhancements = computed(() => {
  if (!props.message?.html || props.message?.streaming || props.message?.rendered === false) {
    return { charts: [], metricCards: [] };
  }

  return createAnalysisEnhancementsFromHtml(props.message.html);
});
</script>
```

Keep the existing Mermaid setup and functions in the same file. In the template, replace the single Markdown body line with this block:

```vue
<template>
  <div v-if="message.html" class="analysis-response-body">
    <div v-if="analysisEnhancements.metricCards.length" class="analysis-metric-grid" aria-label="核心结论摘要">
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

    <div v-if="analysisEnhancements.charts.length" class="analysis-chart-stack">
      <AnalysisChart
        v-for="chart in analysisEnhancements.charts"
        :key="chart.id"
        :model="chart"
      />
    </div>
  </div>
</template>
```

- [ ] **Step 4: Run AssistantMessage tests**

```powershell
Push-Location frontend
npm test -- src/components/__tests__/AssistantMessage.spec.js
Pop-Location
```

Expected: PASS.

- [ ] **Step 5: Run utility and chart component tests**

```powershell
Push-Location frontend
npm test -- src/utils/__tests__/analysisCharts.spec.js src/components/__tests__/AnalysisChart.spec.js
Pop-Location
```

Expected: PASS.

- [ ] **Step 6: Commit AssistantMessage integration**

```powershell
git add -- frontend/src/components/chat/AssistantMessage.vue frontend/src/components/__tests__/AssistantMessage.spec.js
git commit -m "feat: enhance assistant analysis replies"
```

## Task 5: Styles, Background, And Visual Hierarchy

**Files:**
- Modify: `frontend/src/style.css`
- Modify: `frontend/src/__tests__/styleTokens.spec.js`

- [ ] **Step 1: Write failing style assertions**

Append to `frontend/src/__tests__/styleTokens.spec.js`:

```js
describe("chat analysis visual enhancements", () => {
  test("defines background, metric card, and chart hierarchy styles", () => {
    expectSelectorInRule(".chat-screen::before", [
      "background-image: linear-gradient(rgba(244, 247, 251, 0.84), rgba(244, 247, 251, 0.9)), url(\"/background.jpg\");",
      "background-size: cover;"
    ]);

    expectSelectorInRule(".analysis-metric-grid", [
      "display: grid;",
      "grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));"
    ]);

    expectSelectorInRule(".analysis-metric-value", [
      "font-size: 1.45rem;",
      "color: var(--brand-primary);"
    ]);

    expectSelectorInRule(".analysis-chart-panel", [
      "min-height: 380px;",
      "background: rgba(255, 255, 255, 0.94);"
    ]);

    expectSelectorInRule(".analysis-chart-canvas", [
      "height: 320px;"
    ]);
  });
});
```

- [ ] **Step 2: Run style test to verify it fails**

```powershell
Push-Location frontend
npm test -- src/__tests__/styleTokens.spec.js
Pop-Location
```

Expected: FAIL because the new selectors do not exist.

- [ ] **Step 3: Add styles**

Append to `frontend/src/style.css` near the chat and Mermaid styles:

```css
.chat-screen::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background-image: linear-gradient(rgba(244, 247, 251, 0.84), rgba(244, 247, 251, 0.9)), url("/background.jpg");
  background-position: center;
  background-repeat: no-repeat;
  background-size: cover;
}

.chat-scroll,
.jump-latest-button,
.composer-card,
.composer-card-editorial {
  position: relative;
  z-index: 1;
}

.analysis-response-body {
  display: grid;
  gap: 1rem;
}

.analysis-metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 0.75rem;
  margin: 0.35rem 0 0.2rem;
}

.analysis-metric-card {
  min-height: 104px;
  display: grid;
  align-content: start;
  gap: 0.35rem;
  padding: 0.9rem 1rem;
  border: 1px solid rgba(63, 111, 159, 0.2);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(248, 251, 255, 0.96), rgba(233, 242, 251, 0.86));
}

.analysis-metric-label {
  color: var(--text-muted);
  font-size: 0.78rem;
  font-weight: 700;
}

.analysis-metric-value {
  color: var(--brand-primary);
  font-size: 1.45rem;
  font-weight: 760;
  line-height: 1.15;
}

.analysis-metric-body {
  min-width: 0;
  color: var(--text-muted);
  font-size: 0.82rem;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.analysis-chart-stack {
  display: grid;
  gap: 1rem;
  margin-top: 0.25rem;
}

.analysis-chart-panel {
  min-height: 380px;
  display: grid;
  gap: 0.85rem;
  padding: 1rem;
  border: 1px solid rgba(63, 111, 159, 0.22);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: var(--shadow-sm);
}

.analysis-chart-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.analysis-chart-header h3,
.analysis-chart-kicker {
  margin: 0;
}

.analysis-chart-header h3 {
  color: var(--text-strong);
  font-size: 1rem;
}

.analysis-chart-kicker {
  color: var(--text-helper);
  font-size: 0.76rem;
  font-weight: 700;
}

.analysis-chart-controls {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.45rem;
}

.analysis-chart-filter,
.analysis-chart-select {
  min-height: 34px;
  border: 1px solid var(--border-soft);
  border-radius: 6px;
  background: var(--surface);
  color: var(--text-main);
  font-size: 0.82rem;
}

.analysis-chart-filter {
  width: 8rem;
  padding: 0.35rem 0.55rem;
}

.analysis-chart-select {
  padding: 0.35rem 0.45rem;
}

.analysis-chart-canvas {
  width: 100%;
  height: 320px;
  min-width: 0;
}

@media (max-width: 720px) {
  .analysis-chart-header {
    display: grid;
  }

  .analysis-chart-controls {
    justify-content: flex-start;
  }

  .analysis-chart-panel {
    min-height: 360px;
  }
}
```

- [ ] **Step 4: Run style test**

```powershell
Push-Location frontend
npm test -- src/__tests__/styleTokens.spec.js
Pop-Location
```

Expected: PASS.

- [ ] **Step 5: Commit styles**

```powershell
git add -- frontend/src/style.css frontend/src/__tests__/styleTokens.spec.js
git commit -m "style: strengthen chat analysis visuals"
```

## Task 6: Full Frontend Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused frontend tests**

```powershell
Push-Location frontend
npm test -- src/utils/__tests__/analysisCharts.spec.js src/components/__tests__/AnalysisChart.spec.js src/components/__tests__/AssistantMessage.spec.js src/__tests__/styleTokens.spec.js
Pop-Location
```

Expected: PASS.

- [ ] **Step 2: Run full frontend test suite**

```powershell
Push-Location frontend
npm test
Pop-Location
```

Expected: PASS.

- [ ] **Step 3: Build frontend**

```powershell
Push-Location frontend
npm run build
Pop-Location
```

Expected: PASS and Vite writes the production bundle to `backend/src/main/resources/static`.

- [ ] **Step 4: Inspect final changed files**

```powershell
git status --short
git diff --stat HEAD
```

Expected: Only task-related frontend files, docs, and generated static bundle changes are listed. Existing unrelated user changes remain untouched.

- [ ] **Step 5: Final commit if verification changed generated static output**

If `backend/src/main/resources/static/index.html` changed because `npm run build` updated the bundled frontend, include it with the final implementation commit:

```powershell
git add -- backend/src/main/resources/static/index.html frontend
git commit -m "feat: add echarts chat analysis enhancements"
```

If the generated static bundle did not change beyond files already committed in earlier tasks, skip this commit and note that verification produced no additional tracked output.
