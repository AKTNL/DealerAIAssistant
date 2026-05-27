import { describe, expect, it } from "vitest";
import {
  createAnalysisEnhancementsFromHtml,
  extractTableDataset,
  getVisibleChartRows,
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
    expect(parseNumericValue("+12.3%")).toEqual({
      display: "+12.3%",
      isNumeric: true,
      isPercent: true,
      value: 12.3
    });
    expect(parseNumericValue("-81.2%")).toEqual({
      display: "-81.2%",
      isNumeric: true,
      isPercent: true,
      value: -81.2
    });
    expect(parseNumericValue("1,240")).toEqual({
      display: "1,240",
      isNumeric: true,
      isPercent: false,
      value: 1240
    });
    expect(parseNumericValue("1234.5")).toEqual({
      display: "1234.5",
      isNumeric: true,
      isPercent: false,
      value: 1234.5
    });
    expect(parseNumericValue("Shanghai")).toEqual({
      display: "Shanghai",
      isNumeric: false,
      isPercent: false,
      value: null
    });
  });

  it("does not parse digit-containing category labels as numbers", () => {
    expect(parseNumericValue("Tier 1")).toEqual({
      display: "Tier 1",
      isNumeric: false,
      isPercent: false,
      value: null
    });
    expect(parseNumericValue("Store 12")).toEqual({
      display: "Store 12",
      isNumeric: false,
      isPercent: false,
      value: null
    });
    expect(parseNumericValue("FY2025 Plan")).toEqual({
      display: "FY2025 Plan",
      isNumeric: false,
      isPercent: false,
      value: null
    });
    expect(parseNumericValue("SKU-123")).toEqual({
      display: "SKU-123",
      isNumeric: false,
      isPercent: false,
      value: null
    });
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

  it("detects an English dealer label column", () => {
    const host = document.createElement("div");
    host.innerHTML = `
      <table>
        <thead>
          <tr><th>Dealer</th><th>Achievement Rate</th></tr>
        </thead>
        <tbody>
          <tr><td>Hangzhou A</td><td>112.6%</td></tr>
          <tr><td>Shanghai B</td><td>91.1%</td></tr>
        </tbody>
      </table>
    `;

    const dataset = extractTableDataset(host.querySelector("table"));

    expect(dataset.labelHeader).toBe("Dealer");
    expect(dataset.rows.map((row) => row.label)).toEqual(["Hangzhou A", "Shanghai B"]);
    expect(dataset.numericColumns.map((column) => column.header)).toEqual(["Achievement Rate"]);
  });
});

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

  it("extracts metric cards from English conclusion content", () => {
    const result = createAnalysisEnhancementsFromHtml(`
      <h2>Conclusion</h2>
      <p>Average achievement 98.4%, lowest dealer Shanghai B 76.2%, highest dealer Hangzhou A 130.0%, gap 53.8%</p>
    `);

    expect(result.metricCards.map((card) => card.value)).toEqual(["98.4%", "76.2%", "130.0%", "53.8%"]);
  });

  it("assigns conclusion metric cards by role text instead of percentage order", () => {
    const result = createAnalysisEnhancementsFromHtml(`
      <h2>Conclusion</h2>
      <p>Overall retention is 66.0%, but average achievement 98.4% needs attention.</p>
      <p>Lowest dealer Store 12 76.2%, highest dealer Dealer 001 130.0%, and gap 53.8%.</p>
    `);

    expect(result.metricCards.map((card) => card.value)).toEqual(["98.4%", "76.2%", "130.0%", "53.8%"]);
    expect(result.metricCards.map((card) => card.value)).not.toContain("66.0%");
  });

  it("stops conclusion metric extraction before tabular content", () => {
    const result = createAnalysisEnhancementsFromHtml(`
      <h2>Conclusion</h2>
      <p>Average achievement 98.4%, lowest dealer Store 12 76.2%, highest dealer Dealer 001 130.0%, gap 53.8%</p>
      <table>
        <thead><tr><th>Dealer</th><th>Gap</th></tr></thead>
        <tbody><tr><td>Dealer 001</td><td>-53.8%</td></tr></tbody>
      </table>
    `);

    expect(result.metricCards.map((card) => card.value)).toEqual(["98.4%", "76.2%", "130.0%", "53.8%"]);
  });

  it("extracts metric cards across conclusion siblings without using dealer name digits", () => {
    const result = createAnalysisEnhancementsFromHtml(`
      <h2>Conclusion</h2>
      <p>Average achievement 98.4%.</p>
      <p>Lowest dealer Store 12 76.2%.</p>
      <p>Highest dealer Dealer 001 130.0%.</p>
      <p>Gap 53.8%.</p>
    `);

    expect(result.metricCards.map((card) => card.value)).toEqual(["98.4%", "76.2%", "130.0%", "53.8%"]);
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

  it("prefers a later achievement or rate column for the primary bar chart", () => {
    const result = createAnalysisEnhancementsFromHtml(`
      <table>
        <thead><tr><th>门店</th><th>线索数</th><th>达成率</th></tr></thead>
        <tbody>
          <tr><td>杭州A店</td><td>40</td><td>130.0%</td></tr>
          <tr><td>上海B店</td><td>25</td><td>76.2%</td></tr>
        </tbody>
      </table>
    `);

    expect(result.charts.find((chart) => chart.type === "bar")).toMatchObject({
      metric: "达成率"
    });
  });

  it("uses the primary achievement column for fallback metric cards when counts come first", () => {
    const result = createAnalysisEnhancementsFromHtml(`
      <table>
        <thead><tr><th>Dealer</th><th>Leads</th><th>Achievement Rate</th></tr></thead>
        <tbody>
          <tr><td>North</td><td>42</td><td>110%</td></tr>
          <tr><td>South</td><td>18</td><td>80%</td></tr>
          <tr><td>West</td><td>30</td><td>95%</td></tr>
        </tbody>
      </table>
    `);

    expect(result.metricCards.map((card) => card.value)).toEqual(["95.0%", "80%", "110%", "30.0%"]);
    expect(result.metricCards.map((card) => card.body)).toContain("Achievement Rate");
    expect(result.metricCards.map((card) => card.value)).not.toEqual(["30.0", "18", "42", "24.0"]);
  });

  it("prefers a later relevant table metric for fallback metric cards over earlier counts", () => {
    const result = createAnalysisEnhancementsFromHtml(`
      <table>
        <thead><tr><th>Dealer</th><th>Leads</th></tr></thead>
        <tbody>
          <tr><td>A</td><td>30</td></tr>
          <tr><td>B</td><td>42</td></tr>
        </tbody>
      </table>
      <table>
        <thead><tr><th>Dealer</th><th>Achievement Rate</th></tr></thead>
        <tbody>
          <tr><td>A</td><td>130.0%</td></tr>
          <tr><td>B</td><td>76.2%</td></tr>
        </tbody>
      </table>
    `);

    const values = result.metricCards.map((card) => card.value);

    expect(values).toEqual(["103.1%", "76.2%", "130.0%", "53.8%"]);
    expect(values).not.toEqual(["36.0", "30", "42", "12.0"]);
    expect(result.metricCards.map((card) => card.body)).toContain("Achievement Rate");
  });

  it("prefers achievement headers across tables before unrelated percent columns", () => {
    const result = createAnalysisEnhancementsFromHtml(`
      <table>
        <thead><tr><th>Dealer</th><th>Retention Percent</th></tr></thead>
        <tbody>
          <tr><td>A</td><td>66.0%</td></tr>
          <tr><td>B</td><td>70.0%</td></tr>
        </tbody>
      </table>
      <table>
        <thead><tr><th>Dealer</th><th>Achievement Rate</th></tr></thead>
        <tbody>
          <tr><td>A</td><td>130.0%</td></tr>
          <tr><td>B</td><td>76.2%</td></tr>
        </tbody>
      </table>
    `);

    expect(result.metricCards.map((card) => card.value)).toEqual(["103.1%", "76.2%", "130.0%", "53.8%"]);
    expect(result.metricCards.map((card) => card.value)).not.toEqual(["68.0%", "66.0%", "70.0%", "4.0%"]);
    expect(result.metricCards.map((card) => card.body)).toContain("Achievement Rate");
  });

  it("creates heatmap and radar candidates only when table shape is suitable", () => {
    const result = createAnalysisEnhancementsFromHtml(`
      <table>
        <thead><tr><th>区域</th><th>达成率</th><th>转化率</th><th>排名差距</th><th>达成差距</th></tr></thead>
        <tbody>
          <tr><td>华东</td><td>120%</td><td>31%</td><td>88%</td><td>8%</td></tr>
          <tr><td>华南</td><td>95%</td><td>25%</td><td>80%</td><td>-5%</td></tr>
        </tbody>
      </table>
    `);

    expect(result.charts.map((chart) => chart.type)).toContain("heatmap");
    expect(result.charts.map((chart) => chart.type)).toContain("radar");
  });

  it("limits heatmap metrics and coordinates to gap-like columns", () => {
    const result = createAnalysisEnhancementsFromHtml(`
      <table>
        <thead><tr><th>Region</th><th>Achievement Rate</th><th>Conversion Rate</th><th>Plan Gap</th><th>Sales Gap</th></tr></thead>
        <tbody>
          <tr><td>East</td><td>120%</td><td>31%</td><td>88%</td><td>8%</td></tr>
          <tr><td>West</td><td>95%</td><td>25%</td><td>80%</td><td>-5%</td></tr>
        </tbody>
      </table>
    `);
    const heatmap = result.charts.find((chart) => chart.type === "heatmap");

    expect(heatmap).toMatchObject({
      metrics: ["Plan Gap", "Sales Gap"],
      values: [
        [0, 0, 88, "88%"],
        [0, 1, 80, "80%"],
        [1, 0, 8, "8%"],
        [1, 1, -5, "-5%"]
      ]
    });
  });

  it("does not create a heatmap with only one gap-like numeric column", () => {
    const result = createAnalysisEnhancementsFromHtml(`
      <table>
        <thead><tr><th>区域</th><th>达成率</th><th>转化率</th><th>差距</th></tr></thead>
        <tbody>
          <tr><td>华东</td><td>120%</td><td>31%</td><td>8%</td></tr>
          <tr><td>华南</td><td>95%</td><td>25%</td><td>-5%</td></tr>
        </tbody>
      </table>
    `);

    expect(result.charts.map((chart) => chart.type)).not.toContain("heatmap");
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
