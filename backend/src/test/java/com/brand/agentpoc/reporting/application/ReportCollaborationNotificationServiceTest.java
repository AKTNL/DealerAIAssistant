package com.brand.agentpoc.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationAuthorizationContext;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.domain.ReportCollaborationEventType;
import com.brand.agentpoc.reporting.domain.ReportDeliveryStatus;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import com.brand.agentpoc.reporting.domain.ReportScope;
import com.brand.agentpoc.reporting.domain.ReportType;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationEventEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationEventRepository;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationNotificationEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationNotificationRepository;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportCollaborationRepository;
import com.brand.agentpoc.tenant.application.TenantMemberDirectory;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class ReportCollaborationNotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T03:00:00Z");

    private ReportCollaborationNotificationRepository notificationRepository;
    private ReportCollaborationRepository collaborationRepository;
    private ReportCollaborationEventRepository eventRepository;
    private ReportDraftStore draftStore;
    private ReportDeliveryPort deliveryPort;
    private TenantMemberDirectory memberDirectory;
    private TenantRepository tenantRepository;
    private OrganizationAuthorizationService authorizationService;
    private ReportCollaborationNotificationService service;
    private ReportCollaborationEntity collaboration;
    private ReportCollaborationEventEntity event;
    private ReportCollaborationNotificationEntity notification;
    private ReportDraft draft;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(ReportCollaborationNotificationRepository.class);
        collaborationRepository = mock(ReportCollaborationRepository.class);
        eventRepository = mock(ReportCollaborationEventRepository.class);
        draftStore = mock(ReportDraftStore.class);
        deliveryPort = mock(ReportDeliveryPort.class);
        memberDirectory = mock(TenantMemberDirectory.class);
        tenantRepository = mock(TenantRepository.class);
        authorizationService = mock(OrganizationAuthorizationService.class);
        service = new ReportCollaborationNotificationService(
                notificationRepository, collaborationRepository, eventRepository, draftStore,
                deliveryPort, memberDirectory, tenantRepository, authorizationService,
                mock(AuthAuditService.class), Clock.fixed(NOW, ZoneOffset.UTC));

        draft = new ReportDraft(
                "report-1", ReportType.DAILY, "Daily report", "en", "# Daily",
                NOW.minusSeconds(3600), "batch-1", new ReportScope("ORGANIZATION", "10"),
                "deterministic", "v1", 7L);
        collaboration = new ReportCollaborationEntity(draft);
        ReflectionTestUtils.setField(collaboration, "id", 11L);
        ReflectionTestUtils.setField(collaboration, "version", 0L);
        collaboration.assign(8L, "owner", "Report Owner", NOW.minusSeconds(120));
        event = new ReportCollaborationEventEntity(
                collaboration, ReportCollaborationEventType.ASSIGNEE_CHANGED,
                2L, "analyst", "Analyst", "unassigned", "8", null, "trace-1", NOW.minusSeconds(60));
        ReflectionTestUtils.setField(event, "id", 12L);
        notification = new ReportCollaborationNotificationEntity(
                collaboration, event, 8L, "report-collaboration:12:email:8", NOW.minusSeconds(30));
        ReflectionTestUtils.setField(notification, "id", 13L);
        ReflectionTestUtils.setField(notification, "version", 0L);
        notification.claim("worker-1", NOW.minusSeconds(10), NOW.plusSeconds(300));

        when(notificationRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(notification));
        when(notificationRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(collaborationRepository.findById(11L)).thenReturn(Optional.of(collaboration));
        when(eventRepository.findById(12L)).thenReturn(Optional.of(event));
        when(draftStore.findByTenantIdAndId(7L, "report-1")).thenReturn(Optional.of(draft));
        TenantEntity tenant = mock(TenantEntity.class);
        when(tenant.getEnabled()).thenReturn(true);
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));
        AuthPrincipal recipient = recipient();
        when(memberDirectory.requireActivePrincipal(7L, 8L)).thenReturn(recipient);
        when(memberDirectory.requireEmail(7L, 8L)).thenReturn("owner@example.com");
        OrganizationDataScope scope = OrganizationDataScope.tenantScope(
                7L, "tenant-a", Set.of(10L), Set.of(10L), Set.of("D001"), false);
        when(authorizationService.resolve(recipient))
                .thenReturn(new OrganizationAuthorizationContext(recipient, scope));
    }

    @Test
    void sendsThroughTheP2DeliveryPortAndMarksSmtpAcceptance() {
        when(deliveryPort.deliver(any())).thenReturn(ReportDeliveryPort.DeliveryResult.succeeded("smtp-1"));

        var result = service.executeClaimed(13L, "worker-1", NOW);

        assertThat(result.status()).isEqualTo(ReportDeliveryStatus.SUCCEEDED);
        verify(deliveryPort).deliver(any());
    }

    @Test
    void cancelsBeforeProviderCallWhenTheAssigneeWasDisabled() {
        when(memberDirectory.requireActivePrincipal(7L, 8L))
                .thenThrow(new AccessDeniedException("disabled"));

        var result = service.executeClaimed(13L, "worker-1", NOW);

        assertThat(result.status()).isEqualTo(ReportDeliveryStatus.CANCELLED);
        assertThat(result.errorCode()).isEqualTo("RECIPIENT_INACTIVE");
        verify(deliveryPort, never()).deliver(any());
    }

    private AuthPrincipal recipient() {
        return new AuthPrincipal(
                8L, 3L, "family", "owner", "Report Owner", true, false,
                Set.of("VIEWER"), Set.of(PermissionKey.REPORT_READ),
                7L, "tenant-a", 9L, Set.of(12L));
    }
}
