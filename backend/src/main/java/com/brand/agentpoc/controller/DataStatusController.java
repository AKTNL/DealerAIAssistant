package com.brand.agentpoc.controller;

import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.dto.response.ImportDataStatus;
import com.brand.agentpoc.service.ImportQualityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data-status")
public class DataStatusController {

    private final ImportQualityService importQualityService;

    public DataStatusController(ImportQualityService importQualityService) {
        this.importQualityService = importQualityService;
    }

    @GetMapping
    public ApiResult<ImportDataStatus> getStatus() {
        return ApiResult.success(importQualityService.getLatest());
    }
}
