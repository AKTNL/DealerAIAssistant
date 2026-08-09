package com.brand.agentpoc.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.knowledge.domain.KnowledgeHit;
import com.brand.agentpoc.knowledge.domain.KnowledgeSearchResult;
import com.brand.agentpoc.knowledge.domain.KnowledgeType;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeAnswerComposerTest {

    @Test
    void rendersVersionedCitationsAndKeepsStructuredMetricsAuthoritative() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        when(knowledgeService.retrieve("目标达成率口径", null)).thenReturn(KnowledgeSearchResult.from(
                "目标达成率口径",
                List.of(new KnowledgeHit(
                        "kpi:2026.08:1",
                        "kpi",
                        "目标达成率口径",
                        KnowledgeType.KPI_DEFINITION,
                        "2026.08",
                        "knowledge/kpi.md",
                        "目标达成率",
                        "赢单商机数除以目标数。",
                        0.9
                ))
        ));

        String answer = new KnowledgeAnswerComposer(knowledgeService).compose("目标达成率口径", "zh");

        assertThat(answer)
                .contains("knowledge/kpi.md", "2026.08", "目标达成率", "赢单商机数")
                .contains("当前 KPI 数值仍以结构化指标服务为准");
    }

    @Test
    void makesNoMatchExplicitWithoutInventingKnowledge() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        when(knowledgeService.retrieve("差旅政策", null))
                .thenReturn(KnowledgeSearchResult.from("差旅政策", List.of()));

        String answer = new KnowledgeAnswerComposer(knowledgeService).compose("差旅政策", "zh");

        assertThat(answer).contains("没有命中", "不会依据常识补写");
    }
}
