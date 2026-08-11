package com.brand.agentpoc.agent.application;

import com.brand.agentpoc.agent.domain.AgentDataKind;
import com.brand.agentpoc.agent.domain.AgentExecutionPolicy;
import com.brand.agentpoc.knowledge.application.KnowledgeService;
import com.brand.agentpoc.knowledge.domain.KnowledgeSearchResult;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.application.ReportGenerationRequest;
import com.brand.agentpoc.reporting.application.ReportService;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import com.brand.agentpoc.service.AnalyticsApiService;
import com.brand.agentpoc.service.AnalyticsPlan;
import com.brand.agentpoc.service.DashboardService;
import com.brand.agentpoc.service.RuleBasedAnalyticsService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ControlledAgentToolService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_SCENARIO_QUESTION_LENGTH = 2_000;
    private static final Set<String> TARGET_FILTERS = Set.of(
            "targetYear", "targetMonth", "productModel", "dealerCode",
            "city", "dealerName", "dealerGroupName"
    );
    private static final Map<AgentDataKind, Set<String>> METRIC_FILTERS = Map.of(
            AgentDataKind.TARGET, TARGET_FILTERS,
            AgentDataKind.OPPORTUNITY, Set.of("dealerCode", "startDate", "endDate"),
            AgentDataKind.LEAD, Set.of("leadSource", "dealerCode"),
            AgentDataKind.TASK, Set.of("dealerCode"),
            AgentDataKind.CAMPAIGN, Set.of("campaignType")
    );
    private static final Map<AgentDataKind, Set<String>> DETAIL_FILTERS = Map.of(
            AgentDataKind.TARGET, TARGET_FILTERS,
            AgentDataKind.OPPORTUNITY,
            Set.of("dealerCode", "startDate", "endDate", "keyword", "stageName"),
            AgentDataKind.LEAD, Set.of("leadSource", "dealerCode"),
            AgentDataKind.TASK, Set.of("dealerCode", "keyword"),
            AgentDataKind.CAMPAIGN, Set.of("campaignType", "keyword")
    );
    private static final Map<AgentDataKind, Set<String>> DETAIL_SORT_FIELDS = Map.of(
            AgentDataKind.TARGET, Set.of("dealerCode", "targetYear", "targetMonth", "asKTarget"),
            AgentDataKind.OPPORTUNITY, Set.of("createdDate", "stageName", "dealerCode"),
            AgentDataKind.LEAD, Set.of("createdDate", "leadSource", "dealerCode"),
            AgentDataKind.TASK, Set.of("createdDate", "status", "dealerCode"),
            AgentDataKind.CAMPAIGN, Set.of("createdDate", "campaignType", "dealerCode")
    );

    private final DashboardService dashboardService;
    private final AnalyticsApiService analyticsApiService;
    private final RuleBasedAnalyticsService analyticsService;
    private final KnowledgeService knowledgeService;
    private final ReportService reportService;
    private final AgentExecutionPolicy executionPolicy;

    @Autowired
    public ControlledAgentToolService(
            DashboardService dashboardService,
            AnalyticsApiService analyticsApiService,
            RuleBasedAnalyticsService analyticsService,
            KnowledgeService knowledgeService,
            ReportService reportService
    ) {
        this(
                dashboardService,
                analyticsApiService,
                analyticsService,
                knowledgeService,
                reportService,
                AgentExecutionPolicy.defaultPolicy()
        );
    }

    ControlledAgentToolService(
            DashboardService dashboardService,
            AnalyticsApiService analyticsApiService,
            RuleBasedAnalyticsService analyticsService,
            KnowledgeService knowledgeService,
            ReportService reportService,
            AgentExecutionPolicy executionPolicy
    ) {
        this.dashboardService = dashboardService;
        this.analyticsApiService = analyticsApiService;
        this.analyticsService = analyticsService;
        this.knowledgeService = knowledgeService;
        this.reportService = reportService;
        this.executionPolicy = executionPolicy;
    }

    ControlledAgentToolService(
            DashboardService dashboardService,
            AnalyticsApiService analyticsApiService,
            RuleBasedAnalyticsService analyticsService,
            KnowledgeService knowledgeService
    ) {
        this(
                dashboardService,
                analyticsApiService,
                analyticsService,
                knowledgeService,
                null,
                AgentExecutionPolicy.defaultPolicy()
        );
    }

    public AgentToolResult getDashboardSummary() {
        return getDashboardSummary(OrganizationDataScope.unrestrictedScope());
    }

    public AgentToolResult getDashboardSummary(OrganizationDataScope dataScope) {
        Object summary = dataScope.unrestricted()
                ? dashboardService.getSummary()
                : dashboardService.getSummary(dataScope);
        return new AgentToolResult("dashboardSummary", summary);
    }

    public AgentToolResult queryMetric(String metric, Map<String, String> filters) {
        return queryMetric(metric, filters, OrganizationDataScope.unrestrictedScope());
    }

    public AgentToolResult queryMetric(
            String metric,
            Map<String, String> filters,
            OrganizationDataScope dataScope
    ) {
        AgentDataKind dataKind = AgentDataKind.parse(metric);
        Map<String, String> safeFilters = validateFilters(filters, METRIC_FILTERS.get(dataKind));

        java.util.function.Supplier<Object> operation = () -> switch (dataKind) {
            case TARGET -> analyticsApiService.getTargetMetrics(
                    optionalInteger(safeFilters, "targetYear"),
                    optionalInteger(safeFilters, "targetMonth"),
                    safeFilters.get("productModel"),
                    safeFilters.get("dealerCode"),
                    safeFilters.get("city"),
                    safeFilters.get("dealerName"),
                    safeFilters.get("dealerGroupName")
            );
            case OPPORTUNITY -> analyticsApiService.getOpportunityMetrics(
                    safeFilters.get("dealerCode"),
                    safeFilters.get("startDate"),
                    safeFilters.get("endDate")
            );
            case LEAD -> analyticsApiService.getLeadMetrics(
                    safeFilters.get("leadSource"),
                    safeFilters.get("dealerCode")
            );
            case TASK -> analyticsApiService.getTaskMetrics(safeFilters.get("dealerCode"));
            case CAMPAIGN -> analyticsApiService.getCampaignMetrics(safeFilters.get("campaignType"));
        };
        Object result = dataScope.unrestricted()
                ? operation.get()
                : analyticsApiService.withOrganizationScope(dataScope, operation);
        return new AgentToolResult(dataKind.name().toLowerCase(java.util.Locale.ROOT) + "Metric", result);
    }

    public AgentToolResult queryDetails(
            String dataset,
            Map<String, String> filters,
            Integer page,
            Integer pageSize,
            String sortBy,
            String sortOrder
    ) {
        return queryDetails(
                dataset,
                filters,
                page,
                pageSize,
                sortBy,
                sortOrder,
                OrganizationDataScope.unrestrictedScope()
        );
    }

    public AgentToolResult queryDetails(
            String dataset,
            Map<String, String> filters,
            Integer page,
            Integer pageSize,
            String sortBy,
            String sortOrder,
            OrganizationDataScope dataScope
    ) {
        AgentDataKind dataKind = AgentDataKind.parse(dataset);
        Map<String, String> safeFilters = validateFilters(filters, DETAIL_FILTERS.get(dataKind));
        int safePage = page == null ? DEFAULT_PAGE : page;
        int safePageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        executionPolicy.validatePage(safePage, safePageSize);
        String safeSortBy = validateSortBy(dataKind, sortBy);
        String safeSortOrder = validateSortOrder(sortOrder);

        java.util.function.Supplier<Object> operation = () -> switch (dataKind) {
            case TARGET -> analyticsApiService.getTargetDetails(
                    optionalInteger(safeFilters, "targetYear"),
                    optionalInteger(safeFilters, "targetMonth"),
                    safeFilters.get("productModel"),
                    safeFilters.get("dealerCode"),
                    safeFilters.get("city"),
                    safeFilters.get("dealerName"),
                    safeFilters.get("dealerGroupName"),
                    safePage,
                    safePageSize,
                    safeSortBy,
                    safeSortOrder
            );
            case OPPORTUNITY -> analyticsApiService.getOpportunityDetails(
                    safeFilters.get("dealerCode"),
                    safeFilters.get("startDate"),
                    safeFilters.get("endDate"),
                    safeFilters.get("keyword"),
                    safeFilters.get("stageName"),
                    safePage,
                    safePageSize,
                    safeSortBy,
                    safeSortOrder
            );
            case LEAD -> analyticsApiService.getLeadDetails(
                    safeFilters.get("leadSource"),
                    safeFilters.get("dealerCode"),
                    safePage,
                    safePageSize,
                    safeSortBy,
                    safeSortOrder
            );
            case TASK -> analyticsApiService.getTaskDetails(
                    safeFilters.get("dealerCode"),
                    safeFilters.get("keyword"),
                    safePage,
                    safePageSize,
                    safeSortBy,
                    safeSortOrder
            );
            case CAMPAIGN -> analyticsApiService.getCampaignDetails(
                    safeFilters.get("campaignType"),
                    safeFilters.get("keyword"),
                    safePage,
                    safePageSize,
                    safeSortBy,
                    safeSortOrder
            );
        };
        Object result = dataScope.unrestricted()
                ? operation.get()
                : analyticsApiService.withOrganizationScope(dataScope, operation);
        return new AgentToolResult(dataKind.name().toLowerCase(java.util.Locale.ROOT) + "Details", result);
    }

    public AgentScenarioAnalysis runScenarioAnalysis(String question, String language) {
        return runScenarioAnalysis(question, language, OrganizationDataScope.unrestrictedScope());
    }

    public AgentScenarioAnalysis runScenarioAnalysis(
            String question,
            String language,
            OrganizationDataScope dataScope
    ) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question is required.");
        }
        if (question.length() > MAX_SCENARIO_QUESTION_LENGTH) {
            throw new IllegalArgumentException("question exceeds the allowed length.");
        }

        String safeLanguage = validateLanguage(language);
        AnalyticsPlan plan = dataScope.unrestricted()
                ? analyticsService.plan(question.trim(), safeLanguage)
                : analyticsService.plan(question.trim(), safeLanguage, dataScope);
        return new AgentScenarioAnalysis(
                plan.scenario().name(),
                plan.scopeSummary(),
                plan.groundedReference(),
                plan.fallbackReply(),
                plan.metadata()
        );
    }

    public KnowledgeSearchResult retrieveKnowledge(String query, Integer topK) {
        return retrieveKnowledge(query, topK, OrganizationDataScope.unrestrictedScope());
    }

    public KnowledgeSearchResult retrieveKnowledge(
            String query,
            Integer topK,
            OrganizationDataScope dataScope
    ) {
        dataScope.requireDataAccess();
        return knowledgeService.retrieve(query, topK);
    }

    public ReportDraft generateReportDraft(String reportType, String language, String topic) {
        return generateReportDraft(reportType, language, topic, OrganizationDataScope.unrestrictedScope());
    }

    public ReportDraft generateReportDraft(
            String reportType,
            String language,
            String topic,
            OrganizationDataScope dataScope
    ) {
        if (reportService == null) {
            throw new IllegalStateException("Report generation service is unavailable.");
        }
        ReportGenerationRequest request = new ReportGenerationRequest(
                reportType,
                language,
                "GLOBAL",
                "",
                topic
        );
        return dataScope.unrestricted()
                ? reportService.generate(request)
                : reportService.generate(request, dataScope);
    }

    private Map<String, String> validateFilters(Map<String, String> filters, Set<String> allowedKeys) {
        if (filters == null || filters.isEmpty()) {
            return Map.of();
        }

        Map<String, String> safeFilters = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            if (!allowedKeys.contains(entry.getKey())) {
                throw new IllegalArgumentException("Unsupported filter for this metric or dataset.");
            }
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                safeFilters.put(entry.getKey(), entry.getValue().trim());
            }
        }
        return Map.copyOf(safeFilters);
    }

    private Integer optionalInteger(Map<String, String> filters, String key) {
        String value = filters.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer.", exception);
        }
    }

    private String validateSortBy(AgentDataKind dataKind, String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return null;
        }
        String normalized = sortBy.trim();
        if (!DETAIL_SORT_FIELDS.get(dataKind).contains(normalized)) {
            throw new IllegalArgumentException("Unsupported sort field for this dataset.");
        }
        return normalized;
    }

    private String validateSortOrder(String sortOrder) {
        if (sortOrder == null || sortOrder.isBlank()) {
            return "desc";
        }
        String normalized = sortOrder.trim().toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("asc", "desc").contains(normalized)) {
            throw new IllegalArgumentException("sortOrder must be asc or desc.");
        }
        return normalized;
    }

    private String validateLanguage(String language) {
        if ("zh".equalsIgnoreCase(language)) {
            return "zh";
        }
        if ("en".equalsIgnoreCase(language)) {
            return "en";
        }
        throw new IllegalArgumentException("language must be zh or en.");
    }
}
