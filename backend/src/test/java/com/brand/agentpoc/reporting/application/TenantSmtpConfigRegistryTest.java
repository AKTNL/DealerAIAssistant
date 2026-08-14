package com.brand.agentpoc.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.auth.application.IdentityInputPolicy;
import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.reporting.application.TenantSmtpConfigRegistry.SmtpConfigInput;
import com.brand.agentpoc.reporting.domain.SmtpSecurityMode;
import com.brand.agentpoc.reporting.infrastructure.persistence.TenantSmtpConfigEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.TenantSmtpConfigRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TenantSmtpConfigRegistryTest {

    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");

    private TenantSmtpConfigRepository repository;
    private NotificationSecretProvider secretProvider;
    private AuthAuditService auditService;
    private TenantSmtpConfigRegistry registry;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(TenantSmtpConfigRepository.class);
        TenantRepository tenantRepository = org.mockito.Mockito.mock(TenantRepository.class);
        secretProvider = org.mockito.Mockito.mock(NotificationSecretProvider.class);
        auditService = org.mockito.Mockito.mock(AuthAuditService.class);
        AppProperties properties = new AppProperties();
        properties.getNotification().setSmtpAllowedHosts(List.of("smtp.example.com"));
        registry = new TenantSmtpConfigRegistry(
                repository, tenantRepository, secretProvider, new IdentityInputPolicy(),
                properties, auditService, Clock.fixed(NOW, ZoneOffset.UTC));
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(
                new TenantEntity("tenant-a", "Tenant A", true, NOW)));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(secretProvider.protect(7L, "smtp-password")).thenReturn("encrypted-password");
        when(secretProvider.version()).thenReturn(1);
    }

    @Test
    void encryptsNewPasswordAndWritesSafeAudit() {
        TenantSmtpConfigRegistry.SmtpConfigView saved = registry.save(
                7L, 2L, input("STARTTLS", 587, "smtp-password", null), "trace-1");

        assertThat(saved.host()).isEqualTo("smtp.example.com");
        assertThat(saved.passwordConfigured()).isTrue();
        verify(secretProvider).protect(7L, "smtp-password");
        verify(auditService).record(7L, 2L, "SMTP_CONFIG_SAVE", "TENANT_SMTP_CONFIG",
                "null", "SUCCESS", "trace-1", "smtp_config_saved");
    }

    @Test
    void rejectsTlsModeAndPortMismatch() {
        assertThatThrownBy(() -> registry.save(
                7L, 2L, input("STARTTLS", 465, "smtp-password", null), "trace-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STARTTLS requires port 587");
    }

    @Test
    void blankPasswordPreservesExistingCiphertextOnVersionedUpdate() throws Exception {
        TenantSmtpConfigEntity existing = new TenantSmtpConfigEntity(
                7L, "smtp.example.com", 587, SmtpSecurityMode.STARTTLS, "smtp-user",
                "existing-ciphertext", 1, "reports@example.com", null, true, NOW);
        setField(existing, "id", 10L);
        setField(existing, "version", 4L);
        when(repository.findByTenantId(7L)).thenReturn(List.of(existing));

        registry.save(7L, 2L, input("STARTTLS", 587, "", 4L), "trace-2");

        assertThat(existing.getPasswordCiphertext()).isEqualTo("existing-ciphertext");
        verify(secretProvider, never()).protect(any(), any());
    }

    private SmtpConfigInput input(String mode, int port, String password, Long version) {
        return new SmtpConfigInput(
                "smtp.example.com", port, mode, "smtp-user", password,
                "reports@example.com", "Dealer AI", true, version);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
