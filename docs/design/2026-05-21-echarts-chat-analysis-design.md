# ECharts Chat Analysis Design

## Context

The frontend chat area currently renders assistant replies as Markdown, preserved HTML tables, and Mermaid chart blocks. The project already has a Vue/Vite frontend with global styling in `frontend/src/style.css`, message rendering in `frontend/src/components/chat/AssistantMessage.vue`, and Markdown conversion in `frontend/src/utils/markdown.js`.

The user added `background.jpg` at the repository root and wants it used as the frontend chat-area background. The current replies contain many paragraphs and tables, so users may miss the most important findings. The target experience is a clearer analysis view: highlighted conclusion cards, charts with direct labels for key stores or values, interactive bar charts with tooltip/filter/sort controls, and optional heatmap/radar views for gap and ranking analysis.

## Goals

- Use `background.jpg` as the chat area's visual background while preserving text and table readability.
- Make `## 核心结论` / `## Conclusion` visually scannable through highlighted metric cards.
- Surface concrete headline metrics such as average achievement, lowest dealer, highest dealer, and gap as first-scan cards.
- Keep original Markdown, tables, and Mermaid output visible and safe.
- Add ECharts-powered interactive analysis charts in the frontend.
- Support bar-chart tooltip, store filtering, sorting, key-value annotation, and stronger chart scale/contrast.
- Show heatmaps for dealer achievement gaps or ranking/gap tables when the table structure is suitable.
- Show radar charts for region/dealer comparisons only when enough multi-metric data is present.
- Avoid backend/API changes in this iteration.

## Non-Goals

- No Spring backend, prompt, API, authentication, or data-import changes.
- No replacement of Mermaid; Mermaid remains supported.
- No forced chart output when table parsing is ambiguous or data is too sparse.
- No broad layout rewrite outside the chat message and chat screen surfaces.
- No new landing page or marketing-style UI.

## Architecture

Add ECharts as a frontend dependency and keep the enhancement layer inside the chat frontend.

Planned frontend units:

- `frontend/src/utils/analysisCharts.js`
  - Parses rendered table data into structured datasets.
  - Detects label columns, numeric columns, percent values, rank values, and gap values.
  - Decides whether bar, heatmap, or radar charts are viable.
  - Builds plain chart models independent of Vue and ECharts.

- `frontend/src/components/chat/AnalysisChart.vue`
  - Owns ECharts instance lifecycle.
  - Renders the chart canvas and compact controls.
  - Supports chart type, sort order, Top N, and dealer/store filter controls where applicable.
  - Disposes the chart on unmount and reinitializes on data/config changes.

- `frontend/src/components/chat/AssistantMessage.vue`
  - Keeps current Markdown/Mermaid behavior.
  - After message HTML is mounted and rendering is complete, scans the message DOM for supported tables and conclusion sections.
  - Mounts analysis chart components near the source table through Vue-managed state rather than mutating raw unsafe HTML.
  - Skips chart creation while a message is still streaming.

- `frontend/src/style.css`
  - Adds chat background image treatment, overlay, conclusion-card styles, table emphasis, chart panels, chart controls, and responsive chart sizing.

## Visual Design

The chat screen uses `background.jpg` as a subtle background image on the chat content area, with a translucent overlay and existing blue-gray surface tokens to protect contrast. Message cards remain readable, and tables/charts sit on solid or near-solid surfaces.

The conclusion section becomes a compact card group:

- Each bullet under `## 核心结论` / `## Conclusion` maps to one card.
- Cards emphasize detected numbers, percentages, dealer names, and highest/lowest language.
- Cards use restrained business-analysis styling, not decorative hero styling.
- When the data includes achievement statistics, the preferred card roles are average achievement, lowest dealer, highest dealer, and gap.
- Example only: average achievement `112.6%`, lowest dealer `91.1%`, highest dealer `172.3%`, and gap `81.2%`.
- These example values are not fixed requirements and must not be hard-coded. Real card values come from the current parsed reply/table, and missing roles are omitted rather than fabricated.
- The example above defines the visual intent: users should see the main result before reading the narrative.
- Metric names, values, and dealer labels should have separate text weights so the number is the strongest visual element.
- Cards should be arranged in a responsive grid with stable dimensions, avoiding layout shift between short and long dealer names.

Tables keep their original data and structure. Enhancement styles mark the most important cells:

- Highest and lowest numeric values in comparable columns.
- Large positive or negative gaps.
- Rank or achievement columns where labels indicate ranking, gap, attainment, achievement, completion, conversion, or percentage.

ECharts chart panels appear near the relevant table and should have stronger presence than the current Mermaid blocks:

- Increase chart panel height and width within the assistant message column so the chart reads as a primary analysis object, not a small attachment.
- Put chart panels on a distinct surface with clearer spacing above and below, separating charts from dense body text and tables.
- Use differentiated colors for normal, highest, lowest, and selected bars.
- Add direct mark labels to the highest and lowest bars, and to the largest positive/negative gap when available.
- Use chart subtitles or compact badges to name the metric being charted.
- Keep axis labels readable; when labels are long, rotate, truncate, or use tooltip disclosure instead of shrinking the font.

