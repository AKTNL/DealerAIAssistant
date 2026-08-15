package com.brand.agentpoc.modelusage.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ModelBudgetPolicyRepository extends JpaRepository<ModelBudgetPolicyEntity, Long> {
    List<ModelBudgetPolicyEntity> findByTenantId(Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ModelBudgetPolicyEntity> findByTenantIdOrderByIdAsc(Long tenantId);
}
