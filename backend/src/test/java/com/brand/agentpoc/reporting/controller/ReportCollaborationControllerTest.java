package com.brand.agentpoc.reporting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationAuthorizationContext;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.application.ReportCollaborationService;
import com.brand.agentpoc.reporting.application.ReportCollaborationService.AssigneeView;
import com.brand.agentpoc.reporting.application.ReportCollaborationService.CollaborationFilter;
import com.brand.agentpoc.reporting.application.ReportCollaborationService.ReportCollaborationConflictException;
import com.brand.agentpoc.reporting.application.ReportCollaborationService.ReportCollaborationDetail;
import com.brand.agentpoc.reporting.application.ReportCollaborationService.ReportCollaborationSummary;
import com.brand.agentpoc.reporting.application.ReportCollaborationService.TimelineEventView;
import com.brand.agentpoc.reporting.domain.ReportCollaborationEventType;
import com.brand.agentpoc.reporting.domain.ReportCollaborationStatus;
import com.brand.agentpoc.reporting.domain.ReportScope;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ReportCollaborationControllerTest {

    private ReportCollaborationService service;
    private AuthPrincipal actor;
    private OrganizationDataScope scope;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ReportCollaborationService.class);
        OrganizationAuthorizationService authorizationService = mock(OrganizationAuthorizationService.class);
        actor = new AuthPrincipal(
                2L, 3L, "family", "analyst", "Analyst", true, false,
                Set.of("ANALYST"), Set.of(), 7L, "tenant-a", 8L, Set.of(11L));
        scope = OrganizationDataScope.unrestrictedScope();
        when(authorizationService.resolve(actor))
                .thenReturn(new OrganizationAuthorizationContext(actor, scope));
        when(service.list(eq(actor), eq(scope), any(CollaborationFilter.class)))
                .thenReturn(List.of(summary()));
        when(service.get(actor, scope, "report-1")).thenReturn(detail());
        when(service.listAssignees(actor, scope, "report-1"))
                .thenReturn(List.of(new AssigneeView(3L, "reviewer", "Reviewer")));
        when(service.changeStatus(eq(actor), eq(scope), eq("report-1"), eq(0L),
                eq("IN_PROGRESS"), anyString())).thenReturn(detail());
        when(service.changeAssignee(eq(actor), eq(scope), eq("report-1"), eq(0L),
                eq(3L), anyString())).thenReturn(detail());
        when(service.addComment(eq(actor), eq(scope), eq("report-1"), eq(0L),
                eq("Reviewed"), anyString())).thenReturn(detail());

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ReportCollaborationController(service, authorizationService))
                .setCustomArgumentResolvers(principalResolver(actor))
                .setValidator(validator)
                .build();
    }

    @Test
    void listsFilteredReportsAndReturnsDetailAndAssigneesInsideEnvelope() throws Exception {
        mockMvc.perform(get("/api/report-collaborations")
                        .queryParam("status", "IN_PROGRESS")
                        .queryParam("assigneeUserId", "3")
                        .queryParam("organizationId", "11")
                        .queryParam("generatedFrom", "2026-08-01T00:00:00Z")
                        .queryParam("generatedTo", "2026-08-15T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reportId").value("report-1"));
        mockMvc.perform(get("/api/report-collaborations/report-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeline[0].type").value("CREATED"));
        mockMvc.perform(get("/api/report-collaborations/report-1/assignees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(3));

        verify(service).list(actor, scope, new CollaborationFilter(
                "IN_PROGRESS", 3L, 11L,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-15T23:59:59Z")));
    }

    @Test
    void acceptsVersionedStatusAssigneeAndCommentMutations() throws Exception {
        mockMvc.perform(patch("/api/report-collaborations/report-1/status")
                        .header("X-Request-ID", "trace-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.version").value(0));
        mockMvc.perform(patch("/api/report-collaborations/report-1/assignee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeUserId\":3,\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/report-collaborations/report-1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Reviewed\",\"version\":0}"))
                .andExpect(status().isOk());

        verify(service).changeStatus(actor, scope, "report-1", 0L, "IN_PROGRESS", "trace-status");
    }

    @Test
    void returnsCurrentVersionForOptimisticConcurrencyConflicts() throws Exception {
        when(service.changeStatus(eq(actor), eq(scope), eq("report-1"), eq(2L),
                eq("IN_PROGRESS"), anyString()))
                .thenThrow(new ReportCollaborationConflictException(5L));

        mockMvc.perform(patch("/api/report-collaborations/report-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"version\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.data.currentVersion").value(5));
    }

    @Test
    void rejectsInvalidMutationBodiesBeforeCallingTheService() throws Exception {
        mockMvc.perform(patch("/api/report-collaborations/report-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/report-collaborations/report-1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"\",\"version\":0}"))
                .andExpect(status().isBadRequest());
    }

    private ReportCollaborationSummary summary() {
        return new ReportCollaborationSummary(
                "report-1", "Daily report", "daily", "en",
                Instant.parse("2026-08-15T01:00:00Z"),
                new ReportScope("ORGANIZATION", "11"), ReportCollaborationStatus.OPEN,
                null, Instant.parse("2026-08-15T01:00:00Z"), 0L);
    }

    private ReportCollaborationDetail detail() {
        return new ReportCollaborationDetail(summary(), "# Daily report", List.of(
                new TimelineEventView(
                        1L, ReportCollaborationEventType.CREATED, null, "system", "System",
                        null, "OPEN", null, "report-generation",
                        Instant.parse("2026-08-15T01:00:00Z"))));
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
