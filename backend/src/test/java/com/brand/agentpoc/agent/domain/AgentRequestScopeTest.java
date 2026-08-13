package com.brand.agentpoc.agent.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentRequestScopeTest {

    @Test
    void exposesTheTrustedTenantFromTheOrganizationScope() {
        OrganizationDataScope dataScope = OrganizationDataScope.tenantScope(
                9L, "tenant-nine", Set.of(1L), Set.of(1L), Set.of("D-1"), false);

        AgentRequestScope scope = AgentRequestScope.authenticated("session", "subject", Set.of(), dataScope);

        assertThat(scope.tenantId()).isEqualTo(9L);
        assertThat(scope.tenantKey()).isEqualTo("tenant-nine");
        assertThat(scope.hasTenantContext()).isTrue();
    }

    @Test
    void rejectsAConflictingTenantAndOrganizationScope() {
        OrganizationDataScope dataScope = OrganizationDataScope.tenantScope(
                9L, "tenant-nine", Set.of(1L), Set.of(1L), Set.of("D-1"), false);

        assertThatThrownBy(() -> new AgentRequestScope(
                "session", "subject", true, Set.of(), 10L, "tenant-ten", dataScope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }
}
