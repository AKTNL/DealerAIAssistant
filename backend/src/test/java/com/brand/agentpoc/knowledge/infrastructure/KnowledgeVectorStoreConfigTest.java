package com.brand.agentpoc.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.brand.agentpoc.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class KnowledgeVectorStoreConfigTest {

    private final KnowledgeVectorStoreConfig config = new KnowledgeVectorStoreConfig();

    @Test
    void buildsAValidatedTextIdPgVectorStore() {
        AppProperties properties = new AppProperties();

        PgVectorStore vectorStore = config.knowledgeVectorStore(
                mock(JdbcTemplate.class),
                mock(EmbeddingModel.class),
                properties
        );

        assertThat(vectorStore).isNotNull();
        assertThat(vectorStore.getDistanceType()).isEqualTo(PgVectorStore.PgDistanceType.COSINE_DISTANCE);
    }

    @Test
    void rejectsUnsafeIdentifiersDimensionsAndThresholds() {
        AppProperties properties = new AppProperties();
        properties.getKnowledge().setTableName("knowledge;drop table dealers");

        assertThatThrownBy(() -> build(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SQL identifiers");

        properties.getKnowledge().setTableName("knowledge_vector_store");
        properties.getKnowledge().setDimensions(2_001);
        assertThatThrownBy(() -> build(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimensions");

        properties.getKnowledge().setDimensions(1_536);
        properties.getKnowledge().setSimilarityThreshold(Double.NaN);
        assertThatThrownBy(() -> build(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold");
    }

    @Test
    void pgvectorModeFailsWhenNoEmbeddingModelIsConfigured() {
        new ApplicationContextRunner()
                .withUserConfiguration(KnowledgeVectorStoreConfig.class)
                .withPropertyValues("app.knowledge.vector-store=pgvector")
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(AppProperties.class, AppProperties::new)
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasRootCauseInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class));
    }

    private PgVectorStore build(AppProperties properties) {
        return config.knowledgeVectorStore(
                mock(JdbcTemplate.class),
                mock(EmbeddingModel.class),
                properties
        );
    }
}
