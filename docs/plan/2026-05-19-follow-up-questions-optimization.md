# Follow-Up Questions Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix follow-up questions appearing inconsistently and being irrelevant by fixing backend bugs, adding relevance validation, and improving prompt instructions.

**Architecture:** Backend changes in `ChatService.java` (bug fix + relevance validation + contextual fallback) and `PromptFactory.java` (improved model instructions). Frontend change in `chat.js` (parse dedup). Tests added for all new backend logic.

**Tech Stack:** Java 17+ / Spring AI / JUnit 5 + Mockito + AssertJ / Vue 3 / Vitest

---

## File Map

| File | Responsibility |
|---|---|
| `backend/.../ai/PromptFactory.java` | System & user prompts — strengthen follow-up relevance instructions |
| `backend/.../service/ChatService.java` | Core logic — fix bugs, add `extractTopicKeywords()`, `isStronglyRelevant()`, `validateFollowUpRelevance()`, `buildContextualFollowUps()` |
| `backend/.../service/ChatServiceTest.java` | Tests for new methods and bug fixes |
| `frontend/src/utils/chat.js` | Client-side parsing — add dedup, better tolerance |
| `frontend/src/utils/__tests__/chat.spec.js` | New — tests for improved parsing |
| `frontend/src/utils/modelErrors.js` | Remove obsolete error message mapping |

---

### Task 1: Fix Bug — repairPartialFollowUpQuestions graceful fallback

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/ChatService.java:675-706`
- Modify: `backend/src/test/java/com/brand/agentpoc/service/ChatServiceTest.java` (update test)

- [ ] **Step 1: Replace the throw with default substitution**

In `ChatService.java`, change the last few lines of `repairPartialFollowUpQuestions` from:

```java
        throw new IllegalStateException("Reply ended with an invalid follow-up section.");
    }
```

to:

```java
        return """
                %s
                1. %s
                2. %s
                """.formatted(prefix, defaults.getFirst(), defaults.getLast()).trim();
    }
```

Note: also remove the now-unreachable `throw` line and the `if (lines.size() == 1 ...)` early return — the logic should be one unified path.

The full fixed method:

```java
    private String repairPartialFollowUpQuestions(String reply, List<String> defaults) {
        int markerIndex = reply.indexOf("FOLLOW_UP_QUESTIONS:");
        if (markerIndex < 0) {
            return reply;
        }

        String marker = "FOLLOW_UP_QUESTIONS:";
        String prefix = reply.substring(0, markerIndex + marker.length());
        String followUpBlock = reply.substring(markerIndex + marker.length()).trim();
        List<String> lines = followUpBlock.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();

        if (lines.isEmpty()) {
            return """
                    %s
                    1. %s
                    2. %s
                    """.formatted(prefix, defaults.getFirst(), defaults.getLast()).trim();
        }

        if (lines.size() == 1 && lines.getFirst().matches("1\\.\\s+.+")) {
            return """
                    %s
                    %s
                    2. %s
                    """.formatted(prefix, lines.getFirst(), defaults.getLast()).trim();
        }

        return """
                %s
                1. %s
                2. %s
                """.formatted(prefix, defaults.getFirst(), defaults.getLast()).trim();
    }
```

- [ ] **Step 2: Run existing tests to see which ones are affected**

Run: `cd backend && mvn test -pl . -Dtest=ChatServiceTest -Dmaven.test.failure.ignore=false -q 2>&1 | tail -30`

- [ ] **Step 3: Update the test `repairsPartialStreamedFollowUpSections`**

The existing test at line 509 expects success (it already has a 1-line partial case that gets repaired). Verify it still passes. No changes needed — the behavior for the `lines.size() == 1` case is unchanged.

- [ ] **Step 4: Add a test for the case that used to throw (malformed 2+ lines without numbering)**

Add to `ChatServiceTest.java`:

```java
    @Test
    void repairsMalformedFollowUpSectionWithDefaults() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                "hello",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(languageDetector.detectLanguage("hello")).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt("en", "hello", "None")).thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Hello")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "\n\nFOLLOW_UP_QUESTIONS:\nunrelated text\nmore unrelated text"))))
        ));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(payload).contains("event: done");
        // Should not contain error event — was gracefully repaired
        assertThat(payload).doesNotContain("event: error");
        assertThat(payload).contains("FOLLOW_UP_QUESTIONS:");
    }
