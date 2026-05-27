# Analytics Chart And Empty-State Repair Design

## Context

The chat UI renders analysis replies from `RuleBasedAnalyticsService` as Markdown. Analysis replies can include HTML tables, Mermaid charts, plain-text fallback bars, and follow-up questions. Recent chart work added Mermaid chart/code switching in `AssistantMessage.vue` and chart styles in `frontend/src/style.css`.

The reported issues span backend and frontend:

- Long raw Mermaid labels make axis labels overlap.
- Chart blocks look like separate nested cards inside assistant bubbles.
- Some scenarios still produce confident "best practice", benchmark, replication, or uplift conclusions when the useful metric is 0.
- Empty, all-zero, denominator-zero, and render-error states are not explicit enough.

This design covers all built-in analysis scenarios:

- Target achievement
- Opportunity funnel
- Sales follow-up
- Campaign performance
- Lead source
- Dealer benchmark

## Goals

1. Make charts readable inside assistant messages without severe label collisions.
2. Prevent low-confidence data from producing confident best-practice or replication conclusions.
3. Represent no-data, denominator-zero, all-zero, insufficient-sample, and chart-render-error states explicitly.
4. Keep the current Markdown/Mermaid architecture.
5. Add regression tests so future scenario changes cannot reintroduce misleading conclusions.
6. Add lightweight observability for data-quality decisions and chart suppression.

## Non-Goals

- Replacing Mermaid with a custom chart library.
- Redesigning the whole chat page.
- Changing the external HTTP API contract.
- Rewriting all scenario copy beyond the confidence and abnormal-state handling needed for this fix.

## Recommended Approach

Use a two-layer repair:

1. Backend data-quality gating in `RuleBasedAnalyticsService`.
2. Frontend chart presentation, empty-state, and failure-state improvements in the existing Markdown/Mermaid renderer.

This prevents misleading business conclusions at the source while improving the chart surface where the user sees it.

## Backend Data-Quality Contract

### Data-Quality States

Every scenario must classify its data before building conclusions, charts, attributions, or recommendations.

- `NO_DATA`: no matching rows after scope and date filters.
- `DENOMINATOR_ZERO`: rows exist, but no comparable unit has a positive denominator for the primary rate.
- `ALL_ZERO_SIGNAL`: comparable units exist, denominators are usable where needed, but the primary signal is zero across all valid units.
- `INSUFFICIENT_SAMPLE`: usable signal exists, but the valid sample does not meet the scenario threshold for ranking or practice extraction.
- `NORMAL`: valid signal meets the threshold for the intended ranking or comparison.

Mixed denominator quality is not a separate final state. If some units have zero denominators and at least one unit is valid, exclude zero-denominator units from ranking/charting, include an "excluded units" table row, and then classify the remaining valid units.

### Scenario Signal Table

| Scenario | Comparable unit | Primary signal | Numerator | Denominator | Ranking basis | Normal threshold | Insufficient threshold behavior |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Target achievement | Dealer aggregate from targets grouped by dealer code/name/city | Achievement rate and target gap | Sum of `opportunityWonCount` | Sum of `asKTarget` | Risk ranking: achievement rate ascending. Positive performer reference: achievement rate descending. | At least 2 valid dealers for lowest/highest comparison; at least 3 valid dealers and spread >= 5 percentage points for any practice extraction. | With 1 valid dealer, report factual achievement only and suppress ranking/benchmark wording. |
| Opportunity funnel | Opportunity records in current scope | Funnel volume, stage distribution, win rate, high-probability open count | Won count for win rate; stage counts for distribution | Total opportunity count | Bottleneck basis: largest stage count. Lead-source basis: largest source count. | At least 3 opportunities for bottleneck language; at least 2 stages or an explicit single-stage concentration statement. | With 1-2 opportunities, report counts only and say the sample is too small for bottleneck diagnosis. |
| Sales follow-up | Dealer task aggregate grouped by dealer name | Backlog or overdue pressure | Open plus overdue task count, and overdue count where shown | Total task count | Backlog rate descending, then backlog count descending. | At least 2 valid dealer task groups and at least 3 total tasks for dealer ranking. | With one dealer group or fewer than 3 tasks, report task counts only and suppress highest-backlog ranking language. |
| Campaign performance | Campaign record | Attainment rate | `actualOpportunityCount` | `totalNewCustomerTarget` | Attainment rate descending. | At least 2 valid campaigns for top/below-average comparison; at least 3 valid campaigns and best attainment > 0 for replication language. | With 1 valid campaign, report factual attainment only. With 2 valid campaigns, allow comparison but no playbook extraction. |
| Lead source | Lead-source aggregate grouped by `leadSource` | Lead volume and conversion rate | Converted lead count | Lead count | Volume ranking by lead count; conversion ranking by conversion rate among sources with lead count > 0. | At least 2 sources and at least 3 leads for source comparison; at least one converted lead for conversion-quality language. | With one source or fewer than 3 leads, report source counts only. If converted count is 0 across all sources, suppress "best conversion source". |
| Dealer benchmark | Dealer aggregate from targets grouped by dealer code/name/city | Achievement-rate spread | Sum of `opportunityWonCount` | Sum of `asKTarget` | Achievement rate descending, spread between highest and lowest. | At least 2 valid dealers and spread >= 1 percentage point for comparison; at least 3 valid dealers, spread >= 5 percentage points, and best rate > 0 for benchmark/practice language. | With 1 valid dealer or zero spread, report factual achievement only and suppress benchmark wording. |

