package com.brand.agentpoc.controller;

import com.brand.agentpoc.agent.domain.AgentRequestScope;
import com.brand.agentpoc.dto.request.ChatRequest;
import com.brand.agentpoc.dto.response.ChatResponse;
import com.brand.agentpoc.dto.response.SimpleSuccessResponse;
import com.brand.agentpoc.config.SessionTokenFilter;
import com.brand.agentpoc.service.ChatService;
import com.brand.agentpoc.service.SessionMemoryService;
import com.brand.agentpoc.service.SessionOwnershipService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    private final SessionOwnershipService sessionOwnershipService;

    public ChatController(
            ChatService chatService,
            SessionMemoryService sessionMemoryService,
            SessionOwnershipService sessionOwnershipService
    ) {
        this.chatService = chatService;
        this.sessionMemoryService = sessionMemoryService;
        this.sessionOwnershipService = sessionOwnershipService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest servletRequest
    ) {
        String tokenSubject = tokenSubject(servletRequest);
        if (!sessionOwnershipService.claimOrVerify(request.sessionId(), tokenSubject)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        AgentRequestScope agentScope = AgentRequestScope.authenticated(request.sessionId(), tokenSubject);
        return ResponseEntity.ok(new ChatResponse(chatService.chat(request, agentScope)));
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest servletRequest
    ) {
        String tokenSubject = tokenSubject(servletRequest);
        if (!sessionOwnershipService.claimOrVerify(request.sessionId(), tokenSubject)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        AgentRequestScope agentScope = AgentRequestScope.authenticated(request.sessionId(), tokenSubject);
        StreamingResponseBody responseBody = outputStream -> chatService.streamChat(request, outputStream, agentScope);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(responseBody);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<SimpleSuccessResponse> clearSession(
            @PathVariable String sessionId,
            HttpServletRequest servletRequest
    ) {
        String tokenSubject = tokenSubject(servletRequest);
        if (!sessionOwnershipService.owns(sessionId, tokenSubject)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        sessionMemoryService.clearSession(sessionId);
        sessionOwnershipService.release(sessionId, tokenSubject);
        return ResponseEntity.ok(new SimpleSuccessResponse(true));
    }

    private String tokenSubject(HttpServletRequest request) {
        Object tokenSubject = request.getAttribute(SessionTokenFilter.TOKEN_SUBJECT_ATTRIBUTE);
        return tokenSubject instanceof String value ? value : "";
    }
}