```

- [ ] **Step 5: Run tests**

Run: `cd backend && mvn test -pl . -Dtest=ChatServiceTest -Dmaven.test.failure.ignore=false -q 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/ChatService.java backend/src/test/java/com/brand/agentpoc/service/ChatServiceTest.java
git commit -m "fix: gracefully repair malformed follow-up sections instead of throwing"
```

---

### Task 2: Fix Bug — Remove analytics short-circuit in ensureFollowUpQuestions

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/ChatService.java:645-673`

- [ ] **Step 1: Remove the analytics early return**

In `ensureFollowUpQuestions`, change:

```java
    if (trimmed.contains("FOLLOW_UP_QUESTIONS:")) {
        if (analyticsRequested) {
            return trimmed;
        }
        return repairPartialFollowUpQuestions(trimmed, defaults);
    }
```

to:

```java
    if (trimmed.contains("FOLLOW_UP_QUESTIONS:")) {
        return repairPartialFollowUpQuestions(trimmed, defaults);
    }
```

- [ ] **Step 2: Run tests**

Run: `cd backend && mvn test -pl . -Dtest=ChatServiceTest -Dmaven.test.failure.ignore=false -q 2>&1 | tail -20`
Expected: All tests PASS (the analytics tests should still pass since `repairPartialFollowUpQuestions` now handles all cases gracefully)

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/ChatService.java
git commit -m "fix: remove analytics short-circuit so malformed follow-ups are always repaired"
```

---

### Task 3: Add Topic Keyword Extraction

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/ChatService.java` (add methods + constants)
- Modify: `backend/src/test/java/com/brand/agentpoc/service/ChatServiceTest.java` (add tests)

- [ ] **Step 1: Add constant maps and the extraction method**

Add to `ChatService.java` (after the existing `MAX_STREAMED_REPLY_CHARS` field):

```java
    private static final Map<String, List<String>> METRIC_TERMS = Map.of(
            "zh", List.of("达成率", "转化率", "商机", "线索", "任务", "活动", "ROI", "销量",
                    "赢单", "流失", "漏斗", "目标", "活跃度", "参与度", "跟进", "时效", "逾期"),
            "en", List.of("achievement", "conversion", "opportunity", "lead", "task", "campaign",
                    "ROI", "sales", "win", "drop-off", "funnel", "target", "activity", "participation",
                    "follow-up", "turnaround", "overdue")
    );

    private static final Map<String, List<String>> SCENARIO_TERMS = Map.of(
            "zh", List.of("目标达成", "商机漏斗", "转化分析", "销售跟进", "活动效果", "市场活动",
                    "经营对标", "对标分析", "线索来源", "自然流量", "经营活跃度", "门店活跃"),
            "en", List.of("target achievement", "opportunity funnel", "conversion analysis",
                    "sales follow-up", "campaign performance", "dealer benchmark", "lead source",
                    "organic traffic", "business activity", "dealer activity")
    );

    private static final Map<String, List<String>> GENERIC_TERMS = Map.of(
            "zh", List.of("表现", "情况", "数据", "怎么样", "如何", "方面", "内容", "信息", "问题", "了解", "可以", "什么"),
            "en", List.of("performance", "data", "how", "what", "about", "information", "details", "tell me")
    );
```

Add the extraction method:

```java
    List<String> extractTopicKeywords(String reply, String language) {
        Set<String> keywords = new LinkedHashSet<>();
        String normalized = reply == null ? "" : reply;

        for (String term : METRIC_TERMS.getOrDefault(language, METRIC_TERMS.get("en"))) {
            if (normalized.contains(term)) {
                keywords.add(term);
            }
        }

        for (String term : SCENARIO_TERMS.getOrDefault(language, SCENARIO_TERMS.get("en"))) {
            if (normalized.contains(term)) {
                keywords.add(term);
            }
        }

        return List.copyOf(keywords);
    }
```

