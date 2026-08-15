package com.brand.agentpoc.modelusage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.modelusage.application.ModelUsageGovernanceService.PlatformSummaryView;
import com.brand.agentpoc.modelusage.application.ModelUsageGovernanceService.UsageSummaryView;
import com.brand.agentpoc.modelusage.domain.ModelTokenState;
import com.brand.agentpoc.modelusage.domain.ModelUsageScenario;
import com.brand.agentpoc.modelusage.domain.ModelUsageStatus;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelBudgetPolicyRepository;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelPriceVersionRepository;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelUsageEventEntity;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelUsageEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ModelUsageGovernanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T02:00:00Z");
    private ModelUsageEventRepository eventRepository;
    private AuthAuditService auditService;
    private ModelUsageGovernanceService service;

    @BeforeEach
    void setUp() {
        eventRepository = mock(ModelUsageEventRepository.class);
        ModelPriceVersionRepository priceRepository = mock(ModelPriceVersionRepository.class);
        ModelBudgetPolicyRepository budgetRepository = mock(ModelBudgetPolicyRepository.class);
        auditService = mock(AuthAuditService.class);
        service = new ModelUsageGovernanceService(eventRepository, priceRepository, budgetRepository,
                auditService, Clock.fixed(NOW, ZoneOffset.UTC));
        when(budgetRepository.findByTenantId(any())).thenReturn(List.of());
    }

    @Test
    void derivesTenantScopeOnlyFromTheAuthenticatedPrincipal() {
        when(eventRepository
                .findByTenantIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(
                        eq(7L), any(), any()))
                .thenReturn(List.of(event(7L, "tenant-7-call")));

        UsageSummaryView result = service.summary(actor(7L, PermissionKey.MODEL_USAGE_READ), null, null);

        assertThat(result.total().calls()).isEqualTo(1);
        assertThat(result.recentEvents()).extracting(event -> event.tenantId()).containsOnly(7L);
        verify(eventRepository)
                .findByTenantIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(
                        eq(7L), any(), any());
        verify(eventRepository, never())
                .findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(any(), any());
    }

    @Test
    void rejectsPlatformReadsOutsideTheStableDefaultTenant() {
        AuthPrincipal actor = actor(7L, PermissionKey.MODEL_USAGE_PLATFORM_READ);

        assertThatThrownBy(() -> service.platformSummary(actor, null, null, "trace-platform"))
                .isInstanceOf(AccessDeniedException.class);

        verify(eventRepository, never())
                .findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(any(), any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aggregatesAndAuditsAuthorizedPlatformReads() {
        when(eventRepository
                .findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(any(), any()))
                .thenReturn(List.of(event(7L, "call-7"), event(8L, "call-8")));

        PlatformSummaryView result = service.platformSummary(
                actor(1L, PermissionKey.MODEL_USAGE_PLATFORM_READ), null, null, "trace-platform");

        assertThat(result.total().calls()).isEqualTo(2);
        assertThat(result.tenants()).extracting(item -> item.key()).containsExactly("7", "8");
        verify(auditService).record(1L, 2L, "PLATFORM_MODEL_USAGE_READ", "MODEL_USAGE",
                "all-tenants", "SUCCESS", "trace-platform", "platform_summary_read");
    }

    private AuthPrincipal actor(Long tenantId, PermissionKey... permissions) {
        return new AuthPrincipal(
                2L, 3L, "family", "admin", "Admin", true, false,
                Set.of("ADMIN"), Set.of(permissions), tenantId, "tenant-" + tenantId, 9L, Set.of(10L));
    }

    private ModelUsageEventEntity event(Long tenantId, String callKey) {
        return new ModelUsageEventEntity(
                callKey, tenantId, 2L, "openai-compatible", "gpt-test",
                ModelUsageScenario.CHAT, ModelUsageStatus.SUCCESS, ModelTokenState.KNOWN,
                10L, 5L, 15L, 20L, "trace-1", false, null, null, NOW.minusSeconds(60));
    }
}
