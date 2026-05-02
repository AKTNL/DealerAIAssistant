package com.brand.agentpoc.service;

import com.brand.agentpoc.ai.LanguageDetector;
import com.brand.agentpoc.ai.PromptFactory;
import com.brand.agentpoc.dto.request.ChatRequest;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final SessionMemoryService sessionMemoryService;
    private final LanguageDetector languageDetector;
    private final PromptFactory promptFactory;

    public ChatService(
            SessionMemoryService sessionMemoryService,
            LanguageDetector languageDetector,
            PromptFactory promptFactory
    ) {
        this.sessionMemoryService = sessionMemoryService;
        this.languageDetector = languageDetector;
        this.promptFactory = promptFactory;
    }

    public String chat(ChatRequest request) {
        String language = languageDetector.detectLanguage(request.message());
        String reply = buildPlaceholderReply(language, request.message());
        sessionMemoryService.addUserMessage(request.sessionId(), request.message());
        sessionMemoryService.addAssistantMessage(request.sessionId(), reply);
        return reply;
    }

    public void streamChat(ChatRequest request, OutputStream outputStream) throws IOException {
        String language = languageDetector.detectLanguage(request.message());
        String reply = buildPlaceholderReply(language, request.message());

        sessionMemoryService.addUserMessage(request.sessionId(), request.message());

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            writeEvent(writer, "progress", language.equals("zh") ? "正在准备占位响应" : "Preparing placeholder response");
            writeEvent(writer, "message", "<think>" + promptFactory.buildVisibleThinking(language) + "</think>");
            writeEvent(writer, "message", reply);
            writeEvent(writer, "done", "[DONE]");
            sessionMemoryService.addAssistantMessage(request.sessionId(), reply);
        }
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

    private String buildPlaceholderReply(String language, String userMessage) {
        if ("zh".equals(language)) {
            return """
                    ## 占位响应

                    当前 `backend` 骨架已经可接入聊天链路，但这里仍是占位实现，尚未连接真实模型和 Excel 数据。

                    - 当前问题：%s
                    - 下一步：接入 Excel 导入、查询 API 和 Spring AI tool 调用

                    FOLLOW_UP_QUESTIONS:
                    1. 先把 Excel 导入模块补上
                    2. 继续把六类查询接口从占位改成真实实现
                    """.formatted(userMessage);
        }

        return """
                ## Placeholder Response

                The backend scaffold is ready for the chat flow, but this endpoint is still a placeholder and is not connected to the model or Excel data yet.

                - Current question: %s
                - Next step: implement Excel import, query APIs, and Spring AI tool calls

                FOLLOW_UP_QUESTIONS:
                1. Implement the Excel import module next
                2. Replace placeholder data endpoints with real queries
                """.formatted(userMessage);
    }
}

