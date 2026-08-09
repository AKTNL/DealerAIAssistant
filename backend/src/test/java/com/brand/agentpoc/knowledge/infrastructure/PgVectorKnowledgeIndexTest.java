package com.brand.agentpoc.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.knowledge.domain.KnowledgeChunk;
import com.brand.agentpoc.knowledge.domain.KnowledgeQuery;
import com.brand.agentpoc.knowledge.domain.KnowledgeSearchResult;
import com.brand.agentpoc.knowledge.domain.KnowledgeType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class PgVectorKnowledgeIndexTest {

    @Test
    void replacesOnlyBundledKnowledgeAndPreservesCitationMetadata() {
        VectorStore vectorStore = mock(VectorStore.class);
        PgVectorKnowledgeIndex index = new PgVectorKnowledgeIndex(vectorStore, 0.45);

        index.replaceAll(List.of(chunk()));

        verify(vectorStore).delete("catalog == 'bundled'");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documents = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documents.capture());
        assertThat(documents.getValue()).singleElement().satisfies(document -> {
            assertThat(document.getId()).isEqualTo("kpi:2026.08:1");
            assertThat(document.getText()).contains("赢单商机数");
            assertThat(document.getMetadata())
                    .containsEntry("catalog", "bundled")
                    .containsEntry("documentId", "kpi")
                    .containsEntry("version", "2026.08")
                    .containsEntry("section", "目标达成率");
        });
        assertThat(index.isAvailable()).isTrue();
    }

    @Test
    void appliesTopKThresholdAndCatalogFilterToSemanticSearch() {
        VectorStore vectorStore = mock(VectorStore.class);
        PgVectorKnowledgeIndex index = new PgVectorKnowledgeIndex(vectorStore, 0.45);
        index.replaceAll(List.of(chunk()));
        Document match = Document.builder()
                .id("kpi:2026.08:1")
                .text("赢单商机数除以目标数。")
                .metadata(Map.of(
                        "documentId", "kpi",
                        "title", "目标达成率口径",
                        "type", "KPI_DEFINITION",
                        "version", "2026.08",
                        "source", "knowledge/kpi.md",
                        "section", "目标达成率"
                ))
                .score(0.82)
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(match));

        KnowledgeSearchResult result = index.search(new KnowledgeQuery("目标达成率如何计算", 3));

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        assertThat(request.getValue().getQuery()).isEqualTo("目标达成率如何计算");
        assertThat(request.getValue().getTopK()).isEqualTo(3);
        assertThat(request.getValue().getSimilarityThreshold()).isEqualTo(0.45);
        assertThat(request.getValue().hasFilterExpression()).isTrue();
        assertThat(result.noMatch()).isFalse();
        assertThat(result.hits()).singleElement().satisfies(hit -> {
            assertThat(hit.documentId()).isEqualTo("kpi");
            assertThat(hit.score()).isEqualTo(0.82);
            assertThat(hit.source()).isEqualTo("knowledge/kpi.md");
        });
    }

    @Test
    void refusesSearchBeforeTheStartupReplacementCompletes() {
        PgVectorKnowledgeIndex index = new PgVectorKnowledgeIndex(mock(VectorStore.class), 0.45);

        assertThatThrownBy(() -> index.search(new KnowledgeQuery("目标", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not been initialized");
    }

    private KnowledgeChunk chunk() {
        return new KnowledgeChunk(
                "kpi:2026.08:1",
                "kpi",
                "目标达成率口径",
                KnowledgeType.KPI_DEFINITION,
                "2026.08",
                "knowledge/kpi.md",
                "目标达成率",
                1,
                "赢单商机数除以目标数。"
        );
    }
}
