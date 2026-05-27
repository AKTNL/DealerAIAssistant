# Analytics Chart And Empty-State Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all built-in analytics scenarios suppress misleading charts and confident recommendations when data is empty, all-zero, denominator-zero, or too small, while improving Mermaid chart readability and frontend empty/error states.

**Architecture:** Add shared data-quality, chart-label, chart-suppression, and low-confidence reply helpers inside `RuleBasedAnalyticsService`, then route every scenario through those helpers before normal narrative generation. Extend the Markdown renderer with a `chart-empty` fence and style both Mermaid charts and empty chart states as embedded analysis figures inside assistant messages.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, AssertJ, Vue 3, markdown-it, Mermaid, Vitest, Vue Test Utils, CSS.

---

## File Structure

- Modify: `backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java`
  - Owns scenario analysis, shared data-quality state, low-confidence templates, chart labels, chart suppression, and grounded-reference observability.
- Modify: `backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java`
  - Adds low-confidence scenario coverage, chart suppression assertions, mixed-denominator assertions, and chart-label assertions.
- Modify: `frontend/src/utils/markdown.js`
  - Parses `chart-empty` fenced blocks into safe, structured HTML.
- Modify: `frontend/src/utils/__tests__/markdown.spec.js`
  - Verifies `chart-empty` rendering and escaping.
- Modify: `frontend/src/components/chat/AssistantMessage.vue`
  - Adds localized fallback text for empty chart blocks if markdown output marks missing title/body, and development-only Mermaid render warnings if needed.
- Modify: `frontend/src/components/__tests__/AssistantMessage.spec.js`
  - Verifies empty chart state behavior alongside existing Mermaid behavior.
- Modify: `frontend/src/i18n/messages.js`
  - Adds `chartEmpty*` dictionary strings in Chinese and English.
- Modify: `frontend/src/style.css`
  - Restyles Mermaid chart blocks and adds `.analysis-empty-chart` styles.
- Read-only reference: `docs/design/2026-05-18-analytics-chart-empty-state-design.md`

---

### Task 1: Backend Low-Confidence Regression Tests

**Files:**
- Modify: `backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java`

- [ ] **Step 1: Add shared low-confidence assertion helpers**

Add these helpers near the bottom of `RuleBasedAnalyticsServiceTest`, before `scenarioCases()`:

```java
private static final List<String> LOW_CONFIDENCE_BANNED_TERMS = List.of(
        "best practice",
        "top performer",
        "benchmark for others",
        "replicate",
        "playbook",
        "winning playbook",
        "uplift",
        "outperforming",
        "worth promoting",
        "\u6700\u4f73",
        "\u6807\u6746",
        "\u590d\u7528",
        "\u6a2a\u5411\u590d\u5236",
        "\u6253\u6cd5",
        "\u9884\u8ba1\u53ef"
);

private void assertLowConfidenceReply(AnalyticsPlan plan, String expectedState) {
    assertThat(plan.fallbackReply()).contains("##");
    assertThat(plan.fallbackReply()).contains("<table>");
    assertThat(plan.fallbackReply()).contains("```chart-empty");
    assertThat(plan.fallbackReply()).doesNotContain("```mermaid");
    assertThat(plan.groundedReference()).contains("Data Quality: " + expectedState);
    assertThat(plan.groundedReference()).contains("Chart Suppressed:");
    assertThat(plan.fallbackReply().toLowerCase(Locale.ROOT))
            .doesNotContain(LOW_CONFIDENCE_BANNED_TERMS.stream()
                    .map(term -> term.toLowerCase(Locale.ROOT))
                    .toArray(String[]::new));
}
```

Also add `import java.util.Locale;` if it is not present.

- [ ] **Step 2: Run the backend test class to verify the helper compiles**

Run:

```powershell
mvn "-Dfrontend.skip=true" "-Dtest=RuleBasedAnalyticsServiceTest" test
```

Expected: compilation fails because the helper expects `chart-empty`, `Data Quality`, and `Chart Suppressed` behavior that production code does not emit yet.

- [ ] **Step 3: Add all-zero and denominator-zero tests for rate-based scenarios**

Add these tests after `planAggregatesDealerTargetRowsBeforeRankingDealers()`:

```java
@Test
void targetAchievementSuppressesRankingWhenAllTargetsHaveZeroDenominator() {
    when(dealerRepository.findAll()).thenReturn(List.of(
            new Dealer("D001", "Dealer A", "Beijing", "Group A"),
            new Dealer("D002", "Dealer B", "Beijing", "Group A")
    ));
    when(targetRepository.findAll()).thenReturn(List.of(
            new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 5, 0, 0),
            new Target("D002", "Dealer B", "Beijing", "Group A", "Model X", 2026, 5, 0, 0)
    ));

    AnalyticsPlan plan = service.plan("target achievement for Beijing", "en");

    assertThat(plan.fallbackReply()).contains("Target achievement");
    assertThat(plan.fallbackReply()).contains("Observed rows");
    assertThat(plan.fallbackReply()).contains("Primary denominator");
    assertLowConfidenceReply(plan, "DENOMINATOR_ZERO");
}

@Test
void campaignPerformanceSuppressesBestPracticeWhenAllAttainmentIsZero() {
    when(campaignRepository.findAll()).thenReturn(List.of(
            new Campaign("CAMP-2026-BJ-001", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                    "Launch", LocalDate.of(2026, 5, 1), 0, 10),
            new Campaign("CAMP-2026-BJ-002", "D002", "Dealer B", "Beijing", "Group A", "Model X",
                    "Roadshow", LocalDate.of(2026, 5, 2), 0, 12)
    ));

    AnalyticsPlan plan = service.plan("campaign performance for Beijing", "en");

    assertThat(plan.fallbackReply()).contains("Campaign performance");
    assertThat(plan.fallbackReply()).contains("Primary numerator");
    assertThat(plan.fallbackReply()).contains("0");
    assertLowConfidenceReply(plan, "ALL_ZERO_SIGNAL");
}

