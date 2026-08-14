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
import com.brand.agentpoc.reporting.application.ReportGenerationJobService;
import com.brand.agentpoc.reporting.application.ReportGenerationJobService.JobView;
import com.brand.agentpoc.reporting.domain.ReportGenerationJobStatus;
import com.brand.agentpoc.reporting.domain.ReportScope;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ReportGenerationJobControllerTest {

    private ReportGenerationJobService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ReportGenerationJobService.class);
        OrganizationAuthorizationService authorizationService = mock(OrganizationAuthorizationService.class);
        AuthPrincipal actor = new AuthPrincipal(
                2L, 3L, "family", "analyst", "Analyst", true, false,
                Set.of("ANALYST"), Set.of(), 7L, "tenant-a", 8L, Set.of(11L));
        OrganizationDataScope scope = OrganizationDataScope.tenantScope(
                7L, "tenant-a", Set.of(), Set.of(), Set.of(), false);
        when(authorizationService.resolve(actor))
                .thenReturn(new OrganizationAuthorizationContext(actor, scope));
        when(service.list(actor, scope)).thenReturn(List.of(view()));
        when(service.manualRetry(eq(actor), eq(scope), eq(11L), any())).thenReturn(view());
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ReportGenerationJobController(service, authorizationService))
                .setCustomArgumentResolvers(principalResolver(actor))
                .build();
    }

    @Test
    void listsJobsAndReturnsTraceableResult() throws Exception {
        mockMvc.perform(get("/api/report-jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PERMANENT_FAILURE"))
                .andExpect(jsonPath("$.data[0].traceId").value("trace-job"));
    }

    @Test
    void manuallyRetriesTerminalJob() throws Exception {
        mockMvc.perform(post("/api/report-jobs/11/retry")
                        .header("X-Request-ID", "request-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11));
    }

    private JobView view() {
        Instant now = Instant.parse("2026-08-14T01:30:00Z");
        return new JobView(
                11L, 9L, 7L, 2L, Instant.parse("2026-08-14T01:00:00Z"),
                "9:2026-08-14T01:00:00Z", "daily", new ReportScope("ORGANIZATION", "10"),
                "en", "", ReportGenerationJobStatus.PERMANENT_FAILURE, 3, 3,
                null, null, null, "RETRY_EXHAUSTED", "trace-job", null,
                now, now, 2L);
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