Add the strong-relevance check method:

```java
    boolean isStronglyRelevant(String followUp, List<String> topicKeywords) {
        for (String keyword : topicKeywords) {
            if (followUp.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
```

(Add the `LinkedHashSet` import: `import java.util.LinkedHashSet;`)

- [ ] **Step 2: Write tests for topic keyword extraction**

Add to `ChatServiceTest.java`:

```java
    @Test
    void extractsMetricKeywordsFromChineseReply() {
        List<String> keywords = chatService.extractTopicKeywords(
                "北京朝阳店本月目标达成率为85%，商机转化率12%",
                "zh"
        );
        assertThat(keywords).contains("达成率", "转化率", "商机");
    }

    @Test
    void extractsScenarioKeywordsFromReply() {
        List<String> keywords = chatService.extractTopicKeywords(
                "## 目标达成分析\n门店对标数据显示华东区表现突出",
                "zh"
        );
        assertThat(keywords).contains("目标达成", "经营对标");
    }

    @Test
    void returnsEmptyForReplyWithNoKnownTerms() {
        List<String> keywords = chatService.extractTopicKeywords(
                "Hello, how are you today?",
                "en"
        );
        assertThat(keywords).isEmpty();
    }
```

- [ ] **Step 3: Write tests for strong relevance check**

```java
    @Test
    void marksFollowUpRelevantWhenItHitsATopicKeyword() {
        List<String> keywords = List.of("达成率", "转化率", "商机");

        assertThat(chatService.isStronglyRelevant("北京朝阳店的达成率如何提升？", keywords)).isTrue();
        assertThat(chatService.isStronglyRelevant("商机漏斗哪个阶段流失最多？", keywords)).isTrue();
    }

    @Test
    void marksFollowUpIrrelevantWhenMissingAllTopicKeywords() {
        List<String> keywords = List.of("达成率", "商机");

        assertThat(chatService.isStronglyRelevant("你想了解什么？", keywords)).isFalse();
        assertThat(chatService.isStronglyRelevant("还有其他需要帮助的吗？", keywords)).isFalse();
    }
```

- [ ] **Step 4: Run tests**

Run: `cd backend && mvn test -pl . -Dtest=ChatServiceTest -Dmaven.test.failure.ignore=false -q 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/ChatService.java backend/src/test/java/com/brand/agentpoc/service/ChatServiceTest.java
git commit -m "feat: add topic keyword extraction and relevance check for follow-up validation"
```

---

