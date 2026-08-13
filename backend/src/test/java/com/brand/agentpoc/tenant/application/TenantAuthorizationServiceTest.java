package com.brand.agentpoc.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRoleEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRoleRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class TenantAuthorizationServiceTest {

    private final TenantMembershipRepository memberships = mock(TenantMembershipRepository.class);
    private final TenantMembershipRoleRepository membershipRoles = mock(TenantMembershipRoleRepository.class);
    private final AuthRoleRepository roles = mock(AuthRoleRepository.class);
    private final TenantAuthorizationService service = new TenantAuthorizationService(
            memberships, membershipRoles, roles);

    @Test
    void requiresExplicitSelectionWhenUserHasMultipleEnabledMemberships() {
        TenantMembershipEntity defaultMembership = membership(11L, "default", "Default tenant", true);
        TenantMembershipEntity secondMembership = membership(12L, "tenant-b", "Tenant B", true);
        when(memberships.findByUserIdAndEnabledTrue(7L)).thenReturn(List.of(defaultMembership, secondMembership));

        assertThatThrownBy(() -> service.resolve(identity(), null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Tenant access denied.");
    }

    @Test
    void multipleMembershipsWithoutSelectionReturnChoicesWithoutPrincipal() {
        TenantMembershipEntity defaultMembership = membership(11L, "default", "Default tenant", true);
        TenantMembershipEntity secondMembership = membership(12L, "tenant-b", "Tenant B", true);
        when(memberships.findByUserIdAndEnabledTrue(7L)).thenReturn(List.of(defaultMembership, secondMembership));

        TenantAuthorizationService.TenantUserContext context = service.resolveUserContext(identity(), null);

        assertThat(context.principal()).isEmpty();
        assertThat(context.memberships()).containsExactly(
                new TenantAuthorizationService.TenantMembershipView(11L, "default", "Default tenant"),
                new TenantAuthorizationService.TenantMembershipView(12L, "tenant-b", "Tenant B")
        );
    }

    @Test
    void explicitEnabledMembershipReturnsTenantPrincipalAndAllChoices() {
        TenantMembershipEntity defaultMembership = membership(11L, "default", "Default tenant", true);
        TenantMembershipEntity secondMembership = membership(12L, "tenant-b", "Tenant B", true);
        when(memberships.findByUserIdAndEnabledTrue(7L)).thenReturn(List.of(defaultMembership, secondMembership));
        AuthRoleEntity role = mock(AuthRoleEntity.class);
        when(role.getId()).thenReturn(5L);
        when(role.getRoleKey()).thenReturn("VIEWER");
        when(role.getPermissions()).thenReturn(Set.of(PermissionKey.DATA_READ));
        TenantMembershipRoleEntity assignment = mock(TenantMembershipRoleEntity.class);
        when(assignment.getRoleId()).thenReturn(5L);
        when(membershipRoles.findByMembershipId(12L)).thenReturn(List.of(assignment));
        when(roles.findAllById(Set.of(5L))).thenReturn(List.of(role));

        TenantAuthorizationService.TenantUserContext context = service.resolveUserContext(identity(), "tenant-b");

        assertThat(context.principal()).hasValueSatisfying(principal -> {
            assertThat(principal.tenantId()).isEqualTo(12L);
            assertThat(principal.tenantKey()).isEqualTo("tenant-b");
            assertThat(principal.roles()).containsExactly("VIEWER");
            assertThat(principal.permissions()).containsExactly(PermissionKey.DATA_READ);
        });
        assertThat(context.memberships()).containsExactly(
                new TenantAuthorizationService.TenantMembershipView(11L, "default", "Default tenant"),
                new TenantAuthorizationService.TenantMembershipView(12L, "tenant-b", "Tenant B")
        );
    }

    @Test
    void headerCannotSelectTenantWithoutEnabledMembership() {
        TenantMembershipEntity membership = membership(11L, "default", true);
        when(memberships.findByUserIdAndEnabledTrue(7L)).thenReturn(List.of(membership));

        assertThatThrownBy(() -> service.resolve(identity(), "tenant-b"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Tenant access denied.");
    }

    @Test
    void disabledMembershipTakesEffectOnTheNextRequest() {
        TenantMembershipEntity membership = membership(11L, "default", true);
        when(memberships.findByUserIdAndEnabledTrue(7L)).thenReturn(List.of(membership));
        AuthRoleEntity role = mock(AuthRoleEntity.class);
        when(role.getId()).thenReturn(5L);
        when(role.getRoleKey()).thenReturn("VIEWER");
        when(role.getPermissions()).thenReturn(Set.of(PermissionKey.DATA_READ));
        TenantMembershipRoleEntity assignment = mock(TenantMembershipRoleEntity.class);
        when(assignment.getRoleId()).thenReturn(5L);
        when(membershipRoles.findByMembershipId(11L)).thenReturn(List.of(assignment));
        when(roles.findAllById(Set.of(5L))).thenReturn(List.of(role));

        AuthPrincipal resolved = service.resolve(identity(), "default");
        assertThat(resolved.tenantKey()).isEqualTo("default");
        assertThat(resolved.permissions()).containsExactly(PermissionKey.DATA_READ);

        when(memberships.findByUserIdAndEnabledTrue(7L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.resolve(identity(), "default"))
                .isInstanceOf(AccessDeniedException.class);
    }

    private AuthPrincipal identity() {
        return new AuthPrincipal(7L, 8L, "family", "user", "User", true, false, Set.of(), Set.of());
    }

    private TenantMembershipEntity membership(Long id, String key, boolean enabled) {
        return membership(id, key, key, enabled);
    }

    private TenantMembershipEntity membership(Long id, String key, String displayName, boolean enabled) {
        TenantEntity tenant = mock(TenantEntity.class);
        when(tenant.getId()).thenReturn(id);
        when(tenant.getTenantKey()).thenReturn(key);
        when(tenant.getDisplayName()).thenReturn(displayName);
        when(tenant.getEnabled()).thenReturn(enabled);
        TenantMembershipEntity membership = mock(TenantMembershipEntity.class);
        when(membership.getId()).thenReturn(id);
        when(membership.getTenant()).thenReturn(tenant);
        when(membership.getUserId()).thenReturn(7L);
        when(membership.getEnabled()).thenReturn(enabled);
        return membership;
    }
}
