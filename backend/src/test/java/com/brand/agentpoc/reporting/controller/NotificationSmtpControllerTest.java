package com.brand.agentpoc.reporting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.reporting.application.SmtpConfigurationTestService;
import com.brand.agentpoc.reporting.application.SmtpConfigurationTestService.SmtpTestView;
import com.brand.agentpoc.reporting.application.TenantSmtpConfigRegistry;
import com.brand.agentpoc.reporting.application.TenantSmtpConfigRegistry.SmtpConfigView;
import com.brand.agentpoc.reporting.domain.SmtpSecurityMode;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class NotificationSmtpControllerTest {

    private TenantSmtpConfigRegistry registry;
    private SmtpConfigurationTestService testService;
    private AuthPrincipal actor;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registry = mock(TenantSmtpConfigRegistry.class);
        testService = mock(SmtpConfigurationTestService.class);
        actor = new AuthPrincipal(
                2L, 3L, "family", "admin", "Admin", true, false,
                Set.of("ADMIN"), Set.of(), 7L, "tenant-a", 8L, Set.of(11L));
        SmtpConfigView view = view();
        when(registry.view(7L)).thenReturn(Optional.of(view));
        when(registry.save(eq(7L), eq(2L), any(), any())).thenReturn(view);
        when(testService.send(eq(actor), any()))
                .thenReturn(new SmtpTestView(true, "SMTP_ACCEPTED", view.updatedAt()));
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new NotificationSmtpController(registry, testService))
                .setCustomArgumentResolvers(principalResolver(actor))
                .build();
    }

    @Test
    void readsAndSavesOnlyRedactedConfigurationFields() throws Exception {
        mockMvc.perform(get("/api/notification/smtp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.host").value("smtp.example.com"))
                .andExpect(jsonPath("$.data.passwordConfigured").value(true))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordCiphertext").doesNotExist());

        mockMvc.perform(put("/api/notification/smtp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordConfigured").value(true))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void returnsOnlySafeTestResult() throws Exception {
        mockMvc.perform(post("/api/notification/smtp/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true))
                .andExpect(jsonPath("$.data.code").value("SMTP_ACCEPTED"))
                .andExpect(jsonPath("$.data.message").doesNotExist());
    }

    @Test
    void mapsConcurrentDeleteToConflict() throws Exception {
        doThrow(new OptimisticLockingFailureException("stale"))
                .when(registry).delete(eq(7L), eq(2L), eq(3L), any());

        mockMvc.perform(delete("/api/notification/smtp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The resource changed since it was loaded."));
    }

    private SmtpConfigView view() {
        return new SmtpConfigView(
                "smtp.example.com", 587, SmtpSecurityMode.STARTTLS, "smtp-user",
                "reports@example.com", "Dealer AI", true, true, 3L,
                Instant.parse("2026-08-14T02:00:00Z"));
    }

    private String validRequest() {
        return """
                {
                  "host":"smtp.example.com",
                  "port":587,
                  "securityMode":"STARTTLS",
                  "username":"smtp-user",
                  "password":"write-only-secret",
                  "fromAddress":"reports@example.com",
                  "fromDisplayName":"Dealer AI",
                  "enabled":true,
                  "version":3
                }
                """;
    }

    private HandlerMethodArgumentResolver principalResolver(AuthPrincipal principal) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(AuthPrincipal.class);
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer container,
                    NativeWebRequest request,
                    WebDataBinderFactory binderFactory
            ) {
                return principal;
            }
        };
    }
}
