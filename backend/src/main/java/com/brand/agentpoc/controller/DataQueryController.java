package com.brand.agentpoc.controller;

import com.brand.agentpoc.dto.response.DataQueryResponse;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.service.DataQueryService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data")
public class DataQueryController {

    private final DataQueryService dataQueryService;
    private final OrganizationAuthorizationService authorizationService;

    public DataQueryController(DataQueryService dataQueryService) {
        this(dataQueryService, null);
    }

    @Autowired
    public DataQueryController(
            DataQueryService dataQueryService,
            OrganizationAuthorizationService authorizationService
    ) {
        this.dataQueryService = dataQueryService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/dealers")
    public DataQueryResponse dealers(@RequestParam Map<String, String> filters) {
        return query("dealers", filters);
    }

    @GetMapping("/opportunities")
    public DataQueryResponse opportunities(@RequestParam Map<String, String> filters) {
        return query("opportunities", filters);
    }

    @GetMapping("/campaigns")
    public DataQueryResponse campaigns(@RequestParam Map<String, String> filters) {
        return query("campaigns", filters);
    }

    @GetMapping("/tasks")
    public DataQueryResponse tasks(@RequestParam Map<String, String> filters) {
        return query("tasks", filters);
    }

    @GetMapping("/targets")
    public DataQueryResponse targets(@RequestParam Map<String, String> filters) {
        return query("targets", filters);
    }

    @GetMapping("/leads")
    public DataQueryResponse leads(@RequestParam Map<String, String> filters) {
        return query("leads", filters);
    }

    private DataQueryResponse query(String dataset, Map<String, String> filters) {
        if (authorizationService == null) {
            return dataQueryService.query(dataset, filters);
        }
        OrganizationDataScope dataScope = authorizationService.resolveCurrent().dataScope();
        return dataQueryService.query(dataset, filters, dataScope);
    }
}
