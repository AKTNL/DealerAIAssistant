package com.brand.agentpoc.service;

import com.brand.agentpoc.ai.LanguageDetector;
import com.brand.agentpoc.ai.PromptFactory;
import com.brand.agentpoc.dto.request.ChatRequest;
import com.brand.agentpoc.repository.DealerRepository;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

@Service
public class ChatService {

    private static final int HISTORY_LIMIT = 20;
    private static final int HISTORY_ENTRY_LIMIT = 500;
    static final int MAX_STREAMED_REPLY_CHARS = 32_000;
    private static final String STREAMED_REPLY_LIMIT_MESSAGE =
            "The streamed reply exceeded the allowed output limit.";
    private static final Pattern THINK_TAG_PATTERN = Pattern.compile("<think>(.*?)</think>", Pattern.DOTALL);
    private static final Pattern UNKNOWN_ZH_CUSTOMER_PATTERN = Pattern.compile(
            "(?:客户|客戶|顾客|顧客)\\s*[A-Za-z0-9Ａ-Ｚａ-ｚ０-９][A-Za-z0-9Ａ-Ｚａ-ｚ０-９_-]{0,8}"
    );
    private static final Pattern UNKNOWN_EN_CUSTOMER_PATTERN = Pattern.compile(
            "\\b(?:customer|client)\\s+[A-Za-z0-9][A-Za-z0-9_-]{0,8}\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern UNKNOWN_ZH_DEALER_SUFFIX_PATTERN = Pattern.compile(
            "经销商(?:不存在|没有|找不到|未知|无此|没有这个)[A-Za-z0-9_-]+"
    );
    private static final Pattern UNKNOWN_ZH_DEALER_PATTERN = Pattern.compile(
            "经销商[^\\s，。,.]{0,20}?(?:不存在|没有|找不到|未知|无此|没有这个)"
    );
    private static final Pattern UNKNOWN_EN_DEALER_PATTERN = Pattern.compile(
            "\\bdealer\\s+[A-Za-z0-9][A-Za-z0-9_-]{0,8}\\s+(?:does not exist|not found|doesn't exist|unknown|nonexistent)",
            Pattern.CASE_INSENSITIVE
    );
    private static final List<String> BUSINESS_SCOPE_KEYWORDS = List.of(
            "经销商", "门店", "店", "客户", "顾客", "经营", "业务", "销售", "销量",
            "目标", "达成", "商机", "线索", "任务", "活动", "转化", "漏斗", "跟进", "客流",
            "购买周期", "购车周期", "年龄", "赢单",
            "市场", "车型", "车款", "哪款车", "哪种车", "卖得", "畅销", "成交", "城市", "集团", "对标", "绩效", "kpi", "crm", "dealer", "dealers",
            "dealership", "store", "stores", "customer", "client", "sales", "target",
            "achievement", "opportunity", "opportunities", "lead", "leads", "task", "tasks",
            "campaign", "campaigns", "conversion", "funnel", "follow-up", "follow up",
            "benchmark", "performance", "business", "city", "model", "settings", "base url",
            "api key", "model connection", "模型配置", "配置模型"
    );
    private static final List<String> NON_BUSINESS_KEYWORDS = List.of(
            "快速排序", "排序算法", "算法", "数据结构", "leetcode", "编程题", "代码怎么写",
            "java代码", "python", "javascript", "诗", "小说", "菜谱", "天气", "旅游",
            "quick sort", "quicksort", "sorting algorithm", "algorithm", "data structure",
            "programming", "recipe", "weather", "travel"
    );

    private final SessionMemoryService sessionMemoryService;
    private final LanguageDetector languageDetector;
    private final RuleBasedAnalyticsService analyticsService;
    private final PromptFactory promptFactory;
    private final ModelConfigService modelConfigService;
    private final DealerRepository dealerRepository;
    private final SseEventWriter sseEventWriter;
    private final ChatReplyGuard replyGuard;

