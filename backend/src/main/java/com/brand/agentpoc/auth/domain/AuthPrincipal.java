package com.brand.agentpoc.auth.domain;

import java.util.Set;

public record AuthPrincipal(
        Long userId,
        Long sessionId,
        String familyKey,
        String username,
        String displayName,
        boolean enabled,
        boolean mustChangePassword,
        Set<String> roles,
        Set<PermissionKey> permissions,
        Long tenantId,
        String tenantKey,
        Long membershipId,
        Set<Long> roleIds
) {

    public AuthPrincipal {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
        tenantKey = tenantKey == null ? null : tenantKey.trim();
        roleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
    }

    public AuthPrincipal(
            Long userId,
            Long sessionId,
            String familyKey,
            String username,
            String displayName,
            boolean enabled,
            boolean mustChangePassword,
            Set<String> roles,
            Set<PermissionKey> permissions
    ) {
        this(userId, sessionId, familyKey, username, displayName, enabled, mustChangePassword,
                roles, permissions, null, null, null, Set.of());
    }

    public String stableSubject() {
        return hasTenantContext()
                ? userId + ":tenant:" + tenantId
                : String.valueOf(userId);
    }

    public boolean hasPermission(PermissionKey permission) {
        return permissions.contains(permission);
    }

    public boolean hasTenantContext() {
        return tenantId != null && tenantKey != null && !tenantKey.isBlank() && membershipId != null;
    }

    public AuthPrincipal withTenant(
            Long selectedTenantId,
            String selectedTenantKey,
            Long selectedMembershipId,
            Set<Long> selectedRoleIds,
            Set<String> selectedRoles,
            Set<PermissionKey> selectedPermissions
    ) {
        return new AuthPrincipal(
                userId,
                sessionId,
                familyKey,
                username,
                displayName,
                enabled,
                mustChangePassword,
                selectedRoles,
                selectedPermissions,
                selectedTenantId,
                selectedTenantKey,
                selectedMembershipId,
                selectedRoleIds
        );
    }
}
