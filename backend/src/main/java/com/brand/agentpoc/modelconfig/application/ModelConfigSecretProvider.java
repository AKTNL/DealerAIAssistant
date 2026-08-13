package com.brand.agentpoc.modelconfig.application;

public interface ModelConfigSecretProvider {

    String protect(Long tenantId, String secret);

    String reveal(Long tenantId, String protectedSecret);

    int version();
}
