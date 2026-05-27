# Backend Report Import Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved backend improvements: dynamic call-chain reporting, shorter analytics reports without data summaries, tolerant campaign import defaults, and tests for rule/model/import behavior.

**Architecture:** Keep the existing backend contracts. `RuleBasedAnalyticsService` generates the visible call chain and fallback report body, `PromptFactory` instructs external models to preserve the same structure, `ChatService` validates model-polished reports with tolerant heading regexes, and `ExcelImportService` normalizes missing non-critical campaign fields while logging import normalization counts.

**Tech Stack:** Java 21, Spring Boot 3.4, JUnit 5, Mockito, AssertJ, Apache POI, Maven.

---

### Task 1: Report Contract Tests

**Files:**
- Modify: `backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java`
- Modify: `backend/src/test/java/com/brand/agentpoc/ai/PromptFactoryTest.java`

- [ ] **Step 1: Write failing RuleBasedAnalyticsService structure tests**

Update `fallbackReplyContainsSixSectionsForTargetAchievement` to assert the new seven-marker contract and strict order. Replace the old data-summary test with a negative assertion.

```java
@Test
void fallbackReplyContainsNewSevenMarkersForTargetAchievementInOrder() {
    when(dealerRepository.findAll()).thenReturn(List.of(
            new Dealer("D001", "Store A", "Beijing", "Group1")
    ));
    when(targetRepository.findAll()).thenReturn(List.of(
            new Target("D001", "Store A", "Beijing", "Group1", "ModelX", 2026, 5, 100, 80, 0)
    ));

    RuleBasedAnalyticsService.AnalyticsResponse response = service.analyze("本月目标达成率", "zh");

    assertNewZhReportMarkersInOrder(response.reply());
    assertThat(response.reply()).doesNotContain("## 数据汇总");
    assertThat(response.reply()).doesNotContain("## Data Summary");
}

private void assertNewZhReportMarkersInOrder(String reply) {
    assertThat(reply).contains("## 接口调用链");
    assertThat(reply).contains("## 核心结论");
    assertThat(reply).contains("## 数据支撑");
    assertThat(reply).contains("## 经营分析");
    assertThat(reply).contains("## 问题诊断与解决");
    assertThat(reply).contains("## 改进建议");
    assertThat(reply).contains("追问：");
    assertThat(reply).containsSubsequence(
            "## 接口调用链",
            "## 核心结论",
            "## 数据支撑",
            "## 经营分析",
            "## 问题诊断与解决",
            "## 改进建议",
            "追问："
    );
}
```

- [ ] **Step 2: Write failing dynamic call-chain detail test**

Add a target-achievement test using a fixed clock constructor and a matched dealer.

```java
@Test
void callChainDescribesDateScopeDealerMappingAndDataCategories() {
    RuleBasedAnalyticsService timeAwareService = new RuleBasedAnalyticsService(
            promptFactory,
            dealerRepository,
            opportunityRepository,
            campaignRepository,
            taskRepository,
            targetRepository,
            leadRepository,
            dataQueryService,
            Clock.fixed(Instant.parse("2026-05-20T08:15:30Z"), ZoneOffset.UTC)
    );
    when(dealerRepository.findAll()).thenReturn(List.of(
            new Dealer("7036", "经销商AJ(沈阳)", "沈阳", "经销商集团P")
    ));
    when(targetRepository.findAll()).thenReturn(List.of(
            new Target("7036", "经销商AJ(沈阳)", "沈阳", "经销商集团P", "Aurora S", 2026, 5, 100, 80, 0)
    ));

    AnalyticsPlan plan = timeAwareService.plan("经销商AJ(沈阳)本月目标达成率", "zh");

    assertThat(plan.fallbackReply()).contains("## 接口调用链");
    assertThat(plan.fallbackReply()).contains("获取当前时间：2026-05-20");
    assertThat(plan.fallbackReply()).contains("识别为目标达成分析");
    assertThat(plan.fallbackReply()).contains("命中经销商 `7036`");
    assertThat(plan.fallbackReply()).contains("Target", "Opportunity");
    assertThat(plan.fallbackReply()).contains("asKTarget", "opportunityWonCount");
}
```

