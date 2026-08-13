package com.brand.agentpoc.knowledge.application;

import com.brand.agentpoc.knowledge.domain.KnowledgeQuery;
import com.brand.agentpoc.knowledge.domain.KnowledgeSearchResult;

public class KnowledgeService {

    public static final int DEFAULT_TOP_K = 4;
    public static final int MAX_TOP_K = 8;
    public static final int MAX_QUERY_LENGTH = 500;

    private final KnowledgeIndex knowledgeIndex;

    public KnowledgeService(KnowledgeIndex knowledgeIndex) {
        this.knowledgeIndex = knowledgeIndex;
    }

    public KnowledgeSearchResult retrieve(String query, Integer topK) {
        return retrieve(query, topK, com.brand.agentpoc.tenant.domain.TenantScoped.DEFAULT_TENANT_ID);
    }

    public KnowledgeSearchResult retrieve(String query, Integer topK, Long tenantId) {
        if (tenantId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant context is required.");
        }
        String normalizedQuery = validateQuery(query);
        int normalizedTopK = topK == null ? DEFAULT_TOP_K : topK;
        if (normalizedTopK < 1 || normalizedTopK > MAX_TOP_K) {
            throw new IllegalArgumentException("topK must be between 1 and " + MAX_TOP_K + ".");
        }
        if (!knowledgeIndex.isAvailable()) {
            throw new IllegalStateException("Knowledge index is not available.");
        }
        return knowledgeIndex.search(new KnowledgeQuery(normalizedQuery, normalizedTopK), tenantId);
    }

    private String validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank.");
        }
        String normalized = query.trim();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("query exceeds the maximum length.");
        }
        return normalized;
    }
}
