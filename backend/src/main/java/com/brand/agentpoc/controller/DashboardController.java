package com.brand.agentpoc.controller;

import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.dto.response.DashboardSummary;
import com.brand.agentpoc.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ApiResult<DashboardSummary> getSummary() {
        return ApiResult.success(dashboardService.getSummary());
    }
}
