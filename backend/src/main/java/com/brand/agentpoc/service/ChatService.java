package com.brand.agentpoc.service;

import com.brand.agentpoc.ai.LanguageDetector;
import com.brand.agentpoc.ai.PromptFactory;
import com.brand.agentpoc.dto.request.ChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private static final Pattern SECOND_LEVEL_HEADING_LINE_PATTERN = Pattern.compile("(?m)^##[^\\r\\n]*$");
    private static final Pattern DATA_SUPPORT_HEADING_PATTERN = Pattern.compile(
            "^##[\\s\\h]*(?:\\d+[\\s\\h\\.、]*)?(?:Data Support|数据支撑)[\\s\\h]*$",
            Pattern.MULTILINE
    );
    private static final Pattern FORBIDDEN_SUMMARY_HEADING_PATTERN = Pattern.compile(
            "^##[\\s\\h]*(?:\\d+[\\s\\h\\.、]*)?(?:数据汇总|Data Summary|数据总览|Data Overview|摘要段落|Summary)[\\s\\h]*$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );
    private static final Pattern UNKNOWN_ZH_CUSTOMER_PATTERN = Pattern.compile(
            "(?:客户|客戶|顾客|顧客)\\s*[A-Za-z0-9Ａ-Ｚａ-ｚ０-９][A-Za-z0-9Ａ-Ｚａ-ｚ０-９_-]{0,8}"
    );
    private static final Pattern UNKNOWN_EN_CUSTOMER_PATTERN = Pattern.compile(
            "\\b(?:customer|client)\\s+[A-Za-z0-9][A-Za-z0-9_-]{0,8}\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final List<String> BUSINESS_SCOPE_KEYWORDS = List.of(
            "经销商", "门店", "店", "客户", "顾客", "经营", "业务", "销售", "销量",
            "目标", "达成", "商机", "线索", "任务", "活动", "转化", "漏斗", "跟进", "客流",
            "市场", "车型", "城市", "集团", "对标", "绩效", "kpi", "crm", "dealer", "dealers",
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

    private static final Map<String, List<String>> METRIC_TERMS = Map.of(
            "zh", List.of("达成率", "转化率", "商机", "线索", "任务", "活动", "ROI", "销量",
                    "赢单", "流失", "漏斗", "目标", "活跃度", "参与度", "跟进", "时效", "逾期"),
            "en", List.of("achievement", "conversion", "opportunity", "lead", "task", "campaign",
                    "ROI", "sales", "win", "drop-off", "funnel", "target", "activity", "participation",
                    "follow-up", "turnaround", "overdue")
    );

    private static final Map<String, List<String>> SCENARIO_TERMS = Map.of(
            "zh", List.of("目标达成", "商机漏斗", "转化分析", "销售跟进", "活动效果", "市场活动",
                    "经营对标", "对标分析", "线索来源", "自然流量", "经营活跃度", "门店活跃"),
            "en", List.of("target achievement", "opportunity funnel", "conversion analysis",
                    "sales follow-up", "campaign performance", "dealer benchmark", "lead source",
                    "organic traffic", "business activity", "dealer activity")
    );

    private final SessionMemoryService sessionMemoryService;
    private final LanguageDetector languageDetector;
    private final RuleBasedAnalyticsService analyticsService;
    private final PromptFactory promptFactory;
    private final ModelConfigService modelConfigService;

    public ChatService(
            SessionMemoryService sessionMemoryService,
            LanguageDetector languageDetector,
            RuleBasedAnalyticsService analyticsService,
            PromptFactory promptFactory,
            ModelConfigService modelConfigService
    ) {
        this.sessionMemoryService = sessionMemoryService;
        this.languageDetector = languageDetector;
        this.analyticsService = analyticsService;
        this.promptFactory = promptFactory;
        this.modelConfigService = modelConfigService;
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
                        writeStepEvent(writer, step);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            };
            try {
                if (directReply != null) {
                    writeChunkedEvent(writer, "message", directReply);
                    sessionMemoryService.addAssistantMessage(request.sessionId(), directReply);
                    writeEvent(writer, "done", "[DONE]");
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
                    writeEvent(writer, "progress", progressMessages.getFirst());
                }

                if (!configuredModel) {
                    GeneratedReply generatedReply = analyticsPlan != null
                            ? new GeneratedReply(
                                    analyticsPlan.fallbackReply().trim(),
                                    analyticsPlan.progressMessages(),
                                    analyticsPlan.visibleThinking()
                            )
                            : generateReply(request, language, false);
                    writeChunkedEvent(writer, "message", generatedReply.reply());
                    sessionMemoryService.addAssistantMessage(request.sessionId(), generatedReply.reply());
                    writeEvent(writer, "done", "[DONE]");
                    return;
                }

                streamConfiguredReply(writer, request, language, analyticsRequested, analyticsPlan);
            } catch (Exception exception) {
                writeEvent(writer, "error", describeStreamFailure(exception));
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
        String normalizedReply = ensureFollowUpQuestions(visibleReply, language, false);
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
            writeChunkedEvent(writer, "message", appendedTail);
        }

        sessionMemoryService.addAssistantMessage(request.sessionId(), finalReply);
        writeEvent(writer, "done", "[DONE]");
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
            writeEvent(writer, "progress", localizedProgress(
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
                                writeChunkedEvent(writer, "message", wrapped);
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
            writeEvent(writer, "progress", localizedProgress(
                    language,
                    "模型生成完成，正在校验数据一致性",
                    "Model generation complete, validating data consistency"
            ));

            String candidateReply = ensureFollowUpQuestions(stripThinkTags(rawReply), language, true);
            if (isValidAnalyticsReply(candidateReply, analyticsPlan.fallbackReply())) {
                finalReply = candidateReply;
                writeEvent(writer, "progress", localizedProgress(
                        language,
                        "数据一致性校验通过，正在返回最终报告",
                        "Data consistency validation passed, returning the final report"
                ));
            } else {
                finalReply = analyticsPlan.fallbackReply().trim();
                writeEvent(writer, "progress", localizedProgress(
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
            writeEvent(writer, "progress", localizedProgress(
                    language,
                    "模型调用异常，已回退到规则分析报告",
                    "Model call failed, falling back to the rule-based analysis report"
            ));
        }

        if (finalReply.length() > MAX_STREAMED_REPLY_CHARS) {
            throw new IllegalStateException(STREAMED_REPLY_LIMIT_MESSAGE);
        }
        writeChunkedEvent(writer, "message", finalReply);
        sessionMemoryService.addAssistantMessage(request.sessionId(), finalReply);
        writeEvent(writer, "done", "[DONE]");
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
                writeChunkedEvent(writer, "message", wrapped);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        if (text != null && !text.isEmpty()) {
            appendChunk(accumulator, text);
            try {
                writeChunkedEvent(writer, "message", text);
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
                    ensureFollowUpQuestions(reply, language, false),
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
            String normalizedReply = ensureFollowUpQuestions(polishedReply, language, true);
            if (!isValidAnalyticsReply(normalizedReply, plan.fallbackReply())) {
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

    private boolean isValidAnalyticsReply(String reply, String fallbackReply) {
        String normalized = reply == null ? "" : reply.trim();
        if (normalized.isBlank()) {
            return false;
        }

        if (containsForbiddenSummarySection(normalized)) {
            return false;
        }

        String language = languageDetector.detectLanguage(fallbackReply);
        if (!matchesExpectedHeadingSequence(normalized, language)) {
            return false;
        }

        String replyTable = extractDataSupportTable(normalized);
        String fallbackTable = extractDataSupportTable(fallbackReply);
        if (!hasText(replyTable) || !hasText(fallbackTable)) {
            return false;
        }

        return normalizeBlock(replyTable).equals(normalizeBlock(fallbackTable))
                && hasExactlyTwoFollowUpQuestions(normalized);
    }

    private boolean matchesExpectedHeadingSequence(String reply, String language) {
        List<String> headings = extractSecondLevelHeadingLines(reply);
        List<Pattern> expected = "zh".equals(language) ? zhHeadingPatterns() : enHeadingPatterns();
        if (headings.size() != expected.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            if (!expected.get(index).matcher(headings.get(index)).matches()) {
                return false;
            }
        }
        return true;
    }

    private List<String> extractSecondLevelHeadingLines(String reply) {
        Matcher matcher = SECOND_LEVEL_HEADING_LINE_PATTERN.matcher(reply);
        List<String> headings = new ArrayList<>();
        while (matcher.find()) {
            headings.add(matcher.group().trim());
        }
        return headings;
    }

    private List<Pattern> zhHeadingPatterns() {
        return List.of(
                headingPattern(1, "接口调用链"),
                headingPattern(2, "核心结论"),
                headingPattern(3, "数据支撑"),
                headingPattern(4, "经营分析"),
                headingPattern(5, "问题诊断与解决"),
                headingPattern(6, "改进建议")
        );
    }

    private List<Pattern> enHeadingPatterns() {
        return List.of(
                headingPattern(1, "Interface Call Chain"),
                headingPattern(2, "Conclusion"),
                headingPattern(3, "Data Support"),
                headingPattern(4, "Short Analysis"),
                headingPattern(5, "Problem Diagnosis & Solutions"),
                headingPattern(6, "Improvement Suggestions")
        );
    }

    private Pattern headingPattern(int optionalNumber, String headingText) {
        return Pattern.compile(
                "^##[\\s\\h]*(?:%d[\\s\\h\\.、]*)?%s[\\s\\h]*$"
                        .formatted(optionalNumber, Pattern.quote(headingText)),
                Pattern.MULTILINE
        );
    }

    private boolean containsForbiddenSummarySection(String reply) {
        return reply != null && FORBIDDEN_SUMMARY_HEADING_PATTERN.matcher(reply).find();
    }

    private String extractDataSupportTable(String reply) {
        Matcher supportHeading = DATA_SUPPORT_HEADING_PATTERN.matcher(reply);
        if (!supportHeading.find()) {
            return null;
        }

        int start = supportHeading.end();
        Matcher nextHeading = SECOND_LEVEL_HEADING_LINE_PATTERN.matcher(reply);
        nextHeading.region(start, reply.length());
        int end = nextHeading.find() ? nextHeading.start() : reply.length();
        if (end <= start) {
            return null;
        }

        String section = reply.substring(start, end);
        Matcher matcher = Pattern.compile("(?is)<table\\b.*?</table>").matcher(section);
        return matcher.find() ? matcher.group() : null;
    }

    private boolean hasExactlyTwoFollowUpQuestions(String reply) {
        String[] markers = {"FOLLOW_UP_QUESTIONS:", "追问："};
        int markerIndex = -1;
        int markerLength = 0;
        for (String marker : markers) {
            int index = reply.indexOf(marker);
            if (index >= 0 && (markerIndex < 0 || index < markerIndex)) {
                markerIndex = index;
                markerLength = marker.length();
            }
        }
        if (markerIndex < 0) {
            return false;
        }

        String followUpBlock = reply.substring(markerIndex + markerLength).trim();
        List<String> lines = followUpBlock.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
        return lines.size() == 2
                && lines.getFirst().matches("1\\.\\s+.+")
                && lines.getLast().matches("2\\.\\s+.+");
    }

    private String normalizeBlock(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
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

    private String ensureFollowUpQuestions(String reply, String language, boolean analyticsRequested) {
        String trimmed = reply == null ? "" : reply.trim();
        if (trimmed.isBlank()) {
            throw new IllegalStateException("Reply is blank after model generation.");
        }

        List<String> contextDefaults = buildContextualFollowUps(language, trimmed);

        String repaired;
        if (hasExactlyTwoFollowUpQuestions(trimmed)) {
            repaired = trimmed;
        } else if (trimmed.contains("FOLLOW_UP_QUESTIONS:") || trimmed.contains("追问：")) {
            repaired = repairPartialFollowUpQuestions(trimmed, contextDefaults);
        } else {
            return """
                    %s

                    FOLLOW_UP_QUESTIONS:
                    1. %s
                    2. %s
                    """.formatted(trimmed, contextDefaults.getFirst(), contextDefaults.getLast()).trim();
        }

        // Extract and validate follow-ups, then rebuild reply with validated follow-ups
        List<String> extracted = extractFollowUpsFromReply(repaired);
        if (extracted.size() == 2) {
            List<String> validated = validateFollowUpRelevance(repaired, extracted, language, contextDefaults);
            return rebuildReplyWithFollowUps(repaired, validated);
        }

        return repaired;
    }

    private String rebuildReplyWithFollowUps(String reply, List<String> followUps) {
        String[] markers = {"FOLLOW_UP_QUESTIONS:", "追问："};
        for (String marker : markers) {
            int idx = reply.lastIndexOf(marker);
            if (idx >= 0) {
                return reply.substring(0, idx + marker.length())
                        + "\n1. " + followUps.get(0)
                        + "\n2. " + followUps.get(1);
            }
        }
        return reply;
    }

    private String repairPartialFollowUpQuestions(String reply, List<String> defaults) {
        int markerIndex = reply.indexOf("FOLLOW_UP_QUESTIONS:");
        if (markerIndex < 0) {
            return reply;
        }

        String marker = "FOLLOW_UP_QUESTIONS:";
        String prefix = reply.substring(0, markerIndex + marker.length());
        String followUpBlock = reply.substring(markerIndex + marker.length()).trim();
        List<String> lines = followUpBlock.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();

        if (lines.isEmpty()) {
            return """
                    %s
                    1. %s
                    2. %s
                    """.formatted(prefix, defaults.getFirst(), defaults.getLast()).trim();
        }

        if (lines.size() == 1 && lines.getFirst().matches("1\\.\\s+.+")) {
            return """
                    %s
                    %s
                    2. %s
                    """.formatted(prefix, lines.getFirst(), defaults.getLast()).trim();
        }

        return """
                %s
                1. %s
                2. %s
                """.formatted(prefix, defaults.getFirst(), defaults.getLast()).trim();
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

    List<String> buildContextualFollowUps(String language, String reply) {
        if (reply == null || reply.isBlank()) {
            return defaultGeneralFollowUps(language);
        }
        boolean isZh = "zh".equals(language);
        String haystack = isZh ? reply : reply.toLowerCase();

        // Match scenario presence to pick the most relevant template
        if (haystack.contains("目标达成") || haystack.contains("达成率") || haystack.contains("target achievement")) {
            return isZh
                    ? List.of("达成短板主要在哪个车型？", "要不要对比同城市其他店的达成率？")
                    : List.of("Which model drags down achievement the most?",
                            "Compare achievement rates across other city dealers?");
        }
        if (haystack.contains("商机漏斗") || haystack.contains("商机转化") || haystack.contains("opportunity funnel")) {
            return isZh
                    ? List.of("哪个阶段的商机流失最严重？", "要不要按销售顾问拆分转化率？")
                    : List.of("Which funnel stage has the highest drop-off?",
                            "Break down conversion by sales consultant?");
        }
        if (haystack.contains("销售跟进") || haystack.contains("逾期") || haystack.contains("sales follow-up")) {
            return isZh
                    ? List.of("逾期任务集中在哪些门店？", "要不要查看任务完成率的月度趋势？")
                    : List.of("Which dealers have the most overdue tasks?",
                            "Check monthly task completion trends?");
        }
        if (haystack.contains("活动效果") || haystack.contains("市场活动") || haystack.contains("campaign")) {
            return isZh
                    ? List.of("本次活动ROI和去年同期比如何？", "要不要看各门店的活动参与度排名？")
                    : List.of("How does this campaign ROI compare to last year?",
                            "Rank dealers by campaign participation?");
        }
        if (haystack.contains("经营对标") || haystack.contains("门店对标") || haystack.contains("dealer benchmark")) {
            return isZh
                    ? List.of("要不要下钻到车型维度对比？", "这些门店的线索跟进时效如何？")
                    : List.of("Drill down by model dimension?",
                            "How is lead follow-up turnaround at these dealers?");
        }
        if (haystack.contains("线索来源") || haystack.contains("自然流量") || haystack.contains("lead source")) {
            return isZh
                    ? List.of("高意向线索主要来自哪个渠道？", "要不要对比各门店的线索跟进速度？")
                    : List.of("Which channel generates the highest-intent leads?",
                            "Compare lead follow-up speed across dealers?");
        }
        if (haystack.contains("经营活跃度") || haystack.contains("门店活跃") || haystack.contains("business activity")) {
            return isZh
                    ? List.of("活跃度最低的门店在哪个维度失分最多？", "要不要对比活跃度和目标达成率的关系？")
                    : List.of("Which dimension drags down the lowest-activity dealers?",
                            "Correlate activity score with target achievement?");
        }

        return defaultGeneralFollowUps(language);
    }

    List<String> validateFollowUpRelevance(String reply, List<String> followUps, String language, List<String> fallbackDefaults) {
        List<String> topicKeywords = extractTopicKeywords(reply, language);

        // If no keywords extracted (e.g., very short or generic reply), keep original follow-ups
        if (topicKeywords.isEmpty()) {
            return followUps;
        }

        List<String> validated = new ArrayList<>();
        for (String followUp : followUps) {
            if (isStronglyRelevant(followUp, topicKeywords)) {
                validated.add(followUp);
            }
        }

        if (validated.isEmpty()) {
            return fallbackDefaults;
        }

        if (validated.size() == 1) {
            for (String candidate : fallbackDefaults) {
                if (validated.size() >= 2) break;
                if (!validated.contains(candidate)) {
                    validated.add(candidate);
                }
            }
        }

        return validated.subList(0, Math.min(validated.size(), 2));
    }

    List<String> extractFollowUpsFromReply(String reply) {
        String normalized = reply == null ? "" : reply;
        String[] markers = {"FOLLOW_UP_QUESTIONS:", "追问："};
        int markerIndex = -1;
        int markerLen = 0;

        for (String marker : markers) {
            int idx = normalized.indexOf(marker);
            if (idx >= 0 && (markerIndex < 0 || idx < markerIndex)) {
                markerIndex = idx;
                markerLen = marker.length();
            }
        }

        if (markerIndex < 0) {
            return List.of();
        }

        return normalized.substring(markerIndex + markerLen).lines()
                .map(line -> line.replaceFirst("^\\s*(?:\\d+\\.\\s*|[-*·•]\\s*)", "")
                        .replaceAll("[*_~]+", "").trim())
                .filter(line -> !line.isBlank())
                .limit(2)
                .toList();
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

        return null;
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

    private static final com.fasterxml.jackson.databind.ObjectMapper STEP_EVENT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private void writeStepEvent(BufferedWriter writer, StepEvent step) throws IOException {
        String json = STEP_EVENT_MAPPER.writeValueAsString(Map.of(
                "trace_id", step.traceId(),
                "seq", step.seq(),
                "type", step.type().name(),
                "ts", step.ts(),
                "status", step.status(),
                "label", step.label() != null ? step.label() : "",
                "detail", step.detail() != null ? step.detail() : "",
                "meta", step.meta() != null ? step.meta() : Map.of()
        ));
        writeEvent(writer, "step", json);
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

    private void writeChunkedEvent(BufferedWriter writer, String event, String chunk) throws IOException {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        writeEvent(writer, event, chunk);
    }

    List<String> extractTopicKeywords(String reply, String language) {
        Set<String> keywords = new LinkedHashSet<>();
        String normalized = reply == null ? "" : reply;
        boolean isZh = "zh".equals(language);

        for (String term : METRIC_TERMS.getOrDefault(language, METRIC_TERMS.get("en"))) {
            String haystack = isZh ? normalized : normalized.toLowerCase();
            String needle = isZh ? term : term.toLowerCase();
            if (haystack.contains(needle)) {
                keywords.add(term);
            }
        }

        for (String term : SCENARIO_TERMS.getOrDefault(language, SCENARIO_TERMS.get("en"))) {
            String haystack = isZh ? normalized : normalized.toLowerCase();
            String needle = isZh ? term : term.toLowerCase();
            if (haystack.contains(needle)) {
                keywords.add(term);
            }
        }

        return List.copyOf(keywords);
    }

    boolean isStronglyRelevant(String followUp, List<String> topicKeywords) {
        if (followUp == null || topicKeywords == null) {
            return false;
        }
        for (String keyword : topicKeywords) {
            if (followUp.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private record GeneratedReply(String reply, List<String> progressMessages, String visibleThinking) {
    }
}
