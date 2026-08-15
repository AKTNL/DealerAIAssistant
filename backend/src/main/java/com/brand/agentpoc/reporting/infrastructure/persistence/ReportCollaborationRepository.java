package com.brand.agentpoc.reporting.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportCollaborationRepository extends JpaRepository<ReportCollaborationEntity, Long> {

    Optional<ReportCollaborationEntity> findByTenantIdAndReportDraftId(Long tenantId, String reportDraftId);

    List<ReportCollaborationEntity> findByTenantIdAndReportDraftIdIn(
            Long tenantId,
            Collection<String> reportDraftIds
    );
}