### Task 4: Add Contextual Follow-Up Generation

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/ChatService.java` (add methods)
- Modify: `backend/src/test/java/com/brand/agentpoc/service/ChatServiceTest.java` (add tests)

- [ ] **Step 1: Add the contextual follow-up builder method**

Add to `ChatService.java`:

```java
    List<String> buildContextualFollowUps(String language, String reply) {
        if (reply == null || reply.isBlank()) {
            return defaultGeneralFollowUps(language);
        }
        boolean isZh = "zh".equals(language);

        // Match scenario presence to pick the most relevant template
        if (reply.contains("目标达成") || reply.contains("达成率") || reply.contains("Target Achievement")) {
            return isZh
                    ? List.of("达成短板主要在哪个车型？", "要不要对比同城市其他店的达成率？")
                    : List.of("Which model drags down achievement the most?",
                            "Compare achievement rates across other city dealers?");
        }
        if (reply.contains("商机漏斗") || reply.contains("商机转化") || reply.contains("Opportunity Funnel")) {
            return isZh
                    ? List.of("哪个阶段的商机流失最严重？", "要不要按销售顾问拆分转化率？")
                    : List.of("Which funnel stage has the highest drop-off?",
                            "Break down conversion by sales consultant?");
        }
        if (reply.contains("销售跟进") || reply.contains("逾期") || reply.contains("Sales Follow-up")) {
            return isZh
                    ? List.of("逾期任务集中在哪些门店？", "要不要查看任务完成率的月度趋势？")
                    : List.of("Which dealers have the most overdue tasks?",
                            "Check monthly task completion trends?");
        }
        if (reply.contains("活动效果") || reply.contains("市场活动") || reply.contains("Campaign")) {
            return isZh
                    ? List.of("本次活动ROI和去年同期比如何？", "要不要看各门店的活动参与度排名？")
                    : List.of("How does this campaign ROI compare to last year?",
                            "Rank dealers by campaign participation?");
        }
        if (reply.contains("经营对标") || reply.contains("门店对标") || reply.contains("Dealer Benchmark")) {
            return isZh
                    ? List.of("要不要下钻到车型维度对比？", "这些门店的线索跟进时效如何？")
                    : List.of("Drill down by model dimension?",
                            "How is lead follow-up turnaround at these dealers?");
        }
        if (reply.contains("线索来源") || reply.contains("自然流量") || reply.contains("Lead Source")) {
            return isZh
                    ? List.of("高意向线索主要来自哪个渠道？", "要不要对比各门店的线索跟进速度？")
                    : List.of("Which channel generates the highest-intent leads?",
                            "Compare lead follow-up speed across dealers?");
        }
        if (reply.contains("经营活跃度") || reply.contains("门店活跃") || reply.contains("Business Activity")) {
            return isZh
                    ? List.of("活跃度最低的门店在哪个维度失分最多？", "要不要对比活跃度和目标达成率的关系？")
                    : List.of("Which dimension drags down the lowest-activity dealers?",
                            "Correlate activity score with target achievement?");
        }

        return defaultGeneralFollowUps(language);
    }
```

- [ ] **Step 2: Write tests for contextual follow-up generation**

Add to `ChatServiceTest.java`:

```java
    @Test
    void buildsTargetAchievementFollowUpsForChineseReply() {
        List<String> followUps = chatService.buildContextualFollowUps("zh",
                "## 核心结论\n北京朝阳店本月目标达成率为72%");
        assertThat(followUps).containsExactly(
                "达成短板主要在哪个车型？",
                "要不要对比同城市其他店的达成率？"
        );
    }

    @Test
    void buildsOpportunityFunnelFollowUpsForEnglishReply() {
        List<String> followUps = chatService.buildContextualFollowUps("en",
                "## Conclusion\nThe opportunity funnel shows high drop-off at Stage 2");
        assertThat(followUps).containsExactly(
                "Which funnel stage has the highest drop-off?",
                "Break down conversion by sales consultant?"
        );
    }

    @Test
    void fallsBackToGeneralFollowUpsForNonAnalyticsReply() {
        List<String> followUps = chatService.buildContextualFollowUps("zh",
                "你好，请问有什么可以帮助你的？");
        assertThat(followUps).hasSize(2);
        assertThat(followUps.get(0)).contains("商机");
    }
```

- [ ] **Step 3: Run tests**

Run: `cd backend && mvn test -pl . -Dtest=ChatServiceTest -Dmaven.test.failure.ignore=false -q 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/ChatService.java backend/src/test/java/com/brand/agentpoc/service/ChatServiceTest.java
git commit -m "feat: add context-aware follow-up question generation by scenario"
```

---

### Task 5: Wire Relevance Validation into the Reply Pipeline

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/ChatService.java` (`ensureFollowUpQuestions`)
- Modify: `backend/src/test/java/com/brand/agentpoc/service/ChatServiceTest.java` (add integration-style tests)

- [ ] **Step 1: Add validateFollowUpRelevance method**

Add to `ChatService.java`:

