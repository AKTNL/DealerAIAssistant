package com.brand.agentpoc.tenant.application;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRoleRepository;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TenantAuthorizationService {

    public static final String TENANT_HEADER = "X-Tenant-Key";

    private final TenantMembershipRepository membershipRepository;
    private final TenantMembershipRoleRepository membershipRoleRepository;
    private final AuthRoleRepository roleRepository;

    public TenantAuthorizationService(
            TenantMembershipRepository membershipRepository,
            TenantMembershipRoleRepository membershipRoleRepository,
            AuthRoleRepository roleRepository
    ) {
        this.membershipRepository = membershipRepository;
        this.membershipRoleRepository = membershipRoleRepository;
        this.roleRepository = roleRepository;
    }

    public AuthPrincipal resolve(AuthPrincipal identity, String requestedTenantKey) {
        return resolveUserContext(identity, requestedTenantKey).principal()
                .orElseThrow(TenantAuthorizationService::denied);
    }

    public TenantUserContext resolveUserContext(AuthPrincipal identity, String requestedTenantKey) {
        if (identity == null || !identity.enabled()) {
            throw denied();
        }
        List<TenantMembershipEntity> memberships = membershipRepository
                .findByUserIdAndEnabledTrue(identity.userId()).stream()
                .filter(membership -> Boolean.TRUE.equals(membership.getTenant().getEnabled()))
                .sorted(java.util.Comparator.comparing(membership -> membership.getTenant().getId()))
                .toList();
        List<TenantMembershipView> membershipViews = memberships.stream()
                .map(membership -> new TenantMembershipView(
                        membership.getTenant().getId(),
                        membership.getTenant().getTenantKey(),
                        membership.getTenant().getDisplayName()
                ))
                .toList();
        TenantMembershipEntity selected = selectOptionalMembership(memberships, requestedTenantKey);
        if (selected == null) {
            return new TenantUserContext(java.util.Optional.empty(), membershipViews);
        }

        Set<Long> roleIds = membershipRoleRepository.findByMembershipId(selected.getId()).stream()
                .map(assignment -> assignment.getRoleId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<AuthRoleEntity> roles = roleIds.isEmpty() ? List.of() : roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
            throw denied();
        }
        Set<PermissionKey> permissions = roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermissionKey.class)));
        Set<String> roleKeys = roles.stream()
                .map(AuthRoleEntity::getRoleKey)
                .collect(Collectors.toUnmodifiableSet());
        AuthPrincipal principal = identity.withTenant(
                selected.getTenant().getId(),
                selected.getTenant().getTenantKey(),
                selected.getId(),
                roleIds,
                roleKeys,
                permissions
        );
        return new TenantUserContext(java.util.Optional.of(principal), membershipViews);
    }

    public AuthPrincipal requireCurrentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)
                || !principal.hasTenantContext()) {
            throw denied();
        }
        return principal;
    }

    private TenantMembershipEntity selectOptionalMembership(
            List<TenantMembershipEntity> memberships,
            String requestedTenantKey
    ) {
        String normalized = normalize(requestedTenantKey);
        if (normalized == null) {
            return memberships.size() == 1 ? memberships.getFirst() : null;
        }
        return memberships.stream()
                .filter(membership -> membership.getTenant().getTenantKey().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(TenantAuthorizationService::denied);
    }

    private String normalize(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            return null;
        }
        String normalized = tenantKey.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 64 || !normalized.matches("[a-z0-9][a-z0-9_-]*")) {
            throw denied();
        }
        return normalized;
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Tenant access denied.");
    }

    public record TenantUserContext(
            java.util.Optional<AuthPrincipal> principal,
            List<TenantMembershipView> memberships
    ) {
    }

    public record TenantMembershipView(Long id, String key, String displayName) {
    }
}
