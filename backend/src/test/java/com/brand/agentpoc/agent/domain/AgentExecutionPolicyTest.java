package com.brand.agentpoc.agent.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brand.agentpoc.auth.domain.PermissionKey;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentExecutionPolicyTest {

    @Test
    void defaultPolicyExposesOnlyTheSixControlledTools() {
        AgentExecutionPolicy policy = AgentExecutionPolicy.defaultPolicy();

        assertThat(policy.allowedTools()).containsExactlyInAnyOrder(AgentToolName.values());
        assertThat(AgentToolName.wireNames()).containsExactlyInAnyOrder(
                "getDashboardSummary",
                "queryMetric",
                "queryDetails",
                "runScenarioAnalysis",
                "retrieveKnowledge",
                "generateReportDraft"
        );
        assertThat(policy.maxToolCalls()).isEqualTo(4);
        assertThat(policy.maxPageSize()).isEqualTo(50);
    }

    @Test
    void rejectsUnknownToolsAndOutOfRangePages() {
        AgentExecutionPolicy policy = AgentExecutionPolicy.defaultPolicy();

        assertThatThrownBy(() -> policy.requireAllowed("queryOpportunities"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Agent tool is not allowed.");
        assertThatThrownBy(() -> policy.validatePage(1, 51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pageSize is outside the allowed range.");
        assertThatThrownBy(() -> policy.validatePage(0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page must be at least 1.");
    }

    @Test
    void executionContextStopsTheFifthToolCallAndKeepsSafeTraceReasons() {
        AgentExecutionContext context = new AgentExecutionContext(
                AgentRequestScope.authenticated(
                        "session-1",
                        "subject-1",
                        EnumSet.allOf(PermissionKey.class)
                ),
                AgentExecutionPolicy.defaultPolicy(),
                "trace-1"
        );

        List.of(1, 2, 3, 4).forEach(ignored -> context.acquire(AgentToolName.QUERY_METRIC));
        context.record(AgentToolName.QUERY_METRIC, AgentExecutionContext.TraceStatus.SUCCESS, "completed");
        context.record(AgentToolName.QUERY_DETAILS, AgentExecutionContext.TraceStatus.REJECTED, "unsafe value");

        assertThatThrownBy(() -> context.acquire(AgentToolName.QUERY_METRIC))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Agent tool call budget exceeded.");
        assertThat(context.traceEntries())
                .extracting(AgentExecutionContext.TraceEntry::reason)
                .containsExactly("completed", "unspecified");
    }

    @Test
    void executionContextPreservesSafeRequestCorrelationAndReplacesInvalidValues() {
        AgentExecutionContext correlated = new AgentExecutionContext(null, null, "ops.check:001");
        AgentExecutionContext replaced = new AgentExecutionContext(null, null, "unsafe value");

        assertThat(correlated.traceId()).isEqualTo("ops.check:001");
        assertThat(replaced.traceId()).matches("[0-9a-f]{32}");
    }
}