```java
    List<String> validateFollowUpRelevance(String reply, List<String> followUps, String language) {
        List<String> topicKeywords = extractTopicKeywords(reply, language);

        // If no keywords extracted (e.g., very short or generic reply), keep original follow-ups
        if (topicKeywords.isEmpty()) {
            return followUps;
        }

        List<String> validated = new ArrayList<>();
        for (String followUp : followUps) {
            if (isStronglyRelevant(followUp, topicKeywords)) {
                validated.add(followUp);
            }
        }

        if (validated.isEmpty()) {
            return buildContextualFollowUps(language, reply);
        }

        if (validated.size() == 1) {
            List<String> defaults = buildContextualFollowUps(language, reply);
            for (String candidate : defaults) {
                if (validated.size() >= 2) break;
                if (!validated.contains(candidate)) {
                    validated.add(candidate);
                }
            }
        }

        return validated.subList(0, Math.min(validated.size(), 2));
    }
```

- [ ] **Step 2: Extract followUps from reply and wire validation into ensureFollowUpQuestions**

First, add a helper to extract follow-up questions from the final reply text (mirroring frontend logic on backend):

```java
    List<String> extractFollowUpsFromReply(String reply) {
        String normalized = reply == null ? "" : reply;
        String[] markers = {"FOLLOW_UP_QUESTIONS:", "追问："};
        int markerIndex = -1;
        int markerLen = 0;

        for (String marker : markers) {
            int idx = normalized.indexOf(marker);
            if (idx >= 0 && (markerIndex < 0 || idx < markerIndex)) {
                markerIndex = idx;
                markerLen = marker.length();
            }
        }

        if (markerIndex < 0) {
            return List.of();
        }

        return normalized.substring(markerIndex + markerLen).lines()
                .map(line -> line.replaceFirst("^\\s*(?:\\d+\\.\\s*|[-*·•]\\s*)", "")
                        .replaceAll("[*_~]+", "").trim())
                .filter(line -> !line.isBlank())
                .limit(2)
                .toList();
    }
```

Now update `ensureFollowUpQuestions` to call validation:

```java
    private String ensureFollowUpQuestions(String reply, String language, boolean analyticsRequested) {
        String trimmed = reply == null ? "" : reply.trim();
        if (trimmed.isBlank()) {
            throw new IllegalStateException("Reply is blank after model generation.");
        }

        List<String> contextDefaults = buildContextualFollowUps(language, trimmed);

        String repaired;
        if (hasExactlyTwoFollowUpQuestions(trimmed)) {
            repaired = trimmed;
        } else if (trimmed.contains("FOLLOW_UP_QUESTIONS:") || trimmed.contains("追问：")) {
            repaired = repairPartialFollowUpQuestions(trimmed, contextDefaults);
        } else {
            return """
                    %s

                    FOLLOW_UP_QUESTIONS:
                    1. %s
                    2. %s
                    """.formatted(trimmed, contextDefaults.getFirst(), contextDefaults.getLast()).trim();
        }

        // Extract and validate follow-ups, then rebuild reply with validated follow-ups
        List<String> extracted = extractFollowUpsFromReply(repaired);
        if (extracted.size() == 2) {
            List<String> validated = validateFollowUpRelevance(repaired, extracted, language);
            return rebuildReplyWithFollowUps(repaired, validated);
        }

        return repaired;
    }

    private String rebuildReplyWithFollowUps(String reply, List<String> followUps) {
        String[] markers = {"FOLLOW_UP_QUESTIONS:", "追问："};
        for (String marker : markers) {
            int idx = reply.indexOf(marker);
            if (idx >= 0) {
                return reply.substring(0, idx + marker.length())
                        + "\n1. " + followUps.get(0)
                        + "\n2. " + followUps.get(1);
            }
        }
        return reply;
    }
```

- [ ] **Step 3: Write integration tests for the validation pipeline**

