package com.brand.agentpoc.reporting.application;

import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.domain.ReportScope;
import com.brand.agentpoc.reporting.domain.ReportSubscriptionSchedule;
import com.brand.agentpoc.reporting.domain.ReportType;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportSubscriptionEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportSubscriptionRepository;
import com.brand.agentpoc.tenant.application.TenantMemberDirectory;
import com.brand.agentpoc.tenant.application.TenantMemberDirectory.TenantRecipient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReportSubscriptionService {

    public static final String MISFIRE_POLICY = "SKIP";
    public static final int MISFIRE_GRACE_MINUTES = 60;
    private static final int MAX_TOPIC_LENGTH = 500;
    private static final String CHANNEL_KEY_PATTERN = "[a-z][a-z0-9_-]{0,31}";

    private final ReportSubscriptionRepository repository;
    private final TenantMemberDirectory memberDirectory;
    private final OrganizationAuthorizationService organizationAuthorizationService;
    private final AuthAuditService auditService;
    private final Clock clock;

    public ReportSubscriptionService(
            ReportSubscriptionRepository repository,
            TenantMemberDirectory memberDirectory,
            OrganizationAuthorizationService organizationAuthorizationService,
            AuthAuditService auditService,
            Clock clock
    ) {
        this.repository = repository;
        this.memberDirectory = memberDirectory;
        this.organizationAuthorizationService = organizationAuthorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ReportSubscriptionView> list(AuthPrincipal actor, OrganizationDataScope dataScope) {
        requireActor(actor, dataScope, PermissionKey.REPORT_READ);
        return repository.findByTenantIdAndCreatorUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        actor.tenantId(), actor.userId()).stream()
                .map(entity -> toView(entity, evaluate(entity)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecipientView> listRecipients(AuthPrincipal actor, OrganizationDataScope dataScope) {
        requireActor(actor, dataScope, PermissionKey.REPORT_READ);
        return memberDirectory.listReportRecipients(actor.tenantId()).stream()
                .map(recipient -> new RecipientView(
                        recipient.userId(), recipient.username(), recipient.displayName(), recipient.emailConfigured()))
                .toList();
    }

    public ReportSubscriptionView create(
        AuthPrincipal actor,
        OrganizationDataScope dataScope,
            DefinitionInput input,
            boolean enabled,
            String traceId
    ) {
        requireActor(actor, dataScope, PermissionKey.REPORT_GENERATE);
        requireDataAccess(dataScope);
        ValidatedDefinition definition = validateDefinition(actor, dataScope, input, enabled);
        assertUnique(actor.tenantId(), actor.userId(), definition.configurationKey(), null);
        Instant now = clock.instant();
        ReportSubscriptionEntity entity = new ReportSubscriptionEntity(
                actor.tenantId(),
                actor.userId(),
                definition.reportType(),
                definition.scope(),
                definition.language(),
                definition.topic(),
                definition.schedule(),
                definition.channelKey(),
                definition.recipientUserIds(),
                enabled,
                enabled ? definition.schedule().nextAfter(now) : null,
                MISFIRE_POLICY,
                MISFIRE_GRACE_MINUTES,
                definition.configurationKey(),
                now
        );
        ReportSubscriptionEntity saved = save(entity);
        audit(actor, saved, "REPORT_SUBSCRIPTION_CREATE", "definition_created", traceId);
        return toView(saved, evaluate(saved));
    }

    public ReportSubscriptionView update(
            AuthPrincipal actor,
            OrganizationDataScope dataScope,
            Long subscriptionId,
            Long version,
            DefinitionInput input,
            String traceId
    ) {
        requireActor(actor, dataScope, PermissionKey.REPORT_GENERATE);
        requireDataAccess(dataScope);
        ReportSubscriptionEntity entity = requireOwned(actor, subscriptionId);
        requireVersion(entity, version);
        ValidatedDefinition definition = validateDefinition(
                actor, dataScope, input, Boolean.TRUE.equals(entity.getEnabled()));
        assertUnique(actor.tenantId(), actor.userId(), definition.configurationKey(), entity.getId());
        Instant now = clock.instant();
        entity.updateDefinition(
                definition.reportType(),
                definition.scope(),
                definition.language(),
                definition.topic(),
                definition.schedule(),
                definition.channelKey(),
                definition.recipientUserIds(),
                definition.configurationKey(),
                Boolean.TRUE.equals(entity.getEnabled()) ? definition.schedule().nextAfter(now) : null,
                now
        );
        ReportSubscriptionEntity saved = save(entity);
        audit(actor, saved, "REPORT_SUBSCRIPTION_UPDATE", "definition_updated", traceId);
        return toView(saved, evaluate(saved));
    }

    public ReportSubscriptionView changeEnabled(
            AuthPrincipal actor,
            OrganizationDataScope dataScope,
            Long subscriptionId,
            Long version,
            boolean enabled,
            String traceId
    ) {
        requireActor(actor, dataScope, PermissionKey.REPORT_GENERATE);
        ReportSubscriptionEntity entity = requireOwned(actor, subscriptionId);
        requireVersion(entity, version);
        if (enabled) {
            requireExecutableRecipients(entity.getTenantId(), entity.getChannelKey(), entity.getRecipientUserIds());
        }
        Instant now = clock.instant();
        entity.changeEnabled(enabled, enabled ? entity.schedule().nextAfter(now) : null, now);
        ReportSubscriptionEntity saved = repository.saveAndFlush(entity);
        audit(actor, saved,
                enabled ? "REPORT_SUBSCRIPTION_ENABLE" : "REPORT_SUBSCRIPTION_DISABLE",
                enabled ? "subscription_enabled" : "subscription_disabled",
                traceId);
        return toView(saved, evaluate(saved));
    }

    public void delete(
            AuthPrincipal actor,
            OrganizationDataScope dataScope,
            Long subscriptionId,
            Long version,
            String traceId
    ) {
        requireActor(actor, dataScope, PermissionKey.REPORT_GENERATE);
        ReportSubscriptionEntity entity = requireOwned(actor, subscriptionId);
        requireVersion(entity, version);
        entity.softDelete(clock.instant());
        ReportSubscriptionEntity saved = repository.saveAndFlush(entity);
        audit(actor, saved, "REPORT_SUBSCRIPTION_DELETE", "subscription_soft_deleted", traceId);
    }

    @Transactional(readOnly = true)
    public ExecutionEligibility evaluateExecutionEligibility(Long subscriptionId) {
        if (subscriptionId == null) {
            throw new IllegalArgumentException("subscriptionId is required.");
        }
        ReportSubscriptionEntity entity = repository.findById(subscriptionId)
                .orElseThrow(() -> new NoSuchElementException("Report subscription was not found."));
        return evaluate(entity);
    }

    private ExecutionEligibility evaluate(ReportSubscriptionEntity entity) {
        if (entity.getDeletedAt() != null) {
            return ExecutionEligibility.denied("subscription_deleted");
        }
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            return ExecutionEligibility.denied("subscription_disabled");
        }
        if (!"email".equals(entity.getChannelKey())) {
            return ExecutionEligibility.denied("unsupported_channel");
        }
        AuthPrincipal creator;
        try {
            creator = memberDirectory.requireActivePrincipal(entity.getTenantId(), entity.getCreatorUserId());
        } catch (AccessDeniedException | IllegalArgumentException exception) {
            return ExecutionEligibility.denied("membership_or_recipient_revoked");
        }
        if (!creator.hasPermission(PermissionKey.REPORT_GENERATE)) {
            return ExecutionEligibility.denied("report_permission_revoked");
        }
        OrganizationDataScope currentScope;
        try {
            currentScope = organizationAuthorizationService.resolve(creator).dataScope();
        } catch (AccessDeniedException | IllegalArgumentException exception) {
            return ExecutionEligibility.denied("organization_scope_revoked");
        }
        if (!entity.getTenantId().equals(currentScope.tenantId())
                || !currentScope.hasDataAccess()
                || !scopeCovered(entity.scope(), currentScope)) {
            return ExecutionEligibility.denied("organization_scope_revoked");
        }
        try {
            List<TenantRecipient> recipients = memberDirectory.requireReportRecipients(
                    entity.getTenantId(), entity.getRecipientUserIds());
            if (recipients.stream().anyMatch(recipient -> !recipient.emailConfigured())) {
                return ExecutionEligibility.denied("recipient_email_missing");
            }
            return ExecutionEligibility.allowed();
        } catch (AccessDeniedException | IllegalArgumentException exception) {
            return ExecutionEligibility.denied("membership_or_recipient_revoked");
        }
    }

    private ValidatedDefinition validateDefinition(
            AuthPrincipal actor,
            OrganizationDataScope dataScope,
            DefinitionInput input,
            boolean requireEmailReady
    ) {
        if (input == null) {
            throw new IllegalArgumentException("Subscription definition is required.");
        }
        ReportType reportType = ReportType.parse(input.reportType());
        String language = normalizeLanguage(input.language());
        String topic = normalizeTopic(input.topic(), reportType);
        ReportSubscriptionSchedule schedule = ReportSubscriptionSchedule.parse(
                input.scheduleKind(), input.localTime(), input.timeZone(),
                input.dayOfWeek(), input.dayOfMonth());
        String channelKey = normalizeChannelKey(input.channelKey());
        List<TenantRecipient> eligibleRecipients = memberDirectory.requireReportRecipients(
                actor.tenantId(), input.recipientUserIds());
        if (requireEmailReady
                && eligibleRecipients.stream().anyMatch(recipient -> !recipient.emailConfigured())) {
            throw new IllegalArgumentException("Every enabled email recipient must have an email address.");
        }
        Set<Long> recipients = eligibleRecipients.stream()
                .map(TenantRecipient::userId)
                .collect(Collectors.toUnmodifiableSet());
        ReportScope scope = resolvedScope(dataScope);
        String configurationKey = configurationKey(
                reportType, scope, language, topic, schedule, channelKey, recipients);
        return new ValidatedDefinition(
                reportType, scope, language, topic, schedule, channelKey, recipients, configurationKey);
    }

    private ReportScope resolvedScope(OrganizationDataScope dataScope) {
        if (dataScope.unrestricted()) {
            return ReportScope.global();
        }
        return ReportScope.organization(dataScope.grantNodeIds());
    }

    private void requireActor(AuthPrincipal actor, OrganizationDataScope dataScope, PermissionKey permission) {
        if (actor == null || !actor.enabled() || !actor.hasTenantContext() || !actor.hasPermission(permission)) {
            throw new AccessDeniedException("Report subscription access denied.");
        }
        OrganizationDataScope requiredScope = dataScope == null ? OrganizationDataScope.empty() : dataScope;
        requiredScope.requireTenant();
        if (!actor.tenantId().equals(requiredScope.tenantId())) {
            throw new AccessDeniedException("Report subscription tenant scope does not match the current tenant.");
        }
    }

    private void requireDataAccess(OrganizationDataScope dataScope) {
        if (dataScope == null) {
            throw new AccessDeniedException("Report subscription organization scope is required.");
        }
        dataScope.requireDataAccess();
    }

    private ReportSubscriptionEntity requireOwned(AuthPrincipal actor, Long subscriptionId) {
        if (subscriptionId == null) {
            throw new IllegalArgumentException("subscriptionId is required.");
        }
        return repository.findByTenantIdAndIdAndCreatorUserIdAndDeletedAtIsNull(
                        actor.tenantId(), subscriptionId, actor.userId())
                .orElseThrow(() -> new NoSuchElementException("Report subscription was not found."));
    }

    private void assertUnique(Long tenantId, Long creatorUserId, String configurationKey, Long currentId) {
        boolean duplicate = currentId == null
                ? repository.existsByTenantIdAndCreatorUserIdAndActiveConfigurationKey(
                        tenantId, creatorUserId, configurationKey)
                : repository.existsByTenantIdAndCreatorUserIdAndActiveConfigurationKeyAndIdNot(
                        tenantId, creatorUserId, configurationKey, currentId);
        if (duplicate) {
            throw new IllegalStateException("An identical active report subscription already exists.");
        }
    }

    private ReportSubscriptionEntity save(ReportSubscriptionEntity entity) {
        try {
            return repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("An identical active report subscription already exists.", exception);
        }
    }

    private void requireVersion(ReportSubscriptionEntity entity, Long expectedVersion) {
        if (expectedVersion == null) {
            throw new IllegalArgumentException("version is required.");
        }
        if (expectedVersion != null && !expectedVersion.equals(entity.getVersion())) {
            throw new IllegalStateException("The report subscription changed since it was loaded.");
        }
    }

    private boolean scopeCovered(ReportScope scope, OrganizationDataScope dataScope) {
        if ("GLOBAL".equals(scope.type())) {
            return dataScope.unrestricted() || dataScope.rootCoverage();
        }
        return dataScope.containsAllNodes(scope.organizationNodeIds());
    }

    private String normalizeLanguage(String value) {
        if ("zh".equalsIgnoreCase(value)) {
            return "zh";
        }
        if ("en".equalsIgnoreCase(value)) {
            return "en";
        }
        throw new IllegalArgumentException("language must be zh or en.");
    }

    private String normalizeTopic(String value, ReportType reportType) {
        String normalized = value == null ? "" : value.trim();
        if (reportType == ReportType.TOPIC && normalized.isBlank()) {
            throw new IllegalArgumentException("topic is required for a topic report.");
        }
        if (normalized.length() > MAX_TOPIC_LENGTH) {
            throw new IllegalArgumentException("topic exceeds the allowed length.");
        }
        return normalized;
    }

    private String normalizeChannelKey(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches(CHANNEL_KEY_PATTERN) || !"email".equals(normalized)) {
            throw new IllegalArgumentException("channelKey must be email.");
        }
        return normalized;
    }

    private void requireExecutableRecipients(Long tenantId, String channelKey, Set<Long> recipientUserIds) {
        if (!"email".equals(channelKey)) {
            throw new IllegalArgumentException("Only the email delivery channel can be enabled.");
        }
        List<TenantRecipient> recipients = memberDirectory.requireReportRecipients(tenantId, recipientUserIds);
        if (recipients.stream().anyMatch(recipient -> !recipient.emailConfigured())) {
            throw new IllegalArgumentException("Every enabled email recipient must have an email address.");
        }
    }

    private String configurationKey(
            ReportType reportType,
            ReportScope scope,
            String language,
            String topic,
            ReportSubscriptionSchedule schedule,
            String channelKey,
            Set<Long> recipientUserIds
    ) {
        String recipients = recipientUserIds.stream()
                .sorted(Comparator.naturalOrder())
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        String canonical = String.join("\n",
                reportType.wireName(), scope.type(), scope.id(), language, topic,
                schedule.canonicalValue(), channelKey, recipients);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void audit(
            AuthPrincipal actor,
            ReportSubscriptionEntity entity,
            String action,
            String detailCode,
            String traceId
    ) {
        auditService.record(
                actor.tenantId(), actor.userId(), action, "REPORT_SUBSCRIPTION",
                String.valueOf(entity.getId()), "SUCCESS", traceId, detailCode);
    }

    private ReportSubscriptionView toView(
            ReportSubscriptionEntity entity,
            ExecutionEligibility eligibility
    ) {
        ReportSubscriptionSchedule schedule = entity.schedule();
        return new ReportSubscriptionView(
                entity.getId(),
                entity.reportType().wireName(),
                entity.getLanguage(),
                entity.getTopic(),
                entity.scope(),
                schedule.kind().name(),
                schedule.localTime().toString(),
                schedule.timeZone().getId(),
                schedule.dayOfWeek() == null ? null : schedule.dayOfWeek().getValue(),
                schedule.dayOfMonth(),
                entity.getChannelKey(),
                entity.getRecipientUserIds(),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getNextRunAt(),
                entity.getMisfirePolicy(),
                entity.getMisfireGraceMinutes(),
                eligibility.eligible(),
                eligibility.reason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public record DefinitionInput(
            String reportType,
            String language,
            String topic,
            String scheduleKind,
            String localTime,
            String timeZone,
            Integer dayOfWeek,
            Integer dayOfMonth,
            String channelKey,
            Set<Long> recipientUserIds
    ) {
    }

    public record RecipientView(Long userId, String username, String displayName, boolean emailConfigured) {
        public RecipientView(Long userId, String username, String displayName) {
            this(userId, username, displayName, false);
        }
    }

    public record ExecutionEligibility(boolean eligible, String reason) {
        public static ExecutionEligibility allowed() {
            return new ExecutionEligibility(true, "eligible");
        }

        public static ExecutionEligibility denied(String reason) {
            return new ExecutionEligibility(false, reason);
        }
    }

    public record ReportSubscriptionView(
            Long id,
            String reportType,
            String language,
            String topic,
            ReportScope scope,
            String scheduleKind,
            String localTime,
            String timeZone,
            Integer dayOfWeek,
            Integer dayOfMonth,
            String channelKey,
            Set<Long> recipientUserIds,
            boolean enabled,
            Instant nextRunAt,
            String misfirePolicy,
            int misfireGraceMinutes,
            boolean executionEligible,
            String eligibilityReason,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }

    private record ValidatedDefinition(
            ReportType reportType,
            ReportScope scope,
            String language,
            String topic,
            ReportSubscriptionSchedule schedule,
            String channelKey,
            Set<Long> recipientUserIds,
            String configurationKey
    ) {
    }
}
