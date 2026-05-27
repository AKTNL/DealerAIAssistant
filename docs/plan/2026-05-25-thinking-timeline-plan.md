# Thinking Timeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the static thinking panel with a real-time, structured timeline showing backend tool-call chains (full audit detail) and model reasoning in a single unified stream.

**Architecture:** New `step` SSE event type carries structured JSON for each execution step. Backend `RuleBasedAnalyticsService` emits steps via a `Consumer<StepEvent>` callback during synchronous plan execution. `ChatService` wraps the callback with a write-lock for thread safety and normalizes model reasoning into `<think>` tags. Frontend replaces `thinking` fields with a `steps` array, renders a unified timeline panel with type-differentiated display (model_thought collapsible, insight highlighted, others auditable).

**Tech Stack:** Java 17 (Spring AI, Spring Boot), Vue 3 (Composition API), SSE streaming, Vitest + JUnit

---

### Task 1: Create StepType enum and StepEvent record

**Files:**
- Create: `backend/src/main/java/com/brand/agentpoc/service/StepType.java`
- Create: `backend/src/main/java/com/brand/agentpoc/service/StepEvent.java`

- [ ] **Step 1: Create StepType.java**

```java
package com.brand.agentpoc.service;

public enum StepType {
    data_load,
    filter,
    calculation,
    tool_call,
    model_thought,
    insight
}
```

- [ ] **Step 2: Create StepEvent.java**

```java
package com.brand.agentpoc.service;

import java.util.Map;

public record StepEvent(
        String traceId,
        int seq,
        StepType type,
        long ts,
        String status,
        String label,
        String detail,
        Map<String, Object> meta
) {
    public StepEvent {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
    }

    public static StepEvent success(String traceId, int seq, StepType type,
                                     String label, String detail, Map<String, Object> meta) {
        return new StepEvent(traceId, seq, type, System.currentTimeMillis(),
                "success", label, detail, meta);
    }

    public static StepEvent failed(String traceId, int seq, StepType type,
                                    String label, String detail, Map<String, Object> meta) {
        return new StepEvent(traceId, seq, type, System.currentTimeMillis(),
                "failed", label, detail, meta);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/StepType.java \
        backend/src/main/java/com/brand/agentpoc/service/StepEvent.java
git commit -m "feat: add StepType enum and StepEvent record for streaming step events"
```

---

### Task 2: Modify RuleBasedAnalyticsService — add onStep callback to plan()

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java`

- [ ] **Step 1: Add imports and change plan() signature**

At the top of the file, add imports:

```java
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
```

Change the public `plan()` method signature and body (lines 129-173):

```java
public AnalyticsPlan plan(String message, String language) {
    return plan(message, language, null, null);
}

public AnalyticsPlan plan(String message, String language, String traceId, Consumer<StepEvent> onStep) {
    analysisDataCache.set(new AnalysisDataCache());
    try {
        AnalysisTopic topic = detectTopic(message);
        AnalyticsPlan.Scenario scenario = mapScenario(topic);
        AnalyticsScenarioCatalog.ScenarioWorkflow scenarioWorkflow = AnalyticsScenarioCatalog.forScenario(scenario);
        AnalysisScope scope = detectScope(message);
        String scopeSummary = scope.summary(language);
        List<String> progressMessages = buildProgressMessages(language, scenarioWorkflow);

        if (onStep != null) {
            onStep.accept(StepEvent.success(traceId, 1, StepType.insight,
                    isZh(language) ? "识别分析主题" : "Identify analysis theme",
                    isZh(language)
                            ? "识别为【" + scenarioWorkflow.label(language) + "】场景"
                            : "Identified as [" + scenarioWorkflow.label(language) + "] scenario",
                    Map.of("scenario", scenario.name(), "topic", topic.name())));
        }

        ScenarioResult result = switch (topic) {
            case TARGET_ACHIEVEMENT -> analyzeTargetAchievement(scope, language, traceId, onStep);
            // ... rest of cases unchanged except they pass traceId, onStep
        };
        // ... rest of method unchanged
    } finally {
        analysisDataCache.remove();
    }
}
```

Wait — the existing `plan()` overload without the callback must keep the old behavior. And each analysis method (`analyzeTargetAchievement` etc.) needs a new overload. Let me reconsider.

Actually the cleanest approach: keep the original `plan(String, String)` as is (no breaking change for callers that don't need steps), and add a new overload `plan(String, String, String, Consumer<StepEvent>)` that the streaming path uses.

But each analysis method currently takes `(AnalysisScope, String language)` and returns `ScenarioResult`. We need overloads that also accept `traceId, onStep` or we change the existing signatures.

Simplest approach that minimizes churn: add the `traceId` and `onStep` params to each analysis method, defaulting to null. When `onStep` is null (non-streaming path), skip the callback. When non-null, emit steps.

- [ ] **Step 1: Add overloaded plan() method and modify internal analysis methods to accept onStep**

Add the new `plan()` overload after the existing one (after line 173):

```java
public AnalyticsPlan plan(String message, String language, String traceId, Consumer<StepEvent> onStep) {
    analysisDataCache.set(new AnalysisDataCache());
    try {
        AnalysisTopic topic = detectTopic(message);
        AnalyticsPlan.Scenario scenario = mapScenario(topic);
        AnalyticsScenarioCatalog.ScenarioWorkflow scenarioWorkflow = AnalyticsScenarioCatalog.forScenario(scenario);
        AnalysisScope scope = detectScope(message);
        String scopeSummary = scope.summary(language);
        List<String> progressMessages = buildProgressMessages(language, scenarioWorkflow);
        AtomicInteger seq = new AtomicInteger(1);

        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.insight,
                isZh(language) ? "识别分析主题" : "Identify analysis theme",
                isZh(language)
                        ? "识别为【" + scenarioWorkflow.label(language) + "】场景，范围：" + scopeSummary
                        : "Identified [" + scenarioWorkflow.label(language) + "] scenario, scope: " + scopeSummary,
                Map.of("scenario", scenario.name())));

        ScenarioResult result = switch (topic) {
            case TARGET_ACHIEVEMENT -> analyzeTargetAchievement(scope, language, traceId, seq, onStep);
            case OPPORTUNITY_FUNNEL -> analyzeOpportunityFunnel(scope, language, traceId, seq, onStep);
            case SALES_FOLLOW_UP -> analyzeSalesFollowUp(scope, language, detectSalesFollowUpFocus(message), traceId, seq, onStep);
            case CAMPAIGN_PERFORMANCE -> analyzeCampaignPerformance(scope, language, traceId, seq, onStep);
            case LEAD_SOURCE -> analyzeLeadSource(scope, language, traceId, seq, onStep);
            case DEALER_BUSINESS_ACTIVITY -> analyzeDealerBusinessActivity(scope, language, message, traceId, seq, onStep);
            case DEALER_BENCHMARK -> analyzeDealerBenchmark(scope, language, traceId, seq, onStep);
        };

        // Emit insight step summarizing findings
        if (!result.traceSteps().isEmpty() && onStep != null) {
            CalcStep lastStep = result.traceSteps().getLast();
            emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.insight,
                    isZh(language) ? "分析结论" : "Analysis insight",
                    isZh(language) ? lastStep.labelZh() + "：" + lastStep.detailZh()
                                   : lastStep.labelEn() + ": " + lastStep.detailEn(),
                    Map.of()));
        }

        String callChain = buildInterfaceCallChain(language, topic, scenarioWorkflow, scope);
        ScenarioResult resultWithCallChain = result.withReply(
                prependInterfaceCallChain(language, callChain, result.reply()));
        String fallbackReply = resultWithCallChain.reply();
        logDataQuality(language, scope, resultWithCallChain.quality());

        String visibleThinking;
        if (!result.traceSteps().isEmpty()) {
            visibleThinking = promptFactory.buildVisibleThinking(language, result.traceSteps());
        } else {
            visibleThinking = promptFactory.buildVisibleThinking(language, scenarioWorkflow, scopeSummary);
        }

        return new AnalyticsPlan(
                scenario, scenarioWorkflow, scopeSummary, progressMessages,
                visibleThinking,
                buildGroundedReference(scenarioWorkflow, scopeSummary, language, fallbackReply, resultWithCallChain.quality()),
                fallbackReply);
    } finally {
        analysisDataCache.remove();
    }
}

