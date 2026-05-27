package com.brand.agentpoc.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brand.agentpoc.config.SessionTokenFilter;
import com.brand.agentpoc.service.ChatService;
import com.brand.agentpoc.service.SessionMemoryService;
import com.brand.agentpoc.service.SessionOwnershipService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
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
    void claimsSessionBeforeHandlingChatRequest() throws Exception {
        when(sessionOwnershipService.claimOrVerify("session-1", "token-subject")).thenReturn(true);
        when(chatService.chat(any())).thenReturn("hello");

        mockMvc.perform(post("/api/chat")
                        .requestAttr(SessionTokenFilter.TOKEN_SUBJECT_ATTRIBUTE, "token-subject")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"sessionId":"session-1","message":"hi"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("hello"));

        verify(sessionOwnershipService).claimOrVerify("session-1", "token-subject");
    }

    @Test
    void rejectsChatRequestWhenTokenDoesNotOwnSession() throws Exception {
        when(sessionOwnershipService.claimOrVerify("session-1", "other-token")).thenReturn(false);

        mockMvc.perform(post("/api/chat")
                        .requestAttr(SessionTokenFilter.TOKEN_SUBJECT_ATTRIBUTE, "other-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"sessionId":"session-1","message":"hi"}
                                """))
                .andExpect(status().isForbidden());

        verify(chatService, never()).chat(any());
    }

    @Test
    void rejectsStreamRequestWhenTokenDoesNotOwnSession() throws Exception {
        when(sessionOwnershipService.claimOrVerify("session-1", "other-token")).thenReturn(false);

        mockMvc.perform(post("/api/chat/stream")
                        .requestAttr(SessionTokenFilter.TOKEN_SUBJECT_ATTRIBUTE, "other-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"sessionId":"session-1","message":"hi"}
                                """))
                .andExpect(status().isForbidden());

        verify(chatService, never()).streamChat(any(), any());
    }

    @Test
    void createsSseResponseForStreamRequest() throws Exception {
        when(sessionOwnershipService.claimOrVerify("session-1", "token-subject")).thenReturn(true);
        doAnswer(invocation -> null).when(chatService).streamChat(any(), any());

        MvcResult mvcResult = mockMvc.perform(post("/api/chat/stream")
                        .requestAttr(SessionTokenFilter.TOKEN_SUBJECT_ATTRIBUTE, "token-subject")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"sessionId":"session-1","message":"hi"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        verify(chatService).streamChat(any(), any());
    }

    @Test
    void streamsErrorEventsWrittenByService() throws Exception {
        when(sessionOwnershipService.claimOrVerify("session-1", "token-subject")).thenReturn(true);
        doAnswer(invocation -> {
            invocation.getArgument(1, java.io.OutputStream.class)
                    .write("event: error\ndata: failed\n\n".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(chatService).streamChat(any(), any());

        MvcResult mvcResult = mockMvc.perform(post("/api/chat/stream")
                        .requestAttr(SessionTokenFilter.TOKEN_SUBJECT_ATTRIBUTE, "token-subject")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"sessionId":"session-1","message":"hi"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: error")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data: failed")));
    }

    @Test
    void clearsSessionWhenTokenOwnsSession() throws Exception {
        when(sessionOwnershipService.owns("session-1", "token-subject")).thenReturn(true);

        mockMvc.perform(delete("/api/chat/session-1")
                        .requestAttr(SessionTokenFilter.TOKEN_SUBJECT_ATTRIBUTE, "token-subject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sessionOwnershipService).owns("session-1", "token-subject");
        verify(sessionOwnershipService, never()).claimOrVerify("session-1", "token-subject");
        verify(sessionMemoryService).clearSession("session-1");
        verify(sessionOwnershipService).release("session-1", "token-subject");
    }

    @Test
    void rejectsClearSessionWhenTokenDoesNotOwnSession() throws Exception {
        when(sessionOwnershipService.owns("session-1", "other-token")).thenReturn(false);

        mockMvc.perform(delete("/api/chat/session-1")
                        .requestAttr(SessionTokenFilter.TOKEN_SUBJECT_ATTRIBUTE, "other-token"))
                .andExpect(status().isForbidden());

        verify(sessionOwnershipService).owns("session-1", "other-token");
        verify(sessionOwnershipService, never()).claimOrVerify("session-1", "other-token");
        verify(sessionMemoryService, never()).clearSession("session-1");
        verify(sessionOwnershipService, never()).release("session-1", "other-token");
    }

    @Test
    void rejectsClearSessionWhenSessionWasNeverRegistered() throws Exception {
        when(sessionOwnershipService.owns("missing-session", "token-subject")).thenReturn(false);

        mockMvc.perform(delete("/api/chat/missing-session")
                        .requestAttr(SessionTokenFilter.TOKEN_SUBJECT_ATTRIBUTE, "token-subject"))
                .andExpect(status().isForbidden());

        verify(sessionOwnershipService).owns("missing-session", "token-subject");
        verify(sessionOwnershipService, never()).claimOrVerify("missing-session", "token-subject");
        verify(sessionMemoryService, never()).clearSession("missing-session");
        verify(sessionOwnershipService, never()).release("missing-session", "token-subject");
    }
}
