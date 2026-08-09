package com.brand.agentpoc.agent.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.agent.domain.AgentRequestScope;
import com.brand.agentpoc.service.SessionOwnershipService;
import org.junit.jupiter.api.Test;

class SessionOwnershipAgentScopeVerifierTest {

    @Test
    void requiresAuthenticationActiveBatchAndCurrentSessionOwnership() {
        SessionOwnershipService ownershipService = mock(SessionOwnershipService.class);
        SessionOwnershipAgentScopeVerifier verifier = new SessionOwnershipAgentScopeVerifier(ownershipService);
        AgentRequestScope allowedScope = AgentRequestScope.authenticated("session-1", "subject-1");
        when(ownershipService.owns("session-1", "subject-1")).thenReturn(true);

        assertThat(verifier.isAllowed(allowedScope)).isTrue();
        assertThat(verifier.isAllowed(AgentRequestScope.unauthenticated("session-1"))).isFalse();
        assertThat(verifier.isAllowed(new AgentRequestScope("session-1", "subject-1", false))).isFalse();
        verify(ownershipService).owns("session-1", "subject-1");
    }
}