@Test
void dealerBenchmarkSuppressesBenchmarkWhenOnlyOneValidDealerExists() {
    when(dealerRepository.findAll()).thenReturn(List.of(
            new Dealer("D001", "Dealer A", "Beijing", "Group A")
    ));
    when(targetRepository.findAll()).thenReturn(List.of(
            new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 5, 10, 8)
    ));

    AnalyticsPlan plan = service.plan("dealer benchmark comparison for Beijing", "en");

    assertThat(plan.fallbackReply()).contains("Valid comparable units");
    assertLowConfidenceReply(plan, "INSUFFICIENT_SAMPLE");
}
```

- [ ] **Step 4: Add low-sample tests for count-based scenarios**

Add these tests near existing opportunity, task, and lead tests:

```java
@Test
void opportunityFunnelSuppressesBottleneckDiagnosisWhenSampleIsTooSmall() {
    when(opportunityRepository.findAll()).thenReturn(List.of(
            new Opportunity("O1", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                    "Negotiation", "Website", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 20), 70),
            new Opportunity("O2", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                    "Won", "Website", LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 21), 100)
    ));

    AnalyticsPlan plan = service.plan("opportunity funnel for Beijing", "en");

    assertThat(plan.fallbackReply()).contains("Opportunity funnel");
    assertLowConfidenceReply(plan, "INSUFFICIENT_SAMPLE");
}

@Test
void salesFollowUpSuppressesHighestBacklogRankingWhenOnlyOneDealerHasTasks() {
    when(taskRepository.findAll()).thenReturn(List.of(
            new Task("T1", "D001", "Dealer A", "Beijing", "Group A", "O1",
                    "Open", LocalDate.of(2026, 5, 5)),
            new Task("T2", "D001", "Dealer A", "Beijing", "Group A", "O2",
                    "Overdue", LocalDate.of(2026, 5, 6))
    ));

    AnalyticsPlan plan = service.plan("sales follow-up for Beijing", "en");

    assertThat(plan.fallbackReply()).contains("Sales follow-up");
    assertLowConfidenceReply(plan, "INSUFFICIENT_SAMPLE");
}

@Test
void leadSourceSuppressesBestConversionWhenNoLeadConverted() {
    when(leadRepository.findAll()).thenReturn(List.of(
            new Lead("L1", "D001", "Dealer A", "Beijing", "Group A",
                    "Website", "New", "Model X", LocalDate.of(2026, 5, 1), false),
            new Lead("L2", "D001", "Dealer A", "Beijing", "Group A",
                    "Retail", "New", "Model X", LocalDate.of(2026, 5, 2), false),
            new Lead("L3", "D002", "Dealer B", "Beijing", "Group A",
                    "Website", "New", "Model Y", LocalDate.of(2026, 5, 3), false)
    ));

    AnalyticsPlan plan = service.plan("lead source analysis for Beijing", "en");

    assertThat(plan.fallbackReply()).contains("Lead source analysis");
    assertLowConfidenceReply(plan, "ALL_ZERO_SIGNAL");
}
```

Keep each test focused on one state.

- [ ] **Step 5: Run backend tests and confirm red state**

Run:

```powershell
mvn "-Dfrontend.skip=true" "-Dtest=RuleBasedAnalyticsServiceTest" test
```

Expected: tests fail because production replies still emit normal rankings, Mermaid charts, or lack data-quality metadata.

---

### Task 2: Backend Data-Quality Model And Low-Confidence Templates

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java`
- Test: `backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java`

- [ ] **Step 1: Add logger and helper records/enums**

In `RuleBasedAnalyticsService.java`, add imports:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

Add a logger after the class declaration:

```java
private static final Logger LOGGER = LoggerFactory.getLogger(RuleBasedAnalyticsService.class);
```

Add these records/enums near existing internal records:

```java
private enum DataQualityState {
    NO_DATA,
    DENOMINATOR_ZERO,
    ALL_ZERO_SIGNAL,
    INSUFFICIENT_SAMPLE,
    NORMAL
}

private enum ChartSuppressionReason {
    NONE,
    NO_DATA,
    DENOMINATOR_ZERO,
    ALL_ZERO_SIGNAL,
    INSUFFICIENT_SAMPLE,
    TOO_FEW_POINTS,
    EMPTY_LABELS
}

private record DataQualityContext(
        DataQualityState state,
        String scenarioLabel,
        String primaryMetricLabel,
        int observedRows,
        int validComparableUnits,
        int requiredComparableUnits,
        int primaryNumerator,
        int primaryDenominator,
        int excludedUnits,
        ChartSuppressionReason chartSuppressionReason
) {
    boolean suppressChart() {
        return chartSuppressionReason != ChartSuppressionReason.NONE;
    }
}
```

- [ ] **Step 2: Add quality classifier helpers**

Add these methods near `percentage()`:

```java
private DataQualityContext classifyRateQuality(
        String scenarioLabel,
        String primaryMetricLabel,
        int observedRows,
        int validComparableUnits,
        int requiredComparableUnits,
        int primaryNumerator,
        int primaryDenominator,
        int excludedUnits,
        double spreadPercentagePoints,
        int practiceRequiredUnits
) {
    if (observedRows == 0) {
        return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
                requiredComparableUnits, primaryNumerator, primaryDenominator, excludedUnits,
                DataQualityState.NO_DATA, ChartSuppressionReason.NO_DATA);
    }
    if (primaryDenominator <= 0 || validComparableUnits == 0) {
        return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
                requiredComparableUnits, primaryNumerator, primaryDenominator, excludedUnits,
                DataQualityState.DENOMINATOR_ZERO, ChartSuppressionReason.DENOMINATOR_ZERO);
    }
    if (primaryNumerator <= 0) {
        return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
                requiredComparableUnits, primaryNumerator, primaryDenominator, excludedUnits,
                DataQualityState.ALL_ZERO_SIGNAL, ChartSuppressionReason.ALL_ZERO_SIGNAL);
    }
    if (validComparableUnits < requiredComparableUnits
            || validComparableUnits < practiceRequiredUnits
            || spreadPercentagePoints < 5.0) {
        return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
                requiredComparableUnits, primaryNumerator, primaryDenominator, excludedUnits,
                DataQualityState.INSUFFICIENT_SAMPLE, ChartSuppressionReason.INSUFFICIENT_SAMPLE);
    }
    return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
            requiredComparableUnits, primaryNumerator, primaryDenominator, excludedUnits,
            DataQualityState.NORMAL, ChartSuppressionReason.NONE);
}

private DataQualityContext classifyCountQuality(
        String scenarioLabel,
        String primaryMetricLabel,
        int observedRows,
        int validComparableUnits,
        int requiredComparableUnits,
        int primaryNumerator,
        int primaryDenominator
) {
    if (observedRows == 0) {
        return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
                requiredComparableUnits, primaryNumerator, primaryDenominator, 0,
                DataQualityState.NO_DATA, ChartSuppressionReason.NO_DATA);
    }
    if (primaryNumerator <= 0) {
        return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
                requiredComparableUnits, primaryNumerator, primaryDenominator, 0,
                DataQualityState.ALL_ZERO_SIGNAL, ChartSuppressionReason.ALL_ZERO_SIGNAL);
    }
    if (observedRows < requiredComparableUnits || validComparableUnits < 2) {
        return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
                requiredComparableUnits, primaryNumerator, primaryDenominator, 0,
                DataQualityState.INSUFFICIENT_SAMPLE, ChartSuppressionReason.INSUFFICIENT_SAMPLE);
    }
    return quality(scenarioLabel, primaryMetricLabel, observedRows, validComparableUnits,
            requiredComparableUnits, primaryNumerator, primaryDenominator, 0,
            DataQualityState.NORMAL, ChartSuppressionReason.NONE);
}

private DataQualityContext quality(
        String scenarioLabel,
        String primaryMetricLabel,
        int observedRows,
        int validComparableUnits,
        int requiredComparableUnits,
        int primaryNumerator,
        int primaryDenominator,
        int excludedUnits,
        DataQualityState state,
        ChartSuppressionReason chartSuppressionReason
) {
    return new DataQualityContext(
            state,
            scenarioLabel,
            primaryMetricLabel,
            observedRows,
            validComparableUnits,
            requiredComparableUnits,
            primaryNumerator,
            primaryDenominator,
            excludedUnits,
            chartSuppressionReason
    );
}
```

