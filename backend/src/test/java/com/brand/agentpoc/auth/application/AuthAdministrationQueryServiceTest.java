package com.brand.agentpoc.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthSessionEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthSessionRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthAdministrationQueryServiceTest {

    @Test
    void exposesOnlySafeSessionMetadata() {
        Instant now = Instant.parse("2026-08-12T00:00:00Z");
        AuthUserRepository users = mock(AuthUserRepository.class);
        AuthSessionRepository sessions = mock(AuthSessionRepository.class);
        when(users.existsById(5L)).thenReturn(true);
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
                Clock.fixed(now, ZoneOffset.UTC)
        );

        AuthAdministrationQueryService.SessionView view = service.listUserSessions(5L).getFirst();

        assertThat(view.active()).isTrue();
        assertThat(view.id()).isEqualTo(8L);
        assertThat(List.of(AuthAdministrationQueryService.SessionView.class.getRecordComponents())
                .stream()
                .map(component -> component.getName()))
                .doesNotContain("accessTokenHash", "refreshTokenHash", "familyKey");
    }
}
