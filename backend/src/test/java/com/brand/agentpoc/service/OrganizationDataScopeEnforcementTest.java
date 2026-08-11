package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.dto.response.DataQueryResponse;
import com.brand.agentpoc.entity.Dealer;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.repository.CampaignRepository;
import com.brand.agentpoc.repository.DealerRepository;
import com.brand.agentpoc.repository.LeadRepository;
import com.brand.agentpoc.repository.OpportunityRepository;
import com.brand.agentpoc.repository.TargetRepository;
import com.brand.agentpoc.repository.TaskRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class OrganizationDataScopeEnforcementTest {

    private DealerRepository dealerRepository;
    private DataQueryService dataQueryService;

    @BeforeEach
    void setUp() {
        dealerRepository = mock(DealerRepository.class);
        dataQueryService = new DataQueryService(
                dealerRepository,
                mock(OpportunityRepository.class),
                mock(CampaignRepository.class),
                mock(TaskRepository.class),
                mock(TargetRepository.class),
                mock(LeadRepository.class)
        );
    }

    @Test
    void serverScopeFiltersAllRowsAndForgedDealerParameterCannotExpandIt() {
        Dealer allowed = new Dealer("ALLOW-1", "Allowed", "North", "Group A");
        Dealer denied = new Dealer("DENY-1", "Denied", "South", "Group B");
        OrganizationDataScope scope = new OrganizationDataScope(
                Set.of(10L),
                Set.of(10L),
                Set.of("ALLOW-1"),
                false,
                false
        );
        when(dealerRepository.findAll()).thenReturn(List.of(allowed, denied));
        when(dealerRepository.findByDealerCodeIgnoreCase("DENY-1")).thenReturn(List.of(denied));

        DataQueryResponse visible = dataQueryService.query("dealers", Map.of(), scope);
        DataQueryResponse forged = dataQueryService.query(
                "dealers",
                Map.of("dealerCode", "DENY-1"),
                scope
        );

        assertThat(visible.items()).extracting(item -> item.get("dealerCode")).containsExactly("ALLOW-1");
        assertThat(forged.items()).isEmpty();
    }

    @Test
    void emptyOrganizationScopeIsDeniedBeforeRepositoryAccess() {
        assertThatThrownBy(() -> dataQueryService.query("dealers", Map.of(), OrganizationDataScope.empty()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("organization data scope");
    }
}
