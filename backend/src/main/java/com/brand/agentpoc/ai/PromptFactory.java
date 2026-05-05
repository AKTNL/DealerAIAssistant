package com.brand.agentpoc.ai;

public class PromptFactory {

    public String buildSystemPrompt(String language) {
        if ("zh".equals(language)) {
            return """
                    你是经销商经营分析助手。
                    请优先依据提供给你的事实和上下文回答问题，不要编造数据或结论。
                    如果提供了“参考事实”，必须以这些事实为准，不能与其冲突。
                    输出请使用 Markdown。
                    不要暴露隐藏推理过程。
                    请在结尾保留一个 `FOLLOW_UP_QUESTIONS:` 段落，并提供 2 个编号追问。
                    """;
        }

        return """
                You are an analytics assistant for dealer operations.
                Prioritize provided facts and conversation context, avoid fabricated conclusions, and use Markdown.
                If a grounded reference is provided, treat it as the source of truth and do not contradict it.
                Do not reveal hidden reasoning.
                Always end with a `FOLLOW_UP_QUESTIONS:` section containing exactly 2 numbered follow-up questions.
                """;
    }

    public String buildVisibleThinking(String language, String topicLabel, String scopeSummary) {
        if ("zh".equals(language)) {
            return """
                    1. 识别当前问题属于 %s
                    2. 在 %s 范围内筛选相关样例数据
                    3. 汇总关键指标并生成结构化结论
                    """.formatted(topicLabel, scopeSummary);
        }

        return """
                1. Classify the request as %s
                2. Filter the relevant sample data within %s
                3. Summarize the key metrics and produce a structured answer
                """.formatted(topicLabel, scopeSummary);
    }

    public String buildGroundedModelPrompt(
            String language,
            String userMessage,
            String sessionHistory,
            String groundedReference
    ) {
        if ("zh".equals(language)) {
            return """
                    当前用户问题：
                    %s

                    最近会话上下文：
                    %s

                    参考事实（必须优先采用，不能与其冲突）：
                    %s

                    请基于以上内容输出一份自然、专业、可读性更好的回答。
                    要求：
                    1. 使用中文回答
                    2. 使用 Markdown
                    3. 可以润色表达，但不要改变事实
                    4. 不要提到“参考事实”或“内部分析引擎”这类措辞
                    5. 结尾必须保留 `FOLLOW_UP_QUESTIONS:`，并给出 2 个编号追问
                    """.formatted(userMessage, sessionHistory, groundedReference);
        }

        return """
                Current user question:
                %s

                Recent conversation context:
                %s

                Grounded reference facts (must be treated as the source of truth):
                %s

                Please produce a natural, professional, more readable answer based on the material above.
                Requirements:
                1. Answer in English
                2. Use Markdown
                3. You may improve wording, but do not alter the facts
                4. Do not mention "grounded reference" or "internal analytics engine"
                5. End with `FOLLOW_UP_QUESTIONS:` and provide exactly 2 numbered follow-up questions
                """.formatted(userMessage, sessionHistory, groundedReference);
    }

    public String buildConversationModelPrompt(String language, String userMessage, String sessionHistory) {
        if ("zh".equals(language)) {
            return """
                    当前用户问题：
                    %s

                    最近会话上下文：
                    %s

                    请直接给出有帮助的回答。
                    要求：
                    1. 使用中文
                    2. 使用 Markdown
                    3. 回答简洁、专业、自然
                    4. 不要暴露隐藏推理过程
                    5. 结尾必须保留 `FOLLOW_UP_QUESTIONS:`，并给出 2 个编号追问
                    """.formatted(userMessage, sessionHistory);
        }

        return """
                Current user question:
                %s

                Recent conversation context:
                %s

                Please provide a helpful direct answer.
                Requirements:
                1. Use English
                2. Use Markdown
                3. Keep the answer concise, professional, and natural
                4. Do not reveal hidden reasoning
                5. End with `FOLLOW_UP_QUESTIONS:` and provide exactly 2 numbered follow-up questions
                """.formatted(userMessage, sessionHistory);
    }
}