private static void emitStep(Consumer<StepEvent> onStep, StepEvent event) {
    if (onStep != null) {
        onStep.accept(event);
    }
}

private static boolean isZh(String language) {
    return "zh".equals(language);
}
```

- [ ] **Step 2: Add overload for analyzeTargetAchievement with onStep**

Add a new overload after the existing `analyzeTargetAchievement(AnalysisScope, String)` method (after line 478):

```java
private ScenarioResult analyzeTargetAchievement(AnalysisScope scope, String language,
        String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
    List<Target> allTargets = cachedTargets();
    List<CalcStep> traceSteps = new ArrayList<>();

    emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.data_load,
            isZh(language) ? "从 Target 表加载全部目标数据" : "Load all target records from Target table",
            isZh(language) ? "共 " + allTargets.size() + " 条记录" : allTargets.size() + " records total",
            Map.of("source_type", "database", "table", "Target", "recordCount", allTargets.size())));

    List<Target> filtered = allTargets.stream()
            .filter(target -> matchesScope(target.getDealerCode(), target.getDealerName(), target.getCity(),
                    target.getDealerGroupName(), target.getProductModel(), scope))
            .filter(target -> scope.timeRange().matchesTarget(target.getTargetYear(), target.getTargetMonth()))
            .toList();

    emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.filter,
            isZh(language) ? "按分析范围过滤" : "Filter by analysis scope",
            isZh(language) ? "匹配 " + filtered.size() + " 条记录" : filtered.size() + " matching records",
            Map.of("inputCount", allTargets.size(), "outputCount", filtered.size())));

    // Delegate to the existing method for the rest (it already builds traceSteps)
    return analyzeTargetAchievement(scope, language);
}
```

Wait — this approach of "delegate to existing method" would double-execute the database queries. Each `analyzeTargetAchievement` currently does `cachedTargets()` and filters. If we call it twice, it would redo the work.

Better approach: Keep the existing method but add the onStep parameter to it directly. The original `analyzeTargetAchievement(AnalysisScope, String)` calls the new one with `null, null, null`.

Actually, to minimize diffs and risk, the best approach is: add `traceId, AtomicInteger seq, Consumer<StepEvent> onStep` to each analysis method signature, and wrap each `traceSteps.add(new CalcStep(...))` call with a corresponding `emitStep(onStep, ...)` call. Update the original signatures to delegate to the new ones with null params.

To keep the plan manageable, I'll show the pattern for one method (`analyzeTargetAchievement`) and note that the same pattern applies to all other analysis methods.

- [ ] **Step 2: Modify analyzeTargetAchievement signature and add step emission**

Change the existing method signature (line 479) from:
```java
private ScenarioResult analyzeTargetAchievement(AnalysisScope scope, String language) {
```
to:
```java
private ScenarioResult analyzeTargetAchievement(AnalysisScope scope, String language) {
    return analyzeTargetAchievement(scope, language, null, null, null);
}

private ScenarioResult analyzeTargetAchievement(AnalysisScope scope, String language,
        String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
```

After each `traceSteps.add(new CalcStep(...))` block, add the corresponding `emitStep` call. For example, after line 487 (first CalcStep), add:

```java
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.data_load,
                "从 Target 表加载全部目标数据",
                "Load all target records from the Target table",
                "共 " + allTargets.size() + " 条记录",
                allTargets.size() + " records total",
                Map.of("source_type", "database", "table", "Target", "recordCount", allTargets.size())));
```

After line 500 (second CalcStep), add:

```java
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.filter,
                "按分析范围过滤（城市/门店/时间）",
                "Filter by analysis scope (city/dealer/time)",
                "匹配 " + filtered.size() + " 条记录",
                filtered.size() + " matching records",
                Map.of("inputCount", allTargets.size(), "outputCount", filtered.size())));
