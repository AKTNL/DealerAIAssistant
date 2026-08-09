package com.brand.agentpoc.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brand.agentpoc.knowledge.domain.KnowledgeChunk;
import com.brand.agentpoc.knowledge.domain.KnowledgeQuery;
import com.brand.agentpoc.knowledge.domain.KnowledgeSearchResult;
import com.brand.agentpoc.knowledge.domain.KnowledgeType;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryKnowledgeIndexTest {

    @Test
    void ranksRelevantChineseKnowledgeAndReturnsCitations() {
        InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
        index.replaceAll(List.of(
                chunk("kpi:1", "kpi", "目标达成率口径", KnowledgeType.KPI_DEFINITION,
                        "目标达成率", "赢单商机数除以目标数，目标缺失时不得伪造。", 1),
                chunk("sop:1", "sop", "商机跟进 SOP", KnowledgeType.SALES_SOP,
                        "跟进时效", "高意向商机应优先确认客户需求和下一步动作。", 1)
        ));

        KnowledgeSearchResult result = index.search(new KnowledgeQuery("目标达成率如何计算", 2));

        assertThat(result.noMatch()).isFalse();
        assertThat(result.hits()).isNotEmpty();
        assertThat(result.hits().getFirst().documentId()).isEqualTo("kpi");
        assertThat(result.hits().getFirst().source()).isEqualTo("knowledge/kpi.md");
        assertThat(result.hits().getFirst().version()).isEqualTo("2026.08");
        assertThat(result.hits().getFirst().excerpt()).contains("赢单商机数");
    }

    @Test
    void returnsExplicitNoMatchInsteadOfUnrelatedContent() {
        InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
        index.replaceAll(List.of(chunk(
                "kpi:1",
                "kpi",
                "目标口径",
                KnowledgeType.KPI_DEFINITION,
                "目标达成率",
                "赢单数除以目标数。",
                1
        )));

        KnowledgeSearchResult result = index.search(new KnowledgeQuery("员工差旅报销标准", 4));

        assertThat(result.noMatch()).isTrue();
        assertThat(result.hits()).isEmpty();
    }

    @Test
    void refusesSearchBeforeInitializationAndDuplicateChunkIds() {
        InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
        KnowledgeChunk chunk = chunk(
                "kpi:1", "kpi", "目标口径", KnowledgeType.KPI_DEFINITION,
                "目标达成率", "赢单数除以目标数。", 1
        );

        assertThatThrownBy(() -> index.search(new KnowledgeQuery("目标", 1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> index.replaceAll(List.of(chunk, chunk)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    private KnowledgeChunk chunk(
            String chunkId,
            String documentId,
            String title,
            KnowledgeType type,
            String section,
            String content,
            int chunkIndex
    ) {
        return new KnowledgeChunk(
                chunkId,
                documentId,
                title,
                type,
                "2026.08",
                "knowledge/" + documentId + ".md",
                section,
                chunkIndex,
                content
        );
    }
}
