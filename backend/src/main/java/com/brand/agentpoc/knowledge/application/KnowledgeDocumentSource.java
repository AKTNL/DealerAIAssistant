package com.brand.agentpoc.knowledge.application;

import com.brand.agentpoc.knowledge.domain.KnowledgeDocument;
import java.util.List;

public interface KnowledgeDocumentSource {

    List<KnowledgeDocument> load();
}
