package com.brand.agentpoc.reporting.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportCollaborationEventRepository extends JpaRepository<ReportCollaborationEventEntity, Long> {

    List<ReportCollaborationEventEntity> findByTenantIdAndReportDraftIdOrderByCreatedAtAscIdAsc(
            Long tenantId,
            String reportDraftId
    );
}
