package com.brand.agentpoc.organization.domain;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import org.springframework.security.access.AccessDeniedException;

public record OrganizationDataScope(
        Long tenantId,
        String tenantKey,
        Set<Long> organizationNodeIds,
        Set<Long> grantNodeIds,
        Set<String> dealerCodes,
        boolean rootCoverage,
        boolean unrestricted
) {

    public OrganizationDataScope {
        tenantKey = tenantKey == null ? null : tenantKey.trim();
        organizationNodeIds = organizationNodeIds == null ? Set.of() : Set.copyOf(organizationNodeIds);
        grantNodeIds = grantNodeIds == null ? Set.of() : Set.copyOf(grantNodeIds);
        dealerCodes = dealerCodes == null
                ? Set.of()
                : dealerCodes.stream().map(OrganizationDataScope::normalizeDealerCode).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static OrganizationDataScope empty() {
        return new OrganizationDataScope(null, null, Set.of(), Set.of(), Set.of(), false, false);
    }

    public static OrganizationDataScope unrestrictedScope() {
        return new OrganizationDataScope(
                com.brand.agentpoc.tenant.domain.TenantScoped.DEFAULT_TENANT_ID,
                "default",
                Set.of(),
                Set.of(),
                Set.of(),
                true,
                true
        );
    }

    public OrganizationDataScope(
            Set<Long> organizationNodeIds,
            Set<String> dealerCodes,
            boolean rootCoverage,
            boolean unrestricted
    ) {
        this(com.brand.agentpoc.tenant.domain.TenantScoped.DEFAULT_TENANT_ID, "default",
                organizationNodeIds, organizationNodeIds, dealerCodes, rootCoverage, unrestricted);
    }

    public OrganizationDataScope(
            Set<Long> organizationNodeIds,
            Set<Long> grantNodeIds,
            Set<String> dealerCodes,
            boolean rootCoverage,
            boolean unrestricted
    ) {
        this(com.brand.agentpoc.tenant.domain.TenantScoped.DEFAULT_TENANT_ID, "default",
                organizationNodeIds, grantNodeIds, dealerCodes, rootCoverage, unrestricted);
    }

    public static OrganizationDataScope tenantScope(
            Long tenantId,
            String tenantKey,
            Set<Long> organizationNodeIds,
            Set<Long> grantNodeIds,
            Set<String> dealerCodes,
            boolean rootCoverage
    ) {
        if (tenantId == null || tenantKey == null || tenantKey.isBlank()) {
            throw new IllegalArgumentException("tenant context is required.");
        }
        return new OrganizationDataScope(
                tenantId, tenantKey, organizationNodeIds, grantNodeIds, dealerCodes, rootCoverage, false);
    }

    public boolean hasDataAccess() {
        return unrestricted || !dealerCodes.isEmpty();
    }

    public void requireDataAccess() {
        if (!hasDataAccess()) {
            throw new AccessDeniedException("No active organization data scope is assigned.");
        }
        requireTenant();
    }

    public void requireTenant() {
        if (tenantId == null || tenantKey == null || tenantKey.isBlank()) {
            throw new AccessDeniedException("Tenant context is required.");
        }
    }

    public void requireRootCoverage() {
        requireDataAccess();
        if (!unrestricted && !rootCoverage) {
            throw new AccessDeniedException("Root organization coverage is required.");
        }
    }

    public boolean allowsDealer(String dealerCode) {
        return unrestricted || dealerCodes.contains(normalizeDealerCode(dealerCode));
    }

    public boolean containsAllNodes(Set<Long> nodeIds) {
        return unrestricted || organizationNodeIds.containsAll(nodeIds == null ? Set.of() : nodeIds);
    }

    public <T> List<T> filter(List<T> rows, Function<T, String> dealerCodeExtractor) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        if (unrestricted) {
            return List.copyOf(rows);
        }
        return rows.stream()
                .filter(row -> row != null && allowsDealer(dealerCodeExtractor.apply(row)))
                .toList();
    }

    public <T extends com.brand.agentpoc.tenant.domain.TenantScoped> List<T> filterTenant(List<T> rows) {
        requireTenant();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(row -> row != null && tenantId.equals(row.getTenantId()))
                .toList();
    }

    private static String normalizeDealerCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
