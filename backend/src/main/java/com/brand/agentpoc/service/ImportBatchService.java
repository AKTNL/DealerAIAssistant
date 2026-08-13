package com.brand.agentpoc.service;

import com.brand.agentpoc.dto.response.ImportDataStatus;
import com.brand.agentpoc.entity.BatchScoped;
import com.brand.agentpoc.entity.ImportBatch;
import com.brand.agentpoc.repository.ImportBatchRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportBatchService {

    public static final String LEGACY_BATCH_ID = BatchScoped.LEGACY_BATCH_ID;
    public static final String GLOBAL_SCOPE_TYPE = "GLOBAL";

    private static final DateTimeFormatter BATCH_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final ImportBatchRepository importBatchRepository;
    private final ConcurrentMap<Long, ImportBatch> inMemoryActiveBatches = new ConcurrentHashMap<>();

    public ImportBatchService(ImportBatchRepository importBatchRepository) {
        this.importBatchRepository = importBatchRepository;
    }

    ImportBatchService() {
        this.importBatchRepository = null;
    }

    public String newBatchId(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "batch" : prefix.trim().toLowerCase(Locale.ROOT);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return normalizedPrefix + "-" + BATCH_TIME_FORMAT.format(Instant.now()) + "-" + suffix;
    }

    @Transactional
    public ImportBatch activateGlobalBatch(String batchId, String source, boolean fallbackActive, String message) {
        return activateTenantBatch(
                com.brand.agentpoc.tenant.domain.TenantScoped.DEFAULT_TENANT_ID,
                batchId,
                source,
                fallbackActive,
                message
        );
    }

    @Transactional
    public ImportBatch activateTenantBatch(
            Long tenantId,
            String batchId,
            String source,
            boolean fallbackActive,
            String message
    ) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required.");
        }
        Instant now = Instant.now();
        ImportBatch batch = new ImportBatch(
                batchId,
                source,
                GLOBAL_SCOPE_TYPE,
                null,
                true,
                fallbackActive,
                now,
                now,
                message,
                tenantId
        );
        inMemoryActiveBatches.put(tenantId, batch);
        if (importBatchRepository == null) {
            return batch;
        }
        ImportBatch saved = importBatchRepository.save(batch);
        inMemoryActiveBatches.put(tenantId, saved);
        return saved;
    }

    public String activeBatchId() {
        return activeBatchId(com.brand.agentpoc.tenant.domain.TenantScoped.DEFAULT_TENANT_ID);
    }

    public String activeBatchId(Long tenantId) {
        return currentActiveBatch(tenantId)
                .map(ImportBatch::getBatchKey)
                .orElse(LEGACY_BATCH_ID);
    }

    public ImportDataStatus.Batch activeStatusBatch() {
        return activeStatusBatch(com.brand.agentpoc.tenant.domain.TenantScoped.DEFAULT_TENANT_ID);
    }

    public ImportDataStatus.Batch activeStatusBatch(Long tenantId) {
        return currentActiveBatch(tenantId)
                .map(this::toStatusBatch)
                .orElse(new ImportDataStatus.Batch(LEGACY_BATCH_ID, true, GLOBAL_SCOPE_TYPE, null, null));
    }

    public <T extends BatchScoped> List<T> filterActive(List<T> rows) {
        String activeBatchId = activeBatchId();
        return rows.stream()
                .filter(row -> activeBatchId.equals(row.getImportBatchId()))
                .toList();
    }

    public <T extends BatchScoped & com.brand.agentpoc.tenant.domain.TenantScoped> List<T> filterActive(
            List<T> rows,
            Long tenantId
    ) {
        if (tenantId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant context is required.");
        }
        String activeBatchId = activeBatchId(tenantId);
        return rows.stream()
                .filter(row -> tenantId.equals(row.getTenantId()))
                .filter(row -> activeBatchId.equals(row.getImportBatchId()))
                .toList();
    }

    public boolean isActive(BatchScoped row) {
        return row != null && activeBatchId().equals(row.getImportBatchId());
    }

    private Optional<ImportBatch> currentActiveBatch() {
        return currentActiveBatch(com.brand.agentpoc.tenant.domain.TenantScoped.DEFAULT_TENANT_ID);
    }

    private Optional<ImportBatch> currentActiveBatch(Long tenantId) {
        if (importBatchRepository == null) {
            return Optional.ofNullable(inMemoryActiveBatches.get(tenantId));
        }
        Optional<ImportBatch> repositoryBatch =
                importBatchRepository.findByTenantIdAndActiveTrueOrderByActivatedAtDescIdDesc(tenantId)
                        .stream().findFirst();
        return repositoryBatch.or(() -> Optional.ofNullable(inMemoryActiveBatches.get(tenantId)));
    }

    private ImportDataStatus.Batch toStatusBatch(ImportBatch batch) {
        return new ImportDataStatus.Batch(
                batch.getBatchKey(),
                Boolean.TRUE.equals(batch.getActive()),
                batch.getScopeType(),
                batch.getScopeId(),
                batch.getActivatedAt() == null ? null : batch.getActivatedAt().toString()
        );
    }
}
