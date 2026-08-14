package com.brand.agentpoc.reporting.application;

import com.brand.agentpoc.auth.application.IdentityInputPolicy;
import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.reporting.domain.SmtpSecurityMode;
import com.brand.agentpoc.reporting.infrastructure.persistence.TenantSmtpConfigEntity;
import com.brand.agentpoc.reporting.infrastructure.persistence.TenantSmtpConfigRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantSmtpConfigRegistry {

    private static final Pattern HOST_PATTERN = Pattern.compile("[a-z0-9](?:[a-z0-9.-]{0,253}[a-z0-9])?");
    private static final int STARTTLS_PORT = 587;
    private static final int SMTPS_PORT = 465;
    private static final int MAX_DISPLAY_NAME_LENGTH = 128;
    private static final int MAX_USERNAME_LENGTH = 254;
    private static final int MAX_PASSWORD_LENGTH = 1024;

    private final TenantSmtpConfigRepository repository;
    private final TenantRepository tenantRepository;
    private final NotificationSecretProvider secretProvider;
    private final IdentityInputPolicy inputPolicy;
    private final AppProperties appProperties;
    private final AuthAuditService auditService;
    private final Clock clock;

    public TenantSmtpConfigRegistry(
            TenantSmtpConfigRepository repository,
            TenantRepository tenantRepository,
            NotificationSecretProvider secretProvider,
            IdentityInputPolicy inputPolicy,
            AppProperties appProperties,
            AuthAuditService auditService,
            Clock clock
    ) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.secretProvider = secretProvider;
        this.inputPolicy = inputPolicy;
        this.appProperties = appProperties;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<SmtpConfigView> view(Long tenantId) {
        requireActiveTenant(tenantId);
        return findOne(tenantId).map(SmtpConfigView::from);
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedSmtpConfig> resolve(Long tenantId) {
        requireActiveTenant(tenantId);
        return findOne(tenantId).map(entity -> new ResolvedSmtpConfig(
                entity.getHost(),
                entity.getPort(),
                entity.getSecurityMode(),
                entity.getUsername(),
                secretProvider.reveal(tenantId, entity.getPasswordCiphertext()),
                entity.getFromAddress(),
                entity.getFromDisplayName(),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getVersion()
        ));
    }

    @Transactional
    public SmtpConfigView save(Long tenantId, Long actorUserId, SmtpConfigInput input, String traceId) {
        requireActiveTenant(tenantId);
        if (input == null) {
            throw new IllegalArgumentException("SMTP settings are required.");
        }
        String host = normalizeHost(input.host());
        int port = normalizePort(input.port());
        SmtpSecurityMode securityMode = SmtpSecurityMode.parse(input.securityMode());
        requireModePort(securityMode, port);
        String username = requiredHeaderValue(input.username(), "SMTP username");
        if (username.length() > MAX_USERNAME_LENGTH) {
            throw new IllegalArgumentException("SMTP username is too long.");
        }
        String fromAddress = inputPolicy.normalizeEmail(input.fromAddress());
        if (fromAddress == null) {
            throw new IllegalArgumentException("SMTP from address is required.");
        }
        String fromDisplayName = normalizeDisplayName(input.fromDisplayName());
        Instant now = Instant.now(clock);
        TenantSmtpConfigEntity existing = findOne(tenantId).orElse(null);
        if (existing != null) {
            if (input.version() == null) {
                throw new IllegalArgumentException("version is required.");
            }
            if (!input.version().equals(existing.getVersion())) {
                throw new IllegalStateException("The resource changed since it was loaded.");
            }
        }
        String passwordCiphertext;
        int secretVersion;
        if (input.password() != null && !input.password().isBlank()) {
            if (input.password().length() > MAX_PASSWORD_LENGTH) {
                throw new IllegalArgumentException("SMTP password is too long.");
            }
            passwordCiphertext = secretProvider.protect(tenantId, input.password());
            secretVersion = secretProvider.version();
        } else if (existing != null) {
            passwordCiphertext = existing.getPasswordCiphertext();
            secretVersion = existing.getSecretVersion();
        } else {
            throw new IllegalArgumentException("SMTP password is required.");
        }
        if (existing == null) {
            existing = new TenantSmtpConfigEntity(
                    tenantId, host, port, securityMode, username, passwordCiphertext, secretVersion,
                    fromAddress, fromDisplayName, input.enabled(), now);
        } else {
            existing.update(
                    host, port, securityMode, username, passwordCiphertext, secretVersion,
                    fromAddress, fromDisplayName, input.enabled(), now);
        }
        TenantSmtpConfigEntity saved = repository.saveAndFlush(existing);
        auditService.record(tenantId, actorUserId, "SMTP_CONFIG_SAVE", "TENANT_SMTP_CONFIG",
                String.valueOf(saved.getId()), "SUCCESS", traceId, "smtp_config_saved");
        return SmtpConfigView.from(saved);
    }

    @Transactional
    public void delete(Long tenantId, Long actorUserId, Long version, String traceId) {
        requireActiveTenant(tenantId);
        TenantSmtpConfigEntity existing = findOne(tenantId)
                .orElseThrow(() -> new java.util.NoSuchElementException("SMTP configuration was not found."));
        if (version == null) {
            throw new IllegalArgumentException("version is required.");
        }
        if (!version.equals(existing.getVersion())) {
            throw new IllegalStateException("The resource changed since it was loaded.");
        }
        repository.delete(existing);
        repository.flush();
        auditService.record(tenantId, actorUserId, "SMTP_CONFIG_DELETE", "TENANT_SMTP_CONFIG",
                String.valueOf(existing.getId()), "SUCCESS", traceId, "smtp_config_deleted");
    }

    private Optional<TenantSmtpConfigEntity> findOne(Long tenantId) {
        List<TenantSmtpConfigEntity> matches = repository.findByTenantId(tenantId);
        if (matches.size() > 1) {
            throw new IllegalStateException("Tenant SMTP configuration is not unique.");
        }
        return matches.stream().findFirst();
    }

    private String normalizeHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(java.util.Locale.ROOT);
        if (!HOST_PATTERN.matcher(normalized).matches()
                || appProperties.getNotification().getSmtpAllowedHosts().stream()
                        .noneMatch(normalized::equals)) {
            throw new IllegalArgumentException("SMTP host is not allowed.");
        }
        return normalized;
    }

    private int normalizePort(Integer port) {
        if (port == null || (port != STARTTLS_PORT && port != SMTPS_PORT)) {
            throw new IllegalArgumentException("SMTP port must be 465 or 587.");
        }
        return port;
    }

    private void requireModePort(SmtpSecurityMode mode, int port) {
        if ((mode == SmtpSecurityMode.STARTTLS && port != STARTTLS_PORT)
                || (mode == SmtpSecurityMode.SMTPS && port != SMTPS_PORT)) {
            throw new IllegalArgumentException("STARTTLS requires port 587 and SMTPS requires port 465.");
        }
    }

    private String requiredHeaderValue(String value, String fieldName) {
        if (value == null || value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(fieldName + " is invalid.");
        }
        return value.trim();
    }

    private String normalizeDisplayName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_DISPLAY_NAME_LENGTH
                || normalized.indexOf('\r') >= 0
                || normalized.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("SMTP from display name is invalid.");
        }
        return normalized;
    }

    private void requireActiveTenant(Long tenantId) {
        if (tenantId == null) {
            throw new AccessDeniedException("Tenant context is required.");
        }
        boolean enabled = tenantRepository.findById(tenantId)
                .map(tenant -> Boolean.TRUE.equals(tenant.getEnabled()))
                .orElse(false);
        if (!enabled) {
            throw new AccessDeniedException("Tenant access denied.");
        }
    }

    public record SmtpConfigInput(
            String host,
            Integer port,
            String securityMode,
            String username,
            String password,
            String fromAddress,
            String fromDisplayName,
            boolean enabled,
            Long version
    ) {
    }

    public record SmtpConfigView(
            String host,
            int port,
            SmtpSecurityMode securityMode,
            String username,
            String fromAddress,
            String fromDisplayName,
            boolean enabled,
            boolean passwordConfigured,
            Long version,
            Instant updatedAt
    ) {
        private static SmtpConfigView from(TenantSmtpConfigEntity entity) {
            return new SmtpConfigView(
                    entity.getHost(), entity.getPort(), entity.getSecurityMode(), entity.getUsername(),
                    entity.getFromAddress(), entity.getFromDisplayName(), Boolean.TRUE.equals(entity.getEnabled()),
                    true, entity.getVersion(), entity.getUpdatedAt());
        }
    }

    public record ResolvedSmtpConfig(
            String host,
            int port,
            SmtpSecurityMode securityMode,
            String username,
            String password,
            String fromAddress,
            String fromDisplayName,
            boolean enabled,
            Long version
    ) {
    }
}
