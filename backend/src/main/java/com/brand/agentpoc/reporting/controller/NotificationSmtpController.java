package com.brand.agentpoc.reporting.controller;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.observability.infrastructure.web.RequestCorrelation;
import com.brand.agentpoc.reporting.application.TenantSmtpConfigRegistry;
import com.brand.agentpoc.reporting.application.SmtpConfigurationTestService;
import com.brand.agentpoc.reporting.application.SmtpConfigurationTestService.SmtpTestView;
import com.brand.agentpoc.reporting.application.TenantSmtpConfigRegistry.SmtpConfigInput;
import com.brand.agentpoc.reporting.application.TenantSmtpConfigRegistry.SmtpConfigView;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification/smtp")
public class NotificationSmtpController {

    private final TenantSmtpConfigRegistry registry;
    private final SmtpConfigurationTestService testService;

    public NotificationSmtpController(
            TenantSmtpConfigRegistry registry,
            SmtpConfigurationTestService testService
    ) {
        this.registry = registry;
        this.testService = testService;
    }

    @GetMapping
    public ApiResult<Optional<SmtpConfigView>> get(@AuthenticationPrincipal AuthPrincipal actor) {
        return ApiResult.success(registry.view(requireTenant(actor)));
    }

    @PutMapping
    public ResponseEntity<ApiResult<SmtpConfigView>> save(
            @AuthenticationPrincipal AuthPrincipal actor,
            @Valid @RequestBody SaveSmtpRequest request,
            HttpServletRequest servletRequest
    ) {
        try {
            SmtpConfigView view = registry.save(requireTenant(actor), actor.userId(), new SmtpConfigInput(
                    request.host(), request.port(), request.securityMode(), request.username(), request.password(),
                    request.fromAddress(), request.fromDisplayName(), request.enabled(), request.version()),
                    traceId(servletRequest));
            return ResponseEntity.ok(ApiResult.success(view));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(ApiResult.error(409, exception.getMessage()));
        } catch (OptimisticLockingFailureException exception) {
            return ResponseEntity.status(409).body(ApiResult.error(409, "The resource changed since it was loaded."));
        }
    }

    @DeleteMapping
    public ResponseEntity<ApiResult<Void>> delete(
            @AuthenticationPrincipal AuthPrincipal actor,
            @RequestBody DeleteSmtpRequest request,
            HttpServletRequest servletRequest
    ) {
        try {
            registry.delete(requireTenant(actor), actor.userId(), request.version(), traceId(servletRequest));
            return ResponseEntity.ok(ApiResult.success(null));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        } catch (NoSuchElementException exception) {
            return ResponseEntity.status(404).body(ApiResult.error(404, exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(ApiResult.error(409, exception.getMessage()));
        } catch (OptimisticLockingFailureException exception) {
            return ResponseEntity.status(409).body(ApiResult.error(409, "The resource changed since it was loaded."));
        }
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResult<SmtpTestView>> test(
            @AuthenticationPrincipal AuthPrincipal actor,
            HttpServletRequest servletRequest
    ) {
        try {
            return ResponseEntity.ok(ApiResult.success(testService.send(actor, traceId(servletRequest))));
        } catch (IllegalStateException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        }
    }

    private Long requireTenant(AuthPrincipal actor) {
        if (actor == null || !actor.hasTenantContext()) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant context is required.");
        }
        return actor.tenantId();
    }

    private String traceId(HttpServletRequest request) {
        return RequestCorrelation.traceId(request);
    }

    public record SaveSmtpRequest(
            String host,
            Integer port,
            String securityMode,
            String username,
            String password,
            String fromAddress,
            String fromDisplayName,
            boolean enabled,
            Long version
    ) {
    }

    public record DeleteSmtpRequest(Long version) {
    }
}
