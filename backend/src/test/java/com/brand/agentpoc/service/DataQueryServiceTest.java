package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.dto.response.DataQueryResponse;
import com.brand.agentpoc.entity.Campaign;
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
import com.brand.agentpoc.test.LogCapture;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataQueryServiceTest {

    private DealerRepository dealerRepository;
    private OpportunityRepository opportunityRepository;
    private CampaignRepository campaignRepository;
    private TaskRepository taskRepository;
    private TargetRepository targetRepository;
    private LeadRepository leadRepository;
    private DataQueryService service;

    @BeforeEach
    void setUp() {
        dealerRepository = mock(DealerRepository.class);
        opportunityRepository = mock(OpportunityRepository.class);
        campaignRepository = mock(CampaignRepository.class);
        taskRepository = mock(TaskRepository.class);
        targetRepository = mock(TargetRepository.class);
        leadRepository = mock(LeadRepository.class);

        when(dealerRepository.findAll()).thenReturn(List.of());
        when(opportunityRepository.findAll()).thenReturn(List.of());
        when(campaignRepository.findAll()).thenReturn(List.of());
        when(taskRepository.findAll()).thenReturn(List.of());
        when(targetRepository.findAll()).thenReturn(List.of());
        when(leadRepository.findAll()).thenReturn(List.of());

        service = new DataQueryService(
                dealerRepository,
                opportunityRepository,
                campaignRepository,
                taskRepository,
                targetRepository,
                leadRepository
        );
    }

    @Test
    void dataQueryResponseUsesRootMetadataContract() {
        DataQueryResponse response = service.query("opportunities", Map.of());

        assertThat(Arrays.stream(response.getClass().getRecordComponents())
                .map(component -> component.getName()))
                .containsExactly("dataset", "filters", "count", "items", "metadata");
        assertThat(response.dataset()).isEqualTo("opportunities");
        assertThat(count(response)).isZero();
        assertThat(metadata(response)).containsEntry("totalCount", 0);
    }

    @Test
    void queryOpportunitiesReturnsTotalCountInMetadataOnly() {
        when(opportunityRepository.findByDealerCodeIgnoreCase("D001")).thenReturn(List.of(
                new Opportunity("O1", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                        "Negotiation", "Website", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 20), 80),
                new Opportunity("O2", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                        "Won", "Referral", LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 21), 100)
        ));

        DataQueryResponse response = service.query("opportunities", Map.of("dealerCode", "D001"));

        assertThat(count(response)).isEqualTo(2);
        assertThat(metadata(response)).containsEntry("totalCount", 2);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).allSatisfy(item -> assertThat(item).doesNotContainKey("totalCount"));
    }

    @Test
    void queryCampaignsReturnsCampaignCountInMetadataOnly() {
        when(campaignRepository.findByDealerCodeIgnoreCase("D001")).thenReturn(List.of(
                new Campaign("C1", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                        "Roadshow", LocalDate.of(2026, 5, 1), 4, 10),
                new Campaign("C2", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                        "Launch Day", LocalDate.of(2026, 5, 3), 6, 12)
        ));

        DataQueryResponse response = service.query("campaigns", Map.of("dealerCode", "D001"));

        assertThat(count(response)).isEqualTo(2);
        assertThat(metadata(response)).containsEntry("campaignCount", 2);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).allSatisfy(item -> assertThat(item).doesNotContainKey("campaignCount"));
    }

    @Test
    void queryTasksReturnsTotalTaskCountInMetadataOnly() {
        when(taskRepository.findByDealerCodeIgnoreCase("D001")).thenReturn(List.of(
                new Task("T1", "D001", "Dealer A", "Beijing", "Group A", "O1", "Open", LocalDate.of(2026, 5, 1)),
                new Task("T2", "D001", "Dealer A", "Beijing", "Group A", "O2", "Overdue", LocalDate.of(2026, 5, 2))
        ));

        DataQueryResponse response = service.query("tasks", Map.of("dealerCode", "D001"));

        assertThat(count(response)).isEqualTo(2);
        assertThat(metadata(response)).containsEntry("totalTaskCount", 2);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).allSatisfy(item -> assertThat(item).doesNotContainKey("totalTaskCount"));
    }

    @Test
    void queryLeadsReturnsTotalCountInMetadataOnly() {
        when(leadRepository.findByDealerCodeIgnoreCase("D001")).thenReturn(List.of(
                new Lead("L1", "D001", "Dealer A", "Beijing", "Group A", "Website", "New",
                        "Model X", LocalDate.of(2026, 5, 1), true),
                new Lead("L2", "D001", "Dealer A", "Beijing", "Group A", "Website", "Qualified",
                        "Model X", LocalDate.of(2026, 5, 2), false)
        ));

        DataQueryResponse response = service.query("leads", Map.of("dealerCode", "D001"));

        assertThat(count(response)).isEqualTo(2);
        assertThat(metadata(response)).containsEntry("totalCount", 2);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).allSatisfy(item -> assertThat(item).doesNotContainKey("totalCount"));
    }

    @Test
    void queryOpportunitiesUsesDealerCodeRepositoryFilter() {
        Opportunity opportunity = new Opportunity("O1", "D001", "Dealer A", "Beijing", "Group A", "Model X",
                "Negotiation", "Website", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 20), 80);
        when(opportunityRepository.findByDealerCodeIgnoreCase("D001")).thenReturn(List.of(opportunity));

        DataQueryResponse response = service.query("opportunities", Map.of("dealerCode", "D001"));

        assertThat(count(response)).isEqualTo(1);
        verify(opportunityRepository).findByDealerCodeIgnoreCase("D001");
        verify(opportunityRepository, never()).findAll();
    }

    @Test
    void queryTasksUsesDealerCodeRepositoryFilter() {
        Task task = new Task("T1", "D001", "Dealer A", "Beijing", "Group A", "O1", "Open",
                LocalDate.of(2026, 5, 1));
        when(taskRepository.findByDealerCodeIgnoreCase("D001")).thenReturn(List.of(task));

        DataQueryResponse response = service.query("tasks", Map.of("dealerCode", "D001"));

        assertThat(count(response)).isEqualTo(1);
        verify(taskRepository).findByDealerCodeIgnoreCase("D001");
        verify(taskRepository, never()).findAll();
    }

    @Test
    void queryLeadsUsesDealerCodeRepositoryFilter() {
        Lead lead = new Lead("L1", "D001", "Dealer A", "Beijing", "Group A", "Website", "New",
                "Model X", LocalDate.of(2026, 5, 1), true);
        when(leadRepository.findByDealerCodeIgnoreCase("D001")).thenReturn(List.of(lead));

        DataQueryResponse response = service.query("leads", Map.of("dealerCode", "D001"));

        assertThat(count(response)).isEqualTo(1);
        verify(leadRepository).findByDealerCodeIgnoreCase("D001");
        verify(leadRepository, never()).findAll();
    }

    @Test
    void queryTargetsLogsInvalidIntegerFilterAndKeepsReturningData() {
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Dealer A", "Beijing", "Group A", "Model X", 2026, 5, 10, 8, 0)
        ));

        try (LogCapture logs = LogCapture.attach(DataQueryService.class)) {
            DataQueryResponse response = service.query("targets", Map.of("targetYear", "20x6"));

            assertThat(count(response)).isEqualTo(1);
            verify(targetRepository).findAll();
            assertThat(logs.messages()).anySatisfy(message -> assertThat(message)
                    .contains("targetYear", "20x6", "NumberFormatException"));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> metadata(DataQueryResponse response) {
        return (Map<String, Object>) invoke(response, "metadata");
    }

    private int count(DataQueryResponse response) {
        return ((Number) invoke(response, "count")).intValue();
    }

    private Object invoke(DataQueryResponse response, String methodName) {
        try {
            Method method = response.getClass().getMethod(methodName);
            return method.invoke(response);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Missing DataQueryResponse method: " + methodName, ex);
        }
    }
}