Bar charts prioritize store comparisons and direct labels for the largest or weakest value. Heatmaps appear for dealer gap/ranking matrices. Radar charts appear for multi-metric region/dealer comparison tables.

## Interaction Design

Bar chart controls:

- Tooltip on hover with dealer/store name, metric name, and original formatted value.
- Sort: default, ascending, descending.
- Top N: all, 5, 10 where data volume supports it.
- Store/dealer filter: lightweight text filter or compact selector based on parsed labels.
- Direct annotation for the highest, lowest, or largest-gap item.
- Highest and lowest bars use distinct colors and visible labels without requiring hover.
- The default chart height should be large enough for labels and annotations to avoid crowding.

Heatmap controls:

- Tooltip with row label, metric, and value.
- Color scale centered around gap severity when negative/positive gap values exist.
- No heatmap if there are fewer than two comparable rows or two numeric dimensions.

Radar controls:

- Tooltip with region/dealer and metric values.
- Legend for compared regions/dealers.
- Radar is hidden unless at least three numeric metrics and two comparable entities are available.

## Data Flow

1. Backend returns the existing assistant reply.
2. `renderMarkdownLite` converts Markdown to safe HTML and preserves allowed table tags.
3. `AssistantMessage.vue` renders the message.
4. Once a message is not streaming, enhancement logic scans headings, lists, and tables in the message DOM.
5. Conclusion bullets are represented in component state as conclusion-card data.
6. Supported tables are parsed by `analysisCharts.js`.
7. Parsed chart models are passed to `AnalysisChart.vue`.
8. ECharts instances render and update from Vue props.
9. On message rerender, locale change, or component unmount, chart instances are cleaned up.

## Parsing Rules

Table parsing is conservative:

- Use DOM table rows and cells, not string parsing of HTML.
- Detect the primary label column from header names such as dealer, store, region, campaign, source, name, 门店, 经销商, 区域, 活动, 来源, 名称.
- Detect numeric values from plain numbers, comma-separated numbers, percentages, and signed deltas.
- Preserve original displayed text for tooltips.
- Ignore columns that are mostly empty or non-numeric.
- Prefer columns whose headers imply achievement, completion, conversion, rate, ranking, rank, gap, difference, 达成, 完成, 转化, 比率, 排名, 差距, 差值.
- Require enough comparable rows before generating charts.
- If a table contains average, highest, lowest, and gap values, promote those values into conclusion-card candidates even when the narrative bullet text does not repeat every number.

## Degradation

- If ECharts fails to load or initialize, the original table and Mermaid chart remain visible.
- If parsing fails, no chart panel is inserted.
- If a message is streaming, enhancement waits until completion.
- If data is too sparse, chart panels are hidden rather than showing empty states.
- Development mode may log parsing or ECharts errors; production should avoid noisy UI errors.

## Testing

Use test-first implementation.

- `analysisCharts.js` unit tests:
  - Parses HTML table DOM into labels and numeric series.
  - Parses percentages, signed gaps, ranks, and comma-separated numbers.
  - Chooses a bar chart for dealer/store ranking data.
  - Chooses a heatmap for multi-column gap/ranking data.
  - Chooses a radar chart only for multi-metric comparisons.
  - Refuses charts for ambiguous or sparse data.

- `AssistantMessage.spec.js`:
  - Does not initialize analysis charts while streaming.
  - Creates analysis chart data when a supported table exists.
  - Keeps Mermaid controls working.
  - Cleans up chart instances on unmount.

- `AnalysisChart` component tests:
  - Initializes ECharts with expected option shape.
  - Updates when sort, Top N, or filter controls change.
  - Disposes the instance on unmount.

- Style tests:
  - Chat background image rules exist.
  - Conclusion-card, table-highlight, chart-panel, and chart-control styles exist.
  - Chart panel size, highest/lowest bar emphasis, and annotation styles exist.

Verification commands:

```bash
cd frontend
npm test
npm run build
```

## Acceptance Criteria

- `background.jpg` appears as the chat-area background without hurting readability.
- Core conclusions render as highlighted cards while preserving the original reply semantics.
- Achievement summaries can render metric cards for average achievement, lowest dealer, highest dealer, and gap when those values exist in the current reply/table; `112.6%`, `91.1%`, `172.3%`, and `81.2%` are examples only.
- Existing Markdown tables and Mermaid chart blocks still render.
- Supported table replies gain larger ECharts bar charts with tooltip, sorting, filtering, Top N, color differentiation, and visible highest/lowest annotations.
- Charts have clearer visual separation from body text and tables.
- Gap/ranking tables can render heatmaps when structurally suitable.
- Multi-metric region/dealer tables can render radar charts when structurally suitable.
- Sparse or ambiguous replies degrade to the existing table/Mermaid view.
- ECharts instances are cleaned up correctly.
- Focused tests and frontend build pass.