- [ ] **Step 3: Update PromptFactory tests to the new prompt contract**

Replace old six-section assertions with new section and negative prompt checks.

```java
@Test
void systemPromptIncludesNewReportStructureInChinese() {
    String prompt = promptFactory.buildSystemPrompt("zh");

    assertThat(prompt).contains("## 接口调用链");
    assertThat(prompt).contains("## 核心结论");
    assertThat(prompt).contains("## 数据支撑");
    assertThat(prompt).contains("## 经营分析");
    assertThat(prompt).contains("## 问题诊断与解决");
    assertThat(prompt).contains("## 改进建议");
    assertThat(prompt).contains("严禁修改以下二级标题的任何文字");
    assertThat(prompt).contains("严禁在【数据支撑】后方或报告末尾创造任何形式");
    assertThat(prompt).doesNotContain("## 数据汇总");
}

@Test
void groundedPromptIncludesNewSectionInstructionsInEnglish() {
    String prompt = promptFactory.buildGroundedModelPrompt(
            "en", "Target achievement", "None", "Scenario: TARGET_ACHIEVEMENT");

    assertThat(prompt).contains("## Interface Call Chain");
    assertThat(prompt).contains("## Problem Diagnosis & Solutions");
    assertThat(prompt).contains("## Improvement Suggestions");
    assertThat(prompt).contains("Do not change any required level-2 heading text");
    assertThat(prompt).contains("Do not create any data overview");
    assertThat(prompt).doesNotContain("## Data Summary");
}
```

- [ ] **Step 4: Run focused tests and verify RED**

Run:

```powershell
cd backend
mvn -Dfrontend.skip=true -Dtest=RuleBasedAnalyticsServiceTest,PromptFactoryTest test
```

Expected: FAIL because `## 接口调用链` is missing and prompts still mention data summary.

### Task 2: Report Contract Implementation

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java`
- Modify: `backend/src/main/java/com/brand/agentpoc/ai/PromptFactory.java`

- [ ] **Step 1: Add dynamic call-chain generation to RuleBasedAnalyticsService**

Add imports:

```java
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
```

Add formatter field:

```java
private static final DateTimeFormatter CALL_CHAIN_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");
```

In `plan(...)`, wrap the scenario result before using it:

```java
String callChain = buildInterfaceCallChain(language, topic, scenarioWorkflow, scope, result.traceSteps());
ScenarioResult resultWithCallChain = result.withReply(prependInterfaceCallChain(language, callChain, result.reply()));
String fallbackReply = resultWithCallChain.reply();
logDataQuality(language, scope, resultWithCallChain.quality());
```

Add helper methods:

```java
private String prependInterfaceCallChain(String language, String callChain, String reply) {
    String heading = "zh".equals(language) ? "## 接口调用链" : "## Interface Call Chain";
    return heading + "\n\n" + callChain.trim() + "\n\n" + reply.trim();
}