    public ChatService(
            SessionMemoryService sessionMemoryService,
            LanguageDetector languageDetector,
            RuleBasedAnalyticsService analyticsService,
            PromptFactory promptFactory,
            ModelConfigService modelConfigService,
            DealerRepository dealerRepository
    ) {
        this.sessionMemoryService = sessionMemoryService;
        this.languageDetector = languageDetector;
        this.analyticsService = analyticsService;
        this.promptFactory = promptFactory;
        this.modelConfigService = modelConfigService;
        this.dealerRepository = dealerRepository;
        this.sseEventWriter = new SseEventWriter();
        this.replyGuard = new ChatReplyGuard(languageDetector);
    }

    public String chat(ChatRequest request) {
        GeneratedReply generatedReply = generateReply(request);
        sessionMemoryService.addUserMessage(request.sessionId(), request.message());
        sessionMemoryService.addAssistantMessage(request.sessionId(), generatedReply.reply());
        return generatedReply.reply();
    }

    public void streamChat(ChatRequest request, OutputStream outputStream) throws IOException {
        String language = languageDetector.detectLanguage(request.message());
        boolean analyticsRequested = looksLikeAnalyticsRequest(request.message());
        String directReply = buildDirectCasualReply(request.message(), language, analyticsRequested);
        if (directReply == null) {
            if (mentionsUnknownDemoEntity(request.message())) {
                String unknownEntity = extractUnknownDemoEntityName(request.message());
                directReply = buildEntityNotFoundReply(language, unknownEntity);
            } else if (isOutOfScopeQuestion(request.message(), analyticsRequested)) {
                directReply = buildOutOfScopeReply(language);
            }
        }
        boolean configuredModel = hasConfiguredModelSettings(request);
        String traceId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        sessionMemoryService.addUserMessage(request.sessionId(), request.message());

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            final Object writeLock = new Object();
            Consumer<StepEvent> onStep = step -> {
                synchronized (writeLock) {
                    try {
                        sseEventWriter.writeStepEvent(writer, step);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            };
            try {
                if (directReply != null) {
                    sseEventWriter.writeChunkedEvent(writer, "message", directReply);
                    sessionMemoryService.addAssistantMessage(request.sessionId(), directReply);
                    sseEventWriter.writeEvent(writer, "done", "[DONE]");
                    return;
                }

                AnalyticsPlan analyticsPlan = analyticsRequested
                        ? analyticsService.plan(request.message(), language, traceId, onStep)
                        : null;
                List<String> progressMessages = resolveStreamProgressMessages(
                        language,
                        analyticsRequested,
                        configuredModel,
                        analyticsPlan
                );
                if (!progressMessages.isEmpty()) {
                    sseEventWriter.writeEvent(writer, "progress", progressMessages.getFirst());
                }
                writeAnalyticsMetadata(writer, analyticsPlan);

                if (!configuredModel) {
                    GeneratedReply generatedReply = analyticsPlan != null
                            ? new GeneratedReply(
                                    analyticsPlan.fallbackReply().trim(),
                                    analyticsPlan.progressMessages(),
                                    analyticsPlan.visibleThinking()
                            )
                            : generateReply(request, language, false);
                    sseEventWriter.writeChunkedEvent(writer, "message", generatedReply.reply());
                    sessionMemoryService.addAssistantMessage(request.sessionId(), generatedReply.reply());
                    sseEventWriter.writeEvent(writer, "done", "[DONE]");
                    return;
                }

                streamConfiguredReply(writer, request, language, analyticsRequested, analyticsPlan);
            } catch (Exception exception) {
                sseEventWriter.writeEvent(writer, "error", describeStreamFailure(exception));
            }
        }
    }

    private void streamConfiguredReply(
            BufferedWriter writer,
            ChatRequest request,
            String language,
            boolean analyticsRequested,
            AnalyticsPlan analyticsPlan
    ) throws IOException {
        ChatModel chatModel = modelConfigService.createChatModel(request);
        Prompt prompt = buildStreamingPrompt(request, language, analyticsRequested, analyticsPlan);

        if (analyticsRequested) {
            streamConfiguredAnalyticsReply(writer, request, language, analyticsPlan, chatModel, prompt);
            return;
        }

        streamConfiguredGeneralReply(writer, request, language, chatModel, prompt);
    }

    private void streamConfiguredGeneralReply(
            BufferedWriter writer,
            ChatRequest request,
            String language,
            ChatModel chatModel,
            Prompt prompt
    ) throws IOException {
        StringBuilder streamedReply = new StringBuilder();

        try {
            chatModel.stream(prompt)
                    .doOnNext(chunkResponse -> writeStreamChunk(writer, streamedReply, chunkResponse))
                    .blockLast();
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }

        String visibleReply = streamedReply.toString();
        String normalizedReply = replyGuard.ensureFollowUpQuestions(visibleReply, language, false);
        String trimmedVisibleReply = visibleReply.trim();
        String finalReply = visibleReply;
        String appendedTail = "";

        if (!normalizedReply.equals(trimmedVisibleReply)) {
            String canonicalTail = normalizedReply.substring(trimmedVisibleReply.length());
            boolean repairingPartialBlock = trimmedVisibleReply.contains("FOLLOW_UP_QUESTIONS:");
            appendedTail = repairingPartialBlock
                    ? canonicalTail
                    : alignStreamedFollowUpTail(visibleReply, canonicalTail);
            StringBuilder persistedReply = new StringBuilder(repairingPartialBlock ? trimmedVisibleReply : visibleReply);
            appendChunk(persistedReply, appendedTail);
            finalReply = persistedReply.toString();
        }

        if (hasText(appendedTail)) {
            sseEventWriter.writeChunkedEvent(writer, "message", appendedTail);
        }

        sessionMemoryService.addAssistantMessage(request.sessionId(), finalReply);
        sseEventWriter.writeEvent(writer, "done", "[DONE]");
    }

    private void streamConfiguredAnalyticsReply(
            BufferedWriter writer,
            ChatRequest request,
            String language,
            AnalyticsPlan analyticsPlan,
            ChatModel chatModel,
            Prompt prompt
    ) throws IOException {
        StringBuilder streamedReply = new StringBuilder();
        String finalReply;

        try {
            sseEventWriter.writeEvent(writer, "progress", localizedProgress(
                    language,
                    "正在调用外部模型生成经营分析报告",
                    "Calling the external model to generate the business analysis report"
            ));
            chatModel.stream(prompt)
                    .doOnNext(chunkResponse -> {
                        String reasoning = extractReasoningContent(chunkResponse);
                        String text = extractChunkText(chunkResponse);
                        if (hasText(reasoning)) {
                            String wrapped = "<think>" + reasoning.trim() + "</think>";
                            appendChunk(streamedReply, wrapped);
                            try {
                                sseEventWriter.writeChunkedEvent(writer, "message", wrapped);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                        if (text != null && !text.isEmpty()) {
                            appendChunk(streamedReply, text);
                        }
                    })
                    .blockLast();

            String rawReply = streamedReply.toString();
            sseEventWriter.writeEvent(writer, "progress", localizedProgress(
                    language,
                    "模型生成完成，正在校验数据一致性",
                    "Model generation complete, validating data consistency"
            ));

            String candidateReply = replyGuard.ensureFollowUpQuestions(stripThinkTags(rawReply), language, true);
            if (replyGuard.isValidAnalyticsReply(candidateReply, analyticsPlan.fallbackReply())) {
                finalReply = candidateReply;
                sseEventWriter.writeEvent(writer, "progress", localizedProgress(
                        language,
                        "数据一致性校验通过，正在返回最终报告",
                        "Data consistency validation passed, returning the final report"
                ));
            } else {
                finalReply = analyticsPlan.fallbackReply().trim();
                sseEventWriter.writeEvent(writer, "progress", localizedProgress(
                        language,
                        "模型输出未通过数据一致性校验，已回退到规则分析报告",
                        "Model output failed data consistency validation, falling back to the rule-based analysis report"
                ));
            }
        } catch (UncheckedIOException uncheckedIoException) {
            throw uncheckedIoException.getCause();
        } catch (IOException ioException) {
            throw ioException;
        } catch (Exception exception) {
            if (isStreamLimitFailure(exception)) {
                throw new IllegalStateException(STREAMED_REPLY_LIMIT_MESSAGE, exception);
            }
            finalReply = analyticsPlan.fallbackReply().trim();
            sseEventWriter.writeEvent(writer, "progress", localizedProgress(
                    language,
                    "模型调用异常，已回退到规则分析报告",
                    "Model call failed, falling back to the rule-based analysis report"
            ));
        }

        if (finalReply.length() > MAX_STREAMED_REPLY_CHARS) {
            throw new IllegalStateException(STREAMED_REPLY_LIMIT_MESSAGE);
        }
        sseEventWriter.writeChunkedEvent(writer, "message", finalReply);
        sessionMemoryService.addAssistantMessage(request.sessionId(), finalReply);
        sseEventWriter.writeEvent(writer, "done", "[DONE]");
    }

    private void writeAnalyticsMetadata(BufferedWriter writer, AnalyticsPlan analyticsPlan) throws IOException {
        if (analyticsPlan != null) {
            sseEventWriter.writeAnalysisMetadataEvent(writer, analyticsPlan.metadata());
        }
    }

    private Prompt buildStreamingPrompt(
            ChatRequest request,
            String language,
            boolean analyticsRequested,
            AnalyticsPlan analyticsPlan
    ) {
        String sessionHistory = formatSessionHistory(request.sessionId(), language);
        String userPrompt = analyticsRequested
                ? promptFactory.buildGroundedModelPrompt(
                        language,
                        request.message(),
                        sessionHistory,
                        analyticsPlan.groundedReference()
                )
                : promptFactory.buildConversationModelPrompt(language, request.message(), sessionHistory);

        return new Prompt(
                new SystemMessage(promptFactory.buildSystemPrompt(language)),
                new UserMessage(userPrompt)
        );
    }

    private String extractChunkText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private String extractReasoningContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        try {
            var output = response.getResult().getOutput();
            var method = output.getClass().getMethod("getReasoningContent");
            var result = method.invoke(output);
            return result != null ? result.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private void writeStreamChunk(BufferedWriter writer, StringBuilder accumulator, ChatResponse chunkResponse) {
        String reasoning = extractReasoningContent(chunkResponse);
        String text = extractChunkText(chunkResponse);

        if (hasText(reasoning)) {
            String wrapped = "<think>" + reasoning.trim() + "</think>";
            appendChunk(accumulator, wrapped);
            try {
                sseEventWriter.writeChunkedEvent(writer, "message", wrapped);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        if (text != null && !text.isEmpty()) {
            appendChunk(accumulator, text);
            try {
                sseEventWriter.writeChunkedEvent(writer, "message", text);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    private void appendChunk(StringBuilder accumulator, String chunk) {
        if (accumulator.length() + chunk.length() > MAX_STREAMED_REPLY_CHARS) {
            throw new IllegalStateException(STREAMED_REPLY_LIMIT_MESSAGE);
        }
        accumulator.append(chunk);
    }

    private String localizedProgress(String language, String zh, String en) {
        return "zh".equals(language) ? zh : en;
    }

    private boolean isStreamLimitFailure(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (STREAMED_REPLY_LIMIT_MESSAGE.equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    private String alignStreamedFollowUpTail(String visibleReply, String canonicalTail) {
        if (!hasText(canonicalTail)) {
            return "";
        }

        String followUpBlock = canonicalTail.startsWith("\n\n")
                ? canonicalTail.substring(2)
                : canonicalTail;
        return streamedFollowUpSeparator(visibleReply) + followUpBlock;
    }

    private String streamedFollowUpSeparator(String reply) {
        String trailingWhitespace = reply.substring(reply.stripTrailing().length())
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        long trailingLineBreaks = trailingWhitespace.chars()
                .filter(character -> character == '\n')
                .count();

        if (trailingLineBreaks >= 2) {
            return "";
        }
        if (trailingLineBreaks == 1) {
            return "\n";
        }
        return "\n\n";
    }

    private GeneratedReply generateReply(ChatRequest request) {
        String language = languageDetector.detectLanguage(request.message());
        boolean analyticsRequested = looksLikeAnalyticsRequest(request.message());
        return generateReply(request, language, analyticsRequested);
    }

    private GeneratedReply generateReply(ChatRequest request, String language, boolean analyticsRequested) {
        String directReply = buildDirectCasualReply(request.message(), language, analyticsRequested);
        if (directReply != null) {
            return new GeneratedReply(directReply, List.of(), "");
        }
        if (mentionsUnknownDemoEntity(request.message())) {
            String unknownEntity = extractUnknownDemoEntityName(request.message());
            return new GeneratedReply(buildEntityNotFoundReply(language, unknownEntity), List.of(), "");
        }
        if (isOutOfScopeQuestion(request.message(), analyticsRequested)) {
            return new GeneratedReply(buildOutOfScopeReply(language), List.of(), "");
        }

        if (!hasConfiguredModelSettings(request)) {
            if (analyticsRequested) {
                AnalyticsPlan plan = analyticsService.plan(request.message(), language);
                return new GeneratedReply(
                        plan.fallbackReply().trim(),
                        plan.progressMessages(),
                        plan.visibleThinking()
                );
            }

            return new GeneratedReply(
                    buildModelNotConfiguredReply(language),
                    buildConfigurationProgressMessages(language),
                    buildConfigurationThinking(language)
            );
        }

        if (analyticsRequested) {
            return generateAnalyticsReply(request, language);
        }
        return generateGeneralReply(request, language);
    }

    private GeneratedReply generateGeneralReply(ChatRequest request, String language) {
        ChatModel chatModel = modelConfigService.createChatModel(request);

        try {
            String reply = callConfiguredModel(
                    chatModel,
                    request.sessionId(),
                    request.message(),
                    language
            );
            return new GeneratedReply(
                    replyGuard.ensureFollowUpQuestions(reply, language, false),
                    buildModelProgressMessages(language, false),
                    buildModelVisibleThinking(language, false)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Chat request failed with the provided model configuration.", exception);
        }
    }

    private GeneratedReply generateAnalyticsReply(ChatRequest request, String language) {
        AnalyticsPlan plan = analyticsService.plan(request.message(), language);
        ChatModel chatModel = modelConfigService.createChatModel(request);

        try {
            String polishedReply = callGroundedAnalyticsModel(
                    chatModel,
                    request.sessionId(),
                    request.message(),
                    language,
                    plan
            );
            String normalizedReply = replyGuard.ensureFollowUpQuestions(polishedReply, language, true);
            if (!replyGuard.isValidAnalyticsReply(normalizedReply, plan.fallbackReply())) {
                normalizedReply = plan.fallbackReply().trim();
            }
            return new GeneratedReply(normalizedReply, plan.progressMessages(), plan.visibleThinking());
        } catch (Exception exception) {
            return new GeneratedReply(
                    plan.fallbackReply().trim(),
                    plan.progressMessages(),
                    plan.visibleThinking()
            );
        }
    }

    private String describeStreamFailure(Exception exception) {
        Throwable current = exception;

        while (current.getCause() != null && hasText(current.getCause().getMessage())) {
            current = current.getCause();
        }

        if (hasText(current.getMessage())) {
            return current.getMessage();
        }

        if (hasText(exception.getMessage())) {
            return exception.getMessage();
        }

        return "Chat request failed with the provided model configuration.";
    }

    private String callConfiguredModel(
            ChatModel chatModel,
            String sessionId,
            String message,
            String language
    ) {
        String sessionHistory = formatSessionHistory(sessionId, language);
        String userPrompt = promptFactory.buildConversationModelPrompt(language, message, sessionHistory);

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

    private String callGroundedAnalyticsModel(
            ChatModel chatModel,
            String sessionId,
            String message,
            String language,
            AnalyticsPlan plan
    ) {
        String sessionHistory = formatSessionHistory(sessionId, language);
        String userPrompt = promptFactory.buildGroundedModelPrompt(
                language,
                message,
                sessionHistory,
                plan.groundedReference()
        );

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

    private List<String> resolveStreamProgressMessages(
            String language,
            boolean analyticsRequested,
            boolean configuredModel,
            AnalyticsPlan analyticsPlan
    ) {
        if (configuredModel) {
            return buildModelProgressMessages(language, analyticsRequested);
        }
        if (analyticsPlan != null) {
            return analyticsPlan.progressMessages();
        }
        return buildConfigurationProgressMessages(language);
    }

    private List<String> buildModelProgressMessages(String language, boolean analyticsRequested) {
        if ("zh".equals(language)) {
            if (analyticsRequested) {
                return List.of(
                        "正在识别分析主题",
                        "正在调用经营分析工具获取事实",
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
                    "Collecting facts through the analytics tools",
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
                    1. 读取当前问题与最近会话上下文，确认用户真正想解决的事情
                    2. 判断是否需要依赖已有事实、配置状态或历史对话继续回答
                    3. 调用已配置模型生成初稿，并检查是否遗漏关键信息
                    4. 补齐追问建议，整理成最终可读回复后返回前端
                    """;
        }

        return """
                1. Read the current question and recent conversation context
                2. Confirm the key intent, constraints, and any useful prior facts
                3. Call the configured compatible model to draft the reply
                4. Check for missing context, add follow-up questions, and return the final response
                """;
    }

    private String buildModelVisibleThinking(String language, boolean analyticsRequested) {
        if (analyticsRequested) {
            if ("zh".equals(language)) {
                return """
                        1. 识别当前分析主题
                        2. 确认分析范围与关键指标口径
                        3. 调用相关数据工具获取事实
                        4. 提炼主要信号、差距与风险点
                        5. 汇总结果并生成结构化回复
                        """;
            }

            return """
                    1. Identify the current analysis theme
                    2. Confirm the working scope and KPI lens
                    3. Gather facts with the relevant data tools
                    4. Extract the strongest signal, gap, and risk points
                    5. Synthesize the results into a structured reply
                    """;
        }

        return buildConversationThinking(language);
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
                    你好，我现在还没连接外部模型。你可以先到 Settings 里填写 Base URL、API Key 和 Model；如果你想先看业务分析，我也可以基于内置样例数据继续回答。

                    FOLLOW_UP_QUESTIONS:
                    1. 先带我配置模型连接
                    2. 直接问一个经营分析问题
                    """;
        }

        return """
                Hi! No external model is configured yet. Open Settings and fill in Base URL, API Key, and Model. If you want business analysis first, I can still answer analytics questions with the built-in sample data.

                FOLLOW_UP_QUESTIONS:
                1. Help me configure the model connection
                2. Let's start with a sample analytics question
                """;
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

    private boolean hasConfiguredModelSettings(ChatRequest request) {
        return request != null
                && hasText(request.baseUrl())
                && hasText(request.apiKey())
                && hasText(request.model());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String buildDirectCasualReply(String message, String language, boolean analyticsRequested) {
        if (analyticsRequested || !looksLikeCasualGreetingOrIntro(message)) {
            return null;
        }

        if ("zh".equals(language)) {
            return """
                    你好，我是经销商 AI 分析助手。你可以问我目标达成、商机漏斗、销售跟进、市场活动、线索来源等业务问题。

                    FOLLOW_UP_QUESTIONS:
                    1. 哪些门店目标达成率最低？
                    2. 分析一下各门店的商机漏斗转化情况
                    """.trim();
        }

        return """
                Hi, I'm the dealer AI analytics assistant. You can ask about target achievement, opportunity funnels, sales follow-up, campaigns, lead sources, and related business questions.

                FOLLOW_UP_QUESTIONS:
                1. Which dealers have the lowest target achievement rate?
                2. Analyze opportunity funnel conversion by dealer
                """.trim();
    }

    private boolean looksLikeCasualGreetingOrIntro(String message) {
        String normalized = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return false;
        }

        String compact = normalized.replaceAll("[\\s\\p{Punct}，。！？、；：‘’“”（）【】《》]+", "");
        if (compact.isBlank()) {
            return false;
        }

        if (List.of(
                "你好", "您好", "嗨", "哈喽", "哈啰", "hello", "hi", "hey",
                "你是谁", "你是誰", "介绍一下系统", "介绍系统", "系统介绍",
                "你能做什么", "你可以做什么", "你能干什么"
        ).contains(compact)) {
            return true;
        }

        if ((compact.startsWith("你好") || compact.startsWith("您好")) && compact.length() <= 4) {
            return true;
        }

        if (compact.contains("介绍") && compact.contains("系统") && compact.length() <= 20) {
            return true;
        }

        if (compact.contains("你是谁") || compact.contains("你是誰")) {
            return true;
        }

        String asciiOnly = compact.replaceAll("[^a-z]", "");
        if ((asciiOnly.startsWith("hello") || asciiOnly.startsWith("hi") || asciiOnly.startsWith("hey"))
                && (asciiOnly.contains("whoareyou")
                        || asciiOnly.contains("whatareyou")
                        || asciiOnly.contains("whatcanyoudo")
                        || asciiOnly.contains("introduceyourself"))) {
            return true;
        }
        return List.of(
                "hello", "hi", "hey", "hithere", "hellothere",
                "whoareyou", "whatareyou", "introduceyourself",
                "introducethesystem", "whatcanyoudo"
        ).contains(asciiOnly);
    }

    private boolean isOutOfScopeQuestion(String message, boolean analyticsRequested) {
        String normalized = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return true;
        }
        if (looksLikeCasualGreetingOrIntro(message)) {
            return false;
        }

        boolean businessRelated = BUSINESS_SCOPE_KEYWORDS.stream().anyMatch(normalized::contains);
        if (analyticsRequested && businessRelated) {
            return false;
        }
        if (NON_BUSINESS_KEYWORDS.stream().anyMatch(normalized::contains) && !businessRelated) {
            return true;
        }

        return !businessRelated;
    }

    private static final Pattern IMPLICIT_ZH_DEALER_PATTERN = Pattern.compile(
            "(?:经销商|门店|店)\\s*(?:名叫?|叫|是)\\s*([A-Za-z0-9\\u4e00-\\u9fff\\u3105-\\u3129_-]{1,20})"
    );
    private static final Pattern UNKNOWN_GENERIC_ENTITY_PATTERN = Pattern.compile(
            "(?:客户|客戶|顾客|顧客|经销商|门店)\\s*[A-Za-z0-9Ａ-Ｚａ-ｚ０-９_-]{1,30}(?:\\s*(?:的|之))?\\s*(?:目标|商机|线索|任务|活动|达成|转化)"
    );

    private boolean mentionsUnknownDemoEntity(String message) {
        return extractUnknownDemoEntityName(message) != null;
    }

    private String extractUnknownDemoEntityName(String message) {
        if (!hasText(message)) {
            return null;
        }

        Matcher zhMatcher = UNKNOWN_ZH_CUSTOMER_PATTERN.matcher(message);
        if (zhMatcher.find()) {
            return zhMatcher.group().replaceAll("\\s+", "");
        }

        Matcher enMatcher = UNKNOWN_EN_CUSTOMER_PATTERN.matcher(message);
        if (enMatcher.find()) {
            return enMatcher.group().trim().replaceAll("\\s+", " ");
        }

        Matcher zhDealerSuffixMatcher = UNKNOWN_ZH_DEALER_SUFFIX_PATTERN.matcher(message);
        if (zhDealerSuffixMatcher.find()) {
            return zhDealerSuffixMatcher.group().replaceAll("\\s+", "");
        }

        Matcher zhDealerMatcher = UNKNOWN_ZH_DEALER_PATTERN.matcher(message);
        if (zhDealerMatcher.find()) {
            return zhDealerMatcher.group().replaceAll("\\s+", "");
        }

        Matcher enDealerMatcher = UNKNOWN_EN_DEALER_PATTERN.matcher(message);
        if (enDealerMatcher.find()) {
            return enDealerMatcher.group().trim().replaceAll("\\s+", " ");
        }

        Matcher implicitDealerMatcher = IMPLICIT_ZH_DEALER_PATTERN.matcher(message);
        if (implicitDealerMatcher.find()) {
            String dealerName = implicitDealerMatcher.group(1);
            if (isGenericInterrogativeEntity(dealerName)) {
                return null;
            }
            return isKnownDealer(dealerName) ? null : explicitExtract("经销商" + dealerName);
        }

        Matcher genericUnknownMatcher = UNKNOWN_GENERIC_ENTITY_PATTERN.matcher(message);
        if (genericUnknownMatcher.find()) {
            String group = genericUnknownMatcher.group();
            String entityName = group.replaceAll("(?:的|之)?\\s*(?:目标|商机|线索|任务|活动|达成|转化).*$", "");
            if (!hasText(entityName)) {
                return null;
            }
            if (isGenericInterrogativeEntity(entityName)) {
                return null;
            }
            return isKnownDealer(entityName.replaceAll("^经销商|^门店|^店", "")) ? null : explicitExtract(entityName);
        }

        return null;
    }

    private boolean isGenericInterrogativeEntity(String entityName) {
        if (!hasText(entityName)) {
            return true;
        }
        String normalized = entityName.trim()
                .replaceAll("\\s+", "")
                .replaceFirst("^(?:\\u7ecf\\u9500\\u5546|\\u95e8\\u5e97|\\u5e97)", "");
        if (normalized.isBlank()) {
            return true;
        }
        return containsAny(normalized,
                "\u8c01",
                "\u54ea\u4e2a",
                "\u54ea\u5bb6",
                "\u54ea\u4e9b",
                "\u6700\u591a",
                "\u6700\u9ad8",
                "\u6700\u4f4e");
    }

    private boolean isKnownDealer(String dealerName) {
        if (!hasText(dealerName)) {
            return true;
        }
        String normalizedName = dealerName.trim();
        return dealerRepository.findAll().stream()
                .anyMatch(dealer -> {
                    String code = dealer.getDealerCode();
                    String name = dealer.getDealerName();
                    return (code != null && normalizedName.contains(code))
                            || (name != null && (name.contains(normalizedName) || normalizedName.contains(name)));
                });
    }

    private String explicitExtract(String text) {
        return text == null ? null : text.trim().replaceAll("\\s+", "");
    }

    private String buildOutOfScopeReply(String language) {
        if ("zh".equals(language)) {
            return """
                    这个问题超出经销商 AI 分析助手的业务范围。我只能回答经销商经营分析、系统介绍和问候类问题，不回答算法、通用知识或其他非业务问题。

                    FOLLOW_UP_QUESTIONS:
                    1. 哪些门店目标达成率最低？
                    2. 分析一下各门店的商机漏斗转化情况
                    """.trim();
        }

        return """
                This question is outside the dealer AI analytics assistant's business scope. I can answer dealer operations analysis, system introduction, and greeting questions, but not algorithms, general knowledge, or other non-business topics.

                FOLLOW_UP_QUESTIONS:
                1. Which dealers have the lowest target achievement rate?
                2. Analyze opportunity funnel conversion by dealer
                """.trim();
    }

    private String buildEntityNotFoundReply(String language, String entityName) {
        String displayName = hasText(entityName) ? entityName.trim() : ("zh".equals(language) ? "该实体" : "that entity");
        if ("zh".equals(language)) {
            return """
                    未找到“%s”。当前演示数据中没有这个实体，所以我不会继续生成经营分析或补充其他经营数据。

                    FOLLOW_UP_QUESTIONS:
                    1. 换一个已导入的经销商或门店再查
                    2. 查看当前样例数据的门店目标达成情况
                    """.formatted(displayName).trim();
        }

        return """
                I couldn't find "%s" in the current demo data, so I won't generate an operations analysis or return other business data for it.

                FOLLOW_UP_QUESTIONS:
                1. Try an imported dealer or store name
                2. Show target achievement for the current sample dealers
                """.formatted(displayName).trim();
    }

    private String stripThinkTags(String text) {
        if (text == null) {
            return "";
        }
        String result = THINK_TAG_PATTERN.matcher(text).replaceAll("");
        result = result.replace("<think>", "");
        result = result.replace("</think>", "");
        return result.trim();
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
                "经销商", "门店", "目标", "达成", "销量", "销售", "卖得", "畅销", "成交", "商机", "线索", "任务", "活动", "转化",
                "分析", "复盘", "对比", "表现", "趋势", "集团", "城市", "车型", "最低", "最高",
                "车款", "哪款车", "哪种车", "购买周期", "购车周期", "赢单", "客户", "年龄",
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

    List<String> buildContextualFollowUps(String language, String reply) {
        return replyGuard.buildContextualFollowUps(language, reply);
    }

    List<String> validateFollowUpRelevance(
            String reply,
            List<String> followUps,
            String language,
            List<String> fallbackDefaults
    ) {
        return replyGuard.validateFollowUpRelevance(reply, followUps, language, fallbackDefaults);
    }

    List<String> extractFollowUpsFromReply(String reply) {
        return replyGuard.extractFollowUpsFromReply(reply);
    }

    List<String> extractTopicKeywords(String reply, String language) {
        return replyGuard.extractTopicKeywords(reply, language);
    }

    boolean isStronglyRelevant(String followUp, List<String> topicKeywords) {
        return replyGuard.isStronglyRelevant(followUp, topicKeywords);
    }

    private record GeneratedReply(String reply, List<String> progressMessages, String visibleThinking) {
    }
}
