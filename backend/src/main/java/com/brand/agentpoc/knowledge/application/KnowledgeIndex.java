package com.brand.agentpoc.knowledge.application;

import com.brand.agentpoc.knowledge.domain.KnowledgeChunk;
import com.brand.agentpoc.knowledge.domain.KnowledgeQuery;
import com.brand.agentpoc.knowledge.domain.KnowledgeSearchResult;
import java.util.List;

public interface KnowledgeIndex {

    void replaceAll(List<KnowledgeChunk> chunks);

    KnowledgeSearchResult search(KnowledgeQuery query);

    boolean isAvailable();
}