private String buildInterfaceCallChain(
        String language,
        AnalysisTopic topic,
        AnalyticsScenarioCatalog.ScenarioWorkflow workflow,
        AnalysisScope scope,
        List<CalcStep> traceSteps
) {
    boolean isZh = "zh".equals(language);
    String currentDate = formatCallChainDate();
    String scopeSummary = scope.summary(language);
    String dealerMapping = scope.dealerCode() != null
            ? (isZh
                    ? "命中经销商 `%s`，使用该编码过滤相关数据。".formatted(scope.dealerCode())
                    : "Matched dealer `%s` and used that code to filter related data.".formatted(scope.dealerCode()))
            : (isZh
                    ? "未命中单一经销商，按当前范围汇总经销商主数据。"
                    : "No single dealer matched; aggregating dealer master data for the current scope.");
    String dataCategories = dataCategorySummary(topic);
    String aggregation = aggregationSummary(topic, language);

    if (isZh) {
        return """
                1. 获取当前时间：%s，作为本月/近期等时间范围的计算基准。
                2. 实体与意图解析：识别为%s，范围为 %s。
                3. 主数据编码映射：%s
                4. 数据调用：读取 %s，按当前范围过滤。
                5. 聚合对比：%s
                """.formatted(currentDate, workflow.label(language), scopeSummary, dealerMapping, dataCategories, aggregation);
    }
    return """
            1. Current date: %s, used as the baseline for month/recent time windows.
            2. Entity and intent parsing: identified %s, scope %s.
            3. Master data mapping: %s
            4. Data calls: read %s and filtered by the current scope.
            5. Aggregation and comparison: %s
            """.formatted(currentDate, workflow.label(language), scopeSummary, dealerMapping, dataCategories, aggregation);
}

private String formatCallChainDate() {
    return CALL_CHAIN_DATE_FORMATTER.format(clock.instant().atZone(ZoneId.systemDefault()));
}
```

Implement `dataCategorySummary(...)` and `aggregationSummary(...)` with `switch (topic)` returning the scenario-specific strings from the design, including Target/Opportunity for target achievement.

- [ ] **Step 2: Add ScenarioResult copy helper**

Extend the private record:

```java
private record ScenarioResult(String reply, DataQualityContext quality, List<CalcStep> traceSteps) {
    ScenarioResult(String reply, DataQualityContext quality) {
        this(reply, quality, List.of());
    }

    ScenarioResult withReply(String nextReply) {
        return new ScenarioResult(nextReply, quality, traceSteps);
    }
}
```

- [ ] **Step 3: Remove Data Summary sections from reply builders**

In `buildEnrichedReply(...)`, delete the `## 数据汇总` / `## Data Summary` block entirely. In `appendNewSections(...)`, delete both Chinese and English data-summary table blocks and append the follow-up block immediately after improvement suggestions.

- [ ] **Step 4: Update PromptFactory prompts**

In `buildSystemPrompt(...)`, replace output structure with the new headings including `## 接口调用链` / `## Interface Call Chain`, remove `## 数据汇总` / `## Data Summary`, and add the negative prompt text:

```java
严禁修改以下二级标题的任何文字，严禁添加图标或改变 Markdown 层级，否则系统将无法识别。
请将所有客观数字、表格纯粹保留在【数据支撑】中。严禁在【数据支撑】后方或报告末尾创造任何形式的“数据总览”、“总计尾注”或“摘要段落”。
```

For English:

```java
Do not change any required level-2 heading text, do not add icons, and do not change the Markdown heading level; otherwise the system cannot recognize the response.
Keep all objective numbers and tables only in Data Support. Do not create any data overview, total note, summary block, or trailing recap after Data Support or at the end of the report.
```

In `buildGroundedModelPrompt(...)`, add `## 接口调用链` / `## Interface Call Chain` to the strict section lists, remove data-summary requirement 12, and add the same no-summary boundary.

- [ ] **Step 5: Run Task 1 tests and verify GREEN**

Run:

```powershell
cd backend
mvn -Dfrontend.skip=true -Dtest=RuleBasedAnalyticsServiceTest,PromptFactoryTest test
```

Expected: PASS.

### Task 3: ChatService Model Validation Tests

**Files:**
- Modify: `backend/src/test/java/com/brand/agentpoc/service/ChatServiceTest.java`

- [ ] **Step 1: Update analyticsPlan helper data to the new fallback format**

Update test fallback strings to include the new headings:

```java
## Interface Call Chain
1. Current date: 2026-05-20.
## Conclusion
fallback
## Data Support
<table><tr><td>fallback</td></tr></table>
## Short Analysis
fallback
## Problem Diagnosis & Solutions
fallback
## Improvement Suggestions
fallback
FOLLOW_UP_QUESTIONS:
1. next?
2. next?
```

