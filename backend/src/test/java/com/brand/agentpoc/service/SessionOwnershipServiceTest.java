package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionOwnershipServiceTest {

    @Test
    void claimsUnownedSessionAndRejectsDifferentTokenSubject() {
        SessionOwnershipService service = new SessionOwnershipService();

        assertThat(service.claimOrVerify("session-1", "token-a")).isTrue();
        assertThat(service.claimOrVerify("session-1", "token-a")).isTrue();
        assertThat(service.claimOrVerify("session-1", "token-b")).isFalse();
        assertThat(service.owns("session-1", "token-a")).isTrue();
        assertThat(service.owns("session-1", "token-b")).isFalse();
    }

    @Test
    void releasesSessionOnlyForOwningTokenSubject() {
        SessionOwnershipService service = new SessionOwnershipService();

        service.claimOrVerify("session-1", "token-a");

        assertThat(service.release("session-1", "token-b")).isFalse();
        assertThat(service.owns("session-1", "token-a")).isTrue();

        assertThat(service.release("session-1", "token-a")).isTrue();
        assertThat(service.owns("session-1", "token-a")).isFalse();
    }
}
