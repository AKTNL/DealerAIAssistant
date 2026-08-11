package com.brand.agentpoc.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.auth.application.IdentityInputPolicy;
import com.brand.agentpoc.auth.domain.BuiltInRole;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import com.brand.agentpoc.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthBootstrapTest {

    private static final Instant NOW = Instant.parse("2026-08-11T05:30:00Z");
    private AuthRoleRepository roleRepository;
    private AuthUserRepository userRepository;
    private AuthAuditEventRepository auditEventRepository;
    private PasswordEncoder passwordEncoder;
    private AppProperties properties;

    @BeforeEach
    void setUp() {
        roleRepository = mock(AuthRoleRepository.class);
        userRepository = mock(AuthUserRepository.class);
        auditEventRepository = mock(AuthAuditEventRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        properties = new AppProperties();
        when(roleRepository.findByRoleKeyIgnoreCase(any())).thenReturn(List.of());
        when(roleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void seedsCanonicalRolesAndOneTemporaryAdministrator() {
        properties.getAuth().getBootstrap().setUsername(" Initial.Admin ");
        properties.getAuth().getBootstrap().setPassword("temporary-password");
        properties.getAuth().getBootstrap().setDisplayName("Initial Administrator");
        when(passwordEncoder.encode("temporary-password")).thenReturn("{test}encoded");

        bootstrap().run(mock(ApplicationArguments.class));

        ArgumentCaptor<AuthRoleEntity> roles = ArgumentCaptor.forClass(AuthRoleEntity.class);
        verify(roleRepository, org.mockito.Mockito.times(3)).save(roles.capture());
        assertThat(roles.getAllValues())
                .extracting(AuthRoleEntity::getRoleKey)
                .containsExactlyInAnyOrder("ADMIN", "ANALYST", "VIEWER");

        ArgumentCaptor<AuthUserEntity> user = ArgumentCaptor.forClass(AuthUserEntity.class);
        verify(userRepository).save(user.capture());
        assertThat(user.getValue().getUsername()).isEqualTo("initial.admin");
        assertThat(user.getValue().getPasswordHash()).isEqualTo("{test}encoded");
        assertThat(user.getValue().getMustChangePassword()).isTrue();
        assertThat(user.getValue().getRoles())
                .extracting(AuthRoleEntity::getRoleKey)
                .containsExactly(BuiltInRole.ADMIN.roleKey());

        ArgumentCaptor<AuthAuditEventEntity> audit = ArgumentCaptor.forClass(AuthAuditEventEntity.class);
        verify(auditEventRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("USER_BOOTSTRAP");
        assertThat(audit.getValue().getDetailCode()).isEqualTo("initial_administrator_created");
    }

    @Test
    void skipsCredentialBootstrapWhenUsersAlreadyExist() {
        when(userRepository.count()).thenReturn(1L);

        bootstrap().run(mock(ApplicationArguments.class));

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
        verify(auditEventRepository, never()).save(any());
    }

    @Test
    void allowsADevelopmentContextToStartLockedWithoutCredentials() {
        bootstrap().run(mock(ApplicationArguments.class));

        verify(userRepository, never()).save(any());
    }

    @Test
    void failsClosedWhenBootstrapIsRequiredOrPartiallyConfigured() {
        properties.getAuth().getBootstrap().setRequired(true);
        assertThatThrownBy(() -> bootstrap().run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("username and password");

        properties.getAuth().getBootstrap().setRequired(false);
        properties.getAuth().getBootstrap().setUsername("administrator");
        assertThatThrownBy(() -> bootstrap().run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("username and password");
    }

    @Test
    void failsWhenABuiltInRoleDriftsFromTheCodeCatalog() {
        AuthRoleEntity driftedAdmin = new AuthRoleEntity(
                "ADMIN",
                "Administrator",
                true,
                BuiltInRole.VIEWER.permissions(),
                NOW
        );
        when(roleRepository.findByRoleKeyIgnoreCase("ADMIN")).thenReturn(List.of(driftedAdmin));

        assertThatThrownBy(() -> bootstrap().run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("definition drifted");
        verify(userRepository, never()).save(any());
    }

    private AuthBootstrap bootstrap() {
        return new AuthBootstrap(
                roleRepository,
                userRepository,
                auditEventRepository,
                passwordEncoder,
                new IdentityInputPolicy(),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
