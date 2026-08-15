package com.brand.agentpoc.modelusage.application;

import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.modelusage.domain.ModelTokenState;
import com.brand.agentpoc.modelusage.domain.ModelUsageStatus;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelBudgetPolicyEntity;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelBudgetPolicyRepository;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelPriceVersionEntity;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelPriceVersionRepository;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelUsageEventEntity;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelUsageEventRepository;
import com.brand.agentpoc.tenant.domain.TenantScoped;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelUsageGovernanceService {

    private static final Duration DEFAULT_RANGE = Duration.ofDays(30);
    private static final Duration MAX_RANGE = Duration.ofDays(366);
    private static final int EVENT_LIMIT = 500;
    private static final int RECENT_LIMIT = 100;
    private static final int ANOMALY_LIMIT = 20;

    private final ModelUsageEventRepository eventRepository;
    private final ModelPriceVersionRepository priceRepository;
    private final ModelBudgetPolicyRepository budgetRepository;
    private final AuthAuditService auditService;
    private final Clock clock;

    public ModelUsageGovernanceService(
            ModelUsageEventRepository eventRepository,
            ModelPriceVersionRepository priceRepository,
            ModelBudgetPolicyRepository budgetRepository,
            AuthAuditService auditService,
            Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.priceRepository = priceRepository;
        this.budgetRepository = budgetRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UsageSummaryView summary(AuthPrincipal actor, Instant requestedFrom, Instant requestedTo) {
        require(actor, PermissionKey.MODEL_USAGE_READ);
        TimeRange range = range(requestedFrom, requestedTo);
        List<ModelUsageEventEntity> events = tenantEvents(actor.tenantId(), range);
        return summaryView(actor.tenantId(), range, events);
    }

    @Transactional(readOnly = true)
    public List<EventView> events(AuthPrincipal actor, Instant requestedFrom, Instant requestedTo) {
        require(actor, PermissionKey.MODEL_USAGE_READ);
        TimeRange range = range(requestedFrom, requestedTo);
        return tenantEvents(actor.tenantId(), range).stream().limit(EVENT_LIMIT).map(EventView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PriceVersionView> prices(AuthPrincipal actor) {
        require(actor, PermissionKey.MODEL_USAGE_READ);
        return priceRepository.findByTenantIdOrderByEffectiveFromDescIdDesc(actor.tenantId()).stream()
                .map(PriceVersionView::from)
                .toList();
    }

    @Transactional
    public PriceVersionView addPrice(
            AuthPrincipal actor,
            PriceVersionInput input,
            String traceId
    ) {
        require(actor, PermissionKey.MODEL_USAGE_MANAGE);
        Objects.requireNonNull(input, "price input is required");
        String provider = required(input.provider(), "provider", 64).toLowerCase(Locale.ROOT);
        String model = required(input.model(), "model", 128);
        String currency = currency(input.currency());
        String source = required(input.source(), "source", 64);
        BigDecimal inputPrice = nonNegative(input.inputPricePerMillion(), "inputPricePerMillion");
        BigDecimal outputPrice = nonNegative(input.outputPricePerMillion(), "outputPricePerMillion");
        Instant now = Instant.now(clock);
        Instant effectiveFrom = input.effectiveFrom() == null ? now : input.effectiveFrom();
        if (effectiveFrom.isAfter(now.plus(Duration.ofDays(366)))) {
            throw new IllegalArgumentException("effectiveFrom is too far in the future.");
        }
        String versionKey = input.versionKey() == null || input.versionKey().isBlank()
                ? UUID.randomUUID().toString()
                : required(input.versionKey(), "versionKey", 128);
        ModelPriceVersionEntity saved = priceRepository.saveAndFlush(new ModelPriceVersionEntity(
                actor.tenantId(), provider, model, versionKey, inputPrice, outputPrice,
                currency, source, effectiveFrom, actor.userId(), now));
        auditService.record(actor.tenantId(), actor.userId(), "MODEL_PRICE_VERSION_CREATE", "MODEL_PRICE",
                String.valueOf(saved.getId()), "SUCCESS", traceId, "price_version_appended");
        return PriceVersionView.from(saved);
    }

    @Transactional(readOnly = true)
    public BudgetView budget(AuthPrincipal actor) {
        require(actor, PermissionKey.MODEL_USAGE_READ);
        return budgetView(actor.tenantId());
    }

    @Transactional
    public BudgetView saveBudget(AuthPrincipal actor, BudgetPolicyInput input, String traceId) {
        require(actor, PermissionKey.MODEL_USAGE_MANAGE);
        Objects.requireNonNull(input, "budget input is required");
        BigDecimal monthlyLimit = positive(input.monthlyLimit(), "monthlyLimit");
        int threshold = input.softThresholdPercent();
        if (threshold < 1 || threshold > 100) {
            throw new IllegalArgumentException("softThresholdPercent must be between 1 and 100.");
        }
        BigDecimal reservation = nonNegative(input.reservationAmount(), "reservationAmount");
        if (input.hardLimitEnabled() && reservation.signum() <= 0) {
            throw new IllegalArgumentException("Hard limits require a positive reservationAmount.");
        }
        String normalizedCurrency = currency(input.currency());
        List<ModelBudgetPolicyEntity> matches = budgetRepository.findByTenantIdOrderByIdAsc(actor.tenantId());
        if (matches.size() > 1) {
            throw new IllegalStateException("Tenant model budget policy is not unique.");
        }
        Instant now = Instant.now(clock);
        ModelBudgetPolicyEntity policy;
        if (matches.isEmpty()) {
            if (input.version() != null) {
                throw new OptimisticLockingFailureException("The model budget changed since it was loaded.");
            }
            policy = new ModelBudgetPolicyEntity(actor.tenantId(), monthlyLimit, threshold,
                    input.hardLimitEnabled(), input.failOpen(), reservation, normalizedCurrency, now);
        } else {
            policy = matches.getFirst();
            if (input.version() != null && !input.version().equals(policy.getVersion())) {
                throw new OptimisticLockingFailureException("The model budget changed since it was loaded.");
            }
            policy.update(monthlyLimit, threshold, input.hardLimitEnabled(), input.failOpen(), reservation,
                    normalizedCurrency, now);
        }
        ModelBudgetPolicyEntity saved = budgetRepository.saveAndFlush(policy);
        auditService.record(actor.tenantId(), actor.userId(), "MODEL_BUDGET_POLICY_UPDATE", "MODEL_BUDGET",
                String.valueOf(saved.getId()), "SUCCESS", traceId,
                input.hardLimitEnabled() ? "hard_limit_enabled" : "soft_budget_only");
        return budgetView(actor.tenantId());
    }

    @Transactional
    public PlatformSummaryView platformSummary(
            AuthPrincipal actor,
            Instant requestedFrom,
            Instant requestedTo,
            String traceId
    ) {
        require(actor, PermissionKey.MODEL_USAGE_PLATFORM_READ);
        if (!Long.valueOf(TenantScoped.DEFAULT_TENANT_ID).equals(actor.tenantId())) {
            throw new AccessDeniedException("Platform model usage access denied.");
        }
        TimeRange range = range(requestedFrom, requestedTo);
        List<ModelUsageEventEntity> events = eventRepository
                .findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(
                        range.from(), range.to());
        Map<Long, List<ModelUsageEventEntity>> byTenant = new LinkedHashMap<>();
        for (ModelUsageEventEntity event : events) {
            byTenant.computeIfAbsent(event.getTenantId(), ignored -> new ArrayList<>()).add(event);
        }
        List<AggregateView> tenants = byTenant.entrySet().stream()
                .map(entry -> aggregate(String.valueOf(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparing(AggregateView::calls).reversed())
                .toList();
        auditService.record(actor.tenantId(), actor.userId(), "PLATFORM_MODEL_USAGE_READ", "MODEL_USAGE",
                "all-tenants", "SUCCESS", traceId, "platform_summary_read");
        return new PlatformSummaryView(range.from(), range.to(), aggregate("all-tenants", events), tenants);
    }

    private UsageSummaryView summaryView(Long tenantId, TimeRange range, List<ModelUsageEventEntity> events) {
        return new UsageSummaryView(
                range.from(),
                range.to(),
                aggregate("total", events),
                grouped(events, event -> event.getScenario().name()),
                grouped(events, event -> event.getProviderKey() + "/" + event.getModelName()),
                grouped(events, event -> event.getUserId() == null ? "unknown" : String.valueOf(event.getUserId())),
                grouped(events, event -> event.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate().toString()),
                anomalies(events),
                events.stream().limit(RECENT_LIMIT).map(EventView::from).toList(),
                budgetView(tenantId)
        );
    }

    private List<ModelUsageEventEntity> tenantEvents(Long tenantId, TimeRange range) {
        return eventRepository
                .findByTenantIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(
                        tenantId, range.from(), range.to());
    }

    private List<AggregateView> grouped(
            List<ModelUsageEventEntity> events,
            Function<ModelUsageEventEntity, String> classifier
    ) {
        Map<String, List<ModelUsageEventEntity>> groups = new LinkedHashMap<>();
        for (ModelUsageEventEntity event : events) {
            groups.computeIfAbsent(classifier.apply(event), ignored -> new ArrayList<>()).add(event);
        }
        return groups.entrySet().stream()
                .map(entry -> aggregate(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(AggregateView::calls).reversed().thenComparing(AggregateView::key))
                .toList();
    }

    private AggregateView aggregate(String key, List<ModelUsageEventEntity> events) {
        long inputTokens = events.stream().map(ModelUsageEventEntity::getInputTokens)
                .filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        long outputTokens = events.stream().map(ModelUsageEventEntity::getOutputTokens)
                .filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        long unknownTokenCalls = events.stream()
                .filter(event -> event.getTokenState() != ModelTokenState.KNOWN).count();
        long errorCalls = events.stream().filter(event -> event.getStatus() == ModelUsageStatus.ERROR).count();
        long rejectedCalls = events.stream().filter(event -> event.getStatus() == ModelUsageStatus.REJECTED).count();
        long durationMs = events.stream().mapToLong(ModelUsageEventEntity::getDurationMs).sum();
        Map<String, BigDecimal> costs = new LinkedHashMap<>();
        for (ModelUsageEventEntity event : events) {
            if (event.getEstimatedCost() != null && event.getCurrency() != null) {
                costs.merge(event.getCurrency(), event.getEstimatedCost(), BigDecimal::add);
            }
        }
        List<CurrencyCostView> costViews = costs.entrySet().stream()
                .map(entry -> new CurrencyCostView(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CurrencyCostView::currency))
                .toList();
        return new AggregateView(key, events.size(), errorCalls, rejectedCalls, unknownTokenCalls,
                inputTokens, outputTokens, durationMs, costViews);
    }

    private List<AnomalyView> anomalies(List<ModelUsageEventEntity> events) {
        Map<String, AnomalyView> anomalies = new LinkedHashMap<>();
        for (ModelUsageEventEntity event : events) {
            String reason = anomalyReason(event);
            if (reason != null) {
                anomalies.put(event.getCallKey(), new AnomalyView(reason, EventView.from(event)));
            }
        }
        events.stream()
                .filter(event -> event.getEstimatedCost() != null)
                .sorted(Comparator.comparing(ModelUsageEventEntity::getEstimatedCost).reversed())
                .limit(5)
                .forEach(event -> anomalies.putIfAbsent(
                        event.getCallKey(), new AnomalyView("HIGH_COST", EventView.from(event))));
        return anomalies.values().stream().limit(ANOMALY_LIMIT).toList();
    }

    private String anomalyReason(ModelUsageEventEntity event) {
        if (event.getStatus() == ModelUsageStatus.REJECTED) {
            return "BUDGET_REJECTED";
        }
        if (event.getStatus() == ModelUsageStatus.ERROR || event.getStatus() == ModelUsageStatus.CANCELLED) {
            return "CALL_FAILED";
        }
        if (event.getTokenState() != ModelTokenState.KNOWN) {
            return "TOKEN_UNKNOWN";
        }
        return null;
    }

    private BudgetView budgetView(Long tenantId) {
        List<ModelBudgetPolicyEntity> matches = budgetRepository.findByTenantId(tenantId);
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Tenant model budget policy is not unique.");
        }
        ModelBudgetPolicyEntity policy = matches.getFirst();
        Instant now = Instant.now(clock);
        Instant monthStart = now.atZone(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        List<ModelUsageEventEntity> monthEvents = eventRepository
                .findByTenantIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(
                        tenantId, monthStart, now.plusMillis(1));
        BigDecimal spent = monthEvents.stream()
                .filter(event -> policy.getCurrency().equalsIgnoreCase(event.getCurrency()))
                .map(ModelUsageEventEntity::getEstimatedCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long unknownCostCalls = monthEvents.stream()
                .filter(event -> event.getStatus() == ModelUsageStatus.SUCCESS && event.getEstimatedCost() == null)
                .count();
        BigDecimal usagePercent = spent.multiply(BigDecimal.valueOf(100L))
                .divide(policy.getMonthlyLimit(), 2, RoundingMode.HALF_UP);
        String state;
        if (spent.compareTo(policy.getMonthlyLimit()) >= 0) {
            state = "LIMIT_REACHED";
        } else if (usagePercent.compareTo(BigDecimal.valueOf(policy.getSoftThresholdPercent())) >= 0) {
            state = "SOFT_THRESHOLD";
        } else if (unknownCostCalls > 0) {
            state = "COST_INCOMPLETE";
        } else {
            state = "OK";
        }
        return new BudgetView(policy.getMonthlyLimit(), policy.getSoftThresholdPercent(),
                policy.getHardLimitEnabled(), policy.getFailOpen(), policy.getReservationAmount(),
                policy.getCurrency(), spent, usagePercent, unknownCostCalls, state, policy.getVersion(),
                policy.getUpdatedAt());
    }

    private TimeRange range(Instant requestedFrom, Instant requestedTo) {
        Instant now = Instant.now(clock);
        Instant to = requestedTo == null ? now.plusMillis(1) : requestedTo;
        Instant from = requestedFrom == null ? to.minus(DEFAULT_RANGE) : requestedFrom;
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to.");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new IllegalArgumentException("The maximum model usage range is 366 days.");
        }
        if (to.isAfter(now.plus(Duration.ofMinutes(5)))) {
            throw new IllegalArgumentException("to cannot be in the future.");
        }
        return new TimeRange(from, to);
    }

    private void require(AuthPrincipal actor, PermissionKey permission) {
        if (actor == null || !actor.enabled() || !actor.hasTenantContext() || !actor.hasPermission(permission)) {
            throw new AccessDeniedException("Model usage access denied.");
        }
    }

    private String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long.");
        }
        return normalized;
    }

    private String currency(String value) {
        String normalized = required(value, "currency", 3).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an ISO 4217 code.");
        }
        return normalized;
    }

    private BigDecimal positive(BigDecimal value, String field) {
        BigDecimal normalized = nonNegative(value, field);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
        return normalized;
    }

    private BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.scale() > 8 || value.precision() > 20) {
            throw new IllegalArgumentException(field + " must be a non-negative decimal with at most 8 decimals.");
        }
        return value;
    }

    private record TimeRange(Instant from, Instant to) {
    }

    public record PriceVersionInput(
            String provider,
            String model,
            String versionKey,
            BigDecimal inputPricePerMillion,
            BigDecimal outputPricePerMillion,
            String currency,
            String source,
            Instant effectiveFrom
    ) {
    }

    public record BudgetPolicyInput(
            BigDecimal monthlyLimit,
            int softThresholdPercent,
            boolean hardLimitEnabled,
            boolean failOpen,
            BigDecimal reservationAmount,
            String currency,
            Long version
    ) {
    }

    public record CurrencyCostView(String currency, BigDecimal amount) {
    }

    public record AggregateView(
            String key,
            long calls,
            long errorCalls,
            long rejectedCalls,
            long unknownTokenCalls,
            long inputTokens,
            long outputTokens,
            long durationMs,
            List<CurrencyCostView> costs
    ) {
    }

    public record EventView(
            Long id,
            String callKey,
            Long tenantId,
            Long userId,
            String provider,
            String model,
            String scenario,
            String status,
            String tokenState,
            Long inputTokens,
            Long outputTokens,
            Long totalTokens,
            Long durationMs,
            String traceId,
            boolean cacheHit,
            String priceVersionKey,
            BigDecimal estimatedCost,
            String currency,
            String costSource,
            Instant occurredAt
    ) {
        private static EventView from(ModelUsageEventEntity event) {
            return new EventView(event.getId(), event.getCallKey(), event.getTenantId(), event.getUserId(),
                    event.getProviderKey(), event.getModelName(), event.getScenario().name(),
                    event.getStatus().name(), event.getTokenState().name(), event.getInputTokens(),
                    event.getOutputTokens(), event.getTotalTokens(), event.getDurationMs(), event.getTraceId(),
                    Boolean.TRUE.equals(event.getCacheHit()), event.getPriceVersionKey(), event.getEstimatedCost(),
                    event.getCurrency(), event.getCostSource().name(), event.getOccurredAt());
        }
    }

    public record AnomalyView(String reason, EventView event) {
    }

    public record BudgetView(
            BigDecimal monthlyLimit,
            int softThresholdPercent,
            boolean hardLimitEnabled,
            boolean failOpen,
            BigDecimal reservationAmount,
            String currency,
            BigDecimal monthToDateCost,
            BigDecimal usagePercent,
            long unknownCostCalls,
            String state,
            Long version,
            Instant updatedAt
    ) {
    }

    public record PriceVersionView(
            Long id,
            String provider,
            String model,
            String versionKey,
            BigDecimal inputPricePerMillion,
            BigDecimal outputPricePerMillion,
            String currency,
            String source,
            Instant effectiveFrom,
            Long createdBy,
            Instant createdAt
    ) {
        private static PriceVersionView from(ModelPriceVersionEntity price) {
            return new PriceVersionView(price.getId(), price.getProviderKey(), price.getModelName(),
                    price.getVersionKey(), price.getInputPricePerMillion(), price.getOutputPricePerMillion(),
                    price.getCurrency(), price.getSource(), price.getEffectiveFrom(), price.getCreatedBy(),
                    price.getCreatedAt());
        }
    }

    public record UsageSummaryView(
            Instant from,
            Instant to,
            AggregateView total,
            List<AggregateView> byScenario,
            List<AggregateView> byModel,
            List<AggregateView> byUser,
            List<AggregateView> daily,
            List<AnomalyView> anomalies,
            List<EventView> recentEvents,
            BudgetView budget
    ) {
    }

    public record PlatformSummaryView(
            Instant from,
            Instant to,
            AggregateView total,
            List<AggregateView> tenants
    ) {
    }
}
