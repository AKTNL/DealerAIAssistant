package com.brand.agentpoc.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.brand.agentpoc.reporting.application.ReportSubscriptionService.DefinitionInput;
import com.brand.agentpoc.reporting.domain.ReportScope;
import com.brand.agentpoc.reporting.domain.ReportSubscriptionSchedule;
import com.brand.agentpoc.reporting.domain.ReportType;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportSubscriptionEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.ReportSubscriptionRepository;
import com.brand.agentpoc.tenant.application.TenantMemberDirectory;
import com.brand.agentpoc.tenant.application.TenantMemberDirectory.TenantRecipient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportSubscriptionServiceTest {

    private ReportSubscriptionRepository repository;
    private TenantMemberDirectory memberDirectory;
    private OrganizationAuthorizationService organizationAuthorizationService;
    private AuthAuditService auditService;
    private ReportSubscriptionService service;
    private AuthPrincipal actor;
    private OrganizationDataScope dataScope;

    @BeforeEach
    void setUp() {
        repository = mock(ReportSubscriptionRepository.class);
        memberDirectory = mock(TenantMemberDirectory.class);
        organizationAuthorizationService = mock(OrganizationAuthorizationService.class);
        auditService = mock(AuthAuditService.class);
        service = new ReportSubscriptionService(
                repository,
                memberDirectory,
                organizationAuthorizationService,
                auditService,
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC)
        );
        actor = principal(EnumSet.of(PermissionKey.REPORT_READ, PermissionKey.REPORT_GENERATE));
        dataScope = OrganizationDataScope.tenantScope(
                7L, "tenant-a", Set.of(10L), Set.of(10L), Set.of("D001"), false);
        when(memberDirectory.requireReportRecipients(eq(7L), any()))
                .thenReturn(java.util.List.of(new TenantRecipient(2L, "analyst", "Analyst", true)));
        when(memberDirectory.requireActivePrincipal(7L, 2L)).thenReturn(actor);
        when(organizationAuthorizationService.resolve(actor))
                .thenReturn(new OrganizationAuthorizationContext(actor, dataScope));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsAuditedTenantScopedPresetWithDeterministicNextRun() {
        var created = service.create(actor, dataScope, definition(), true, "trace-1");

        assertThat(created.scope()).isEqualTo(new ReportScope("ORGANIZATION", "10"));
        assertThat(created.scheduleKind()).isEqualTo("DAILY");
        assertThat(created.nextRunAt()).isEqualTo(Instant.parse("2026-08-14T01:00:00Z"));
        assertThat(created.misfirePolicy()).isEqualTo("SKIP");
        assertThat(created.misfireGraceMinutes()).isEqualTo(60);
        assertThat(created.executionEligible()).isTrue();
        verify(auditService).record(
                eq(7L), eq(2L), eq("REPORT_SUBSCRIPTION_CREATE"),
                eq("REPORT_SUBSCRIPTION"), any(), eq("SUCCESS"), eq("trace-1"), eq("definition_created"));
    }

    @Test
    void rejectsDuplicateConfigurationBeforePersistence() {
        when(repository.existsByTenantIdAndCreatorUserIdAndActiveConfigurationKey(
                eq(7L), eq(2L), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(actor, dataScope, definition(), true, "trace-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identical");
    }

    @Test
    void executionEligibilityRechecksCurrentReportPermission() {
        ReportSubscriptionEntity entity = entity();
        AuthPrincipal revoked = principal(EnumSet.of(PermissionKey.REPORT_READ));
        when(repository.findById(9L)).thenReturn(Optional.of(entity));
        when(memberDirectory.requireActivePrincipal(7L, 2L)).thenReturn(revoked);

        var eligibility = service.evaluateExecutionEligibility(9L);

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reason()).isEqualTo("report_permission_revoked");
    }

    @Test
    void listsOwnedSubscriptionsWithoutCurrentOrganizationDataAccess() {
        OrganizationDataScope noDataScope = OrganizationDataScope.tenantScope(
                7L, "tenant-a", Set.of(), Set.of(), Set.of(), false);
        when(repository.findByTenantIdAndCreatorUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(7L, 2L))
                .thenReturn(List.of());

        assertThat(service.list(actor, noDataScope)).isEmpty();
        assertThatThrownBy(() -> service.create(actor, noDataScope, definition(), true, "trace-1"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void executionEligibilityClassifiesMissingOrganizationCoverage() {
        ReportSubscriptionEntity entity = entity();
        OrganizationDataScope noDataScope = OrganizationDataScope.tenantScope(
                7L, "tenant-a", Set.of(), Set.of(), Set.of(), false);
        when(repository.findById(9L)).thenReturn(Optional.of(entity));
        when(organizationAuthorizationService.resolve(actor))
                .thenReturn(new OrganizationAuthorizationContext(actor, noDataScope));

        var eligibility = service.evaluateExecutionEligibility(9L);

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reason()).isEqualTo("organization_scope_revoked");
    }

    @Test
    void staleMutationVersionIsRejectedBeforeUpdate() {
        ReportSubscriptionEntity entity = entity();
        when(repository.findByTenantIdAndIdAndCreatorUserIdAndDeletedAtIsNull(7L, 9L, 2L))
                .thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.update(
                actor, dataScope, 9L, 3L, definition(), "trace-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed");
    }

    @Test
    void mutationRequiresAnOptimisticVersion() {
        ReportSubscriptionEntity entity = entity();
        when(repository.findByTenantIdAndIdAndCreatorUserIdAndDeletedAtIsNull(7L, 9L, 2L))
                .thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.update(
                actor, dataScope, 9L, null, definition(), "trace-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    private DefinitionInput definition() {
        return new DefinitionInput(
                "daily", "zh", "", "DAILY", "09:00", "Asia/Shanghai",
                null, null, "email", Set.of(2L));
    }

    private ReportSubscriptionEntity entity() {
        ReportSubscriptionSchedule schedule = ReportSubscriptionSchedule.parse(
                "DAILY", "09:00", "Asia/Shanghai", null, null);
        return new ReportSubscriptionEntity(
                7L, 2L, ReportType.DAILY, new ReportScope("ORGANIZATION", "10"),
                "zh", "", schedule, "email", Set.of(2L), true,
                Instant.parse("2026-08-14T01:00:00Z"), "SKIP", 60,
                "configuration-key", Instant.parse("2026-08-13T00:00:00Z"));
    }

    private AuthPrincipal principal(Set<PermissionKey> permissions) {
        return new AuthPrincipal(
                2L, 3L, "family", "analyst", "Analyst", true, false,
                Set.of("ANALYST"), permissions, 7L, "tenant-a", 8L, Set.of(11L));
    }
}
