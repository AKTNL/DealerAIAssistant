package com.brand.agentpoc.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.dto.response.DataQueryResponse;
import com.brand.agentpoc.service.DataQueryService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalyticsToolValidationTest {

    private DataQueryService dataQueryService;

    @BeforeEach
    void setUp() {
        dataQueryService = mock(DataQueryService.class);
        when(dataQueryService.query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new DataQueryResponse("dataset", Map.of(), 0, List.of(), Map.of()));
    }

    @Test
    void queryOpportunitiesRequiresRaw() {
        OpportunityTools tools = new OpportunityTools(dataQueryService);

        assertThatThrownBy(() -> tools.queryOpportunities(null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("raw");

        verifyNoInteractions(dataQueryService);
    }

    @Test
    void queryCampaignsRequiresRaw() {
        CampaignTools tools = new CampaignTools(dataQueryService);

        assertThatThrownBy(() -> tools.queryCampaigns(null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("raw");

        verifyNoInteractions(dataQueryService);
    }

    @Test
    void queryTasksRequiresRaw() {
        TaskTools tools = new TaskTools(dataQueryService);

        assertThatThrownBy(() -> tools.queryTasks(null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("raw");

        verifyNoInteractions(dataQueryService);
    }

    @Test
    void queryLeadsRequiresLeadSourceAndRaw() {
        LeadTools tools = new LeadTools(dataQueryService);

        assertThatThrownBy(() -> tools.queryLeads(null, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leadSource");

        assertThatThrownBy(() -> tools.queryLeads(null, null, null, "Website", null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("raw");

        verifyNoInteractions(dataQueryService);
    }

    @Test
    void queryTargetsRequiresTargetYearAndTargetMonth() {
        TargetTools tools = new TargetTools(dataQueryService);

        assertThatThrownBy(() -> tools.queryTargets(null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetYear");

        assertThatThrownBy(() -> tools.queryTargets(null, null, null, null, 2026, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetMonth");

        verifyNoInteractions(dataQueryService);
    }

    @Test
    void toolsDelegateWhenRequiredParametersArePresent() {
        OpportunityTools opportunityTools = new OpportunityTools(dataQueryService);
        CampaignTools campaignTools = new CampaignTools(dataQueryService);
        TaskTools taskTools = new TaskTools(dataQueryService);
        LeadTools leadTools = new LeadTools(dataQueryService);
        TargetTools targetTools = new TargetTools(dataQueryService);

        opportunityTools.queryOpportunities(null, null, null, null, null, null, null, null, true);
        campaignTools.queryCampaigns(null, null, null, null, null, null, null, false);
        taskTools.queryTasks(null, null, null, null, null, null, null, true);
        leadTools.queryLeads(null, null, null, "Website", null, null, null, null, null, true);
        targetTools.queryTargets(null, null, null, null, 2026, 5);

        verify(dataQueryService).query(org.mockito.ArgumentMatchers.eq("opportunities"), org.mockito.ArgumentMatchers.anyMap());
        verify(dataQueryService).query(org.mockito.ArgumentMatchers.eq("campaigns"), org.mockito.ArgumentMatchers.anyMap());
        verify(dataQueryService).query(org.mockito.ArgumentMatchers.eq("tasks"), org.mockito.ArgumentMatchers.anyMap());
        verify(dataQueryService).query(org.mockito.ArgumentMatchers.eq("leads"), org.mockito.ArgumentMatchers.anyMap());
        verify(dataQueryService).query(org.mockito.ArgumentMatchers.eq("targets"), org.mockito.ArgumentMatchers.anyMap());
    }
}
