package com.brand.agentpoc.knowledge.infrastructure;

import com.brand.agentpoc.knowledge.application.KnowledgeAnswerComposer;
import com.brand.agentpoc.knowledge.application.KnowledgeDocumentChunker;
import com.brand.agentpoc.knowledge.application.KnowledgeIndex;
import com.brand.agentpoc.knowledge.application.KnowledgeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeModuleConfig {

    @Bean
    public KnowledgeDocumentChunker knowledgeDocumentChunker() {
        return new KnowledgeDocumentChunker();
    }

    @Bean
    public KnowledgeService knowledgeService(KnowledgeIndex knowledgeIndex) {
        return new KnowledgeService(knowledgeIndex);
    }

    @Bean
    public KnowledgeAnswerComposer knowledgeAnswerComposer(KnowledgeService knowledgeService) {
        return new KnowledgeAnswerComposer(knowledgeService);
    }
}
