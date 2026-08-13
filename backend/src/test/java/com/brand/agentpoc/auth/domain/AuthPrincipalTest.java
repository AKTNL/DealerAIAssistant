package com.brand.agentpoc.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthPrincipalTest {

    @Test
    void stableSubjectSeparatesTheSameIdentityAcrossTenants() {
        AuthPrincipal identity = new AuthPrincipal(
                42L, 7L, "family", "user", "User", true, false, Set.of(), Set.of());

        AuthPrincipal tenantOne = identity.withTenant(1L, "default", 10L, Set.of(), Set.of(), Set.of());
        AuthPrincipal tenantTwo = identity.withTenant(2L, "second", 20L, Set.of(), Set.of(), Set.of());

        assertThat(tenantOne.stableSubject()).isEqualTo("42:tenant:1");
        assertThat(tenantTwo.stableSubject()).isEqualTo("42:tenant:2");
    }
}
