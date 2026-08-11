package com.brand.agentpoc.agent.domain;

import com.brand.agentpoc.auth.domain.PermissionKey;
import java.util.Set;

public record AgentRequestScope(
        String sessionId,
        String subject,
        boolean activeBatchOnly,
        Set<PermissionKey> permissions
) {

    public AgentRequestScope {
        sessionId = normalize(sessionId);
        subject = normalize(subject);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public static AgentRequestScope authenticated(
            String sessionId,
            String subject,
            Set<PermissionKey> permissions
    ) {
        return new AgentRequestScope(sessionId, subject, true, permissions);
    }

    public static AgentRequestScope unauthenticated(String sessionId) {
        return new AgentRequestScope(sessionId, "", true, Set.of());
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
