package com.brand.agentpoc.agent.domain;

public record AgentRequestScope(
        String sessionId,
        String subject,
        boolean activeBatchOnly
) {

    public AgentRequestScope {
        sessionId = normalize(sessionId);
        subject = normalize(subject);
    }

    public static AgentRequestScope authenticated(String sessionId, String subject) {
        return new AgentRequestScope(sessionId, subject, true);
    }

    public static AgentRequestScope unauthenticated(String sessionId) {
        return new AgentRequestScope(sessionId, "", true);
    }

    public boolean authenticated() {
        return !sessionId.isBlank() && !subject.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
