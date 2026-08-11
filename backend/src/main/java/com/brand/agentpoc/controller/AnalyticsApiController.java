package com.brand.agentpoc.controller;

import com.brand.agentpoc.dto.detail.*;
import com.brand.agentpoc.dto.metrics.*;
import com.brand.agentpoc.dto.response.ApiPage;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.service.AnalyticsApiService;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AnalyticsApiController {

    private final AnalyticsApiService analyticsApiService;
    private final OrganizationAuthorizationService authorizationService;

    public AnalyticsApiController(AnalyticsApiService analyticsApiService) {
        this(analyticsApiService, null);
    }

    @Autowired
    public AnalyticsApiController(
            AnalyticsApiService analyticsApiService,
            OrganizationAuthorizationService authorizationService
    ) {
        this.analyticsApiService = analyticsApiService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/targets/metrics")
    public ApiResult<TargetMetrics> getTargetMetrics(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String productModel,
            @RequestParam(required = false) String dealerCode,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String dealerName,
            @RequestParam(required = false) String dealerGroupName
    ) {
        return scoped(() -> analyticsApiService.getTargetMetrics(
                year, month, productModel, dealerCode, city, dealerName, dealerGroupName));
    }

    @GetMapping("/targets/details")
    public ApiResult<ApiPage<TargetDetail>> getTargetDetails(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String productModel,
            @RequestParam(required = false) String dealerCode,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String dealerName,
            @RequestParam(required = false) String dealerGroupName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "dealerCode") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder
    ) {
        return scoped(() -> analyticsApiService.getTargetDetails(year, month, productModel, dealerCode,
                city, dealerName, dealerGroupName,
                page, pageSize, sortBy, sortOrder));
    }

    @GetMapping("/opportunities/metrics")
    public ApiResult<OpportunityMetrics> getOpportunityMetrics(
            @RequestParam(required = false) String dealerCode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        return scoped(() -> analyticsApiService.getOpportunityMetrics(dealerCode, startDate, endDate));
    }

    @GetMapping("/opportunities/details")
    public ApiResult<ApiPage<OpportunityDetail>> getOpportunityDetails(
            @RequestParam(required = false) String dealerCode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String stageName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "createdDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        return scoped(() -> analyticsApiService.getOpportunityDetails(
                dealerCode, startDate, endDate, keyword, stageName, page, pageSize, sortBy, sortOrder));
    }

    @GetMapping("/leads/metrics")
    public ApiResult<LeadMetrics> getLeadMetrics(
            @RequestParam(required = false) String leadSource,
            @RequestParam(required = false) String dealerCode
    ) {
        return scoped(() -> analyticsApiService.getLeadMetrics(leadSource, dealerCode));
    }

    @GetMapping("/leads/details")
    public ApiResult<ApiPage<LeadDetail>> getLeadDetails(
            @RequestParam(required = false) String leadSource,
            @RequestParam(required = false) String dealerCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "createdDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        return scoped(() -> analyticsApiService.getLeadDetails(
                leadSource, dealerCode, page, pageSize, sortBy, sortOrder));
    }

    @GetMapping("/tasks/metrics")
    public ApiResult<TaskMetrics> getTaskMetrics(@RequestParam(required = false) String dealerCode) {
        return scoped(() -> analyticsApiService.getTaskMetrics(dealerCode));
    }

    @GetMapping("/tasks/details")
    public ApiResult<ApiPage<TaskDetail>> getTaskDetails(
            @RequestParam(required = false) String dealerCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "createdDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        return scoped(() -> analyticsApiService.getTaskDetails(
                dealerCode, keyword, page, pageSize, sortBy, sortOrder));
    }

    @GetMapping("/campaigns/metrics")
    public ApiResult<CampaignMetrics> getCampaignMetrics(@RequestParam(required = false) String campaignType) {
        return scoped(() -> analyticsApiService.getCampaignMetrics(campaignType));
    }

    @GetMapping("/campaigns/details")
    public ApiResult<ApiPage<CampaignDetail>> getCampaignDetails(
            @RequestParam(required = false) String campaignType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "createdDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        return scoped(() -> analyticsApiService.getCampaignDetails(
                campaignType, keyword, page, pageSize, sortBy, sortOrder));
    }

    private <T> T scoped(Supplier<T> operation) {
        if (authorizationService == null) {
            return operation.get();
        }
        return analyticsApiService.withOrganizationScope(
                authorizationService.resolveCurrent().dataScope(),
                operation
        );
    }
}