- [ ] **Step 3: Add low-confidence reply builder**

Add this method near `buildNoDataReply()`:

```java
private String buildLowConfidenceReply(String language, AnalysisScope scope, DataQualityContext quality) {
    boolean isZh = "zh".equals(language);
    String scopeSummary = scope.summary(language);
    String conclusion = isZh
            ? "- \u5f53\u524d\u8303\u56f4\u6709\u8bb0\u5f55\uff0c\u4f46 %s \u4fe1\u53f7\u4e0d\u8db3\uff0c\u6682\u4e0d\u652f\u6301\u53ef\u9760\u6392\u540d\u6216\u5b9e\u8df5\u63d0\u70bc\n- \u6570\u636e\u72b6\u6001\uff1a%s\uff0c\u5efa\u8bae\u5148\u6269\u5927\u8303\u56f4\u6216\u68c0\u67e5\u6570\u636e\u5b57\u6bb5\u914d\u7f6e"
                    .formatted(localizeTopicLabel(language, quality.scenarioLabel()), quality.state())
            : "- Records exist in %s, but %s does not have enough reliable signal for ranking or practice extraction.\n- Data quality state: %s. Broaden the scope or verify the metric configuration before using this result operationally."
                    .formatted(scopeSummary, quality.primaryMetricLabel(), quality.state());

    List<String[]> rows = new ArrayList<>();
    rows.add(new String[]{"Requested scope", scopeSummary});
    rows.add(new String[]{"Requested topic", localizeTopicLabel(language, quality.scenarioLabel())});
    rows.add(new String[]{"Data quality", quality.state().name()});
    rows.add(new String[]{"Observed rows", String.valueOf(quality.observedRows())});
    rows.add(new String[]{"Valid comparable units", "%d / %d".formatted(quality.validComparableUnits(), quality.requiredComparableUnits())});
    rows.add(new String[]{"Primary numerator", String.valueOf(quality.primaryNumerator())});
    rows.add(new String[]{"Primary denominator", String.valueOf(quality.primaryDenominator())});
    if (quality.excludedUnits() > 0) {
        rows.add(new String[]{"Excluded units", String.valueOf(quality.excludedUnits())});
    }

    List<String> attributions = isZh
            ? List.of(
                    "\u5f53\u524d\u6570\u636e\u72b6\u6001\u4e3a " + quality.state() + "\uff0c\u56e0\u6b64\u4ec5\u80fd\u8bf4\u660e\u6570\u91cf\u4e8b\u5b9e",
                    "\u56fe\u8868\u5df2\u9690\u85cf\uff0c\u539f\u56e0\u662f " + quality.chartSuppressionReason()
            )
            : List.of(
                    "The current data quality state is " + quality.state() + ", so the reply is limited to factual counts.",
                    "The chart is hidden because " + quality.chartSuppressionReason() + "."
            );

    List<String> recommendations = isZh
            ? List.of(
                    "\u5148\u6269\u5927\u5230\u57ce\u5e02\u6216\u6574\u4f53\u6837\u672c\u8303\u56f4\u518d\u6bd4\u8f83",
                    "\u68c0\u67e5 " + quality.primaryMetricLabel() + " \u7684\u5206\u5b50\u548c\u5206\u6bcd\u5b57\u6bb5\u662f\u5426\u5b8c\u6574"
            )
            : List.of(
                    "Broaden the scope to city level or the full sample before comparing entities.",
                    "Verify the numerator and denominator fields for " + quality.primaryMetricLabel() + "."
            );

    List<String> followUps = isZh
            ? List.of(
                    "\u653e\u5927\u5230\u6574\u4e2a\u6837\u672c\u540e\u4f1a\u770b\u5230\u4ec0\u4e48\uff1f",
                    "\u5f53\u524d\u8303\u56f4\u8fd8\u6709\u54ea\u4e9b\u53ef\u7528\u6307\u6807\uff1f"
            )
            : List.of(
                    "What does this look like across the full sample dataset?",
                    "Which related metrics are available in this scope?"
            );

    return buildEnrichedReply(
            language,
            conclusion,
            rows,
            null,
            buildChartEmptyFence(language, quality),
            attributions,
            recommendations,
            followUps
    );
}
```

- [ ] **Step 4: Add chart-empty fence builder**

Add this method below `buildLowConfidenceReply()`:

```java
private String buildChartEmptyFence(String language, DataQualityContext quality) {
    boolean isZh = "zh".equals(language);
    String title = isZh ? "\u6682\u65e0\u53ef\u89c6\u5316\u4fe1\u53f7" : "No visual signal";
    String body = isZh
            ? "\u5f53\u524d\u6570\u636e\u4e0d\u652f\u6301\u53ef\u9760\u6392\u540d\u56fe\u8868\uff0c\u56e0\u6b64\u5df2\u9690\u85cf\u53ef\u89c6\u5316\u3002"
            : "The chart is hidden because the available data is not reliable enough for a ranked visualization.";
    return "```chart-empty\nreason: %s\ntitle: %s\nbody: %s\n```"
            .formatted(quality.chartSuppressionReason(), title, body);
}
```

