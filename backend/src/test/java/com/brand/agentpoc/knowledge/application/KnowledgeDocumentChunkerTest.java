package com.brand.agentpoc.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brand.agentpoc.knowledge.domain.KnowledgeChunk;
import com.brand.agentpoc.knowledge.domain.KnowledgeDocument;
import com.brand.agentpoc.knowledge.domain.KnowledgeType;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentChunkerTest {

    @Test
    void preservesCitationMetadataAndHeadingBoundaries() {
        KnowledgeDocument document = document("""
                # 目标达成率

                赢单数除以目标数。

                ## 数据限制

                目标缺失时不得伪造结果。
                """);

        List<KnowledgeChunk> chunks = new KnowledgeDocumentChunker().split(List.of(document));

        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(KnowledgeChunk::section)
                .containsExactly("目标达成率", "数据限制");
        assertThat(chunks).extracting(KnowledgeChunk::chunkId)
                .containsExactly(
                        "kpi-target:2026.08:目标达成率:1",
                        "kpi-target:2026.08:数据限制:1"
                );
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.source()).isEqualTo("knowledge/kpi.md");
            assertThat(chunk.version()).isEqualTo("2026.08");
            assertThat(chunk.type()).isEqualTo(KnowledgeType.KPI_DEFINITION);
        });
    }

    @Test
    void splitsLongSectionsDeterministically() {
        KnowledgeDocument document = document("# 长内容\n\n" + "达".repeat(450));
        KnowledgeDocumentChunker chunker = new KnowledgeDocumentChunker(200);

        List<KnowledgeChunk> first = chunker.split(List.of(document));
        List<KnowledgeChunk> second = chunker.split(List.of(document));

        assertThat(first).hasSize(3);
        assertThat(first).isEqualTo(second);
        assertThat(first).extracting(KnowledgeChunk::chunkId)
                .containsExactly(
                        "kpi-target:2026.08:长内容:1",
                        "kpi-target:2026.08:长内容:2",
                        "kpi-target:2026.08:长内容:3"
                );
    }

    @Test
    void rejectsDocumentsWithoutIndexableContent() {
        KnowledgeDocument document = document("# 只有标题");

        assertThatThrownBy(() -> new KnowledgeDocumentChunker().split(List.of(document)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no indexable content");
    }

    private KnowledgeDocument document(String content) {
        return new KnowledgeDocument(
                "kpi-target",
                "指标口径",
                KnowledgeType.KPI_DEFINITION,
                "2026.08",
                "knowledge/kpi.md",
                content
        );
    }
}
