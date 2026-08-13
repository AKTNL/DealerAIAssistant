package com.brand.agentpoc.knowledge.infrastructure;

import com.brand.agentpoc.knowledge.application.KnowledgeDocumentChunker;
import com.brand.agentpoc.knowledge.application.KnowledgeDocumentSource;
import com.brand.agentpoc.knowledge.application.KnowledgeIndex;
import com.brand.agentpoc.knowledge.domain.KnowledgeChunk;
import java.util.List;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantRepository;
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
    private final TenantRepository tenantRepository;

    public KnowledgeBootstrap(
            KnowledgeDocumentSource documentSource,
            KnowledgeIndex knowledgeIndex,
            KnowledgeDocumentChunker documentChunker,
            TenantRepository tenantRepository
    ) {
        this.documentSource = documentSource;
        this.knowledgeIndex = knowledgeIndex;
        this.documentChunker = documentChunker;
        this.tenantRepository = tenantRepository;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        List<KnowledgeChunk> chunks = documentChunker.split(documentSource.load());
        List<Long> tenantIds = tenantRepository.findByEnabledTrueOrderByIdAsc().stream()
                .map(com.brand.agentpoc.tenant.infrastructure.persistence.TenantEntity::getId)
                .toList();
        for (Long tenantId : tenantIds) {
            knowledgeIndex.replaceAll(tenantId, chunks);
        }
        log.info("Knowledge catalog initialized: tenantCount={}, chunksPerTenant={}", tenantIds.size(), chunks.size());
    }
}
