package com.brand.agentpoc.reporting.application;

import com.brand.agentpoc.reporting.domain.ReportDraft;
import java.util.List;
import java.util.Optional;

public interface ReportDraftStore {

    ReportDraft save(ReportDraft draft);

    Optional<ReportDraft> findByTenantIdAndId(Long tenantId, String id);

    List<ReportDraft> findAllByTenantId(Long tenantId);
}
