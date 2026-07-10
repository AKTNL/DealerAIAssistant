package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.ai.CalcStep;
import com.brand.agentpoc.ai.PromptFactory;
import com.brand.agentpoc.dto.response.DataQueryResponse;
import com.brand.agentpoc.entity.Campaign;
import com.brand.agentpoc.entity.Dealer;
import com.brand.agentpoc.entity.Lead;
import com.brand.agentpoc.entity.Opportunity;
import com.brand.agentpoc.entity.Task;
import com.brand.agentpoc.entity.Target;
import com.brand.agentpoc.repository.CampaignRepository;
import com.brand.agentpoc.repository.DealerRepository;
import com.brand.agentpoc.repository.LeadRepository;
import com.brand.agentpoc.repository.OpportunityRepository;
import com.brand.agentpoc.repository.TargetRepository;
import com.brand.agentpoc.repository.TaskRepository;
import com.brand.agentpoc.test.LogCapture;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

class RuleBasedAnalyticsServiceTest {

    private PromptFactory promptFactory;
    private DealerRepository dealerRepository;
    private OpportunityRepository opportunityRepository;
    private CampaignRepository campaignRepository;
    private TaskRepository taskRepository;
    private TargetRepository targetRepository;
    private LeadRepository leadRepository;
    private DataQueryService dataQueryService;
    private RuleBasedAnalyticsService service;

    @BeforeEach
    void setUp() {
        promptFactory = mock(PromptFactory.class);
        dealerRepository = mock(DealerRepository.class);
        opportunityRepository = mock(OpportunityRepository.class);
        campaignRepository = mock(CampaignRepository.class);
        taskRepository = mock(TaskRepository.class);
        targetRepository = mock(TargetRepository.class);
        leadRepository = mock(LeadRepository.class);
        dataQueryService = mock(DataQueryService.class);

        when(dealerRepository.findAll()).thenReturn(List.of());
        when(opportunityRepository.findAll()).thenReturn(List.of());
        when(campaignRepository.findAll()).thenReturn(List.of());
        when(taskRepository.findAll()).thenReturn(List.of());
        when(targetRepository.findAll()).thenReturn(List.of());
        when(leadRepository.findAll()).thenReturn(List.of());
        when(dataQueryService.query(anyString(), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new DataQueryResponse("empty", Map.of(), 0, List.of(), Map.of()));
        when(promptFactory.buildVisibleThinking(
                anyString(),
                org.mockito.ArgumentMatchers.any(AnalyticsScenarioCatalog.ScenarioWorkflow.class),
                anyString()
        )).thenReturn("visible-thinking");

        service = new RuleBasedAnalyticsService(
                promptFactory,
                dealerRepository,
                opportunityRepository,
                campaignRepository,
                taskRepository,
                targetRepository,
                leadRepository,
                dataQueryService
        );
    }

    @ParameterizedTest
    @MethodSource("scenarioCases")
    void planMapsInternalTopicsToAnalyticsScenarios(String message, AnalyticsPlan.Scenario expectedScenario) {
        AnalyticsPlan plan = service.plan(message, "en");

        assertThat(plan.scenario()).isEqualTo(expectedScenario);
        assertThat(plan.scenarioWorkflow()).isNotNull();
        assertThat(plan.scenarioWorkflow().scenario()).isEqualTo(expectedScenario);
        assertThat(plan.scopeSummary()).isEqualTo("the current sample dataset");
        assertThat(plan.progressMessages()).hasSize(3);
        assertThat(plan.visibleThinking()).isEqualTo("visible-thinking");
    }

    @Test
    void multiEntityCountQuestionRoutesToDataOverviewBeforeCampaign() {
        AnalyticsPlan plan = service.plan(
                "\u6837\u672c\u6570\u636e\u91cc\u4e00\u5171\u6709\u591a\u5c11\u6761\u5546\u673a\u3001\u7ebf\u7d22\u3001\u4efb\u52a1\u548c\u5e02\u573a\u6d3b\u52a8\uff1f",
                "zh"
        );

        assertThat(plan.scenario()).isEqualTo(AnalyticsPlan.Scenario.DATA_OVERVIEW);
        assertThat(plan.fallbackReply()).contains("\u5546\u673a");
        assertThat(plan.fallbackReply()).contains("\u7ebf\u7d22");
        assertThat(plan.fallbackReply()).contains("\u4efb\u52a1");
        assertThat(plan.fallbackReply()).contains("\u5e02\u573a\u6d3b\u52a8");
    }

    @Test
    void planExposesScenarioWorkflowMetadataForTargetAchievement() {
        AnalyticsPlan plan = service.plan("Which dealers have the lowest target achievement this month?", "en");

        assertThat(plan.scenarioWorkflow().scenario()).isEqualTo(AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT);
        assertThat(plan.scenarioWorkflow().label("en")).isEqualTo("Target Achievement Analysis");
        assertThat(plan.scenarioWorkflow().toolChain()).containsExactly(
                "getCurrentDate()",
                "searchDealers()",
                "queryTargets()"
        );
        assertThat(plan.scenarioWorkflow().logicSummary("en")).contains("achievement");
        assertThat(plan.groundedReference()).contains("Tool Chain: getCurrentDate() -> searchDealers() -> queryTargets()");
    }

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
        assertThat(plan.scenarioWorkflow().logicSummary("zh")).contains("四个维度");
    }

    @Test
    void planBuildsTheExpectedFallbackContractWhenNoDataMatches() {
        AnalyticsPlan plan = service.plan("show target achievement overview", "en");

        assertThat(plan.scenario()).isEqualTo(AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT);
        assertThat(plan.fallbackReply()).contains("## Conclusion");
        assertThat(plan.fallbackReply()).contains("## Data Support");
        assertThat(plan.fallbackReply()).contains("<table>");
        assertThat(plan.fallbackReply()).contains("FOLLOW_UP_QUESTIONS:");
        assertThat(plan.fallbackReply()).contains("## Short Analysis");
        assertThat(plan.fallbackReply()).contains("**Recommendations:**");
        assertThat(plan.fallbackReply()).contains("## Short Analysis");
        assertThat(plan.groundedReference()).contains("Scenario: TARGET_ACHIEVEMENT");
        assertThat(plan.groundedReference()).contains("Scenario Label: Target Achievement Analysis");
        assertThat(plan.groundedReference()).contains("Scope: the current sample dataset");
        assertThat(plan.groundedReference()).contains("Language: en");
        assertThat(plan.groundedReference()).contains("Workflow: User Query -> Context Assembly -> Intent Recognition -> Tool Selection -> Data Processing -> Report Generation -> Streaming");
        assertThat(plan.groundedReference()).contains("Canonical fallback report:");
        assertThat(plan.groundedReference()).contains(plan.fallbackReply());
    }

