package com.brand.agentpoc.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.brand.agentpoc.service.AnalyticsPlan;
import com.brand.agentpoc.service.AnalyticsScenarioCatalog;
import java.net.URL;
import org.junit.jupiter.api.Test;

class PromptFactoryTest {

    private final PromptFactory promptFactory = new PromptFactory();

    @Test
    void systemPromptTemplatesAreExternalResources() {
        ClassLoader classLoader = PromptFactory.class.getClassLoader();
        URL zhTemplate = classLoader.getResource("prompts/system-prompt-zh.txt");
        URL enTemplate = classLoader.getResource("prompts/system-prompt-en.txt");

        assertThat(zhTemplate).isNotNull();
        assertThat(enTemplate).isNotNull();
    }

    @Test
    void systemPromptDocumentsWorkflowAndScenarioMatrixInEnglish() {
        String prompt = promptFactory.buildSystemPrompt("en");

        assertThat(prompt).contains("User Query -> Context Assembly -> Intent Recognition -> Tool Selection -> Data Processing -> Report Generation -> Streaming");
        assertThat(prompt).contains("Target Achievement Analysis");
        assertThat(prompt).contains("Opportunity Funnel & Conversion Analysis");
        assertThat(prompt).contains("Sales Follow-up Analysis");
        assertThat(prompt).contains("Campaign Planning & Performance Analysis");
        assertThat(prompt).contains("Dealer Benchmark Analysis");
        assertThat(prompt).contains("Lead Source & Organic Traffic Trend Analysis");
        assertThat(prompt).contains("getCurrentDate() -> searchDealers() -> queryTargets()");
        assertThat(prompt).contains("getCurrentDate() -> queryLeads(leadSource=");
    }

    @Test
    void groundedAnalyticsPromptAsksForRicherStructuredAnalysisInEnglish() {
        String prompt = promptFactory.buildGroundedModelPrompt(
                "en",
                "Which dealers have the lowest target achievement?",
                "None",
                "Scenario: TARGET_ACHIEVEMENT"
        );

        assertThat(prompt).contains("well-structured analytics report");
        assertThat(prompt).contains("2-4 bullets covering key findings");
        assertThat(prompt).contains("business takeaway");
        assertThat(prompt).contains("only source of truth");
        assertThat(prompt).contains("Do not write an unproven assumption as a root cause");
        assertThat(prompt).contains("at most 2 numbered questions");
        assertThat(prompt).doesNotContain("exactly 2 numbered questions");
    }

    @Test
    void groundedAnalyticsPromptUsesReadableChineseInstructions() {
        String prompt = promptFactory.buildGroundedModelPrompt(
                "zh",
                "本月哪些经销商目标达成率最低？",
                "无",
                "Scenario: TARGET_ACHIEVEMENT"
        );

        assertThat(prompt).contains("当前用户问题");
        assertThat(prompt).contains("使用中文");
        assertThat(prompt).contains("2-4 条要点");
        assertThat(prompt).contains("## 数据支撑");
        assertThat(prompt).contains("只能基于参考事实作答");
        assertThat(prompt).contains("禁止把未证实推测写成根因");
        assertThat(prompt).contains("允许不输出追问");
        assertThat(prompt).doesNotContain("必须包含且只包含 2 个编号追问");
    }

    @Test
    void systemPromptDoesNotForceReportStructureInChinese() {
        String prompt = promptFactory.buildSystemPrompt("zh");

        assertThat(prompt).contains("经销商");
        assertThat(prompt).doesNotContain("## 接口调用链");
        assertThat(prompt).doesNotContain("## 核心结论");
        assertThat(prompt).doesNotContain("## 数据支撑");
        assertThat(prompt).doesNotContain("## 经营分析");
        assertThat(prompt).doesNotContain("## 问题诊断与解决");
        assertThat(prompt).doesNotContain("## 改进建议");
        assertThat(prompt).doesNotContain("严禁修改以下二级标题的任何文字");
        assertThat(prompt).doesNotContain("## 数据汇总");
    }

    @Test
    void systemPromptDoesNotForceReportStructureInEnglish() {
        String prompt = promptFactory.buildSystemPrompt("en");

        assertThat(prompt).contains("dealer operations");
        assertThat(prompt).doesNotContain("## Interface Call Chain");
        assertThat(prompt).doesNotContain("## Conclusion");
        assertThat(prompt).doesNotContain("## Data Support");
        assertThat(prompt).doesNotContain("## Short Analysis");
        assertThat(prompt).doesNotContain("## Problem Diagnosis & Solutions");
        assertThat(prompt).doesNotContain("## Improvement Suggestions");
        assertThat(prompt).doesNotContain("Do not change any required level-2 heading text");
        assertThat(prompt).doesNotContain("Do not create any data overview");
        assertThat(prompt).doesNotContain("## Data Summary");
    }