```java
    @Test
    void keepsRelevantFollowUpsDuringStreamingGeneralReply() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1", "北京朝阳店达成率如何？",
                "https://api.example.com", "sk-test", "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(languageDetector.detectLanguage(request.message())).thenReturn("zh");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt(eq("zh"), eq(request.message()), anyString()))
                .thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("zh")).thenReturn("System prompt");
        // Model generates relevant follow-ups
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "朝阳店本月达成率为72%。\n\n追问：\n1. 朝阳店的达成短板主要在哪个车型？\n2. 要不要对比北京其他店的达成率？"
                ))))
        ));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(payload).contains("达成短板主要在哪个车型");
        assertThat(payload).contains("对比北京其他店的达成率");
    }

    @Test
    void replacesIrrelevantFollowUpsDuringStreamingAnalyticsReply() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1", "Which dealers have the lowest target achievement?",
                "https://api.example.com", "sk-test", "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AnalyticsPlan plan = analyticsPlan(
                AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT,
                """
                ## Conclusion
                Store A has 45% achievement.
                ## Data Support
                <table><tr><td>Store A</td><td>45%</td></tr></table>
                ## Short Analysis
                Store A lags significantly.
                ## Problem Diagnosis & Solutions
                Gap in follow-up efficiency.
                ## Improvement Suggestions
                Increase lead outreach by 20%.
                ## Data Summary
                <table><tr><td>Achievement</td><td>45%</td></tr></table>
                FOLLOW_UP_QUESTIONS:
                1. What would you like to know more about?
                2. How can I help you further?
                """
        );

        when(languageDetector.detectLanguage(request.message())).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(analyticsService.plan(request.message(), "en")).thenReturn(plan);
        when(promptFactory.buildGroundedModelPrompt(eq("en"), eq(request.message()), anyString(), anyString()))
                .thenReturn("Grounded prompt");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "## Conclusion\nStore A has 45% achievement.\n"
                                + "## Data Support\n<table><tr><td>Store A</td><td>45%</td></tr></table>\n"
                                + "## Short Analysis\nStore A lags.\n"
                                + "## Problem Diagnosis & Solutions\nFollow-up gap.\n"
                                + "## Improvement Suggestions\nIncrease outreach.\n"
                                + "## Data Summary\n<table><tr><td>Achievement</td><td>45%</td></tr></table>\n"
                                + "FOLLOW_UP_QUESTIONS:\n1. What would you like to know more about?\n2. How can I help you further?"
                ))))
        ));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        // The irrelevant "what would you like to know" and "how can I help" should be replaced
        assertThat(payload).doesNotContain("What would you like to know more about");
        assertThat(payload).doesNotContain("How can I help you further");
        assertThat(payload).contains("FOLLOW_UP_QUESTIONS:");
    }
```

- [ ] **Step 4: Run tests**

Run: `cd backend && mvn test -pl . -Dtest=ChatServiceTest -Dmaven.test.failure.ignore=false -q 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/ChatService.java backend/src/test/java/com/brand/agentpoc/service/ChatServiceTest.java
git commit -m "feat: wire follow-up relevance validation into reply pipeline"
```

---

### Task 6: Improve PromptFactory Follow-Up Instructions

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/ai/PromptFactory.java`

- [ ] **Step 1: Update system prompt follow-up instructions**

In `buildSystemPrompt`, replace the follow-up line in the Chinese block:

From:
```
                    结尾必须保留一个 `追问：` 段落，并提供 2 个编号追问。
```

To:
```
                    `追问：` 段落要求：
                    - 必须包含且只包含 2 个编号追问
                    - 每个追问必须与当前分析的具体门店、指标或场景直接相关
                    - 追问应引导用户深化分析，例如：下钻到更细维度（城市/车型/时间）、对比其他门店、查看关联指标（商机/线索/任务）
                    - 禁止生成笼统的泛泛追问（如"你想了解什么"、"需要进一步分析吗"）
```

In the English block, replace:
```
                Always end with a `FOLLOW_UP_QUESTIONS:` section containing exactly 2 numbered follow-up questions.
```

To:
```
                `FOLLOW_UP_QUESTIONS:` requirements:
                - Must contain exactly 2 numbered follow-up questions
                - Each question must directly relate to the specific dealers, metrics, or scenarios discussed
                - Questions should guide deeper analysis, e.g., drill down to city/model/time dimensions, compare dealers, or explore related metrics
                - Never generate vague generic questions (like "What do you want to know?" or "How can I help?")
```

- [ ] **Step 2: Update grounded model prompt follow-up instructions**

In `buildGroundedModelPrompt` Chinese block, replace:
```
                    9. `追问：` 必须包含且只包含 2 个编号追问
