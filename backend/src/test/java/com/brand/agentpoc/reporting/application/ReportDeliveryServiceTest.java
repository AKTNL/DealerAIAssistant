package com.brand.agentpoc.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.reporting.application.ReportDeliveryPort.DeliveryRequest;
import com.brand.agentpoc.reporting.application.ReportDeliveryPort.DeliveryResult;
import com.brand.agentpoc.reporting.domain.ReportDeliveryStatus;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import com.brand.agentpoc.reporting.domain.ReportScope;
import com.brand.agentpoc.reporting.domain.ReportType;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportDeliveryEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportDeliveryRepository;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportGenerationJobEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportSubscriptionEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportSubscriptionRepository;
import com.brand.agentpoc.tenant.application.TenantMemberDirectory;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReportDeliveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");

    private ReportDeliveryRepository deliveryRepository;
    private ReportSubscriptionRepository subscriptionRepository;
    private ReportSubscriptionService subscriptionService;
    private ReportDraftStore draftStore;
    private ReportDeliveryPort deliveryPort;
    private TenantMemberDirectory memberDirectory;
    private TenantRepository tenantRepository;
    private AuthAuditService auditService;
    private ReportDeliveryService service;

    @BeforeEach
    void setUp() {
        deliveryRepository = org.mockito.Mockito.mock(ReportDeliveryRepository.class);
        subscriptionRepository = org.mockito.Mockito.mock(ReportSubscriptionRepository.class);
        subscriptionService = org.mockito.Mockito.mock(ReportSubscriptionService.class);
        draftStore = org.mockito.Mockito.mock(ReportDraftStore.class);
        deliveryPort = org.mockito.Mockito.mock(ReportDeliveryPort.class);
        memberDirectory = org.mockito.Mockito.mock(TenantMemberDirectory.class);
        tenantRepository = org.mockito.Mockito.mock(TenantRepository.class);
        auditService = org.mockito.Mockito.mock(AuthAuditService.class);
        service = new ReportDeliveryService(
                deliveryRepository, subscriptionRepository, subscriptionService, draftStore,
                deliveryPort, memberDirectory, tenantRepository, auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(deliveryRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void materializesOneStableOutboxRecordPerRecipient() {
        ReportGenerationJobEntity job = org.mockito.Mockito.mock(ReportGenerationJobEntity.class);
        when(job.getId()).thenReturn(11L);
        when(job.getSubscriptionId()).thenReturn(9L);
        when(job.getTenantId()).thenReturn(7L);
        when(job.getCreatorUserId()).thenReturn(2L);
        ReportSubscriptionEntity subscription = subscription();
        when(subscription.getRecipientUserIds()).thenReturn(Set.of(5L, 3L));
        when(subscriptionRepository.findById(9L)).thenReturn(Optional.of(subscription));
        when(deliveryRepository.findByDeliveryKey(any())).thenReturn(Optional.empty());

        List<ReportDeliveryService.DeliveryView> materialized = service.materialize(job, draft(), NOW);

        assertThat(materialized).hasSize(2);
        ArgumentCaptor<ReportDeliveryEntity> captor = ArgumentCaptor.forClass(ReportDeliveryEntity.class);
        verify(deliveryRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ReportDeliveryEntity::getDeliveryKey)
                .containsExactly("report-delivery:11:email:3", "report-delivery:11:email:5");
    }

    @Test
    void sendsCompletePlainTextReportAndMarksSmtpAcceptanceSucceeded() throws Exception {
        ReportDeliveryEntity delivery = claimedDelivery();
        prepareExecution(delivery);
        when(deliveryPort.deliver(any())).thenReturn(DeliveryResult.succeeded("provider-1"));

        ReportDeliveryService.DeliveryView result = service.executeClaimed(21L, "worker-a", NOW.plusSeconds(10));

        assertThat(result.status()).isEqualTo(ReportDeliveryStatus.SUCCEEDED);
        ArgumentCaptor<DeliveryRequest> request = ArgumentCaptor.forClass(DeliveryRequest.class);
        verify(deliveryPort).deliver(request.capture());
        assertThat(request.getValue().recipientEmail()).isEqualTo("analyst@example.com");
        assertThat(request.getValue().body()).contains("Title: Daily", "Report type: daily", "# Complete report");
        assertThat(request.getValue().deliveryKey()).isEqualTo("report-delivery:11:email:3");
    }

    @Test
    void ambiguousProviderResultBecomesUnknownWithoutAutomaticRetry() throws Exception {
        ReportDeliveryEntity delivery = claimedDelivery();
        prepareExecution(delivery);
        when(deliveryPort.deliver(any())).thenReturn(DeliveryResult.unknown("SMTP_TIMEOUT_UNKNOWN"));

        ReportDeliveryService.DeliveryView result = service.executeClaimed(21L, "worker-a", NOW.plusSeconds(10));

        assertThat(result.status()).isEqualTo(ReportDeliveryStatus.UNKNOWN);
        assertThat(result.nextRetryAt()).isNull();
        assertThat(result.errorCode()).isEqualTo("SMTP_TIMEOUT_UNKNOWN");
    }

    private void prepareExecution(ReportDeliveryEntity delivery) {
        when(deliveryRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(delivery));
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(
                new TenantEntity("tenant-a", "Tenant A", true, NOW)));
        ReportSubscriptionEntity subscription = subscription();
        when(subscriptionRepository.findById(9L)).thenReturn(Optional.of(subscription));
        when(subscriptionService.evaluateExecutionEligibility(9L))
                .thenReturn(ReportSubscriptionService.ExecutionEligibility.allowed());
        when(memberDirectory.requireActivePrincipal(7L, 3L)).thenReturn(recipient());
        when(memberDirectory.requireEmail(7L, 3L)).thenReturn("analyst@example.com");
        when(draftStore.findByTenantIdAndId(7L, "draft-1")).thenReturn(Optional.of(draft()));
    }

    private ReportDeliveryEntity claimedDelivery() throws Exception {
        ReportDeliveryEntity delivery = new ReportDeliveryEntity(
                11L, 9L, 7L, 2L, "draft-1", 3L,
                "email", "report-delivery:11:email:3", NOW);
        Field id = ReportDeliveryEntity.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(delivery, 21L);
        delivery.claim("worker-a", NOW, NOW.plus(ReportDeliveryService.LEASE_DURATION));
        return delivery;
    }

    private ReportSubscriptionEntity subscription() {
        ReportSubscriptionEntity subscription = org.mockito.Mockito.mock(ReportSubscriptionEntity.class);
        when(subscription.getId()).thenReturn(9L);
        when(subscription.getTenantId()).thenReturn(7L);
        when(subscription.getChannelKey()).thenReturn("email");
        when(subscription.getEnabled()).thenReturn(true);
        when(subscription.getRecipientUserIds()).thenReturn(Set.of(3L));
        return subscription;
    }

    private ReportDraft draft() {
        return new ReportDraft(
                "draft-1", ReportType.DAILY, "Daily", "en", "# Complete report",
                NOW, "batch-1", ReportScope.global(), "deterministic", "reporting-v1", 7L);
    }

    private AuthPrincipal recipient() {
        return new AuthPrincipal(
                3L, null, null, "analyst", "Analyst", true, false,
                Set.of("VIEWER"), Set.of(PermissionKey.REPORT_READ),
                7L, "tenant-a", 30L, Set.of(4L));
    }
}