- [ ] **Step 2: Add positive validation test with numbered heading**

Add or update `streamingAnalyticsAcceptsValidStructuredReplies` so the model reply starts with `## 1. Interface Call Chain`.

```java
String validReply = """
        ## 1. Interface Call Chain
        1. Current date: 2026-05-20.
        ## 2. Conclusion
        fallback
        ## 3. Data Support
        <table><tr><td>fallback</td></tr></table>
        ## 4. Short Analysis
        fallback
        ## 5. Problem Diagnosis & Solutions
        fallback
        ## 6. Improvement Suggestions
        fallback
        FOLLOW_UP_QUESTIONS:
        1. next?
        2. next?
        """.trim();
```

- [ ] **Step 3: Add negative validation tests for data summary and missing call chain**

Add two sync chat tests:

```java
@Test
void analyticsRequestsRejectRepliesThatAddDataSummary() {
    // arrange configured analytics request and plan
    when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
            new Generation(new AssistantMessage("""
                    ## Interface Call Chain
                    ok
                    ## Conclusion
                    fallback
                    ## Data Support
                    <table><tr><td>fallback</td></tr></table>
                    ## Short Analysis
                    fallback
                    ## Problem Diagnosis & Solutions
                    fallback
                    ## Improvement Suggestions
                    fallback
                    ## Data Summary
                    forbidden
                    FOLLOW_UP_QUESTIONS:
                    1. a
                    2. b
                    """))
    )));

    String reply = chatService.chat(request);

    assertThat(reply).isEqualTo(plan.fallbackReply().trim());
}

@Test
void analyticsRequestsRejectRepliesMissingInterfaceCallChain() {
    // same arrange, but start reply at ## Conclusion
    String reply = chatService.chat(request);
    assertThat(reply).isEqualTo(plan.fallbackReply().trim());
}
```

- [ ] **Step 4: Run focused ChatService tests and verify RED**

Run:

```powershell
cd backend
mvn -Dfrontend.skip=true -Dtest=ChatServiceTest test
```

Expected: FAIL because `ChatService` still requires exact old headings.

### Task 4: ChatService Model Validation Implementation

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/ChatService.java`

- [ ] **Step 1: Replace exact heading equality with regex sequence validation**

Add constants:

```java
private static final Pattern ZH_INTERFACE_CALL_CHAIN_HEADING =
        Pattern.compile("^##[\\s\\h]*(?:1[\\s\\h\\.、]*)?接口调用链[\\s\\h]*$", Pattern.MULTILINE);
```

Add generic matcher helpers:

```java
private boolean matchesExpectedHeadingSequence(String reply, String language) {
    List<String> headings = extractSecondLevelHeadingLines(reply);
    List<Pattern> expected = "zh".equals(language) ? zhHeadingPatterns() : enHeadingPatterns();
    if (headings.size() != expected.size()) {
        return false;
    }
    for (int i = 0; i < expected.size(); i++) {
        if (!expected.get(i).matcher(headings.get(i)).matches()) {
            return false;
        }
    }
    return true;
}

