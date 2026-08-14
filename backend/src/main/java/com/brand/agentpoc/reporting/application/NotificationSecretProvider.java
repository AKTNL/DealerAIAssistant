package com.brand.agentpoc.reporting.application;

public interface NotificationSecretProvider {

    String protect(Long tenantId, String secret);

    String reveal(Long tenantId, String protectedSecret);

    int version();
}