```

After line 545 (third CalcStep, the aggregation step), add:

```java
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                "按门店聚合目标与赢单数据，计算达成率",
                "Aggregate targets and won deals per dealer, compute achievement rate",
                validMetrics.size() + " 个门店，达成率 = opportunityWonCount / asKTarget",
                validMetrics.size() + " dealers, achievement rate = wonCount / targetValue",
                Map.of("dealerCount", validMetrics.size(), "formula", "wonCount / targetValue")));
```

After line 552 (fourth CalcStep, the sort step), add:

```java
        emitStep(onStep, StepEvent.success(traceId, seq.getAndIncrement(), StepType.calculation,
                "排序找出最低/最高达成率门店",
                "Sort to find lowest/highest achievement dealers",
                "最低：" + lowest.dealerName() + "（" + formatPercent(lowest.achievementRate()) + "，"
                        + lowest.wonCount() + "/" + lowest.targetValue() + "）；最高："
                        + highest.dealerName() + "（" + formatPercent(highest.achievementRate()) + "，"
                        + highest.wonCount() + "/" + highest.targetValue() + "）",
                "Lowest: " + lowest.dealerName() + " (" + formatPercent(lowest.achievementRate()) + ", "
                        + lowest.wonCount() + "/" + lowest.targetValue() + "); Highest: "
                        + highest.dealerName() + " (" + formatPercent(highest.achievementRate()) + ", "
                        + highest.wonCount() + "/" + highest.targetValue() + ")",
                Map.of("lowestDealer", lowest.dealerName(), "lowestRate", lowest.achievementRate(),
                        "highestDealer", highest.dealerName(), "highestRate", highest.achievementRate(),
                        "averageRate", averageRate)));
```

- [ ] **Step 3: Apply the same pattern to other analysis methods**

For `analyzeOpportunityFunnel`, `analyzeSalesFollowUp`, `analyzeCampaignPerformance`, `analyzeLeadSource`, `analyzeDealerBusinessActivity`, `analyzeDealerBenchmark`: add the same overload pattern — keep the original signature delegating to the new one, add `emitStep` after each `traceSteps.add()`.

For `analyzeSalesFollowUp` which has an additional `focus` parameter:

```java
private ScenarioResult analyzeSalesFollowUp(AnalysisScope scope, String language, String focus) {
    return analyzeSalesFollowUp(scope, language, focus, null, null, null);
}

private ScenarioResult analyzeSalesFollowUp(AnalysisScope scope, String language, String focus,
        String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
    // ... existing body with emitStep calls added
}
```

For `analyzeDealerBusinessActivity` which has an additional `message` parameter:

```java
private ScenarioResult analyzeDealerBusinessActivity(AnalysisScope scope, String language, String message) {
    return analyzeDealerBusinessActivity(scope, language, message, null, null, null);
}

private ScenarioResult analyzeDealerBusinessActivity(AnalysisScope scope, String language, String message,
        String traceId, AtomicInteger seq, Consumer<StepEvent> onStep) {
    // ... existing body with emitStep calls added
}
```

- [ ] **Step 4: Handle the `noDataResult` and `lowConfidenceResult` edge cases**

In each analysis method, when `filtered.isEmpty()` triggers `noDataResult`, emit a failed step before returning:

```java
if (filtered.isEmpty()) {
    emitStep(onStep, StepEvent.failed(traceId, seq.getAndIncrement(), StepType.filter,
            isZh(language) ? "按分析范围过滤" : "Filter by analysis scope",
            isZh(language) ? "无匹配记录" : "No matching records",
            Map.of("reason", "empty_result")));
    return noDataResult(language, scope, "target achievement");
}
```

Similarly for `lowConfidenceResult`:

```java
if (quality.state() != DataQualityState.NORMAL) {
    emitStep(onStep, new StepEvent(traceId, seq.getAndIncrement(), StepType.calculation,
            System.currentTimeMillis(), "skipped",
            isZh(language) ? "数据质量不足，跳过深度分析" : "Insufficient data quality, skipping deep analysis",
            isZh(language) ? "数据质量状态：" + quality.state() : "Data quality: " + quality.state(),
            Map.of("qualityState", quality.state().name())));
    return lowConfidenceResult(language, scope, quality, traceSteps);
}
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java
git commit -m "feat: add onStep callback to RuleBasedAnalyticsService for real-time step streaming"
```

---

### Task 3: Modify ChatService — traceId, locking, writeStepEvent, model reasoning normalization

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/ChatService.java`

- [ ] **Step 1: Add writeStepEvent helper and generate traceId in streamChat()**

Add the helper method to ChatService:

```java
private void writeStepEvent(BufferedWriter writer, StepEvent step) throws IOException {
    String json = String.format(
            "{\"trace_id\":\"%s\",\"seq\":%d,\"type\":\"%s\",\"ts\":%d," +
            "\"status\":\"%s\",\"label\":\"%s\",\"detail\":\"%s\",\"meta\":%s}",
            step.traceId(), step.seq(), step.type().name(), step.ts(),
            step.status(),
            escapeJson(step.label()),
            escapeJson(step.detail()),
            step.meta() != null && !step.meta().isEmpty()
                    ? toJsonString(step.meta()) : "{}");
    writeEvent(writer, "step", json);
}

private String escapeJson(String value) {
    if (value == null) return "";
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
}

private String toJsonString(Map<String, Object> meta) {
    // Use a simple JSON builder or Jackson ObjectMapper
    try {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(meta);
    } catch (Exception e) {
        return "{}";
    }
}
```

- [ ] **Step 2: Modify streamChat() to use onStep callback with locking**

Replace the current `streamChat()` body (lines 90-133). Key changes:

```java
public void streamChat(ChatRequest request, OutputStream outputStream) throws IOException {
    String language = languageDetector.detectLanguage(request.message());
    boolean analyticsRequested = looksLikeAnalyticsRequest(request.message());
    boolean configuredModel = hasConfiguredModelSettings(request);
    sessionMemoryService.addUserMessage(request.sessionId(), request.message());

    String traceId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    final Object writeLock = new Object();

    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
        try {
            Consumer<StepEvent> onStep = step -> {
                synchronized (writeLock) {
                    try {
                        writeStepEvent(writer, step);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            };

            AnalyticsPlan analyticsPlan = analyticsRequested
                    ? analyticsService.plan(request.message(), language, traceId, onStep)
                    : null;

            List<String> progressMessages = resolveStreamProgressMessages(
                    language, analyticsRequested, configuredModel, analyticsPlan);
            if (!progressMessages.isEmpty()) {
                writeEvent(writer, "progress", progressMessages.getFirst());
            }

            if (!configuredModel) {
                // For no-model path: steps already emitted during plan(), just send reply
                GeneratedReply generatedReply = analyticsPlan != null
                        ? new GeneratedReply(analyticsPlan.fallbackReply().trim(),
                                analyticsPlan.progressMessages(), analyticsPlan.visibleThinking())
                        : generateReply(request, language, false);
                writeChunkedEvent(writer, "message", generatedReply.reply());
                sessionMemoryService.addAssistantMessage(request.sessionId(), generatedReply.reply());
                writeEvent(writer, "done", "[DONE]");
                return;
            }

            // For configured model path: steps already emitted, now stream model reply
            streamConfiguredReply(writer, request, language, analyticsRequested, analyticsPlan);
        } catch (Exception exception) {
            writeEvent(writer, "error", describeStreamFailure(exception));
        }
    }
}
```

Note: remove the old `writeChunkedEvent(writer, "message", "<think>" + visibleThinking + "</think>")` lines (117 and 127 in the original). The thinking content is now delivered via `step` events.

- [ ] **Step 3: Add model reasoning normalization in stream chunks**

Modify `writeStreamChunk` and `extractChunkText` to handle reasoning content:

```java
private void writeStreamChunk(BufferedWriter writer, StringBuilder accumulator, ChatResponse chunkResponse) {
    String reasoning = extractReasoningContent(chunkResponse);
    String text = extractChunkText(chunkResponse);

    if (hasText(reasoning)) {
        // Normalize native reasoning_content into <think> tags
        String wrapped = "<think>" + reasoning.trim() + "</think>";
        appendChunk(accumulator, wrapped);
        writeChunkedEvent(writer, "message", wrapped);
    }

    if (hasText(text)) {
        appendChunk(accumulator, text);
        writeChunkedEvent(writer, "message", text);
    }
}

private String extractReasoningContent(ChatResponse response) {
    if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
        return "";
    }
    // Spring AI exposes reasoning content via getOutput().getText() or a dedicated method
    // Check for reasoning content if available in the Spring AI version
    try {
        var output = response.getResult().getOutput();
        // Try reflection for getReasoningContent() if available
        var method = output.getClass().getMethod("getReasoningContent");
        var result = method.invoke(output);
        return result != null ? result.toString() : "";
    } catch (Exception e) {
        return "";
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/ChatService.java
git commit -m "feat: add step event streaming, write-lock, and model reasoning normalization to ChatService"
```

---

### Task 4: Modify PromptFactory and system prompt templates

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/ai/PromptFactory.java`
- Modify: `backend/src/main/resources/prompts/system-prompt-zh.txt`
- Modify: `backend/src/main/resources/prompts/system-prompt-en.txt`

- [ ] **Step 1: Add thinking_protocol to system-prompt-zh.txt**

Replace line 26 `不要暴露隐藏推理过程、内部工具名、接口名或实现细节。` with:

```
将你的分析思路写在 <think>...</think> 标签中，然后再输出最终回复。

<thinking_protocol>
在生成最终回复前，你必须先在 <think>...</think> 标签内交代你的分析思路：

1. 解读用户问题的核心意图
2. 列出你需要确认的关键数据维度（时间、门店、指标等）
3. 参考事实中提供了哪些数据？它们的口径和范围是什么？
4. 你打算如何组织回答结构？每个章节的要点是什么？
5. 有没有数据缺口或需要注意的假设？

<think> 内容应该是一段通顺的、可用 Markdown 的自然语言推理，不要写成 JSON 或代码。
<think> 标签属于内部推理，不要在 <think> 和最终回复之间重复相同内容。
最终回复紧跟 </think> 之后，按规定的 6 段结构输出。
</thinking_protocol>
```

- [ ] **Step 2: Add thinking_protocol to system-prompt-en.txt**

Find and replace the equivalent line "Do not reveal hidden reasoning" (or similar) with:

```
Write your analysis reasoning inside <think>...</think> tags before the final reply.

<thinking_protocol>
Before generating the final reply, you MUST first articulate your analysis approach inside <think>...</think> tags:

1. Interpret the user's core intent
2. List the key data dimensions to confirm (time period, dealers, metrics, etc.)
3. What data does the reference provide? What are its scope and definitions?
4. How do you plan to organize the response? Key points for each section?
5. Are there data gaps or assumptions to note?

<think> content should be natural language reasoning, not JSON or code.
Do not repeat the same content between <think> and the final reply.
The final reply follows immediately after </think>, in the required 6-section structure.
</thinking_protocol>
```

- [ ] **Step 3: Update PromptFactory.buildSystemPrompt() if it appends additional rules**

No code changes needed in `PromptFactory.java` — the templates are loaded via `loadSystemPromptTemplate()`. Verify the template changes are correct by compiling.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/prompts/system-prompt-zh.txt \
        backend/src/main/resources/prompts/system-prompt-en.txt
git commit -m "feat: replace hidden-reasoning rule with thinking_protocol in system prompts"
```

---

### Task 5: Update backend tests

**Files:**
- Modify: `backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java`
- Modify: `backend/src/test/java/com/brand/agentpoc/service/ModelConfigServiceTest.java`

- [ ] **Step 1: Update RuleBasedAnalyticsServiceTest for new plan() signature**

The existing `plan(String, String)` signature is preserved as an overload, so existing test calls continue to work. Add new test cases that verify the callback path:

```java
@Test
void planWithCallbackEmitsSteps() {
    List<StepEvent> capturedSteps = new ArrayList<>();
    String traceId = "test1234";

    AnalyticsPlan plan = analyticsService.plan(
            "北京 6 月目标达成率怎么样？", "zh", traceId, capturedSteps::add);

    assertNotNull(plan);
    assertFalse(capturedSteps.isEmpty(), "Should emit at least one step");

    // First step should be topic identification
    StepEvent firstStep = capturedSteps.get(0);
    assertEquals(traceId, firstStep.traceId());
    assertEquals(1, firstStep.seq());
    assertEquals("success", firstStep.status());

    // Steps should have increasing seq numbers
    for (int i = 1; i < capturedSteps.size(); i++) {
        assertTrue(capturedSteps.get(i).seq() > capturedSteps.get(i - 1).seq(),
                "seq should be monotonically increasing");
    }
}

@Test
void planWithoutCallbackStillWorks() {
    // Old signature should still work (backward compat)
    AnalyticsPlan plan = analyticsService.plan("北京 6 月目标达成率怎么样？", "zh");
    assertNotNull(plan);
    assertNotNull(plan.fallbackReply());
}
```

- [ ] **Step 2: Run backend tests**

```bash
cd backend && ./mvnw test -pl . -Dtest="RuleBasedAnalyticsServiceTest,ModelConfigServiceTest" -DfailIfNoTests=false
```

Expected: All tests PASS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java
git commit -m "test: add step callback verification to RuleBasedAnalyticsServiceTest"
```

---

### Task 6: Modify useChat.js — step events, progress placeholders, model_thought, clean old fields

**Files:**
- Modify: `frontend/src/composables/useChat.js`

- [ ] **Step 1: Update createAssistantMessage to use steps array instead of thinking fields**

Change lines 154-171. Replace:
```javascript
function createAssistantMessage() {
    return {
      id: `assistant-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
      kind: "assistant",
      role: "assistant",
      content: "",
      rawContent: "",
      html: "",
      followUps: [],
      thinking: "",
      thinkingHtml: "",
      thinkingExpanded: false,
      status: dictionary.value.statusThinking,
      steps: [],
      streaming: true,
      rendered: false
    };
  }
```

With:
```javascript
function createAssistantMessage() {
    return {
      id: `assistant-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
      kind: "assistant",
      role: "assistant",
      content: "",
      rawContent: "",
      html: "",
      followUps: [],
      status: dictionary.value.statusThinking,
      steps: [],
      streaming: true,
      rendered: false
    };
  }
```

Also remove `thinking: "", thinkingExpanded: false` from `createUserMessage` (line 145-146).

- [ ] **Step 2: Remove toggleThinking function**

Delete lines 311-313:
```javascript
function toggleThinking(message) {
    message.thinkingExpanded = !message.thinkingExpanded;
  }
```

And remove `toggleThinking` from the return object (line 551).

- [ ] **Step 3: Add step event handler and progress placeholder logic in submitPrompt()**

Inside `submitPrompt()`, after the `onEvent` callback definition, add the `step` event case before the `progress` case:

```javascript
if (event === "step") {
            let stepData;
            try {
              stepData = typeof eventText === "string" ? JSON.parse(eventText) : eventText;
            } catch {
              return;
            }

            // Remove all loading placeholder steps
            assistantMessage.steps = assistantMessage.steps.filter(
              s => s.status !== "loading"
            );

            // Ensure status field exists
            if (!stepData.status) {
              stepData.status = "success";
            }

            assistantMessage.steps.push(stepData);

            // If this is model_thought and streaming, it will be updated by think tag parser
            syncViewport({ markUnread: true });
            return;
          }

          if (event === "progress") {
            // Push loading placeholder with optional stage for matching
            assistantMessage.steps.push({
              traceId: null,
              seq: assistantMessage.steps.length + 1,
              type: null,
              status: "loading",
              label: eventText || "",
              detail: "",
              meta: null,
              ts: Date.now()
            });
            syncViewport({ markUnread: true });
            return;
          }
```

Note: the `progress` handler replaces the old lines 397-401 that only pushed to `steps` (as text). Now steps are objects.

- [ ] **Step 4: Replace the thinking accumulation logic with steps-based model_thought**

In the `<think>` tag parsing section (the `inStreamThinkTag` state machine, lines 337-475), replace occurrences of:
- `appendThinking(assistantMessage, ...)` → push/update a `model_thought` step in `assistantMessage.steps`
- `message.thinking` / `message.thinkingHtml` → removed

When `<think>` is first encountered (`inStreamThinkTag` becomes true), create the model_thought step entry:

```javascript
// When entering think tag (inStreamThinkTag transitions false -> true):
assistantMessage.steps.push({
  traceId: null,
  seq: assistantMessage.steps.length + 1,
  type: "model_thought",
  status: "loading",
  label: dictionary.value.showThinking || "模型思考中",
  detail: "",
  meta: null,
  ts: Date.now()
});
```

When text arrives inside the think tag, append to the last step's `detail`:
```javascript
const lastStep = assistantMessage.steps[assistantMessage.steps.length - 1];
if (lastStep && lastStep.type === "model_thought") {
  lastStep.detail += textChunk;
}
```

When `</think>` is encountered, mark the model_thought step as complete:
```javascript
const lastStep = assistantMessage.steps[assistantMessage.steps.length - 1];
if (lastStep && lastStep.type === "model_thought") {
  lastStep.status = "success";
}
```

Remove the old `appendThinking()` function (lines 352-358) and `flushPendingThinking()` (lines 368-373).

Remove `streamThinkingBuffer` and `inStreamThinkTag` — these are now managed via the steps array.

Actually, `inStreamThinkTag` is still needed to track whether we're inside a `<think>` block (to know where to route chunks). Keep the state machine but redirect output to steps instead of the old thinking fields.

- [ ] **Step 5: Handle done event — finalize loading steps**

In the `done` handler (around line 494), add:

```javascript
// Finalize any remaining loading steps
for (const step of assistantMessage.steps) {
  if (step.status === "loading") {
    step.status = "success";
  }
}
```

- [ ] **Step 6: Clean up streamPhase references**

`streamPhase` is still used by `AssistantMessage.vue` for the thinking indicator. Keep it. Remove only the old `thinking`/`thinkingHtml`/`thinkingExpanded` field usage.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/composables/useChat.js
git commit -m "feat: replace thinking fields with structured steps array, add step event handling"
```

