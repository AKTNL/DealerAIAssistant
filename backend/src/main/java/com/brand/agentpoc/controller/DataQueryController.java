package com.brand.agentpoc.controller;

import com.brand.agentpoc.dto.response.DataQueryResponse;
import com.brand.agentpoc.service.DataQueryService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data")
public class DataQueryController {

    private final DataQueryService dataQueryService;

    public DataQueryController(DataQueryService dataQueryService) {
        this.dataQueryService = dataQueryService;
    }

    @GetMapping("/dealers")
    public DataQueryResponse dealers(@RequestParam Map<String, String> filters) {
        return dataQueryService.query("dealers", filters);
    }

    @GetMapping("/opportunities")
    public DataQueryResponse opportunities(@RequestParam Map<String, String> filters) {
        return dataQueryService.query("opportunities", filters);
    }

    @GetMapping("/campaigns")
    public DataQueryResponse campaigns(@RequestParam Map<String, String> filters) {
        return dataQueryService.query("campaigns", filters);
    }

    @GetMapping("/tasks")
    public DataQueryResponse tasks(@RequestParam Map<String, String> filters) {
        return dataQueryService.query("tasks", filters);
    }

    @GetMapping("/targets")
    public DataQueryResponse targets(@RequestParam Map<String, String> filters) {
        return dataQueryService.query("targets", filters);
    }

    @GetMapping("/leads")
    public DataQueryResponse leads(@RequestParam Map<String, String> filters) {
        return dataQueryService.query("leads", filters);
    }
}

