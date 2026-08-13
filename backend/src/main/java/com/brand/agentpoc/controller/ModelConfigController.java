package com.brand.agentpoc.controller;

import com.brand.agentpoc.dto.request.ModelConfigRequest;
import com.brand.agentpoc.dto.response.ModelConfigTestResponse;
import com.brand.agentpoc.service.ModelConfigService;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/model-config")
public class ModelConfigController {

    private final ModelConfigService modelConfigService;
    private final OrganizationAuthorizationService authorizationService;

    @Autowired
    public ModelConfigController(
            ModelConfigService modelConfigService,
            OrganizationAuthorizationService authorizationService
    ) {
        this.modelConfigService = modelConfigService;
        this.authorizationService = authorizationService;
    }

    public ModelConfigController(ModelConfigService modelConfigService) {
        this(modelConfigService, null);
    }

    @PostMapping("/test")
    public ModelConfigTestResponse test(@Valid @RequestBody TestModelConfigRequest request) {
        ModelConfigRequest settings = new ModelConfigRequest(request.baseUrl(), request.apiKey(), request.model());
        return authorizationService == null
                ? modelConfigService.testConnection(settings)
                : modelConfigService.testConnection(
                        settings,
                        authorizationService.resolveCurrent().tenantId(),
                        request.allowedHosts()
                );
    }

    @GetMapping
    public Optional<com.brand.agentpoc.modelconfig.application.TenantModelConfigRegistry.ModelConfigView> get() {
        return modelConfigService.tenantConfigView(authorizationService.resolveCurrent().tenantId());
    }

    @PutMapping
    public com.brand.agentpoc.modelconfig.application.TenantModelConfigRegistry.ModelConfigView save(
            @Valid @RequestBody SaveModelConfigRequest request
    ) {
        return modelConfigService.saveTenantConfig(
                authorizationService.resolveCurrent().tenantId(),
                new ModelConfigRequest(request.baseUrl(), request.apiKey(), request.model()),
                request.allowedHosts()
        );
    }

    @DeleteMapping
    public void delete() {
        modelConfigService.deleteTenantConfig(authorizationService.resolveCurrent().tenantId());
    }

    public record SaveModelConfigRequest(
            @jakarta.validation.constraints.NotBlank String baseUrl,
            String apiKey,
            @jakarta.validation.constraints.NotBlank String model,
            @jakarta.validation.constraints.NotEmpty List<@jakarta.validation.constraints.NotBlank String> allowedHosts
    ) {
    }

    public record TestModelConfigRequest(
            @jakarta.validation.constraints.NotBlank String baseUrl,
            String apiKey,
            @jakarta.validation.constraints.NotBlank String model,
            List<@jakarta.validation.constraints.NotBlank String> allowedHosts
    ) {
    }
}
