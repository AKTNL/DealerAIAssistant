package com.brand.agentpoc.tenant.domain;

/**
 * Shared tenant ownership contract for records that are migrated from the original single-tenant schema.
 */
public interface TenantScoped {

    long DEFAULT_TENANT_ID = 1L;

    Long getTenantId();
}
