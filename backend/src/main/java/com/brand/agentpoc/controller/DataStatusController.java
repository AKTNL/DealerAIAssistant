package com.brand.agentpoc.controller;

import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.dto.response.ImportDataStatus;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.service.ImportQualityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data-status")
public class DataStatusController {

    private final ImportQualityService importQualityService;
    private final OrganizationAuthorizationService authorizationService;

    public DataStatusController(ImportQualityService importQualityService) {
        this(importQualityService, null);
    }

    @Autowired
    public DataStatusController(
            ImportQualityService importQualityService,
            OrganizationAuthorizationService authorizationService
    ) {
        this.importQualityService = importQualityService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ApiResult<ImportDataStatus> getStatus() {
        if (authorizationService != null) {
            authorizationService.resolveCurrent().dataScope().requireRootCoverage();
        }
        return ApiResult.success(importQualityService.getLatest());
    }
}