    @Test
    void groundedPromptIncludesNewSectionInstructionsInChinese() {
        String prompt = promptFactory.buildGroundedModelPrompt(
                "zh", "本月目标达成", "无", "Scenario: TARGET_ACHIEVEMENT");

        assertThat(prompt).contains("## 接口调用链");
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

    @Test
    void conversationPromptDoesNotRequireAnalyticsReportStructureInChinese() {
        String prompt = promptFactory.buildConversationModelPrompt("zh", "你好", "无");

        assertThat(prompt).contains("自然");
        assertThat(prompt).doesNotContain("## 接口调用链");
        assertThat(prompt).doesNotContain("## 核心结论");
        assertThat(prompt).doesNotContain("## 数据支撑");
        assertThat(prompt).doesNotContain("## 经营分析");
        assertThat(prompt).doesNotContain("## 问题诊断与解决");
        assertThat(prompt).doesNotContain("## 改进建议");
    }

    @Test
    void conversationPromptDoesNotRequireAnalyticsReportStructureInEnglish() {
        String prompt = promptFactory.buildConversationModelPrompt("en", "hello", "None");

        assertThat(prompt).contains("natural");
        assertThat(prompt).doesNotContain("## Interface Call Chain");
        assertThat(prompt).doesNotContain("## Conclusion");
        assertThat(prompt).doesNotContain("## Data Support");
        assertThat(prompt).doesNotContain("## Short Analysis");
        assertThat(prompt).doesNotContain("## Problem Diagnosis & Solutions");
        assertThat(prompt).doesNotContain("## Improvement Suggestions");
    }

    @Test
    void visibleThinkingProvidesMoreDetailedExecutionSummary() {
        String thinking = promptFactory.buildVisibleThinking(
                "en",
                AnalyticsScenarioCatalog.forScenario(AnalyticsPlan.Scenario.OPPORTUNITY_FUNNEL),
                "Beijing / current month"
        );

        assertThat(thinking).contains("Opportunity Funnel & Conversion Analysis");
        assertThat(thinking).contains("Beijing / current month");
        assertThat(thinking).contains("standard chain");
        assertThat(thinking).contains("separate facts, supported judgments, and assumptions");
        assertThat(thinking).doesNotContainPattern("(?m)^\\s*\\d+\\.");
    }

    @Test
    void visibleThinkingWithTraceFormatsNaturalChineseOutput() {
        java.util.List<CalcStep> steps = java.util.List.of(
                new CalcStep("从 Task 表加载", "Load from Task table", "共 57,582 条", "57,582 records"),
                new CalcStep("过滤未完成任务", "Filter open tasks", "共 1,234 条", "1,234 open tasks")
        );

        String thinking = promptFactory.buildVisibleThinking("zh", steps);

        assertThat(thinking).contains("我会依据已完成的计算轨迹组织分析");
        assertThat(thinking).contains("从 Task 表加载（共 57,582 条）");
        assertThat(thinking).contains("过滤未完成任务（共 1,234 条）");
        assertThat(thinking).doesNotContainPattern("(?m)^\\s*\\d+\\.");
    }

    @Test
    void visibleThinkingWithTraceFormatsNaturalEnglishOutput() {
        java.util.List<CalcStep> steps = java.util.List.of(
                new CalcStep("从 Task 表加载", "Load from Task table", "共 57,582 条", "57,582 records"),
                new CalcStep("过滤未完成任务", "Filter open tasks", "共 1,234 条", "1,234 open tasks")
        );

        String thinking = promptFactory.buildVisibleThinking("en", steps);

        assertThat(thinking).contains("I will base the analysis on the completed calculation trace");
        assertThat(thinking).contains("Load from Task table (57,582 records)");
        assertThat(thinking).contains("Filter open tasks (1,234 open tasks)");
        assertThat(thinking).doesNotContainPattern("(?m)^\\s*\\d+\\.");
    }

    @Test
    void visibleThinkingWithNullTraceReturnsEmpty() {
        assertThat(promptFactory.buildVisibleThinking("zh", (java.util.List<CalcStep>) null)).isEmpty();
    }

    @Test
    void visibleThinkingWithEmptyTraceReturnsEmpty() {
        assertThat(promptFactory.buildVisibleThinking("zh", java.util.List.of())).isEmpty();
    }

    @Test
    void visibleThinkingWithNullFieldsUsesEmptyStrings() {
        java.util.List<CalcStep> steps = java.util.List.of(
                new CalcStep(null, null, null, null)
        );

        String thinking = promptFactory.buildVisibleThinking("zh", steps);

        assertThat(thinking).isEqualTo("我会依据已完成的计算轨迹组织分析。");
    }

    @Test
    void systemPromptDoesNotRequireThinkingOrFixedReportStructureForGeneralConversation() {
        String prompt = promptFactory.buildSystemPrompt("en");

        assertThat(prompt).doesNotContain("Before generating the final reply");
        assertThat(prompt).doesNotContain("The final reply follows immediately after </think>");
        assertThat(prompt).doesNotContain("1. Interpret the user's core intent");
    }
}
