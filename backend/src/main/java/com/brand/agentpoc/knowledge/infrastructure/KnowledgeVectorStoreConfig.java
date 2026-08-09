package com.brand.agentpoc.knowledge.infrastructure;

import com.brand.agentpoc.config.AppProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIdType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class KnowledgeVectorStoreConfig {

    @Bean("knowledgeVectorStore")
    @ConditionalOnProperty(prefix = "app.knowledge", name = "vector-store", havingValue = "pgvector")
    public PgVectorStore knowledgeVectorStore(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            AppProperties appProperties
    ) {
        AppProperties.Knowledge properties = appProperties.getKnowledge();
        validate(properties);
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .schemaName(properties.getSchemaName())
                .vectorTableName(properties.getTableName())
                .idType(PgIdType.TEXT)
                .dimensions(properties.getDimensions())
                .distanceType(PgDistanceType.COSINE_DISTANCE)
                .indexType(PgIndexType.HNSW)
                .initializeSchema(false)
                .vectorTableValidationsEnabled(true)
                .build();
    }

    private void validate(AppProperties.Knowledge properties) {
        if (!properties.getSchemaName().matches("[A-Za-z_][A-Za-z0-9_]*")
                || !properties.getTableName().matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Knowledge schema and table names must be SQL identifiers.");
        }
        if (properties.getDimensions() < 1 || properties.getDimensions() > 2_000) {
            throw new IllegalArgumentException("Knowledge embedding dimensions must be between 1 and 2000.");
        }
        double threshold = properties.getSimilarityThreshold();
        if (!Double.isFinite(threshold) || threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Knowledge similarity threshold must be between 0 and 1.");
        }
    }
}
