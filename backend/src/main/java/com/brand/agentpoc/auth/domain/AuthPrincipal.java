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
        Set<PermissionKey> permissions
) {

    public AuthPrincipal {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public String stableSubject() {
        return String.valueOf(userId);
    }

    public boolean hasPermission(PermissionKey permission) {
        return permissions.contains(permission);
    }
}