    @Test
    void planUsesTheKpiBackedReplyWhenDataMatches() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("D001", "Dealer A", "Beijing", "Group A")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 5, 10, 5, 0),
                new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 5, 10, 8, 0)
        ));

        AnalyticsPlan plan = service.plan("target achievement for Dealer A in Beijing", "en");

        assertThat(plan.scenario()).isEqualTo(AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT);
        assertThat(plan.scopeSummary()).isEqualTo("Beijing / Group A / Dealer A");
        assertThat(plan.fallbackReply()).contains("## Conclusion");
        assertThat(plan.fallbackReply()).contains("## Data Support");
        assertThat(plan.fallbackReply()).contains("<table>");
        assertThat(plan.fallbackReply()).contains("65.0%");
        assertThat(plan.fallbackReply()).contains("13 / 20");
        assertThat(plan.groundedReference()).contains("Scenario: TARGET_ACHIEVEMENT");
        assertThat(plan.groundedReference()).contains("Tool Chain: getCurrentDate() -> searchDealers() -> queryTargets()");
        assertThat(plan.groundedReference()).contains("Scope: Beijing / Group A / Dealer A");
        assertThat(plan.groundedReference()).contains(plan.fallbackReply());
    }

    @Test
    void planKeepsTheAnalyticsContractInZhForNoDataReplies() {
        AnalyticsPlan plan = service.plan("\u5317\u4eac\u95e8\u5e97\u76ee\u6807\u8fbe\u6210\u6982\u89c8", "zh");

        assertThat(plan.fallbackReply()).contains("## \u6838\u5fc3\u7ed3\u8bba");
        assertThat(plan.fallbackReply()).contains("## \u6570\u636e\u652f\u6491");
        assertThat(plan.fallbackReply()).contains("<table>");
        assertThat(plan.fallbackReply()).contains("## 经营分析");
        assertThat(plan.fallbackReply()).contains("**可执行建议：**");
        assertThat(plan.fallbackReply()).doesNotContain("## Short Analysis");
        assertThat(plan.fallbackReply()).contains("追问：");
        assertThat(plan.fallbackReply()).doesNotContain("There is not enough data within");
        assertThat(plan.fallbackReply()).contains("\u7f3a\u5c11\u8db3\u591f\u6570\u636e");
        assertThat(plan.fallbackReply()).contains("\u76ee\u6807\u8fbe\u6210");
        assertThat(plan.fallbackReply()).contains("<th>\u6307\u6807</th>");
        assertThat(plan.fallbackReply()).contains("<th>\u6570\u503c</th>");
        assertThat(plan.fallbackReply()).contains("\u53ef\u7528\u6837\u672c\u8bb0\u5f55");
        assertThat(plan.fallbackReply()).doesNotContain("Metric</th>");
        assertThat(plan.fallbackReply()).doesNotContain("Value</th>");
        assertThat(plan.fallbackReply()).doesNotContain("Available sample records");
        assertThat(plan.scopeSummary()).isEqualTo("\u5317\u4eac");
        assertThat(plan.progressMessages()).containsExactly(
                "\u6b63\u5728\u8fdb\u884c\u610f\u56fe\u8bc6\u522b\u4e0e\u573a\u666f\u5f52\u7c7b",
                "\u6b63\u5728\u6309\u5de5\u5177\u94fe\u6267\u884c\uff1agetCurrentDate() -> searchDealers() -> queryTargets()",
                "\u6b63\u5728\u5904\u7406\u6570\u636e\u5e76\u751f\u6210\u7ed3\u6784\u5316\u62a5\u544a"
        );
        assertThat(plan.groundedReference()).contains("Language: zh");
        assertThat(plan.groundedReference()).contains(plan.fallbackReply());
    }

    @Test
    void planKeepsTheAnalyticsContractInZhForDataBackedReplies() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("D001", "Dealer A", "Beijing", "Group A")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 5, 10, 5, 0),
                new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 5, 10, 8, 0)
        ));

        AnalyticsPlan plan = service.plan("\u5317\u4eac Dealer A \u7684\u76ee\u6807\u8fbe\u6210", "zh");

        assertThat(plan.fallbackReply()).contains("## 核心结论");
        assertThat(plan.fallbackReply()).contains("## 数据支撑");
        assertThat(plan.fallbackReply()).contains("<table>");
        assertThat(plan.fallbackReply()).contains("## 经营分析");
        assertThat(plan.fallbackReply()).contains("**可执行建议：**");
        assertThat(plan.fallbackReply()).doesNotContain("## Short Analysis");
        assertThat(plan.fallbackReply()).contains("追问：");
        assertThat(plan.fallbackReply()).doesNotContain("Within Beijing / Group A / Dealer A");
        assertThat(plan.fallbackReply()).contains("\u5317\u4eac / Group A / Dealer A");
        assertThat(plan.fallbackReply()).contains("\u76ee\u6807\u8fbe\u6210\u7387\u4ec5");
        assertThat(plan.fallbackReply()).contains("<th>\u6307\u6807</th>");
        assertThat(plan.fallbackReply()).contains("<th>\u6570\u503c</th>");
        assertThat(plan.fallbackReply()).contains("\u8986\u76d6\u95e8\u5e97\u6570");
        assertThat(plan.fallbackReply()).doesNotContain("Dealers covered");
        assertThat(plan.groundedReference()).contains("Language: zh");
        assertThat(plan.groundedReference()).contains(plan.fallbackReply());
    }

    @Test
    void planDoesNotTreatSingleDealerAboveTargetAsRedWarningInZh() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("7037", "经销商AK(太原)", "太原", "经销商集团Q")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("7037", "经销商AK(太原)", "太原", "经销商集团Q", "Aurora S", 2026, 5, 100, 120, 0)
        ));

        AnalyticsPlan plan = service.plan("经销商AK(太原)的目标达成率", "zh");

        assertThat(plan.scopeSummary()).isEqualTo("太原 / 经销商集团Q / 经销商AK(太原)");
        assertThat(plan.fallbackReply()).contains("120.0%");
        assertThat(plan.fallbackReply()).contains("超额完成");
        assertThat(plan.fallbackReply()).doesNotContain("红色预警");
        assertThat(plan.fallbackReply()).doesNotContain("不到平均水平的一半");
        assertThat(plan.fallbackReply()).doesNotContain("目标达成率仅 **120.0%**");
        assertThat(plan.fallbackReply()).doesNotContain("清理 0 条");
    }

    @Test
    void planAggregatesDealerTargetRowsBeforeRankingDealers() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("D001", "Dealer A", "Beijing", "Group A"),
                new Dealer("D002", "Dealer B", "Beijing", "Group A")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 5, 5, 1, 0),
                new Target("D001", "Dealer A", "Beijing", "Group A", "Model Y", 2026, 5, 20, 19, 0),
                new Target("D002", "Dealer B", "Beijing", "Group A", "Model X", 2026, 5, 10, 7, 0)
        ));

        AnalyticsPlan targetPlan = service.plan("target achievement for Beijing dealers", "en");
        AnalyticsPlan benchmarkPlan = service.plan("benchmark comparison for Beijing stores", "en");

        assertThat(targetPlan.fallbackReply()).contains("**Dealer B** has the lowest target achievement");
        assertThat(targetPlan.fallbackReply()).contains("Dealer B (7 / 10)");
        assertThat(targetPlan.fallbackReply()).contains("Dealer A (20 / 25)");
        assertThat(benchmarkPlan.fallbackReply()).contains("**Dealer A** has the strongest achievement rate");
        assertThat(benchmarkPlan.fallbackReply()).contains("**Dealer B** needs the most attention");
        assertThat(benchmarkPlan.fallbackReply()).contains("80.0%");
        assertThat(benchmarkPlan.fallbackReply()).contains("70.0%");
    }

    @Test
    void planReusesTargetRowsLoadedDuringScopeDetection() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("D001", "Dealer A", "Beijing", "Group A"),
                new Dealer("D002", "Dealer B", "Beijing", "Group A")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 5, 10, 8, 0),
                new Target("D002", "Dealer B", "Beijing", "Group A", "Model X", 2026, 5, 10, 5, 0)
        ));

        service.plan("target achievement for Beijing", "en");

        verify(targetRepository, times(1)).findAll();
    }

    @Test
    void chartLabelsAreShortSanitizedAndDeduplicated() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("D001", "Dealer A Flagship Downtown", "Beijing", "Group A"),
                new Dealer("D002", "Dealer A Flagship Downtown", "Beijing", "Group A"),
                new Dealer("D003", "Dealer <C>, \"North\"", "Beijing", "Group A")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Dealer A Flagship Downtown", "Beijing", "Group A", "Model X", 2026, 5, 10, 9, 0),
                new Target("D002", "Dealer A Flagship Downtown", "Beijing", "Group A", "Model X", 2026, 5, 10, 7, 0),
                new Target("D003", "Dealer <C>, \"North\"", "Beijing", "Group A", "Model X", 2026, 5, 10, 5, 0)
        ));

        AnalyticsPlan plan = service.plan("dealer benchmark comparison for Beijing", "en");

        assertThat(plan.fallbackReply()).contains("```chart-json");
        assertThat(plan.fallbackReply()).contains("\"type\":\"bar\"");
        assertThat(plan.fallbackReply()).contains("\"metricType\":\"percentage\"");
        assertThat(plan.fallbackReply()).contains("Dealer A...");
        assertThat(plan.fallbackReply()).contains("Dealer A... #2");
        assertThat(plan.fallbackReply()).doesNotContain("Dealer <C>, \"North\"");
        assertThat(plan.fallbackReply()).contains("Dealer &lt;C&gt;, &quot;North&quot;");
        assertThat(plan.fallbackReply()).doesNotContain("```mermaid");
    }

    @Test
    void targetAchievementSuppressesRankingWhenAllTargetsHaveZeroDenominator() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("D001", "Dealer A", "Beijing", "Group A"),
                new Dealer("D002", "Dealer B", "Beijing", "Group A")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 5, 0, 0, 0),
                new Target("D002", "Dealer B", "Beijing", "Group A", "Model X", 2026, 5, 0, 0, 0)
        ));

        AnalyticsPlan plan = service.plan("target achievement for Beijing", "en");

        assertLowConfidenceReply(plan, "DENOMINATOR_ZERO");
        assertThat(plan.fallbackReply()).contains("Target achievement");
        assertThat(plan.fallbackReply()).contains("Observed rows");
        assertThat(plan.fallbackReply()).contains("Primary denominator");
    }

    @Test
    void targetAchievementSuppressesRankingWhenAllWonCountsAreZero() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("D001", "Dealer A", "Beijing", "Group A"),
                new Dealer("D002", "Dealer B", "Beijing", "Group A")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 5, 10, 0, 0),
                new Target("D002", "Dealer B", "Beijing", "Group A", "Model X", 2026, 5, 12, 0, 0)
        ));

        AnalyticsPlan plan = service.plan("target achievement for Beijing", "en");

        assertLowConfidenceReply(plan, "ALL_ZERO_SIGNAL");
        assertThat(plan.fallbackReply()).contains("Target achievement");
    }

    @Test
    void dataQualityTraceUsesBusinessSpecificTargetCopy() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("D001", "Dealer A", "Beijing", "Group A"),
                new Dealer("D002", "Dealer B", "Beijing", "Group A")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 5, 10, 0, 0),
                new Target("D002", "Dealer B", "Beijing", "Group A", "Model X", 2026, 5, 12, 0, 0)
        ));

        service.plan("北京目标达成率", "zh");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<CalcStep>> traceCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(promptFactory).buildVisibleThinking(org.mockito.ArgumentMatchers.eq("zh"), traceCaptor.capture());

        String qualityDetail = traceCaptor.getValue().stream()
                .filter(step -> "数据质量判定".equals(step.labelZh()))
                .findFirst()
                .map(CalcStep::detailZh)
                .orElse("");

        assertThat(qualityDetail).contains("可对比门店：2 家");
        assertThat(qualityDetail).contains("最低需要 2 家");
        assertThat(qualityDetail).contains("赢单数合计：0");
        assertThat(qualityDetail).contains("目标数合计：22");
        assertThat(qualityDetail).contains("目标达成率均为 0");
        assertThat(qualityDetail).doesNotContain("有效可比单元", "可参与对比的对象", "指标分子", "指标分母",
                "PrimaryNumerator", "PrimaryDenominator", "Achievement rate");
    }

    @Test
    void dataQualityTraceUsesBusinessSpecificCampaignCopy() {
        when(campaignRepository.findAll()).thenReturn(List.of(
                new Campaign("CAMP-2026-BJ-001", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                        "Launch", LocalDate.of(2026, 5, 1), 0, 10),
                new Campaign("CAMP-2026-BJ-002", "D002", "Dealer B", "Beijing", "Group A", "Model X",
                        "Roadshow", LocalDate.of(2026, 5, 2), 0, 12)
        ));

        service.plan("北京市场活动效果", "zh");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<CalcStep>> traceCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(promptFactory).buildVisibleThinking(org.mockito.ArgumentMatchers.eq("zh"), traceCaptor.capture());

        String qualityDetail = traceCaptor.getValue().stream()
                .filter(step -> "数据质量判定".equals(step.labelZh()))
                .findFirst()
                .map(CalcStep::detailZh)
                .orElse("");

        assertThat(qualityDetail).contains("可对比活动：2 场");
        assertThat(qualityDetail).contains("最低需要 2 场");
        assertThat(qualityDetail).contains("实际产出商机合计：0");
        assertThat(qualityDetail).contains("活动目标合计：22");
        assertThat(qualityDetail).contains("活动达成率均为 0");
        assertThat(qualityDetail).doesNotContain("有效可比单元", "可参与对比的对象", "指标分子", "指标分母",
                "PrimaryNumerator", "PrimaryDenominator", "Campaign attainment");
    }

    @Test
    void campaignDirectDealerQuestionMatchesByDealerNameWhenCampaignCodeUsesExternalId() {
        String dealerName = "\u7ecf\u9500\u5546C(\u6df1\u5733\u5357\u5c71)";
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("7003", dealerName, "\u6df1\u5733\u5357\u5c71", "\u7ecf\u9500\u5546\u96c6\u56e2E")
        ));
        when(campaignRepository.findAll()).thenReturn(List.of(
                new Campaign(
                        "RT-EV-26-AM-7003-001",
                        "\u7ecf\u9500\u5546C\u6d3b\u52a8",
                        "001XYA000000000003",
                        dealerName,
                        "\u6df1\u5733\u5357\u5c71",
                        "",
                        "Aurora S",
                        "Event",
                        "EV",
                        LocalDate.of(2026, 5, 1),
                        259,
                        939,
                        18,
                        177,
                        0,
                        259
                )
        ));

        AnalyticsPlan plan = service.plan(
                "\u7ecf\u9500\u5546C(\u6df1\u5733\u5357\u5c71)\u6d3b\u52a8\u6548\u679c\u600e\u4e48\u6837\uff1f",
                "zh"
        );

        assertThat(plan.fallbackReply()).contains(dealerName);
        assertThat(plan.fallbackReply()).contains("259");
        assertThat(plan.fallbackReply()).contains("939");
        assertThat(plan.fallbackReply()).contains("362.5%");
        assertThat(plan.fallbackReply()).contains("177");
        assertThat(plan.fallbackReply()).doesNotContain("0 matching rows");
    }

    @Test
    void directTargetQuestionsHandleSalesAndCompletionParaphrases() {
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "经销商A", "上海", "集团1", "Aurora S", 2026, 5, 100, 30, 40),
                new Target("D002", "经销商B", "上海", "集团1", "Nova X", 2026, 5, 50, 45, 55),
                new Target("D003", "经销商C", "上海", "集团1", "Aurora S", 2026, 5, 20, 5, 10)
        ));

        AnalyticsPlan modelPlan = service.plan("全国范围内哪款车卖得最好？", "zh");
        AnalyticsPlan dealerPlan = service.plan("目标完成率最高的是谁？", "zh");

        assertThat(modelPlan.scenario()).isEqualTo(AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT);
        assertThat(modelPlan.fallbackReply()).contains("车型赢单最多");
        assertThat(modelPlan.fallbackReply()).contains("Nova X");
        assertThat(dealerPlan.scenario()).isEqualTo(AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT);
        assertThat(dealerPlan.fallbackReply()).contains("经销商目标达成率最高");
        assertThat(dealerPlan.fallbackReply()).contains("经销商B");
        assertThat(dealerPlan.fallbackReply()).contains("90.0%");
    }

    @Test
    void directOpportunityQuestionHandlesStageDistributionParaphrase() {
        when(opportunityRepository.findAll()).thenReturn(List.of(
                new Opportunity("O1", "D001", "经销商A", "上海", "集团1", "Aurora S",
                        "Negotiation", "Website", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 20), 80),
                new Opportunity("O2", "D002", "经销商B", "上海", "集团1", "Nova X",
                        "Negotiation", "Retail", LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 21), 70),
                new Opportunity("O3", "D003", "经销商C", "上海", "集团1", "Aurora S",
                        "Won", "Website", LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 22), 100)
        ));

        AnalyticsPlan plan = service.plan("商机按阶段怎么分布？", "zh");

        assertThat(plan.scenario()).isEqualTo(AnalyticsPlan.Scenario.OPPORTUNITY_FUNNEL);
        assertThat(plan.fallbackReply()).contains("阶段分布");
        assertThat(plan.fallbackReply()).contains("Negotiation 2");
        assertThat(plan.fallbackReply()).contains("Won 1");
    }

    @Test
    void directOpportunityQuestionHandlesClosedDealVolumeParaphrase() {
        when(opportunityRepository.findAll()).thenReturn(List.of(
                new Opportunity("O1", "D001", "经销商A", "上海", "集团1", "Aurora S",
                        "Closed Won", "Website", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 20), 100),
                new Opportunity("O2", "D001", "经销商A", "上海", "集团1", "Nova X",
                        "Closed Won", "Retail", LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 21), 100),
                new Opportunity("O3", "D002", "经销商B", "上海", "集团1", "Aurora S",
                        "Closed Won", "Website", LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 22), 100),
                new Opportunity("O4", "D002", "经销商B", "上海", "集团1", "Nova X",
                        "Qualification & Discovery", "Retail", LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 23), 40)
        ));

        AnalyticsPlan plan = service.plan("哪个经销商的成交商机最多？", "zh");

        assertThat(plan.scenario()).isEqualTo(AnalyticsPlan.Scenario.OPPORTUNITY_FUNNEL);
        assertThat(plan.fallbackReply()).contains("赢单商机最多的经销商");
        assertThat(plan.fallbackReply()).contains("经销商A");
        assertThat(plan.fallbackReply()).contains("共 2 条");
    }

    @Test
    void directTaskQuestionHandlesTaskTypeTopThreeParaphrase() {
        when(taskRepository.findAll()).thenReturn(List.of(
                new Task("T1", "D001", "经销商A", "上海", "集团1", "O1", "Call", "Completed", LocalDate.of(2026, 5, 1)),
                new Task("T2", "D001", "经销商A", "上海", "集团1", "O2", "Call", "Completed", LocalDate.of(2026, 5, 2)),
                new Task("T3", "D002", "经销商B", "上海", "集团1", "O3", "Call", "Open", LocalDate.of(2026, 5, 3)),
                new Task("T4", "D002", "经销商B", "上海", "集团1", "O4", "Visit", "Open", LocalDate.of(2026, 5, 4)),
                new Task("T5", "D003", "经销商C", "上海", "集团1", "O5", "Visit", "Completed", LocalDate.of(2026, 5, 5)),
                new Task("T6", "D003", "经销商C", "上海", "集团1", "O6", "Email", "Completed", LocalDate.of(2026, 5, 6)),
                new Task("T7", "D003", "经销商C", "上海", "集团1", "O7", "Other", "Completed", LocalDate.of(2026, 5, 7))
        ));

        AnalyticsPlan plan = service.plan("任务类型前三是什么？", "zh");

        assertThat(plan.scenario()).isEqualTo(AnalyticsPlan.Scenario.SALES_FOLLOW_UP);
        assertThat(plan.fallbackReply()).contains("任务 Subject 中最多的是 Call 3，Visit 2，Email 1");
        assertThat(plan.fallbackReply()).doesNotContain("Other 1");
    }

    @Test
    void directLeadQuestionHandlesStatusBreakdownParaphrase() {
        when(leadRepository.findAll()).thenReturn(List.of(
                new Lead("L1", "D001", "经销商A", "上海", "集团1", "Website", "New",
                        "Aurora S", LocalDate.of(2026, 5, 1), false),
                new Lead("L2", "D002", "经销商B", "上海", "集团1", "Retail", "New",
                        "Nova X", LocalDate.of(2026, 5, 2), true),
                new Lead("L3", "D003", "经销商C", "上海", "集团1", "Website", "Qualified",
                        "Aurora S", null, false)
        ));

        AnalyticsPlan plan = service.plan("线索状态分别多少？", "zh");

        assertThat(plan.scenario()).isEqualTo(AnalyticsPlan.Scenario.LEAD_SOURCE);
        assertThat(plan.fallbackReply()).contains("New 2");
        assertThat(plan.fallbackReply()).contains("Qualified 1");
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

        assertLowConfidenceReply(plan, "ALL_ZERO_SIGNAL");
        assertThat(plan.fallbackReply()).contains("Campaign performance");
        assertThat(plan.fallbackReply()).contains("Primary numerator");
        assertThat(plan.fallbackReply()).contains("0");
    }

    @Test
    void dealerBenchmarkSuppressesBenchmarkWhenOnlyOneValidDealerExists() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("D001", "Dealer A", "Beijing", "Group A")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 5, 10, 8, 0)
        ));

        AnalyticsPlan plan = service.plan("dealer benchmark comparison for Beijing", "en");

        assertLowConfidenceReply(plan, "INSUFFICIENT_SAMPLE");
        assertThat(plan.fallbackReply()).contains("Observed rows");
    }

    @Test
    void planFiltersTargetAchievementToTheCurrentMonthWhenRequested() {
        RuleBasedAnalyticsService timeAwareService = new RuleBasedAnalyticsService(
                promptFactory,
                dealerRepository,
                opportunityRepository,
                campaignRepository,
                taskRepository,
                targetRepository,
                leadRepository,
                dataQueryService,
                Clock.fixed(Instant.parse("2026-05-11T00:00:00Z"), ZoneOffset.UTC)
        );

        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("D001", "Dealer A", "Beijing", "Group A"),
                new Dealer("D002", "Dealer B", "Beijing", "Group A")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 4, 10, 1, 0),
                new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 5, 10, 7, 0),
                new Target("D002", "Dealer B", "Beijing", "Group A", "Model X", 2026, 5, 10, 5, 0)
        ));

        AnalyticsPlan plan = timeAwareService.plan("本月北京门店目标达成概览", "zh");

        assertThat(plan.scopeSummary()).isEqualTo("2026年5月 / 北京");
        assertThat(plan.fallbackReply()).contains("Dealer B (5 / 10)");
        assertThat(plan.fallbackReply()).contains("60.0%");
        assertThat(plan.fallbackReply()).doesNotContain("8 / 20");
    }

    @Test
    void planMatchesChineseDealerByShortNameAndParenthesizedCity() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("7036", "经销商AJ(沈阳)", "沈阳", "经销商集团P"),
                new Dealer("7037", "经销商AK(太原)", "太原", "经销商集团Q")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("7036", "经销商AJ(沈阳)", "沈阳", "经销商集团P", "Aurora S", 2026, 5, 10, 8, 0),
                new Target("7037", "经销商AK(太原)", "太原", "经销商集团Q", "Aurora S", 2026, 5, 10, 1, 0)
        ));

        AnalyticsPlan plan = service.plan("经销商AJ在沈阳的目标达成率", "zh");

        assertThat(plan.scopeSummary()).isEqualTo("沈阳 / 经销商集团P / 经销商AJ(沈阳)");
        assertThat(plan.fallbackReply()).contains("经销商AJ(沈阳) (8 / 10)");
        assertThat(plan.fallbackReply()).doesNotContain("经销商AK(太原) (1 / 10)");
    }

    @Test
    void planPrefersLongerDealerShortNameWhenOneDealerNamePrefixesAnother() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("7001", "经销商A(上海静安)", "上海静安", "经销商集团A"),
                new Dealer("7037", "经销商AK(太原)", "太原", "经销商集团Q")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("7001", "经销商A(上海静安)", "上海静安", "经销商集团A", "Aurora S", 2026, 5, 10, 8, 0),
                new Target("7037", "经销商AK(太原)", "太原", "经销商集团Q", "Aurora S", 2026, 5, 10, 1, 0)
        ));

        AnalyticsPlan plan = service.plan("经销商AK(太原)的目标达成率", "zh");

        assertThat(plan.scopeSummary()).isEqualTo("太原 / 经销商集团Q / 经销商AK(太原)");
        assertThat(plan.fallbackReply()).contains("经销商AK(太原) (1 / 10)");
        assertThat(plan.fallbackReply()).doesNotContain("经销商A(上海静安) (8 / 10)");
    }

    @Test
    void planDetectsCitiesFromImportedDealerDataBeyondBuiltInAliases() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("7036", "经销商AJ(沈阳)", "沈阳", "经销商集团P"),
                new Dealer("7037", "经销商AK(太原)", "太原", "经销商集团Q")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("7036", "经销商AJ(沈阳)", "沈阳", "经销商集团P", "Aurora S", 2026, 5, 10, 8, 0),
                new Target("7037", "经销商AK(太原)", "太原", "经销商集团Q", "Aurora S", 2026, 5, 10, 1, 0)
        ));

        AnalyticsPlan plan = service.plan("沈阳目标达成率", "zh");

        assertThat(plan.scopeSummary()).isEqualTo("沈阳");
        assertThat(plan.fallbackReply()).contains("<td>沈阳</td>");
        assertThat(plan.fallbackReply()).contains("<td>1</td>");
        assertThat(plan.fallbackReply()).doesNotContain("经销商AK(太原) (1 / 10)");
    }

    @Test
    void planFiltersOpportunityFunnelToTheCurrentMonthWhenRequested() {
        RuleBasedAnalyticsService timeAwareService = new RuleBasedAnalyticsService(
                promptFactory,
                dealerRepository,
                opportunityRepository,
                campaignRepository,
                taskRepository,
                targetRepository,
                leadRepository,
                dataQueryService,
                Clock.fixed(Instant.parse("2026-05-11T00:00:00Z"), ZoneOffset.UTC)
        );

        when(opportunityRepository.findAll()).thenReturn(List.of(
                new Opportunity("O1", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                        "Negotiation", "Website", LocalDate.of(2026, 4, 28), LocalDate.of(2026, 5, 20), 80),
                new Opportunity("O2", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                        "Negotiation", "Website", LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 21), 65),
                new Opportunity("O3", "D002", "Dealer B", "Beijing", "Group A", "Model Y",
                        "Won", "Retail", LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 18), 100)
        ));

        AnalyticsPlan plan = timeAwareService.plan("this month opportunity funnel review for Beijing", "en");

        assertThat(plan.scopeSummary()).isEqualTo("May 2026 / Beijing");
        assertThat(plan.fallbackReply()).contains("**2** opportunities in scope");
        assertThat(plan.fallbackReply()).doesNotContain("**3** opportunities in scope");
    }

    @Test
    void zhFallbackTablesUseLocalizedLabelsOnly() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("D001", "Dealer A", "Beijing", "Group A")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 5, 10, 8, 0)
        ));

        AnalyticsPlan plan = service.plan("\u5317\u4eac Dealer A \u7684\u76ee\u6807\u8fbe\u6210", "zh");

        assertThat(plan.fallbackReply()).contains("\u8986\u76d6\u95e8\u5e97\u6570");
        assertThat(plan.fallbackReply()).contains("\u6700\u4f4e\u8fbe\u6210\u95e8\u5e97");
        assertThat(plan.fallbackReply()).contains("\u6700\u9ad8\u8fbe\u6210\u95e8\u5e97");
        assertThat(plan.fallbackReply()).contains("\u5e73\u5747\u8fbe\u6210\u7387");
        assertThat(plan.fallbackReply()).doesNotContain("Dealers covered");
        assertThat(plan.fallbackReply()).doesNotContain("Lowest achievement");
        assertThat(plan.fallbackReply()).doesNotContain("Highest achievement");
        assertThat(plan.fallbackReply()).doesNotContain("Average achievement");
    }

    @Test
    void fallbackTablesEscapeHtmlCellValues() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("D<01>", "Dealer \"A\" & Sons'", "Beijing", "Group A")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D<01>", "Dealer \"A\" & Sons'", "Beijing", "Group A", "Model X", 2026, 5, 10, 8, 0)
        ));

        AnalyticsPlan plan = service.plan("\u5317\u4eac D<01> \u7684\u76ee\u6807\u8fbe\u6210", "zh");

        assertThat(plan.fallbackReply()).contains("Dealer &quot;A&quot; &amp; Sons&#39;");
        assertThat(plan.fallbackReply()).contains("Dealer &quot;A&quot; &amp; Sons&#39; (8 / 10)");
        assertThat(plan.fallbackReply()).doesNotContain("<td>Dealer \"A\" & Sons'</td>");
    }

    @Test
    void planEscapesDerivedStringsAcrossSharedFallbackSections() {
        when(opportunityRepository.findAll()).thenReturn(List.of(
                new Opportunity("O1", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                        "Negotiation <Late>", "Web & Referral \"Mix\"'", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 20), 80),
                new Opportunity("O2", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                        "Negotiation <Late>", "Web & Referral \"Mix\"'", LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 22), 70),
                new Opportunity("O3", "D002", "Dealer B", "Beijing", "Group A", "Model X",
                        "Won", "Retail", LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 15), 100)
        ));

        AnalyticsPlan plan = service.plan("\u5317\u4eac\u5546\u673a\u6f0f\u6597\u5206\u6790", "zh");

        assertThat(plan.fallbackReply()).contains("## 核心结论");
        assertThat(plan.fallbackReply()).contains("## 数据支撑");
        assertThat(plan.fallbackReply()).contains("## 经营分析");
        assertThat(plan.fallbackReply()).contains("**可执行建议：**");
        assertThat(plan.fallbackReply()).doesNotContain("## Short Analysis");
        assertThat(plan.fallbackReply()).contains("追问：");
        assertThat(plan.fallbackReply()).contains("Negotiation &lt;Late&gt;");
        assertThat(plan.fallbackReply()).contains("Web &amp; Referral &quot;Mix&quot;&#39;");
        assertThat(plan.fallbackReply()).doesNotContain("Negotiation <Late>");
        assertThat(plan.fallbackReply()).doesNotContain("Web & Referral \"Mix\"'");
    }

    @Test
    void planEscapesRepositoryDerivedScopeTextInEnglishNoDataReplies() {
        when(leadRepository.findAll()).thenReturn(List.of(
                new Lead("L1", "D001", "Dealer A", "Beijing", "Group A", "Web", "New",
                        "Model <Elite> & \"Plus\"'", LocalDate.of(2026, 5, 1), false)
        ));

        AnalyticsPlan plan = service.plan("target achievement for Model <Elite> & \"Plus\"'", "en");

        assertContractMarkers(plan.fallbackReply());
        assertThat(plan.fallbackReply()).contains("Model &lt;Elite&gt; &amp; &quot;Plus&quot;&#39;");
        assertThat(plan.fallbackReply()).doesNotContain("Model <Elite> & \"Plus\"'");
    }

    @Test
    void planBuildsOpportunityFunnelReplyWithScenarioSpecificContent() {
        when(dataQueryService.query(org.mockito.ArgumentMatchers.eq("opportunities"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new DataQueryResponse("opportunities", Map.of("city", "Beijing"), 4, List.of(
                        row("opportunityId", "O1", "dealerCode", "D001", "dealerName", "Dealer A", "city", "Beijing",
                                "dealerGroupName", "Group A", "productModel", "Model X", "stageName", "Negotiation",
                                "leadSource", "Website", "createdDate", "2026-05-01", "expectedCloseDate", "2026-05-20",
                                "probability", 80),
                        row("opportunityId", "O2", "dealerCode", "D001", "dealerName", "Dealer A", "city", "Beijing",
                                "dealerGroupName", "Group A", "productModel", "Model X", "stageName", "Negotiation",
                                "leadSource", "Website", "createdDate", "2026-05-02", "expectedCloseDate", "2026-05-21",
                                "probability", 65),
                        row("opportunityId", "O3", "dealerCode", "D002", "dealerName", "Dealer B", "city", "Beijing",
                                "dealerGroupName", "Group A", "productModel", "Model Y", "stageName", "Won",
                                "leadSource", "Retail", "createdDate", "2026-05-03", "expectedCloseDate", "2026-05-18",
                                "probability", 100),
                        row("opportunityId", "O4", "dealerCode", "D003", "dealerName", "Dealer C", "city", "Beijing",
                                "dealerGroupName", "Group B", "productModel", "Model Z", "stageName", "Lost",
                                "leadSource", "Website", "createdDate", "2026-05-04", "expectedCloseDate", "2026-05-17",
                                "probability", 20)
                ), Map.of("totalCount", 6)));

        AnalyticsPlan plan = service.plan("opportunity funnel review for Beijing", "en");

        assertThat(plan.scenario()).isEqualTo(AnalyticsPlan.Scenario.OPPORTUNITY_FUNNEL);
        assertContractMarkers(plan.fallbackReply());
        assertThat(plan.fallbackReply()).contains("**6** opportunities in scope");
        assertThat(plan.fallbackReply()).contains("**Negotiation**");
        assertThat(plan.fallbackReply()).contains("Primary lead source");
        assertThat(plan.fallbackReply()).contains("Website");
    }

    @Test
    void opportunityFunnelSuppressesBottleneckDiagnosisWhenSampleIsTooSmall() {
        when(opportunityRepository.findAll()).thenReturn(List.of(
                new Opportunity("O1", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                        "Negotiation", "Website", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 20), 70),
                new Opportunity("O2", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                        "Won", "Website", LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 21), 100)
        ));

        AnalyticsPlan plan = service.plan("opportunity funnel for Beijing", "en");

        assertLowConfidenceReply(plan, "INSUFFICIENT_SAMPLE");
        assertThat(plan.fallbackReply()).contains("Opportunity funnel");
    }

    @Test
    void planBuildsSalesFollowUpReplyWithScenarioSpecificContent() {
        when(dataQueryService.query(org.mockito.ArgumentMatchers.eq("tasks"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new DataQueryResponse("tasks", Map.of("city", "Beijing"), 4, List.of(
                        row("taskId", "T1", "dealerCode", "D001", "dealerName", "Dealer A", "city", "Beijing",
                                "dealerGroupName", "Group A", "opportunityId", "O1", "status", "Open",
                                "createdDate", "2026-05-01"),
                        row("taskId", "T2", "dealerCode", "D001", "dealerName", "Dealer A", "city", "Beijing",
                                "dealerGroupName", "Group A", "opportunityId", "O2", "status", "Overdue",
                                "createdDate", "2026-05-02"),
                        row("taskId", "T3", "dealerCode", "D001", "dealerName", "Dealer A", "city", "Beijing",
                                "dealerGroupName", "Group A", "opportunityId", "O3", "status", "Completed",
                                "createdDate", "2026-05-03"),
                        row("taskId", "T4", "dealerCode", "D002", "dealerName", "Dealer B", "city", "Beijing",
                                "dealerGroupName", "Group A", "opportunityId", "O4", "status", "Completed",
                                "createdDate", "2026-05-04")
                ), Map.of("totalTaskCount", 5)));

        AnalyticsPlan plan = service.plan("sales follow up status for Beijing", "en");

        assertThat(plan.scenario()).isEqualTo(AnalyticsPlan.Scenario.SALES_FOLLOW_UP);
        assertContractMarkers(plan.fallbackReply());
        assertThat(plan.fallbackReply()).contains("**Dealer A** has the heaviest follow-up backlog");
        assertThat(plan.fallbackReply()).contains("Overdue tasks");
        assertThat(plan.fallbackReply()).contains("<td>5</td>");
        assertThat(plan.fallbackReply()).contains("Dealer A (2 / 3)");
    }

    @Test
    void highActivityFollowUpQuestionRanksCompletedDealersInsteadOfBacklogDealers() {
        when(dataQueryService.query(org.mockito.ArgumentMatchers.eq("tasks"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new DataQueryResponse("tasks", Map.of(), 9, List.of(
                        row("taskId", "T1", "dealerCode", "D001", "dealerName", "Dealer A", "city", "Beijing",
                                "dealerGroupName", "Group A", "opportunityId", "O1", "status", "Open",
                                "createdDate", "2026-05-01"),
                        row("taskId", "T2", "dealerCode", "D001", "dealerName", "Dealer A", "city", "Beijing",
                                "dealerGroupName", "Group A", "opportunityId", "O2", "status", "Overdue",
                                "createdDate", "2026-05-02"),
                        row("taskId", "T3", "dealerCode", "D001", "dealerName", "Dealer A", "city", "Beijing",
                                "dealerGroupName", "Group A", "opportunityId", "O3", "status", "Completed",
                                "createdDate", "2026-05-03"),
                        row("taskId", "T4", "dealerCode", "D002", "dealerName", "Dealer B", "city", "Beijing",
                                "dealerGroupName", "Group A", "opportunityId", "O4", "status", "Completed",
                                "createdDate", "2026-05-04"),
                        row("taskId", "T5", "dealerCode", "D002", "dealerName", "Dealer B", "city", "Beijing",
                                "dealerGroupName", "Group A", "opportunityId", "O5", "status", "Completed",
                                "createdDate", "2026-05-05"),
                        row("taskId", "T6", "dealerCode", "D002", "dealerName", "Dealer B", "city", "Beijing",
                                "dealerGroupName", "Group A", "opportunityId", "O6", "status", "Completed",
                                "createdDate", "2026-05-06"),
                        row("taskId", "T7", "dealerCode", "D003", "dealerName", "Dealer C", "city", "Beijing",
                                "dealerGroupName", "Group A", "opportunityId", "O7", "status", "Completed",
                                "createdDate", "2026-05-07"),
                        row("taskId", "T8", "dealerCode", "D003", "dealerName", "Dealer C", "city", "Beijing",
                                "dealerGroupName", "Group A", "opportunityId", "O8", "status", "Completed",
                                "createdDate", "2026-05-08"),
                        row("taskId", "T9", "dealerCode", "D003", "dealerName", "Dealer C", "city", "Beijing",
                                "dealerGroupName", "Group A", "opportunityId", "O9", "status", "Open",
                                "createdDate", "2026-05-09")
                ), Map.of("totalTaskCount", 9)));

        AnalyticsPlan plan = service.plan("\u54ea\u4e9b\u95e8\u5e97\u7684\u6d3b\u8dc3\u5ea6\u6bd4\u8f83\u9ad8", "en");

        assertThat(plan.scenario()).isEqualTo(AnalyticsPlan.Scenario.SALES_FOLLOW_UP);
        assertContractMarkers(plan.fallbackReply());
        assertThat(plan.fallbackReply()).contains("**Dealer B** shows the strongest follow-up activity");
        assertThat(plan.fallbackReply()).contains("Highest activity dealer");
        assertThat(plan.fallbackReply()).contains("Dealer B (3 / 3)");
        assertThat(plan.fallbackReply()).doesNotContain("**Dealer A** has the heaviest follow-up backlog");
        assertThat(plan.fallbackReply()).doesNotContain("Highest backlog dealer");
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

        assertLowConfidenceReply(plan, "INSUFFICIENT_SAMPLE");
        assertThat(plan.fallbackReply()).contains("Sales follow-up");
    }

    @Test
    void planBuildsCampaignPerformanceReplyWithScenarioSpecificContent() {
        when(dataQueryService.query(org.mockito.ArgumentMatchers.eq("campaigns"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new DataQueryResponse("campaigns", Map.of("city", "Beijing"), 2, List.of(
                        row("campaignId", "C-ALPHA", "dealerCode", "D001", "dealerName", "Dealer A", "city", "Beijing",
                                "dealerGroupName", "Group A", "productModel", "Model X", "campaignType", "Launch Day",
                                "createdDate", "2026-05-01", "actualOpportunityCount", 8, "totalNewCustomerTarget", 10),
                        row("campaignId", "C-BETA", "dealerCode", "D002", "dealerName", "Dealer B", "city", "Beijing",
                                "dealerGroupName", "Group A", "productModel", "Model Y", "campaignType", "Roadshow",
                                "createdDate", "2026-05-02", "actualOpportunityCount", 3, "totalNewCustomerTarget", 10)
                ), Map.of("campaignCount", 7)));

        AnalyticsPlan plan = service.plan("campaign performance analysis for Beijing", "en");

        assertThat(plan.scenario()).isEqualTo(AnalyticsPlan.Scenario.CAMPAIGN_PERFORMANCE);
        assertContractMarkers(plan.fallbackReply());
        assertThat(plan.fallbackReply()).contains("**Launch Day** leads with");
        assertThat(plan.fallbackReply()).contains("C-ALPHA (8 / 10)");
        assertThat(plan.fallbackReply()).contains("<td>7</td>");
        assertThat(plan.fallbackReply()).contains("80.0%");
    }

    @Test
    void planBuildsLeadSourceReplyWithScenarioSpecificContent() {
        when(dataQueryService.query(org.mockito.ArgumentMatchers.eq("leads"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new DataQueryResponse("leads", Map.of("city", "Beijing"), 3, List.of(
                        row("leadId", "L1", "dealerCode", "D001", "dealerName", "Dealer A", "city", "Beijing",
                                "dealerGroupName", "Group A", "leadSource", "Website", "stageName", "New",
                                "productModel", "Model X", "createdDate", "2026-05-01", "isConverted", true),
                        row("leadId", "L2", "dealerCode", "D001", "dealerName", "Dealer A", "city", "Beijing",
                                "dealerGroupName", "Group A", "leadSource", "Website", "stageName", "Qualified",
                                "productModel", "Model X", "createdDate", "2026-05-02", "isConverted", false),
                        row("leadId", "L3", "dealerCode", "D002", "dealerName", "Dealer B", "city", "Beijing",
                                "dealerGroupName", "Group A", "leadSource", "Referral", "stageName", "New",
                                "productModel", "Model Y", "createdDate", "2026-05-03", "isConverted", true)
                ), Map.of("totalCount", 9)));

        AnalyticsPlan plan = service.plan("lead source breakdown for Beijing", "en");

        assertThat(plan.scenario()).isEqualTo(AnalyticsPlan.Scenario.LEAD_SOURCE);
        assertContractMarkers(plan.fallbackReply());
        assertThat(plan.fallbackReply()).contains("**Website** delivers the most leads");
        assertThat(plan.fallbackReply()).contains("**Referral** has the best conversion rate");
        assertThat(plan.fallbackReply()).contains("```chart-json");
        assertThat(plan.fallbackReply()).contains("\"type\":\"pie\"");
        assertThat(plan.fallbackReply()).doesNotContain("```mermaid");
        assertThat(plan.fallbackReply()).contains("<td>9</td>");
        assertThat(plan.fallbackReply()).contains("Referral (conversion rate 100.0%");
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

        assertLowConfidenceReply(plan, "ALL_ZERO_SIGNAL");
        assertThat(plan.fallbackReply()).contains("Lead source analysis");
    }

    @Test
    void fallbackReplyContainsNewSevenMarkersForTargetAchievementInOrder() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("D001", "Store A", "Beijing", "Group1")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Store A", "Beijing", "Group1", "ModelX", 2026, 5, 100, 80, 0)
        ));

        AnalyticsPlan plan = service.plan("本月目标达成率", "zh");

        String reply = plan.fallbackReply();
        assertNewZhReportMarkersInOrder(reply);
        assertThat(reply).doesNotContain("## 数据汇总");
        assertThat(reply).doesNotContain("## Data Summary");
    }

    @Test
    void fallbackReplyOmitsTrailingDataSummary() {
        AnalyticsPlan plan = service.plan("本月目标达成率", "zh");

        String reply = plan.fallbackReply();
        assertThat(reply).doesNotContain("## 数据汇总");
        assertThat(reply).doesNotContain("## Data Summary");
        assertThat(reply).doesNotContain("<th>范围</th>");
        assertThat(reply).doesNotContain("<th>对比基准</th>");
    }

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
        when(promptFactory.buildVisibleThinking(anyString(), org.mockito.ArgumentMatchers.<List<com.brand.agentpoc.ai.CalcStep>>any()))
                .thenReturn("visible-thinking");
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

    @Test
    void analyzeKeepsZhCompatibilityWithReadableChineseOutput() {
        AnalyticsPlan plan = service.plan("\u5317\u4eac\u95e8\u5e97\u76ee\u6807\u8fbe\u6210\u6982\u89c8", "zh");

        assertThat(plan.fallbackReply()).contains("## \u6838\u5fc3\u7ed3\u8bba");
        assertThat(plan.fallbackReply()).contains("\u7f3a\u5c11\u8db3\u591f\u6570\u636e");
        assertThat(plan.fallbackReply()).contains("\u76ee\u6807\u8fbe\u6210");
        assertThat(plan.progressMessages()).containsExactly(
                "\u6b63\u5728\u8fdb\u884c\u610f\u56fe\u8bc6\u522b\u4e0e\u573a\u666f\u5f52\u7c7b",
                "\u6b63\u5728\u6309\u5de5\u5177\u94fe\u6267\u884c\uff1agetCurrentDate() -> searchDealers() -> queryTargets()",
                "\u6b63\u5728\u5904\u7406\u6570\u636e\u5e76\u751f\u6210\u7ed3\u6784\u5316\u62a5\u544a"
        );
        assertThat(plan.visibleThinking()).isEqualTo("visible-thinking");
    }

    @Test
    void planComputesDealerBusinessActivityScoresWithFiveTiers() {
        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("D001", "Very Active Dealer", "Beijing", "Group A"),
                new Dealer("D002", "Dormant Dealer", "Shanghai", "Group B"),
                new Dealer("D003", "Moderate Dealer", "Guangzhou", "Group C")
        ));

        List<Target> targets = new ArrayList<>();
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
    void planDoesNotEmitDataQualityLogAtInfoLevel() {
        try (LogCapture logs = LogCapture.attach(RuleBasedAnalyticsService.class, ch.qos.logback.classic.Level.INFO)) {
            service.plan("Which dealers have the lowest target achievement this month?", "en");

            assertThat(logs.messages()).isEmpty();
        }
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

    @Test
    void planWithCallbackEmitsSteps() {
        List<StepEvent> capturedSteps = new ArrayList<>();
        String traceId = "test1234";

        AnalyticsPlan plan = service.plan(
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
                    "seq should be monotonically increasing at index " + i);
        }
    }

    @Test
    void targetAchievementStepsIncludeAuditInputsFiltersAndFormula() {
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 5, 10, 8, 0),
                new Target("D002", "Dealer B", "Beijing", "Group A", "Model X", 2026, 5, 10, 5, 0),
                new Target("D003", "Dealer C", "Shanghai", "Group B", "Model X", 2026, 5, 10, 9, 0)
        ));
        List<StepEvent> capturedSteps = new ArrayList<>();

        service.plan("target achievement for Beijing in May 2026", "en", "trace-audit", capturedSteps::add);

        StepEvent filterStep = capturedSteps.stream()
                .filter(step -> step.type() == StepType.filter)
                .findFirst()
                .orElseThrow();
        assertThat(filterStep.meta()).containsEntry("inputCount", 3);
        assertThat(filterStep.meta()).containsEntry("outputCount", 2);
        assertThat(filterStep.meta()).containsEntry("city", "Beijing");

        StepEvent calculationStep = capturedSteps.stream()
                .filter(step -> step.type() == StepType.calculation
                        && step.label().contains("Aggregate targets"))
                .findFirst()
                .orElseThrow();
        assertThat(calculationStep.detail()).contains("achievementRate = wonCount / targetValue");
        assertThat(calculationStep.meta()).containsEntry("formula", "achievementRate = wonCount / targetValue");
        assertThat(calculationStep.meta()).containsEntry("totalWonCount", 13);
        assertThat(calculationStep.meta()).containsEntry("totalTargetValue", 20);
        assertThat((List<?>) calculationStep.meta().get("sampleRows")).isNotEmpty();
    }

    @Test
    void opportunityFunnelStepsIncludeStageDistributionAndSourceAudit() {
        when(opportunityRepository.findAll()).thenReturn(List.of(
                new Opportunity("O1", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                        "Negotiation", "Website", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 20), 80),
                new Opportunity("O2", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                        "Negotiation", "Website", LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 21), 70),
                new Opportunity("O3", "D002", "Dealer B", "Beijing", "Group A", "Model X",
                        "Won", "Retail", LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 22), 100),
                new Opportunity("O4", "D003", "Dealer C", "Beijing", "Group A", "Model X",
                        "Lost", "Referral", LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 23), 10)
        ));
        List<StepEvent> capturedSteps = new ArrayList<>();

        service.plan("opportunity funnel analysis for Beijing in May 2026", "en", "trace-funnel", capturedSteps::add);

        StepEvent stageStep = capturedSteps.stream()
                .filter(step -> step.type() == StepType.calculation
                        && step.label().contains("Group by stage"))
                .findFirst()
                .orElseThrow();
        assertThat(stageStep.detail()).contains("winRate = wonCount / totalOpportunityCount");
        assertThat(stageStep.meta()).containsEntry("formula", "stageCounts = count by stageName; winRate = wonCount / totalOpportunityCount");
        assertThat(stageStep.meta()).containsEntry("totalOpportunityCount", 4);
        assertThat(stageStep.meta()).containsEntry("wonCount", 1L);
        assertThat(((Map<?, ?>) stageStep.meta().get("stageCounts")).get("Negotiation")).isEqualTo(2L);
        assertThat(((Map<?, ?>) stageStep.meta().get("leadSourceCounts")).get("Website")).isEqualTo(2L);
    }

    @Test
    void salesFollowUpStepsIncludeTaskStatusAndBacklogFormula() {
        when(taskRepository.findAll()).thenReturn(List.of(
                new Task("T1", "D001", "Dealer A", "Beijing", "Group A", "O1", "Open", LocalDate.of(2026, 5, 1)),
                new Task("T2", "D001", "Dealer A", "Beijing", "Group A", "O2", "Overdue", LocalDate.of(2026, 5, 2)),
                new Task("T3", "D001", "Dealer A", "Beijing", "Group A", "O3", "Completed", LocalDate.of(2026, 5, 3)),
                new Task("T4", "D002", "Dealer B", "Beijing", "Group A", "O4", "Open", LocalDate.of(2026, 5, 4)),
                new Task("T5", "D002", "Dealer B", "Beijing", "Group A", "O5", "Completed", LocalDate.of(2026, 5, 5)),
                new Task("T6", "D003", "Dealer C", "Beijing", "Group A", "O6", "Completed", LocalDate.of(2026, 5, 6))
        ));
        List<StepEvent> capturedSteps = new ArrayList<>();

        service.plan("sales follow-up backlog for Beijing in May 2026", "en", "trace-task", capturedSteps::add);

        StepEvent openTaskStep = capturedSteps.stream()
                .filter(step -> step.type() == StepType.filter
                        && step.label().contains("Filter open tasks"))
                .findFirst()
                .orElseThrow();
        assertThat(openTaskStep.meta()).containsEntry("formula", "openTaskCount = count(status != Completed); overdueCount = count(status == Overdue)");
        assertThat(openTaskStep.meta()).containsEntry("openCount", 3L);
        assertThat(openTaskStep.meta()).containsEntry("overdueCount", 1L);
        assertThat(((Map<?, ?>) openTaskStep.meta().get("statusCounts")).get("Completed")).isEqualTo(3L);

        StepEvent backlogStep = capturedSteps.stream()
                .filter(step -> step.type() == StepType.calculation
                        && step.label().contains("compute backlog rate"))
                .findFirst()
                .orElseThrow();
        assertThat(backlogStep.detail()).contains("backlogRate = openTaskCount / totalTaskCount");
        assertThat(backlogStep.meta()).containsEntry("formula", "backlogRate = openTaskCount / totalTaskCount");
        assertThat(backlogStep.meta()).containsEntry("dealerCount", 3);
        assertThat((List<?>) backlogStep.meta().get("sampleRows")).isNotEmpty();
    }

    @Test
    void campaignPerformanceStepsIncludeAttainmentFormulaAndSampleRows() {
        when(campaignRepository.findAll()).thenReturn(List.of(
                new Campaign("C1", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                        "Launch Day", LocalDate.of(2026, 5, 1), 8, 10),
                new Campaign("C2", "D002", "Dealer B", "Beijing", "Group A", "Model X",
                        "Roadshow", LocalDate.of(2026, 5, 2), 5, 10),
                new Campaign("C3", "D003", "Dealer C", "Shanghai", "Group B", "Model X",
                        "Mall Event", LocalDate.of(2026, 5, 3), 7, 10)
        ));
        List<StepEvent> capturedSteps = new ArrayList<>();

        service.plan("campaign performance for Beijing in May 2026", "en", "trace-campaign", capturedSteps::add);

        StepEvent filterStep = capturedSteps.stream()
                .filter(step -> step.type() == StepType.filter)
                .findFirst()
                .orElseThrow();
        assertThat(filterStep.meta()).containsEntry("inputCount", 3);
        assertThat(filterStep.meta()).containsEntry("outputCount", 2);
        assertThat(filterStep.meta()).containsEntry("city", "Beijing");

        StepEvent calculationStep = capturedSteps.stream()
                .filter(step -> step.type() == StepType.calculation
                        && step.label().contains("Group by campaign type"))
                .findFirst()
                .orElseThrow();
        assertThat(calculationStep.detail()).contains("attainmentRate = actualOpportunityCount / totalNewCustomerTarget");
        assertThat(calculationStep.meta()).containsEntry("formula", "attainmentRate = actualOpportunityCount / totalNewCustomerTarget");
        assertThat(calculationStep.meta()).containsEntry("campaignCount", 2);
        assertThat(calculationStep.meta()).containsEntry("totalActualOpportunityCount", 13);
        assertThat(calculationStep.meta()).containsEntry("totalNewCustomerTarget", 20);
        assertThat((List<?>) calculationStep.meta().get("sampleRows")).isNotEmpty();
    }

    @Test
    void leadSourceStepsIncludeConversionFormulaAndSampleRows() {
        when(leadRepository.findAll()).thenReturn(List.of(
                new Lead("L1", "D001", "Dealer A", "Beijing", "Group A",
                        "Website", "New", "Model X", LocalDate.of(2026, 5, 1), true),
                new Lead("L2", "D001", "Dealer A", "Beijing", "Group A",
                        "Website", "Qualified", "Model X", LocalDate.of(2026, 5, 2), false),
                new Lead("L3", "D002", "Dealer B", "Beijing", "Group A",
                        "Referral", "New", "Model X", LocalDate.of(2026, 5, 3), true),
                new Lead("L4", "D003", "Dealer C", "Beijing", "Group A",
                        "Retail", "New", "Model X", LocalDate.of(2026, 5, 4), false),
                new Lead("L5", "D004", "Dealer D", "Shanghai", "Group B",
                        "Website", "New", "Model X", LocalDate.of(2026, 5, 5), true)
        ));
        List<StepEvent> capturedSteps = new ArrayList<>();

        service.plan("lead source analysis for Beijing in May 2026", "en", "trace-leads", capturedSteps::add);

        StepEvent filterStep = capturedSteps.stream()
                .filter(step -> step.type() == StepType.filter)
                .findFirst()
                .orElseThrow();
        assertThat(filterStep.meta()).containsEntry("inputCount", 5);
        assertThat(filterStep.meta()).containsEntry("outputCount", 4);
        assertThat(filterStep.meta()).containsEntry("city", "Beijing");

        StepEvent calculationStep = capturedSteps.stream()
                .filter(step -> step.type() == StepType.calculation
                        && step.label().contains("Group by lead source"))
                .findFirst()
                .orElseThrow();
        assertThat(calculationStep.detail()).contains("conversionRate = convertedCount / leadCount");
        assertThat(calculationStep.meta()).containsEntry("formula", "conversionRate = convertedCount / leadCount");
        assertThat(calculationStep.meta()).containsEntry("sourceCount", 3);
        assertThat(calculationStep.meta()).containsEntry("leadCount", 4);
        assertThat(calculationStep.meta()).containsEntry("convertedCount", 2);
        assertThat((List<?>) calculationStep.meta().get("sampleRows")).isNotEmpty();
    }

    @Test
    void campaignPerformanceQueryPathEmitsEvidenceSteps() {
        when(dataQueryService.query(org.mockito.ArgumentMatchers.eq("campaigns"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new DataQueryResponse("campaigns", Map.of("city", "Beijing"), 2, List.of(
                        row("campaignId", "C1", "dealerCode", "D001", "dealerName", "Dealer A", "city", "Beijing",
                                "dealerGroupName", "Group A", "productModel", "Model X", "campaignType", "Launch Day",
                                "createdDate", "2026-05-01", "actualOpportunityCount", 8, "totalNewCustomerTarget", 10),
                        row("campaignId", "C2", "dealerCode", "D002", "dealerName", "Dealer B", "city", "Beijing",
                                "dealerGroupName", "Group A", "productModel", "Model X", "campaignType", "Roadshow",
                                "createdDate", "2026-05-02", "actualOpportunityCount", 5, "totalNewCustomerTarget", 10)
                ), Map.of("campaignCount", 2)));
        List<StepEvent> capturedSteps = new ArrayList<>();

        service.plan("campaign performance for Beijing in May 2026", "en", "trace-campaign-query", capturedSteps::add);

        StepEvent filterStep = capturedSteps.stream()
                .filter(step -> step.type() == StepType.filter)
                .findFirst()
                .orElseThrow();
        assertThat(filterStep.meta()).containsEntry("outputCount", 2);
        assertThat(filterStep.meta()).containsEntry("city", "Beijing");

        StepEvent calculationStep = capturedSteps.stream()
                .filter(step -> step.type() == StepType.calculation
                        && step.label().contains("Group by campaign type"))
                .findFirst()
                .orElseThrow();
        assertThat(calculationStep.meta()).containsEntry("formula", "attainmentRate = actualOpportunityCount / totalNewCustomerTarget");
        assertThat(calculationStep.meta()).containsEntry("campaignCount", 2);
        assertThat(calculationStep.meta()).containsEntry("totalActualOpportunityCount", 13);
        assertThat(calculationStep.meta()).containsEntry("totalNewCustomerTarget", 20);
        assertThat((List<?>) calculationStep.meta().get("sampleRows")).isNotEmpty();
    }

    @Test
    void leadSourceQueryPathEmitsEvidenceSteps() {
        when(dataQueryService.query(org.mockito.ArgumentMatchers.eq("leads"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new DataQueryResponse("leads", Map.of("city", "Beijing"), 4, List.of(
                        row("leadId", "L1", "dealerCode", "D001", "dealerName", "Dealer A", "city", "Beijing",
                                "dealerGroupName", "Group A", "leadSource", "Website", "stageName", "New",
                                "productModel", "Model X", "createdDate", "2026-05-01", "isConverted", true),
                        row("leadId", "L2", "dealerCode", "D001", "dealerName", "Dealer A", "city", "Beijing",
                                "dealerGroupName", "Group A", "leadSource", "Website", "stageName", "Qualified",
                                "productModel", "Model X", "createdDate", "2026-05-02", "isConverted", false),
                        row("leadId", "L3", "dealerCode", "D002", "dealerName", "Dealer B", "city", "Beijing",
                                "dealerGroupName", "Group A", "leadSource", "Referral", "stageName", "New",
                                "productModel", "Model X", "createdDate", "2026-05-03", "isConverted", true),
                        row("leadId", "L4", "dealerCode", "D003", "dealerName", "Dealer C", "city", "Beijing",
                                "dealerGroupName", "Group A", "leadSource", "Retail", "stageName", "New",
                                "productModel", "Model X", "createdDate", "2026-05-04", "isConverted", false)
                ), Map.of("totalCount", 4)));
        List<StepEvent> capturedSteps = new ArrayList<>();

        service.plan("lead source analysis for Beijing in May 2026", "en", "trace-leads-query", capturedSteps::add);

        StepEvent filterStep = capturedSteps.stream()
                .filter(step -> step.type() == StepType.filter)
                .findFirst()
                .orElseThrow();
        assertThat(filterStep.meta()).containsEntry("outputCount", 4);
        assertThat(filterStep.meta()).containsEntry("city", "Beijing");

        StepEvent calculationStep = capturedSteps.stream()
                .filter(step -> step.type() == StepType.calculation
                        && step.label().contains("Group by lead source"))
                .findFirst()
                .orElseThrow();
        assertThat(calculationStep.meta()).containsEntry("formula", "conversionRate = convertedCount / leadCount");
        assertThat(calculationStep.meta()).containsEntry("sourceCount", 3);
        assertThat(calculationStep.meta()).containsEntry("leadCount", 4);
        assertThat(calculationStep.meta()).containsEntry("convertedCount", 2);
        assertThat((List<?>) calculationStep.meta().get("sampleRows")).isNotEmpty();
    }

    @Test
    void planWithoutCallbackStillWorks() {
        AnalyticsPlan plan = service.plan("北京 6 月目标达成率怎么样？", "zh");
        assertNotNull(plan);
        assertNotNull(plan.fallbackReply());
    }

    @Test
    void planWithCallbackNullOnStepDoesNotThrow() {
        // Should not throw when onStep is null
        AnalyticsPlan plan = service.plan(
                "北京 6 月目标达成率怎么样？", "zh", "trace1", null);
        assertNotNull(plan);
        assertNotNull(plan.fallbackReply());
    }

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

    private static Stream<Arguments> scenarioCases() {
        return Stream.of(
                Arguments.of("target achievement for the store", AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT),
                Arguments.of("opportunity funnel review", AnalyticsPlan.Scenario.OPPORTUNITY_FUNNEL),
                Arguments.of("sales follow up status", AnalyticsPlan.Scenario.SALES_FOLLOW_UP),
                Arguments.of("campaign performance analysis", AnalyticsPlan.Scenario.CAMPAIGN_PERFORMANCE),
                Arguments.of("dealer benchmark comparison", AnalyticsPlan.Scenario.DEALER_BENCHMARK),
                Arguments.of("lead source breakdown", AnalyticsPlan.Scenario.LEAD_SOURCE),
                Arguments.of("哪些门店经营活跃度最高？", AnalyticsPlan.Scenario.DEALER_BUSINESS_ACTIVITY),
                Arguments.of("哪些门店处于休眠状态？", AnalyticsPlan.Scenario.DEALER_BUSINESS_ACTIVITY),
                Arguments.of("Which dealers show the highest business activity?", AnalyticsPlan.Scenario.DEALER_BUSINESS_ACTIVITY),
                Arguments.of("Which dealers are dormant?", AnalyticsPlan.Scenario.DEALER_BUSINESS_ACTIVITY)
        );
    }

    private void assertContractMarkers(String reply) {
        assertThat(reply).contains("## Conclusion");
        assertThat(reply).contains("## Data Support");
        assertThat(reply).contains("## Short Analysis");
        assertThat(reply).contains("**Recommendations:**");
        assertThat(reply).contains("FOLLOW_UP_QUESTIONS:");
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

    private static Map<String, Object> row(Object... entries) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            row.put((String) entries[i], entries[i + 1]);
        }
        return row;
    }
}
