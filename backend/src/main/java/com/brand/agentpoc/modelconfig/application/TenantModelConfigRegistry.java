package com.brand.agentpoc.modelconfig.application;

import com.brand.agentpoc.dto.request.ModelConfigRequest;
import com.brand.agentpoc.modelconfig.infrastructure.persistence.TenantModelConfigEntity;
import com.brand.agentpoc.modelconfig.infrastructure.persistence.TenantModelConfigRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantModelConfigRegistry {

    private final TenantModelConfigRepository repository;
    private final TenantRepository tenantRepository;
    private final ModelConfigSecretProvider secretProvider;
    private final Clock clock;

    public TenantModelConfigRegistry(
            TenantModelConfigRepository repository,
            TenantRepository tenantRepository,
            ModelConfigSecretProvider secretProvider,
            Clock clock
    ) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.secretProvider = secretProvider;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedModelConfig> resolve(Long tenantId) {
        requireActiveTenant(tenantId);
        return findOne(tenantId).map(entity -> new ResolvedModelConfig(
                new ModelConfigRequest(
                        entity.getBaseUrl(),
                        secretProvider.reveal(tenantId, entity.getSecretCiphertext()),
                        entity.getModelName()
                ),
                splitHosts(entity.getAllowedHosts())
        ));
    }

    @Transactional(readOnly = true)
    public Optional<ModelConfigView> view(Long tenantId) {
        requireActiveTenant(tenantId);
        return findOne(tenantId).map(ModelConfigView::from);
    }

    @Transactional
    public ModelConfigView save(Long tenantId, ModelConfigRequest request, List<String> allowedHosts) {
        requireActiveTenant(tenantId);
        if (request == null) {
            throw new IllegalArgumentException("Model settings are required.");
        }
        String hosts = normalizeHosts(allowedHosts);
        Instant now = Instant.now(clock);
        TenantModelConfigEntity entity = findOne(tenantId).orElse(null);
        String protectedSecret;
        int secretVersion;
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            protectedSecret = secretProvider.protect(tenantId, request.apiKey());
            secretVersion = secretProvider.version();
        } else if (entity != null) {
            protectedSecret = entity.getSecretCiphertext();
            secretVersion = entity.getSecretVersion();
        } else {
            throw new IllegalArgumentException("Model API key is required.");
        }
        if (entity == null) {
            entity = new TenantModelConfigEntity(
                    tenantId,
                    request.baseUrl(),
                    request.model(),
                    hosts,
                    protectedSecret,
                    secretVersion,
                    now
            );
        } else {
            entity.update(
                    request.baseUrl(),
                    request.model(),
                    hosts,
                    protectedSecret,
                    secretVersion,
                    now
            );
        }
        return ModelConfigView.from(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long tenantId) {
        requireActiveTenant(tenantId);
        findOne(tenantId).ifPresent(repository::delete);
    }

    private Optional<TenantModelConfigEntity> findOne(Long tenantId) {
        List<TenantModelConfigEntity> matches = repository.findByTenantId(tenantId);
        if (matches.size() > 1) {
            throw new IllegalStateException("Tenant model configuration is not unique.");
        }
        return matches.stream().findFirst();
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

    private String normalizeHosts(List<String> allowedHosts) {
        List<String> normalized = allowedHosts == null ? List.of() : allowedHosts.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .map(String::toLowerCase)
                .distinct()
                .sorted()
                .toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one allowed model host is required.");
        }
        return String.join(",", normalized);
    }

    public record ModelConfigView(
            String baseUrl,
            String model,
            List<String> allowedHosts,
            boolean apiKeyConfigured,
            Long version,
            Instant updatedAt
    ) {
        private static ModelConfigView from(TenantModelConfigEntity entity) {
            return new ModelConfigView(
                    entity.getBaseUrl(),
                    entity.getModelName(),
                    splitHosts(entity.getAllowedHosts()),
                    true,
                    entity.getVersion(),
                    entity.getUpdatedAt()
            );
        }
    }

    private static List<String> splitHosts(String hosts) {
        return Arrays.stream(hosts.split(","))
                .filter(value -> !value.isBlank())
                .toList();
    }

    public record ResolvedModelConfig(ModelConfigRequest settings, List<String> allowedHosts) {
    }
}
