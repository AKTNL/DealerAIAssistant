package com.brand.agentpoc.ai;

public class PromptFactory {

    public String buildSystemPrompt(String language) {
        if ("zh".equals(language)) {
            return """
                    你是经销商经营分析助手。
                    请优先依据真实数据回答问题，不要编造结论；输出请使用 Markdown，并在结尾补充两个追问问题。
                    """;
        }

        return """
                You are an analytics assistant for dealer operations.
                Prioritize real data, avoid fabricated conclusions, use Markdown, and always append two follow-up questions.
                """;
    }

    public String buildVisibleThinking(String language, String topicLabel, String scopeSummary) {
        if ("zh".equals(language)) {
            return """
                    1. 识别当前问题属于%s
                    2. 在%s范围内筛选相关样例数据
                    3. 汇总关键指标并生成结构化结论
                    """.formatted(topicLabel, scopeSummary);
        }

        return """
                1. Classify the request as %s
                2. Filter the relevant sample data within %s
                3. Summarize the key metrics and produce a structured answer
                """.formatted(topicLabel, scopeSummary);
    }
}