private List<String> extractSecondLevelHeadingLines(String reply) {
    Matcher matcher = Pattern.compile("(?m)^##[^\\r\\n]*$").matcher(reply);
    List<String> headings = new ArrayList<>();
    while (matcher.find()) {
        headings.add(matcher.group().trim());
    }
    return headings;
}
```

Use patterns like:

```java
Pattern.compile("^##[\\s\\h]*(?:2[\\s\\h\\.、]*)?核心结论[\\s\\h]*$")
Pattern.compile("^##[\\s\\h]*(?:2[\\s\\h\\.]*)?Conclusion[\\s\\h]*$")
```

- [ ] **Step 2: Reject data-summary variants explicitly**

Before table comparison, reject old or synonymous summary headings:

```java
if (containsForbiddenSummarySection(normalized)) {
    return false;
}
```

Implement:

```java
private boolean containsForbiddenSummarySection(String reply) {
    return Pattern.compile("(?im)^##[\\s\\h]*(?:\\d+[\\s\\h\\.、]*)?(数据汇总|Data Summary|数据总览|Data Overview|摘要段落|Summary)[\\s\\h]*$")
            .matcher(reply)
            .find();
}
```

- [ ] **Step 3: Derive language for validation**

Change `isValidAnalyticsReply(String reply, String fallbackReply)` to derive from fallback headings:

```java
private boolean isValidAnalyticsReply(String reply, String fallbackReply) {
    String normalized = reply == null ? "" : reply.trim();
    if (normalized.isBlank() || containsForbiddenSummarySection(normalized)) {
        return false;
    }
    String language = fallbackReply != null && fallbackReply.contains("## 接口调用链") ? "zh" : "en";
    if (!matchesExpectedHeadingSequence(normalized, language)) {
        return false;
    }
    ...
}
```

- [ ] **Step 4: Run ChatService tests and verify GREEN**

Run:

```powershell
cd backend
mvn -Dfrontend.skip=true -Dtest=ChatServiceTest test
```

Expected: PASS.

### Task 5: Excel Import Tests

**Files:**
- Modify: `backend/src/test/java/com/brand/agentpoc/service/ExcelImportServiceTest.java`

- [ ] **Step 1: Add campaign import test helpers**

Add `Campaign` import and captor helper:

```java
import com.brand.agentpoc.entity.Campaign;
```

Add overloaded import helper that accepts `CampaignRepository`:

```java
private ExcelImportService importService(
        TargetRepository targetRepository,
        DealerRepository dealerRepository,
        CampaignRepository campaignRepository,
        Path workbookPath
) {
    AppProperties properties = new AppProperties();
    properties.getExcel().setPath(workbookPath.toString());
    OpportunityRepository opportunityRepository = mock(OpportunityRepository.class);
    TaskRepository taskRepository = mock(TaskRepository.class);
    LeadRepository leadRepository = mock(LeadRepository.class);
    when(dealerRepository.count()).thenReturn(0L);
    when(opportunityRepository.count()).thenReturn(0L);
    when(campaignRepository.count()).thenReturn(0L);
    when(taskRepository.count()).thenReturn(0L);
    when(targetRepository.count()).thenReturn(0L);
    when(leadRepository.count()).thenReturn(0L);
    return new ExcelImportService(properties, new DefaultResourceLoader(), dealerRepository,
            opportunityRepository, campaignRepository, taskRepository, targetRepository, leadRepository);
}
```

Add workbook builder:

```java
private Path workbookWithCampaignRows(List<Object[]> campaignRows) throws Exception {
    Path workbookPath = tempDir.resolve("campaigns.xlsx");
    try (Workbook workbook = new XSSFWorkbook()) {
        Sheet sheet = workbook.createSheet("Campaign");
        Row header = sheet.createRow(0);
        String[] headers = {
                "Id", "Retailer__r.DealerCode__c", "Retailer__r.Name", "ProductModel__c",
                "CampaignType__c", "CreatedDate", "NumberOfOpportunities", "NewCustomerCount__c"
        };
        for (int column = 0; column < headers.length; column++) {
            header.createCell(column).setCellValue(headers[column]);
        }
        for (int i = 0; i < campaignRows.size(); i++) {
            Object[] values = campaignRows.get(i);
            Row row = sheet.createRow(i + 1);
            for (int column = 0; column < values.length; column++) {
                Object value = values[column];
                if (value instanceof Number number) {
                    row.createCell(column).setCellValue(number.doubleValue());
                } else if (value != null) {
                    row.createCell(column).setCellValue(String.valueOf(value));
                }
            }
        }
        try (OutputStream outputStream = Files.newOutputStream(workbookPath)) {
            workbook.write(outputStream);
        }
    }
    return workbookPath;
}
```

- [ ] **Step 2: Add failing campaign normalization test**

```java
@Test
void importsCampaignRowsWithBlankNonCriticalFieldsUsingDefaults() throws Exception {
    TargetRepository targetRepository = mock(TargetRepository.class);
    DealerRepository dealerRepository = mock(DealerRepository.class);
    CampaignRepository campaignRepository = mock(CampaignRepository.class);
    ExcelImportService service = importService(targetRepository, dealerRepository, campaignRepository,
            workbookWithCampaignRows(List.of(new Object[]{
                    "CAM-001", null, null, null, null, "2026-05-12", null, null
            })));

    service.run(mock(ApplicationArguments.class));

    List<Campaign> savedCampaigns = captureSavedCampaigns(campaignRepository);
    assertThat(savedCampaigns).hasSize(1);
    Campaign campaign = savedCampaigns.getFirst();
    assertThat(campaign.getCampaignId()).isEqualTo("CAM-001");
    assertThat(campaign.getCampaignType()).isEqualTo("0");
    assertThat(campaign.getDealerCode()).isEqualTo("未分配");
    assertThat(campaign.getDealerName()).isEqualTo("未分配");
    assertThat(campaign.getProductModel()).isEqualTo("未知");
    assertThat(campaign.getActualOpportunityCount()).isZero();
    assertThat(campaign.getTotalNewCustomerTarget()).isZero();
}
```

- [ ] **Step 3: Add captor helper**

```java
@SuppressWarnings({"unchecked", "rawtypes"})
private List<Campaign> captureSavedCampaigns(CampaignRepository campaignRepository) {
    ArgumentCaptor<Iterable<Campaign>> captor = ArgumentCaptor.forClass(Iterable.class);
    verify(campaignRepository).saveAll(captor.capture());
    List<Campaign> savedCampaigns = new ArrayList<>();
    captor.getValue().forEach(savedCampaigns::add);
    return savedCampaigns;
}
```

- [ ] **Step 4: Run ExcelImportServiceTest and verify RED**

Run:

```powershell
cd backend
mvn -Dfrontend.skip=true -Dtest=ExcelImportServiceTest test
```

Expected: FAIL because blank `campaignType` still skips the row.

### Task 6: Excel Import Implementation

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/ExcelImportService.java`

