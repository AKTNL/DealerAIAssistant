package com.brand.agentpoc.agent.application;

import com.brand.agentpoc.agent.domain.AgentRequestScope;

@FunctionalInterface
public interface AgentScopeVerifier {

    boolean isAllowed(AgentRequestScope scope);
}
