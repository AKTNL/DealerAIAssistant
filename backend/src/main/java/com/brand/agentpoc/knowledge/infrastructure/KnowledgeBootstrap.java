package com.brand.agentpoc.knowledge.infrastructure;

import com.brand.agentpoc.knowledge.application.KnowledgeDocumentChunker;
import com.brand.agentpoc.knowledge.application.KnowledgeDocumentSource;
import com.brand.agentpoc.knowledge.application.KnowledgeIndex;
import com.brand.agentpoc.knowledge.domain.KnowledgeChunk;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBootstrap.class);

    private final KnowledgeDocumentSource documentSource;
    private final KnowledgeIndex knowledgeIndex;
    private final KnowledgeDocumentChunker documentChunker;

    public KnowledgeBootstrap(
            KnowledgeDocumentSource documentSource,
            KnowledgeIndex knowledgeIndex,
            KnowledgeDocumentChunker documentChunker
    ) {
        this.documentSource = documentSource;
        this.knowledgeIndex = knowledgeIndex;
        this.documentChunker = documentChunker;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        List<KnowledgeChunk> chunks = documentChunker.split(documentSource.load());
        knowledgeIndex.replaceAll(chunks);
        log.info("Knowledge catalog initialized: chunks={}", chunks.size());
    }
}
