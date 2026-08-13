package com.brand.agentpoc.knowledge.application;

import com.brand.agentpoc.knowledge.domain.KnowledgeChunk;
import com.brand.agentpoc.knowledge.domain.KnowledgeQuery;
import com.brand.agentpoc.knowledge.domain.KnowledgeSearchResult;
import java.util.List;

public interface KnowledgeIndex {

    void replaceAll(List<KnowledgeChunk> chunks);

    default void replaceAll(Long tenantId, List<KnowledgeChunk> chunks) {
        replaceAll(chunks);
    }

    KnowledgeSearchResult search(KnowledgeQuery query);

    default KnowledgeSearchResult search(KnowledgeQuery query, Long tenantId) {
        return search(query);
    }

    boolean isAvailable();
}
