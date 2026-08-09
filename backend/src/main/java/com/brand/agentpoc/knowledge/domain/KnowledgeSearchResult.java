package com.brand.agentpoc.knowledge.domain;

import java.util.List;

public record KnowledgeSearchResult(String query, List<KnowledgeHit> hits, boolean noMatch) {

    public KnowledgeSearchResult {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank.");
        }
        query = query.trim();
        hits = hits == null ? List.of() : List.copyOf(hits);
        if (noMatch != hits.isEmpty()) {
            throw new IllegalArgumentException("noMatch must agree with the hit list.");
        }
    }

    public static KnowledgeSearchResult from(String query, List<KnowledgeHit> hits) {
        List<KnowledgeHit> safeHits = hits == null ? List.of() : List.copyOf(hits);
        return new KnowledgeSearchResult(query, safeHits, safeHits.isEmpty());
    }
}
