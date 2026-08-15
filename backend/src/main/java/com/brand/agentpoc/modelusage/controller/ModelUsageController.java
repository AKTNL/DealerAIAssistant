package com.brand.agentpoc.modelusage.controller;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.modelusage.application.ModelUsageGovernanceService;
import com.brand.agentpoc.modelusage.application.ModelUsageGovernanceService.BudgetPolicyInput;
import com.brand.agentpoc.modelusage.application.ModelUsageGovernanceService.BudgetView;
import com.brand.agentpoc.modelusage.application.ModelUsageGovernanceService.EventView;
import com.brand.agentpoc.modelusage.application.ModelUsageGovernanceService.PriceVersionInput;
import com.brand.agentpoc.modelusage.application.ModelUsageGovernanceService.PriceVersionView;
import com.brand.agentpoc.modelusage.application.ModelUsageGovernanceService.UsageSummaryView;
import com.brand.agentpoc.observability.infrastructure.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/model-usage")
public class ModelUsageController {

    private final ModelUsageGovernanceService service;

    public ModelUsageController(ModelUsageGovernanceService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResult<UsageSummaryView> summary(
            @AuthenticationPrincipal AuthPrincipal actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ApiResult.success(service.summary(actor, from, to));
    }

    @GetMapping("/events")
    public ApiResult<List<EventView>> events(
            @AuthenticationPrincipal AuthPrincipal actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ApiResult.success(service.events(actor, from, to));
    }

    @GetMapping("/prices")
    public ApiResult<List<PriceVersionView>> prices(@AuthenticationPrincipal AuthPrincipal actor) {
        return ApiResult.success(service.prices(actor));
    }

    @PostMapping("/prices")
    public ResponseEntity<ApiResult<PriceVersionView>> addPrice(
            @AuthenticationPrincipal AuthPrincipal actor,
            @Valid @RequestBody PriceVersionRequest request,
            HttpServletRequest servletRequest
    ) {
        try {
            PriceVersionView saved = service.addPrice(actor, request.toInput(),
                    RequestCorrelation.traceId(servletRequest));
            return ResponseEntity.ok(ApiResult.success(saved));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        }
    }

    @GetMapping("/budget")
    public ApiResult<BudgetView> budget(@AuthenticationPrincipal AuthPrincipal actor) {
        return ApiResult.success(service.budget(actor));
    }

    @PutMapping("/budget")
    public ResponseEntity<ApiResult<BudgetView>> saveBudget(
            @AuthenticationPrincipal AuthPrincipal actor,
            @Valid @RequestBody BudgetPolicyRequest request,
            HttpServletRequest servletRequest
    ) {
        try {
            return ResponseEntity.ok(ApiResult.success(service.saveBudget(
                    actor, request.toInput(), RequestCorrelation.traceId(servletRequest))));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        } catch (OptimisticLockingFailureException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResult.error(409, "The model budget changed since it was loaded."));
        }
    }

    public record PriceVersionRequest(
            @NotBlank String provider,
            @NotBlank String model,
            String versionKey,
            @NotNull @PositiveOrZero BigDecimal inputPricePerMillion,
            @NotNull @PositiveOrZero BigDecimal outputPricePerMillion,
            @NotBlank String currency,
            @NotBlank String source,
            Instant effectiveFrom
    ) {
        private PriceVersionInput toInput() {
            return new PriceVersionInput(provider, model, versionKey, inputPricePerMillion,
                    outputPricePerMillion, currency, source, effectiveFrom);
        }
    }

    public record BudgetPolicyRequest(
            @NotNull @DecimalMin("0.00000001") BigDecimal monthlyLimit,
            @Min(1) @Max(100) int softThresholdPercent,
            boolean hardLimitEnabled,
            boolean failOpen,
            @NotNull @PositiveOrZero BigDecimal reservationAmount,
            @NotBlank String currency,
            Long version
    ) {
        private BudgetPolicyInput toInput() {
            return new BudgetPolicyInput(monthlyLimit, softThresholdPercent, hardLimitEnabled,
                    failOpen, reservationAmount, currency, version);
        }
    }
}