- [ ] **Step 5: Add observability helpers and grounded-reference metadata**

Add this method near the low-confidence helpers:

```java
private void logDataQuality(String language, AnalysisScope scope, DataQualityContext quality) {
    if (quality.state() == DataQualityState.NORMAL) {
        return;
    }
    String message = "analytics data quality scenario={} language={} scope={} state={} observedRows={} validComparableUnits={} requiredComparableUnits={} excludedUnits={} primaryMetric={} chartSuppressed={} chartSuppressionReason={}";
    Object[] args = {
            quality.scenarioLabel(),
            language,
            scope.summary(language),
            quality.state(),
            quality.observedRows(),
            quality.validComparableUnits(),
            quality.requiredComparableUnits(),
            quality.excludedUnits(),
            quality.primaryMetricLabel(),
            quality.suppressChart(),
            quality.chartSuppressionReason()
    };
    if (quality.state() == DataQualityState.DENOMINATOR_ZERO) {
        LOGGER.warn(message, args);
    } else {
        LOGGER.info(message, args);
    }
}
```

Change `buildGroundedReference(...)` to accept a `DataQualityContext quality` parameter, and append:

```java
Data Quality: %s
Chart Suppressed: %s%s
```

Use:

```java
quality == null ? DataQualityState.NORMAL : quality.state()
quality != null && quality.suppressChart()
quality != null && quality.suppressChart() ? " (" + quality.chartSuppressionReason() + ")" : ""
```

Then update `plan()` to create a `ScenarioResult` record from each analysis branch in Task 3. If Task 3 has not been started, temporarily keep `quality` null so the file compiles after this task.

- [ ] **Step 6: Run focused backend tests**

Run:

```powershell
mvn "-Dfrontend.skip=true" "-Dtest=RuleBasedAnalyticsServiceTest" test
```

Expected: tests still fail until each scenario calls `buildLowConfidenceReply`, but helper compilation errors should be resolved.

---

### Task 3: Route All Backend Scenarios Through Quality Gates

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java`
- Test: `backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java`

- [ ] **Step 1: Add a scenario result wrapper**

Add this record near `DataQualityContext`:

```java
private record ScenarioResult(String reply, DataQualityContext quality) {
}
```

Change `plan()` so each scenario returns `ScenarioResult`:

```java
ScenarioResult result = switch (topic) {
    case TARGET_ACHIEVEMENT -> analyzeTargetAchievement(scope, language);
    case OPPORTUNITY_FUNNEL -> analyzeOpportunityFunnel(scope, language);
    case SALES_FOLLOW_UP -> analyzeSalesFollowUp(scope, language);
    case CAMPAIGN_PERFORMANCE -> analyzeCampaignPerformance(scope, language);
    case LEAD_SOURCE -> analyzeLeadSource(scope, language);
    case DEALER_BENCHMARK -> analyzeDealerBenchmark(scope, language);
};
logDataQuality(language, scope, result.quality());
String fallbackReply = result.reply();
```

Update `buildGroundedReference(...)` call to pass `result.quality()`.

- [ ] **Step 2: Change scenario method return types**

Change these method signatures from `String` to `ScenarioResult`:

```java
private ScenarioResult analyzeTargetAchievement(AnalysisScope scope, String language)
private ScenarioResult analyzeOpportunityFunnel(AnalysisScope scope, String language)
private ScenarioResult analyzeSalesFollowUp(AnalysisScope scope, String language)
private ScenarioResult analyzeCampaignPerformance(AnalysisScope scope, String language)
private ScenarioResult analyzeLeadSource(AnalysisScope scope, String language)
private ScenarioResult analyzeDealerBenchmark(AnalysisScope scope, String language)
private ScenarioResult analyzeOpportunityFunnelFromQuery(DataQueryResponse response, AnalysisScope scope, String language)
private ScenarioResult analyzeSalesFollowUpFromQuery(DataQueryResponse response, AnalysisScope scope, String language)
private ScenarioResult analyzeCampaignPerformanceFromQuery(DataQueryResponse response, AnalysisScope scope, String language)
private ScenarioResult analyzeLeadSourceFromQuery(DataQueryResponse response, AnalysisScope scope, String language)
```

Wrap normal returns:

```java
DataQualityContext quality = quality("campaign performance", "attainment rate", metrics.size(), metrics.size(),
        2, metrics.stream().mapToInt(CampaignMetric::actualOpportunityCount).sum(),
        metrics.stream().mapToInt(CampaignMetric::totalNewCustomerTarget).sum(),
        0, DataQualityState.NORMAL, ChartSuppressionReason.NONE);
return new ScenarioResult(buildEnrichedReply(language, conclusion, dataRows, mermaid, fallback, attributions, recommendations, followUps), quality);
```

- [ ] **Step 3: Update no-data path**

Change `buildNoDataReply(...)` to return `ScenarioResult`, or keep it as a reply builder and create quality at call sites:

```java
DataQualityContext quality = quality("target achievement", "achievement rate", 0, 0, 2, 0, 0, 0,
        DataQualityState.NO_DATA, ChartSuppressionReason.NO_DATA);
return new ScenarioResult(buildNoDataReply(language, scope, "target achievement"), quality);
```

For no-data replies, do not add `chart-empty` unless implementation decides a missing chart region adds value. Tests in Task 1 expect `chart-empty` for low-confidence non-empty states, not no-data.

- [ ] **Step 4: Gate target achievement**

After `metrics` is built, split valid and invalid denominator units:

```java
List<DealerTargetMetric> validMetrics = metrics.stream()
        .filter(metric -> metric.targetValue() > 0)
        .toList();
int excludedUnits = metrics.size() - validMetrics.size();
int primaryNumerator = validMetrics.stream().mapToInt(DealerTargetMetric::wonCount).sum();
int primaryDenominator = validMetrics.stream().mapToInt(DealerTargetMetric::targetValue).sum();
double spread = validMetrics.stream().mapToDouble(DealerTargetMetric::achievementRate).max().orElse(0)
        - validMetrics.stream().mapToDouble(DealerTargetMetric::achievementRate).min().orElse(0);
