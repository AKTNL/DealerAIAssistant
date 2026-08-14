package com.brand.agentpoc.tenant.application;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRoleRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TenantMemberDirectory {

    private static final int MAX_RECIPIENTS = 50;

    private final TenantMembershipRepository membershipRepository;
    private final TenantMembershipRoleRepository membershipRoleRepository;
    private final AuthRoleRepository roleRepository;
    private final AuthUserRepository userRepository;

    public TenantMemberDirectory(
            TenantMembershipRepository membershipRepository,
            TenantMembershipRoleRepository membershipRoleRepository,
            AuthRoleRepository roleRepository,
            AuthUserRepository userRepository
    ) {
        this.membershipRepository = membershipRepository;
        this.membershipRoleRepository = membershipRoleRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    public List<TenantRecipient> listReportRecipients(Long tenantId) {
        if (tenantId == null) {
            throw denied();
        }
        return activeMemberships(tenantId).stream()
                .flatMap(membership -> findActivePrincipal(membership).stream()
                        .filter(principal -> principal.hasPermission(PermissionKey.REPORT_READ))
                        .map(principal -> new TenantRecipient(
                                principal.userId(), principal.username(), principal.displayName(),
                                membership.getEmail() != null && !membership.getEmail().isBlank())))
                .sorted(Comparator.comparing(TenantRecipient::displayName)
                        .thenComparing(TenantRecipient::userId))
                .toList();
    }

    public List<TenantRecipient> requireReportRecipients(Long tenantId, Collection<Long> recipientUserIds) {
        Set<Long> normalized = recipientUserIds == null
                ? Set.of()
                : recipientUserIds.stream()
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty() || normalized.size() > MAX_RECIPIENTS
                || recipientUserIds == null || normalized.size() != recipientUserIds.size()) {
            throw new IllegalArgumentException("Recipients must contain 1 to 50 unique tenant user IDs.");
        }
        Map<Long, TenantRecipient> eligible = listReportRecipients(tenantId).stream()
                .collect(Collectors.toMap(TenantRecipient::userId, Function.identity()));
        if (!eligible.keySet().containsAll(normalized)) {
            throw new AccessDeniedException("One or more recipients are outside the active tenant or report scope.");
        }
        return normalized.stream().map(eligible::get).toList();
    }

    public AuthPrincipal requireActivePrincipal(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            throw denied();
        }
        TenantMembershipEntity membership = membershipRepository.findByTenantIdAndUserId(tenantId, userId).stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .filter(item -> Boolean.TRUE.equals(item.getTenant().getEnabled()))
                .findFirst()
                .orElseThrow(TenantMemberDirectory::denied);
        return findActivePrincipal(membership).orElseThrow(TenantMemberDirectory::denied);
    }

    public String requireEmail(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            throw denied();
        }
        TenantMembershipEntity membership = membershipRepository.findByTenantIdAndUserId(tenantId, userId).stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .filter(item -> Boolean.TRUE.equals(item.getTenant().getEnabled()))
                .findFirst()
                .orElseThrow(TenantMemberDirectory::denied);
        if (membership.getEmail() == null || membership.getEmail().isBlank()) {
            throw new IllegalStateException("Recipient email is not configured.");
        }
        return membership.getEmail();
    }

    private List<TenantMembershipEntity> activeMemberships(Long tenantId) {
        return membershipRepository.findByTenantIdAndEnabledTrue(tenantId).stream()
                .filter(membership -> Boolean.TRUE.equals(membership.getTenant().getEnabled()))
                .toList();
    }

    private AuthPrincipal toActivePrincipal(TenantMembershipEntity membership) {
        AuthUserEntity user = userRepository.findById(membership.getUserId())
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .orElseThrow(TenantMemberDirectory::denied);
        Set<Long> roleIds = membershipRoleRepository.findByMembershipId(membership.getId()).stream()
                .map(assignment -> assignment.getRoleId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<AuthRoleEntity> roles = roleIds.isEmpty() ? List.of() : roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
            throw denied();
        }
        Set<PermissionKey> permissions = roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermissionKey.class)));
        if (Boolean.TRUE.equals(user.getMustChangePassword())) {
            permissions.clear();
        }
        Set<String> roleKeys = roles.stream()
                .map(AuthRoleEntity::getRoleKey)
                .collect(Collectors.toUnmodifiableSet());
        return new AuthPrincipal(
                user.getId(),
                null,
                null,
                user.getUsername(),
                user.getDisplayName(),
                true,
                Boolean.TRUE.equals(user.getMustChangePassword()),
                roleKeys,
                permissions,
                membership.getTenant().getId(),
                membership.getTenant().getTenantKey(),
                membership.getId(),
                roleIds
        );
    }

    private Optional<AuthPrincipal> findActivePrincipal(TenantMembershipEntity membership) {
        try {
            return Optional.of(toActivePrincipal(membership));
        } catch (AccessDeniedException exception) {
            return Optional.empty();
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Active tenant membership is required.");
    }

    public record TenantRecipient(Long userId, String username, String displayName, boolean emailConfigured) {
        public TenantRecipient(Long userId, String username, String displayName) {
            this(userId, username, displayName, false);
        }
    }
}
