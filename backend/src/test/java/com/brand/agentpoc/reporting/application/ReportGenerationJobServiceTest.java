package com.brand.agentpoc.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.organization.application.OrganizationAuthorizationService;
import com.brand.agentpoc.organization.domain.OrganizationAuthorizationContext;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import com.brand.agentpoc.reporting.domain.ReportGenerationJobStatus;
import com.brand.agentpoc.reporting.domain.ReportScope;
import com.brand.agentpoc.reporting.domain.ReportType;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportGenerationJobEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportGenerationJobRepository;
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
import org.springframework.dao.TransientDataAccessResourceException;

class ReportGenerationJobServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T01:30:00Z");

    private ReportGenerationJobRepository jobRepository;
    private ReportSubscriptionRepository subscriptionRepository;
    private ReportSubscriptionService subscriptionService;
    private ReportService reportService;
    private ReportDeliveryService deliveryService;
    private TenantMemberDirectory memberDirectory;
    private TenantRepository tenantRepository;
    private OrganizationAuthorizationService organizationAuthorizationService;
    private AuthAuditService auditService;
    private ReportGenerationJobService service;

    @BeforeEach
    void setUp() {
        jobRepository = org.mockito.Mockito.mock(ReportGenerationJobRepository.class);
        subscriptionRepository = org.mockito.Mockito.mock(ReportSubscriptionRepository.class);
        subscriptionService = org.mockito.Mockito.mock(ReportSubscriptionService.class);
        reportService = org.mockito.Mockito.mock(ReportService.class);
        deliveryService = org.mockito.Mockito.mock(ReportDeliveryService.class);
        memberDirectory = org.mockito.Mockito.mock(TenantMemberDirectory.class);
        tenantRepository = org.mockito.Mockito.mock(TenantRepository.class);
        organizationAuthorizationService = org.mockito.Mockito.mock(OrganizationAuthorizationService.class);
        auditService = org.mockito.Mockito.mock(AuthAuditService.class);
        service = new ReportGenerationJobService(
                jobRepository, subscriptionRepository, subscriptionService, reportService,
                deliveryService, memberDirectory, tenantRepository, organizationAuthorizationService, auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(jobRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(
                new TenantEntity("tenant-a", "Tenant A", true, NOW)));
    }

    @Test
    void materializesDueWindowAndAdvancesSubscriptionCursor() throws Exception {
        ReportSubscriptionEntity subscription = subscription();
        setId(subscription, 9L);
        when(subscriptionRepository
                .findTop50ByEnabledTrueAndDeletedAtIsNullAndNextRunAtLessThanEqualOrderByNextRunAtAscIdAsc(NOW))
                .thenReturn(List.of(subscription));
        when(subscriptionRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(subscription));
        when(jobRepository.findByIdempotencyKey("9:2026-08-14T01:00:00Z")).thenReturn(Optional.empty());

        List<ReportGenerationJobService.JobView> created = service.materializeDueSubscriptions(NOW);

        assertThat(created).singleElement().satisfies(view -> {
            assertThat(view.status()).isEqualTo(ReportGenerationJobStatus.READY);
            assertThat(view.idempotencyKey()).isEqualTo("9:2026-08-14T01:00:00Z");
        });
        assertThat(subscription.getNextRunAt()).isEqualTo(Instant.parse("2026-08-15T01:00:00Z"));
        verify(auditService).record(
                eq(7L), eq(2L), eq("REPORT_JOB_CREATED"), eq("REPORT_GENERATION_JOB"),
                any(), eq("SUCCESS"), any(), eq("job_materialized"));
    }

    @Test
    void marksWindowMissedAfterGraceInsteadOfGenerating() throws Exception {
        ReportSubscriptionEntity subscription = subscription();
        setId(subscription, 9L);
        Instant now = Instant.parse("2026-08-14T03:01:00Z");
        when(subscriptionRepository
                .findTop50ByEnabledTrueAndDeletedAtIsNullAndNextRunAtLessThanEqualOrderByNextRunAtAscIdAsc(now))
                .thenReturn(List.of(subscription));
        when(subscriptionRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(subscription));
        when(jobRepository.findByIdempotencyKey("9:2026-08-14T01:00:00Z")).thenReturn(Optional.empty());

        ReportGenerationJobService.JobView skipped = service.materializeDueSubscriptions(now).getFirst();

        assertThat(skipped.status()).isEqualTo(ReportGenerationJobStatus.SKIPPED);
        assertThat(skipped.errorCode()).isEqualTo("MISSED_WINDOW");
        assertThat(subscription.getNextRunAt()).isEqualTo(Instant.parse("2026-08-15T01:00:00Z"));
        verify(auditService).record(
                eq(7L), eq(2L), eq("REPORT_JOB_SKIPPED"), eq("REPORT_GENERATION_JOB"),
                any(), eq("SUCCESS"), any(), eq("MISSED_WINDOW"));
    }

    @Test
    void claimsAndExecutesAfterReloadingAuthorizationContext() throws Exception {
        ReportGenerationJobEntity job = job();
        setId(job, 11L);
        when(jobRepository.findTop50ByStatusOrderByScheduledAtAscIdAsc(ReportGenerationJobStatus.READY))
                .thenReturn(List.of(job));
        when(jobRepository.findTop50ByStatusAndNextRetryAtLessThanEqualOrderByScheduledAtAscIdAsc(
                ReportGenerationJobStatus.RETRY_WAIT, NOW)).thenReturn(List.of());
        when(jobRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(job));
        when(subscriptionService.evaluateExecutionEligibility(9L))
                .thenReturn(ReportSubscriptionService.ExecutionEligibility.allowed());
        AuthPrincipal creator = creator();
        when(memberDirectory.requireActivePrincipal(7L, 2L)).thenReturn(creator);
        OrganizationDataScope scope = OrganizationDataScope.tenantScope(
                7L, "tenant-a", Set.of(10L), Set.of(10L), Set.of("D001"), true);
        when(organizationAuthorizationService.resolve(creator))
                .thenReturn(new OrganizationAuthorizationContext(creator, scope));
        ReportDraft draft = new ReportDraft(
                "draft-1", ReportType.DAILY, "Daily", "en", "# Daily",
                NOW, "batch-1", new ReportScope("ORGANIZATION", "10"),
                "deterministic", "reporting-v1", 7L);
        when(reportService.generate(any(), eq(scope))).thenReturn(draft);

        ReportGenerationJobService.JobView claimed = service.claimNext("worker-a", NOW).orElseThrow();
        ReportGenerationJobService.JobView result = service.executeClaimed(
                11L, "worker-a", NOW.plusSeconds(30));

        assertThat(claimed.status()).isEqualTo(ReportGenerationJobStatus.RUNNING);
        assertThat(result.status()).isEqualTo(ReportGenerationJobStatus.SUCCEEDED);
        assertThat(result.reportDraftId()).isEqualTo("draft-1");
        verify(reportService).generate(any(), eq(scope));
        verify(deliveryService).materialize(eq(job), eq(draft), eq(NOW.plusSeconds(30)));
        verify(auditService).record(
                eq(7L), eq(2L), eq("REPORT_JOB_SUCCEEDED"), eq("REPORT_GENERATION_JOB"),
                eq("11"), eq("SUCCESS"), any(), eq("report_generated"));
    }

    @Test
    void retriesTransientFailureWithFixedBackoff() throws Exception {
        ReportGenerationJobEntity job = job();
        setId(job, 11L);
        job.claim("worker-a", NOW, NOW.plus(ReportGenerationJobService.LEASE_DURATION));
        when(jobRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(job));
        when(subscriptionService.evaluateExecutionEligibility(9L))
                .thenReturn(ReportSubscriptionService.ExecutionEligibility.allowed());
        AuthPrincipal creator = creator();
        when(memberDirectory.requireActivePrincipal(7L, 2L)).thenReturn(creator);
        OrganizationDataScope scope = OrganizationDataScope.tenantScope(
                7L, "tenant-a", Set.of(10L), Set.of(10L), Set.of("D001"), true);
        when(organizationAuthorizationService.resolve(creator))
                .thenReturn(new OrganizationAuthorizationContext(creator, scope));
        doThrow(new TransientDataAccessResourceException("database timeout"))
                .when(reportService).generate(any(), eq(scope));

        ReportGenerationJobService.JobView result = service.executeClaimed(
                11L, "worker-a", NOW.plusSeconds(30));

        assertThat(result.status()).isEqualTo(ReportGenerationJobStatus.RETRY_WAIT);
        assertThat(result.errorCode()).isEqualTo("TRANSIENT_FAILURE");
        assertThat(result.nextRetryAt()).isEqualTo(NOW.plusSeconds(330));
        assertThat(result.attempt()).isEqualTo(1);
    }

    @Test
    void usesTwoHourBackoffForThirdRetry() throws Exception {
        ReportGenerationJobEntity job = job();
        setId(job, 11L);
        job.claim("worker-old-1", NOW.minusSeconds(900), NOW.minusSeconds(600));
        job.markRetry("TRANSIENT_FAILURE", NOW.minusSeconds(600), NOW.minusSeconds(899));
        job.claim("worker-old-2", NOW.minusSeconds(600), NOW.minusSeconds(300));
        job.markRetry("TRANSIENT_FAILURE", NOW.minusSeconds(300), NOW.minusSeconds(599));
        job.claim("worker-a", NOW, NOW.plus(ReportGenerationJobService.LEASE_DURATION));
        when(jobRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(job));
        when(subscriptionService.evaluateExecutionEligibility(9L))
                .thenReturn(ReportSubscriptionService.ExecutionEligibility.allowed());
        AuthPrincipal creator = creator();
        when(memberDirectory.requireActivePrincipal(7L, 2L)).thenReturn(creator);
        OrganizationDataScope scope = OrganizationDataScope.tenantScope(
                7L, "tenant-a", Set.of(10L), Set.of(10L), Set.of("D001"), true);
        when(organizationAuthorizationService.resolve(creator))
                .thenReturn(new OrganizationAuthorizationContext(creator, scope));
        doThrow(new TransientDataAccessResourceException("database timeout"))
                .when(reportService).generate(any(), eq(scope));

        ReportGenerationJobService.JobView result = service.executeClaimed(
                11L, "worker-a", NOW.plusSeconds(30));

        assertThat(result.status()).isEqualTo(ReportGenerationJobStatus.RETRY_WAIT);
        assertThat(result.nextRetryAt()).isEqualTo(NOW.plusSeconds(30 + 7200));
        assertThat(result.attempt()).isEqualTo(3);
    }

    @Test
    void rejectsExecutionWhenSubscriptionEligibilityWasRevoked() throws Exception {
        ReportGenerationJobEntity job = job();
        setId(job, 11L);
        job.claim("worker-a", NOW, NOW.plus(ReportGenerationJobService.LEASE_DURATION));
        when(jobRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(job));
        when(subscriptionService.evaluateExecutionEligibility(9L))
                .thenReturn(ReportSubscriptionService.ExecutionEligibility.denied("organization_scope_revoked"));

        ReportGenerationJobService.JobView result = service.executeClaimed(
                11L, "worker-a", NOW.plusSeconds(30));

        assertThat(result.status()).isEqualTo(ReportGenerationJobStatus.PERMANENT_FAILURE);
        assertThat(result.errorCode()).isEqualTo("ORGANIZATION_SCOPE_REVOKED");
        org.mockito.Mockito.verifyNoInteractions(reportService);
    }

    @Test
    void cancelsClaimedJobWhenTenantWasDisabled() throws Exception {
        ReportGenerationJobEntity job = job();
        setId(job, 11L);
        job.claim("worker-a", NOW, NOW.plus(ReportGenerationJobService.LEASE_DURATION));
        when(jobRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(job));
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(
                new TenantEntity("tenant-a", "Tenant A", false, NOW)));

        ReportGenerationJobService.JobView result = service.executeClaimed(
                11L, "worker-a", NOW.plusSeconds(30));

        assertThat(result.status()).isEqualTo(ReportGenerationJobStatus.CANCELLED);
        assertThat(result.errorCode()).isEqualTo("TENANT_DISABLED");
        org.mockito.Mockito.verifyNoInteractions(reportService);
    }

    private ReportSubscriptionEntity subscription() {
        return new ReportSubscriptionEntity(
                7L, 2L, ReportType.DAILY, new ReportScope("ORGANIZATION", "10"),
                "en", "", com.brand.agentpoc.reporting.domain.ReportSubscriptionSchedule.parse(
                        "DAILY", "09:00", "Asia/Shanghai", null, null),
                "email", Set.of(2L), true, Instant.parse("2026-08-14T01:00:00Z"),
                "SKIP", 60, "config", Instant.parse("2026-08-13T00:00:00Z"));
    }

    private ReportGenerationJobEntity job() {
        return new ReportGenerationJobEntity(
                9L, 7L, 2L, Instant.parse("2026-08-14T01:00:00Z"),
                "9:2026-08-14T01:00:00Z", "daily", new ReportScope("ORGANIZATION", "10"),
                "en", "", ReportGenerationJobStatus.READY, "trace-job", NOW);
    }

    private AuthPrincipal creator() {
        return new AuthPrincipal(
                2L, null, null, "creator", "Creator", true, false,
                Set.of("ANALYST"), Set.of(PermissionKey.REPORT_GENERATE, PermissionKey.REPORT_READ),
                7L, "tenant-a", 8L, Set.of(11L));
    }

    private void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
