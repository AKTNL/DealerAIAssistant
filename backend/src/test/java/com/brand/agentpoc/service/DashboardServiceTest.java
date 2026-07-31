package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.dto.response.DashboardSummary;
import com.brand.agentpoc.dto.response.ImportDataStatus;
import com.brand.agentpoc.entity.Campaign;
import com.brand.agentpoc.entity.Dealer;
import com.brand.agentpoc.entity.Lead;
import com.brand.agentpoc.entity.Opportunity;
import com.brand.agentpoc.entity.Target;
import com.brand.agentpoc.entity.Task;
import com.brand.agentpoc.repository.CampaignRepository;
import com.brand.agentpoc.repository.DealerRepository;
import com.brand.agentpoc.repository.LeadRepository;
import com.brand.agentpoc.repository.OpportunityRepository;
import com.brand.agentpoc.repository.TargetRepository;
import com.brand.agentpoc.repository.TaskRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

    private DealerRepository dealerRepository;
    private TargetRepository targetRepository;
    private OpportunityRepository opportunityRepository;
    private LeadRepository leadRepository;
    private TaskRepository taskRepository;
    private CampaignRepository campaignRepository;
    private ImportBatchService importBatchService;
    private ImportQualityService importQualityService;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dealerRepository = mock(DealerRepository.class);
        targetRepository = mock(TargetRepository.class);
        opportunityRepository = mock(OpportunityRepository.class);
        leadRepository = mock(LeadRepository.class);
        taskRepository = mock(TaskRepository.class);
        campaignRepository = mock(CampaignRepository.class);
        importBatchService = new ImportBatchService();
        importQualityService = new ImportQualityService();
        dashboardService = new DashboardService(
                dealerRepository,
                targetRepository,
                opportunityRepository,
                leadRepository,
                taskRepository,
                campaignRepository,
                importBatchService,
                importQualityService
        );
    }

    @Test
    void getSummaryAggregatesOnlyTheActiveBatch() {
        String activeBatch = "dashboard-active";
        importBatchService.activateGlobalBatch(activeBatch, "configured-workbook", false, "ok");
        importQualityService.publish(dataStatus());

        when(dealerRepository.findAll()).thenReturn(List.of(
                new Dealer("D001", "Store A", "Beijing", "Group 1", activeBatch),
                new Dealer("D002", "Store B", "Shanghai", "Group 1", activeBatch),
                new Dealer("D999", "Inactive", "Nanjing", "Group 9", "old-batch")
        ));
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Store A", "Beijing", "Group 1", "Model X", 2026, 5, 100, 50, 60, activeBatch),
                new Target("D001", "Store A", "Beijing", "Group 1", "Model Y", 2026, 5, null, 10, 12, activeBatch),
                new Target("D002", "Store B", "Shanghai", "Group 1", "Model X", 2026, 5, 100, 20, 30, activeBatch),
                new Target("D999", "Inactive", "Nanjing", "Group 9", "Model X", 2026, 5, 100, 100, 100, "old-batch")
        ));
        when(opportunityRepository.findAll()).thenReturn(List.of(
                opportunity("O1", "D001", "Store A", "Closed Won", "Website", activeBatch),
                opportunity("O2", "D001", "Store A", "Closed Lost", "Website", activeBatch),
                opportunity("O3", "D002", "Store B", "Negotiation", "Ads", activeBatch),
                opportunity("O9", "D999", "Inactive", "Closed Won", "Website", "old-batch")
        ));
        when(leadRepository.findAll()).thenReturn(List.of(
                lead("L1", "D001", "Store A", "Website", true, activeBatch),
                lead("L2", "D001", "Store A", "Website", false, activeBatch),
                lead("L3", "D002", "Store B", "Ads", false, activeBatch),
                lead("L9", "D999", "Inactive", "Website", true, "old-batch")
        ));
        when(taskRepository.findAll()).thenReturn(List.of(
                task("T1", "D001", "Store A", "Completed", activeBatch),
                task("T2", "D002", "Store B", "Overdue", activeBatch),
                task("T3", "D002", "Store B", "Open", activeBatch),
                task("T9", "D999", "Inactive", "Overdue", "old-batch")
        ));
        when(campaignRepository.findAll()).thenReturn(List.of(
                campaign("C1", "Launch A", "D001", "Store A", 2, 10, activeBatch),
                campaign("C2", "Launch B", "D002", "Store B", 8, 10, activeBatch),
                campaign("C3", "Launch C", "D002", "Store B", 5, null, activeBatch),
                campaign("C9", "Inactive", "D999", "Inactive", 10, 10, "old-batch")
        ));

        DashboardSummary summary = dashboardService.getSummary();

        assertThat(summary.overview().dealerCount()).isEqualTo(2);
        assertThat(summary.overview().totalTarget()).isEqualTo(200);
        assertThat(summary.overview().totalWon()).isEqualTo(80);
        assertThat(summary.overview().comparableWon()).isEqualTo(70);
        assertThat(summary.overview().targetAchievementRate()).isEqualTo(35.0);
        assertThat(summary.overview().totalOpportunities()).isEqualTo(3);
        assertThat(summary.overview().opportunityWinRate()).isEqualTo(50.0);
        assertThat(summary.overview().leadConversionRate()).isEqualTo(33.3);
        assertThat(summary.overview().taskCompletionRate()).isEqualTo(33.3);
        assertThat(summary.overview().campaignAttainmentRate()).isEqualTo(50.0);

        assertThat(summary.targetAchievement().lowDealers())
                .extracting(DashboardSummary.DealerAchievement::dealerCode)
                .containsExactly("D002", "D001");
        assertThat(summary.followUpTasks().backlogDealers().getFirst().dealerCode()).isEqualTo("D002");
        assertThat(summary.campaignEffect().lowPerformingCampaigns().getFirst().campaignId()).isEqualTo("C1");
        assertThat(summary.dataStatus().lowConfidence()).isTrue();
        assertThat(summary.dataStatus().issueCount()).isEqualTo(1);
        assertThat(summary.dataStatus().issueSummaries()).containsExactly("Lead:missing_created_date=1");
    }

    private ImportDataStatus dataStatus() {
        Map<String, ImportDataStatus.SheetStatus> sheets = new LinkedHashMap<>();
        sheets.put("Lead", new ImportDataStatus.SheetStatus(
                3,
                2,
                0,
                1,
                Map.of("missing_created_date", 1)
        ));
        return new ImportDataStatus(
                "configured-workbook",
                false,
                "Configured workbook imported successfully.",
                importBatchService.activeStatusBatch(),
                new ImportDataStatus.Totals(3, 2, 0, 1),
                sheets
        );
    }

    private Opportunity opportunity(String id, String dealerCode, String dealerName, String stage, String source, String batch) {
        return new Opportunity(
                id,
                dealerCode,
                dealerName,
                "Beijing",
                "Group 1",
                "Model X",
                "未知",
                stage,
                source,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 15),
                80,
                batch
        );
    }

    private Lead lead(String id, String dealerCode, String dealerName, String source, boolean converted, String batch) {
        return new Lead(
                id,
                dealerCode,
                dealerName,
                "Beijing",
                "Group 1",
                source,
                "New",
                "Model X",
                LocalDate.of(2026, 5, 1),
                converted,
                batch
        );
    }

    private Task task(String id, String dealerCode, String dealerName, String status, String batch) {
        return new Task(
                id,
                dealerCode,
                dealerName,
                "Beijing",
                "Group 1",
                "O1",
                "Follow up",
                status,
                LocalDate.of(2026, 5, 1),
                batch
        );
    }

    private Campaign campaign(
            String id,
            String name,
            String dealerCode,
            String dealerName,
            Integer actual,
            Integer target,
            String batch
    ) {
        return new Campaign(
                id,
                name,
                dealerCode,
                dealerName,
                "Beijing",
                "Group 1",
                "Model X",
                "Event",
                "Roadshow",
                LocalDate.of(2026, 5, 1),
                target,
                actual,
                0,
                0,
                0,
                target,
                batch
        );
    }
}
