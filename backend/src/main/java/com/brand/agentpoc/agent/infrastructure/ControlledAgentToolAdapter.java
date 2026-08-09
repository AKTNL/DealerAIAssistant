package com.brand.agentpoc.agent.infrastructure;

import com.brand.agentpoc.agent.application.AgentScenarioAnalysis;
import com.brand.agentpoc.agent.application.AgentToolResult;
import com.brand.agentpoc.agent.application.ControlledAgentToolService;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ControlledAgentToolAdapter {

    private final ControlledAgentToolService toolService;

    public ControlledAgentToolAdapter(ControlledAgentToolService toolService) {
        this.toolService = toolService;
    }

    @Tool(
            name = "getDashboardSummary",
            description = "Get the current active-batch dealer operations dashboard summary. Read only."
    )
    public AgentToolResult getDashboardSummary() {
        return toolService.getDashboardSummary();
    }

    @Tool(
            name = "queryMetric",
            description = "Query one active-batch metric or ranking for target, opportunity, lead, task, or campaign data. Read only."
    )
    public AgentToolResult queryMetric(
            @ToolParam(
                    description = "Required metric: target, opportunity, lead, task, or campaign.",
                    required = true
            )
            String metric,
            @ToolParam(
                    description = "Optional metric filters. Use only documented business fields; never pass SQL or a batch id.",
                    required = false
            )
            Map<String, String> filters
    ) {
        return toolService.queryMetric(metric, filters);
    }

    @Tool(
            name = "queryDetails",
            description = "Query one bounded page of active-batch business details. Page size cannot exceed 50. Read only."
    )
    public AgentToolResult queryDetails(
            @ToolParam(
                    description = "Required dataset: target, opportunity, lead, task, or campaign.",
                    required = true
            )
            String dataset,
            @ToolParam(
                    description = "Optional dataset-specific filters. Never pass SQL or a batch id.",
                    required = false
            )
            Map<String, String> filters,
            @ToolParam(description = "One-based page number. Defaults to 1.", required = false)
            Integer page,
            @ToolParam(description = "Page size from 1 to 50. Defaults to 20.", required = false)
            Integer pageSize,
            @ToolParam(description = "Optional dataset-specific sort field.", required = false)
            String sortBy,
            @ToolParam(description = "Optional sort order: asc or desc.", required = false)
            String sortOrder
    ) {
        return toolService.queryDetails(dataset, filters, page, pageSize, sortBy, sortOrder);
    }

    @Tool(
            name = "runScenarioAnalysis",
            description = "Run the deterministic dealer analytics scenario engine and return grounded facts plus its fallback report. Read only."
    )
    public AgentScenarioAnalysis runScenarioAnalysis(
            @ToolParam(description = "The dealer operations analysis question.", required = true)
            String question,
            @ToolParam(description = "Response language: zh or en.", required = true)
            String language
    ) {
        return toolService.runScenarioAnalysis(question, language);
    }
}
