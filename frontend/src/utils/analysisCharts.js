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

const CONCLUSION_HEADINGS = [/核心结论/, /^conclusion$/i];
const CONCLUSION_BLOCK_TAGS = new Set(["TABLE", "FIGURE", "CANVAS", "SVG"]);
const ROLE_DEFINITIONS = [
  {
    key: "average-achievement",
    label: "平均达成率",
    marker: "(?:平均(?:达成率|achievement)|average\\s+achievement)"
  },
  {
    key: "lowest-dealer",
    label: "最低门店",
    marker: "(?:最低(?:门店|dealer)?|lowest\\s+dealer)"
  },
  {
    key: "highest-dealer",
    label: "最高门店",
    marker: "(?:最高(?:门店|dealer)?|highest\\s+dealer)"
  },
  {
    key: "gap",
    label: "差距",
    marker: "(?:差距|gap)"
  }
];
const BAR_METRIC_PATTERNS = [/达成/, /完成/, /转化/, /比率/, /率$/, /achievement/i, /completion/i, /conversion/i, /rate/i];
const GAP_METRIC_PATTERNS = [/差距/, /差值/, /排名/, /gap/i, /difference/i, /rank/i];

export function parseNumericValue(rawValue) {
  const display = String(rawValue ?? "").trim();
  const isNumericValue = /^[+-]?(?:(?:\d{1,3}(?:,\d{3})+)|\d+)(?:\.\d+)?%?$/.test(display);

  if (!isNumericValue) {
    return { display, isNumeric: false, isPercent: false, value: null };
  }

  const isPercent = display.endsWith("%");
  const normalized = display.replace(/,/g, "").replace(/%$/, "");

  return {
    display,
    isNumeric: true,
    isPercent,
    value: Number.parseFloat(normalized)
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
  const text = collectConclusionText(heading);

  return ROLE_DEFINITIONS
    .map((definition) => {
      const segment = extractRoleSegment(text, definition);
      const value = segment ? extractMetricValue(segment) : null;

      return value
        ? { key: definition.key, label: definition.label, value, body: cleanText(text) }
        : null;
    })
    .filter(Boolean);
}

function extractRoleSegment(text, definition) {
  const otherMarkers = ROLE_DEFINITIONS
    .filter((candidate) => candidate.key !== definition.key)
    .map((candidate) => candidate.marker)
    .join("|");
  const pattern = new RegExp(`${definition.marker}([\\s\\S]*?)(?=${otherMarkers}|$)`, "i");

  return text.match(pattern)?.[1] ?? "";
}

function collectConclusionText(heading) {
  if (!heading) {
    return "";
  }

  const textParts = [];
  let sibling = heading.nextElementSibling;

  while (sibling && !/^H[1-3]$/i.test(sibling.tagName) && !CONCLUSION_BLOCK_TAGS.has(sibling.tagName)) {
    textParts.push(sibling.textContent);
    sibling = sibling.nextElementSibling;
  }

  return cleanText(textParts.join(" "));
}

function extractMetricValue(text) {
  const values = Array.from(String(text ?? "").matchAll(/[+-]?\d+(?:,\d{3})*(?:\.\d+)?%?/g), (match) => match[0])
    .filter((value) => value.trim() !== "");
  const percentValues = values.filter((value) => value.endsWith("%"));

  return percentValues[percentValues.length - 1] ?? values[values.length - 1] ?? "";
}

function extractMetricCardsFromTables(datasets) {
  const selectedColumn = chooseMetricCardColumn(datasets);
  const values = selectedColumn?.column?.values ?? [];

  if (values.length < 2) {
    return [];
  }

  const sorted = [...values].sort((left, right) => left.value - right.value);
  const min = sorted[0];
  const max = sorted[sorted.length - 1];
  const average = values.reduce((sum, entry) => sum + entry.value, 0) / values.length;
  const suffix = values.some((entry) => entry.isPercent) ? "%" : "";

  return [
    { key: "average-achievement", label: "平均达成率", value: `${average.toFixed(1)}${suffix}`, body: selectedColumn.column.header },
    { key: "lowest-dealer", label: "最低门店", value: min.display, body: min.label },
    { key: "highest-dealer", label: "最高门店", value: max.display, body: max.label },
    { key: "gap", label: "差距", value: `${(max.value - min.value).toFixed(1)}${suffix}`, body: `${max.label} - ${min.label}` }
  ];
}

function chooseMetricCardColumn(datasets) {
  const columns = datasets.flatMap((dataset) =>
    dataset.numericColumns.map((column) => ({
      column,
      hasPercent: column.values.some((entry) => entry.isPercent),
      matchedByPattern: BAR_METRIC_PATTERNS.some((pattern) => pattern.test(column.header))
    }))
  );
  const matchedColumn = columns.find((entry) => entry.matchedByPattern);
  const percentColumn = columns.find((entry) => entry.hasPercent);

  return matchedColumn ?? percentColumn ?? columns[0] ?? null;
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

  const gapLikeColumns = dataset.numericColumns.filter((column) =>
    GAP_METRIC_PATTERNS.some((pattern) => pattern.test(column.header))
  );

  if (gapLikeColumns.length >= 2) {
    models.push({
      id: `analysis-chart-${tableIndex}-heatmap`,
      type: "heatmap",
      title: "差距热力图",
      labels: dataset.rows.map((row) => row.label),
      metrics: gapLikeColumns.map((column) => column.header),
      values: gapLikeColumns.flatMap((column, columnIndex) =>
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
  const matchedColumn = columns.find((column) => BAR_METRIC_PATTERNS.some((pattern) => pattern.test(column.header)));
  const percentColumn = columns.find((column) => column.values.some((entry) => entry.isPercent));

  return matchedColumn ?? percentColumn ?? columns[0] ?? null;
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