```
With:
```
                    9. `追问：` 必须包含且只包含 2 个编号追问，每个追问必须与当前分析的具体门店、指标或场景直接相关，禁止泛泛追问
```

In English block, replace:
```
                9. `FOLLOW_UP_QUESTIONS:` must contain exactly 2 numbered questions.
```
With:
```
                9. `FOLLOW_UP_QUESTIONS:` must contain exactly 2 numbered questions, each directly tied to the specific dealers, metrics, or scenarios analyzed. No vague generic questions.
```

- [ ] **Step 3: Update conversation model prompt follow-up instructions**

In `buildConversationModelPrompt` Chinese block, replace:
```
                    5. 结尾必须保留 `追问：`，并给出 2 个编号追问
```
With:
```
                    5. 结尾必须保留 `追问：`，并给出 2 个编号追问，每个追问必须与当前对话主题的具体实体或指标直接相关，禁止笼统的泛泛追问
```

In English block, replace:
```
                5. End with `FOLLOW_UP_QUESTIONS:` and provide exactly 2 numbered follow-up questions
```
With:
```
                5. End with `FOLLOW_UP_QUESTIONS:` and provide exactly 2 numbered follow-up questions, each directly related to the specific entities or metrics discussed. No vague generic questions.
```

- [ ] **Step 4: Run backend tests to verify no regressions**

Run: `cd backend && mvn test -pl . -q 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/ai/PromptFactory.java
git commit -m "feat: strengthen follow-up question relevance instructions in all prompts"
```

---

### Task 7: Improve Frontend Parsing and Update Error Map

**Files:**
- Modify: `frontend/src/utils/chat.js` (add dedup, better tolerance)
- Create: `frontend/src/utils/__tests__/chat.spec.js` (new tests)
- Modify: `frontend/src/utils/modelErrors.js` (remove obsolete mapping)

- [ ] **Step 1: Add dedup and improve parse tolerance in chat.js**

In `parseFollowUpLines`, update to:

```javascript
function parseFollowUpLines(section) {
  return section
    .split(/\r?\n/)
    .map((line) => line
      .replace(/^\s*(?:\d+\.\s*|[-*·•]\s*)/, "")
      .replace(/\*{1,3}([^*]+)\*{1,3}/g, "$1")
      .replace(/_{1,2}([^_]+)_{1,2}/g, "$1")
      .replace(/~{2}([^~]+)~{2}/g, "$1")
      .replace(/^[*·•]+|[*·•]+$/g, "")
      .trim()
    )
    .filter(Boolean)
    .filter((item, index, array) => array.indexOf(item) === index) // dedup
    .slice(0, 2);
}
```

Also add a client-side fallback: if after parsing `followUps` is empty but the raw text contains `FOLLOW_UP_QUESTIONS:` or `追问：`, try a looser extraction (just grab the next two non-empty lines):

In `extractFollowUps`, after parsing, add:

```javascript
  const followUps = parseFollowUpLines(followUpSection);
  
  // Fallback: if regex parsing produced nothing but section has content, try looser extraction
  if (followUps.length === 0 && followUpSection.trim()) {
    const looseLines = followUpSection
      .split(/\r?\n/)
      .map(line => line.trim())
      .filter(Boolean)
      .slice(0, 2);
    if (looseLines.length > 0) {
      return { body, followUps: looseLines };
    }
  }

  return { body, followUps };
```

- [ ] **Step 2: Create frontend test file**

Create `frontend/src/utils/__tests__/chat.spec.js`:

```javascript
import { describe, expect, it } from "vitest";
import { normalizeAssistantPayload } from "../chat";