DataQualityContext quality = classifyRateQuality(
        "target achievement", "achievement rate", filtered.size(), validMetrics.size(), 2,
        primaryNumerator, primaryDenominator, excludedUnits, spread, 3);
if (quality.state() != DataQualityState.NORMAL) {
    return new ScenarioResult(buildLowConfidenceReply(language, scope, quality), quality);
}
metrics = validMetrics.stream()
        .sorted(Comparator.comparingDouble(DealerTargetMetric::achievementRate))
        .toList();
```

- [ ] **Step 5: Gate campaign performance**

After campaign metrics are built:

```java
List<CampaignMetric> validMetrics = metrics.stream()
        .filter(metric -> metric.totalNewCustomerTarget() > 0)
        .toList();
int excludedUnits = metrics.size() - validMetrics.size();
int primaryNumerator = validMetrics.stream().mapToInt(CampaignMetric::actualOpportunityCount).sum();
int primaryDenominator = validMetrics.stream().mapToInt(CampaignMetric::totalNewCustomerTarget).sum();
double spread = validMetrics.stream().mapToDouble(CampaignMetric::attainmentRate).max().orElse(0)
        - validMetrics.stream().mapToDouble(CampaignMetric::attainmentRate).min().orElse(0);
DataQualityContext quality = classifyRateQuality(
        "campaign performance", "attainment rate", metrics.size(), validMetrics.size(), 2,
        primaryNumerator, primaryDenominator, excludedUnits, spread, 3);
if (quality.state() != DataQualityState.NORMAL) {
    return new ScenarioResult(buildLowConfidenceReply(language, scope, quality), quality);
}
metrics = validMetrics.stream()
        .sorted(Comparator.comparingDouble(CampaignMetric::attainmentRate).reversed())
        .toList();
```

Apply the same gate to `analyzeCampaignPerformanceFromQuery`.

- [ ] **Step 6: Gate dealer benchmark**

After dealer metrics are built:

```java
List<DealerTargetMetric> validMetrics = metrics.stream()
        .filter(metric -> metric.targetValue() > 0)
        .toList();
int excludedUnits = metrics.size() - validMetrics.size();
int primaryNumerator = validMetrics.stream().mapToInt(DealerTargetMetric::wonCount).sum();
int primaryDenominator = validMetrics.stream().mapToInt(DealerTargetMetric::targetValue).sum();
double spread = validMetrics.stream().mapToDouble(DealerTargetMetric::achievementRate).max().orElse(0)
        - validMetrics.stream().mapToDouble(DealerTargetMetric::achievementRate).min().orElse(0);
DataQualityContext quality = classifyRateQuality(
        "dealer benchmark", "achievement-rate spread", filteredTargets.size(), validMetrics.size(), 2,
        primaryNumerator, primaryDenominator, excludedUnits, spread, 3);
if (quality.state() != DataQualityState.NORMAL) {
    return new ScenarioResult(buildLowConfidenceReply(language, scope, quality), quality);
}
metrics = validMetrics.stream()
        .sorted(Comparator.comparingDouble(DealerTargetMetric::achievementRate).reversed())
        .toList();
```

- [ ] **Step 7: Gate opportunity funnel**

After stage/source counts:

```java
DataQualityContext quality = classifyCountQuality(
        "opportunity funnel", "opportunity count", filtered.size(),
        stageCounts.size(), 3, filtered.size(), filtered.size());
if (quality.state() != DataQualityState.NORMAL) {
    return new ScenarioResult(buildLowConfidenceReply(language, scope, quality), quality);
}
```

Apply the same idea to `analyzeOpportunityFunnelFromQuery`, using `totalOpportunities` as observed rows and denominator.

- [ ] **Step 8: Gate sales follow-up**

After task metrics:

```java
int openAndOverdue = (int) filtered.stream()
        .filter(task -> "Open".equalsIgnoreCase(task.getStatus()) || "Overdue".equalsIgnoreCase(task.getStatus()))
        .count();
DataQualityContext quality = classifyCountQuality(
        "sales follow-up", "task backlog", filtered.size(),
        metrics.size(), 3, openAndOverdue, filtered.size());
if (quality.state() != DataQualityState.NORMAL) {
    return new ScenarioResult(buildLowConfidenceReply(language, scope, quality), quality);
}
```

Apply the same idea to `analyzeSalesFollowUpFromQuery`, using `totalTasks`.

- [ ] **Step 9: Gate lead source**

After source metrics:

```java
int totalLeads = filtered.size();
int convertedLeads = (int) filtered.stream().filter(lead -> Boolean.TRUE.equals(lead.getConverted())).count();
DataQualityContext quality = classifyCountQuality(
        "lead source analysis", "lead conversion", totalLeads,
        sourceMetrics.size(), 3, convertedLeads, totalLeads);
if (quality.state() != DataQualityState.NORMAL) {
    return new ScenarioResult(buildLowConfidenceReply(language, scope, quality), quality);
}
```

Apply the same idea to `analyzeLeadSourceFromQuery`, using aggregate converted count and total count.

- [ ] **Step 10: Run focused backend tests**

Run:

```powershell
mvn "-Dfrontend.skip=true" "-Dtest=RuleBasedAnalyticsServiceTest" test
```

Expected: low-confidence tests pass or reveal wording adjustments. Existing normal-path tests should still pass after their assertions are adjusted only when the new data-quality metadata changes grounded reference text.

- [ ] **Step 11: Commit backend quality gates**

Run:

```powershell
git add -- backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java
git commit -m "fix: gate low-confidence analytics conclusions"
```

Expected: commit succeeds if git writes are available.

---

### Task 4: Backend Chart Label And Point-Limit Policy

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java`
- Modify: `backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java`

- [ ] **Step 1: Write chart-label regression tests**

Add this test near existing escaping tests:

```java
@Test
void chartLabelsAreShortSanitizedAndDeduplicated() {
    when(dealerRepository.findAll()).thenReturn(List.of(
            new Dealer("D001", "Dealer A Flagship Downtown", "Beijing", "Group A"),
            new Dealer("D002", "Dealer A Flagship Downtown", "Beijing", "Group A"),
            new Dealer("D003", "Dealer <C>, \"North\"", "Beijing", "Group A")
    ));
    when(targetRepository.findAll()).thenReturn(List.of(
            new Target("D001", "Dealer A Flagship Downtown", "Beijing", "Group A", "Model X", 2026, 5, 10, 9),
            new Target("D002", "Dealer A Flagship Downtown", "Beijing", "Group A", "Model X", 2026, 5, 10, 7),
            new Target("D003", "Dealer <C>, \"North\"", "Beijing", "Group A", "Model X", 2026, 5, 10, 5)
    ));

    AnalyticsPlan plan = service.plan("dealer benchmark comparison for Beijing", "en");

    assertThat(plan.fallbackReply()).contains("```mermaid");
    assertThat(plan.fallbackReply()).contains("Dealer A...");
    assertThat(plan.fallbackReply()).contains("Dealer A... #2");
    assertThat(plan.fallbackReply()).doesNotContain("Dealer <C>, \"North\"");
    assertThat(plan.fallbackReply()).contains("Dealer &lt;C&gt;, &quot;North&quot;");
}
```

- [ ] **Step 2: Run test to verify red state**

Run:

```powershell
mvn "-Dfrontend.skip=true" "-Dtest=RuleBasedAnalyticsServiceTest#chartLabelsAreShortSanitizedAndDeduplicated" test
```

Expected: FAIL because raw labels are still passed directly into Mermaid.

- [ ] **Step 3: Add chart label helper**

Add enum and record near other helper records:

```java
private enum ChartEntityType {
    DEALER,
    CAMPAIGN,
    STAGE,
    SOURCE,
    GENERIC
}

private record ChartPoint(String label, double value) {
}
```

Add helper methods near Mermaid builders:

```java
private List<String> chartLabels(ChartEntityType entityType, List<String> rawLabels) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    List<String> labels = new ArrayList<>();
    for (int i = 0; i < rawLabels.size(); i++) {
        String base = shortenChartLabel(entityType, rawLabels.get(i), i + 1);
        int count = counts.getOrDefault(base, 0) + 1;
        counts.put(base, count);
        labels.add(count == 1 ? base : base + " #" + count);
    }
    return labels;
}

private String shortenChartLabel(ChartEntityType entityType, String rawLabel, int ordinal) {
    String cleaned = String.valueOf(rawLabel == null ? "" : rawLabel)
            .replaceAll("[\\r\\n\\t]+", " ")
            .replaceAll("[,\\[\\]\"<>]+", " ")
            .replaceAll("\\p{Cntrl}", "")
            .trim()
            .replaceAll("\\s+", " ");
    if (cleaned.isBlank()) {
        return "Item " + ordinal;
    }
    int limit = switch (entityType) {
        case DEALER -> 10;
        case CAMPAIGN, GENERIC -> 12;
        case STAGE, SOURCE -> 14;
    };
    if (cleaned.length() <= limit) {
        return cleaned;
    }
    if (entityType == ChartEntityType.CAMPAIGN && cleaned.length() > limit) {
        return "..." + cleaned.substring(Math.max(0, cleaned.length() - (limit - 3)));
    }
    return cleaned.substring(0, Math.max(1, limit - 3)) + "...";
}
```

- [ ] **Step 4: Apply label helper and point limits**

Replace Mermaid chart label arguments:

```java
topBottom.stream().map(DealerTargetMetric::dealerName).toList()
```

with:

```java
chartLabels(ChartEntityType.DEALER, topBottom.stream().map(DealerTargetMetric::dealerName).toList())
```

Apply matching entity types:

- Target achievement: `DEALER`
- Dealer benchmark: `DEALER`
- Campaign performance: `CAMPAIGN`
- Sales follow-up: `DEALER`
- Opportunity funnel pie labels: `STAGE`
- Lead source pie labels: `SOURCE`

For bottom/top charts, change show count from 5 to 3:

```java
int show = Math.min(3, sorted.size());
```

Deduplicate combined bottom/top lists:

```java
List<DealerTargetMetric> topBottom = Stream.concat(
        sorted.subList(0, show).stream(),
        sorted.subList(Math.max(0, sorted.size() - show), sorted.size()).stream()
).distinct().toList();
```

- [ ] **Step 5: Run backend tests**

Run:

```powershell
mvn "-Dfrontend.skip=true" "-Dtest=RuleBasedAnalyticsServiceTest" test
```

Expected: PASS after any assertion updates needed for shortened labels.

---

### Task 5: Frontend Markdown `chart-empty` Renderer

**Files:**
- Modify: `frontend/src/utils/markdown.js`
- Modify: `frontend/src/utils/__tests__/markdown.spec.js`

- [ ] **Step 1: Write failing markdown tests**

Add these tests to `frontend/src/utils/__tests__/markdown.spec.js`:

```javascript
it("renders chart-empty fences as a structured empty chart state", () => {
  const html = renderMarkdownLite(`\`\`\`chart-empty
reason: ALL_ZERO_SIGNAL
title: No visual signal
body: Records exist, but the key metric is 0.
\`\`\``);

  expect(html).toContain('class="analysis-empty-chart"');
  expect(html).toContain('role="status"');
  expect(html).toContain('data-chart-empty-reason="ALL_ZERO_SIGNAL"');
  expect(html).toContain("No visual signal");
  expect(html).toContain("Records exist, but the key metric is 0.");
});

it("escapes chart-empty title and body values", () => {
  const html = renderMarkdownLite(`\`\`\`chart-empty
reason: DENOMINATOR_ZERO
title: <img src=x onerror=alert(1)>
body: <script>alert(1)</script>
\`\`\``);

  expect(html).toContain("&lt;img src=x onerror=alert(1)&gt;");
  expect(html).toContain("&lt;script&gt;alert(1)&lt;/script&gt;");
  expect(html).not.toContain("<script>alert(1)</script>");
});
```

- [ ] **Step 2: Run frontend markdown tests to verify red state**

Run:

```powershell
cd frontend
npm.cmd run test -- src/utils/__tests__/markdown.spec.js
```

Expected: FAIL because `chart-empty` currently renders as a normal code block.

- [ ] **Step 3: Implement `chart-empty` fence rendering**

In `frontend/src/utils/markdown.js`, before the generic fence rendering, add:

```javascript
  if (langName === "chart-empty") {
    const state = parseChartEmptyFence(token.content);
    const reason = escapeHtml(state.reason || "");
    const title = escapeHtml(state.title || "");
    const body = escapeHtml(state.body || "");

    return `<div class="analysis-empty-chart" role="status"${reason ? ` data-chart-empty-reason="${reason}"` : ""} data-chart-empty="true">${title ? `<div class="analysis-empty-chart-title">${title}</div>` : ""}${body ? `<div class="analysis-empty-chart-body">${body}</div>` : ""}</div>`;
  }
