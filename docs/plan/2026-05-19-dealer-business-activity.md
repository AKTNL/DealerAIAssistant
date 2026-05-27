# 门店经营活跃度 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增"门店经营活跃度"分析类型，基于 Target 表的 AaKTarget/opportunityCreateCount/opportunityWonCount/数据覆盖度 4 个维度，按最近 12 个业务月进行月度达标计数评分（满分 48 分）并分为五级。

**Architecture:** 遵循现有 `RuleBasedAnalyticsService` 的分发模式 — 新增 `AnalysisTopic` 枚举值 → `detectTopic()` 添加关键词匹配 → `plan()` 的 switch 添加新 case → 新方法实现评分计算与报告构建。休眠识别时从 `DealerRepository` 取全量在册门店并左联 Target 数据补齐 0 分。

**Tech Stack:** Java 17, Spring Boot 3, JPA/Hibernate, JUnit 5 + AssertJ + Mockito

---

### Task 1: Add opportunityCreateCount field to Target entity and DB

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/entity/Target.java`
- Modify: `backend/src/main/resources/data.sql` (or create migration)

- [ ] **Step 1: Add field to Target entity**

Add `opportunityCreateCount` field after `opportunityWonCount`. Add to constructor and expose via getter. No setter needed (immutable entity pattern).

```java
// In Target.java, after line 42 (opportunityWonCount field):

    @Column(nullable = false)
    private Integer opportunityCreateCount;

// Update constructor (after line 67, opportunityWonCount parameter):
    public Target(
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            String productModel,
            Integer targetYear,
            Integer targetMonth,
            Integer asKTarget,
            Integer opportunityWonCount,
            Integer opportunityCreateCount
    ) {
        this.dealerCode = dealerCode;
        this.dealerName = dealerName;
        this.city = city;
        this.dealerGroupName = dealerGroupName;
        this.productModel = productModel;
        this.targetYear = targetYear;
        this.targetMonth = targetMonth;
        this.asKTarget = asKTarget;
        this.opportunityWonCount = opportunityWonCount;
        this.opportunityCreateCount = opportunityCreateCount;
    }

// Add getter after getOpportunityWonCount():
    public Integer getOpportunityCreateCount() {
        return opportunityCreateCount;
    }
```

- [ ] **Step 2: Add default constructor for JPA**

The protected no-arg constructor already exists (line 45-46) and JPA will use it. Fields default to null; `@Column(nullable = false)` ensures DB-level enforcement.

- [ ] **Step 3: Update DB schema**

Add column to `dealer_targets` table:

```sql
ALTER TABLE dealer_targets ADD COLUMN opportunity_create_count INT NOT NULL DEFAULT 0;
```

- [ ] **Step 4: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: Compilation errors in files calling `new Target(...)` with old constructor signature — these will be fixed in subsequent tasks.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/entity/Target.java
git commit -m "feat: add opportunityCreateCount field to Target entity"
```

---

### Task 2: Fix Excel import to parse and persist OpportunityCreateCount

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/ExcelImportService.java`

- [ ] **Step 1: Parse opportunityCreateCount in parseTargetSheet**

In `parseTargetSheet()`, after line 452 (`opportunityWonCount`) and before the null check on line 455, add parsing for the new field with a default of 0 (tolerant for older Excel files that lack this column):

```java
            Integer opportunityCreateCount = getInteger(row, headerInfo.headers(),
                    "opportunitycreatecount", "商机创建数", "商机创建数量",
                    "opportunitycreatecountc");
            if (opportunityCreateCount == null) {
                opportunityCreateCount = 0;
            }
```

- [ ] **Step 2: Update Target constructor call**

Update the `new Target(...)` call on lines 469-479 to include the new parameter:

```java
            items.add(new Target(
                    dealerCode,
                    dealerName,
                    city,
                    dealerGroupName != null ? dealerGroupName : "",
                    productModel,
                    targetYear,
                    targetMonth,
                    asKTarget,
                    opportunityWonCount,
                    opportunityCreateCount
            ));
```

- [ ] **Step 3: Update seed data Target constructor calls**

Find all `new Target(...)` calls in `ExcelImportService.java` (around line 877-883 for seed data) and add the `opportunityCreateCount` parameter. Use 0 for existing seed data without this field:

```
new Target("BJ001", "Beijing Star Motors", "Beijing", "North Star Group", "M7", 2026, 4, 120, 92, 15),
new Target("BJ002", "Beijing Horizon Auto", "Beijing", "North Star Group", "M7", 2026, 4, 100, 68, 10),
new Target("SH001", "Shanghai Prime Mobility", "Shanghai", "East River Group", "X5", 2026, 4, 130, 126, 20),
new Target("HZ001", "Hangzhou Lakeside Auto", "Hangzhou", "East River Group", "X5", 2026, 4, 110, 97, 12),
new Target("GZ001", "Guangzhou Motion Hub", "Guangzhou", "South Bay Group", "E3", 2026, 4, 105, 88, 8),
new Target("CD001", "Chengdu Drive Center", "Chengdu", "West Link Group", "E3", 2026, 4, 95, 61, 5)
```

- [ ] **Step 4: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/ExcelImportService.java
git commit -m "feat: parse and persist opportunityCreateCount in Excel import"
```

