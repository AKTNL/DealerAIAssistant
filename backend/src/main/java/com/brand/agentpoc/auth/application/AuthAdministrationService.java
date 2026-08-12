package com.brand.agentpoc.auth.application;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
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

    public AuthAdministrationService(
            AuthUserRepository userRepository,
            AuthRoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            IdentityInputPolicy inputPolicy,
            AuthSessionService sessionService,
            AuthAuditService auditService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.inputPolicy = inputPolicy;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserView> listUsers() {
        return userRepository.findAll().stream().map(UserView::from).toList();
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
        String normalizedUsername = inputPolicy.normalizeUsername(username);
        if (!userRepository.findByUsernameIgnoreCase(normalizedUsername).isEmpty()) {
            throw new IllegalArgumentException("Username already exists.");
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
                roles,
                now
        ));
        auditService.record(actor.userId(), "USER_CREATE", "USER", String.valueOf(saved.getId()),
                "SUCCESS", traceId, "temporary_password_assigned");
        return UserView.from(saved);
    }

    @Transactional
    public UserView changeEnabled(
            AuthPrincipal actor,
            Long userId,
            boolean enabled,
            Long expectedVersion,
            String traceId
    ) {
        if (!enabled) {
            userRepository.lockAllForAdministrationUpdate();
        }
        AuthUserEntity user = requireUser(userId);
        requireVersion(user.getVersion(), expectedVersion);
        if (!enabled && isEffectiveAdministrator(user)) {
            requireAnotherEffectiveAdministrator(user.getId(), null, null);
        }
        user.changeEnabled(enabled, Instant.now(clock));
        userRepository.saveAndFlush(user);
        if (!enabled) {
            sessionService.revokeAllForUser(userId, "account_disabled");
        }
        auditService.record(actor.userId(), enabled ? "USER_ENABLE" : "USER_DISABLE", "USER",
                String.valueOf(userId), "SUCCESS", traceId, enabled ? "account_enabled" : "sessions_revoked");
        return UserView.from(user);
    }

    @Transactional
    public UserView assignRoles(
            AuthPrincipal actor,
            Long userId,
            Set<String> roleKeys,
            Long expectedVersion,
            String traceId
    ) {
        userRepository.lockAllForAdministrationUpdate();
        AuthUserEntity user = requireUser(userId);
        requireVersion(user.getVersion(), expectedVersion);
        Set<AuthRoleEntity> roles = requireRoles(roleKeys);
        boolean losesAdministration = isEffectiveAdministrator(user)
                && roles.stream().noneMatch(role -> role.getPermissions().contains(PermissionKey.USER_MANAGE));
        if (losesAdministration && Boolean.TRUE.equals(user.getEnabled())) {
            requireAnotherEffectiveAdministrator(userId, null, null);
        }
        user.replaceRoles(roles, Instant.now(clock));
        userRepository.saveAndFlush(user);
        sessionService.revokeAllForUser(userId, "roles_changed");
        auditService.record(actor.userId(), "USER_ROLES_UPDATE", "USER", String.valueOf(userId),
                "SUCCESS", traceId, "sessions_revoked");
        return UserView.from(user);
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
        AuthUserEntity user = requireUser(userId);
        requireVersion(user.getVersion(), expectedVersion);
        user.changePassword(passwordEncoder.encode(temporaryPassword), true, Instant.now(clock));
        userRepository.saveAndFlush(user);
        sessionService.revokeAllForUser(userId, "password_reset");
        auditService.record(actor.userId(), "PASSWORD_RESET", "USER", String.valueOf(userId),
                "SUCCESS", traceId, "temporary_password_assigned_sessions_revoked");
        return UserView.from(user);
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
            boolean enabled,
            boolean mustChangePassword,
            Set<String> roles,
            Long version
    ) {
        private static UserView from(AuthUserEntity user) {
            return new UserView(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    Boolean.TRUE.equals(user.getEnabled()),
                    Boolean.TRUE.equals(user.getMustChangePassword()),
                    user.getRoles().stream().map(AuthRoleEntity::getRoleKey).collect(java.util.stream.Collectors.toSet()),
                    user.getVersion()
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