```

Add this helper near `normalizeMermaidSource`:

```javascript
function parseChartEmptyFence(value) {
  const result = {};

  for (const line of String(value ?? "").replace(/\r\n?/g, "\n").split("\n")) {
    const match = line.match(/^([A-Za-z][A-Za-z0-9_-]*)\s*:\s*(.*)$/);

    if (!match) {
      continue;
    }

    const key = match[1];
    const field = key === "reason" || key === "title" || key === "body" ? key : null;

    if (field) {
      result[field] = match[2].trim();
    }
  }

  return result;
}
```

- [ ] **Step 4: Run markdown tests**

Run:

```powershell
cd frontend
npm.cmd run test -- src/utils/__tests__/markdown.spec.js
```

Expected: PASS.

---

### Task 6: Frontend Empty-State Fallbacks, Styles, And Mermaid Container Polish

**Files:**
- Modify: `frontend/src/components/chat/AssistantMessage.vue`
- Modify: `frontend/src/components/__tests__/AssistantMessage.spec.js`
- Modify: `frontend/src/i18n/messages.js`
- Modify: `frontend/src/style.css`

- [ ] **Step 1: Add dictionary strings**

In both `zh` and `en` objects in `frontend/src/i18n/messages.js`, add keys near existing Mermaid strings.

English:

```javascript
chartEmptyTitle: "No visual signal",
chartEmptyBody: "The chart is hidden because the available data is not reliable enough for a ranked visualization.",
chartEmptyNoDataBody: "No matching records are available for this scope.",
chartEmptyDenominatorZeroBody: "The chart is hidden because the denominator for this metric is zero.",
chartEmptyAllZeroBody: "The chart is hidden because the key metric is zero across the current data.",
chartEmptyInsufficientSampleBody: "The chart is hidden because the sample is too small for a reliable ranking.",
```

Chinese:

```javascript
chartEmptyTitle: "\u6682\u65e0\u53ef\u89c6\u5316\u4fe1\u53f7",
chartEmptyBody: "\u5f53\u524d\u6570\u636e\u4e0d\u8db3\u4ee5\u652f\u6301\u53ef\u9760\u7684\u6392\u540d\u56fe\u8868\uff0c\u56e0\u6b64\u5df2\u9690\u85cf\u53ef\u89c6\u5316\u3002",
chartEmptyNoDataBody: "\u5f53\u524d\u8303\u56f4\u6ca1\u6709\u5339\u914d\u8bb0\u5f55\u3002",
chartEmptyDenominatorZeroBody: "\u8be5\u6307\u6807\u5206\u6bcd\u4e3a 0\uff0c\u6682\u4e0d\u5c55\u793a\u56fe\u8868\u3002",
chartEmptyAllZeroBody: "\u5f53\u524d\u6570\u636e\u7684\u5173\u952e\u6307\u6807\u5747\u4e3a 0\uff0c\u6682\u4e0d\u5c55\u793a\u56fe\u8868\u3002",
chartEmptyInsufficientSampleBody: "\u6837\u672c\u91cf\u4e0d\u8db3\u4ee5\u652f\u6301\u53ef\u9760\u6392\u540d\uff0c\u6682\u4e0d\u5c55\u793a\u56fe\u8868\u3002",
```

If this file currently contains mojibake around Chinese strings, preserve surrounding style and insert the correct UTF-8 strings.

- [ ] **Step 2: Write AssistantMessage fallback test**

Add this test to `AssistantMessage.spec.js`:

```javascript
test("fills missing chart empty copy from dictionary", async () => {
  const html = '<div class="analysis-empty-chart" role="status" data-chart-empty="true" data-chart-empty-reason="ALL_ZERO_SIGNAL"></div>';
  const wrapper = mountAssistant(buildMessage(html));

  await flushPromises();

  expect(wrapper.find(".analysis-empty-chart-title").text()).toBe(dictionary.chartEmptyTitle);
  expect(wrapper.find(".analysis-empty-chart-body").text()).toBe(dictionary.chartEmptyAllZeroBody);
});
```

Extend the local `dictionary` fixture with:

```javascript
chartEmptyTitle: "\u6682\u65e0\u53ef\u89c6\u5316\u4fe1\u53f7",
chartEmptyBody: "\u5f53\u524d\u6570\u636e\u4e0d\u8db3\u4ee5\u652f\u6301\u53ef\u9760\u7684\u6392\u540d\u56fe\u8868\uff0c\u56e0\u6b64\u5df2\u9690\u85cf\u53ef\u89c6\u5316\u3002",
chartEmptyNoDataBody: "\u5f53\u524d\u8303\u56f4\u6ca1\u6709\u5339\u914d\u8bb0\u5f55\u3002",
chartEmptyDenominatorZeroBody: "\u8be5\u6307\u6807\u5206\u6bcd\u4e3a 0\uff0c\u6682\u4e0d\u5c55\u793a\u56fe\u8868\u3002",
chartEmptyAllZeroBody: "\u5f53\u524d\u6570\u636e\u7684\u5173\u952e\u6307\u6807\u5747\u4e3a 0\uff0c\u6682\u4e0d\u5c55\u793a\u56fe\u8868\u3002",
chartEmptyInsufficientSampleBody: "\u6837\u672c\u91cf\u4e0d\u8db3\u4ee5\u652f\u6301\u53ef\u9760\u6392\u540d\uff0c\u6682\u4e0d\u5c55\u793a\u56fe\u8868\u3002",
```

- [ ] **Step 3: Run test to verify red state**

Run:

```powershell
cd frontend
npm.cmd run test -- src/components/__tests__/AssistantMessage.spec.js
```

Expected: FAIL because `AssistantMessage.vue` does not fill empty chart copy yet.

- [ ] **Step 4: Implement empty chart copy fill**

In `AssistantMessage.vue`, add:

```javascript
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
      title.textContent = props.dictionary?.chartEmptyTitle ?? (props.locale === "zh" ? "\u6682\u65e0\u53ef\u89c6\u5316\u4fe1\u53f7" : "No visual signal");
      state.prepend(title);
    }

    if (!state.querySelector(".analysis-empty-chart-body")) {
      const body = document.createElement("div");
      body.className = "analysis-empty-chart-body";
      body.textContent = getChartEmptyBody(state.dataset.chartEmptyReason)
        ?? (props.locale === "zh"
          ? "\u5f53\u524d\u6570\u636e\u4e0d\u8db3\u4ee5\u652f\u6301\u53ef\u9760\u7684\u6392\u540d\u56fe\u8868\uff0c\u56e0\u6b64\u5df2\u9690\u85cf\u53ef\u89c6\u5316\u3002"
          : "The chart is hidden because the available data is not reliable enough for a ranked visualization.");
      state.append(body);
    }
  }
}
```

Call `syncChartEmptyStates(messageCard.value)` inside `queueMermaidSync()` before `syncMermaidBlocks(...)`.

- [ ] **Step 5: Add development-only Mermaid render warning**

In the `catch` block of `renderMermaidBlock`, change `catch {` to:

```javascript
  } catch (error) {
    if (import.meta.env.DEV) {
      console.warn("Mermaid chart render failed", {
        blockId,
        message: error?.message
      });
    }
```

Keep the existing user-facing error state unchanged.

- [ ] **Step 6: Restyle Mermaid and empty chart blocks**

In `frontend/src/style.css`, replace the `.mermaid-block`, `.mermaid-toolbar`, `.mermaid-toggle-button`, `.mermaid-chart`, `.mermaid-error-state`, and related chart rules with styles that keep the same selectors but lower contrast:

```css
.mermaid-block {
  margin: 14px 0;
  border: 1px solid rgba(17, 17, 17, 0.1);
  border-radius: 8px;
  background: rgba(248, 248, 246, 0.72);
  overflow: hidden;
}

.mermaid-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 8px 10px;
  border-bottom: 1px solid rgba(17, 17, 17, 0.08);
  background: rgba(255, 255, 255, 0.72);
}

