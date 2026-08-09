package com.brand.agentpoc.agent.infrastructure;

import com.brand.agentpoc.agent.application.AgentScopeVerifier;
import com.brand.agentpoc.agent.domain.AgentExecutionContext;
import com.brand.agentpoc.agent.domain.AgentExecutionContext.TraceStatus;
import com.brand.agentpoc.agent.domain.AgentExecutionPolicy;
import com.brand.agentpoc.agent.domain.AgentRequestScope;
import com.brand.agentpoc.agent.domain.AgentToolName;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

@Component
public class ControlledAgentToolCallbacks {

    private static final Logger log = LoggerFactory.getLogger(ControlledAgentToolCallbacks.class);

    private final Map<AgentToolName, ToolCallback> callbacksByName;
    private final AgentScopeVerifier scopeVerifier;

    public ControlledAgentToolCallbacks(
            ControlledAgentToolAdapter toolAdapter,
            AgentScopeVerifier scopeVerifier
    ) {
        this.scopeVerifier = scopeVerifier;
        this.callbacksByName = indexCallbacks(toolAdapter);
    }

    public ControlledAgentToolSession openSession(AgentRequestScope scope, String traceId) {
        AgentExecutionContext context = new AgentExecutionContext(
                scope,
                AgentExecutionPolicy.defaultPolicy(),
                traceId
        );
        List<ToolCallback> guardedCallbacks = context.policy().allowedTools().stream()
                .map(callbacksByName::get)
                .map(callback -> new GuardedToolCallback(callback, context, scopeVerifier))
                .map(ToolCallback.class::cast)
                .toList();
        return new ControlledAgentToolSession(context.traceId(), guardedCallbacks);
    }

    private Map<AgentToolName, ToolCallback> indexCallbacks(ControlledAgentToolAdapter toolAdapter) {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(toolAdapter)
                .build()
                .getToolCallbacks();
        Map<AgentToolName, ToolCallback> indexed = new LinkedHashMap<>();
        Arrays.stream(callbacks).forEach(callback -> {
            AgentToolName name = AgentToolName.fromWireName(callback.getToolDefinition().name());
            if (indexed.put(name, callback) != null) {
                throw new IllegalStateException("Duplicate controlled agent tool callback.");
            }
        });

        if (!indexed.keySet().equals(Set.of(AgentToolName.values()))) {
            throw new IllegalStateException("Controlled agent tool callback set is incomplete.");
        }
        return Map.copyOf(indexed);
    }

    private static final class GuardedToolCallback implements ToolCallback {

        private final ToolCallback delegate;
        private final AgentExecutionContext executionContext;
        private final AgentScopeVerifier scopeVerifier;
        private final AgentToolName toolName;

        private GuardedToolCallback(
                ToolCallback delegate,
                AgentExecutionContext executionContext,
                AgentScopeVerifier scopeVerifier
        ) {
            this.delegate = delegate;
            this.executionContext = executionContext;
            this.scopeVerifier = scopeVerifier;
            this.toolName = executionContext.policy().requireAllowed(delegate.getToolDefinition().name());
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return execute(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return execute(toolInput, toolContext);
        }

        private String execute(String toolInput, ToolContext toolContext) {
            if (!verifyScope()) {
                record(TraceStatus.REJECTED, "scope_denied");
                throw new IllegalStateException("Agent request scope is not authorized.");
            }

            try {
                executionContext.acquire(toolName);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                record(TraceStatus.REJECTED, "budget_or_allowlist_denied");
                throw exception;
            }

            try {
                String result = toolContext == null
                        ? delegate.call(toolInput)
                        : delegate.call(toolInput, toolContext);
                record(TraceStatus.SUCCESS, "completed");
                return result;
            } catch (IllegalArgumentException exception) {
                record(TraceStatus.REJECTED, "invalid_arguments");
                throw exception;
            } catch (RuntimeException exception) {
                record(TraceStatus.FAILED, "tool_execution_failed");
                throw exception;
            }
        }

        private boolean verifyScope() {
            try {
                return scopeVerifier.isAllowed(executionContext.scope());
            } catch (RuntimeException exception) {
                record(TraceStatus.FAILED, "scope_check_failed");
                return false;
            }
        }

        private void record(TraceStatus status, String reason) {
            executionContext.record(toolName, status, reason);
            if (status == TraceStatus.SUCCESS) {
                log.info("Controlled agent tool trace: traceId={}, tool={}, status={}, reason={}",
                        executionContext.traceId(), toolName.wireName(), status, reason);
                return;
            }
            log.warn("Controlled agent tool trace: traceId={}, tool={}, status={}, reason={}",
                    executionContext.traceId(), toolName.wireName(), status, reason);
        }
    }
}
