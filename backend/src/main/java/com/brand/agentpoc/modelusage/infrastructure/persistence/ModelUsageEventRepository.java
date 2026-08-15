package com.brand.agentpoc.modelusage.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelUsageEventRepository extends JpaRepository<ModelUsageEventEntity, Long> {
    List<ModelUsageEventEntity> findByCallKey(String callKey);

    List<ModelUsageEventEntity> findByTenantIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(
            Long tenantId,
            Instant from,
            Instant to
    );

    List<ModelUsageEventEntity> findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(
            Instant from,
            Instant to
    );
}
