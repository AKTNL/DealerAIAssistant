package com.brand.agentpoc.agent.infrastructure;

import java.util.List;
import org.springframework.ai.tool.ToolCallback;

public record ControlledAgentToolSession(
        String traceId,
        List<ToolCallback> callbacks
) {

    public ControlledAgentToolSession {
        callbacks = callbacks == null ? List.of() : List.copyOf(callbacks);
    }
}
