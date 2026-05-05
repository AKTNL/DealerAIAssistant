package com.brand.agentpoc.service;

import com.brand.agentpoc.ai.LanguageDetector;
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
    private final RuleBasedAnalyticsService analyticsService;

    public ChatService(
            SessionMemoryService sessionMemoryService,
            LanguageDetector languageDetector,
            RuleBasedAnalyticsService analyticsService
    ) {
        this.sessionMemoryService = sessionMemoryService;
        this.languageDetector = languageDetector;
        this.analyticsService = analyticsService;
    }

    public String chat(ChatRequest request) {
        String language = languageDetector.detectLanguage(request.message());
        RuleBasedAnalyticsService.AnalyticsResponse analyticsResponse = analyticsService.analyze(request.message(), language);
        sessionMemoryService.addUserMessage(request.sessionId(), request.message());
        sessionMemoryService.addAssistantMessage(request.sessionId(), analyticsResponse.reply());
        return analyticsResponse.reply();
    }

    public void streamChat(ChatRequest request, OutputStream outputStream) throws IOException {
        String language = languageDetector.detectLanguage(request.message());
        RuleBasedAnalyticsService.AnalyticsResponse analyticsResponse = analyticsService.analyze(request.message(), language);

        sessionMemoryService.addUserMessage(request.sessionId(), request.message());

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            for (String progress : analyticsResponse.progressMessages()) {
                writeEvent(writer, "progress", progress);
            }
            writeEvent(writer, "message", "<think>" + analyticsResponse.visibleThinking() + "</think>");
            writeEvent(writer, "message", analyticsResponse.reply());
            writeEvent(writer, "done", "[DONE]");
            sessionMemoryService.addAssistantMessage(request.sessionId(), analyticsResponse.reply());
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
}
