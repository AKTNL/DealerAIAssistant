package com.brand.agentpoc.agent.infrastructure;

import com.brand.agentpoc.agent.application.AgentScopeVerifier;
import com.brand.agentpoc.agent.domain.AgentRequestScope;
import com.brand.agentpoc.service.SessionOwnershipService;
import org.springframework.stereotype.Component;

@Component
public class SessionOwnershipAgentScopeVerifier implements AgentScopeVerifier {

    private final SessionOwnershipService sessionOwnershipService;

    public SessionOwnershipAgentScopeVerifier(SessionOwnershipService sessionOwnershipService) {
        this.sessionOwnershipService = sessionOwnershipService;
    }

    @Override
    public boolean isAllowed(AgentRequestScope scope) {
        return scope != null
                && scope.authenticated()
                && scope.activeBatchOnly()
                && scope.hasTenantContext()
                && scope.organizationDataScope().hasDataAccess()
                && sessionOwnershipService.owns(scope.sessionId(), scope.subject());
    }
}