### Threshold Strategy

Use a common helper, with scenario overrides only where the table above requires them:

- `validComparableUnits`: units with denominator > 0 when the primary signal is rate-based.
- `totalRows`: matching raw records after filters.
- `nonZeroSignalUnits`: valid units where the primary signal value > 0.
- `spreadPercentagePoints`: max primary rate minus min primary rate, for comparative rate scenarios.

Default thresholds:

- Ranking requires `validComparableUnits >= 2`.
- Practice extraction requires `validComparableUnits >= 3`, `nonZeroSignalUnits >= 1`, and `spreadPercentagePoints >= 5` for rate comparisons.
- Count-only funnel diagnosis requires `totalRows >= 3`.
- Charting xychart bars requires at least 2 plotted points unless the chart is explicitly a single-value KPI, which the current implementation is not.
- Pie charts require total count > 0 and at least 1 nonzero slice. If there is exactly one nonzero slice, copy must describe concentration, not comparison.

## Low-Confidence Narrative Policy

### Template-Only Low-Confidence Replies

`NO_DATA`, `DENOMINATOR_ZERO`, `ALL_ZERO_SIGNAL`, and `INSUFFICIENT_SAMPLE` must be generated through shared templates, not scenario-specific free-form prose.

Template inputs:

- `language`
- `scenario`
- `scopeSummary`
- `dataQualityState`
- `observedRows`
- `validComparableUnits`
- `requiredComparableUnits`
- `primaryMetricLabel`
- `primaryNumerator`
- `primaryDenominator`
- `excludedUnits`
- `chartSuppressionReason`
- `diagnosticFollowUps`

Template IDs:

- `LOW_CONFIDENCE_NO_DATA`
- `LOW_CONFIDENCE_DENOMINATOR_ZERO`
- `LOW_CONFIDENCE_ALL_ZERO_SIGNAL`
- `LOW_CONFIDENCE_INSUFFICIENT_SAMPLE`
- `LOW_CONFIDENCE_MIXED_DENOMINATOR`

Required sections remain:

- Conclusion
- Data Support
- Short Analysis or localized equivalent
- Recommendations
- Follow-up questions

The conclusion must say what is known and what cannot be inferred. It must not rank units or name a "winner" when the state is low-confidence.

### Approved Low-Confidence Copy Patterns

Use these intent patterns in localized copy:

- Records exist, but the key metric has no effective signal.
- The current scope does not support reliable ranking.
- The denominator is missing or zero, so rate-based comparison is unavailable.
- The sample is too small for benchmark or practice extraction.
- Broaden the scope, verify data import, or switch to a related metric.

### Banned Confidence Language

Low-confidence template output must not contain Chinese or English equivalents of:

- best
- top performer
- benchmark
- replicate
- playbook
- best practice
- winning approach
- uplift estimate
- outperforms
- worth promoting
- horizontal rollout

Tests should check a banned-token list against low-confidence replies in both Chinese and English. The banned list can be centralized in tests and mirrored in a code comment near the templates.

## Chart Suppression Policy

Suppress both Mermaid and plain-text fallback bars when any of these are true:

- `dataQualityState` is `NO_DATA`.
- `dataQualityState` is `DENOMINATOR_ZERO`.
- `dataQualityState` is `ALL_ZERO_SIGNAL`.
- `dataQualityState` is `INSUFFICIENT_SAMPLE` and the chart would imply ranking or comparison.
- Xychart plotted point count is less than 2.
- All xychart plotted values are 0.
- All pie values are 0 or the pie total is 0.
- Sanitized chart labels are empty after cleanup.
- Mixed denominator data leaves fewer than the scenario's minimum valid plotted units.

If charting is suppressed, the backend should emit a standardized empty-chart marker after the data table, unless the state is `NO_DATA` and no chart region is useful.

The empty-chart marker must not be emitted in normal replies.

## Frontend Empty-State Contract

### Markdown Contract

Add support for a dedicated fenced block:

````markdown
```chart-empty
reason: ALL_ZERO_SIGNAL
title: No visual signal
body: Records exist in this scope, but the key metric is 0, so no ranking chart is shown.
```
````

