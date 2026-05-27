package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.dto.detail.TargetDetail;
import com.brand.agentpoc.dto.metrics.CampaignMetrics;
import com.brand.agentpoc.dto.metrics.LeadMetrics;
import com.brand.agentpoc.dto.metrics.OpportunityMetrics;
import com.brand.agentpoc.dto.metrics.TargetMetrics;
import com.brand.agentpoc.dto.metrics.TaskMetrics;
import com.brand.agentpoc.dto.response.ApiPage;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.entity.Campaign;
import com.brand.agentpoc.entity.Lead;
import com.brand.agentpoc.entity.Opportunity;
import com.brand.agentpoc.entity.Target;
import com.brand.agentpoc.entity.Task;
import com.brand.agentpoc.repository.*;
import com.brand.agentpoc.test.LogCapture;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalyticsApiServiceTest {

    private TargetRepository targetRepository;
    private OpportunityRepository opportunityRepository;
    private CampaignRepository campaignRepository;
    private TaskRepository taskRepository;
    private LeadRepository leadRepository;
    private AnalyticsApiService service;

    @BeforeEach
    void setUp() {
        targetRepository = mock(TargetRepository.class);
        opportunityRepository = mock(OpportunityRepository.class);
        campaignRepository = mock(CampaignRepository.class);
        taskRepository = mock(TaskRepository.class);
        leadRepository = mock(LeadRepository.class);
        service = new AnalyticsApiService(
                mock(DealerRepository.class),
                opportunityRepository,
                campaignRepository,
                taskRepository,
                targetRepository,
                leadRepository
        );
    }

    @Test
    void getTargetMetricsReturnsZeroValuesWhenNoData() {
        when(targetRepository.findAll()).thenReturn(Collections.emptyList());

        ApiResult<TargetMetrics> result = service.getTargetMetrics(null, null, null, null);

        assertThat(result.code()).isEqualTo(200);
        TargetMetrics m = result.data();
        assertThat(m.totalDealers()).isEqualTo(0);
        assertThat(m.totalAsKTarget()).isEqualTo(0);
        assertThat(m.totalOpportunityWon()).isEqualTo(0);
        assertThat(m.averageAchievementRate()).isEqualTo(0.0);
        assertThat(m.lowestDealer()).isNull();
        assertThat(m.highestDealer()).isNull();
    }

    @Test
    void getTargetMetricsComputesCorrectAggregates() {
        Target t1 = new Target("D001", "Store A", "Beijing", "Group1",
                "ModelX", 2026, 5, 100, 80, 0);
        Target t2 = new Target("D001", "Store A", "Beijing", "Group1",
                "ModelY", 2026, 5, 50, 45, 0);
        Target t3 = new Target("D002", "Store B", "Beijing", "Group1",
                "ModelX", 2026, 5, 200, 120, 0);
        when(targetRepository.findByTargetYearAndTargetMonth(2026, 5)).thenReturn(List.of(t1, t2, t3));

        ApiResult<TargetMetrics> result = service.getTargetMetrics(2026, 5, null, null);

        assertThat(result.code()).isEqualTo(200);
        TargetMetrics m = result.data();
        assertThat(m.totalDealers()).isEqualTo(2);
        assertThat(m.totalAsKTarget()).isEqualTo(350);
        assertThat(m.totalOpportunityWon()).isEqualTo(245);
        assertThat(m.averageAchievementRate()).isCloseTo(70.0, org.assertj.core.data.Offset.offset(0.1));
        assertThat(m.lowestDealer().dealerCode()).isEqualTo("D002");
        assertThat(m.highestDealer().dealerCode()).isEqualTo("D001");
    }

    @Test
    void getTargetMetricsCanFilterByCityAndPartialDealerName() {
        Target t1 = new Target("7036", "经销商AJ(沈阳)", "沈阳", "经销商集团P",
                "Aurora S", 2026, 5, 100, 80, 0);
        Target t2 = new Target("7037", "经销商AK(太原)", "太原", "经销商集团Q",
                "Aurora S", 2026, 5, 100, 10, 0);
        when(targetRepository.findByTargetYearAndTargetMonth(2026, 5)).thenReturn(List.of(t1, t2));

        ApiResult<TargetMetrics> result = service.getTargetMetrics(2026, 5, null, null,
                "沈阳", "经销商AJ", null);

        assertThat(result.data().totalDealers()).isEqualTo(1);
        assertThat(result.data().totalAsKTarget()).isEqualTo(100);
        assertThat(result.data().totalOpportunityWon()).isEqualTo(80);
        assertThat(result.data().lowestDealer().dealerCode()).isEqualTo("7036");
    }

    @Test
    void getTargetDetailsPaginatesCorrectly() {
        List<Target> targets = new java.util.ArrayList<>();
        for (int i = 0; i < 55; i++) {
            targets.add(new Target("D00" + (i + 1), "Store " + i, "City", "Group",
                    "Model", 2026, 5, 100, 80, 0));
        }
        when(targetRepository.findAll()).thenReturn(targets);

        ApiResult<ApiPage<TargetDetail>> result = service.getTargetDetails(null, null, null, null, 1, 50, "dealerCode", "asc");

        assertThat(result.code()).isEqualTo(200);
        assertThat(result.data().items()).hasSize(50);
        assertThat(result.data().total()).isEqualTo(55);
        assertThat(result.data().page()).isEqualTo(1);
        assertThat(result.data().pageSize()).isEqualTo(50);
    }

    @Test
    void getTargetDetailsPageZeroReturnsEmptyItems() {
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Store A", "Beijing", "Group1", "ModelX", 2026, 5, 100, 80, 0)
        ));

        ApiResult<ApiPage<TargetDetail>> result = service.getTargetDetails(null, null, null, null, 0, 50, "dealerCode", "asc");

        assertThat(result.code()).isEqualTo(200);
        assertThat(result.data().items()).isEmpty();
        assertThat(result.data().total()).isEqualTo(1);
    }

    @Test
    void getTargetDetailsPageExceedsTotalReturnsEmptyItems() {
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Store A", "Beijing", "Group1", "ModelX", 2026, 5, 100, 80, 0)
        ));

        ApiResult<ApiPage<TargetDetail>> result = service.getTargetDetails(null, null, null, null, 3, 50, "dealerCode", "asc");

        assertThat(result.code()).isEqualTo(200);
        assertThat(result.data().items()).isEmpty();
        assertThat(result.data().total()).isEqualTo(1);
    }

    @Test
    void getOpportunityMetricsUsesDealerAndDateRepositoryFilter() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 31);
        when(opportunityRepository.findByDealerCodeIgnoreCaseAndCreatedDateBetween("D001", start, end))
                .thenReturn(List.of(new Opportunity("O1", "D001", "Store A", "Beijing", "Group1", "ModelX",
                        "Won", "Website", LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 20), 100)));

        ApiResult<OpportunityMetrics> result = service.getOpportunityMetrics("D001", "2026-05-01", "2026-05-31");

        assertThat(result.data().totalOpportunities()).isEqualTo(1);
        verify(opportunityRepository).findByDealerCodeIgnoreCaseAndCreatedDateBetween("D001", start, end);
        verify(opportunityRepository, never()).findAll();
    }

    @Test
    void getLeadMetricsUsesLeadSourceAndDealerRepositoryFilter() {
        when(leadRepository.findByLeadSourceIgnoreCaseAndDealerCodeIgnoreCase("Website", "D001"))
                .thenReturn(List.of(new Lead("L1", "D001", "Store A", "Beijing", "Group1",
                        "Website", "New", "ModelX", LocalDate.of(2026, 5, 1), true)));

        ApiResult<LeadMetrics> result = service.getLeadMetrics("Website", "D001");

        assertThat(result.data().totalLeads()).isEqualTo(1);
        verify(leadRepository).findByLeadSourceIgnoreCaseAndDealerCodeIgnoreCase("Website", "D001");
        verify(leadRepository, never()).findAll();
    }

    @Test
    void getTaskMetricsUsesDealerRepositoryFilter() {
        when(taskRepository.findByDealerCodeIgnoreCase("D001"))
                .thenReturn(List.of(new Task("T1", "D001", "Store A", "Beijing", "Group1",
                        "O1", "Completed", LocalDate.of(2026, 5, 1))));

        ApiResult<TaskMetrics> result = service.getTaskMetrics("D001");

        assertThat(result.data().totalTasks()).isEqualTo(1);
        verify(taskRepository).findByDealerCodeIgnoreCase("D001");
        verify(taskRepository, never()).findAll();
    }

    @Test
    void getCampaignMetricsUsesCampaignTypeRepositoryFilter() {
        when(campaignRepository.findByCampaignTypeIgnoreCase("Roadshow"))
                .thenReturn(List.of(new Campaign("C1", "D001", "Store A", "Beijing", "Group1",
                        "ModelX", "Roadshow", LocalDate.of(2026, 5, 1), 4, 10)));

        ApiResult<CampaignMetrics> result = service.getCampaignMetrics("Roadshow");

        assertThat(result.data().totalCampaigns()).isEqualTo(1);
        verify(campaignRepository).findByCampaignTypeIgnoreCase("Roadshow");
        verify(campaignRepository, never()).findAll();
    }

    @Test
    void getOpportunityMetricsLogsInvalidDateFilterAndKeepsReturningData() {
        when(opportunityRepository.findByDealerCodeIgnoreCase("D001"))
                .thenReturn(List.of(new Opportunity("O1", "D001", "Store A", "Beijing", "Group1", "ModelX",
                        "Won", "Website", LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 20), 100)));

        try (LogCapture logs = LogCapture.attach(AnalyticsApiService.class)) {
            ApiResult<OpportunityMetrics> result = service.getOpportunityMetrics("D001", "bad-date", "2026-05-31");

            assertThat(result.data().totalOpportunities()).isEqualTo(1);
            verify(opportunityRepository).findByDealerCodeIgnoreCase("D001");
            assertThat(logs.messages()).anySatisfy(message -> assertThat(message)
                    .contains("startDate", "bad-date", "DateTimeParseException"));
        }
    }
}
