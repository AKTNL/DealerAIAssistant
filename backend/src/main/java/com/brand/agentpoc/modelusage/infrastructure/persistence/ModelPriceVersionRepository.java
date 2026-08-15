package com.brand.agentpoc.modelusage.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelPriceVersionRepository extends JpaRepository<ModelPriceVersionEntity, Long> {
    List<ModelPriceVersionEntity> findByTenantIdOrderByEffectiveFromDescIdDesc(Long tenantId);

    List<ModelPriceVersionEntity> findByTenantIdAndProviderKeyIgnoreCaseAndModelNameIgnoreCaseAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
            Long tenantId,
            String providerKey,
            String modelName,
            Instant effectiveFrom
    );
}