- [ ] **Step 1: Add import counters**

Add import:

```java
import java.util.concurrent.atomic.AtomicInteger;
```

Add fields:

```java
private final AtomicInteger campaignRowsProcessedCount = new AtomicInteger();
private final AtomicInteger normalizedRowsCount = new AtomicInteger();
```

- [ ] **Step 2: Reset and log counters around workbook parsing**

In `importWorkbook(...)`, reset before parsing and log in `finally`:

```java
private boolean importWorkbook(Resource resource) {
    log.info("Attempting workbook import from {}", resource);
    campaignRowsProcessedCount.set(0);
    normalizedRowsCount.set(0);

    try (InputStream inputStream = resource.getInputStream();
         Workbook workbook = WorkbookFactory.create(inputStream)) {
        ...
        return true;
    } catch (Exception exception) {
        log.error("Workbook import failed. Falling back to built-in sample data.", exception);
        return false;
    } finally {
        log.info("[Import-Normalization] Campaign import completed. Total rows processed: {}, rows with defaulted non-critical fields: {}.",
                campaignRowsProcessedCount.get(), normalizedRowsCount.get());
    }
}
```

- [ ] **Step 3: Normalize campaign fields**

Inside `parseCampaignSheet(...)`, increment processed count for each nonblank data row, default optional fields, and increment `normalizedRowsCount` once per row:

