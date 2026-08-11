package com.brand.agentpoc.agent.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.agent.application.AgentScopeVerifier;
import com.brand.agentpoc.agent.application.AgentToolResult;
import com.brand.agentpoc.agent.application.ControlledAgentToolService;
import com.brand.agentpoc.agent.domain.AgentRequestScope;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

class ControlledAgentToolCallbacksTest {

    private ControlledAgentToolService toolService;
    private AgentScopeVerifier scopeVerifier;
    private ControlledAgentToolCallbacks toolCallbacks;
    private AgentRequestScope scope;

    @BeforeEach
    void setUp() {
        toolService = mock(ControlledAgentToolService.class);
        scopeVerifier = mock(AgentScopeVerifier.class);
        toolCallbacks = new ControlledAgentToolCallbacks(
                new ControlledAgentToolAdapter(toolService),
                scopeVerifier
        );
        scope = AgentRequestScope.authenticated(
                "session-1",
                "subject-1",
                EnumSet.allOf(PermissionKey.class)
        );
    }

    @Test
    void publishesOnlyTheSixBusinessLevelCallbacks() {
        ControlledAgentToolSession session = toolCallbacks.openSession(scope, "trace-1");

        assertThat(session.callbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder(
                        "getDashboardSummary",
                        "queryMetric",
                        "queryDetails",
                        "runScenarioAnalysis",
                        "retrieveKnowledge",
                        "generateReportDraft"
                )
                .doesNotContain(
                        "searchDealers",
                        "queryOpportunities",
                        "queryCampaigns",
                        "queryTasks",
                        "queryTargets",
                        "queryLeads"
                );
    }

    @Test
    void executesWithinScopeAndStopsAfterTheFourthToolCall() {
        when(scopeVerifier.isAllowed(scope)).thenReturn(true);
        when(toolService.getDashboardSummary())
                .thenReturn(new AgentToolResult("dashboardSummary", Map.of("status", "ok")));
        ToolCallback dashboardCallback = callback("getDashboardSummary");

        for (int index = 0; index < 4; index++) {
            assertThat(dashboardCallback.call("{}"))
                    .contains("\"status\":\"ok\"");
        }

        assertThatThrownBy(() -> dashboardCallback.call("{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Agent tool call budget exceeded.");
        verify(toolService, org.mockito.Mockito.times(4)).getDashboardSummary();
    }

    @Test
    void rejectsToolExecutionWhenSessionOwnershipScopeIsMissing() {
        when(scopeVerifier.isAllowed(scope)).thenReturn(false);
        ToolCallback dashboardCallback = callback("getDashboardSummary");

        assertThatThrownBy(() -> dashboardCallback.call("{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Agent request scope is not authorized.");
        verify(toolService, never()).getDashboardSummary();
    }

    @Test
    void guardsKnowledgeRetrievalWithTheSharedRequestScopeAndBudget() {
        when(scopeVerifier.isAllowed(scope)).thenReturn(true);
        when(toolService.retrieveKnowledge("目标达成率口径", 2))
                .thenReturn(com.brand.agentpoc.knowledge.domain.KnowledgeSearchResult.from(
                        "目标达成率口径",
                        java.util.List.of()
                ));

        String result = callback("retrieveKnowledge").call("{\"query\":\"目标达成率口径\",\"topK\":2}");

        assertThat(result).contains("\"noMatch\":true");
        verify(toolService).retrieveKnowledge("目标达成率口径", 2);
    }

    @Test
    void propagatesTheResolvedOrganizationScopeIntoAgentToolExecution() {
        OrganizationDataScope dataScope = new OrganizationDataScope(
                Set.of(2L, 3L, 4L), Set.of(2L), Set.of("NORTH-1"), false, false);
        AgentRequestScope scopedRequest = AgentRequestScope.authenticated(
                "session-1",
                "subject-1",
                EnumSet.allOf(PermissionKey.class),
                dataScope
        );
        when(scopeVerifier.isAllowed(scopedRequest)).thenReturn(true);
        when(toolService.getDashboardSummary(dataScope))
                .thenReturn(new AgentToolResult("dashboardSummary", Map.of("dealerCount", 1)));

        ToolCallback callback = toolCallbacks.openSession(scopedRequest, "trace-scope").callbacks().stream()
                .filter(candidate -> "getDashboardSummary".equals(candidate.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();

        assertThat(callback.call("{}")).contains("\"dealerCount\":1");
        verify(toolService).getDashboardSummary(dataScope);
        verify(toolService, never()).getDashboardSummary();
    }

    @Test
    void propagatesTheResolvedOrganizationScopeIntoKnowledgeRetrieval() {
        OrganizationDataScope dataScope = new OrganizationDataScope(
                Set.of(2L, 3L, 4L), Set.of(2L), Set.of("NORTH-1"), false, false);
        AgentRequestScope scopedRequest = AgentRequestScope.authenticated(
                "session-1",
                "subject-1",
                EnumSet.allOf(PermissionKey.class),
                dataScope
        );
        when(scopeVerifier.isAllowed(scopedRequest)).thenReturn(true);
        when(toolService.retrieveKnowledge("目标达成率口径", 2, dataScope))
                .thenReturn(com.brand.agentpoc.knowledge.domain.KnowledgeSearchResult.from(
                        "目标达成率口径",
                        java.util.List.of()
                ));

        ToolCallback callback = toolCallbacks.openSession(scopedRequest, "trace-scope").callbacks().stream()
                .filter(candidate -> "retrieveKnowledge".equals(candidate.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();

        assertThat(callback.call("{\"query\":\"目标达成率口径\",\"topK\":2}"))
                .contains("\"noMatch\":true");
        verify(toolService).retrieveKnowledge("目标达成率口径", 2, dataScope);
        verify(toolService, never()).retrieveKnowledge("目标达成率口径", 2);
    }

    private ToolCallback callback(String name) {
        return toolCallbacks.openSession(scope, "trace-1").callbacks().stream()
                .filter(callback -> name.equals(callback.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();
    }
}
