package com.brand.agentpoc.ai;

public class PromptFactory {

    public String buildSystemPrompt(String language) {
        if ("zh".equals(language)) {
            return """
                    你是经销商经营分析助手。
                    对于经销商经营分析类问题，请优先调用可用工具获取事实，不要跳过工具直接编造数据或结论。
                    可用工具包括：searchDealers、queryOpportunities、queryCampaigns、queryTasks、queryTargets、queryLeads。
                    如果已经通过上下文或工具拿到了事实，必须以这些事实为准，不能与其冲突。
                    输出请使用 Markdown。
                    不要暴露隐藏推理过程。
                    不要暴露内部工具名、接口名或实现细节。
                    如果数据不足，请明确说明数据不足。
                    请在结尾保留一个 `FOLLOW_UP_QUESTIONS:` 段落，并提供 2 个编号追问。
                    """;
        }

        return """
                You are an analytics assistant for dealer operations.
                For dealer operations analysis questions, use the available tools before answering.
                Available tools include searchDealers, queryOpportunities, queryCampaigns, queryTasks, queryTargets, and queryLeads.
                Prioritize tool-backed facts and conversation context, avoid fabricated conclusions, and use Markdown.
                If facts are already available from context or tools, treat them as the source of truth and do not contradict them.
                Do not reveal hidden reasoning.
                Do not expose internal tool names, API names, or implementation details.
                If the available data is insufficient, say so clearly.
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

                    如果当前问题涉及经销商经营分析、门店表现、商机、线索、任务、活动、目标或对标，请先调用相关工具获取事实，再给出回答。
                    如果只是普通问候或配置类问题，可以直接回答。
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

                If the current question is about dealer analysis, store performance, opportunities, leads, tasks, campaigns, targets, or benchmarking, call the relevant tools before answering.
                If it is only a greeting or configuration question, you may answer directly.
                Requirements:
                1. Use English
                2. Use Markdown
                3. Keep the answer concise, professional, and natural
                4. Do not reveal hidden reasoning
                5. End with `FOLLOW_UP_QUESTIONS:` and provide exactly 2 numbered follow-up questions
                """.formatted(userMessage, sessionHistory);
    }
}
