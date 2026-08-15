package com.brand.agentpoc.controller;

import com.brand.agentpoc.agent.domain.AgentRequestScope;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.observability.infrastructure.web.AsyncTraceContext;
import com.brand.agentpoc.observability.infrastructure.web.RequestCorrelation;
import com.brand.agentpoc.dto.request.ChatRequest;
import com.brand.agentpoc.dto.response.ChatResponse;
import com.brand.agentpoc.dto.response.SimpleSuccessResponse;
import com.brand.agentpoc.service.ChatService;
import com.brand.agentpoc.service.SessionMemoryService;
import com.brand.agentpoc.service.SessionOwnershipService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    private final OrganizationAuthorizationService authorizationService;

    public ChatController(
            ChatService chatService,
            SessionMemoryService sessionMemoryService,
            SessionOwnershipService sessionOwnershipService
    ) {
        this(chatService, sessionMemoryService, sessionOwnershipService, null);
    }

    @Autowired
    public ChatController(
            ChatService chatService,
            SessionMemoryService sessionMemoryService,
            SessionOwnershipService sessionOwnershipService,
            OrganizationAuthorizationService authorizationService
    ) {
        this.chatService = chatService;
        this.sessionMemoryService = sessionMemoryService;
        this.sessionOwnershipService = sessionOwnershipService;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        AuthPrincipal principal = principal(authentication);
        String tokenSubject = principal.stableSubject();
        if (!sessionOwnershipService.claimOrVerify(request.sessionId(), tokenSubject)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        AgentRequestScope agentScope = AgentRequestScope.authenticated(
                request.sessionId(), tokenSubject, principal.permissions(), dataScope(principal));
        chatService.authorizeRequest(request, agentScope);
        return ResponseEntity.ok(new ChatResponse(
                chatService.chat(request, agentScope, RequestCorrelation.traceId(servletRequest))));
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        AuthPrincipal principal = principal(authentication);
        String tokenSubject = principal.stableSubject();
        if (!sessionOwnershipService.claimOrVerify(request.sessionId(), tokenSubject)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        AgentRequestScope agentScope = AgentRequestScope.authenticated(
                request.sessionId(), tokenSubject, principal.permissions(), dataScope(principal));
        chatService.authorizeRequest(request, agentScope);
        String requestId = RequestCorrelation.requestId(servletRequest);
        String traceId = RequestCorrelation.traceId(servletRequest);
        AsyncTraceContext asyncTraceContext = AsyncTraceContext.capture(requestId);
        StreamingResponseBody responseBody = outputStream -> asyncTraceContext.run(
                () -> chatService.streamChat(request, outputStream, agentScope, traceId));
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(responseBody);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<SimpleSuccessResponse> clearSession(
            @PathVariable String sessionId,
            Authentication authentication
    ) {
        AuthPrincipal principal = principal(authentication);
        String tokenSubject = principal.stableSubject();
        if (!sessionOwnershipService.owns(sessionId, tokenSubject)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        sessionMemoryService.clearSession(sessionId);
        sessionOwnershipService.release(sessionId, tokenSubject);
        return ResponseEntity.ok(new SimpleSuccessResponse(true));
    }

    private AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }

    private OrganizationDataScope dataScope(AuthPrincipal principal) {
        return authorizationService == null
                ? OrganizationDataScope.unrestrictedScope()
                : authorizationService.resolve(principal).dataScope();
    }

}

