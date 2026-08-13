package com.brand.agentpoc.repository;

import com.brand.agentpoc.entity.ImportBatch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    List<ImportBatch> findByActiveTrueOrderByActivatedAtDescIdDesc();

    List<ImportBatch> findByTenantIdAndActiveTrueOrderByActivatedAtDescIdDesc(Long tenantId);

    List<ImportBatch> findByBatchKeyIgnoreCase(String batchKey);

    List<ImportBatch> findByTenantIdAndBatchKeyIgnoreCase(Long tenantId, String batchKey);
}
