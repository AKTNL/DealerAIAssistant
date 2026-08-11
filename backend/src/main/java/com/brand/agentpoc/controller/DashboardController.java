package com.brand.agentpoc.controller;

import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.dto.response.DashboardSummary;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final OrganizationAuthorizationService authorizationService;

    public DashboardController(DashboardService dashboardService) {
        this(dashboardService, null);
    }

    @Autowired
    public DashboardController(
            DashboardService dashboardService,
            OrganizationAuthorizationService authorizationService
    ) {
        this.dashboardService = dashboardService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ApiResult<DashboardSummary> getSummary() {
        if (authorizationService == null) {
            return ApiResult.success(dashboardService.getSummary());
        }
        OrganizationDataScope dataScope = authorizationService.resolveCurrent().dataScope();
        return ApiResult.success(dashboardService.getSummary(dataScope));
    }
}
