package com.brand.agentpoc.reporting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationAuthorizationContext;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.application.ReportDeliveryService;
import com.brand.agentpoc.reporting.application.ReportDeliveryService.DeliveryView;
import com.brand.agentpoc.reporting.domain.ReportDeliveryStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ReportDeliveryControllerTest {

    private ReportDeliveryService service;
    private AuthPrincipal actor;
    private OrganizationDataScope scope;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ReportDeliveryService.class);
        OrganizationAuthorizationService authorizationService = mock(OrganizationAuthorizationService.class);
        actor = new AuthPrincipal(
                2L, 3L, "family", "analyst", "Analyst", true, false,
                Set.of("ANALYST"), Set.of(), 7L, "tenant-a", 8L, Set.of(11L));
        scope = OrganizationDataScope.tenantScope(
                7L, "tenant-a", Set.of(), Set.of(), Set.of(), false);
        when(authorizationService.resolve(actor))
                .thenReturn(new OrganizationAuthorizationContext(actor, scope));
        when(service.list(actor, scope)).thenReturn(List.of(view(ReportDeliveryStatus.UNKNOWN)));
        when(service.manualRetry(eq(actor), eq(scope), eq(21L), any()))
                .thenReturn(view(ReportDeliveryStatus.READY));
        when(service.forceReplay(eq(actor), eq(scope), eq(21L), eq(true), any()))
                .thenReturn(view(ReportDeliveryStatus.READY));
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ReportDeliveryController(service, authorizationService))
                .setCustomArgumentResolvers(principalResolver(actor))
                .build();
    }

    @Test
    void listsOnlySafeDeliveryMetadataInsideEnvelope() throws Exception {
        mockMvc.perform(get("/api/report-deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("UNKNOWN"))
                .andExpect(jsonPath("$.data[0].recipientUserId").value(3))
                .andExpect(jsonPath("$.data[0].recipientEmail").doesNotExist())
                .andExpect(jsonPath("$.data[0].providerMessageId").doesNotExist());
    }

    @Test
    void retriesExplicitFailureAndRequiresForceReplayAcknowledgement() throws Exception {
        mockMvc.perform(post("/api/report-deliveries/21/retry")
                        .header("X-Request-ID", "request-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"));

        mockMvc.perform(post("/api/report-deliveries/21/force-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acknowledgeDuplicateRisk\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"));
    }

    private DeliveryView view(ReportDeliveryStatus status) {
        Instant now = Instant.parse("2026-08-14T02:00:00Z");
        return new DeliveryView(
                21L, 11L, 9L, 3L, "email", status,
                1, 4, null, status == ReportDeliveryStatus.UNKNOWN ? "SMTP_TIMEOUT_UNKNOWN" : null,
                now, now, 1L);
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
