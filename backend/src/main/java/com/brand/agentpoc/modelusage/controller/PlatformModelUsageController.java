package com.brand.agentpoc.modelusage.controller;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.modelusage.application.ModelUsageGovernanceService;
import com.brand.agentpoc.modelusage.application.ModelUsageGovernanceService.PlatformSummaryView;
import com.brand.agentpoc.observability.infrastructure.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/model-usage")
public class PlatformModelUsageController {

    private final ModelUsageGovernanceService service;

    public PlatformModelUsageController(ModelUsageGovernanceService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResult<PlatformSummaryView> summary(
            @AuthenticationPrincipal AuthPrincipal actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            HttpServletRequest servletRequest
    ) {
        return ApiResult.success(service.platformSummary(
                actor, from, to, RequestCorrelation.traceId(servletRequest)));
    }
}
