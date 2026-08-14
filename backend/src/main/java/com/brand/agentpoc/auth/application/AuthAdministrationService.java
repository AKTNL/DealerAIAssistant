package com.brand.agentpoc.auth.application;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRoleEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRoleRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthAdministrationService {

    private static final Pattern ROLE_KEY_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");

    private final AuthUserRepository userRepository;
    private final AuthRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityInputPolicy inputPolicy;
    private final AuthSessionService sessionService;
    private final AuthAuditService auditService;
    private final Clock clock;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final TenantMembershipRoleRepository membershipRoleRepository;

    public AuthAdministrationService(
            AuthUserRepository userRepository,
            AuthRoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            IdentityInputPolicy inputPolicy,
            AuthSessionService sessionService,
            AuthAuditService auditService,
            Clock clock,
            TenantRepository tenantRepository,
            TenantMembershipRepository membershipRepository,
            TenantMembershipRoleRepository membershipRoleRepository
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.inputPolicy = inputPolicy;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.clock = clock;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.membershipRoleRepository = membershipRoleRepository;
    }

    @Transactional(readOnly = true)
    public List<UserView> listUsers(AuthPrincipal actor) {
        Long tenantId = requireActorTenantId(actor);
        return membershipRepository.findByTenantId(tenantId).stream()
                .map(membership -> UserView.from(
                        requireUser(membership.getUserId()),
                        membership,
                        roleKeys(membership)
                ))
                .toList();
    }

    @Transactional
    public UserView createUser(
            AuthPrincipal actor,
            String username,
            String displayName,
            String temporaryPassword,
            Set<String> roleKeys,
            String traceId
    ) {
        return createUser(actor, username, displayName, null, temporaryPassword, roleKeys, traceId);
    }

    @Transactional
    public UserView createUser(
            AuthPrincipal actor,
            String username,
            String displayName,
            String email,
            String temporaryPassword,
            Set<String> roleKeys,
            String traceId
    ) {
        String normalizedUsername = inputPolicy.normalizeUsername(username);
        String normalizedEmail = inputPolicy.normalizeEmail(email);
        if (!userRepository.findByUsernameIgnoreCase(normalizedUsername).isEmpty()) {
            throw new IllegalArgumentException("Username already exists.");
        }
        if (normalizedEmail != null
                && !membershipRepository.findByTenantIdAndEmailIgnoreCase(actor.tenantId(), normalizedEmail).isEmpty()) {
            throw new IllegalArgumentException("Email already exists in this tenant.");
        }
        inputPolicy.validatePassword(temporaryPassword);
        Set<AuthRoleEntity> roles = requireRoles(roleKeys);
        Instant now = Instant.now(clock);
        AuthUserEntity saved = userRepository.saveAndFlush(new AuthUserEntity(
                normalizedUsername,
                inputPolicy.normalizeDisplayName(displayName, normalizedUsername),
                passwordEncoder.encode(temporaryPassword),
                true,
                true,
                Set.of(),
                now
        ));
        TenantEntity tenant = requireActorTenant(actor);
        TenantMembershipEntity membership;
        try {
            membership = membershipRepository.saveAndFlush(new TenantMembershipEntity(
                    tenant,
                    saved.getId(),
                    normalizedEmail,
                    true,
                    now
            ));
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("Email already exists in this tenant.", exception);
        }
        roles.stream()
                .map(AuthRoleEntity::getId)
                .map(roleId -> new TenantMembershipRoleEntity(membership, roleId))
                .forEach(membershipRoleRepository::save);
        auditService.record(actor.userId(), "USER_CREATE", "USER", String.valueOf(saved.getId()),
                "SUCCESS", traceId, "temporary_password_assigned");
        return UserView.from(saved, membership, roleKeys(roles));
    }

    @Transactional
    public UserView changeEmail(
            AuthPrincipal actor,
            Long userId,
            String email,
            Long expectedVersion,
            String traceId
    ) {
        AuthUserEntity user = requireUser(userId);
        TenantMembershipEntity membership = requireMembership(actor, userId);
        requireVersion(membership.getVersion(), expectedVersion);
        String normalizedEmail = inputPolicy.normalizeEmail(email);
        if (normalizedEmail != null
                && membershipRepository.findByTenantIdAndEmailIgnoreCase(actor.tenantId(), normalizedEmail).stream()
                        .anyMatch(candidate -> !candidate.getId().equals(membership.getId()))) {
            throw new IllegalArgumentException("Email already exists in this tenant.");
        }
        membership.updateEmail(normalizedEmail, Instant.now(clock));
        TenantMembershipEntity savedMembership;
        try {
            savedMembership = membershipRepository.saveAndFlush(membership);
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("Email already exists in this tenant.", exception);
        }
        auditService.record(actor.tenantId(), actor.userId(), "USER_EMAIL_UPDATE", "USER", String.valueOf(userId),
                "SUCCESS", traceId, normalizedEmail == null ? "email_cleared" : "email_updated");
        return UserView.from(user, savedMembership, roleKeys(savedMembership));
    }

    @Transactional
    public UserView changeEnabled(
            AuthPrincipal actor,
            Long userId,
            boolean enabled,
            Long expectedVersion,
            String traceId
    ) {
        AuthUserEntity user = requireUser(userId);
        TenantMembershipEntity membership = requireMembership(actor, userId);
        requireVersion(membership.getVersion(), expectedVersion);
        if (!enabled && isEffectiveAdministrator(membership)) {
            requireAnotherTenantAdministrator(actor.tenantId(), membership.getId(), null);
        }
        membership.updateEnabled(enabled, Instant.now(clock));
        membershipRepository.saveAndFlush(membership);
        auditService.record(actor.userId(), enabled ? "USER_ENABLE" : "USER_DISABLE", "USER",
                String.valueOf(userId), "SUCCESS", traceId,
                enabled ? "tenant_membership_enabled" : "tenant_membership_disabled");
        return UserView.from(user, membership, roleKeys(membership));
    }

    @Transactional
    public UserView assignRoles(
            AuthPrincipal actor,
            Long userId,
            Set<String> roleKeys,
            Long expectedVersion,
            String traceId
    ) {
        AuthUserEntity user = requireUser(userId);
        TenantMembershipEntity membership = requireMembership(actor, userId);
        requireVersion(membership.getVersion(), expectedVersion);
        Set<AuthRoleEntity> roles = requireRoles(roleKeys);
        boolean losesAdministration = isEffectiveAdministrator(membership)
                && roles.stream().noneMatch(role -> role.getPermissions().contains(PermissionKey.USER_MANAGE));
        if (losesAdministration && Boolean.TRUE.equals(membership.getEnabled())) {
            requireAnotherTenantAdministrator(actor.tenantId(), membership.getId(), null);
        }
        membershipRoleRepository.deleteByMembershipId(membership.getId());
        roles.stream()
                .map(AuthRoleEntity::getId)
                .map(roleId -> new TenantMembershipRoleEntity(membership, roleId))
                .forEach(membershipRoleRepository::save);
        membership.touch(Instant.now(clock));
        membershipRepository.saveAndFlush(membership);
        auditService.record(actor.userId(), "USER_ROLES_UPDATE", "USER", String.valueOf(userId),
                "SUCCESS", traceId, "tenant_membership_roles_updated");
        return UserView.from(user, membership, roleKeys(roles));
    }

    @Transactional
    public UserView resetPassword(
            AuthPrincipal actor,
            Long userId,
            String temporaryPassword,
            Long expectedVersion,
            String traceId
    ) {
        inputPolicy.validatePassword(temporaryPassword);
        requireExclusiveMembership(actor, userId);
        AuthUserEntity user = requireUser(userId);
        requireVersion(user.getVersion(), expectedVersion);
        user.changePassword(passwordEncoder.encode(temporaryPassword), true, Instant.now(clock));
        userRepository.saveAndFlush(user);
        sessionService.revokeAllForUser(userId, "password_reset");
        auditService.record(actor.userId(), "PASSWORD_RESET", "USER", String.valueOf(userId),
                "SUCCESS", traceId, "temporary_password_assigned_sessions_revoked");
        TenantMembershipEntity membership = requireMembership(actor, userId);
        return UserView.from(user, membership, roleKeys(membership));
    }

    @Transactional(readOnly = true)
    public List<RoleView> listRoles() {
        return roleRepository.findAll().stream().map(RoleView::from).toList();
    }

    @Transactional
    public RoleView createRole(
            AuthPrincipal actor,
            String roleKey,
            String displayName,
            Set<PermissionKey> permissions,
            String traceId
    ) {
        requirePlatformRoleAdministration();
        String normalizedKey = normalizeRoleKey(roleKey);
        if (!roleRepository.findByRoleKeyIgnoreCase(normalizedKey).isEmpty()) {
            throw new IllegalArgumentException("Role key already exists.");
        }
        Set<PermissionKey> normalizedPermissions = requirePermissions(permissions);
        String normalizedDisplayName = inputPolicy.normalizeDisplayName(displayName, normalizedKey);
        AuthRoleEntity saved = roleRepository.saveAndFlush(new AuthRoleEntity(
                normalizedKey,
                normalizedDisplayName,
                false,
                normalizedPermissions,
                Instant.now(clock)
        ));
        auditService.record(actor.userId(), "ROLE_CREATE", "ROLE", String.valueOf(saved.getId()),
                "SUCCESS", traceId, "custom_role_created");
        return RoleView.from(saved);
    }

    @Transactional
    public RoleView updateRolePermissions(
            AuthPrincipal actor,
            Long roleId,
            Set<PermissionKey> permissions,
            Long expectedVersion,
            String traceId
    ) {
        requirePlatformRoleAdministration();
        AuthRoleEntity role = requireRole(roleId);
        requireVersion(role.getVersion(), expectedVersion);
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw new IllegalArgumentException("Built-in role permissions are protected.");
        }
        Set<PermissionKey> normalizedPermissions = requirePermissions(permissions);
        boolean removesAdministration = role.getPermissions().contains(PermissionKey.USER_MANAGE)
                && !normalizedPermissions.contains(PermissionKey.USER_MANAGE);
        if (removesAdministration) {
            userRepository.lockAllForAdministrationUpdate();
            requireAnotherEffectiveAdministrator(null, roleId, normalizedPermissions);
        }
        role.replacePermissions(normalizedPermissions, Instant.now(clock));
        roleRepository.saveAndFlush(role);
        userRepository.findAll().stream()
                .filter(user -> user.getRoles().stream().anyMatch(assigned -> assigned.getId().equals(roleId)))
                .forEach(user -> sessionService.revokeAllForUser(user.getId(), "role_permissions_changed"));
        auditService.record(actor.userId(), "ROLE_PERMISSIONS_UPDATE", "ROLE", String.valueOf(roleId),
                "SUCCESS", traceId, "affected_sessions_revoked");
        return RoleView.from(role);
    }

    private TenantEntity requireActorTenant(AuthPrincipal actor) {
        Long tenantId = requireActorTenantId(actor);
        return tenantRepository.findById(tenantId)
                .filter(tenant -> Boolean.TRUE.equals(tenant.getEnabled()))
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "Tenant access denied."));
    }

    private Long requireActorTenantId(AuthPrincipal actor) {
        if (actor == null || !actor.hasTenantContext()) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant access denied.");
        }
        return actor.tenantId();
    }

    private TenantMembershipEntity requireMembership(AuthPrincipal actor, Long userId) {
        return membershipRepository.findByTenantIdAndUserId(requireActorTenantId(actor), userId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown user."));
    }

    private void requireExclusiveMembership(AuthPrincipal actor, Long userId) {
        requireMembership(actor, userId);
        if (membershipRepository.findByUserId(userId).size() != 1) {
            throw new IllegalStateException("Shared identity requires platform administration.");
        }
    }

    private Set<String> roleKeys(TenantMembershipEntity membership) {
        Set<Long> roleIds = membershipRoleRepository.findByMembershipId(membership.getId()).stream()
                .map(TenantMembershipRoleEntity::getRoleId)
                .collect(java.util.stream.Collectors.toSet());
        return roleIds.isEmpty()
                ? Set.of()
                : roleRepository.findAllById(roleIds).stream()
                        .map(AuthRoleEntity::getRoleKey)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Set<String> roleKeys(Set<AuthRoleEntity> roles) {
        return roles.stream()
                .map(AuthRoleEntity::getRoleKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Set<AuthRoleEntity> requireRoles(Set<String> roleKeys) {
        if (roleKeys == null || roleKeys.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required.");
        }
        Set<AuthRoleEntity> roles = new LinkedHashSet<>();
        for (String roleKey : roleKeys) {
            String normalized = normalizeRoleKey(roleKey);
            List<AuthRoleEntity> matches = roleRepository.findByRoleKeyIgnoreCase(normalized);
            if (matches.size() != 1) {
                throw new IllegalArgumentException("Unknown role key.");
            }
            roles.add(matches.getFirst());
        }
        return Set.copyOf(roles);
    }

    private Set<PermissionKey> requirePermissions(Set<PermissionKey> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new IllegalArgumentException("At least one known permission is required.");
        }
        return Set.copyOf(permissions);
    }

    private void requirePlatformRoleAdministration() {
        throw new org.springframework.security.access.AccessDeniedException(
                "Global role templates require platform administration.");
    }

    private void requireAnotherEffectiveAdministrator(
            Long excludedUserId,
            Long changedRoleId,
            Set<PermissionKey> replacementPermissions
    ) {
        long remaining = userRepository.findAll().stream()
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .filter(user -> excludedUserId == null || !excludedUserId.equals(user.getId()))
                .filter(user -> effectivePermissions(user, changedRoleId, replacementPermissions)
                        .contains(PermissionKey.USER_MANAGE))
                .count();
        if (remaining == 0) {
            throw new IllegalStateException("The last effective administrator cannot be removed or disabled.");
        }
    }

    private Set<PermissionKey> effectivePermissions(
            AuthUserEntity user,
            Long changedRoleId,
            Set<PermissionKey> replacementPermissions
    ) {
        Set<PermissionKey> resolved = EnumSet.noneOf(PermissionKey.class);
        for (AuthRoleEntity role : user.getRoles()) {
            if (changedRoleId != null && changedRoleId.equals(role.getId())) {
                resolved.addAll(replacementPermissions);
            } else {
                resolved.addAll(role.getPermissions());
            }
        }
        return resolved;
    }

    private boolean isEffectiveAdministrator(AuthUserEntity user) {
        return Boolean.TRUE.equals(user.getEnabled())
                && user.getRoles().stream()
                        .flatMap(role -> role.getPermissions().stream())
                        .anyMatch(PermissionKey.USER_MANAGE::equals);
    }

    private boolean isEffectiveAdministrator(TenantMembershipEntity membership) {
        if (!Boolean.TRUE.equals(membership.getEnabled())) {
            return false;
        }
        Set<Long> roleIds = membershipRoleRepository.findByMembershipId(membership.getId()).stream()
                .map(TenantMembershipRoleEntity::getRoleId)
                .collect(java.util.stream.Collectors.toSet());
        return roleRepository.findAllById(roleIds).stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(PermissionKey.USER_MANAGE::equals);
    }

    private void requireAnotherTenantAdministrator(
            Long tenantId,
            Long excludedMembershipId,
            Set<Long> replacementRoleIds
    ) {
        long remaining = membershipRepository.findByTenantIdAndEnabledTrue(tenantId).stream()
                .filter(membership -> !membership.getId().equals(excludedMembershipId))
                .filter(this::isEffectiveAdministrator)
                .count();
        if (remaining == 0 && (replacementRoleIds == null || replacementRoleIds.isEmpty())) {
            throw new IllegalStateException("The last effective administrator cannot be removed or disabled.");
        }
    }

    private AuthUserEntity requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user."));
    }

    private void requireVersion(Long currentVersion, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(currentVersion)) {
            throw new IllegalStateException("The resource changed since it was loaded.");
        }
    }

    private AuthRoleEntity requireRole(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role."));
    }

    private String normalizeRoleKey(String roleKey) {
        String normalized = roleKey == null ? "" : roleKey.trim().toUpperCase(Locale.ROOT);
        if (!ROLE_KEY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Role key does not meet the role policy.");
        }
        return normalized;
    }

    public record UserView(
            Long id,
            String username,
            String displayName,
            String email,
            boolean enabled,
            boolean mustChangePassword,
            Set<String> roles,
            Long version
    ) {
        private static UserView from(
                AuthUserEntity user,
                TenantMembershipEntity membership,
                Set<String> tenantRoles
        ) {
            return new UserView(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    membership.getEmail(),
                    Boolean.TRUE.equals(user.getEnabled()) && Boolean.TRUE.equals(membership.getEnabled()),
                    Boolean.TRUE.equals(user.getMustChangePassword()),
                    tenantRoles,
                    membership.getVersion()
            );
        }
    }

    public record RoleView(
            Long id,
            String roleKey,
            String displayName,
            boolean builtIn,
            Set<PermissionKey> permissions,
            Long version
    ) {
        private static RoleView from(AuthRoleEntity role) {
            return new RoleView(
                    role.getId(),
                    role.getRoleKey(),
                    role.getDisplayName(),
                    Boolean.TRUE.equals(role.getBuiltIn()),
                    role.getPermissions(),
                    role.getVersion()
            );
        }
    }
}
