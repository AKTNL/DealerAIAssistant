package com.brand.agentpoc.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SessionMemoryService {

    private final Map<String, List<String>> sessions = new ConcurrentHashMap<>();

    public void addUserMessage(String sessionId, String message) {
        sessions.computeIfAbsent(sessionId, ignored -> new ArrayList<>()).add("USER:" + message);
    }

    public void addAssistantMessage(String sessionId, String message) {
        sessions.computeIfAbsent(sessionId, ignored -> new ArrayList<>()).add("ASSISTANT:" + message);
    }

    public List<String> getMessages(String sessionId) {
        return sessions.getOrDefault(sessionId, List.of());
    }

    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }
}