```java
campaignRowsProcessedCount.incrementAndGet();
boolean normalized = false;

if (campaignType == null) {
    campaignType = "0";
    normalized = true;
    log.debug("[Import-Normalization] Row {}: campaignType is blank, defaulting to '0'", rowIndex + 1);
}
if (dealerCode == null) {
    dealerCode = "未分配";
    normalized = true;
    log.debug("[Import-Normalization] Row {}: dealerCode is blank, defaulting to '未分配'", rowIndex + 1);
}
if (dealerName == null) {
    dealerName = "未分配";
    normalized = true;
    log.debug("[Import-Normalization] Row {}: dealerName is blank, defaulting to '未分配'", rowIndex + 1);
}
if (productModel == null) {
    productModel = "未知";
    normalized = true;
    log.debug("[Import-Normalization] Row {}: productModel is blank, defaulting to '未知'", rowIndex + 1);
}
if (actualOpportunityCount == null) {
    actualOpportunityCount = 0;
    normalized = true;
    log.debug("[Import-Normalization] Row {}: actualOpportunityCount is blank, defaulting to 0", rowIndex + 1);
}
if (totalNewCustomerTarget == null) {
    totalNewCustomerTarget = 0;
    normalized = true;
    log.debug("[Import-Normalization] Row {}: totalNewCustomerTarget is blank, defaulting to 0", rowIndex + 1);
}
if (normalized) {
    normalizedRowsCount.incrementAndGet();
}

if (hasBlank(campaignId) || createdDate == null) {
    log.debug("Skipping campaign row {} due to missing required values.", rowIndex + 1);
    continue;
}
```

Ensure required-field skipping occurs after reading fields but before entity creation; only `campaignId` and `createdDate` remain required.

- [ ] **Step 4: Run ExcelImportServiceTest and verify GREEN**

Run:

```powershell
cd backend
mvn -Dfrontend.skip=true -Dtest=ExcelImportServiceTest test
```

Expected: PASS.

### Task 7: Verification And Commit

**Files:**
- Verify all modified Java and test files.

- [ ] **Step 1: Run backend focused suite**

Run:

```powershell
cd backend
mvn -Dfrontend.skip=true -Dtest=RuleBasedAnalyticsServiceTest,PromptFactoryTest,ChatServiceTest,ExcelImportServiceTest test
```

Expected: PASS.

- [ ] **Step 2: Run broader backend test suite**

Run:

```powershell
cd backend
mvn -Dfrontend.skip=true test
```

Expected: PASS. If unrelated pre-existing tests fail, capture the failing class and reason before deciding whether to fix or report.

- [ ] **Step 3: Inspect git diff**

Run:

```powershell
git status --short
git diff -- backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java backend/src/main/java/com/brand/agentpoc/ai/PromptFactory.java backend/src/main/java/com/brand/agentpoc/service/ChatService.java backend/src/main/java/com/brand/agentpoc/service/ExcelImportService.java backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java backend/src/test/java/com/brand/agentpoc/ai/PromptFactoryTest.java backend/src/test/java/com/brand/agentpoc/service/ChatServiceTest.java backend/src/test/java/com/brand/agentpoc/service/ExcelImportServiceTest.java
```

Expected: only planned backend and test files changed, plus this plan file.

- [ ] **Step 4: Commit implementation**

Run:

```powershell
git add -- backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java backend/src/main/java/com/brand/agentpoc/ai/PromptFactory.java backend/src/main/java/com/brand/agentpoc/service/ChatService.java backend/src/main/java/com/brand/agentpoc/service/ExcelImportService.java backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java backend/src/test/java/com/brand/agentpoc/ai/PromptFactoryTest.java backend/src/test/java/com/brand/agentpoc/service/ChatServiceTest.java backend/src/test/java/com/brand/agentpoc/service/ExcelImportServiceTest.java docs/superpowers/plans/2026-05-20-backend-report-import-improvements.md
git commit -m "feat: improve backend analytics reporting and import normalization"
```

Expected: commit succeeds without staging `docs/改进方案.md` or the Excel temporary file.

## Self-Review

- Spec coverage: dynamic call chain, no data summary, tolerant regex validation, prompt negative constraints, campaign import defaults, normalization logs, positive/negative model tests, and import tests are each covered by tasks.
- Placeholder scan: no TBD/TODO placeholders remain.
- Type consistency: all referenced classes and helpers exist in the current codebase or are introduced in the task that first uses them.