---

### Task 3: Register new scenario in AnalyticsPlan and AnalyticsScenarioCatalog

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/AnalyticsPlan.java`
- Modify: `backend/src/main/java/com/brand/agentpoc/service/AnalyticsScenarioCatalog.java`

- [ ] **Step 1: Add Scenario enum value**

In `AnalyticsPlan.java`, line 28 (after `LEAD_SOURCE`):

```java
        DEALER_BUSINESS_ACTIVITY
```

- [ ] **Step 2: Add ScenarioWorkflow entry in AnalyticsScenarioCatalog**

In `AnalyticsScenarioCatalog.java`, add a new `ScenarioWorkflow` entry in the `SCENARIOS` list (before the closing `);` of `List.of(...)`):

```java
            new ScenarioWorkflow(
                    AnalyticsPlan.Scenario.DEALER_BUSINESS_ACTIVITY,
                    "门店经营活跃度",
                    "Dealer Business Activity",
                    List.of(
                            "哪些门店经营活跃度最高？",
                            "哪些门店处于休眠状态？",
                            "华东区门店活跃度排名"
                    ),
                    List.of(
                            "Which dealers show the highest business activity?",
                            "Which dealers are dormant?",
                            "Dealer business activity ranking in East China"
                    ),
                    List.of("getCurrentDate()", "searchDealers()", "queryTargets()"),
                    "按最近12个业务月逐月判定有目标/有商机/有成交/有数据四个维度，汇总0-48分评出门店经营活跃度五级。",
                    "Score each dealer 0-48 across 4 dimensions (target/opportunity create/won/data presence) over the last 12 business months, classified into 5 activity tiers."
            )
```

- [ ] **Step 3: Verify catalog loads correctly**

Run: `cd backend && ./mvnw test -pl . -Dtest="RuleBasedAnalyticsServiceTest#planMapsInternalTopicsToAnalyticsScenarios" -q`
Expected: Existing tests still pass (no new topic mapping yet, but catalog loads without error).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/AnalyticsPlan.java backend/src/main/java/com/brand/agentpoc/service/AnalyticsScenarioCatalog.java
git commit -m "feat: add DEALER_BUSINESS_ACTIVITY scenario to AnalyticsPlan and catalog"
```

---

### Task 4: Add AnalysisTopic enum value and intent routing

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java`

- [ ] **Step 1: Add DEALER_BUSINESS_ACTIVITY to AnalysisTopic enum**

In `RuleBasedAnalyticsService.java`, add after `DEALER_BENCHMARK` (around line 3219):

```java
        DEALER_BUSINESS_ACTIVITY("门店经营活跃度", "dealer business activity"),
```

- [ ] **Step 2: Add keyword detection in detectTopic()**

In `detectTopic()`, add before the `"跟进"` line (around line 203). Order matters — "经营活跃度" must be checked before the generic "活跃度" which routes to SALES_FOLLOW_UP:

```java
        if (containsAny(normalized, "经营活跃度", "门店活跃", "休眠门店",
                "经营活跃", "活跃度排名", "business activity", "dormant dealer")) {
            return AnalysisTopic.DEALER_BUSINESS_ACTIVITY;
        }
```

Unicode decoding:
- `经营活跃度` = 经营活跃度
- `门店活跃` = 门店活跃
- `休眠门店` = 休眠门店
- `经营活跃` = 经营活跃
- `活跃度排名` = 活跃度排名

> Important: This block must go BEFORE the `"跟进"` / `"任务"` / `"活跃度"` check (line 203-204), because otherwise "经营活跃度" would be captured by the generic "活跃度" keyword and routed to SALES_FOLLOW_UP.

- [ ] **Step 3: Add mapping in mapScenario()**

In `mapScenario()`, add case after `LEAD_SOURCE` (around line 2435):

```java
            case DEALER_BUSINESS_ACTIVITY -> AnalyticsPlan.Scenario.DEALER_BUSINESS_ACTIVITY;
```

- [ ] **Step 4: Add switch case in plan()**

In `plan()`, add case after `LEAD_SOURCE` (around line 173):

```java
            case DEALER_BUSINESS_ACTIVITY -> analyzeDealerBusinessActivity(scope, language, message);
