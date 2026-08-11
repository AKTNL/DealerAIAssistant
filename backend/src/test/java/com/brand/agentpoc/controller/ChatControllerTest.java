package com.brand.agentpoc.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brand.agentpoc.agent.domain.AgentRequestScope;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.dto.request.ChatRequest;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationAuthorizationContext;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.service.ChatService;
import com.brand.agentpoc.service.SessionMemoryService;
import com.brand.agentpoc.service.SessionOwnershipService;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ChatControllerTest {

    private ChatService chatService;
    private SessionMemoryService sessionMemoryService;
    private SessionOwnershipService sessionOwnershipService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        sessionMemoryService = mock(SessionMemoryService.class);
        sessionOwnershipService = mock(SessionOwnershipService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ChatController(chatService, sessionMemoryService, sessionOwnershipService))
                .setValidator(validator)
                .build();
    }

    @Test
    void claimsSessionUsingTheStableUserIdentity() throws Exception {
        when(sessionOwnershipService.claimOrVerify("session-1", "42")).thenReturn(true);
        when(chatService.chat(any(ChatRequest.class), any(AgentRequestScope.class))).thenReturn("hello");

        mockMvc.perform(post("/api/chat")
                        .principal(authentication(42L))
                        .contentType(APPLICATION_JSON)
                        .content("{\"sessionId\":\"session-1\",\"message\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("hello"));

        verify(chatService).chat(any(ChatRequest.class), eq(scope("session-1", 42L)));
    }

    @Test
    void rejectsChatWhenTheUserDoesNotOwnTheConversation() throws Exception {
        when(sessionOwnershipService.claimOrVerify("session-1", "43")).thenReturn(false);

        mockMvc.perform(post("/api/chat")
                        .principal(authentication(43L))
                        .contentType(APPLICATION_JSON)
                        .content("{\"sessionId\":\"session-1\",\"message\":\"hi\"}"))
                .andExpect(status().isForbidden());

        verify(chatService, never()).chat(any(), any());
    }

    @Test
    void createsTheSseResponseWithTheSameUserScope() throws Exception {
        when(sessionOwnershipService.claimOrVerify("session-1", "42")).thenReturn(true);
        doAnswer(invocation -> null).when(chatService).streamChat(any(), any(OutputStream.class), any());

        MvcResult result = mockMvc.perform(post("/api/chat/stream")
                        .principal(authentication(42L))
                        .contentType(APPLICATION_JSON)
                        .content("{\"sessionId\":\"session-1\",\"message\":\"hi\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
        verify(chatService).streamChat(any(), any(OutputStream.class), eq(scope("session-1", 42L)));
    }

    @Test
    void propagatesTheSameResolvedOrganizationScopeToSyncAndSseChat() throws Exception {
        OrganizationAuthorizationService authorizationService = mock(OrganizationAuthorizationService.class);
        OrganizationDataScope dataScope = new OrganizationDataScope(
                Set.of(2L, 3L, 4L), Set.of(2L), Set.of("NORTH-1"), false, false);
        when(authorizationService.resolve(any(AuthPrincipal.class)))
                .thenReturn(new OrganizationAuthorizationContext(principal(42L), dataScope));
        when(sessionOwnershipService.claimOrVerify("session-1", "42")).thenReturn(true);
        when(chatService.chat(any(ChatRequest.class), any(AgentRequestScope.class))).thenReturn("hello");
        doAnswer(invocation -> null).when(chatService).streamChat(any(), any(OutputStream.class), any());
        MockMvc scopedMockMvc = MockMvcBuilders.standaloneSetup(new ChatController(
                        chatService,
                        sessionMemoryService,
                        sessionOwnershipService,
                        authorizationService
                ))
                .build();
        AgentRequestScope expectedScope = AgentRequestScope.authenticated(
                "session-1", "42", EnumSet.allOf(PermissionKey.class), dataScope);

        scopedMockMvc.perform(post("/api/chat")
                        .principal(authentication(42L))
                        .contentType(APPLICATION_JSON)
                        .content("{\"sessionId\":\"session-1\",\"message\":\"hi\"}"))
                .andExpect(status().isOk());

        MvcResult streamResult = scopedMockMvc.perform(post("/api/chat/stream")
                        .principal(authentication(42L))
                        .contentType(APPLICATION_JSON)
                        .content("{\"sessionId\":\"session-1\",\"message\":\"hi\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        scopedMockMvc.perform(asyncDispatch(streamResult)).andExpect(status().isOk());

        verify(chatService).chat(any(ChatRequest.class), eq(expectedScope));
        verify(chatService).streamChat(any(), any(OutputStream.class), eq(expectedScope));
    }

    @Test
    void clearsOnlyAConversationOwnedByTheCurrentUser() throws Exception {
        when(sessionOwnershipService.owns("session-1", "42")).thenReturn(true);

        mockMvc.perform(delete("/api/chat/session-1").principal(authentication(42L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sessionMemoryService).clearSession("session-1");
        verify(sessionOwnershipService).release("session-1", "42");
    }

    private AgentRequestScope scope(String sessionId, Long userId) {
        return AgentRequestScope.authenticated(sessionId, String.valueOf(userId), EnumSet.allOf(PermissionKey.class));
    }

    private UsernamePasswordAuthenticationToken authentication(Long userId) {
        return UsernamePasswordAuthenticationToken.authenticated(principal(userId), "token", Set.of());
    }

    private AuthPrincipal principal(Long userId) {
        return new AuthPrincipal(
                userId, 100L + userId, "family", "user-" + userId, "User " + userId,
                true, false, Set.of("ADMIN"), EnumSet.allOf(PermissionKey.class)
        );
    }
}
