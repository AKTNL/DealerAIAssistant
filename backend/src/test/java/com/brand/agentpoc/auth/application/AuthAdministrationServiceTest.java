package com.brand.agentpoc.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRoleEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRoleRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthAdministrationServiceTest {

    @Test
    void rejectsAStaleUserVersionBeforeMutation() {
        AuthUserRepository users = mock(AuthUserRepository.class);
        AuthUserEntity user = mock(AuthUserEntity.class);
        TenantMembershipEntity membership = mock(TenantMembershipEntity.class);
        when(membership.getVersion()).thenReturn(4L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        TenantMembershipRepository memberships = mock(TenantMembershipRepository.class);
        when(memberships.findByTenantIdAndUserId(9L, 7L)).thenReturn(List.of(membership));
        AuthAdministrationService service = new AuthAdministrationService(
                users,
                mock(AuthRoleRepository.class),
                mock(PasswordEncoder.class),
                mock(IdentityInputPolicy.class),
                mock(AuthSessionService.class),
                mock(AuthAuditService.class),
                Clock.systemUTC(),
                mock(TenantRepository.class),
                memberships,
                mock(TenantMembershipRoleRepository.class)
        );

        assertThatThrownBy(() -> service.changeEnabled(actor(), 7L, true, 3L, "trace"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed since it was loaded");
    }

    @Test
    void createsMembershipOnlyInTheActorsTenant() {
        AuthUserRepository users = mock(AuthUserRepository.class);
        AuthRoleRepository roles = mock(AuthRoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        IdentityInputPolicy inputPolicy = mock(IdentityInputPolicy.class);
        TenantRepository tenants = mock(TenantRepository.class);
        TenantMembershipRepository memberships = mock(TenantMembershipRepository.class);
        TenantMembershipRoleRepository membershipRoles = mock(TenantMembershipRoleRepository.class);
        AuthRoleEntity role = role(5L, "ANALYST");
        TenantEntity tenant = tenant(9L, "tenant-nine");
        AuthUserEntity saved = user(17L, "new.user");
        TenantMembershipEntity membership = membership(21L, tenant, 17L);
        when(inputPolicy.normalizeUsername("new.user")).thenReturn("new.user");
        when(inputPolicy.normalizeDisplayName("New User", "new.user")).thenReturn("New User");
        when(encoder.encode("Temporary!123")).thenReturn("encoded");
        when(roles.findByRoleKeyIgnoreCase("ANALYST")).thenReturn(List.of(role));
        when(tenants.findById(9L)).thenReturn(Optional.of(tenant));
        when(users.saveAndFlush(any(AuthUserEntity.class))).thenReturn(saved);
        when(memberships.saveAndFlush(any(TenantMembershipEntity.class))).thenReturn(membership);
        when(membershipRoles.findByMembershipId(21L)).thenReturn(List.of(
                new TenantMembershipRoleEntity(membership, 5L)));
        when(roles.findAllById(Set.of(5L))).thenReturn(List.of(role));
        AuthAdministrationService service = service(users, roles, encoder, inputPolicy, tenants, memberships,
                membershipRoles);

        AuthAdministrationService.UserView view = service.createUser(
                actor(), "new.user", "New User", "Temporary!123", Set.of("ANALYST"), "trace");

        ArgumentCaptor<TenantMembershipEntity> createdMembership =
                ArgumentCaptor.forClass(TenantMembershipEntity.class);
        verify(memberships).saveAndFlush(createdMembership.capture());
        assertThat(createdMembership.getValue().getTenant().getId()).isEqualTo(9L);
        assertThat(view.roles()).containsExactly("ANALYST");
    }

    @Test
    void assigningRolesOnlyReplacesTheCurrentTenantMembership() {
        AuthUserRepository users = mock(AuthUserRepository.class);
        AuthRoleRepository roles = mock(AuthRoleRepository.class);
        TenantMembershipRepository memberships = mock(TenantMembershipRepository.class);
        TenantMembershipRoleRepository membershipRoles = mock(TenantMembershipRoleRepository.class);
        AuthUserEntity user = user(17L, "member");
        TenantMembershipEntity current = membership(21L, tenant(9L, "tenant-nine"), 17L);
        TenantMembershipEntity other = membership(22L, tenant(10L, "tenant-ten"), 17L);
        AuthRoleEntity role = role(5L, "ANALYST");
        when(users.findById(17L)).thenReturn(Optional.of(user));
        when(memberships.findByTenantIdAndUserId(9L, 17L)).thenReturn(List.of(current));
        when(roles.findByRoleKeyIgnoreCase("ANALYST")).thenReturn(List.of(role));
        when(membershipRoles.findByMembershipId(21L)).thenReturn(List.of());
        AuthAdministrationService service = service(
                users, roles, mock(PasswordEncoder.class), mock(IdentityInputPolicy.class),
                mock(TenantRepository.class), memberships, membershipRoles);

        service.assignRoles(actor(), 17L, Set.of("ANALYST"), 0L, "trace");

        verify(membershipRoles).deleteByMembershipId(21L);
        verify(membershipRoles, never()).deleteByMembershipId(other.getId());
        verify(users, never()).saveAndFlush(any(AuthUserEntity.class));
    }

    @Test
    void listsOnlyMembershipsFromTheActorsTenant() {
        AuthUserRepository users = mock(AuthUserRepository.class);
        AuthRoleRepository roles = mock(AuthRoleRepository.class);
        TenantMembershipRepository memberships = mock(TenantMembershipRepository.class);
        TenantMembershipRoleRepository membershipRoles = mock(TenantMembershipRoleRepository.class);
        AuthUserEntity visible = user(17L, "visible");
        TenantMembershipEntity current = membership(21L, tenant(9L, "tenant-nine"), 17L);
        when(memberships.findByTenantId(9L)).thenReturn(List.of(current));
        when(users.findById(17L)).thenReturn(Optional.of(visible));
        when(membershipRoles.findByMembershipId(21L)).thenReturn(List.of());
        AuthAdministrationService service = service(
                users, roles, mock(PasswordEncoder.class), mock(IdentityInputPolicy.class),
                mock(TenantRepository.class), memberships, membershipRoles);

        assertThat(service.listUsers(actor())).extracting(AuthAdministrationService.UserView::username)
                .containsExactly("visible");
        verify(memberships).findByTenantId(9L);
        verify(users, never()).findAll();
    }

    private AuthAdministrationService service(
            AuthUserRepository users,
            AuthRoleRepository roles,
            PasswordEncoder encoder,
            IdentityInputPolicy inputPolicy,
            TenantRepository tenants,
            TenantMembershipRepository memberships,
            TenantMembershipRoleRepository membershipRoles
    ) {
        return new AuthAdministrationService(
                users, roles, encoder, inputPolicy, mock(AuthSessionService.class), mock(AuthAuditService.class),
                Clock.systemUTC(), tenants, memberships, membershipRoles);
    }

    private AuthRoleEntity role(Long id, String key) {
        AuthRoleEntity role = mock(AuthRoleEntity.class);
        when(role.getId()).thenReturn(id);
        when(role.getRoleKey()).thenReturn(key);
        when(role.getPermissions()).thenReturn(Set.of(PermissionKey.DATA_READ));
        return role;
    }

    private TenantEntity tenant(Long id, String key) {
        TenantEntity tenant = mock(TenantEntity.class);
        when(tenant.getId()).thenReturn(id);
        when(tenant.getTenantKey()).thenReturn(key);
        when(tenant.getEnabled()).thenReturn(true);
        return tenant;
    }

    private TenantMembershipEntity membership(Long id, TenantEntity tenant, Long userId) {
        TenantMembershipEntity membership = mock(TenantMembershipEntity.class);
        when(membership.getId()).thenReturn(id);
        when(membership.getTenant()).thenReturn(tenant);
        when(membership.getUserId()).thenReturn(userId);
        when(membership.getEnabled()).thenReturn(true);
        when(membership.getVersion()).thenReturn(0L);
        return membership;
    }

    private AuthUserEntity user(Long id, String username) {
        AuthUserEntity user = mock(AuthUserEntity.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        when(user.getDisplayName()).thenReturn(username);
        when(user.getEnabled()).thenReturn(true);
        when(user.getMustChangePassword()).thenReturn(false);
        return user;
    }

    private AuthPrincipal actor() {
        return new AuthPrincipal(
                1L, 1L, "family", "admin", "Admin", true, false, Set.of(), Set.of(),
                9L, "tenant-nine", 1L, Set.of());
    }
}
