package com.brand.agentpoc.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.dto.response.DashboardSummary;
import com.brand.agentpoc.dto.response.ImportDataStatus;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import com.brand.agentpoc.reporting.infrastructure.InMemoryReportDraftStore;
import com.brand.agentpoc.service.DashboardService;
import com.brand.agentpoc.service.ImportBatchService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

    private DashboardService dashboardService;
    private ImportBatchService importBatchService;
    private ReportDraftStore draftStore;
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        dashboardService = mock(DashboardService.class);
        importBatchService = mock(ImportBatchService.class);
        draftStore = new InMemoryReportDraftStore();
        AppProperties properties = new AppProperties();
        properties.getModel().setName("test-model");
        reportService = new ReportService(
                dashboardService,
                importBatchService,
                draftStore,
                Clock.fixed(Instant.parse("2026-08-10T05:00:00Z"), ZoneOffset.UTC),
                properties,
                new ReportMarkdownRenderer()
        );
    }

    @Test
    void generatesCitableMarkdownFromTheCurrentDashboardSnapshot() {
        when(dashboardService.getSummary()).thenReturn(summary());

        ReportDraft draft = reportService.generate(new ReportGenerationRequest(
                "weekly", "en", null, null, null
        ));

        assertThat(draft.reportType().wireName()).isEqualTo("weekly");
        assertThat(draft.generatedAt()).isEqualTo(Instant.parse("2026-08-10T05:00:00Z"));
        assertThat(draft.importBatchId()).isEqualTo("batch-1");
        assertThat(draft.scope().type()).isEqualTo("GLOBAL");
        assertThat(draft.model()).isEqualTo("test-model");
        assertThat(draft.promptVersion()).isEqualTo(ReportService.PROMPT_VERSION);
        assertThat(draft.markdown()).contains("# Dealer Operations Weekly Report");
        assertThat(draft.markdown()).contains("- Target achievement: 75.0%");
        assertThat(reportService.require(draft.id())).isEqualTo(draft);
    }

    @Test
    void rendersChineseTitlesAndLabelsWithoutEncodingLoss() {
        when(dashboardService.getSummary()).thenReturn(summary());

        ReportDraft draft = reportService.generate(new ReportGenerationRequest(
                "weekly", "zh", null, null, null
        ));

        assertThat(draft.markdown()).contains("# \u7ecf\u8425\u5468\u62a5");
        assertThat(draft.markdown()).contains("\u76ee\u6807\u8fbe\u6210\u7387");
        assertThat(draft.markdown()).doesNotContain("涓");
    }

    @Test
    void requiresTopicAndRejectsUnsupportedScope() {
        when(dashboardService.getSummary()).thenReturn(summary());

        assertThatThrownBy(() -> reportService.generate(new ReportGenerationRequest(
                "topic", "zh", null, null, ""
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topic is required for a topic report.");

        assertThatThrownBy(() -> reportService.generate(new ReportGenerationRequest(
                "daily", "zh", "DEALER", "D001", null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only GLOBAL report scope is supported.");
        verifyNoInteractions(dashboardService);
    }

    @Test
    void validatesTheCompleteRequestBeforeReadingDashboardData() {
        assertThatThrownBy(() -> reportService.generate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("report request is required.");
        assertThatThrownBy(() -> reportService.generate(new ReportGenerationRequest(
                null, "zh", null, null, null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reportType is required.");
        assertThatThrownBy(() -> reportService.generate(new ReportGenerationRequest(
                "daily", "fr", null, null, null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("language must be zh or en.");
        assertThatThrownBy(() -> reportService.generate(new ReportGenerationRequest(
                "daily", "zh", "GLOBAL", "dealer-1", null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("GLOBAL report scope cannot have a scope id.");
        assertThatThrownBy(() -> reportService.generate(new ReportGenerationRequest(
                "topic", "zh", null, null, "x".repeat(501)
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topic exceeds the allowed length.");

        verifyNoInteractions(dashboardService, importBatchService);
    }

    @Test
    void recordsDeterministicWhenNoModelNameIsConfigured() {
        AppProperties properties = new AppProperties();
        ReportService deterministicService = new ReportService(
                dashboardService,
                importBatchService,
                draftStore,
                Clock.fixed(Instant.parse("2026-08-10T05:00:00Z"), ZoneOffset.UTC),
                properties,
                new ReportMarkdownRenderer()
        );
        when(dashboardService.getSummary()).thenReturn(summary());

        ReportDraft draft = deterministicService.generate(new ReportGenerationRequest(
                "daily", "en", null, null, null
        ));

        assertThat(draft.model()).isEqualTo("deterministic");
    }

    @Test
    void listsNewestDraftsFirst() {
        when(dashboardService.getSummary()).thenReturn(summary());
        reportService.generate(new ReportGenerationRequest("daily", "zh", null, null, null));
        reportService.generate(new ReportGenerationRequest("monthly", "zh", null, null, null));

        assertThat(reportService.list()).hasSize(2)
                .extracting(ReportDraft::reportType)
                .containsExactlyInAnyOrder(
                        com.brand.agentpoc.reporting.domain.ReportType.MONTHLY,
                        com.brand.agentpoc.reporting.domain.ReportType.DAILY);
        verify(dashboardService, org.mockito.Mockito.times(2)).getSummary();
    }

    private DashboardSummary summary() {
        DashboardSummary.Overview overview = new DashboardSummary.Overview(
                2, 100, 80, 75, 10, 4, 20, 5, 8, 2, 3, 4,
                75.0, 40.0, 25.0, 50.0, 25.0, 60.0
        );
        return new DashboardSummary(
                new DashboardSummary.DataStatus(
                        "built-in-sample", true, true, true, "sample",
                        new ImportDataStatus.Batch("batch-1", true, "GLOBAL", null, "2026-08-10T05:00:00Z"),
                        100, 90, 10, 1, List.of("Targets:missing=1")
                ),
                overview,
                new DashboardSummary.TargetAchievement(
                        100, 75,
                        List.of(new DashboardSummary.DealerAchievement("D001", "Dealer One", "Beijing", 50.0, 10, 20)),
                        List.of()
                ),
                new DashboardSummary.OpportunityFunnel(10, 4, 3, 3, 40.0, List.of()),
                new DashboardSummary.LeadSources(20, 5, 25.0, List.of()),
                new DashboardSummary.FollowUpTasks(8, 4, 2, 2, 50.0, 25.0, List.of()),
                new DashboardSummary.CampaignEffect(3, 4, 10, 4, 10, 40.0, List.of())
        );
    }
}