```

- [ ] **Step 5: Compile check — expect error for missing method**

Run: `cd backend && ./mvnw compile -q`
Expected: FAIL — `analyzeDealerBusinessActivity` method not found. This confirms the routing is wired. Proceed to Task 5.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java
git commit -m "feat: add AnalysisTopic and routing for dealer business activity"
```

---

### Task 5: Write failing tests for analyzeDealerBusinessActivity

**Files:**
- Modify: `backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java`

- [ ] **Step 1: Add DEALER_BUSINESS_ACTIVITY to scenarioCases parameterized test**

Find the `scenarioCases` method (search for `static Stream<Arguments> scenarioCases`) and add test cases:

```java
        // ... existing cases ...
        Arguments.of("哪些门店经营活跃度最高？", AnalyticsPlan.Scenario.DEALER_BUSINESS_ACTIVITY),
        Arguments.of("哪些门店处于休眠状态？", AnalyticsPlan.Scenario.DEALER_BUSINESS_ACTIVITY),
        Arguments.of("Which dealers show the highest business activity?", AnalyticsPlan.Scenario.DEALER_BUSINESS_ACTIVITY),
        Arguments.of("Which dealers are dormant?", AnalyticsPlan.Scenario.DEALER_BUSINESS_ACTIVITY)
```

- [ ] **Step 2: Add test for scenario workflow metadata**

```java
    @Test
    void planExposesScenarioWorkflowMetadataForDealerBusinessActivity() {
        AnalyticsPlan plan = service.plan("哪些门店经营活跃度最高？", "zh");

        assertThat(plan.scenarioWorkflow().scenario()).isEqualTo(AnalyticsPlan.Scenario.DEALER_BUSINESS_ACTIVITY);
        assertThat(plan.scenarioWorkflow().label("zh")).isEqualTo("门店经营活跃度");
        assertThat(plan.scenarioWorkflow().toolChain()).containsExactly(
                "getCurrentDate()",
                "searchDealers()",
                "queryTargets()"
        );
        assertThat(plan.scenarioWorkflow().logicSummary("zh")).contains("4个维度");
    }
```

- [ ] **Step 3: Run tests — expect failure**

Run: `cd backend && ./mvnw test -Dtest="RuleBasedAnalyticsServiceTest#planMapsInternalTopicsToAnalyticsScenarios" -q`
Expected: New cases FAIL — `analyzeDealerBusinessActivity` method missing.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java
git commit -m "test: add failing tests for dealer business activity scenario"
```

---

### Task 6: Implement core calculation — window, monthly matrix, scorer

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java`

- [ ] **Step 1: Add stub method to fix compilation**

Add a minimal stub so the project compiles:

```java
    private ScenarioResult analyzeDealerBusinessActivity(AnalysisScope scope, String language, String message) {
        return new ScenarioResult("TODO",
                quality("门店经营活跃度", "活跃度总分", 0, 0, 0, 0, 0, 0,
                        DataQualityState.NO_DATA, ChartSuppressionReason.NO_DATA));
    }
```

This will be replaced in subsequent steps. Compile-check to confirm:

Run: `cd backend && ./mvnw compile -q`
Expected: PASS

- [ ] **Step 2: Add activity metric record**

Add a new record near the other record definitions (around line 3399, near `TaskBacklogMetric`):

```java
    private record DealerActivityScore(
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            int targetMonths,
            int opportunityCreateMonths,
            int opportunityWonMonths,
            int dataMonths,
            int totalScore,
            boolean isNewStore,
            int observedMonths
    ) {
        String tier() {
            if (totalScore >= 40) return "非常活跃";
            if (totalScore >= 30) return "活跃";
            if (totalScore >= 20) return "一般";
            if (totalScore >= 10) return "低活跃";
            return "休眠";
        }

        String tierEn() {
            if (totalScore >= 40) return "Very Active";
            if (totalScore >= 30) return "Active";
            if (totalScore >= 20) return "Moderate";
            if (totalScore >= 10) return "Low Activity";
            return "Dormant";
        }
    }
```

- [ ] **Step 3: Implement the scoring engine**

Replace the stub with the real implementation. The method must:

1. Determine the 12-month window: `[currentMonth - 11, currentMonth]` based on today's date
2. Decide scope: if message contains "休眠"/"dormant", query ALL dealers from `dealerRepository`; otherwise use dealers with Target data in the window
3. For each dealer, iterate 12 months, checking 4 dimensions per month:
   - Has target: any Target row in that month has `asKTarget >= 1`
   - Has opportunity created: any Target row in that month has `opportunityCreateCount >= 1`
   - Has opportunity won: any Target row in that month has `opportunityWonCount >= 1`
   - Has data: any Target row exists in that month
