package com.brand.agentpoc.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthAdministrationServiceTest {

    @Test
    void rejectsAStaleUserVersionBeforeMutation() {
        AuthUserRepository users = mock(AuthUserRepository.class);
        AuthUserEntity user = mock(AuthUserEntity.class);
        when(user.getVersion()).thenReturn(4L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        AuthAdministrationService service = new AuthAdministrationService(
                users,
                mock(AuthRoleRepository.class),
                mock(PasswordEncoder.class),
                mock(IdentityInputPolicy.class),
                mock(AuthSessionService.class),
                mock(AuthAuditService.class),
                Clock.systemUTC()
        );

        assertThatThrownBy(() -> service.changeEnabled(actor(), 7L, true, 3L, "trace"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed since it was loaded");
    }

    private AuthPrincipal actor() {
        return new AuthPrincipal(1L, 1L, "family", "admin", "Admin", true, false, Set.of(), Set.of());
    }
}