.mermaid-toggle-button {
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted, #5f5f5f);
  padding: 0.2rem 0.55rem;
  line-height: 1.35;
  font-size: 0.82rem;
  transition: background 180ms ease, border-color 180ms ease, color 180ms ease, box-shadow 180ms ease;
}

.mermaid-toggle-button[aria-pressed="true"] {
  border-color: rgba(17, 17, 17, 0.12);
  background: var(--surface, #ffffff);
  color: var(--text-main, #111111);
}

.mermaid-chart {
  min-height: 180px;
  opacity: 0;
  transition: opacity 0.3s ease;
  padding: 14px 12px 12px;
  margin: 0;
  overflow-x: auto;
  background: transparent;
  border: 0;
  border-radius: 0;
}

.analysis-empty-chart {
  display: grid;
  align-content: center;
  gap: 0.35rem;
  min-height: 104px;
  margin: 14px 0;
  padding: 0.95rem 1rem;
  border: 1px dashed rgba(17, 17, 17, 0.16);
  border-radius: 8px;
  background: rgba(248, 248, 246, 0.72);
}

.analysis-empty-chart-title {
  color: var(--text-main, #111111);
  font-size: 0.92rem;
  font-weight: 700;
}

.analysis-empty-chart-body {
  color: var(--text-muted, #5f5f5f);
  font-size: 0.86rem;
  line-height: 1.6;
}
```

Keep skeleton, source, retry, and SVG rules unless they conflict with the new container. Do not add nested cards.

- [ ] **Step 7: Run frontend tests**

Run:

```powershell
cd frontend
npm.cmd run test -- src/utils/__tests__/markdown.spec.js src/components/__tests__/AssistantMessage.spec.js
```

Expected: PASS.

---

### Task 7: Full Verification And Build

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run focused backend suite**

Run:

```powershell
mvn "-Dfrontend.skip=true" "-Dtest=RuleBasedAnalyticsServiceTest" test
```

Expected: PASS with 0 failures.

- [ ] **Step 2: Run focused frontend suites**

Run:

```powershell
cd frontend
npm.cmd run test -- src/utils/__tests__/markdown.spec.js src/components/__tests__/AssistantMessage.spec.js
```

Expected: PASS with 0 failures.

- [ ] **Step 3: Run frontend build**

Run:

```powershell
cd frontend
npm.cmd run build
```

Expected: Vite build succeeds.

- [ ] **Step 4: Run backend package tests without frontend build**

Run:

```powershell
mvn "-Dfrontend.skip=true" test
```

Expected: backend test suite succeeds.

- [ ] **Step 5: Inspect diff**

Run:

```powershell
git diff -- backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java frontend/src/utils/markdown.js frontend/src/utils/__tests__/markdown.spec.js frontend/src/components/chat/AssistantMessage.vue frontend/src/components/__tests__/AssistantMessage.spec.js frontend/src/i18n/messages.js frontend/src/style.css docs/design/2026-05-18-analytics-chart-empty-state-design.md docs/plan/2026-05-18-analytics-chart-empty-state-implementation.md
```

Expected: diff is limited to the planned files and contains no unrelated formatting churn.

- [ ] **Step 6: Commit final implementation**

Run:

```powershell
git add -- backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java frontend/src/utils/markdown.js frontend/src/utils/__tests__/markdown.spec.js frontend/src/components/chat/AssistantMessage.vue frontend/src/components/__tests__/AssistantMessage.spec.js frontend/src/i18n/messages.js frontend/src/style.css docs/design/2026-05-18-analytics-chart-empty-state-design.md docs/plan/2026-05-18-analytics-chart-empty-state-implementation.md
git commit -m "fix: improve analytics chart empty states"
```

Expected: commit succeeds if git writes are available.

---

## Self-Review

- Spec coverage:
  - Scenario primary signal, denominator, and ranking basis are implemented by Task 3.
  - `INSUFFICIENT_SAMPLE` thresholds are implemented by Tasks 2 and 3.
  - Chart suppression is implemented by Tasks 2 and 3.
  - Frontend empty-state Markdown, visual, and copy contracts are implemented by Tasks 5 and 6.
  - Low-confidence template-only replies and banned confidence terms are covered by Tasks 1 and 2.
  - Chart label policy and point limits are covered by Task 4.
  - Observability is covered by Task 2.
- Red-flag scan: no incomplete markers are intentionally left in this plan.
- Type consistency:
  - `DataQualityState`, `ChartSuppressionReason`, `DataQualityContext`, and `ScenarioResult` are introduced before use.
  - `chart-empty` maps to `.analysis-empty-chart`.
  - Dictionary keys in Task 6 match the frontend fallback helper names.