4. Aggregate: each dimension = count of months satisfying condition → 0-12
5. Total score = sum of 4 dimensions → 0-48
6. Sort by totalScore descending
7. Build report

```java
    private ScenarioResult analyzeDealerBusinessActivity(
            AnalysisScope scope, String language, String message) {

        List<CalcStep> traceSteps = new ArrayList<>();
        String normalized = message != null ? normalize(message) : "";
        boolean includeDormant = containsAny(normalized,
                "休眠", "dormant", "不活跃", "inactive");

        // Determine 12-month window: [currentMonth - 11, currentMonth]
        LocalDate today = LocalDate.now(clock);
        int endYear = today.getYear();
        int endMonth = today.getMonthValue();
        LocalDate windowStart = today.minusMonths(11).withDayOfMonth(1);
        int startYear = windowStart.getYear();
        int startMonth = windowStart.getMonthValue();

        traceSteps.add(new CalcStep(
                "确定12个月统计窗口",
                "Determine 12-month statistics window",
                String.format("%d-%02d 至 %d-%02d", startYear, startMonth, endYear, endMonth),
                String.format("%d-%02d to %d-%02d", startYear, startMonth, endYear, endMonth)
        ));

        // Load Target data within window
        List<Target> windowTargets = targetRepository.findAll().stream()
                .filter(t -> isWithinWindow(t.getTargetYear(), t.getTargetMonth(),
                        startYear, startMonth, endYear, endMonth))
                .filter(t -> matchesScope(t, scope))
                .toList();

        // Group targets by dealerCode + business month
        Map<String, Map<String, List<Target>>> byDealerMonth = windowTargets.stream()
                .collect(Collectors.groupingBy(
                        Target::getDealerCode,
                        Collectors.groupingBy(
                                t -> t.getTargetYear() + "-" + String.format("%02d", t.getTargetMonth()))
                ));

        // Build list of 12 month keys in order
        List<String> monthKeys = new ArrayList<>();
        for (int m = 0; m < 12; m++) {
            LocalDate d = windowStart.plusMonths(m);
            monthKeys.add(d.getYear() + "-" + String.format("%02d", d.getMonthValue()));
        }

        // Determine set of dealers to score
        Set<String> dealerCodesToScore;
        Map<String, Dealer> dealerByCode = new LinkedHashMap<>();
        if (includeDormant) {
            // Full roster: all dealers matching scope
            List<Dealer> allDealers = dealerRepository.findAll().stream()
                    .filter(d -> scopeMatchesDealer(d, scope))
                    .toList();
            for (Dealer d : allDealers) {
                dealerByCode.put(d.getDealerCode(), d);
            }
            dealerCodesToScore = dealerByCode.keySet();
        } else {
            // Only dealers with at least one Target record in window
            dealerCodesToScore = byDealerMonth.keySet();
            for (Target t : windowTargets) {
                String code = t.getDealerCode();
                if (!dealerByCode.containsKey(code)) {
                    dealerByCode.put(code, new Dealer(code, t.getDealerName(), t.getCity(), t.getDealerGroupName()));
                }
            }
        }

        // Score each dealer
        List<DealerActivityScore> scores = new ArrayList<>();
        for (String dealerCode : dealerCodesToScore) {
            Map<String, List<Target>> monthData = byDealerMonth.getOrDefault(dealerCode, Map.of());
            Dealer dealer = dealerByCode.get(dealerCode);

            int targetMonths = 0, createMonths = 0, wonMonths = 0, dataMonths = 0;
            int firstDataMonthIndex = -1;

            for (int i = 0; i < monthKeys.size(); i++) {
                String mk = monthKeys.get(i);
                List<Target> monthTargets = monthData.getOrDefault(mk, List.of());
                if (!monthTargets.isEmpty()) {
                    dataMonths++;
                    if (firstDataMonthIndex == -1) firstDataMonthIndex = i;
                    boolean hasTarget = monthTargets.stream().anyMatch(t -> t.getAsKTarget() != null && t.getAsKTarget() >= 1);
                    boolean hasCreate = monthTargets.stream().anyMatch(t -> t.getOpportunityCreateCount() != null && t.getOpportunityCreateCount() >= 1);
                    boolean hasWon = monthTargets.stream().anyMatch(t -> t.getOpportunityWonCount() != null && t.getOpportunityWonCount() >= 1);
                    if (hasTarget) targetMonths++;
                    if (hasCreate) createMonths++;
                    if (hasWon) wonMonths++;
                }
            }

            int totalScore = targetMonths + createMonths + wonMonths + dataMonths;
            int observedMonths = firstDataMonthIndex >= 0 ? (12 - firstDataMonthIndex) : 0;
            boolean isNewStore = observedMonths > 0 && observedMonths < 12;

            scores.add(new DealerActivityScore(
                    dealerCode,
                    dealer != null ? dealer.getDealerName() : "",
                    dealer != null ? dealer.getCity() : "",
                    dealer != null ? dealer.getDealerGroupName() : "",
                    targetMonths, createMonths, wonMonths, dataMonths,
                    totalScore, isNewStore, observedMonths
            ));
        }

        scores.sort(Comparator.comparingInt(DealerActivityScore::totalScore).reversed()
                .thenComparing(DealerActivityScore::dealerCode));

        traceSteps.add(new CalcStep(
                "按门店计算4维度得分并分级",
                "Score each dealer across 4 dimensions and assign tier",
                scores.size() + " 个门店",
                scores.size() + " dealers"
        ));

        return buildDealerActivityResult(scope, language, traceSteps, scores,
                startYear, startMonth, endYear, endMonth, includeDormant, windowTargets.size());
    }

    private boolean matchesScope(Target t, AnalysisScope scope) {
        if (scope.city() != null && !scope.city().equals(t.getCity())) return false;
        if (scope.dealerCode() != null && !scope.dealerCode().equals(t.getDealerCode())) return false;
        if (scope.dealerGroupName() != null && !scope.dealerGroupName().equals(t.getDealerGroupName())) return false;
        if (scope.productModel() != null && !scope.productModel().equals(t.getProductModel())) return false;
        return true;
    }

    private boolean scopeMatchesDealer(Dealer d, AnalysisScope scope) {
        if (scope.city() != null && !scope.city().equals(d.getCity())) return false;
        if (scope.dealerCode() != null && !scope.dealerCode().equals(d.getDealerCode())) return false;
        if (scope.dealerGroupName() != null && !scope.dealerGroupName().equals(d.getDealerGroupName())) return false;
        return true;
    }

    private boolean isWithinWindow(int year, int month,
                                   int startYear, int startMonth,
                                   int endYear, int endMonth) {
        int targetYm = year * 100 + month;
        int startYm = startYear * 100 + startMonth;
        int endYm = endYear * 100 + endMonth;
        return targetYm >= startYm && targetYm <= endYm;
    }
```

