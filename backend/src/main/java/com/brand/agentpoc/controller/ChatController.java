package com.brand.agentpoc.controller;

import com.brand.agentpoc.dto.request.ChatRequest;
import com.brand.agentpoc.dto.response.ChatResponse;
import com.brand.agentpoc.dto.response.SimpleSuccessResponse;
import com.brand.agentpoc.service.ChatService;
import com.brand.agentpoc.service.SessionMemoryService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final SessionMemoryService sessionMemoryService;

    public ChatController(ChatService chatService, SessionMemoryService sessionMemoryService) {
        this.chatService = chatService;
        this.sessionMemoryService = sessionMemoryService;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return new ChatResponse(chatService.chat(request));
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(@Valid @RequestBody ChatRequest request) {
        StreamingResponseBody responseBody = outputStream -> chatService.streamChat(request, outputStream);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(responseBody);
    }

    @DeleteMapping("/{sessionId}")
    public SimpleSuccessResponse clearSession(@PathVariable String sessionId) {
        sessionMemoryService.clearSession(sessionId);
        return new SimpleSuccessResponse(true);
    }
}