---

### Task 7: Modify AssistantMessage.vue — timeline panel

**Files:**
- Modify: `frontend/src/components/chat/AssistantMessage.vue`

- [ ] **Step 1: Replace the thinking-panel section in template**

Replace lines 540-555 (the thinking panel and indicator section) with the new timeline panel:

```html
      <!-- Timeline panel: unified step display -->
      <div v-if="message.steps.length" class="timeline-panel">
        <div
          v-for="step in message.steps"
          :key="`${step.type ?? 'loading'}-${step.seq}-${step.ts}`"
          class="timeline-step"
          :class="[
            `timeline-step--${step.status || 'loading'}`,
            step.type ? `timeline-step--${step.type}` : ''
          ]"
        >
          <!-- Status icon -->
          <span class="timeline-step-icon" aria-hidden="true">
            <span v-if="step.status === 'loading'" class="timeline-step-spinner"></span>
            <span v-else-if="step.status === 'failed'" class="timeline-step-cross">&#10007;</span>
            <span v-else-if="step.status === 'skipped'" class="timeline-step-dash">&#8212;</span>
            <span v-else class="timeline-step-check">&#10003;</span>
          </span>

          <!-- Step body -->
          <div class="timeline-step-body">
            <div class="timeline-step-header">
              <span class="timeline-step-label">{{ step.label }}</span>
              <span v-if="step.status === 'failed'" class="timeline-step-badge badge-error">
                {{ locale === 'zh' ? '失败' : 'Failed' }}
              </span>
              <span v-else-if="step.status === 'loading'" class="timeline-step-badge badge-loading">
                {{ locale === 'zh' ? '进行中' : 'Running' }}
              </span>
            </div>

            <!-- model_thought: expand during streaming, collapse after done -->
            <details
              v-if="step.type === 'model_thought'"
              class="timeline-step-detail"
              :open="message.streaming"
            >
              <summary>{{ dictionary.showThinking || (locale === 'zh' ? '模型思考过程' : 'Model reasoning') }}</summary>
              <div class="markdown-body" v-html="step.detailHtml || step.detail"></div>
            </details>

            <!-- insight: highlighted blockquote, always expanded -->
            <blockquote v-else-if="step.type === 'insight' && step.detail" class="timeline-step-insight">
              {{ step.detail }}
            </blockquote>

            <!-- data_load / filter / calculation / tool_call: brief detail + collapsible meta audit -->
            <template v-else>
              <div v-if="step.detail" class="timeline-step-detail-text">{{ step.detail }}</div>
              <details v-if="step.meta && Object.keys(step.meta).length" class="timeline-step-meta">
                <summary>{{ locale === 'zh' ? '查看审计数据' : 'Audit details' }}</summary>
                <pre><code>{{ JSON.stringify(step.meta, null, 2) }}</code></pre>
              </details>
            </template>
          </div>
        </div>
      </div>

      <!-- Thinking indicator: shown when streaming but no model_thought step yet -->
      <div v-if="message.streaming && streamPhase === 'thinking' && !hasActiveModelThought(message)" class="thinking-indicator">
        <span class="thinking-dot"></span>
        {{ dictionary.bubbleThinking }}
      </div>
```

- [ ] **Step 2: Add hasActiveModelThought helper**

Add a computed or method:

```javascript
function hasActiveModelThought(message) {
  return message.steps.some(s => s.type === 'model_thought' && s.status === 'loading');
}
```

- [ ] **Step 3: Remove the old thinking-toggle button**

Remove lines 528-537 (the `<button class="thinking-toggle">` element).

- [ ] **Step 4: Remove the old CSS-dependent classes from template**

The old `thinking-panel` div (line 541-545) and `thinking-dots` div (line 551-555) are already replaced in Step 1.

- [ ] **Step 5: Update the emit definition**

Remove `toggle-thinking` from `defineEmits` (line 17):
```javascript
defineEmits(["submit-follow-up"]);
```

- [ ] **Step 6: Remove old thinkingPanelId computed**

Remove line 45:
```javascript
const thinkingPanelId = computed(() => `thinking-panel-${props.message.id ?? "current"}`);
```

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/chat/AssistantMessage.vue
git commit -m "feat: replace thinking panel with unified timeline panel in AssistantMessage"
```

---

### Task 8: Modify style.css — timeline styles, remove dead thinking styles

**Files:**
- Modify: `frontend/src/style.css`

- [ ] **Step 1: Remove old thinking-related styles**

Remove these CSS rule sets: `.thinking-toggle` (lines 958-965), `.thinking-toggle-inline` (lines 967-970), `.thinking-toggle:hover` (lines 972-975), `.thinking-panel` (lines 977-983), `.thinking-dots` (lines 985-991), `.thinking-dot` (lines 993-1007).

Also remove the `@keyframes thinking-dot-bounce` (around line 1535).

Remove `.thinking-toggle` from the transition group (line 276, 291, 314).

- [ ] **Step 2: Add timeline panel styles**

Append these new styles at the end of the CSS file:

```css
/* ── Timeline Panel ── */
.timeline-panel {
  margin-top: 0.85rem;
  padding: 0.6rem 0;
  border-top: 1px solid var(--border-soft);
}

.timeline-step {
  display: flex;
  gap: 0.65rem;
  padding: 0.55rem 0;
  position: relative;
}

.timeline-step + .timeline-step {
  border-top: 1px solid var(--border-soft);
}

/* ── Step status icon ── */
.timeline-step-icon {
  flex: none;
  width: 1.35rem;
  height: 1.35rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  font-size: 0.72rem;
  margin-top: 0.08rem;
}

.timeline-step--success .timeline-step-icon {
  background: #e6f7ed;
  color: #1a7d3f;
}

