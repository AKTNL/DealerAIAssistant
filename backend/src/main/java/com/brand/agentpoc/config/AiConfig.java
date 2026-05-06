package com.brand.agentpoc.config;

import com.brand.agentpoc.ai.CampaignTools;
import com.brand.agentpoc.ai.DealerTools;
import com.brand.agentpoc.ai.LeadTools;
import com.brand.agentpoc.ai.OpportunityTools;
import com.brand.agentpoc.ai.PromptFactory;
import com.brand.agentpoc.ai.TargetTools;
import com.brand.agentpoc.ai.TaskTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public PromptFactory promptFactory() {
        return new PromptFactory();
    }

    @Bean
    public ToolCallbackProvider aiToolCallbackProvider(
            DealerTools dealerTools,
            OpportunityTools opportunityTools,
            CampaignTools campaignTools,
            TaskTools taskTools,
            TargetTools targetTools,
            LeadTools leadTools
    ) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(
                        dealerTools,
                        opportunityTools,
                        campaignTools,
                        taskTools,
                        targetTools,
                        leadTools
                )
                .build();
    }
}
