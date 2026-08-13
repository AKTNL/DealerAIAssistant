package com.brand.agentpoc.modelconfig.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.dto.request.ModelConfigRequest;
import com.brand.agentpoc.modelconfig.infrastructure.persistence.TenantModelConfigEntity;
import com.brand.agentpoc.modelconfig.infrastructure.persistence.TenantModelConfigRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class TenantModelConfigRegistryTest {

    private final TenantModelConfigRepository configs = mock(TenantModelConfigRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final ModelConfigSecretProvider secrets = mock(ModelConfigSecretProvider.class);
    private final TenantModelConfigRegistry registry = new TenantModelConfigRegistry(
            configs,
            tenants,
            secrets,
            Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void returnsOnlyTheSelectedTenantViewWithoutSecretMaterial() {
        enableTenant(1L, true);
        TenantModelConfigEntity tenantOne = entity(1L, "cipher-a");
        TenantModelConfigEntity tenantTwo = entity(2L, "cipher-b");
        when(configs.findByTenantId(1L)).thenReturn(List.of(tenantOne));
        when(configs.findByTenantId(2L)).thenReturn(List.of(tenantTwo));

        TenantModelConfigRegistry.ModelConfigView view = registry.view(1L).orElseThrow();

        assertThat(view.baseUrl()).isEqualTo("https://api.example.com/v1");
        assertThat(view.apiKeyConfigured()).isTrue();
        assertThat(view.toString()).doesNotContain("cipher-a", "cipher-b");
        verify(configs).findByTenantId(1L);
        verify(configs, never()).findByTenantId(2L);
        verify(secrets, never()).reveal(any(), any());
    }

    @Test
    void refusesReadsAndWritesForDisabledTenants() {
        enableTenant(9L, false);

        assertThatThrownBy(() -> registry.view(9L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> registry.resolve(9L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> registry.save(
                9L,
                new ModelConfigRequest("https://api.example.com/v1", "sk-test", "gpt-test"),
                List.of("api.example.com")
        )).isInstanceOf(AccessDeniedException.class);
        verify(configs, never()).findByTenantId(any());
        verify(configs, never()).saveAndFlush(any());
    }

    @Test
    void keepsExistingCiphertextWhenUpdatingWithoutAReplacementKey() {
        enableTenant(1L, true);
        TenantModelConfigEntity existing = entity(1L, "cipher-existing");
        when(configs.findByTenantId(1L)).thenReturn(List.of(existing));
        when(configs.saveAndFlush(existing)).thenReturn(existing);

        registry.save(
                1L,
                new ModelConfigRequest("https://new.example.com/v1", "", "gpt-new"),
                List.of("new.example.com")
        );

        assertThat(existing.getSecretCiphertext()).isEqualTo("cipher-existing");
        verify(secrets, never()).protect(any(), any());
    }

    private void enableTenant(Long tenantId, boolean enabled) {
        TenantEntity tenant = mock(TenantEntity.class);
        when(tenant.getEnabled()).thenReturn(enabled);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
    }

    private TenantModelConfigEntity entity(Long tenantId, String ciphertext) {
        return new TenantModelConfigEntity(
                tenantId,
                "https://api.example.com/v1",
                "gpt-test",
                "api.example.com",
                ciphertext,
                1,
                Instant.parse("2026-08-13T00:00:00Z")
        );
    }
}