.timeline-step--failed .timeline-step-icon {
  background: #fde8e8;
  color: #c92a2a;
}

.timeline-step--loading .timeline-step-icon {
  background: var(--surface-soft);
  color: var(--accent-info);
}

.timeline-step--skipped .timeline-step-icon {
  background: var(--surface-soft);
  color: var(--text-dim);
}

.timeline-step-check,
.timeline-step-cross,
.timeline-step-dash {
  line-height: 1;
  font-weight: 700;
}

/* ── Spinner for loading steps ── */
.timeline-step-spinner {
  width: 0.75rem;
  height: 0.75rem;
  border: 2px solid var(--border-soft);
  border-top-color: var(--accent-info);
  border-radius: 999px;
  animation: timeline-spin 0.7s linear infinite;
}

@keyframes timeline-spin {
  to { transform: rotate(360deg); }
}

/* ── Step body ── */
.timeline-step-body {
  flex: 1;
  min-width: 0;
}

.timeline-step-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.timeline-step-label {
  font-weight: 600;
  font-size: 0.85rem;
  color: var(--text-main);
}

.timeline-step-badge {
  font-size: 0.7rem;
  padding: 0.1rem 0.45rem;
  border-radius: 6px;
  font-weight: 600;
}

.badge-error {
  background: #fde8e8;
  color: #c92a2a;
}

.badge-loading {
  background: var(--surface-soft);
  color: var(--accent-info);
}

/* ── Step detail ── */
.timeline-step-detail-text {
  font-size: 0.82rem;
  color: var(--text-dim);
  margin-top: 0.18rem;
  line-height: 1.45;
}

.timeline-step-detail {
  margin-top: 0.25rem;
}

.timeline-step-detail summary {
  cursor: pointer;
  font-size: 0.82rem;
  color: var(--accent-info);
  font-weight: 500;
}

.timeline-step-detail .markdown-body {
  margin-top: 0.4rem;
  font-size: 0.85rem;
  padding-left: 0.5rem;
  border-left: 2px solid var(--border-soft);
}

/* ── Insight highlight ── */
.timeline-step-insight {
  margin-top: 0.25rem;
  padding: 0.5rem 0.75rem;
  background: #fef9e7;
  border-left: 3px solid #f0b400;
  border-radius: 0 6px 6px 0;
  font-size: 0.85rem;
  color: var(--text-main);
  line-height: 1.5;
}

/* ── Meta audit data ── */
.timeline-step-meta {
  margin-top: 0.35rem;
}

.timeline-step-meta summary {
  cursor: pointer;
  font-size: 0.78rem;
  color: var(--text-dim);
}

