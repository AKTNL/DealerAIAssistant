package com.brand.agentpoc.agent.infrastructure;

import com.brand.agentpoc.agent.application.AgentScenarioAnalysis;
import com.brand.agentpoc.agent.application.AgentToolResult;
import com.brand.agentpoc.agent.application.ControlledAgentToolService;
import com.brand.agentpoc.knowledge.domain.KnowledgeSearchResult;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ControlledAgentToolAdapter {

    private final ControlledAgentToolService toolService;
    private final ThreadLocal<OrganizationDataScope> organizationScope = new ThreadLocal<>();

    public ControlledAgentToolAdapter(ControlledAgentToolService toolService) {
        this.toolService = toolService;
    }

    @Tool(
            name = "getDashboardSummary",
            description = "Get the current active-batch dealer operations dashboard summary. Read only."
    )
    public AgentToolResult getDashboardSummary() {
        OrganizationDataScope dataScope = requireOrganizationScope();
        return dataScope.unrestricted()
                ? toolService.getDashboardSummary()
                : toolService.getDashboardSummary(dataScope);
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
        OrganizationDataScope dataScope = requireOrganizationScope();
        return dataScope.unrestricted()
                ? toolService.queryMetric(metric, filters)
                : toolService.queryMetric(metric, filters, dataScope);
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
        OrganizationDataScope dataScope = requireOrganizationScope();
        return dataScope.unrestricted()
                ? toolService.queryDetails(dataset, filters, page, pageSize, sortBy, sortOrder)
                : toolService.queryDetails(dataset, filters, page, pageSize, sortBy, sortOrder, dataScope);
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
        OrganizationDataScope dataScope = requireOrganizationScope();
        return dataScope.unrestricted()
                ? toolService.runScenarioAnalysis(question, language)
                : toolService.runScenarioAnalysis(question, language, dataScope);
    }

    @Tool(
            name = "retrieveKnowledge",
            description = "Retrieve citable dealer business knowledge such as KPI definitions, SOPs, policies, and product or campaign rules. Read only. Never use it as the source of current KPI values."
    )
    public KnowledgeSearchResult retrieveKnowledge(
            @ToolParam(description = "A specific dealer business knowledge question.", required = true)
            String query,
            @ToolParam(description = "Optional result limit from 1 to 8. Defaults to 4.", required = false)
            Integer topK
    ) {
        OrganizationDataScope dataScope = requireOrganizationScope();
        return dataScope.unrestricted()
                ? toolService.retrieveKnowledge(query, topK)
                : toolService.retrieveKnowledge(query, topK, dataScope);
    }

    @Tool(
            name = "generateReportDraft",
            description = "Generate a deterministic Markdown dealer operations report draft from the current active-batch metrics. Read only; no PDF/Word export or data mutation."
    )
    public ReportDraft generateReportDraft(
            @ToolParam(description = "Report type: daily, weekly, monthly, or topic.", required = true)
            String reportType,
            @ToolParam(description = "Response language: zh or en.", required = true)
            String language,
            @ToolParam(description = "Required only for a topic report; describe the business topic in at most 500 characters.", required = false)
            String topic
    ) {
        OrganizationDataScope dataScope = requireOrganizationScope();
        return dataScope.unrestricted()
                ? toolService.generateReportDraft(reportType, language, topic)
                : toolService.generateReportDraft(reportType, language, topic, dataScope);
    }

    <T> T withOrganizationScope(OrganizationDataScope dataScope, Supplier<T> operation) {
        OrganizationDataScope previous = organizationScope.get();
        organizationScope.set(dataScope == null ? OrganizationDataScope.empty() : dataScope);
        try {
            return operation.get();
        } finally {
            if (previous == null) {
                organizationScope.remove();
            } else {
                organizationScope.set(previous);
            }
        }
    }

    private OrganizationDataScope requireOrganizationScope() {
        OrganizationDataScope current = organizationScope.get();
        if (current == null) {
            throw new IllegalStateException("Agent organization scope is unavailable.");
        }
        current.requireDataAccess();
        return current;
    }
}
