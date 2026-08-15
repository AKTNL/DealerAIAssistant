package com.brand.agentpoc.reporting.controller;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.observability.infrastructure.web.RequestCorrelation;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.application.ReportSubscriptionService;
import com.brand.agentpoc.reporting.application.ReportSubscriptionService.DefinitionInput;
import com.brand.agentpoc.reporting.application.ReportSubscriptionService.RecipientView;
import com.brand.agentpoc.reporting.application.ReportSubscriptionService.ReportSubscriptionView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report-subscriptions")
public class ReportSubscriptionController {

    private final ReportSubscriptionService subscriptionService;
    private final OrganizationAuthorizationService authorizationService;

    public ReportSubscriptionController(
            ReportSubscriptionService subscriptionService,
            OrganizationAuthorizationService authorizationService
    ) {
        this.subscriptionService = subscriptionService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ApiResult<List<ReportSubscriptionView>> list(
            @AuthenticationPrincipal AuthPrincipal actor
    ) {
        return ApiResult.success(subscriptionService.list(actor, dataScope(actor)));
    }

    @GetMapping("/recipients")
    public ApiResult<List<RecipientView>> listRecipients(
            @AuthenticationPrincipal AuthPrincipal actor
    ) {
        return ApiResult.success(subscriptionService.listRecipients(actor, dataScope(actor)));
    }

    @PostMapping
    public ResponseEntity<ApiResult<ReportSubscriptionView>> create(
            @AuthenticationPrincipal AuthPrincipal actor,
            @Valid @RequestBody SubscriptionRequest request,
            HttpServletRequest servletRequest
    ) {
        return execute(() -> subscriptionService.create(
                actor,
                dataScope(actor),
                request.definition(),
                request.enabled(),
                traceId(servletRequest)
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResult<ReportSubscriptionView>> update(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionRequest request,
            HttpServletRequest servletRequest
    ) {
        return execute(() -> subscriptionService.update(
                actor,
                dataScope(actor),
                id,
                request.version(),
                request.definition(),
                traceId(servletRequest)
        ));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<ApiResult<ReportSubscriptionView>> changeEnabled(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody EnabledRequest request,
            HttpServletRequest servletRequest
    ) {
        return execute(() -> subscriptionService.changeEnabled(
                actor,
                dataScope(actor),
                id,
                request.version(),
                request.enabled(),
                traceId(servletRequest)
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResult<Void>> delete(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody VersionRequest request,
            HttpServletRequest servletRequest
    ) {
        try {
            subscriptionService.delete(
                    actor, dataScope(actor), id, request.version(), traceId(servletRequest));
            return ResponseEntity.ok(ApiResult.success(null));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        } catch (NoSuchElementException exception) {
            return ResponseEntity.status(404).body(ApiResult.error(404, exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(ApiResult.error(409, exception.getMessage()));
        } catch (OptimisticLockingFailureException exception) {
            return ResponseEntity.status(409)
                    .body(ApiResult.error(409, "The report subscription changed since it was loaded."));
        }
    }

    private OrganizationDataScope dataScope(AuthPrincipal actor) {
        return authorizationService.resolve(actor).dataScope();
    }

    private String traceId(HttpServletRequest request) {
        return RequestCorrelation.traceId(request);
    }

    private ResponseEntity<ApiResult<ReportSubscriptionView>> execute(SubscriptionOperation operation) {
        try {
            return ResponseEntity.ok(ApiResult.success(operation.run()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        } catch (NoSuchElementException exception) {
            return ResponseEntity.status(404).body(ApiResult.error(404, exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(ApiResult.error(409, exception.getMessage()));
        } catch (OptimisticLockingFailureException exception) {
            return ResponseEntity.status(409)
                    .body(ApiResult.error(409, "The report subscription changed since it was loaded."));
        }
    }

    public record SubscriptionRequest(
            @NotBlank String reportType,
            @NotBlank String language,
            String topic,
            @NotBlank String scheduleKind,
            @NotBlank String localTime,
            @NotBlank String timeZone,
            Integer dayOfWeek,
            Integer dayOfMonth,
            @NotBlank String channelKey,
            @NotEmpty Set<@Positive Long> recipientUserIds,
            boolean enabled,
            Long version
    ) {
        private DefinitionInput definition() {
            return new DefinitionInput(
                    reportType,
                    language,
                    topic,
                    scheduleKind,
                    localTime,
                    timeZone,
                    dayOfWeek,
                    dayOfMonth,
                    channelKey,
                    recipientUserIds
            );
        }
    }

    public record EnabledRequest(boolean enabled, @NotNull @PositiveOrZero Long version) {
    }

    public record VersionRequest(@NotNull @PositiveOrZero Long version) {
    }

    @FunctionalInterface
    private interface SubscriptionOperation {
        ReportSubscriptionView run();
    }
}
