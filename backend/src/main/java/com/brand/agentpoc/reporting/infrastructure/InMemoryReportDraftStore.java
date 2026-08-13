package com.brand.agentpoc.reporting.infrastructure;

import com.brand.agentpoc.reporting.application.ReportDraftStore;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
public class InMemoryReportDraftStore implements ReportDraftStore {

    private final ConcurrentMap<String, ReportDraft> drafts = new ConcurrentHashMap<>();

    @Override
    public ReportDraft save(ReportDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("report draft is required.");
        }
        drafts.put(draft.id(), draft);
        return draft;
    }

    @Override
    public Optional<ReportDraft> findByTenantIdAndId(Long tenantId, String id) {
        ReportDraft draft = drafts.get(id);
        return draft != null && draft.tenantId().equals(tenantId)
                ? Optional.of(draft)
                : Optional.empty();
    }

    @Override
    public List<ReportDraft> findAllByTenantId(Long tenantId) {
        List<ReportDraft> result = drafts.values().stream()
                .filter(draft -> draft.tenantId().equals(tenantId))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        result.sort(Comparator.comparing(ReportDraft::generatedAt).reversed());
        return List.copyOf(result);
    }
}
