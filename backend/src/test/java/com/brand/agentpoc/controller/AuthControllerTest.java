package com.brand.agentpoc.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brand.agentpoc.service.AuthService;
import com.brand.agentpoc.service.AuthRateLimitService;
import com.brand.agentpoc.service.SessionTokenService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AuthControllerTest {

    private AuthService authService;
    private AuthRateLimitService authRateLimitService;
    private SessionTokenService sessionTokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        authRateLimitService = mock(AuthRateLimitService.class);
        sessionTokenService = mock(SessionTokenService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, authRateLimitService, sessionTokenService))
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsTokenWhenAccessKeyIsValid() throws Exception {
        when(authService.verifyAccessKey("configured-key")).thenReturn(true);
        when(sessionTokenService.issueToken()).thenReturn(new SessionTokenService.IssuedToken(
                "signed-token",
                Instant.parse("2026-05-21T16:00:00Z")
        ));

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(APPLICATION_JSON)
                        .content("{\"key\":\"configured-key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sessionToken").value("signed-token"))
                .andExpect(jsonPath("$.expiresAt").value("2026-05-21T16:00:00Z"));

        verify(authRateLimitService).recordSuccess("127.0.0.1");
    }

    @Test
    void omitsTokenWhenAccessKeyIsInvalid() throws Exception {
        when(authService.verifyAccessKey("wrong")).thenReturn(false);

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(APPLICATION_JSON)
                        .content("{\"key\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.sessionToken").doesNotExist())
                .andExpect(jsonPath("$.expiresAt").doesNotExist());

        verify(authRateLimitService).recordFailure("127.0.0.1");
    }

    @Test
    void omitsTokenWhenSessionTokenCannotBeIssued() throws Exception {
        when(authService.verifyAccessKey("configured-key")).thenReturn(true);
        when(sessionTokenService.issueToken())
                .thenThrow(new IllegalStateException("app.auth.session-secret is required to sign session tokens."));

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(APPLICATION_JSON)
                        .content("{\"key\":\"configured-key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.sessionToken").doesNotExist())
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    void rejectsRateLimitedClientsBeforeCheckingAccessKey() throws Exception {
        when(authRateLimitService.isLimited("127.0.0.1")).thenReturn(true);
        when(authRateLimitService.retryAfterSeconds("127.0.0.1")).thenReturn(300L);

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(APPLICATION_JSON)
                        .content("{\"key\":\"configured-key\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.sessionToken").doesNotExist())
                .andExpect(jsonPath("$.expiresAt").doesNotExist());

        verify(authService, never()).verifyAccessKey("configured-key");
        verify(sessionTokenService, never()).issueToken();
    }
}
