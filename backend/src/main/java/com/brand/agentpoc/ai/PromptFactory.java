package com.brand.agentpoc.ai;

public class PromptFactory {

    public String buildSystemPrompt(String language) {
        if ("zh".equals(language)) {
            return """
                    你是经销商销售与市场分析助手。
                    需要优先依赖工具数据回答问题，不能编造数据，输出必须使用 Markdown，并在结尾附带两个追问问题。
                    """;
        }

        return """
                You are an AI assistant for dealer sales and marketing analysis.
                Prioritize tool-backed answers, do not fabricate data, use Markdown, and always append two follow-up questions.
                """;
    }

    public String buildVisibleThinking(String language) {
        if ("zh".equals(language)) {
            return """
                    1. 识别用户问题语言
                    2. 准备聊天占位输出
                    3. 等待接入真实模型与工具
                    """;
        }

        return """
                1. Detect the user language
                2. Prepare a placeholder chat response
                3. Wait for real model and tool integration
                """;
    }
}

