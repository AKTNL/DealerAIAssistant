package com.brand.agentpoc.reporting.controller;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.observability.infrastructure.web.RequestCorrelation;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.application.ReportGenerationJobService;
import com.brand.agentpoc.reporting.application.ReportGenerationJobService.JobView;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report-jobs")
public class ReportGenerationJobController {

    private final ReportGenerationJobService jobService;
    private final OrganizationAuthorizationService authorizationService;

    public ReportGenerationJobController(
            ReportGenerationJobService jobService,
            OrganizationAuthorizationService authorizationService
    ) {
        this.jobService = jobService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ApiResult<List<JobView>> list(@AuthenticationPrincipal AuthPrincipal actor) {
        return ApiResult.success(jobService.list(actor, dataScope(actor)));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResult<JobView>> retry(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        try {
            return ResponseEntity.ok(ApiResult.success(jobService.manualRetry(
                    actor, dataScope(actor), id, traceId(request))));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        } catch (NoSuchElementException exception) {
            return ResponseEntity.status(404).body(ApiResult.error(404, exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(ApiResult.error(409, exception.getMessage()));
        }
    }

    private OrganizationDataScope dataScope(AuthPrincipal actor) {
        return authorizationService.resolve(actor).dataScope();
    }

    private String traceId(HttpServletRequest request) {
        return RequestCorrelation.traceId(request);
    }
}
