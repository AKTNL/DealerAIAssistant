package com.brand.agentpoc.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.dto.detail.TaskDetail;
import com.brand.agentpoc.dto.metrics.TargetMetrics;
import com.brand.agentpoc.dto.response.ApiPage;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.knowledge.application.KnowledgeService;
import com.brand.agentpoc.knowledge.domain.KnowledgeSearchResult;
import com.brand.agentpoc.service.AnalyticsApiService;
import com.brand.agentpoc.service.AnalyticsMetadata;
import com.brand.agentpoc.service.AnalyticsPlan;
import com.brand.agentpoc.service.DashboardService;
import com.brand.agentpoc.service.RuleBasedAnalyticsService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ControlledAgentToolServiceTest {

    private DashboardService dashboardService;
    private AnalyticsApiService analyticsApiService;
    private RuleBasedAnalyticsService analyticsService;
    private KnowledgeService knowledgeService;
    private ControlledAgentToolService toolService;

    @BeforeEach
    void setUp() {
        dashboardService = mock(DashboardService.class);
        analyticsApiService = mock(AnalyticsApiService.class);
        analyticsService = mock(RuleBasedAnalyticsService.class);
        knowledgeService = mock(KnowledgeService.class);
        toolService = new ControlledAgentToolService(
                dashboardService,
                analyticsApiService,
                analyticsService,
                knowledgeService
        );
    }

    @Test
    void mapsTargetMetricFiltersToTheExistingApplicationService() {
        ApiResult<TargetMetrics> expected = ApiResult.success(
                new TargetMetrics(0, 0, 0, 0, 0.0, null, null)
        );
        when(analyticsApiService.getTargetMetrics(
                2026, 8, "M7", "BJ001", "Beijing", null, "North"
        )).thenReturn(expected);

        AgentToolResult result = toolService.queryMetric("targetAchievement", Map.of(
                "targetYear", "2026",
                "targetMonth", "8",
                "productModel", "M7",
                "dealerCode", "BJ001",
                "city", "Beijing",
                "dealerGroupName", "North"
        ));

        assertThat(result.kind()).isEqualTo("targetMetric");
        assertThat(result.data()).isSameAs(expected);
        verify(analyticsApiService).getTargetMetrics(
                2026, 8, "M7", "BJ001", "Beijing", null, "North"
        );
    }

    @Test
    void boundsDetailQueriesAndUsesSafeDefaults() {
        ApiResult<ApiPage<TaskDetail>> expected = ApiResult.success(ApiPage.of(List.of(), 0, 1, 20));
        when(analyticsApiService.getTaskDetails("BJ001", "late", 1, 20, "status", "asc"))
                .thenReturn(expected);

        AgentToolResult result = toolService.queryDetails(
                "tasks",
                Map.of("dealerCode", "BJ001", "keyword", "late"),
                null,
                null,
                "status",
                "asc"
        );

        assertThat(result.kind()).isEqualTo("taskDetails");
        assertThat(result.data()).isSameAs(expected);
        verify(analyticsApiService).getTaskDetails("BJ001", "late", 1, 20, "status", "asc");
    }

    @Test
    void rejectsUnknownFiltersAndPageSizesAboveTheAgentLimit() {
        assertThatThrownBy(() -> toolService.queryMetric(
                "task",
                Map.of("sql", "select * from dealer_tasks")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported filter for this metric or dataset.");

        assertThatThrownBy(() -> toolService.queryDetails(
                "lead", Map.of(), 1, 51, null, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pageSize is outside the allowed range.");

        verifyNoInteractions(analyticsApiService);
    }

    @Test
    void rejectsUnsupportedMetricAndDetailDatasetTypes() {
        assertThatThrownBy(() -> toolService.queryMetric("dealer", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported metric or dataset.");

        assertThatThrownBy(() -> toolService.queryDetails(
                "importBatch", Map.of(), 1, 20, null, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported metric or dataset.");

        verifyNoInteractions(analyticsApiService);
    }

    @Test
    void rejectsInvalidSortAndScenarioInputsBeforeDelegation() {
        assertThatThrownBy(() -> toolService.queryDetails(
                "task", Map.of(), 1, 20, "importBatchId", "asc"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported sort field for this dataset.");

        assertThatThrownBy(() -> toolService.runScenarioAnalysis("Analyze targets", "fr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("language must be zh or en.");

        assertThatThrownBy(() -> toolService.runScenarioAnalysis("x".repeat(2_001), "en"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("question exceeds the allowed length.");

        verifyNoInteractions(analyticsApiService, analyticsService);
    }

    @Test
    void returnsGroundedScenarioFactsAndFallbackWithoutReimplementingAnalytics() {
        AnalyticsMetadata metadata = new AnalyticsMetadata(
                "Target analysis",
                "active batch",
                "won / target",
                List.of("targets"),
                List.of("organization roles are not configured"),
                "medium"
        );
        AnalyticsPlan plan = new AnalyticsPlan(
                AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT,
                null,
                "active batch",
                List.of(),
                "",
                "grounded facts",
                "fallback report",
                metadata
        );
        when(analyticsService.plan("Which dealer is behind target?", "en")).thenReturn(plan);

        AgentScenarioAnalysis result = toolService.runScenarioAnalysis(
                "Which dealer is behind target?",
                "en"
        );

        assertThat(result.scenario()).isEqualTo("TARGET_ACHIEVEMENT");
        assertThat(result.scope()).isEqualTo("active batch");
        assertThat(result.groundedReference()).isEqualTo("grounded facts");
        assertThat(result.fallbackReply()).isEqualTo("fallback report");
        assertThat(result.metadata()).isSameAs(metadata);
    }

    @Test
    void delegatesKnowledgeRetrievalThroughThePublicApplicationService() {
        KnowledgeSearchResult expected = KnowledgeSearchResult.from("目标达成率口径", List.of());
        when(knowledgeService.retrieve("目标达成率口径", 3)).thenReturn(expected);

        KnowledgeSearchResult result = toolService.retrieveKnowledge("目标达成率口径", 3);

        assertThat(result).isSameAs(expected);
        verify(knowledgeService).retrieve("目标达成率口径", 3);
        verifyNoInteractions(dashboardService, analyticsApiService, analyticsService);
    }
}
