package com.brand.agentpoc.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthSessionEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthSessionRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthAdministrationQueryServiceTest {

    @Test
    void exposesOnlySafeSessionMetadata() {
        Instant now = Instant.parse("2026-08-12T00:00:00Z");
        AuthUserRepository users = mock(AuthUserRepository.class);
        AuthSessionRepository sessions = mock(AuthSessionRepository.class);
        when(users.existsById(5L)).thenReturn(true);
        TenantMembershipRepository memberships = mock(TenantMembershipRepository.class);
        when(memberships.findByTenantIdAndUserId(9L, 5L)).thenReturn(List.of(mock(TenantMembershipEntity.class)));
        when(memberships.findByUserId(5L)).thenReturn(List.of(mock(TenantMembershipEntity.class)));
        AuthSessionEntity session = mock(AuthSessionEntity.class);
        when(session.getId()).thenReturn(8L);
        when(session.getIssuedAt()).thenReturn(now.minusSeconds(60));
        when(session.getAccessExpiresAt()).thenReturn(now.plusSeconds(300));
        when(session.getRefreshExpiresAt()).thenReturn(now.plusSeconds(3600));
        when(sessions.findByUserId(5L)).thenReturn(List.of(session));
        AuthAdministrationQueryService service = new AuthAdministrationQueryService(
                users,
                sessions,
                mock(AuthAuditEventRepository.class),
                mock(AuthSessionService.class),
                mock(AuthAuditService.class),
                Clock.fixed(now, ZoneOffset.UTC),
                memberships
        );

        AuthAdministrationQueryService.SessionView view = service.listUserSessions(actor(), 5L).getFirst();

        assertThat(view.active()).isTrue();
        assertThat(view.id()).isEqualTo(8L);
        assertThat(List.of(AuthAdministrationQueryService.SessionView.class.getRecordComponents())
                .stream()
                .map(component -> component.getName()))
                .doesNotContain("accessTokenHash", "refreshTokenHash", "familyKey");
    }

    private AuthPrincipal actor() {
        return new AuthPrincipal(
                1L, 1L, "family", "admin", "Admin", true, false, Set.of(), Set.of(),
                9L, "tenant-nine", 1L, Set.of());
    }
}
