package com.brand.agentpoc.auth.application;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthSessionEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthSessionRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import com.brand.agentpoc.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthSessionService {

    private static final String DUMMY_PASSWORD_HASH =
            "{bcrypt}$2a$10$dXJ3SW6G7P50lGmMkkmwe.2eIuWljGfEXqQ72JzY7Yvj6w/p17QeK";
    private static final int TOKEN_BYTES = 32;

    private final AuthUserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityInputPolicy inputPolicy;
    private final AuthAuditService auditService;
    private final AppProperties appProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthSessionService(
            AuthUserRepository userRepository,
            AuthSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            IdentityInputPolicy inputPolicy,
            AuthAuditService auditService,
            AppProperties appProperties,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.inputPolicy = inputPolicy;
        this.auditService = auditService;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    @Transactional
    public IssuedSession login(String username, String password, String traceId) {
        String normalized;
        try {
            normalized = inputPolicy.normalizeUsername(username);
        } catch (IllegalArgumentException exception) {
            passwordEncoder.matches(password == null ? "" : password, DUMMY_PASSWORD_HASH);
            auditService.record(null, "LOGIN", "USER", null, "FAILURE", traceId, "invalid_credentials");
            throw invalidCredentials();
        }

        List<AuthUserEntity> matches = userRepository.findByUsernameIgnoreCase(normalized);
        AuthUserEntity user = matches.size() == 1 ? matches.getFirst() : null;
        String storedHash = user == null ? DUMMY_PASSWORD_HASH : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(password == null ? "" : password, storedHash);
        if (user == null || !passwordMatches || !Boolean.TRUE.equals(user.getEnabled())) {
            auditService.record(null, "LOGIN", "USER", normalized, "FAILURE", traceId, "invalid_credentials");
            throw invalidCredentials();
        }

        IssuedSession issued = issue(user, UUID.randomUUID().toString().replace("-", ""));
        auditService.record(user.getId(), "LOGIN", "SESSION", String.valueOf(issued.principal().sessionId()),
                "SUCCESS", traceId, "session_issued");
        return issued;
    }

    @Transactional(readOnly = true)
    public Optional<AuthPrincipal> authenticateAccessToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        List<AuthSessionEntity> matches = sessionRepository.findByAccessTokenHash(digest(rawToken));
        if (matches.size() != 1) {
            return Optional.empty();
        }
        AuthSessionEntity session = matches.getFirst();
        Instant now = Instant.now(clock);
        if (session.getRevokedAt() != null
                || session.getRotatedAt() != null
                || !now.isBefore(session.getAccessExpiresAt())
                || !Boolean.TRUE.equals(session.getUser().getEnabled())) {
            return Optional.empty();
        }
        return Optional.of(toPrincipal(session));
    }

    @Transactional(noRollbackFor = RefreshReplayException.class)
    public IssuedSession refresh(String rawRefreshToken, String traceId) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw invalidCredentials();
        }
        List<AuthSessionEntity> matches = sessionRepository.findRefreshTokenForUpdate(digest(rawRefreshToken));
        if (matches.size() != 1) {
            throw invalidCredentials();
        }

        AuthSessionEntity current = matches.getFirst();
        Instant now = Instant.now(clock);
        if (current.getRotatedAt() != null) {
            revokeFamily(current.getFamilyKey(), "refresh_replay", now);
            auditService.record(current.getUser().getId(), "REFRESH_REPLAY", "SESSION_FAMILY",
                    current.getFamilyKey(), "FAILURE", traceId, "family_revoked");
            throw new RefreshReplayException();
        }
        if (current.getRevokedAt() != null
                || !now.isBefore(current.getRefreshExpiresAt())
                || !Boolean.TRUE.equals(current.getUser().getEnabled())) {
            throw invalidCredentials();
        }

        current.markRotated(now);
        sessionRepository.save(current);
        IssuedSession issued = issue(current.getUser(), current.getFamilyKey());
        auditService.record(current.getUser().getId(), "REFRESH", "SESSION",
                String.valueOf(issued.principal().sessionId()), "SUCCESS", traceId, "token_rotated");
        return issued;
    }

    @Transactional
    public void logout(AuthPrincipal principal, String rawRefreshToken, String traceId) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            List<AuthSessionEntity> matches = sessionRepository.findRefreshTokenForUpdate(digest(rawRefreshToken));
            if (matches.size() == 1) {
                revokeLogoutFamily(matches.getFirst(), traceId);
                return;
            }
        }
        if (principal != null) {
            sessionRepository.findById(principal.sessionId())
                    .ifPresent(session -> revokeLogoutFamily(session, traceId));
        }
    }

    @Transactional
    public void logoutAll(AuthPrincipal principal, String traceId) {
        revokeAllForUser(principal.userId(), "logout_all");
        auditService.record(principal.userId(), "LOGOUT_ALL", "USER", String.valueOf(principal.userId()),
                "SUCCESS", traceId, "all_sessions_revoked");
    }

    @Transactional
    public void changePassword(
            AuthPrincipal principal,
            String currentPassword,
            String newPassword,
            String traceId
    ) {
        inputPolicy.validatePassword(newPassword);
        AuthUserEntity user = requireUser(principal.userId());
        if (!passwordEncoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
            auditService.record(principal.userId(), "PASSWORD_CHANGE", "USER", String.valueOf(principal.userId()),
                    "FAILURE", traceId, "invalid_current_password");
            throw invalidCredentials();
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must differ from the current password.");
        }
        user.changePassword(passwordEncoder.encode(newPassword), false, Instant.now(clock));
        userRepository.save(user);
        revokeAllForUser(user.getId(), "password_changed");
        auditService.record(user.getId(), "PASSWORD_CHANGE", "USER", String.valueOf(user.getId()),
                "SUCCESS", traceId, "all_sessions_revoked");
    }

    @Transactional
    public void revokeAllForUser(Long userId, String reason) {
        Instant now = Instant.now(clock);
        sessionRepository.findByUserId(userId).forEach(session -> session.revoke(now, reason));
    }

    private IssuedSession issue(AuthUserEntity user, String familyKey) {
        Instant issuedAt = Instant.now(clock);
        String accessToken = randomToken();
        String refreshToken = randomToken();
        AuthSessionEntity saved = sessionRepository.save(new AuthSessionEntity(
                familyKey,
                user,
                digest(accessToken),
                digest(refreshToken),
                issuedAt,
                issuedAt.plus(appProperties.getAuth().getAccessTokenTtl()),
                issuedAt.plus(appProperties.getAuth().getRefreshTokenTtl())
        ));
        return new IssuedSession(
                accessToken,
                saved.getAccessExpiresAt(),
                refreshToken,
                saved.getRefreshExpiresAt(),
                toPrincipal(saved)
        );
    }

    private AuthPrincipal toPrincipal(AuthSessionEntity session) {
        AuthUserEntity user = session.getUser();
        Set<PermissionKey> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermissionKey.class)));
        Set<String> roles = user.getRoles().stream()
                .map(AuthRoleEntity::getRoleKey)
                .collect(Collectors.toUnmodifiableSet());
        return new AuthPrincipal(
                user.getId(),
                session.getId(),
                session.getFamilyKey(),
                user.getUsername(),
                user.getDisplayName(),
                Boolean.TRUE.equals(user.getEnabled()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                roles,
                permissions
        );
    }

    private AuthUserEntity requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists."));
    }

    private void revokeFamily(String familyKey, String reason, Instant now) {
        sessionRepository.findByFamilyKey(familyKey).forEach(session -> session.revoke(now, reason));
    }

    private void revokeLogoutFamily(AuthSessionEntity session, String traceId) {
        revokeFamily(session.getFamilyKey(), "logout", Instant.now(clock));
        auditService.record(session.getUser().getId(), "LOGOUT", "SESSION_FAMILY", session.getFamilyKey(),
                "SUCCESS", traceId, "family_revoked");
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String digest(String rawToken) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private AuthenticationException invalidCredentials() {
        return new BadCredentialsException("Invalid username or password.");
    }

    public record IssuedSession(
            String accessToken,
            Instant accessExpiresAt,
            String refreshToken,
            Instant refreshExpiresAt,
            AuthPrincipal principal
    ) {
    }

    public static class RefreshReplayException extends BadCredentialsException {
        private static final long serialVersionUID = 1L;

        public RefreshReplayException() {
            super("Refresh token replay detected.");
        }
    }
}
