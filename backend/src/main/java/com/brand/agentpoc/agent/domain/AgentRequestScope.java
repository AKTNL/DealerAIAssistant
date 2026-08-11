package com.brand.agentpoc.agent.domain;

import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import java.util.Set;

public record AgentRequestScope(
        String sessionId,
        String subject,
        boolean activeBatchOnly,
        Set<PermissionKey> permissions,
        OrganizationDataScope organizationDataScope
) {

    public AgentRequestScope {
        sessionId = normalize(sessionId);
        subject = normalize(subject);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        organizationDataScope = organizationDataScope == null
                ? OrganizationDataScope.empty()
                : organizationDataScope;
    }

    public AgentRequestScope(
            String sessionId,
            String subject,
            boolean activeBatchOnly,
            Set<PermissionKey> permissions
    ) {
        this(sessionId, subject, activeBatchOnly, permissions, OrganizationDataScope.unrestrictedScope());
    }

    public static AgentRequestScope authenticated(
            String sessionId,
            String subject,
            Set<PermissionKey> permissions
    ) {
        return new AgentRequestScope(
                sessionId,
                subject,
                true,
                permissions,
                OrganizationDataScope.unrestrictedScope()
        );
    }

    public static AgentRequestScope authenticated(
            String sessionId,
            String subject,
            Set<PermissionKey> permissions,
            OrganizationDataScope organizationDataScope
    ) {
        return new AgentRequestScope(sessionId, subject, true, permissions, organizationDataScope);
    }

    public static AgentRequestScope unauthenticated(String sessionId) {
        return new AgentRequestScope(sessionId, "", true, Set.of(), OrganizationDataScope.empty());
    }

    public boolean authenticated() {
        return !sessionId.isBlank() && !subject.isBlank();
    }

    public boolean hasPermission(PermissionKey permission) {
        return permissions.contains(permission);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
