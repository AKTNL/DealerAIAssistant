package com.brand.agentpoc.service;

import com.brand.agentpoc.ai.LanguageDetector;
import com.brand.agentpoc.ai.PromptFactory;
import com.brand.agentpoc.config.OpenAiProperties;
import com.brand.agentpoc.dto.request.ChatRequest;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int HISTORY_LIMIT = 8;
    private static final int HISTORY_ENTRY_LIMIT = 500;

    private final SessionMemoryService sessionMemoryService;
    private final LanguageDetector languageDetector;
    private final RuleBasedAnalyticsService analyticsService;
    private final PromptFactory promptFactory;
    private final OpenAiProperties openAiProperties;
    private final ObjectProvider<ChatModel> chatModelProvider;

    public ChatService(
            SessionMemoryService sessionMemoryService,
            LanguageDetector languageDetector,
            RuleBasedAnalyticsService analyticsService,
            PromptFactory promptFactory,
            OpenAiProperties openAiProperties,
            ObjectProvider<ChatModel> chatModelProvider
    ) {
        this.sessionMemoryService = sessionMemoryService;
        this.languageDetector = languageDetector;
        this.analyticsService = analyticsService;
        this.promptFactory = promptFactory;
        this.openAiProperties = openAiProperties;
        this.chatModelProvider = chatModelProvider;
    }

    public String chat(ChatRequest request) {
        GeneratedReply generatedReply = generateReply(request);
        sessionMemoryService.addUserMessage(request.sessionId(), request.message());
        sessionMemoryService.addAssistantMessage(request.sessionId(), generatedReply.reply());
        return generatedReply.reply();
    }

    public void streamChat(ChatRequest request, OutputStream outputStream) throws IOException {
        GeneratedReply generatedReply = generateReply(request);
        sessionMemoryService.addUserMessage(request.sessionId(), request.message());

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            for (String progress : generatedReply.progressMessages()) {
                writeEvent(writer, "progress", progress);
            }
            writeEvent(writer, "message", "<think>" + generatedReply.visibleThinking() + "</think>");
            writeEvent(writer, "message", generatedReply.reply());
            writeEvent(writer, "done", "[DONE]");
            sessionMemoryService.addAssistantMessage(request.sessionId(), generatedReply.reply());
        }
    }

    private GeneratedReply generateReply(ChatRequest request) {
        String language = languageDetector.detectLanguage(request.message());
        boolean analyticsRequested = looksLikeAnalyticsRequest(request.message());
        RuleBasedAnalyticsService.AnalyticsResponse analyticsResponse = analyticsRequested
                ? analyticsService.analyze(request.message(), language)
                : null;

        ChatModel chatModel = isModelConfigured() ? chatModelProvider.getIfAvailable() : null;
        if (chatModel != null) {
            try {
                String reply = callConfiguredModel(
                        chatModel,
                        request.sessionId(),
                        request.message(),
                        language,
                        analyticsResponse
                );
                return new GeneratedReply(
                        ensureFollowUpQuestions(reply, language, analyticsRequested),
                        buildModelProgressMessages(language, analyticsRequested),
                        analyticsRequested && analyticsResponse != null
                                ? analyticsResponse.visibleThinking()
                                : buildConversationThinking(language)
                );
            } catch (Exception exception) {
                log.warn(
                        "Configured model call failed. Falling back to local response. model={}, baseUrl={}",
                        openAiProperties.getModel(),
                        openAiProperties.getBaseUrl(),
                        exception
                );
            }
        }

        if (analyticsResponse != null) {
            return new GeneratedReply(
                    analyticsResponse.reply(),
                    analyticsResponse.progressMessages(),
                    analyticsResponse.visibleThinking()
            );
        }

        return new GeneratedReply(
                buildModelNotConfiguredReply(language),
                buildConfigurationProgressMessages(language),
                buildConfigurationThinking(language)
        );
    }

    private String callConfiguredModel(
            ChatModel chatModel,
            String sessionId,
            String message,
            String language,
            RuleBasedAnalyticsService.AnalyticsResponse analyticsResponse
    ) {
        String sessionHistory = formatSessionHistory(sessionId, language);
        String userPrompt = analyticsResponse != null
                ? promptFactory.buildGroundedModelPrompt(language, message, sessionHistory, analyticsResponse.reply())
                : promptFactory.buildConversationModelPrompt(language, message, sessionHistory);

        String reply = ChatClient.create(chatModel)
                .prompt()
                .system(promptFactory.buildSystemPrompt(language))
                .user(userPrompt)
                .call()
                .content();

        if (reply == null || reply.isBlank()) {
            throw new IllegalStateException("Configured model returned an empty response.");
        }

        return reply.trim();
    }

    private List<String> buildModelProgressMessages(String language, boolean analyticsRequested) {
        if ("zh".equals(language)) {
            if (analyticsRequested) {
                return List.of(
                        "正在识别分析主题",
                        "正在整理样例数据中的参考事实",
                        "正在调用已配置模型生成回复"
                );
            }

            return List.of(
                    "正在整理当前会话上下文",
                    "正在调用已配置模型",
                    "正在生成最终回复"
            );
        }

        if (analyticsRequested) {
            return List.of(
                    "Identifying the analysis theme",
                    "Preparing grounded facts from the sample data",
                    "Calling the configured model"
            );
        }

        return List.of(
                "Preparing the current conversation context",
                "Calling the configured model",
                "Generating the final reply"
        );
    }

    private List<String> buildConfigurationProgressMessages(String language) {
        if ("zh".equals(language)) {
            return List.of(
                    "正在检查聊天模型配置",
                    "当前未发现可用模型配置",
                    "正在返回配置指引"
            );
        }

        return List.of(
                "Checking the chat model configuration",
                "No usable model configuration was found",
                "Returning configuration guidance"
        );
    }

    private String buildConversationThinking(String language) {
        if ("zh".equals(language)) {
            return """
                    1. 读取当前问题和最近会话上下文
                    2. 调用已配置的兼容模型生成回答
                    3. 补齐追问并返回给前端
                    """;
        }

        return """
                1. Read the current question and recent conversation context
                2. Call the configured compatible model
                3. Append follow-up questions and return the response
                """;
    }

    private String buildConfigurationThinking(String language) {
        if ("zh".equals(language)) {
            return """
                    1. 检查当前聊天能力是否已配置
                    2. 发现模型相关配置还不可用
                    3. 返回可直接补齐的配置项说明
                    """;
        }

        return """
                1. Check whether chat model capability is configured
                2. Detect that model-related settings are not usable yet
                3. Return the configuration items needed to enable chat
                """;
    }

    private String buildModelNotConfiguredReply(String language) {
        if ("zh".equals(language)) {
            return """
                    ## 当前状态
                    当前聊天模型尚未配置完成，因此暂时不能执行通用模型对话。

                    ## 至少需要配置
                    - `SERVER_PORT`
                    - `APP_ACCESS_KEY`
                    - `APP_API_KEY`
                    - `OPENAI_API_KEY`
                    - `OPENAI_BASE_URL`
                    - `OPENAI_MODEL`
                    - `APP_EXCEL_PATH`

                    ## 说明
                    - 其中聊天真正依赖的是 `OPENAI_API_KEY`、`OPENAI_BASE_URL` 和 `OPENAI_MODEL`
                    - `APP_EXCEL_PATH` 现在可以先保留默认值，等你拿到样例数据后再接
                    - 如果你问的是经营分析类问题，当前系统仍会优先回退到内置样例数据分析

                    FOLLOW_UP_QUESTIONS:
                    1. 要不要我直接帮你整理一份可复制的 `application.yml` 配置模板？
                    2. 还是先继续用当前内置样例数据验证分析类聊天接口？
                    """;
        }

        return """
                ## Current Status
                The chat model is not configured yet, so general model-based conversation is not available right now.

                ## Required Configuration
                - `SERVER_PORT`
                - `APP_ACCESS_KEY`
                - `APP_API_KEY`
                - `OPENAI_API_KEY`
                - `OPENAI_BASE_URL`
                - `OPENAI_MODEL`
                - `APP_EXCEL_PATH`

                ## Notes
                - The real chat capability depends on `OPENAI_API_KEY`, `OPENAI_BASE_URL`, and `OPENAI_MODEL`
                - `APP_EXCEL_PATH` can stay on the default value until you get the sample workbook
                - For analytics-style questions, the system can still fall back to the built-in sample dataset

                FOLLOW_UP_QUESTIONS:
                1. Do you want me to generate a copy-ready `application.yml` template next?
                2. Or should we keep validating analytics questions with the built-in sample data first?
                """;
    }

    private String ensureFollowUpQuestions(String reply, String language, boolean analyticsRequested) {
        String trimmed = reply == null ? "" : reply.trim();
        if (trimmed.isBlank()) {
            throw new IllegalStateException("Reply is blank after model generation.");
        }

        if (trimmed.contains("FOLLOW_UP_QUESTIONS:")) {
            return trimmed;
        }

        List<String> defaults = analyticsRequested
                ? defaultAnalyticsFollowUps(language)
                : defaultGeneralFollowUps(language);

        return """
                %s

                FOLLOW_UP_QUESTIONS:
                1. %s
                2. %s
                """.formatted(trimmed, defaults.getFirst(), defaults.getLast()).trim();
    }

    private List<String> defaultAnalyticsFollowUps(String language) {
        if ("zh".equals(language)) {
            return List.of(
                    "你想继续看这个问题对应的商机、线索还是任务状态？",
                    "要不要我再按城市、门店或车型细分对比一层？"
            );
        }

        return List.of(
                "Do you want to drill into the related opportunities, leads, or tasks next?",
                "Should I break this down further by city, dealer, or product model?"
        );
    }

    private List<String> defaultGeneralFollowUps(String language) {
        if ("zh".equals(language)) {
            return List.of(
                    "你想先继续配置模型连接，还是先验证现有聊天链路？",
                    "要不要我顺手把这 7 个配置项整理成可直接复制的模板？"
            );
        }

        return List.of(
                "Do you want to finish the model connection first or validate the current chat flow first?",
                "Should I turn those seven configuration items into a copy-ready template?"
        );
    }

    private String formatSessionHistory(String sessionId, String language) {
        List<String> history = sessionMemoryService.getMessages(sessionId);
        if (history.isEmpty()) {
            return "zh".equals(language) ? "无" : "None";
        }

        int startIndex = Math.max(history.size() - HISTORY_LIMIT, 0);
        List<String> recentHistory = history.subList(startIndex, history.size());
        String userLabel = "zh".equals(language) ? "用户" : "User";
        String assistantLabel = "zh".equals(language) ? "助手" : "Assistant";

        return recentHistory.stream()
                .map(entry -> formatHistoryEntry(entry, userLabel, assistantLabel))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("zh".equals(language) ? "无" : "None");
    }

    private String formatHistoryEntry(String entry, String userLabel, String assistantLabel) {
        String normalizedEntry = entry == null ? "" : entry.trim();
        if (normalizedEntry.startsWith("USER:")) {
            return "- " + userLabel + ": " + shorten(normalizedEntry.substring(5));
        }
        if (normalizedEntry.startsWith("ASSISTANT:")) {
            return "- " + assistantLabel + ": " + shorten(normalizedEntry.substring(10));
        }
        return "- " + shorten(normalizedEntry);
    }

    private String shorten(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= HISTORY_ENTRY_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, HISTORY_ENTRY_LIMIT) + "...";
    }

    private boolean isModelConfigured() {
        return hasText(openAiProperties.getApiKey())
                && hasText(openAiProperties.getBaseUrl())
                && hasText(openAiProperties.getModel())
                && !"changeme".equalsIgnoreCase(openAiProperties.getApiKey().trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean looksLikeAnalyticsRequest(String message) {
        String normalized = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return false;
        }

        if (normalized.matches(".*\\b[a-z]{2}\\d{3}\\b.*")) {
            return true;
        }

        return containsAny(
                normalized,
                "经销商", "门店", "目标", "达成", "销量", "商机", "线索", "任务", "活动", "转化",
                "分析", "复盘", "对比", "表现", "趋势", "集团", "城市", "车型", "最低", "最高",
                "dealer", "dealers", "target", "achievement", "sales", "opportunity", "opportunities",
                "lead", "leads", "task", "tasks", "campaign", "campaigns", "benchmark", "funnel",
                "conversion", "city", "model", "performance", "trend", "lowest", "highest", "compare"
        );
    }

    private boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void writeEvent(BufferedWriter writer, String event, String data) throws IOException {
        writer.write("event: " + event);
        writer.newLine();
        for (String line : data.split("\\R", -1)) {
            writer.write("data: " + line);
            writer.newLine();
        }
        writer.newLine();
        writer.flush();
    }

    private record GeneratedReply(String reply, List<String> progressMessages, String visibleThinking) {
    }
}
