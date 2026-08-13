package com.brand.agentpoc.service;

import com.brand.agentpoc.dto.response.ImportDataStatus;
import com.brand.agentpoc.tenant.domain.TenantScoped;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class ImportQualityService {

    private final ConcurrentMap<Long, ImportDataStatus> latestByTenant = new ConcurrentHashMap<>();

    public ImportDataStatus getLatest() {
        return getLatest(TenantScoped.DEFAULT_TENANT_ID);
    }

    public ImportDataStatus getLatest(Long tenantId) {
        return latestByTenant.getOrDefault(requireTenantId(tenantId), ImportDataStatus.pending());
    }

    public void publish(ImportDataStatus status) {
        publish(TenantScoped.DEFAULT_TENANT_ID, status);
    }

    public void publish(Long tenantId, ImportDataStatus status) {
        latestByTenant.put(requireTenantId(tenantId), java.util.Objects.requireNonNull(status, "status"));
    }

    private Long requireTenantId(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required.");
        }
        return tenantId;
    }
}
