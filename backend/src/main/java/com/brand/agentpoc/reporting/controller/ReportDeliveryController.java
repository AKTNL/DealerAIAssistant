package com.brand.agentpoc.reporting.controller;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.observability.infrastructure.web.RequestCorrelation;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.application.ReportDeliveryService;
import com.brand.agentpoc.reporting.application.ReportDeliveryService.DeliveryView;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report-deliveries")
public class ReportDeliveryController {

    private final ReportDeliveryService deliveryService;
    private final OrganizationAuthorizationService authorizationService;

    public ReportDeliveryController(
            ReportDeliveryService deliveryService,
            OrganizationAuthorizationService authorizationService
    ) {
        this.deliveryService = deliveryService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ApiResult<List<DeliveryView>> list(@AuthenticationPrincipal AuthPrincipal actor) {
        return ApiResult.success(deliveryService.list(actor, dataScope(actor)));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResult<DeliveryView>> retry(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        return execute(() -> deliveryService.manualRetry(actor, dataScope(actor), id, traceId(request)));
    }

    @PostMapping("/{id}/force-replay")
    public ResponseEntity<ApiResult<DeliveryView>> forceReplay(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable Long id,
            @RequestBody ForceReplayRequest replayRequest,
            HttpServletRequest request
    ) {
        return execute(() -> deliveryService.forceReplay(
                actor, dataScope(actor), id, replayRequest.acknowledgeDuplicateRisk(), traceId(request)));
    }

    private ResponseEntity<ApiResult<DeliveryView>> execute(DeliveryOperation operation) {
        try {
            return ResponseEntity.ok(ApiResult.success(operation.run()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        } catch (NoSuchElementException exception) {
            return ResponseEntity.status(404).body(ApiResult.error(404, exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(ApiResult.error(409, exception.getMessage()));
        }
    }

    private OrganizationDataScope dataScope(AuthPrincipal actor) {
        return authorizationService.resolve(actor).dataScope();
    }

    private String traceId(HttpServletRequest request) {
        return RequestCorrelation.traceId(request);
    }

    public record ForceReplayRequest(boolean acknowledgeDuplicateRisk) {
    }

    @FunctionalInterface
    private interface DeliveryOperation {
        DeliveryView run();
    }
}