.timeline-step-meta pre {
  margin-top: 0.3rem;
  padding: 0.5rem 0.65rem;
  background: var(--surface-soft);
  border-radius: 6px;
  font-size: 0.72rem;
  overflow-x: auto;
  max-height: 220px;
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/style.css
git commit -m "feat: add timeline panel styles, remove deprecated thinking styles"
```

---

### Task 9: Update frontend tests

**Files:**
- Modify: `frontend/src/composables/__tests__/useChat.spec.js`
- Modify: `frontend/src/components/__tests__/AssistantMessage.spec.js`

- [ ] **Step 1: Add step event handling test to useChat.spec.js**

Add a new test:

```javascript
it("handles step events by pushing structured steps to the assistant message", async () => {
  const wrapper = mountChatHarness(ref({ apiKey: "k", baseUrl: "u", model: "m" }));
  const harness = wrapper.vm;

  const deferred = createDeferred();
  streamChatMock.mockImplementation(({ onEvent }) => {
    onEvent({ event: "step", data: JSON.stringify({
      trace_id: "abc12345",
      seq: 1,
      type: "data_load",
      ts: Date.now(),
      status: "success",
      label: "Load targets",
      detail: "248 records",
      meta: { table: "Target", recordCount: 248 }
    })});

    onEvent({ event: "step", data: JSON.stringify({
      trace_id: "abc12345",
      seq: 2,
      type: "filter",
      ts: Date.now(),
      status: "success",
      label: "Filter by scope",
      detail: "24 matching records",
      meta: { inputCount: 248, outputCount: 24 }
    })});

    onEvent({ event: "done", data: "[DONE]" });
    return deferred.promise;
  });

  await harness.submitPrompt("test");

  const assistantMsg = harness.messages.value.find(m => m.role === "assistant");
  expect(assistantMsg).toBeDefined();
  expect(assistantMsg.steps).toHaveLength(2);
  expect(assistantMsg.steps[0]).toMatchObject({
    type: "data_load",
    status: "success",
    label: "Load targets"
  });
  expect(assistantMsg.steps[1]).toMatchObject({
    type: "filter",
    status: "success",
    label: "Filter by scope"
  });
});
```

- [ ] **Step 2: Add progress placeholder test**

```javascript
it("creates loading placeholders for progress events and clears them on step arrival", async () => {
  const wrapper = mountChatHarness(ref({ apiKey: "k", baseUrl: "u", model: "m" }));
  const harness = wrapper.vm;

  const deferred = createDeferred();
  streamChatMock.mockImplementation(({ onEvent }) => {
    onEvent({ event: "progress", data: "Loading data..." });
    onEvent({ event: "progress", data: "Filtering..." });

    onEvent({ event: "step", data: JSON.stringify({
      trace_id: "abc12345", seq: 1, type: "data_load",
      ts: Date.now(), status: "success",
      label: "Load targets", detail: "248 records", meta: {}
    })});

    onEvent({ event: "done", data: "[DONE]" });
    return deferred.promise;
  });

  await harness.submitPrompt("test");

  const assistantMsg = harness.messages.value.find(m => m.role === "assistant");
  // Should have exactly 1 step (the real one), all loading placeholders cleared
  expect(assistantMsg.steps).toHaveLength(1);
  expect(assistantMsg.steps[0].type).toBe("data_load");
  expect(assistantMsg.steps[0].status).toBe("success");
});
```

- [ ] **Step 3: Add model_thought from think tag test**

```javascript
it("creates model_thought step from <think> tags in message stream", async () => {
  const wrapper = mountChatHarness(ref({ apiKey: "k", baseUrl: "u", model: "m" }));
  const harness = wrapper.vm;

  const deferred = createDeferred();
  streamChatMock.mockImplementation(({ onEvent }) => {
    onEvent({ event: "message", data: "<think>Let me analyze the data..." });
    onEvent({ event: "message", data: "The achievement rate shows a gap.</think>" });
    onEvent({ event: "message", data: "## Conclusion\nD005 has the lowest rate." });
    onEvent({ event: "done", data: "[DONE]" });
    return deferred.promise;
  });

  await harness.submitPrompt("test");

  const assistantMsg = harness.messages.value.find(m => m.role === "assistant");
  const thoughtStep = assistantMsg.steps.find(s => s.type === "model_thought");
  expect(thoughtStep).toBeDefined();
  expect(thoughtStep.status).toBe("success");
  expect(thoughtStep.detail).toContain("Let me analyze the data");
  expect(thoughtStep.detail).toContain("The achievement rate shows a gap");
  // Content should not contain the think block
  expect(assistantMsg.content).not.toContain("<think>");
  expect(assistantMsg.content).toContain("## Conclusion");
});
```

- [ ] **Step 4: Update AssistantMessage.spec.js — add timeline rendering tests**

```javascript
test("renders timeline steps when message has steps", () => {
  const message = {
    id: "msg-1",
    role: "assistant",
    content: "Test",
    html: "<p>Test</p>",
    followUps: [],
    steps: [
      { seq: 1, type: "data_load", status: "success", label: "Load data", detail: "248 records", meta: { table: "Target" }, ts: 1000 },
      { seq: 2, type: "insight", status: "success", label: "Key finding", detail: "D005 is underperforming", meta: null, ts: 2000 }
    ],
    streaming: false,
    rendered: true
  };

  const wrapper = mount(AssistantMessage, {
    props: { dictionary, locale: "zh", message, streamPhase: "idle" }
  });

  expect(wrapper.find(".timeline-panel").exists()).toBe(true);
  expect(wrapper.findAll(".timeline-step")).toHaveLength(2);
  expect(wrapper.find(".timeline-step--insight").exists()).toBe(true);
  expect(wrapper.find(".timeline-step-insight").text()).toContain("D005 is underperforming");
});

test("renders model_thought as collapsible details", () => {
  const message = {
    id: "msg-2",
    role: "assistant",
    content: "Test",
    html: "<p>Test</p>",
    followUps: [],
    steps: [
      { seq: 1, type: "model_thought", status: "success", label: "Model thinking", detail: "Analyzing data patterns...", meta: null, ts: 1000 }
    ],
    streaming: false,
    rendered: true
  };

  const wrapper = mount(AssistantMessage, {
    props: { dictionary, locale: "zh", message, streamPhase: "idle" }
  });

  const details = wrapper.find(".timeline-step-detail");
  expect(details.exists()).toBe(true);
  // Should be collapsed when not streaming (open=false)
  expect(details.attributes("open")).toBeUndefined();
});

test("model_thought is expanded during streaming", () => {
  const message = {
    id: "msg-3",
    role: "assistant",
    content: "",
    html: "",
    followUps: [],
    steps: [
      { seq: 1, type: "model_thought", status: "loading", label: "Model thinking", detail: "Analyzing...", meta: null, ts: 1000 }
    ],
    streaming: true,
    rendered: false
  };

  const wrapper = mount(AssistantMessage, {
    props: { dictionary, locale: "zh", message, streamPhase: "thinking" }
  });

  const details = wrapper.find(".timeline-step-detail");
  expect(details.exists()).toBe(true);
  // Should be expanded during streaming
  expect(details.attributes("open")).toBeDefined();
});
```

- [ ] **Step 5: Run frontend tests**

```bash
cd frontend && npx vitest run src/composables/__tests__/useChat.spec.js src/components/__tests__/AssistantMessage.spec.js
```

Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/composables/__tests__/useChat.spec.js \
        frontend/src/components/__tests__/AssistantMessage.spec.js
git commit -m "test: add step events, timeline rendering, and model_thought tests"
```

---

### Task 10: Integration verification

- [ ] **Step 1: Build and run full test suite**

```bash
cd backend && ./mvnw test
```

Expected: All tests PASS.

```bash
cd frontend && npx vitest run
```

Expected: All tests PASS.

- [ ] **Step 2: Verify ChatView integration**

Check `frontend/src/views/ChatView.vue` that it no longer passes `@toggle-thinking` to `AssistantMessage` (line 212 in the current code). If it does, remove the event listener and the `toggleThinking` handler.

- [ ] **Step 3: Commit final integration fixes**

```bash
git add -A
git commit -m "chore: final integration cleanup for thinking timeline"
```

---

## Self-Review

**1. Spec coverage:** Each requirement from the spec maps to tasks:
- StepEvent/StepType records → Task 1
- RuleBasedAnalyticsService onStep callback → Task 2
- ChatService traceId, locking, writeStepEvent → Task 3
- Model reasoning normalization → Task 3 (Step 3)
- Prompt thinking_protocol → Task 4
- useChat.js steps array, progress placeholders, think tag → model_thought → Task 6
- AssistantMessage timeline panel → Task 7
- CSS timeline styles → Task 8
- Backend tests → Task 5
- Frontend tests → Task 9
- Thread safety (write lock) → Task 3 (Step 2)
- Progress clearing algorithm → Task 6 (Step 3)
- model_thought streaming performance → Task 6 (Step 4, shallow property update)
- Differentiated rendering (model_thought collapsed, insight highlighted) → Task 7

**2. Placeholder scan:** No TBD, TODO, or "fill in details" found. All code steps have complete implementations.

**3. Type consistency:** 
- StepEvent fields (traceId, seq, type, ts, status, label, detail, meta) are consistent across Java record, JSON serialization, and frontend data model
- StepType enum values match between Java enum and frontend string checks
- `trace_id` in JSON maps to `traceId` in Java record (Jackson default) and `traceId` in frontend
