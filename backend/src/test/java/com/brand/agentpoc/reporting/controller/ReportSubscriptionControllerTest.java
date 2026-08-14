package com.brand.agentpoc.reporting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationAuthorizationContext;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.application.ReportSubscriptionService;
import com.brand.agentpoc.reporting.application.ReportSubscriptionService.RecipientView;
import com.brand.agentpoc.reporting.application.ReportSubscriptionService.ReportSubscriptionView;
import com.brand.agentpoc.reporting.domain.ReportScope;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ReportSubscriptionControllerTest {

    private ReportSubscriptionService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ReportSubscriptionService.class);
        OrganizationAuthorizationService authorizationService = mock(OrganizationAuthorizationService.class);
        AuthPrincipal actor = new AuthPrincipal(
                2L, 3L, "family", "analyst", "Analyst", true, false,
                Set.of("ANALYST"), Set.of(), 7L, "tenant-a", 8L, Set.of(11L));
        OrganizationDataScope scope = OrganizationDataScope.unrestrictedScope();
        when(authorizationService.resolve(actor))
                .thenReturn(new OrganizationAuthorizationContext(actor, scope));
        when(service.list(actor, scope)).thenReturn(List.of(view()));
        when(service.listRecipients(actor, scope))
                .thenReturn(List.of(new RecipientView(2L, "analyst", "Analyst")));
        when(service.create(any(), any(), any(), anyBoolean(), anyString()))
                .thenReturn(view());

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ReportSubscriptionController(service, authorizationService))
                .setCustomArgumentResolvers(principalResolver(actor))
                .setValidator(validator)
                .build();
    }

    @Test
    void listsRecipientsAndCreatesSubscriptionInsideEnvelope() throws Exception {
        mockMvc.perform(get("/api/report-subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].scheduleKind").value("DAILY"));
        mockMvc.perform(get("/api/report-subscriptions/recipients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(2));
        mockMvc.perform(post("/api/report-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduleKind").value("DAILY"));
    }

    @Test
    void rejectsMissingScheduleAndRecipientFields() throws Exception {
        mockMvc.perform(post("/api/report-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportType\":\"daily\",\"language\":\"zh\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletesSubscriptionWithOptimisticVersion() throws Exception {
        mockMvc.perform(delete("/api/report-subscriptions/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void rejectsEnablementAndDeleteRequestsWithoutVersion() throws Exception {
        mockMvc.perform(delete("/api/report-subscriptions/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/report-subscriptions/9/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isBadRequest());
    }

    private String validRequest() {
        return """
                {
                  "reportType":"daily",
                  "language":"zh",
                  "scheduleKind":"DAILY",
                  "localTime":"09:00",
                  "timeZone":"Asia/Shanghai",
                  "channelKey":"email",
                  "recipientUserIds":[2],
                  "enabled":true
                }
                """;
    }

    private ReportSubscriptionView view() {
        return new ReportSubscriptionView(
                9L, "daily", "zh", "", new ReportScope("ORGANIZATION", "10"),
                "DAILY", "09:00", "Asia/Shanghai", null, null,
                "email", Set.of(2L), true, Instant.parse("2026-08-14T01:00:00Z"),
                "SKIP", 60, true, "eligible",
                Instant.parse("2026-08-13T00:00:00Z"),
                Instant.parse("2026-08-13T00:00:00Z"), 2L);
    }

    private HandlerMethodArgumentResolver principalResolver(AuthPrincipal actor) {
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
                return actor;
            }
        };
    }
}
