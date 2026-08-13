package com.brand.agentpoc.organization.domain;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import java.util.Set;

public record OrganizationAuthorizationContext(
        AuthPrincipal principal,
        OrganizationDataScope dataScope
) {

    public OrganizationAuthorizationContext {
        if (principal == null) {
            throw new IllegalArgumentException("principal is required.");
        }
        dataScope = dataScope == null ? OrganizationDataScope.empty() : dataScope;
    }

    public Set<PermissionKey> permissions() {
        return principal.permissions();
    }

    public Long tenantId() {
        return principal.tenantId();
    }

    public String tenantKey() {
        return principal.tenantKey();
    }
}
