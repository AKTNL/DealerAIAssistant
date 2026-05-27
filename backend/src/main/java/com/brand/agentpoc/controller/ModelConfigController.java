package com.brand.agentpoc.controller;

import com.brand.agentpoc.dto.request.ModelConfigRequest;
import com.brand.agentpoc.dto.response.ModelConfigTestResponse;
import com.brand.agentpoc.service.ModelConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model-config")
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    public ModelConfigController(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    @PostMapping("/test")
    public ModelConfigTestResponse test(@Valid @RequestBody ModelConfigRequest request) {
        return modelConfigService.testConnection(request);
    }
}
