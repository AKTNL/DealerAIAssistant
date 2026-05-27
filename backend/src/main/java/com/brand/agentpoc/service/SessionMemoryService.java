package com.brand.agentpoc.service;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SessionMemoryService {

    private final InMemoryChatMemory chatMemory;

    public SessionMemoryService(InMemoryChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    public void addUserMessage(String sessionId, String message) {
        chatMemory.addUserMessage(sessionId, message);
    }

    public void addAssistantMessage(String sessionId, String message) {
        chatMemory.addAssistantMessage(sessionId, message);
    }

    public List<String> getMessages(String sessionId) {
        return chatMemory.getMessages(sessionId);
    }

    public void clearSession(String sessionId) {
        chatMemory.clearSession(sessionId);
    }
}