Rules:

- The fence language is `chart-empty`.
- Supported keys are `reason`, `title`, and `body`.
- Unknown keys are ignored.
- Values are rendered as text, escaped, and never treated as HTML.
- `reason` maps to `data-chart-empty-reason`.
- If `title` or `body` is missing, the renderer falls back to localized frontend dictionary strings.

Dictionary keys:

- `chartEmptyTitle`
- `chartEmptyBody`
- `chartEmptyNoDataBody`
- `chartEmptyDenominatorZeroBody`
- `chartEmptyAllZeroBody`
- `chartEmptyInsufficientSampleBody`

### Visual Contract

Render the fence as:

- Wrapper class: `.analysis-empty-chart`
- Title class: `.analysis-empty-chart-title`
- Body class: `.analysis-empty-chart-body`
- Optional reason attribute: `data-chart-empty-reason`

Visual requirements:

- Same visual weight as a subtle note inside an assistant answer, not a card inside a card.
- Border: soft dashed or low-contrast solid border.
- Background: aligned with assistant message surface, not a high-contrast warning panel.
- Minimum height: enough to reserve the missing chart region, but shorter than a normal chart, around 96-120px.
- Accessible status role: `role="status"`.
- No icon is required; if an icon is added later, use an existing icon library and keep it secondary.

### Copy Contract

Default English strings:

- Title: `No visual signal`
- Body: `The chart is hidden because the available data is not reliable enough for a ranked visualization.`

Default Chinese strings should carry the same meaning:

- Title meaning: no visual signal yet.
- Body meaning: the chart is hidden because current data does not support a reliable ranked visualization.

The copy must be factual and diagnostic. It must not imply failure of the system or poor performance by a dealer unless the backend state is normal and the data supports it.

## Chart Label Policy

### Label Generation

Backend chart labels must be generated with a helper instead of passing raw names directly to Mermaid.

Inputs:

- `entityType`: `dealer`, `campaign`, `stage`, `source`, or `generic`
- `rawLabel`
- `stableId`
- `ordinal`

Rules:

- Trim whitespace and collapse internal whitespace.
- Replace Mermaid-sensitive xychart characters with spaces or hyphens: comma, square brackets, double quote, angle brackets, line breaks.
- Remove remaining control characters.
- If the result is blank, use `Item N`.
- Deduplicate within one chart by appending ` #2`, ` #3`, and so on.
- Keep full raw labels in the data table.

### Length Limits

Use visible-code-point limits, not byte length:

- Dealer x-axis label: 10 visible characters.
- Campaign x-axis label: prefer the campaign ID suffix, max 12 visible characters.
- Stage label: 14 visible characters.
- Lead source label: 14 visible characters.
- Generic xychart label: 12 visible characters.
- Pie label: 16 visible characters.

If truncation is needed, preserve the start and suffix with `...` in the middle when a stable ID exists. Otherwise truncate the end with `...`.

Examples:

- `Campaign-2026-Beijing-May-001` -> `...May-001`
- `Dealer A Flagship Downtown` -> `Dealer A...`
- Empty label -> `Item 1`
- Duplicate `Website` labels -> `Website`, `Website #2`

### Point Limits

Limit plotted categories to reduce label collisions:

- Target achievement: bottom 3 plus top 3, deduplicated, max 6.
- Dealer benchmark: bottom 3 plus top 3, deduplicated, max 6.
- Campaign performance: top 5 by valid attainment rate.
- Sales follow-up: top 5 by backlog pressure.
- Opportunity funnel pie: top 5 stages by count plus `Other` if more remain.
- Lead source pie: top 5 sources by count plus `Other` if more remain.

The table should still contain the core summary rows. It does not need to list every plotted category unless the current scenario already does.

## Frontend Chart Container Design

Keep the existing `.mermaid-block`, toolbar, and chart/code toggle behavior, but restyle the chart as an embedded analysis figure:

- Use a quieter background aligned with assistant bubbles.
- Reduce border contrast and avoid a separate-card feel.
- Keep stable min-height for loading and rendered states to prevent layout jumps.
- Preserve horizontal scrolling for wide SVGs.
- Keep the toolbar compact and visually secondary.
- Keep the code view accessible for debugging.

Mermaid SVG constraints:

- `.mermaid-chart` keeps `overflow-x: auto`.
- Rendered SVG uses `max-width: 100%` and `height: auto`.
- Do not use CSS transforms that shrink text or create unreadable labels.
- Axis and title text use existing text tokens for contrast.

## Error Handling

- Empty data stays localized and structured.
- Denominator-zero data is not treated as valid 0% performance.
- All-zero primary signal is treated as no effective signal, not as best/worst ranking.
- Mermaid render errors show an accessible status with retry and source view.
- Chart source view remains available after render failure.
- Sanitized labels must avoid Mermaid syntax breakage.
- Mixed denominator data includes an excluded-unit count in the data table.