describe("normalizeAssistantPayload", () => {
  it("extracts follow-up questions from Chinese marker", () => {
    const result = normalizeAssistantPayload(
      "核心结论内容\n\n追问：\n1. 朝阳店的达成短板是什么？\n2. 要不要对比其他门店？"
    );
    expect(result.content).toBe("核心结论内容");
    expect(result.followUps).toEqual([
      "朝阳店的达成短板是什么？",
      "要不要对比其他门店？"
    ]);
  });

  it("deduplicates identical follow-up questions", () => {
    const result = normalizeAssistantPayload(
      "Some content\n\nFOLLOW_UP_QUESTIONS:\n1. Same question?\n2. Same question?"
    );
    expect(result.followUps).toHaveLength(1);
    expect(result.followUps[0]).toBe("Same question?");
  });

  it("returns empty followUps when no marker present", () => {
    const result = normalizeAssistantPayload("Just a plain reply with no follow-ups.");
    expect(result.content).toBe("Just a plain reply with no follow-ups.");
    expect(result.followUps).toEqual([]);
  });

  it("extracts follow-ups with mixed formatting", () => {
    const result = normalizeAssistantPayload(
      "Content\n\nFOLLOW_UP_QUESTIONS:\n- **First question?**\n- *Second question?*"
    );
    expect(result.followUps).toEqual([
      "First question?",
      "Second question?"
    ]);
  });

  it("loose fallback extracts lines even without proper numbering", () => {
    const result = normalizeAssistantPayload(
      "Content\n\n追问：\n   first question here   \n   second question here   "
    );
    expect(result.followUps).toHaveLength(2);
  });
});
```

- [ ] **Step 3: Run frontend tests**

Run: `cd frontend && npx vitest run --reporter=verbose 2>&1 | tail -30`
Expected: All tests PASS

- [ ] **Step 4: Update modelErrors.js — remove obsolete error entry**

In `frontend/src/utils/modelErrors.js`, remove line 131 (`|| normalized.includes("reply ended with an invalid follow-up section")`), and fix line 130 to end with `;` instead of `||`:

```javascript
// Before (lines 128-132):
function looksLikeInvalidResponseError(normalized) {
  return normalized.includes("configured model returned an empty response")
    || normalized.includes("reply is blank after model generation")
    || normalized.includes("reply ended with an invalid follow-up section");
}

// After:
function looksLikeInvalidResponseError(normalized) {
  return normalized.includes("configured model returned an empty response")
    || normalized.includes("reply is blank after model generation");
}
```

In `frontend/src/utils/__tests__/modelErrors.spec.js`, remove the second assertion from the test at lines 15-17:

```javascript
// Before:
  it("maps empty or malformed model replies to a response-specific message", () => {
    expect(getModelErrorMessage("Reply is blank after model generation.")).toBe(
      "The model returned an empty or invalid response. Please try again."
    );
    expect(getModelErrorMessage("Reply ended with an invalid follow-up section.")).toBe(
      "The model returned an empty or invalid response. Please try again."
    );
  });

// After:
  it("maps empty or malformed model replies to a response-specific message", () => {
    expect(getModelErrorMessage("Reply is blank after model generation.")).toBe(
      "The model returned an empty or invalid response. Please try again."
    );
  });
```

- [ ] **Step 5: Run all frontend tests**

Run: `cd frontend && npx vitest run --reporter=verbose 2>&1 | tail -30`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/utils/chat.js frontend/src/utils/__tests__/chat.spec.js frontend/src/utils/__tests__/modelErrors.spec.js frontend/src/utils/modelErrors.js
git commit -m "feat: add dedup and fallback to frontend follow-up parsing, remove obsolete error mapping"
```

---

### Task 8: Final Integration Verification

- [ ] **Step 1: Run full backend test suite**

Run: `cd backend && mvn test -pl . -q 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 2: Run full frontend test suite**

Run: `cd frontend && npx vitest run --reporter=verbose 2>&1 | tail -30`
Expected: All tests PASS

- [ ] **Step 3: Start backend and frontend, verify manually**

1. Start backend: `cd backend && mvn spring-boot:run`
2. Start frontend: `cd frontend && npm run dev`
3. Test cases:
   - Send analytics question → verify follow-up buttons appear and are relevant
   - Send general question → verify follow-up buttons appear with contextual defaults
   - Verify both model-generated relevant follow-ups are kept
   - Verify generic follow-ups from model are replaced
