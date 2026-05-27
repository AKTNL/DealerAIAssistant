package com.brand.agentpoc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.brand.agentpoc.ai.CampaignTools;
import com.brand.agentpoc.ai.CurrentDateTools;
import com.brand.agentpoc.ai.DealerTools;
import com.brand.agentpoc.ai.LeadTools;
import com.brand.agentpoc.ai.OpportunityTools;
import com.brand.agentpoc.ai.TargetTools;
import com.brand.agentpoc.ai.TaskTools;
import com.brand.agentpoc.service.DataQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;

class AiConfigTest {

    @Test
    void createsPromptFactoryAndSystemClockBeans() {
        AiConfig config = new AiConfig();

        assertThat(config.promptFactory()).isNotNull();
        assertThat(config.systemClock()).isNotNull();
    }

    @Test
    void registersAllAiToolCallbacks() {
        AiConfig config = new AiConfig();
        DataQueryService dataQueryService = mock(DataQueryService.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-23T00:00:00Z"), ZoneOffset.UTC);

        ToolCallbackProvider provider = config.aiToolCallbackProvider(
                new CurrentDateTools(fixedClock),
                new DealerTools(dataQueryService),
                new OpportunityTools(dataQueryService),
                new CampaignTools(dataQueryService),
                new TaskTools(dataQueryService),
                new TargetTools(dataQueryService),
                new LeadTools(dataQueryService)
        );

        assertThat(Arrays.stream(provider.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name()))
                .containsExactlyInAnyOrder(
                        "getCurrentDate",
                        "searchDealers",
                        "queryOpportunities",
                        "queryCampaigns",
                        "queryTasks",
                        "queryTargets",
                        "queryLeads"
                );
    }
}
