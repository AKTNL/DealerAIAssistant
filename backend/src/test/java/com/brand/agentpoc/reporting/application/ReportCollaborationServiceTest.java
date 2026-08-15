package com.brand.agentpoc.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationAuthorizationContext;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.application.ReportCollaborationService.CollaborationFilter;
import com.brand.agentpoc.reporting.application.ReportCollaborationService.ReportCollaborationConflictException;
import com.brand.agentpoc.reporting.domain.ReportCollaborationEventType;
import com.brand.agentpoc.reporting.domain.ReportCollaborationStatus;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import com.brand.agentpoc.reporting.domain.ReportScope;
import com.brand.agentpoc.reporting.domain.ReportType;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationEventEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationEventRepository;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationRepository;
import com.brand.agentpoc.tenant.application.TenantMemberDirectory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class ReportCollaborationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T02:00:00Z");

    private ReportCollaborationRepository collaborationRepository;
    private ReportCollaborationEventRepository eventRepository;
    private ReportCollaborationNotificationService notificationService;
    private ReportDraftStore draftStore;
    private TenantMemberDirectory memberDirectory;
    private OrganizationAuthorizationService authorizationService;
    private ReportCollaborationService service;
    private AuthPrincipal actor;
    private OrganizationDataScope actorScope;
    private ReportDraft draft;
    private ReportCollaborationEntity collaboration;
    private List<ReportCollaborationEventEntity> events;

    @BeforeEach
    void setUp() {
        collaborationRepository = mock(ReportCollaborationRepository.class);
        eventRepository = mock(ReportCollaborationEventRepository.class);
        notificationService = mock(ReportCollaborationNotificationService.class);
        draftStore = mock(ReportDraftStore.class);
        memberDirectory = mock(TenantMemberDirectory.class);
        authorizationService = mock(OrganizationAuthorizationService.class);
        AuthAuditService auditService = mock(AuthAuditService.class);
        service = new ReportCollaborationService(
                collaborationRepository, eventRepository, notificationService, draftStore,
                memberDirectory, authorizationService, auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));

        actor = principal(2L, "analyst", "Analyst", EnumSet.of(
                PermissionKey.REPORT_READ, PermissionKey.REPORT_COLLABORATE));
        actorScope = scope(Set.of(10L));
        draft = draft("report-1", 10L, Instant.parse("2026-08-15T00:00:00Z"));
        collaboration = persistedCollaboration(draft, 11L, 0L);
        events = new ArrayList<>();

        when(draftStore.findByTenantIdAndId(7L, "report-1")).thenReturn(Optional.of(draft));
        when(draftStore.findAllByTenantId(7L)).thenReturn(List.of(draft));
        when(collaborationRepository.findByTenantIdAndReportDraftId(7L, "report-1"))
                .thenReturn(Optional.of(collaboration));
        when(collaborationRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ReportCollaborationEventEntity event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", (long) events.size() + 20L);
            events.add(event);
            return event;
        });
        when(eventRepository.findByTenantIdAndReportDraftIdOrderByCreatedAtAscIdAsc(7L, "report-1"))
                .thenAnswer(invocation -> List.copyOf(events));
    }

    @Test
    void movesForwardAndRecordsAnAuditableNotificationEvent() {
        var detail = service.changeStatus(
                actor, actorScope, "report-1", 0L, "IN_PROGRESS", "trace-status");

        assertThat(detail.report().status()).isEqualTo(ReportCollaborationStatus.IN_PROGRESS);
        assertThat(detail.timeline()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(ReportCollaborationEventType.STATUS_CHANGED);
            assertThat(event.previousValue()).isEqualTo("OPEN");
            assertThat(event.currentValue()).isEqualTo("IN_PROGRESS");
            assertThat(event.traceId()).isEqualTo("trace-status");
        });
        verify(notificationService).materialize(eq(collaboration), any(), eq(2L), eq(NOW));
    }

    @Test
    void staleVersionReturnsTheCurrentVersionWithoutChangingState() {
        assertThatThrownBy(() -> service.changeStatus(
                actor, actorScope, "report-1", 9L, "IN_PROGRESS", "trace-stale"))
                .isInstanceOfSatisfying(ReportCollaborationConflictException.class,
                        exception -> assertThat(exception.currentVersion()).isZero());
        assertThat(collaboration.getStatus()).isEqualTo(ReportCollaborationStatus.OPEN);
    }

    @Test
    void rejectsBackwardAndTerminalMutations() {
        collaboration.changeStatus(ReportCollaborationStatus.IN_PROGRESS, NOW);
        collaboration.changeStatus(ReportCollaborationStatus.RESOLVED, NOW.plusSeconds(1));

        assertThatThrownBy(() -> service.changeStatus(
                actor, actorScope, "report-1", 0L, "OPEN", "trace-backward"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not allowed");
        assertThatThrownBy(() -> service.addComment(
                actor, actorScope, "report-1", 0L, "late comment", "trace-late"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void rejectsAssigneeWhoseOrganizationScopeDoesNotCoverTheReport() {
        AuthPrincipal assignee = principal(8L, "south", "South Analyst", Set.of(PermissionKey.REPORT_READ));
        when(memberDirectory.requireActivePrincipal(7L, 8L)).thenReturn(assignee);
        when(authorizationService.resolve(assignee))
                .thenReturn(new OrganizationAuthorizationContext(assignee, scope(Set.of(20L))));

        assertThatThrownBy(() -> service.changeAssignee(
                actor, actorScope, "report-1", 0L, 8L, "trace-assignee"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("cannot access");
    }

    @Test
    void disabledMemberCannotReceiveANewAssignment() {
        when(memberDirectory.requireActivePrincipal(7L, 8L))
                .thenThrow(new AccessDeniedException("disabled"));

        assertThatThrownBy(() -> service.changeAssignee(
                actor, actorScope, "report-1", 0L, 8L, "trace-disabled"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void appendsAnImmutableCommentToTheTimeline() {
        var detail = service.addComment(
                actor, actorScope, "report-1", 0L, "  Investigating the dealer variance.  ", "trace-comment");

        assertThat(detail.timeline()).singleElement()
                .extracting(ReportCollaborationService.TimelineEventView::commentBody)
                .isEqualTo("Investigating the dealer variance.");
        assertThat(collaboration.getActivityCount()).isEqualTo(1L);
    }

    @Test
    void reportReadWithoutCollaboratePermissionRemainsReadOnly() {
        AuthPrincipal viewer = principal(9L, "viewer", "Viewer", Set.of(PermissionKey.REPORT_READ));

        assertThat(service.get(viewer, actorScope, "report-1").report().reportId()).isEqualTo("report-1");
        assertThatThrownBy(() -> service.addComment(
                viewer, actorScope, "report-1", 0L, "Not allowed", "trace-viewer"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void filtersByStatusOrganizationAndGenerationTimeWithinTheActiveScope() {
        List<ReportCollaborationService.ReportCollaborationSummary> results = service.list(
                actor,
                actorScope,
                new CollaborationFilter(
                        "OPEN", null, 10L,
                        Instant.parse("2026-08-14T00:00:00Z"),
                        Instant.parse("2026-08-16T00:00:00Z"))
        );

        assertThat(results).singleElement()
                .extracting(ReportCollaborationService.ReportCollaborationSummary::reportId)
                .isEqualTo("report-1");
    }

    private ReportCollaborationEntity persistedCollaboration(ReportDraft value, Long id, Long version) {
        ReportCollaborationEntity entity = new ReportCollaborationEntity(value);
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "version", version);
        return entity;
    }

    private ReportDraft draft(String id, Long organizationId, Instant generatedAt) {
        return new ReportDraft(
                id, ReportType.DAILY, "Daily report", "en", "# Daily report",
                generatedAt, "batch-1", new ReportScope("ORGANIZATION", String.valueOf(organizationId)),
                "deterministic", "v1", 7L);
    }

    private OrganizationDataScope scope(Set<Long> organizationIds) {
        return OrganizationDataScope.tenantScope(
                7L, "tenant-a", organizationIds, organizationIds, Set.of("D001"), false);
    }

    private AuthPrincipal principal(
            Long userId,
            String username,
            String displayName,
            Set<PermissionKey> permissions
    ) {
        return new AuthPrincipal(
                userId, 3L, "family", username, displayName, true, false,
                Set.of("ANALYST"), permissions, 7L, "tenant-a", 8L, Set.of(11L));
    }
}
