package com.brand.agentpoc.reporting.application;

import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.domain.ReportCollaborationEventType;
import com.brand.agentpoc.reporting.domain.ReportCollaborationStatus;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import com.brand.agentpoc.reporting.domain.ReportScope;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationEventEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationEventRepository;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationRepository;
import com.brand.agentpoc.tenant.application.TenantMemberDirectory;
import com.brand.agentpoc.tenant.application.TenantMemberDirectory.TenantRecipient;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReportCollaborationService implements ReportCollaborationInitializer {

    private static final int MAX_COMMENT_LENGTH = 2000;
    private static final int MAX_TRACE_ID_LENGTH = 128;
    private static final String SYSTEM_ACTOR = "system";

    private final ReportCollaborationRepository collaborationRepository;
    private final ReportCollaborationEventRepository eventRepository;
    private final ReportCollaborationNotificationService notificationService;
    private final ReportDraftStore draftStore;
    private final TenantMemberDirectory memberDirectory;
    private final OrganizationAuthorizationService authorizationService;
    private final AuthAuditService auditService;
    private final Clock clock;

    public ReportCollaborationService(
            ReportCollaborationRepository collaborationRepository,
            ReportCollaborationEventRepository eventRepository,
            ReportCollaborationNotificationService notificationService,
            ReportDraftStore draftStore,
            TenantMemberDirectory memberDirectory,
            OrganizationAuthorizationService authorizationService,
            AuthAuditService auditService,
            Clock clock
    ) {
        this.collaborationRepository = collaborationRepository;
        this.eventRepository = eventRepository;
        this.notificationService = notificationService;
        this.draftStore = draftStore;
        this.memberDirectory = memberDirectory;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    public void initialize(ReportDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Report draft is required.");
        }
        ensureCollaboration(draft);
    }

    public List<ReportCollaborationSummary> list(
            AuthPrincipal actor,
            OrganizationDataScope dataScope,
            CollaborationFilter filter
    ) {
        requireActor(actor, dataScope, PermissionKey.REPORT_READ);
        dataScope.requireDataAccess();
        ValidatedFilter validated = validateFilter(dataScope, filter);
        List<ReportDraft> drafts = draftStore.findAllByTenantId(actor.tenantId()).stream()
                .filter(draft -> ReportAccessPolicy.canRead(draft, dataScope))
                .filter(matchesDraft(validated))
                .sorted(Comparator.comparing(ReportDraft::generatedAt).reversed())
                .toList();
        Map<String, ReportCollaborationEntity> collaborations = drafts.stream()
                .map(this::ensureCollaboration)
                .collect(Collectors.toMap(ReportCollaborationEntity::getReportDraftId, value -> value));
        return drafts.stream()
                .map(draft -> toSummary(draft, collaborations.get(draft.id())))
                .filter(summary -> validated.status() == null || validated.status() == summary.status())
                .filter(summary -> validated.assigneeUserId() == null
                        || validated.assigneeUserId().equals(summary.assignee() == null
                                ? null : summary.assignee().userId()))
                .toList();
    }

    public ReportCollaborationDetail get(
            AuthPrincipal actor,
            OrganizationDataScope dataScope,
            String reportId
    ) {
        requireActor(actor, dataScope, PermissionKey.REPORT_READ);
        ReportDraft draft = requireDraft(actor.tenantId(), reportId, dataScope);
        return toDetail(draft, ensureCollaboration(draft));
    }

    public List<AssigneeView> listAssignees(
            AuthPrincipal actor,
            OrganizationDataScope dataScope,
            String reportId
    ) {
        requireActor(actor, dataScope, PermissionKey.REPORT_READ);
        ReportDraft draft = requireDraft(actor.tenantId(), reportId, dataScope);
        return memberDirectory.listReportRecipients(actor.tenantId()).stream()
                .filter(recipient -> canAccessReport(recipient, draft))
                .map(recipient -> new AssigneeView(
                        recipient.userId(), recipient.username(), recipient.displayName()))
                .toList();
    }

    public ReportCollaborationDetail changeStatus(
            AuthPrincipal actor,
            OrganizationDataScope dataScope,
            String reportId,
            Long version,
            String requestedStatus,
            String traceId
    ) {
        requireActor(actor, dataScope, PermissionKey.REPORT_COLLABORATE);
        ReportDraft draft = requireDraft(actor.tenantId(), reportId, dataScope);
        ReportCollaborationEntity collaboration = requireVersion(ensureCollaboration(draft), version);
        ReportCollaborationStatus target = ReportCollaborationStatus.parse(requestedStatus);
        if (target == collaboration.getStatus()) {
            return toDetail(draft, collaboration);
        }
        String previous = collaboration.getStatus().name();
        collaboration.changeStatus(target, clock.instant());
        ReportCollaborationEntity saved = collaborationRepository.saveAndFlush(collaboration);
        ReportCollaborationEventEntity event = recordEvent(
                actor, saved, ReportCollaborationEventType.STATUS_CHANGED,
                previous, target.name(), null, traceId);
        notificationService.materialize(saved, event, actor.userId(), clock.instant());
        return toDetail(draft, saved);
    }

    public ReportCollaborationDetail changeAssignee(
            AuthPrincipal actor,
            OrganizationDataScope dataScope,
            String reportId,
            Long version,
            Long assigneeUserId,
            String traceId
    ) {
        requireActor(actor, dataScope, PermissionKey.REPORT_COLLABORATE);
        ReportDraft draft = requireDraft(actor.tenantId(), reportId, dataScope);
        ReportCollaborationEntity collaboration = requireVersion(ensureCollaboration(draft), version);
        if (java.util.Objects.equals(collaboration.getAssigneeUserId(), assigneeUserId)) {
            return toDetail(draft, collaboration);
        }
        String previous = assigneeValue(collaboration.getAssigneeUserId());
        if (assigneeUserId == null) {
            collaboration.assign(null, null, null, clock.instant());
        } else {
            AuthPrincipal assignee = requireAssignee(actor.tenantId(), assigneeUserId, draft);
            collaboration.assign(
                    assignee.userId(), assignee.username(), assignee.displayName(), clock.instant());
        }
        ReportCollaborationEntity saved = collaborationRepository.saveAndFlush(collaboration);
        ReportCollaborationEventEntity event = recordEvent(
                actor, saved, ReportCollaborationEventType.ASSIGNEE_CHANGED,
                previous, assigneeValue(saved.getAssigneeUserId()), null, traceId);
        notificationService.materialize(saved, event, actor.userId(), clock.instant());
        return toDetail(draft, saved);
    }

    public ReportCollaborationDetail addComment(
            AuthPrincipal actor,
            OrganizationDataScope dataScope,
            String reportId,
            Long version,
            String body,
            String traceId
    ) {
        requireActor(actor, dataScope, PermissionKey.REPORT_COLLABORATE);
        String comment = normalizeComment(body);
        ReportDraft draft = requireDraft(actor.tenantId(), reportId, dataScope);
        ReportCollaborationEntity collaboration = requireVersion(ensureCollaboration(draft), version);
        collaboration.addCommentActivity(clock.instant());
        ReportCollaborationEntity saved = collaborationRepository.saveAndFlush(collaboration);
        ReportCollaborationEventEntity event = recordEvent(
                actor, saved, ReportCollaborationEventType.COMMENT_ADDED,
                null, "comment_length:" + comment.length(), comment, traceId);
        notificationService.materialize(saved, event, actor.userId(), clock.instant());
        return toDetail(draft, saved);
    }

    @Transactional(readOnly = true)
    public Long currentVersion(
            AuthPrincipal actor,
            OrganizationDataScope dataScope,
            String reportId
    ) {
        requireActor(actor, dataScope, PermissionKey.REPORT_READ);
        requireDraft(actor.tenantId(), reportId, dataScope);
        return collaborationRepository.findByTenantIdAndReportDraftId(actor.tenantId(), normalizeReportId(reportId))
                .map(ReportCollaborationEntity::getVersion)
                .orElse(null);
    }

    private ReportCollaborationEntity ensureCollaboration(ReportDraft draft) {
        ReportCollaborationEntity existing = collaborationRepository
                .findByTenantIdAndReportDraftId(draft.tenantId(), draft.id())
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        try {
            ReportCollaborationEntity saved = collaborationRepository.saveAndFlush(
                    new ReportCollaborationEntity(draft));
            ReportCollaborationEventEntity event = new ReportCollaborationEventEntity(
                    saved, ReportCollaborationEventType.CREATED, null, SYSTEM_ACTOR, "System",
                    null, ReportCollaborationStatus.OPEN.name(), null,
                    "report-generation", draft.generatedAt());
            eventRepository.saveAndFlush(event);
            auditService.record(saved.getTenantId(), null, "REPORT_COLLABORATION_CREATE",
                    "REPORT_COLLABORATION", String.valueOf(saved.getId()), "SUCCESS",
                    "report-generation", "collaboration_initialized");
            return saved;
        } catch (DataIntegrityViolationException exception) {
            return collaborationRepository.findByTenantIdAndReportDraftId(draft.tenantId(), draft.id())
                    .orElseThrow(() -> exception);
        }
    }

    private ReportCollaborationEventEntity recordEvent(
            AuthPrincipal actor,
            ReportCollaborationEntity collaboration,
            ReportCollaborationEventType type,
            String previousValue,
            String currentValue,
            String commentBody,
            String traceId
    ) {
        String safeTraceId = safeTraceId(traceId);
        ReportCollaborationEventEntity event = eventRepository.saveAndFlush(
                new ReportCollaborationEventEntity(
                        collaboration, type, actor.userId(), actor.username(), actor.displayName(),
                        previousValue, currentValue, commentBody, safeTraceId, clock.instant()));
        auditService.record(
                collaboration.getTenantId(), actor.userId(), "REPORT_COLLABORATION_" + type.name(),
                "REPORT_COLLABORATION", String.valueOf(collaboration.getId()), "SUCCESS",
                safeTraceId, detailCode(type, previousValue, currentValue));
        return event;
    }

    private String detailCode(
            ReportCollaborationEventType type,
            String previousValue,
            String currentValue
    ) {
        return switch (type) {
            case STATUS_CHANGED -> "status_" + lower(previousValue) + "_to_" + lower(currentValue);
            case ASSIGNEE_CHANGED -> "assignee_changed";
            case COMMENT_ADDED -> "comment_added";
            case CREATED -> "collaboration_initialized";
        };
    }

    private String lower(String value) {
        return value == null ? "none" : value.toLowerCase(java.util.Locale.ROOT);
    }

    private ReportDraft requireDraft(Long tenantId, String reportId, OrganizationDataScope dataScope) {
        String normalized = normalizeReportId(reportId);
        ReportDraft draft = draftStore.findByTenantIdAndId(tenantId, normalized)
                .orElseThrow(() -> new NoSuchElementException("Report was not found."));
        if (!ReportAccessPolicy.canRead(draft, dataScope)) {
            throw new NoSuchElementException("Report was not found.");
        }
        return draft;
    }

    private AuthPrincipal requireAssignee(Long tenantId, Long userId, ReportDraft draft) {
        AuthPrincipal assignee = memberDirectory.requireActivePrincipal(tenantId, userId);
        if (!assignee.hasPermission(PermissionKey.REPORT_READ)) {
            throw new AccessDeniedException("The assignee cannot access this report.");
        }
        OrganizationDataScope assigneeScope = authorizationService.resolve(assignee).dataScope();
        if (!ReportAccessPolicy.canRead(draft, assigneeScope)) {
            throw new AccessDeniedException("The assignee cannot access this report.");
        }
        return assignee;
    }

    private boolean canAccessReport(TenantRecipient recipient, ReportDraft draft) {
        try {
            requireAssignee(draft.tenantId(), recipient.userId(), draft);
            return true;
        } catch (AccessDeniedException | IllegalArgumentException exception) {
            return false;
        }
    }

    private ReportCollaborationEntity requireVersion(
            ReportCollaborationEntity collaboration,
            Long expectedVersion
    ) {
        if (expectedVersion == null) {
            throw new IllegalArgumentException("version is required.");
        }
        if (!expectedVersion.equals(collaboration.getVersion())) {
            throw new ReportCollaborationConflictException(collaboration.getVersion());
        }
        return collaboration;
    }

    private void requireActor(AuthPrincipal actor, OrganizationDataScope dataScope, PermissionKey permission) {
        if (actor == null || !actor.enabled() || !actor.hasTenantContext() || !actor.hasPermission(permission)) {
            throw new AccessDeniedException("Report collaboration access denied.");
        }
        OrganizationDataScope requiredScope = dataScope == null ? OrganizationDataScope.empty() : dataScope;
        requiredScope.requireTenant();
        if (!actor.tenantId().equals(requiredScope.tenantId())) {
            throw new AccessDeniedException("Report collaboration tenant scope does not match the current tenant.");
        }
    }

    private ValidatedFilter validateFilter(OrganizationDataScope dataScope, CollaborationFilter filter) {
        CollaborationFilter input = filter == null ? new CollaborationFilter(null, null, null, null, null) : filter;
        ReportCollaborationStatus status = input.status() == null || input.status().isBlank()
                ? null : ReportCollaborationStatus.parse(input.status());
        if (input.assigneeUserId() != null && input.assigneeUserId() <= 0) {
            throw new IllegalArgumentException("assigneeUserId must be positive.");
        }
        if (input.organizationId() != null) {
            if (input.organizationId() <= 0) {
                throw new IllegalArgumentException("organizationId must be positive.");
            }
            if (!dataScope.unrestricted() && !dataScope.containsAllNodes(Set.of(input.organizationId()))) {
                throw new AccessDeniedException("The organization filter is outside the active scope.");
            }
        }
        if (input.generatedFrom() != null && input.generatedTo() != null
                && input.generatedFrom().isAfter(input.generatedTo())) {
            throw new IllegalArgumentException("generatedFrom must not be after generatedTo.");
        }
        return new ValidatedFilter(
                status, input.assigneeUserId(), input.organizationId(),
                input.generatedFrom(), input.generatedTo());
    }

    private Predicate<ReportDraft> matchesDraft(ValidatedFilter filter) {
        return draft -> (filter.generatedFrom() == null || !draft.generatedAt().isBefore(filter.generatedFrom()))
                && (filter.generatedTo() == null || !draft.generatedAt().isAfter(filter.generatedTo()))
                && matchesOrganization(draft.scope(), filter.organizationId());
    }

    private boolean matchesOrganization(ReportScope scope, Long organizationId) {
        if (organizationId == null) {
            return true;
        }
        return "ORGANIZATION".equals(scope.type())
                && scope.organizationNodeIds().contains(organizationId);
    }

    private ReportCollaborationDetail toDetail(
            ReportDraft draft,
            ReportCollaborationEntity collaboration
    ) {
        List<TimelineEventView> timeline = eventRepository
                .findByTenantIdAndReportDraftIdOrderByCreatedAtAscIdAsc(draft.tenantId(), draft.id()).stream()
                .map(this::toTimelineEvent)
                .toList();
        return new ReportCollaborationDetail(toSummary(draft, collaboration), draft.markdown(), timeline);
    }

    private ReportCollaborationSummary toSummary(
            ReportDraft draft,
            ReportCollaborationEntity collaboration
    ) {
        AssigneeView assignee = collaboration.getAssigneeUserId() == null
                ? null
                : new AssigneeView(
                        collaboration.getAssigneeUserId(),
                        collaboration.getAssigneeUsername(),
                        collaboration.getAssigneeDisplayName(),
                        assigneeActive(collaboration, draft));
        return new ReportCollaborationSummary(
                draft.id(), draft.title(), draft.reportType().wireName(), draft.language(),
                draft.generatedAt(), draft.scope(), collaboration.getStatus(), assignee,
                collaboration.getUpdatedAt(), collaboration.getVersion());
    }

    private boolean assigneeActive(ReportCollaborationEntity collaboration, ReportDraft draft) {
        try {
            requireAssignee(collaboration.getTenantId(), collaboration.getAssigneeUserId(), draft);
            return true;
        } catch (AccessDeniedException | IllegalArgumentException exception) {
            return false;
        }
    }

    private TimelineEventView toTimelineEvent(ReportCollaborationEventEntity event) {
        return new TimelineEventView(
                event.getId(), event.getEventType(), event.getActorUserId(),
                event.getActorUsername(), event.getActorDisplayName(),
                event.getPreviousValue(), event.getCurrentValue(), event.getCommentBody(),
                event.getTraceId(), event.getCreatedAt());
    }

    private String normalizeComment(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Comment body is required.");
        }
        if (normalized.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Comment body exceeds the allowed length.");
        }
        return normalized;
    }

    private String normalizeReportId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reportId is required.");
        }
        return value.trim();
    }

    private String safeTraceId(String value) {
        String normalized = value == null || value.isBlank() ? "unavailable" : value.trim();
        return normalized.length() <= MAX_TRACE_ID_LENGTH
                ? normalized : normalized.substring(0, MAX_TRACE_ID_LENGTH);
    }

    private String assigneeValue(Long userId) {
        return userId == null ? "unassigned" : String.valueOf(userId);
    }

    public record CollaborationFilter(
            String status,
            Long assigneeUserId,
            Long organizationId,
            Instant generatedFrom,
            Instant generatedTo
    ) {
    }

    public record AssigneeView(Long userId, String username, String displayName, boolean active) {
        public AssigneeView(Long userId, String username, String displayName) {
            this(userId, username, displayName, true);
        }
    }

    public record ReportCollaborationSummary(
            String reportId,
            String title,
            String reportType,
            String language,
            Instant generatedAt,
            ReportScope scope,
            ReportCollaborationStatus status,
            AssigneeView assignee,
            Instant updatedAt,
            Long version
    ) {
    }

    public record ReportCollaborationDetail(
            ReportCollaborationSummary report,
            String markdown,
            List<TimelineEventView> timeline
    ) {
    }

    public record TimelineEventView(
            Long id,
            ReportCollaborationEventType type,
            Long actorUserId,
            String actorUsername,
            String actorDisplayName,
            String previousValue,
            String currentValue,
            String commentBody,
            String traceId,
            Instant createdAt
    ) {
    }

    public static final class ReportCollaborationConflictException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final Long currentVersion;

        public ReportCollaborationConflictException(Long currentVersion) {
            super("The report collaboration changed since it was loaded.");
            this.currentVersion = currentVersion;
        }

        public Long currentVersion() {
            return currentVersion;
        }
    }

    private record ValidatedFilter(
            ReportCollaborationStatus status,
            Long assigneeUserId,
            Long organizationId,
            Instant generatedFrom,
            Instant generatedTo
    ) {
    }
}