## Observability

Add lightweight observability without introducing new infrastructure.

Backend structured logs:

- Log once per `plan()` when data quality is not `NORMAL`.
- Level: `DEBUG` so routine low-confidence diagnostics stay out of normal logs.
- Fields: `scenario`, `language`, `scopeSummary`, `dataQualityState`, `observedRows`, `validComparableUnits`, `requiredComparableUnits`, `excludedUnits`, `primaryMetric`, `chartSuppressed`, `chartSuppressionReason`.
- Do not log API keys, prompts beyond the scenario/scope summary, or full row payloads.

Backend grounded reference:

- Include a short `Data Quality:` line in the grounded reference so tests and debugging can see why a chart or ranking was suppressed.
- Include `Chart Suppressed:` and the reason when applicable.

Frontend render observability:

- Mermaid render failure should not spam the console in production.
- In development only, a single `console.warn` may include the block ID and error message.
- User-facing render errors remain the accessible retry/source-view state.

Future metrics hook:

- Keep helper boundaries suitable for later Micrometer counters, such as `analytics.low_confidence_reply` and `analytics.chart_suppressed`, but do not add metrics infrastructure in this change.

## Data Flow

1. User asks an analysis question.
2. `RuleBasedAnalyticsService.plan()` detects scenario and scope.
3. Scenario fetches data from `DataQueryService` or repositories.
4. Scenario computes primary metrics and data-quality state.
5. Low-confidence states return shared template replies and, when useful, a `chart-empty` fence.
6. Normal states build tables, safe chart labels, Mermaid blocks, attributions, recommendations, and follow-ups.
7. Markdown renderer converts Mermaid fences into `.mermaid-block` and `chart-empty` fences into `.analysis-empty-chart`.
8. `AssistantMessage.vue` renders Mermaid asynchronously and exposes chart/code/retry controls.
9. CSS keeps charts and empty states visually integrated with the assistant bubble.

## Testing Strategy

### Backend Tests

Extend `RuleBasedAnalyticsServiceTest`:

- Keep one normal-path test for each scenario.
- Add low-confidence tests for all six scenarios.
- For all-zero or denominator-zero inputs, assert the reply:
  - contains the standard report sections,
  - contains relevant raw counts,
  - does not contain Mermaid fences or plain fallback bars when charting is suppressed,
  - contains `chart-empty` when a chart region should explain suppression,
  - does not contain banned confidence terms,
  - contains an abnormal, denominator, no-effective-signal, or insufficient-sample explanation,
  - includes data-quality and chart-suppression metadata in the grounded reference.
- Add mixed-denominator tests for rate-based scenarios to verify invalid units are excluded and counted.
- Add chart-label tests through scenario output for truncation, sanitization, deduplication, and point limits.

### Frontend Tests

Extend `AssistantMessage.spec.js` and Markdown tests:

- Mermaid blocks still render chart/code toggles.
- Failed Mermaid render shows retry and source view.
- `chart-empty` fences render `.analysis-empty-chart` with escaped title/body.
- Missing `chart-empty` title/body uses dictionary fallback text.
- Long source remains available in code view.
- Chart blocks and empty states expose stable class names for CSS.

### Visual Verification

Run the app and inspect at least:

- Normal xychart reply with long dealer/campaign labels.
- Normal pie reply with many lead sources or stages.
- All-zero campaign performance reply.
- Denominator-zero target achievement reply.
- Mermaid render-error state by forcing invalid source in a test or fixture.

## Acceptance Criteria

- All built-in scenarios avoid best-practice or replication conclusions when the primary signal is empty, denominator-zero, all-zero, or insufficient for ranking.
- All-zero quantitative results do not produce misleading bar charts.
- Suppressed charts show the standardized empty-chart state when useful.
- Normal scenarios still produce structured reports with tables and charts.
- Common long dealer, campaign, stage, and source labels no longer severely overlap.
- Chart containers and chart-empty states look like part of the assistant answer.
- Mermaid render failure and no-data paths are explicit and recoverable.
- Low-confidence decisions and chart suppression are visible in logs and grounded references.
- Regression tests cover backend low-confidence behavior, chart-label policy, and frontend Mermaid/empty states.

## Implementation Notes

- Keep changes localized to `RuleBasedAnalyticsService`, its tests, `markdown.js`, `AssistantMessage.vue` only if needed, `messages.js`, and `style.css`.
- Avoid broad UI redesign and unrelated refactors.
- Prefer shared helpers for data-quality checks, chart labels, chart suppression, and low-confidence templates.
- Preserve localized Chinese and English report structure.
- Use ASCII template IDs and enum names even when localized copy is Chinese.