- [ ] **Step 4: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: PASS (with buildDealerActivityResult stub if not yet implemented)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java
git commit -m "feat: implement dealer business activity scoring engine"
```

---

### Task 7: Implement report builder buildDealerActivityResult

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java`

**Key existing helpers available:** `buildMermaidXyChart(title, xLabel, yLabel, entityType, labels, values, null)`, `buildFallbackBars(entityType, labels, values, maxValue)`, `buildEnrichedReply(language, conclusion, dataRows, mermaid, fallback, attributions, recommendations, followUps)`, `classifyCountQuality(...)`, `noDataResult(...)`, `lowConfidenceResult(...)`, `buildNoDataReply(...)`, `formatPercent(double)`, `percentage(int,int)`.

- [ ] **Step 1: Implement the report builder**

This follows the same pattern as `buildSalesFollowUpActivityResult` (lines 1037-1211): assemble conclusion text, build `List<String[]> dataRows`, generate chart via `buildMermaidXyChart` + `buildFallbackBars`, then wrap via `buildEnrichedReply`. No fictional `buildSection`/`buildTable` helpers — all HTML is built inline.

```java
    private ScenarioResult buildDealerActivityResult(
            AnalysisScope scope, String language,
            List<CalcStep> traceSteps,
            List<DealerActivityScore> scores,
            int startYear, int startMonth, int endYear, int endMonth,
            boolean includeDormant, int totalTargetRows) {

        boolean isZh = "zh".equals(language);

        // ── Quality check ──
        if (scores.isEmpty()) {
            String label = isZh ? "门店经营活跃度" : "Dealer Business Activity";
            return noDataResult(language, scope, label);
        }

        int positiveScoreCount = (int) scores.stream().filter(s -> s.totalScore() > 0).count();
        DataQualityContext quality = classifyCountQuality(
                isZh ? "门店经营活跃度" : "Dealer Business Activity",
                isZh ? "活跃度总分" : "activity total score",
                scores.size(), scores.size(), 3,
                positiveScoreCount, scores.size(), false);
        if (quality.state() != DataQualityState.NORMAL) {
            return lowConfidenceResult(language, scope, quality);
        }

        // ── Tier distribution ──
        Map<String, Long> tierCounts = new LinkedHashMap<>();
        String[] zhTiers = {"非常活跃", "活跃", "一般", "低活跃", "休眠"};
        String[] enTiers = {"Very Active", "Active", "Moderate", "Low Activity", "Dormant"};
        for (int i = 0; i < zhTiers.length; i++) {
            String key = isZh ? zhTiers[i] : enTiers[i];
            tierCounts.put(key, 0L);
        }
        for (DealerActivityScore s : scores) {
            String t = isZh ? s.tier() : s.tierEn();
            tierCounts.merge(t, 1L, Long::sum);
        }

        // ── Top 3 / Bottom 3 ──
        List<DealerActivityScore> top3 = scores.stream().limit(3).toList();
        List<DealerActivityScore> bottom3 = scores.size() > 3
                ? scores.reversed().stream().limit(3).toList().reversed()
                : scores;

        // ── Conclusion ──
        DealerActivityScore top1 = scores.get(0);
        String conclusion;
        if (isZh) {
            List<String> tierParts = new ArrayList<>();
            for (Map.Entry<String, Long> e : tierCounts.entrySet()) {
                if (e.getValue() > 0) tierParts.add(e.getKey() + " " + e.getValue() + " 家");
            }
            conclusion = "## 核心结论\n\n"
                    + String.format("- 共 **%d** 个门店，其中：%s\n", scores.size(), String.join("，", tierParts))
                    + String.format("- 活跃度最高：**%s**（%d 分），%d 个月有目标，%d 个月有商机，%d 个月有成交\n",
                            top1.dealerName(), top1.totalScore(),
                            top1.targetMonths(), top1.opportunityCreateMonths(), top1.opportunityWonMonths());
            if (includeDormant) {
                long dormant = tierCounts.getOrDefault("休眠", 0L);
                if (dormant > 0) {
                    conclusion += String.format("- ⚠️ %d 家休眠门店需关注，建议核实经营状态\n", dormant);
                }
            }
            for (DealerActivityScore s : scores) {
                if (s.isNewStore()) {
                    conclusion += String.format("- **%s** 观察期不足 12 个月（仅 %d 个月有数据），新店结果偏保守\n",
                            s.dealerName(), s.observedMonths());
                }
            }
        } else {
            List<String> tierParts = new ArrayList<>();
            for (Map.Entry<String, Long> e : tierCounts.entrySet()) {
                if (e.getValue() > 0) tierParts.add(e.getKey() + ": " + e.getValue());
            }
            conclusion = "## Conclusion\n\n"
                    + String.format("- **%d** dealers total. %s\n", scores.size(), String.join(", ", tierParts))
                    + String.format("- Highest activity: **%s** (%d pts), %d mo target, %d mo opportunities, %d mo won\n",
                            top1.dealerName(), top1.totalScore(),
                            top1.targetMonths(), top1.opportunityCreateMonths(), top1.opportunityWonMonths());
            if (includeDormant) {
                long dormant = tierCounts.getOrDefault("Dormant", 0L);
                if (dormant > 0) {
                    conclusion += String.format("- ⚠️ %d dormant dealers require attention\n", dormant);
                }
            }
            for (DealerActivityScore s : scores) {
                if (s.isNewStore()) {
                    conclusion += String.format("- **%s** only %d months observed — new store, results conservative\n",
                            s.dealerName(), s.observedMonths());
                }
            }
        }

        // ── Data table (List<String[]> dataRows for buildEnrichedReply) ──
        String windowLabelZh = String.format("%d年%d月 — %d年%d月", startYear, startMonth, endYear, endMonth);
        String windowLabelEn = String.format("%d-%02d — %d-%02d", startYear, startMonth, endYear, endMonth);

        List<String[]> dataRows = new ArrayList<>();
        dataRows.add(new String[]{
                isZh ? "统计窗口" : "Window",
                isZh ? windowLabelZh : windowLabelEn});
        dataRows.add(new String[]{
                isZh ? "有效 Target 记录数" : "Valid Target rows",
                String.valueOf(totalTargetRows)});
        dataRows.add(new String[]{
                isZh ? "覆盖门店数" : "Dealers covered",
                String.valueOf(scores.size())});

        StringBuilder tableHtml = new StringBuilder();
        String header = isZh
                ? "<tr><th>门店代码</th><th>门店名称</th><th>有目标(月)</th><th>有商机(月)</th><th>有成交(月)</th><th>有数据(月)</th><th>总分</th><th>等级</th></tr>"
                : "<tr><th>Code</th><th>Name</th><th>Target(mo)</th><th>Oppt Created(mo)</th><th>Oppt Won(mo)</th><th>Data(mo)</th><th>Score</th><th>Tier</th></tr>";
        tableHtml.append("<table>\n").append(header).append("\n");
        for (DealerActivityScore s : scores) {
            String tierLabel = isZh ? s.tier() : s.tierEn();
            String note = "";
            if (s.isNewStore()) {
                note = isZh ? " (新店)" : " (new)";
            }
            tableHtml.append(String.format(
                    "<tr><td>%s</td><td>%s</td><td>%d</td><td>%d</td><td>%d</td><td>%d</td><td>%d</td><td>%s%s</td></tr>%n",
                    s.dealerCode(), s.dealerName(), s.targetMonths(), s.opportunityCreateMonths(),
                    s.opportunityWonMonths(), s.dataMonths(), s.totalScore(), tierLabel, note));
        }
        tableHtml.append("</table>\n");

        // ── Mermaid chart (Top 10 via existing buildMermaidXyChart) ──
        int showN = Math.min(10, scores.size());
        List<String> chartLabels = new ArrayList<>();
        List<Double> chartValues = new ArrayList<>();
        for (int i = 0; i < showN; i++) {
            chartLabels.add(scores.get(i).dealerName());
            chartValues.add((double) scores.get(i).totalScore());
        }
        String chartTitle = isZh
                ? scope.summary(language) + " 门店经营活跃度 Top " + showN
                : scope.summary(language) + " Dealer Business Activity Top " + showN;
        String mermaid = buildMermaidXyChart(
                chartTitle,
                isZh ? "门店" : "Dealer",
                isZh ? "总分 (满分48)" : "Score (max 48)",
                ChartEntityType.DEALER,
                chartLabels, chartValues, null);
        String fallback = buildFallbackBars(ChartEntityType.DEALER, chartLabels, chartValues, 48);

        // ── Recommendations ──
        List<String> recommendations = new ArrayList<>();
        long dormantCount = scores.stream().filter(s -> s.totalScore() < 10).count();
        long lowCount = scores.stream().filter(s -> s.totalScore() >= 10 && s.totalScore() < 20).count();
        if (dormantCount > 0) {
            recommendations.add(isZh
                    ? String.format("休眠门店（%d 家）：核实经营状态，确认是否已停业或数据中断，安排跟进", dormantCount)
                    : String.format("Dormant dealers (%d): verify operational status, check for data gaps or closures", dormantCount));
        }
        if (lowCount > 0) {
            recommendations.add(isZh
                    ? String.format("低活跃门店（%d 家）：分析短板维度，制定针对性提升计划", lowCount)
                    : String.format("Low-activity dealers (%d): identify weak dimensions and create improvement plans", lowCount));
        }
        if (dormantCount == 0 && lowCount == 0) {
            recommendations.add(isZh
                    ? "整体活跃度良好，建议关注个别维度偏低的门店，保持稳定运营"
                    : "Overall activity is healthy. Monitor dealers with specific weak dimensions.");
        }

        // ── Attributions ──
        List<String> attributions = List.of(
                isZh
                        ? "数据来源：dealer_targets 表，含 AaKTarget__c / OpportunityCreateCount__c / OpportunityWonCount__c"
                        : "Data source: dealer_targets table, fields AaKTarget__c / OpportunityCreateCount__c / OpportunityWonCount__c");

        // ── Follow-ups ──
        List<String> followUps = new ArrayList<>();
        if (isZh) {
            followUps.add("这些休眠门店最近一次有数据是什么时候？");
            followUps.add("低活跃门店在哪个维度最弱？");
            followUps.add("非常活跃门店有哪些共同特征？");
        } else {
            followUps.add("When was the last time dormant dealers had data?");
            followUps.add("Which dimension is weakest for low-activity dealers?");
            followUps.add("What common traits do very active dealers share?");
        }

        String fullReply = buildEnrichedReply(language, conclusion, dataRows, mermaid, fallback,
                attributions, recommendations, followUps);

        return new ScenarioResult(fullReply, quality, traceSteps);
    }
```

