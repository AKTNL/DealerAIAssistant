package com.brand.agentpoc.service;

import java.util.List;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

@Component
public class InMemoryChatMemory {

    private static final int MAX_MESSAGES = 20;

    private final ChatMemory chatMemory;

    public InMemoryChatMemory() {
        this(MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(MAX_MESSAGES)
                .build());
    }

    InMemoryChatMemory(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    public void addUserMessage(String sessionId, String message) {
        chatMemory.add(sessionId, new UserMessage(message));
    }

    public void addAssistantMessage(String sessionId, String message) {
        chatMemory.add(sessionId, new AssistantMessage(message));
    }

    public List<String> getMessages(String sessionId) {
        return chatMemory.get(sessionId).stream()
                .map(this::formatMessage)
                .toList();
    }

    public void clearSession(String sessionId) {
        chatMemory.clear(sessionId);
    }

    private String formatMessage(Message message) {
        return switch (message.getMessageType()) {
            case USER -> "USER:" + message.getText();
            case ASSISTANT -> "ASSISTANT:" + message.getText();
            default -> messageTypePrefix(message.getMessageType()) + ":" + message.getText();
        };
    }

    private String messageTypePrefix(MessageType messageType) {
        return messageType == null ? "UNKNOWN" : messageType.getValue().toUpperCase();
    }
}
