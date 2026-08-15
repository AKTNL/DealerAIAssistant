package com.brand.agentpoc.reporting.controller;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.observability.infrastructure.web.RequestCorrelation;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.application.ReportCollaborationService;
import com.brand.agentpoc.reporting.application.ReportCollaborationService.AssigneeView;
import com.brand.agentpoc.reporting.application.ReportCollaborationService.CollaborationFilter;
import com.brand.agentpoc.reporting.application.ReportCollaborationService.ReportCollaborationConflictException;
import com.brand.agentpoc.reporting.application.ReportCollaborationService.ReportCollaborationDetail;
import com.brand.agentpoc.reporting.application.ReportCollaborationService.ReportCollaborationSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report-collaborations")
public class ReportCollaborationController {

    private final ReportCollaborationService collaborationService;
    private final OrganizationAuthorizationService authorizationService;

    public ReportCollaborationController(
            ReportCollaborationService collaborationService,
            OrganizationAuthorizationService authorizationService
    ) {
        this.collaborationService = collaborationService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ApiResult<List<ReportCollaborationSummary>> list(
            @AuthenticationPrincipal AuthPrincipal actor,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long assigneeUserId,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant generatedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant generatedTo
    ) {
        return ApiResult.success(collaborationService.list(
                actor, dataScope(actor),
                new CollaborationFilter(status, assigneeUserId, organizationId, generatedFrom, generatedTo)));
    }

    @GetMapping("/{reportId}")
    public ApiResult<ReportCollaborationDetail> get(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable String reportId
    ) {
        return ApiResult.success(collaborationService.get(actor, dataScope(actor), reportId));
    }

    @GetMapping("/{reportId}/assignees")
    public ApiResult<List<AssigneeView>> listAssignees(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable String reportId
    ) {
        return ApiResult.success(collaborationService.listAssignees(actor, dataScope(actor), reportId));
    }

    @PatchMapping("/{reportId}/status")
    public ResponseEntity<ApiResult<?>> changeStatus(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable String reportId,
            @Valid @RequestBody StatusRequest request,
            HttpServletRequest servletRequest
    ) {
        return execute(reportId, actor, () -> collaborationService.changeStatus(
                actor, dataScope(actor), reportId, request.version(), request.status(), traceId(servletRequest)));
    }

    @PatchMapping("/{reportId}/assignee")
    public ResponseEntity<ApiResult<?>> changeAssignee(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable String reportId,
            @Valid @RequestBody AssigneeRequest request,
            HttpServletRequest servletRequest
    ) {
        return execute(reportId, actor, () -> collaborationService.changeAssignee(
                actor, dataScope(actor), reportId, request.version(), request.assigneeUserId(),
                traceId(servletRequest)));
    }

    @PostMapping("/{reportId}/comments")
    public ResponseEntity<ApiResult<?>> addComment(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable String reportId,
            @Valid @RequestBody CommentRequest request,
            HttpServletRequest servletRequest
    ) {
        return execute(reportId, actor, () -> collaborationService.addComment(
                actor, dataScope(actor), reportId, request.version(), request.body(), traceId(servletRequest)));
    }

    private ResponseEntity<ApiResult<?>> execute(
            String reportId,
            AuthPrincipal actor,
            CollaborationOperation operation
    ) {
        try {
            return ResponseEntity.ok(ApiResult.success(operation.run()));
        } catch (ReportCollaborationConflictException exception) {
            return conflict(exception.currentVersion());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        } catch (NoSuchElementException exception) {
            return ResponseEntity.status(404).body(ApiResult.error(404, exception.getMessage()));
        } catch (OptimisticLockingFailureException exception) {
            return conflict(collaborationService.currentVersion(actor, dataScope(actor), reportId));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(ApiResult.error(409, exception.getMessage()));
        }
    }

    private ResponseEntity<ApiResult<?>> conflict(Long currentVersion) {
        ApiResult<ConflictView> body = new ApiResult<>(
                409,
                new ConflictView(currentVersion),
                "The report collaboration changed since it was loaded."
        );
        return ResponseEntity.status(409).body(body);
    }

    private OrganizationDataScope dataScope(AuthPrincipal actor) {
        return authorizationService.resolve(actor).dataScope();
    }

    private String traceId(HttpServletRequest request) {
        return RequestCorrelation.traceId(request);
    }

    public record StatusRequest(
            @NotBlank String status,
            @NotNull @PositiveOrZero Long version
    ) {
    }

    public record AssigneeRequest(
            @Positive Long assigneeUserId,
            @NotNull @PositiveOrZero Long version
    ) {
    }

    public record CommentRequest(
            @NotBlank @Size(max = 2000) String body,
            @NotNull @PositiveOrZero Long version
    ) {
    }

    public record ConflictView(Long currentVersion) {
    }

    @FunctionalInterface
    private interface CollaborationOperation {
        ReportCollaborationDetail run();
    }
}