- [ ] **Step 2: Compile and run tests**

Run: `cd backend && ./mvnw test -Dtest="RuleBasedAnalyticsServiceTest" -q`
Expected: Scenario routing tests pass. Data-backed activity test passes (Task 8).

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java
git commit -m "feat: implement dealer business activity report builder"
```

- [ ] **Step 2: Compile and run tests**

Run: `cd backend && ./mvnw test -Dtest="RuleBasedAnalyticsServiceTest" -q`
Expected: Scenario routing tests pass. Data-backed tests may pass if the stub returns valid output.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java
git commit -m "feat: implement dealer business activity report builder"
```

---

### Task 8: Write data-backed integration test

**Files:**
- Modify: `backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java`

- [ ] **Step 1: Add test for full scoring with mock data**

```java
    @Test
    void planComputesDealerBusinessActivityScoresWithFiveTiers() {
        // Setup: 3 dealers, 12 months of data with different activity levels
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("D001", "Very Active Dealer", "Beijing", "Group A"),
                new Dealer("D002", "Dormant Dealer", "Shanghai", "Group B"),
                new Dealer("D003", "Moderate Dealer", "Guangzhou", "Group C")
        ));

        // Build 12 months of data for each dealer
        List<Target> targets = new ArrayList<>();
        // D001: fully active all 12 months
        // D002: no data at all
        // D003: 6 months of partial data
        LocalDate now = LocalDate.now(Clock.systemDefaultZone());
        for (int i = 0; i < 12; i++) {
            LocalDate month = now.minusMonths(11 - i);
            int y = month.getYear();
            int m = month.getMonthValue();
            // asKTarget=10, opportunityWon=5, opportunityCreate=3
            targets.add(new Target("D001", "Very Active Dealer", "Beijing", "Group A",
                    "Model X", y, m, 10, 5, 3));
            if (i < 6) {
                targets.add(new Target("D003", "Moderate Dealer", "Guangzhou", "Group C",
                        "Model Y", y, m, 5, 2, 1));
            }
        }
        when(targetRepository.findAll()).thenReturn(targets);

        // Trigger via dormant keyword to include all dealers
        AnalyticsPlan plan = service.plan("哪些门店处于休眠状态？", "zh");

        assertThat(plan.scenario()).isEqualTo(AnalyticsPlan.Scenario.DEALER_BUSINESS_ACTIVITY);
        assertThat(plan.fallbackReply()).contains("核心结论");
        assertThat(plan.fallbackReply()).contains("Very Active Dealer");
        assertThat(plan.fallbackReply()).contains("Dormant Dealer");
        assertThat(plan.fallbackReply()).contains("休眠");
        assertThat(plan.fallbackReply()).contains("非常活跃");
        assertThat(plan.fallbackReply()).contains("48"); // D001 should have 48 points (12×4)
    }

    @Test
    void planReturnsNoDataForEmptyDealerDatabase() {
        when(dealerRepository.findAll()).thenReturn(List.of());
        when(targetRepository.findAll()).thenReturn(List.of());

        AnalyticsPlan plan = service.plan("哪些门店处于休眠状态？", "zh");

        assertThat(plan.fallbackReply()).contains("缺少足够数据");
    }

    @Test
    void planMarksNewStoreWhenObservedLessThan12Months() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("N001", "New Store", "Beijing", "Group A")
        ));

        LocalDate now = LocalDate.now(Clock.systemDefaultZone());
        List<Target> targets = new ArrayList<>();
        // Only 3 months of recent data
        for (int i = 0; i < 3; i++) {
            LocalDate month = now.minusMonths(2 - i);
            targets.add(new Target("N001", "New Store", "Beijing", "Group A",
                    "Model X", month.getYear(), month.getMonthValue(), 5, 2, 1));
        }
        when(targetRepository.findAll()).thenReturn(targets);

        AnalyticsPlan plan = service.plan("哪些门店经营活跃度最高？", "zh");

        assertThat(plan.fallbackReply()).contains("新店");
        assertThat(plan.fallbackReply()).contains("观察期");
    }

    @Test
    void planDetectsBusinessActivityIntentInEnglish() {
        when(dealerRepository.findAll()).thenReturn(List.of());
        when(targetRepository.findAll()).thenReturn(List.of());

        AnalyticsPlan plan = service.plan("Which dealers are dormant?", "en");

        assertThat(plan.scenario()).isEqualTo(AnalyticsPlan.Scenario.DEALER_BUSINESS_ACTIVITY);
        assertThat(plan.scenarioWorkflow().label("en")).isEqualTo("Dealer Business Activity");
        assertThat(plan.fallbackReply()).contains("not enough data");
    }
```

- [ ] **Step 2: Run the new tests**

Run: `cd backend && ./mvnw test -Dtest="RuleBasedAnalyticsServiceTest" -q`
Expected: All tests PASS.

- [ ] **Step 3: Run full test suite**

Run: `cd backend && ./mvnw test -q`
Expected: All tests PASS. No regressions.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java
git commit -m "test: add integration tests for dealer business activity scoring"
```

---

### Task 9: Final verification and cleanup

- [ ] **Step 1: Run full test suite**

```bash
cd backend && ./mvnw test -q
```

- [ ] **Step 2: Verify no compilation warnings for unused imports**

```bash
cd backend && ./mvnw compile -q
```

- [ ] **Step 3: Manually verify scenario routing coverage**

```bash
cd backend && ./mvnw test -Dtest="RuleBasedAnalyticsServiceTest#planMapsInternalTopicsToAnalyticsScenarios" -q
```

- [ ] **Step 4: Commit any cleanup**

```bash
git add -A
git diff --cached --stat
git commit -m "chore: cleanup and final verification for dealer business activity"
```
