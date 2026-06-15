package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.ai.LanguageDetector;
import com.brand.agentpoc.ai.PromptFactory;
import com.brand.agentpoc.dto.request.ChatRequest;
import com.brand.agentpoc.repository.DealerRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

class ChatServiceTest {

    private static final String GENERAL_MESSAGE = "What is CRM hygiene?";

    private SessionMemoryService sessionMemoryService;
    private LanguageDetector languageDetector;
    private RuleBasedAnalyticsService analyticsService;
    private PromptFactory promptFactory;
    private ModelConfigService modelConfigService;
    private DealerRepository dealerRepository;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        sessionMemoryService = mock(SessionMemoryService.class);
        languageDetector = mock(LanguageDetector.class);
        analyticsService = mock(RuleBasedAnalyticsService.class);
        promptFactory = mock(PromptFactory.class);
        modelConfigService = mock(ModelConfigService.class);
        dealerRepository = mock(DealerRepository.class);

        chatService = new ChatService(
                sessionMemoryService,
                languageDetector,
                analyticsService,
                promptFactory,
                modelConfigService,
                dealerRepository
        );
    }

    @Test
    void streamsBuiltInChineseGreetingWithoutCallingModelOrAnalytics() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                "你好",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);

        when(languageDetector.detectLanguage("你好")).thenReturn("zh");

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(payload).contains("event: message");
        assertThat(payload).contains("我是经销商 AI 分析助手");
        assertThat(payload).contains("目标达成");
        assertThat(payload).contains("FOLLOW_UP_QUESTIONS:");
        assertThat(payload).contains("哪些门店目标达成率最低？");
        assertThat(payload).contains("分析一下各门店的商机漏斗转化情况");
        assertThat(payload).doesNotContain("接口调用链");
        assertThat(payload).doesNotContain("核心结论");
        assertThat(payload).doesNotContain("数据支撑");
        assertThat(payload).doesNotContain("event: progress");
        assertThat(payload).doesNotContain("event: error");
        assertThat(payload).contains("event: done");
        verify(sessionMemoryService).addUserMessage("s1", "你好");
        verify(sessionMemoryService).addAssistantMessage(eq("s1"), replyCaptor.capture());
        assertThat(replyCaptor.getValue()).contains("我是经销商 AI 分析助手");
        assertThat(replyCaptor.getValue()).contains("FOLLOW_UP_QUESTIONS:");
        assertThat(replyCaptor.getValue()).contains("哪些门店目标达成率最低？");
        assertThat(replyCaptor.getValue()).contains("分析一下各门店的商机漏斗转化情况");
        assertThat(replyCaptor.getValue()).doesNotContain("接口调用链");
        verifyNoInteractions(modelConfigService);
        verifyNoInteractions(analyticsService);
    }

    @Test
    void streamsBuiltInEnglishGreetingWithoutCallingModelOrAnalytics() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                "hello",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);

        when(languageDetector.detectLanguage("hello")).thenReturn("en");

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(payload).contains("event: message");
        assertThat(payload).contains("dealer AI analytics assistant");
        assertThat(payload).contains("target achievement");
        assertThat(payload).contains("FOLLOW_UP_QUESTIONS:");
        assertThat(payload).contains("Which dealers have the lowest target achievement rate?");
        assertThat(payload).contains("Analyze opportunity funnel conversion by dealer");
        assertThat(payload).doesNotContain("Interface Call Chain");
        assertThat(payload).doesNotContain("Data Support");
        assertThat(payload).doesNotContain("event: progress");
        assertThat(payload).doesNotContain("event: error");
        assertThat(payload).contains("event: done");
        verify(sessionMemoryService).addUserMessage("s1", "hello");
        verify(sessionMemoryService).addAssistantMessage(eq("s1"), replyCaptor.capture());
        assertThat(replyCaptor.getValue()).contains("dealer AI analytics assistant");
        assertThat(replyCaptor.getValue()).contains("FOLLOW_UP_QUESTIONS:");
        assertThat(replyCaptor.getValue()).contains("Which dealers have the lowest target achievement rate?");
        assertThat(replyCaptor.getValue()).contains("Analyze opportunity funnel conversion by dealer");
        assertThat(replyCaptor.getValue()).doesNotContain("Interface Call Chain");
        verifyNoInteractions(modelConfigService);
        verifyNoInteractions(analyticsService);
    }

    @Test
    void combinedEnglishGreetingAndIntroUsesBuiltInAssistantIntroduction() {
        ChatRequest request = new ChatRequest(
                "s1",
                "Hello, who are you?",
                "",
                "",
                ""
        );

        when(languageDetector.detectLanguage(request.message())).thenReturn("en");

        String reply = chatService.chat(request);

        assertThat(reply).contains("dealer AI analytics assistant");
        assertThat(reply).contains("target achievement");
        assertThat(reply).contains("FOLLOW_UP_QUESTIONS:");
        assertThat(reply).doesNotContain("outside the dealer AI analytics assistant's business scope");
        verify(sessionMemoryService).addUserMessage("s1", request.message());
        verify(sessionMemoryService).addAssistantMessage("s1", reply);
        verifyNoInteractions(modelConfigService);
        verifyNoInteractions(analyticsService);
    }

    @Test
    void chatReturnsBuiltInIntroForIdentityQuestionWithoutCallingModelOrAnalytics() {
        ChatRequest request = new ChatRequest(
                "s1",
                "你是谁",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );

        when(languageDetector.detectLanguage("你是谁")).thenReturn("zh");

        String reply = chatService.chat(request);

        assertThat(reply).contains("我是经销商 AI 分析助手");
        assertThat(reply).contains("FOLLOW_UP_QUESTIONS:");
        assertThat(reply).contains("哪些门店目标达成率最低？");
        assertThat(reply).contains("分析一下各门店的商机漏斗转化情况");
        assertThat(reply).doesNotContain("接口调用链");
        assertThat(reply).doesNotContain("核心结论");
        verify(sessionMemoryService).addUserMessage("s1", "你是谁");
        verify(sessionMemoryService).addAssistantMessage("s1", reply);
        verifyNoInteractions(modelConfigService);
        verifyNoInteractions(analyticsService);
    }

    @Test
    void chatReturnsEntityNotFoundForUnknownCustomerBeforeModelOrAnalytics() {
        ChatRequest request = new ChatRequest(
                "s1",
                "客户A的目标达成率怎么样？",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );

        when(languageDetector.detectLanguage(request.message())).thenReturn("zh");

        String reply = chatService.chat(request);

        assertThat(reply).contains("未找到");
        assertThat(reply).contains("客户A");
        assertThat(reply).contains("FOLLOW_UP_QUESTIONS:");
        assertThat(reply).doesNotContain("接口调用链");
        assertThat(reply).doesNotContain("核心结论");
        assertThat(reply).doesNotContain("数据支撑");
        verify(sessionMemoryService).addUserMessage("s1", request.message());
        verify(sessionMemoryService).addAssistantMessage("s1", reply);
        verifyNoInteractions(modelConfigService);
        verifyNoInteractions(analyticsService);
    }

    @Test
    void chatReturnsEntityNotFoundForUnknownDealerSuffixBeforeModelOrAnalytics() {
        String message = "\u7ecf\u9500\u5546\u4e0d\u5b58\u5728XYZ\u7684\u76ee\u6807\u8fbe\u6210\u60c5\u51b5\u600e\u4e48\u6837\uff1f";
        ChatRequest request = new ChatRequest(
                "s1",
                message,
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );

        when(languageDetector.detectLanguage(message)).thenReturn("zh");

        String reply = chatService.chat(request);

        assertThat(reply).contains("\u672a\u627e\u5230");
        assertThat(reply).contains("\u7ecf\u9500\u5546\u4e0d\u5b58\u5728XYZ");
        assertThat(reply).doesNotContain("\u6838\u5fc3\u7ed3\u8bba");
        verify(sessionMemoryService).addUserMessage("s1", message);
        verify(sessionMemoryService).addAssistantMessage("s1", reply);
        verifyNoInteractions(modelConfigService);
        verifyNoInteractions(analyticsService);
    }

    @Test
    void streamReturnsEntityNotFoundForUnknownCustomerBeforeModelOrAnalytics() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                "客户A的目标达成率怎么样？",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(languageDetector.detectLanguage(request.message())).thenReturn("zh");

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(payload).contains("event: message");
        assertThat(payload).contains("未找到");
        assertThat(payload).contains("客户A");
        assertThat(payload).contains("FOLLOW_UP_QUESTIONS:");
        assertThat(payload).doesNotContain("接口调用链");
        assertThat(payload).doesNotContain("核心结论");
        assertThat(payload).doesNotContain("数据支撑");
        assertThat(payload).doesNotContain("event: progress");
        assertThat(payload).doesNotContain("event: error");
        assertThat(payload).contains("event: done");
        verify(sessionMemoryService).addUserMessage("s1", request.message());
        verify(sessionMemoryService).addAssistantMessage(eq("s1"), anyString());
        verifyNoInteractions(modelConfigService);
        verifyNoInteractions(analyticsService);
    }

    @Test
    void chatRejectsOutOfScopeQuestionBeforeModelOrAnalytics() {
        ChatRequest request = new ChatRequest(
                "s1",
                "快速排序算法怎么写？",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );

        when(languageDetector.detectLanguage(request.message())).thenReturn("zh");

        String reply = chatService.chat(request);

        assertThat(reply).contains("只能回答");
        assertThat(reply).contains("经销商经营分析");
        assertThat(reply).contains("FOLLOW_UP_QUESTIONS:");
        assertThat(reply).doesNotContain("快速排序");
        assertThat(reply).doesNotContain("接口调用链");
        verify(sessionMemoryService).addUserMessage("s1", request.message());
        verify(sessionMemoryService).addAssistantMessage("s1", reply);
        verifyNoInteractions(modelConfigService);
        verifyNoInteractions(analyticsService);
    }

    @Test
    void streamRejectsOutOfScopeQuestionBeforeModelOrAnalytics() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                "快速排序算法怎么写？",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(languageDetector.detectLanguage(request.message())).thenReturn("zh");

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(payload).contains("event: message");
        assertThat(payload).contains("只能回答");
        assertThat(payload).contains("经销商经营分析");
        assertThat(payload).contains("FOLLOW_UP_QUESTIONS:");
        assertThat(payload).doesNotContain("快速排序");
        assertThat(payload).doesNotContain("event: progress");
        assertThat(payload).doesNotContain("event: error");
        assertThat(payload).contains("event: done");
        verify(sessionMemoryService).addUserMessage("s1", request.message());
        verify(sessionMemoryService).addAssistantMessage(eq("s1"), anyString());
        verifyNoInteractions(modelConfigService);
        verifyNoInteractions(analyticsService);
    }

    @Test
    void missingConfigGeneralStreamingUsesConfigurationProgressMessage() throws Exception {
        ChatRequest request = new ChatRequest("s1", GENERAL_MESSAGE, "", "", "");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(languageDetector.detectLanguage(GENERAL_MESSAGE)).thenReturn("en");

        chatService.streamChat(request, outputStream);

        List<String> progressEvents = extractEventData(outputStream.toString(StandardCharsets.UTF_8), "progress");
        assertThat(progressEvents).containsExactly("Checking the chat model configuration");
        verifyNoInteractions(modelConfigService);
        verifyNoInteractions(analyticsService);
    }

    @Test
    void analyticsRequestsUseBuiltInFallbackWhenModelSettingsAreMissing() {
        ChatRequest request = new ChatRequest(
                "s1",
                "Which dealers have the lowest target achievement?",
                "",
                "",
                ""
        );
        AnalyticsPlan plan = new AnalyticsPlan(
                AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT,
                null,
                "the current sample dataset",
                List.of("Identifying the analysis theme", "Filtering target data", "Returning the built-in reply"),
                "1. Identify the topic\n2. Check the sample data\n3. Return the built-in answer",
                "Scenario: TARGET_ACHIEVEMENT\nScope: the current sample dataset\nLanguage: en",
                """
                ## Conclusion
                fallback
                ## Data Support
                <table><tr><td>fallback</td></tr></table>
                ## Short Analysis
                fallback
                FOLLOW_UP_QUESTIONS:
                1. next?
                2. next?
                """
        );

        when(languageDetector.detectLanguage(request.message())).thenReturn("en");
        when(analyticsService.plan(request.message(), "en")).thenReturn(plan);

        String reply = chatService.chat(request);

        assertThat(reply).isEqualTo(plan.fallbackReply().trim());
        verify(analyticsService).plan(request.message(), "en");
        verifyNoInteractions(modelConfigService);
    }

    @Test
    void purchaseCycleQuestionUsesAnalyticsFallbackInsteadOfOutOfScopeReply() {
        String message = "\u8d2d\u4e70\u5468\u671f\u6700\u591a\u96c6\u4e2d\u5728\u54ea\u4e2a\u533a\u95f4\uff1f";
        ChatRequest request = new ChatRequest("s1", message, "", "", "");
        AnalyticsPlan plan = analyticsPlan(
                AnalyticsPlan.Scenario.OPPORTUNITY_FUNNEL,
                """
                ## \u6838\u5fc3\u7ed3\u8bba
                \u8d2d\u4e70\u5468\u671f\u6700\u591a\u96c6\u4e2d\u5728 More than 10 Months\u3002
                ## \u6570\u636e\u652f\u6491
                <table><tr><td>More than 10 Months</td></tr></table>
                ## \u7ecf\u8425\u5206\u6790
                fallback
                \u8ffd\u95ee\uff1a
                1. next?
                2. next?
                """
        );

        when(languageDetector.detectLanguage(message)).thenReturn("zh");
        when(analyticsService.plan(message, "zh")).thenReturn(plan);

        String reply = chatService.chat(request);

        assertThat(reply).contains("More than 10 Months");
        assertThat(reply).doesNotContain("\u8d85\u51fa\u7ecf\u9500\u5546 AI \u5206\u6790\u52a9\u624b\u7684\u4e1a\u52a1\u8303\u56f4");
        verify(analyticsService).plan(message, "zh");
        verifyNoInteractions(modelConfigService);
    }

    @Test
    void bestSellingModelQuestionUsesAnalyticsFallbackInsteadOfOutOfScopeReply() {
        String message = "全国范围内哪款车卖得最好？";
        ChatRequest request = new ChatRequest("s1", message, "", "", "");
        AnalyticsPlan plan = analyticsPlan(
                AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT,
                """
                ## 核心结论
                车型赢单最多：Nova X。
                ## 数据支撑
                <table><tr><td>Nova X</td></tr></table>
                ## 经营分析
                fallback
                追问：
                1. next?
                2. next?
                """
        );

        when(languageDetector.detectLanguage(message)).thenReturn("zh");
        when(analyticsService.plan(message, "zh")).thenReturn(plan);

        String reply = chatService.chat(request);

        assertThat(reply).contains("车型赢单最多");
        assertThat(reply).doesNotContain("超出经销商 AI 分析助手的业务范围");
        verify(analyticsService).plan(message, "zh");
        verifyNoInteractions(modelConfigService);
    }

    @Test
    void usesTheRequestScopedModelConfiguration() {
        ChatRequest request = new ChatRequest(
                "s1",
                GENERAL_MESSAGE,
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);

        when(languageDetector.detectLanguage(GENERAL_MESSAGE)).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt("en", GENERAL_MESSAGE, "None")).thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("Model reply"))
        )));

        String reply = chatService.chat(request);

        assertThat(reply).contains("Model reply");
        verify(modelConfigService).createChatModel(request);
        verify(sessionMemoryService).addUserMessage("s1", GENERAL_MESSAGE);
        verify(sessionMemoryService).addAssistantMessage(eq("s1"), eq(reply));
        verifyNoInteractions(analyticsService);
    }

    @Test
    void includesUpToTwentyMessagesOfConversationHistoryInPrompt() {
        ChatRequest request = new ChatRequest(
                "s1",
                GENERAL_MESSAGE,
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        List<String> history = new ArrayList<>();
        ArgumentCaptor<String> historyCaptor = ArgumentCaptor.forClass(String.class);

        for (int i = 1; i <= 6; i++) {
            history.add("USER:user-" + i);
            history.add("ASSISTANT:assistant-" + i);
        }

        when(languageDetector.detectLanguage(GENERAL_MESSAGE)).thenReturn("en");
        when(sessionMemoryService.getMessages("s1")).thenReturn(history);
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt(eq("en"), eq(GENERAL_MESSAGE), any(String.class))).thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("Model reply"))
        )));

        chatService.chat(request);

        verify(promptFactory).buildConversationModelPrompt(eq("en"), eq(GENERAL_MESSAGE), historyCaptor.capture());
        assertThat(historyCaptor.getValue()).contains("- User: user-1");
        assertThat(historyCaptor.getValue()).contains("- Assistant: assistant-1");
        assertThat(historyCaptor.getValue()).contains("- User: user-6");
        assertThat(historyCaptor.getValue()).contains("- Assistant: assistant-6");
    }

    @Test
    void streamsProgressAndAnErrorEventWhenTheConfiguredModelFails() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                GENERAL_MESSAGE,
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(languageDetector.detectLanguage(GENERAL_MESSAGE)).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt("en", GENERAL_MESSAGE, "None")).thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("429 busy")));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString();
        assertThat(payload).contains("event: progress");
        assertThat(payload).contains("data: Preparing the current conversation context");
        assertThat(payload).contains("event: error");
        assertThat(payload).contains("data: 429 busy");
        verify(sessionMemoryService).addUserMessage("s1", GENERAL_MESSAGE);
        verify(sessionMemoryService, never()).addAssistantMessage(eq("s1"), any());
    }

    @Test
    void streamsAnErrorEventWhenModelUrlPolicyRejectsTheRequest() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                GENERAL_MESSAGE,
                "http://localhost:11434/v1",
                "sk-test",
                "gpt-4.1-mini"
        );
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(languageDetector.detectLanguage(GENERAL_MESSAGE)).thenReturn("en");
        when(modelConfigService.createChatModel(request))
                .thenThrow(new IllegalArgumentException("Model base URL is not allowed."));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(payload).contains("event: progress");
        assertThat(payload).contains("event: error");
        assertThat(payload).contains("data: Model base URL is not allowed.");
        verify(sessionMemoryService).addUserMessage("s1", GENERAL_MESSAGE);
        verify(sessionMemoryService, never()).addAssistantMessage(eq("s1"), any());
    }

    @Test
    void writesAnInitialProgressEventBeforeInvokingTheConfiguredModel() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                GENERAL_MESSAGE,
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        AtomicBoolean progressSeen = new AtomicBoolean(false);

        when(languageDetector.detectLanguage(GENERAL_MESSAGE)).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt("en", GENERAL_MESSAGE, "None")).thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> {
            assertThat(progressSeen.get()).isTrue();
            return Flux.just(new ChatResponse(List.of(
                    new Generation(new AssistantMessage("Model reply"))
            )));
        });

        OutputStream outputStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                buffer.write(b);
                if (new String(buffer.toByteArray(), StandardCharsets.UTF_8).contains("event: progress")) {
                    progressSeen.set(true);
                }
            }
        };

        chatService.streamChat(request, outputStream);

        assertThat(new String(buffer.toByteArray(), StandardCharsets.UTF_8)).contains("data: Preparing the current conversation context");
        verify(sessionMemoryService).addUserMessage("s1", GENERAL_MESSAGE);
    }

    @Test
    void streamsConfiguredModelRepliesAcrossMultipleMessageEvents() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                GENERAL_MESSAGE,
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);

        when(languageDetector.detectLanguage(GENERAL_MESSAGE)).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt("en", GENERAL_MESSAGE, "None")).thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Hello ")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("world"))))
        ));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(payload).containsSubsequence(
                "event: message\r\ndata: Hello ",
                "event: message\r\ndata: world"
        );
        assertThat(payload).contains("data: FOLLOW_UP_QUESTIONS:");
        assertThat(payload).contains("event: done");
        verify(sessionMemoryService).addAssistantMessage(eq("s1"), replyCaptor.capture());
        assertThat(replyCaptor.getValue()).contains("Hello world");
        assertThat(replyCaptor.getValue()).contains("FOLLOW_UP_QUESTIONS:");
    }

    @Test
    void preservesWhitespaceOnlyChunksAsDistinctMessageEvents() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                GENERAL_MESSAGE,
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);

        when(languageDetector.detectLanguage(GENERAL_MESSAGE)).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt("en", GENERAL_MESSAGE, "None")).thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Hello")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage(" ")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("world"))))
        ));

        chatService.streamChat(request, outputStream);

        List<String> messageEvents = extractEventData(outputStream.toString(StandardCharsets.UTF_8), "message").stream()
                .filter(eventData -> !eventData.startsWith("<think>"))
                .toList();

        assertThat(messageEvents).hasSize(4);
        assertThat(messageEvents.get(0)).isEqualTo("Hello");
        assertThat(messageEvents.get(1)).isEqualTo(" ");
        assertThat(messageEvents.get(2)).isEqualTo("world");
        assertThat(messageEvents.get(3)).startsWith("\n\nFOLLOW_UP_QUESTIONS:");

        verify(sessionMemoryService).addAssistantMessage(eq("s1"), replyCaptor.capture());
        assertThat(messageEvents.stream().collect(Collectors.joining()))
                .isEqualTo(replyCaptor.getValue());
        assertThat(replyCaptor.getValue()).startsWith("Hello world");
    }

    @Test
    void keepsPartialChunksVisibleButSkipsAssistantPersistenceWhenStreamingFails() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                GENERAL_MESSAGE,
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(languageDetector.detectLanguage(GENERAL_MESSAGE)).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt("en", GENERAL_MESSAGE, "None")).thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.concat(
                Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("Hello "))))),
                Flux.error(new RuntimeException("429 busy"))
        ));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(payload).contains("data: Hello ");
        assertThat(payload).contains("event: error");
        assertThat(payload).doesNotContain("event: done");
        verify(sessionMemoryService, never()).addAssistantMessage(eq("s1"), any());
    }

    @Test
    void doesNotEmitDoneBeforePersistenceFailureOnSuccessfulStream() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                GENERAL_MESSAGE,
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(languageDetector.detectLanguage(GENERAL_MESSAGE)).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt("en", GENERAL_MESSAGE, "None")).thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Hello"))))
        ));
        doThrow(new RuntimeException("persist failed"))
                .when(sessionMemoryService)
                .addAssistantMessage(eq("s1"), any());

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(payload).contains("data: Hello");
        assertThat(payload).contains("event: error");
        assertThat(payload).contains("data: persist failed");
        assertThat(payload).doesNotContain("event: done");
    }

    @Test
    void stopsStreamingWhenTheAccumulatedReplyExceedsTheHardLimit() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                GENERAL_MESSAGE,
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        String oversizedChunk = "x".repeat(32_001);

        when(languageDetector.detectLanguage(GENERAL_MESSAGE)).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt("en", GENERAL_MESSAGE, "None")).thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage(oversizedChunk))))
        ));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(payload).contains("event: error");
        assertThat(payload).doesNotContain("event: done");
        verify(sessionMemoryService, never()).addAssistantMessage(eq("s1"), any());
    }

    @Test
    void doesNotEmitTheFollowUpTailWhenItWouldExceedTheHardLimit() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                GENERAL_MESSAGE,
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        String nearLimitChunk = "x".repeat(ChatService.MAX_STREAMED_REPLY_CHARS - 32);

        when(languageDetector.detectLanguage(GENERAL_MESSAGE)).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt("en", GENERAL_MESSAGE, "None")).thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage(nearLimitChunk))))
        ));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(payload).contains("event: error");
        assertThat(payload).doesNotContain("event: done");
        assertThat(payload).doesNotContain("FOLLOW_UP_QUESTIONS:");
        verify(sessionMemoryService, never()).addAssistantMessage(eq("s1"), any());
    }

    @Test
    void preservesTrailingWhitespaceWhenAppendingTheFollowUpTail() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                GENERAL_MESSAGE,
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);
        String streamedChunk = "Hello from stream \n";

        when(languageDetector.detectLanguage(GENERAL_MESSAGE)).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt("en", GENERAL_MESSAGE, "None")).thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage(streamedChunk))))
        ));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        List<String> messageEvents = extractEventData(payload, "message").stream()
                .filter(eventData -> !eventData.startsWith("<think>"))
                .toList();

        assertThat(messageEvents).hasSize(2);
        assertThat(messageEvents.getFirst()).isEqualTo(streamedChunk);
        assertThat(messageEvents.getLast()).startsWith("\nFOLLOW_UP_QUESTIONS:");

        verify(sessionMemoryService).addAssistantMessage(eq("s1"), replyCaptor.capture());
        assertThat(messageEvents.stream().collect(Collectors.joining()))
                .isEqualTo(replyCaptor.getValue());
        assertThat(replyCaptor.getValue())
                .startsWith("Hello from stream \n\nFOLLOW_UP_QUESTIONS:");
    }

    @Test
    void repairsPartialStreamedFollowUpSections() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                GENERAL_MESSAGE,
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);

        when(languageDetector.detectLanguage(GENERAL_MESSAGE)).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt("en", GENERAL_MESSAGE, "None")).thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Hello")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("\n\nFOLLOW_UP_QUESTIONS:\n1. Partial question only"))))
        ));

        chatService.streamChat(request, outputStream);

        List<String> messageEvents = extractEventData(outputStream.toString(StandardCharsets.UTF_8), "message").stream()
                .filter(eventData -> !eventData.startsWith("<think>"))
                .toList();

        assertThat(messageEvents).hasSize(3);
        assertThat(messageEvents.getFirst()).isEqualTo("Hello");
        assertThat(messageEvents.get(1)).isEqualTo("\n\nFOLLOW_UP_QUESTIONS:\n1. Partial question only");
        assertThat(messageEvents.getLast())
                .isEqualTo("\n2. Should I turn those seven configuration items into a copy-ready template?");

        verify(sessionMemoryService).addAssistantMessage(eq("s1"), replyCaptor.capture());
        assertThat(replyCaptor.getValue()).isEqualTo("""
                Hello

                FOLLOW_UP_QUESTIONS:
                1. Partial question only
                2. Should I turn those seven configuration items into a copy-ready template?
                """.trim());
    }

    @Test
    void streamingAnalyticsRejectsInvalidStructuredRepliesAndFallsBack() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                "Which dealers have the lowest target achievement?",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);
        AnalyticsPlan plan = analyticsPlan(
                AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT,
                """
                ## Conclusion
                fallback
                ## Data Support
                <table><tr><td>fallback</td></tr></table>
                ## Short Analysis
                fallback
                FOLLOW_UP_QUESTIONS:
                1. next?
                2. next?
                """
        );

        when(languageDetector.detectLanguage(request.message())).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(analyticsService.plan(eq(request.message()), eq("en"), anyString(), any())).thenReturn(plan);
        when(promptFactory.buildGroundedModelPrompt("en", request.message(), "None", plan.groundedReference()))
                .thenReturn("Grounded prompt");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("## Conclusion\nbad")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("\n## Data Support\n- no html table")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("\n## Short Analysis\nbad"))))
        ));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        List<String> progressEvents = extractEventData(payload, "progress");
        List<String> messageEvents = extractEventData(payload, "message").stream()
                .filter(eventData -> !eventData.startsWith("<think>"))
                .toList();

        assertThat(progressEvents).containsExactly(
                "Identifying the analysis theme",
                "Calling the external model to generate the business analysis report",
                "Model generation complete, validating data consistency",
                "Model output failed data consistency validation, falling back to the rule-based analysis report"
        );
        assertThat(messageEvents).containsExactly(plan.fallbackReply().trim());
        verify(sessionMemoryService).addAssistantMessage(eq("s1"), replyCaptor.capture());
        assertThat(replyCaptor.getValue()).isEqualTo(plan.fallbackReply().trim());
        assertThat(messageEvents.getFirst()).isEqualTo(replyCaptor.getValue());
    }

    @Test
    void streamingAnalyticsAcceptsValidStructuredReplies() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                "Which dealers have the lowest target achievement?",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);
        AnalyticsPlan plan = analyticsPlan(
                AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT,
                englishAnalyticsFallbackReport()
        );
        String validReply = """
                ## 1. Interface Call Chain
                1. Current date: 2026-05-20.
                ## 2. Conclusion
                fallback
                ## 3. Data Support
                <table><tr><td>fallback</td></tr></table>
                ## 4. Short Analysis
                fallback
                ## 5. Problem Diagnosis & Solutions
                fallback
                ## 6. Improvement Suggestions
                fallback
                FOLLOW_UP_QUESTIONS:
                1. next?
                2. next?
                """.trim();

        when(languageDetector.detectLanguage(request.message())).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(analyticsService.plan(eq(request.message()), eq("en"), anyString(), any())).thenReturn(plan);
        when(promptFactory.buildGroundedModelPrompt("en", request.message(), "None", plan.groundedReference()))
                .thenReturn("Grounded prompt");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("## 1. Interface Call Chain\n1. Current date: 2026-05-20.")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("\n## 2. Conclusion\nfallback")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("\n## 3. Data Support\n<table><tr><td>fallback</td></tr></table>")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("\n## 4. Short Analysis\nfallback")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("\n## 5. Problem Diagnosis & Solutions\nfallback")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("\n## 6. Improvement Suggestions\nfallback\nFOLLOW_UP_QUESTIONS:\n1. next?\n2. next?"))))
        ));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        List<String> progressEvents = extractEventData(payload, "progress");
        List<String> messageEvents = extractEventData(payload, "message").stream()
                .filter(eventData -> !eventData.startsWith("<think>"))
                .toList();

        assertThat(progressEvents).containsExactly(
                "Identifying the analysis theme",
                "Calling the external model to generate the business analysis report",
                "Model generation complete, validating data consistency",
                "Data consistency validation passed, returning the final report"
        );
        assertThat(messageEvents).containsExactly(validReply);
        verify(sessionMemoryService).addAssistantMessage(eq("s1"), replyCaptor.capture());
        assertThat(replyCaptor.getValue()).isEqualTo(validReply);
        assertThat(messageEvents.getFirst()).isEqualTo(replyCaptor.getValue());
    }

    @Test
    void streamingAnalyticsUsesLocalizedChineseProgressMessages() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                "本月哪些经销商目标达成率最低？",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AnalyticsPlan plan = analyticsPlan(
                AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT,
                chineseAnalyticsFallbackReport()
        );
        String validReply = chineseAnalyticsValidReply().trim();

        when(languageDetector.detectLanguage(request.message())).thenReturn("zh");
        when(languageDetector.detectLanguage(plan.fallbackReply())).thenReturn("zh");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(analyticsService.plan(eq(request.message()), eq("zh"), anyString(), any())).thenReturn(plan);
        when(promptFactory.buildGroundedModelPrompt("zh", request.message(), "无", plan.groundedReference()))
                .thenReturn("Grounded prompt");
        when(promptFactory.buildSystemPrompt("zh")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage(validReply))))
        ));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        List<String> progressEvents = extractEventData(payload, "progress");

        assertThat(progressEvents).containsExactly(
                "正在识别分析主题",
                "正在调用外部模型生成经营分析报告",
                "模型生成完成，正在校验数据一致性",
                "数据一致性校验通过，正在返回最终报告"
        );
        assertThat(extractEventData(payload, "message")).containsExactly(validReply);
    }

    @Test
    void streamingAnalyticsFallsBackWhenConfiguredModelStreamFails() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                "Which dealers have the lowest target achievement?",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ArgumentCaptor<String> replyCaptor = ArgumentCaptor.forClass(String.class);
        AnalyticsPlan plan = analyticsPlan(
                AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT,
                """
                ## Conclusion
                fallback
                ## Data Support
                <table><tr><td>fallback</td></tr></table>
                ## Short Analysis
                fallback
                FOLLOW_UP_QUESTIONS:
                1. next?
                2. next?
                """
        );

        when(languageDetector.detectLanguage(request.message())).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(analyticsService.plan(eq(request.message()), eq("en"), anyString(), any())).thenReturn(plan);
        when(promptFactory.buildGroundedModelPrompt("en", request.message(), "None", plan.groundedReference()))
                .thenReturn("Grounded prompt");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("429 busy")));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        List<String> messageEvents = extractEventData(payload, "message").stream()
                .filter(eventData -> !eventData.startsWith("<think>"))
                .toList();
        List<String> progressEvents = extractEventData(payload, "progress");

        assertThat(progressEvents).containsExactly(
                "Identifying the analysis theme",
                "Calling the external model to generate the business analysis report",
                "Model call failed, falling back to the rule-based analysis report"
        );
        assertThat(messageEvents).containsExactly(plan.fallbackReply().trim());
        assertThat(payload).contains("event: done");
        assertThat(payload).doesNotContain("event: error");
        verify(sessionMemoryService).addAssistantMessage(eq("s1"), replyCaptor.capture());
        assertThat(replyCaptor.getValue()).isEqualTo(plan.fallbackReply().trim());
    }

    @Test
    void streamingAnalyticsReturnsAnErrorWhenTheModelOutputExceedsTheHardLimit() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                "Which dealers have the lowest target achievement?",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AnalyticsPlan plan = analyticsPlan(
                AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT,
                englishAnalyticsFallbackReport()
        );
        String oversizedChunk = "OVER_LIMIT_MARKER" + "x".repeat(ChatService.MAX_STREAMED_REPLY_CHARS);

        when(languageDetector.detectLanguage(request.message())).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(analyticsService.plan(eq(request.message()), eq("en"), anyString(), any())).thenReturn(plan);
        when(promptFactory.buildGroundedModelPrompt("en", request.message(), "None", plan.groundedReference()))
                .thenReturn("Grounded prompt");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage(oversizedChunk))))
        ));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(payload).contains("event: error");
        assertThat(payload).contains("The streamed reply exceeded the allowed output limit.");
        assertThat(payload).doesNotContain("event: done");
        assertThat(payload).doesNotContain("OVER_LIMIT_MARKER");
        verify(sessionMemoryService, never()).addAssistantMessage(eq("s1"), any());
    }

    @Test
    void analyticsPromptExplicitlyRequiresStructuredSections() {
        PromptFactory actualPromptFactory = new PromptFactory();

        String prompt = actualPromptFactory.buildGroundedModelPrompt(
                "en",
                "Which dealers have the lowest target achievement?",
                "None",
                "Scenario: TARGET_ACHIEVEMENT"
        );

        assertThat(prompt).contains("## Conclusion");
        assertThat(prompt).contains("## Data Support");
        assertThat(prompt).contains("## Short Analysis");
        assertThat(prompt).contains("HTML <table>");
        assertThat(prompt).contains("must not be changed");
    }

    @Test
    void zhAnalyticsPromptExplicitlyRequiresStructuredSections() {
        PromptFactory actualPromptFactory = new PromptFactory();

        String prompt = actualPromptFactory.buildGroundedModelPrompt(
                "zh",
                "哪些门店的目标达成率最低？",
                "无",
                "Scenario: TARGET_ACHIEVEMENT"
        );

        assertThat(prompt).contains("## 核心结论");
        assertThat(prompt).contains("## 数据支撑");
        assertThat(prompt).contains("## 经营分析");
        assertThat(prompt).contains("HTML <table>");
        assertThat(prompt).contains("不得改动任何 KPI 数值");
    }

    @Test
    void analyticsRequestsUseGroundedPlanningAndFallbackWhenPolishFails() {
        ChatRequest request = new ChatRequest(
                "s1",
                "Which dealers have the lowest target achievement?",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        AnalyticsPlan plan = new AnalyticsPlan(
                AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT,
                null,
                "Shanghai / East Group",
                List.of("Identifying the analysis theme", "Filtering target data", "Generating a structured response"),
                "1. Classify the request\n2. Compute KPIs\n3. Produce the report",
                """
                Scenario: TARGET_ACHIEVEMENT
                Scope: Shanghai / East Group
                Language: en
                Canonical fallback report:
                ## Conclusion
                fallback
                ## Data Support
                <table><tr><td>fallback</td></tr></table>
                ## Short Analysis
                fallback
                FOLLOW_UP_QUESTIONS:
                1. next?
                2. next?
                """,
                """
                ## Conclusion
                fallback
                ## Data Support
                <table><tr><td>fallback</td></tr></table>
                ## Short Analysis
                fallback
                FOLLOW_UP_QUESTIONS:
                1. next?
                2. next?
                """
        );

        when(languageDetector.detectLanguage(request.message())).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(analyticsService.plan(request.message(), "en")).thenReturn(plan);
        when(promptFactory.buildGroundedModelPrompt("en", request.message(), "None", plan.groundedReference()))
                .thenReturn("Grounded prompt");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("429 busy"));

        String reply = chatService.chat(request);

        assertThat(reply).isEqualTo(plan.fallbackReply().trim());
        verify(analyticsService).plan(request.message(), "en");
    }

    @Test
    void analyticsRequestsRejectInvalidPolishedReplies() {
        ChatRequest request = new ChatRequest(
                "s1",
                "How does the opportunity funnel perform?",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        AnalyticsPlan plan = new AnalyticsPlan(
                AnalyticsPlan.Scenario.OPPORTUNITY_FUNNEL,
                null,
                "the current sample dataset",
                List.of("Identifying the analysis theme", "Filtering funnel data", "Generating a structured response"),
                "1. Classify the request\n2. Compute KPIs\n3. Produce the report",
                "Scenario: OPPORTUNITY_FUNNEL\nScope: the current sample dataset\nLanguage: en",
                """
                ## Conclusion
                fallback
                ## Data Support
                <table><tr><td>fallback</td></tr></table>
                ## Short Analysis
                fallback
                FOLLOW_UP_QUESTIONS:
                1. next?
                2. next?
                """
        );

        when(languageDetector.detectLanguage(request.message())).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(analyticsService.plan(request.message(), "en")).thenReturn(plan);
        when(promptFactory.buildGroundedModelPrompt("en", request.message(), "None", plan.groundedReference()))
                .thenReturn("Grounded prompt");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage(
                        "## Conclusion\nbad\n## Data Support\n- no html table\n## Short Analysis\nbad\nFOLLOW_UP_QUESTIONS:\n1. a\n2. b"
                ))
        )));

        String reply = chatService.chat(request);

        assertThat(reply).isEqualTo(plan.fallbackReply().trim());
        verify(analyticsService).plan(request.message(), "en");
    }

    @Test
    void analyticsRequestsRejectRepliesThatBreakTheFourSectionContract() {
        ChatRequest request = new ChatRequest(
                "s1",
                "How does the opportunity funnel perform?",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        AnalyticsPlan plan = new AnalyticsPlan(
                AnalyticsPlan.Scenario.OPPORTUNITY_FUNNEL,
                null,
                "the current sample dataset",
                List.of("Identifying the analysis theme", "Filtering funnel data", "Generating a structured response"),
                "1. Classify the request\n2. Compute KPIs\n3. Produce the report",
                "Scenario: OPPORTUNITY_FUNNEL\nScope: the current sample dataset\nLanguage: en",
                """
                ## Conclusion
                fallback
                ## Data Support
                <table><tr><td>fallback</td></tr></table>
                ## Short Analysis
                fallback
                FOLLOW_UP_QUESTIONS:
                1. next?
                2. next?
                """
        );

        when(languageDetector.detectLanguage(request.message())).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(analyticsService.plan(request.message(), "en")).thenReturn(plan);
        when(promptFactory.buildGroundedModelPrompt("en", request.message(), "None", plan.groundedReference()))
                .thenReturn("Grounded prompt");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage(
                        """
                        ## Conclusion
                        bad
                        ## Data Support
                        <table><tr><td>bad</td></tr></table>
                        ## Analysis
                        extra section
                        ## Short Analysis
                        bad
                        FOLLOW_UP_QUESTIONS:
                        1. a
                        2. b
                        """
                ))
        )));

        String reply = chatService.chat(request);

        assertThat(reply).isEqualTo(plan.fallbackReply().trim());
        verify(analyticsService).plan(request.message(), "en");
    }

    @Test
    void analyticsRequestsRejectRepliesThatAddDataSummary() {
        ChatRequest request = new ChatRequest(
                "s1",
                "How does the opportunity funnel perform?",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        AnalyticsPlan plan = analyticsPlan(AnalyticsPlan.Scenario.OPPORTUNITY_FUNNEL, englishAnalyticsFallbackReport());

        when(languageDetector.detectLanguage(request.message())).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(analyticsService.plan(request.message(), "en")).thenReturn(plan);
        when(promptFactory.buildGroundedModelPrompt("en", request.message(), "None", plan.groundedReference()))
                .thenReturn("Grounded prompt");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("""
                        ## Interface Call Chain
                        1. Current date: 2026-05-20.
                        ## Conclusion
                        fallback
                        ## Data Support
                        <table><tr><td>fallback</td></tr></table>
                        ## Short Analysis
                        fallback
                        ## Problem Diagnosis & Solutions
                        fallback
                        ## Improvement Suggestions
                        fallback
                        ## Data Summary
                        forbidden
                        FOLLOW_UP_QUESTIONS:
                        1. a
                        2. b
                        """))
        )));

        String reply = chatService.chat(request);

        assertThat(reply).isEqualTo(plan.fallbackReply().trim());
        verify(analyticsService).plan(request.message(), "en");
    }

    @Test
    void analyticsRequestsRejectRepliesMissingInterfaceCallChain() {
        ChatRequest request = new ChatRequest(
                "s1",
                "How does the opportunity funnel perform?",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        AnalyticsPlan plan = analyticsPlan(AnalyticsPlan.Scenario.OPPORTUNITY_FUNNEL, englishAnalyticsFallbackReport());

        when(languageDetector.detectLanguage(request.message())).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(analyticsService.plan(request.message(), "en")).thenReturn(plan);
        when(promptFactory.buildGroundedModelPrompt("en", request.message(), "None", plan.groundedReference()))
                .thenReturn("Grounded prompt");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("""
                        ## Conclusion
                        fallback
                        ## Data Support
                        <table><tr><td>fallback</td></tr></table>
                        ## Short Analysis
                        fallback
                        ## Problem Diagnosis & Solutions
                        fallback
                        ## Improvement Suggestions
                        fallback
                        FOLLOW_UP_QUESTIONS:
                        1. a
                        2. b
                        """))
        )));

        String reply = chatService.chat(request);

        assertThat(reply).isEqualTo(plan.fallbackReply().trim());
        verify(analyticsService).plan(request.message(), "en");
    }

    @Test
    void analyticsRequestsUseDetectedFallbackLanguageWhenValidatingReplies() {
        ChatRequest request = new ChatRequest(
                "s1",
                "本月哪些经销商目标达成率最低？",
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        AnalyticsPlan plan = analyticsPlan(AnalyticsPlan.Scenario.TARGET_ACHIEVEMENT, chineseAnalyticsFallbackReport());
        String validReply = chineseAnalyticsValidReply().trim();

        when(languageDetector.detectLanguage(request.message())).thenReturn("zh");
        when(languageDetector.detectLanguage(plan.fallbackReply())).thenReturn("zh");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(analyticsService.plan(request.message(), "zh")).thenReturn(plan);
        when(promptFactory.buildGroundedModelPrompt("zh", request.message(), "无", plan.groundedReference()))
                .thenReturn("Grounded prompt");
        when(promptFactory.buildSystemPrompt("zh")).thenReturn("System prompt");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage(validReply))
        )));

        String reply = chatService.chat(request);

        assertThat(reply).isEqualTo(validReply);
        verify(analyticsService).plan(request.message(), "zh");
    }

    @Test
    void repairsMalformedFollowUpSectionWithDefaults() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1",
                GENERAL_MESSAGE,
                "https://api.example.com",
                "sk-test",
                "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(languageDetector.detectLanguage(GENERAL_MESSAGE)).thenReturn("en");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt("en", GENERAL_MESSAGE, "None")).thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("en")).thenReturn("System prompt");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Hello")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "\n\nFOLLOW_UP_QUESTIONS:\nunrelated text\nmore unrelated text"))))
        ));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(payload).contains("event: done");
        // Should not contain error event — was gracefully repaired
        assertThat(payload).doesNotContain("event: error");
        assertThat(payload).contains("FOLLOW_UP_QUESTIONS:");
    }

    @Test
    void extractsMetricKeywordsFromChineseReply() {
        List<String> keywords = chatService.extractTopicKeywords(
                "北京朝阳店本月目标达成率为85%，商机转化率12%",
                "zh"
        );
        assertThat(keywords).contains("达成率", "转化率", "商机");
    }

    @Test
    void extractsScenarioKeywordsFromReply() {
        List<String> keywords = chatService.extractTopicKeywords(
                "## 目标达成分析\n经营对标数据显示华东区表现突出",
                "zh"
        );
        assertThat(keywords).contains("目标达成", "经营对标");
    }

    @Test
    void returnsEmptyForReplyWithNoKnownTerms() {
        List<String> keywords = chatService.extractTopicKeywords(
                "Hello, how are you today?",
                "en"
        );
        assertThat(keywords).isEmpty();
    }

    @Test
    void marksFollowUpRelevantWhenItHitsATopicKeyword() {
        List<String> keywords = List.of("达成率", "转化率", "商机");

        assertThat(chatService.isStronglyRelevant("北京朝阳店的达成率如何提升？", keywords)).isTrue();
        assertThat(chatService.isStronglyRelevant("商机漏斗哪个阶段流失最多？", keywords)).isTrue();
    }

    @Test
    void marksFollowUpIrrelevantWhenMissingAllTopicKeywords() {
        List<String> keywords = List.of("达成率", "商机");

        assertThat(chatService.isStronglyRelevant("你想了解什么？", keywords)).isFalse();
        assertThat(chatService.isStronglyRelevant("还有其他需要帮助的吗？", keywords)).isFalse();
    }

    @Test
    void buildsTargetAchievementFollowUpsForChineseReply() {
        List<String> followUps = chatService.buildContextualFollowUps("zh",
                "## 核心结论\n北京朝阳店本月目标达成率为72%");
        assertThat(followUps).containsExactly(
                "达成短板主要在哪个车型？",
                "要不要对比同城市其他店的达成率？"
        );
    }

    @Test
    void buildsOpportunityFunnelFollowUpsForEnglishReply() {
        List<String> followUps = chatService.buildContextualFollowUps("en",
                "## Conclusion\nThe opportunity funnel shows high drop-off at Stage 2");
        assertThat(followUps).containsExactly(
                "Which funnel stage has the highest drop-off?",
                "Break down conversion by sales consultant?"
        );
    }

    @Test
    void fallsBackToGeneralFollowUpsForNonAnalyticsReply() {
        List<String> followUps = chatService.buildContextualFollowUps("zh",
                "你好，请问有什么可以帮助你的？");
        assertThat(followUps).hasSize(2);
        assertThat(followUps.get(0)).isEqualTo("你想先继续配置模型连接，还是先验证现有聊天链路？");
        assertThat(followUps.get(1)).isEqualTo("要不要我顺手把这 7 个配置项整理成可直接复制的模板？");
    }

    @Test
    void keepsRelevantFollowUpsDuringStreamingGeneralReply() throws Exception {
        ChatRequest request = new ChatRequest(
                "s1", "帮我介绍一下朝阳店的情况",
                "https://api.example.com", "sk-test", "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(languageDetector.detectLanguage(request.message())).thenReturn("zh");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt(eq("zh"), eq(request.message()), any(String.class)))
                .thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("zh")).thenReturn("System prompt");
        // Model generates relevant follow-ups (both contain topic keyword 达成率)
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "朝阳店本月达成率为72%。\n\n追问：\n1. 达成率低的主要影响因素是什么？\n2. 要不要对比北京其他店的达成率？"
                ))))
        ));

        chatService.streamChat(request, outputStream);

        String payload = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(payload).contains("达成率低的主要影响因素是什么");
        assertThat(payload).contains("对比北京其他店的达成率");
    }

    @Test
    void replacesIrrelevantFollowUpsWithContextualDefaults() {
        ChatRequest request = new ChatRequest(
                "s1", "帮我看看朝阳店的情况",
                "https://api.example.com", "sk-test", "gpt-4.1-mini"
        );
        ChatModel chatModel = mock(ChatModel.class);

        when(languageDetector.detectLanguage(request.message())).thenReturn("zh");
        when(modelConfigService.createChatModel(request)).thenReturn(chatModel);
        when(promptFactory.buildConversationModelPrompt(eq("zh"), eq(request.message()), anyString()))
                .thenReturn("Prompt body");
        when(promptFactory.buildSystemPrompt("zh")).thenReturn("System prompt");
        // Model generates irrelevant follow-ups (no metric/scenario keywords)
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage(
                        "朝阳店本月达成率为72%。\n\nFOLLOW_UP_QUESTIONS:\n1. 你想了解什么？\n2. 还有其他需要帮助的吗？"
                ))
        )));

        String reply = chatService.chat(request);

        // Irrelevant follow-ups should be replaced with contextual defaults
        assertThat(reply).doesNotContain("你想了解什么");
        assertThat(reply).doesNotContain("还有其他需要帮助的吗");
        assertThat(reply).contains("FOLLOW_UP_QUESTIONS:");
    }

    private List<String> extractEventData(String payload, String eventName) {
        String normalizedPayload = payload.replace("\r\n", "\n");
        List<String> matchingData = new ArrayList<>();

        for (String block : normalizedPayload.split("\n\n")) {
            if (block.isBlank()) {
                continue;
            }

            String event = null;
            List<String> dataLines = new ArrayList<>();
            for (String line : block.split("\n", -1)) {
                if (line.startsWith("event: ")) {
                    event = line.substring(7);
                    continue;
                }
                if (line.startsWith("data: ")) {
                    dataLines.add(line.substring(6));
                }
            }

            if (eventName.equals(event)) {
                matchingData.add(String.join("\n", dataLines));
            }
        }

        return matchingData;
    }

    private AnalyticsPlan analyticsPlan(AnalyticsPlan.Scenario scenario, String fallbackReply) {
        return new AnalyticsPlan(
                scenario,
                null,
                "the current sample dataset",
                List.of("Identifying the analysis theme", "Filtering target data", "Generating a structured response"),
                "1. Classify the request\n2. Compute KPIs\n3. Produce the report",
                "Scenario: %s\nScope: the current sample dataset\nLanguage: en".formatted(scenario.name()),
                fallbackReply
        );
    }

    private String englishAnalyticsFallbackReport() {
        return """
                ## Interface Call Chain
                1. Current date: 2026-05-20.
                ## Conclusion
                fallback
                ## Data Support
                <table><tr><td>fallback</td></tr></table>
                ## Short Analysis
                fallback
                ## Problem Diagnosis & Solutions
                fallback
                ## Improvement Suggestions
                fallback
                FOLLOW_UP_QUESTIONS:
                1. next?
                2. next?
                """;
    }

    private String chineseAnalyticsFallbackReport() {
        return """
                ## 接口调用链
                1. 当前日期：2026-05-20。
                ## 核心结论
                fallback
                ## 数据支撑
                <table><tr><td>fallback</td></tr></table>
                ## 经营分析
                fallback
                ## 问题诊断与解决
                fallback
                ## 改进建议
                fallback
                追问：
                1. next?
                2. next?
                """;
    }

    private String chineseAnalyticsValidReply() {
        return """
                ## 1. 接口调用链
                1. 当前日期：2026-05-20。
                ## 2. 核心结论
                fallback
                ## 3. 数据支撑
                <table><tr><td>fallback</td></tr></table>
                ## 4. 经营分析
                fallback
                ## 5. 问题诊断与解决
                fallback
                ## 6. 改进建议
                fallback
                追问：
                1. a
                2. b
                """;
    }
}
