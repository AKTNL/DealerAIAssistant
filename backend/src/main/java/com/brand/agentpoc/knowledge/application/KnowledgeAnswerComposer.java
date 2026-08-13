package com.brand.agentpoc.knowledge.application;

import com.brand.agentpoc.knowledge.domain.KnowledgeHit;
import com.brand.agentpoc.knowledge.domain.KnowledgeSearchResult;

public class KnowledgeAnswerComposer {

    private final KnowledgeService knowledgeService;

    public KnowledgeAnswerComposer(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    public String compose(String query, String language) {
        return render(knowledgeService.retrieve(query, null), language);
    }

    public String compose(String query, String language, Long tenantId) {
        return render(knowledgeService.retrieve(query, null, tenantId), language);
    }

    private String render(KnowledgeSearchResult result, String language) {
        if (result.noMatch()) {
            return noMatch(language);
        }
        return "zh".equals(language) ? composeChinese(result) : composeEnglish(result);
    }

    private String composeChinese(KnowledgeSearchResult result) {
        StringBuilder answer = new StringBuilder("## 知识库检索结果\n\n");
        answer.append("找到以下可追溯资料片段：\n");
        int index = 1;
        for (KnowledgeHit hit : result.hits()) {
            answer.append("\n### ").append(index).append(". ").append(hit.title()).append("\n\n")
                    .append("- 来源：`").append(hit.source()).append("`\n")
                    .append("- 版本：`").append(hit.version()).append("`\n")
                    .append("- 章节：").append(hit.section()).append("\n")
                    .append("- 片段 ID：`").append(hit.chunkId()).append("`\n\n")
                    .append("> ").append(hit.excerpt().replace("\n", "\n> ")).append("\n");
            index++;
        }
        answer.append("\n以上资料只用于解释制度、流程和口径；当前 KPI 数值仍以结构化指标服务为准。");
        return answer.toString();
    }

    private String composeEnglish(KnowledgeSearchResult result) {
        StringBuilder answer = new StringBuilder("## Knowledge Retrieval Results\n\n");
        answer.append("The following traceable source excerpts were found:\n");
        int index = 1;
        for (KnowledgeHit hit : result.hits()) {
            answer.append("\n### ").append(index).append(". ").append(hit.title()).append("\n\n")
                    .append("- Source: `").append(hit.source()).append("`\n")
                    .append("- Version: `").append(hit.version()).append("`\n")
                    .append("- Section: ").append(hit.section()).append("\n")
                    .append("- Chunk ID: `").append(hit.chunkId()).append("`\n\n")
                    .append("> ").append(hit.excerpt().replace("\n", "\n> ")).append("\n");
            index++;
        }
        answer.append("\nThese sources explain policies, procedures, and definitions only. Current KPI values remain governed by the structured metrics service.");
        return answer.toString();
    }

    private String noMatch(String language) {
        if ("zh".equals(language)) {
            return "知识库中没有命中可引用的文档。我不会依据常识补写制度、流程或指标口径，请补充正式资料或换一个更具体的业务问题。";
        }
        return "No citable knowledge document matched this question. I will not invent a policy, procedure, or KPI definition; provide an approved source or ask a more specific business question.";
    }
}
