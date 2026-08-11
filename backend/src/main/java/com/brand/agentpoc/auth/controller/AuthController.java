package com.brand.agentpoc.auth.controller;

import com.brand.agentpoc.auth.application.AuthSessionService;
import com.brand.agentpoc.auth.application.AuthSessionService.IssuedSession;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.dto.response.ApiResult;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.brand.agentpoc.service.AuthRateLimitService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String REFRESH_COOKIE = "agentpoc_refresh";
    private static final String LOGIN_FAILURE_MESSAGE = "Invalid username or password.";

    private final AuthSessionService sessionService;
    private final AuthRateLimitService rateLimitService;
    private final AppProperties appProperties;

    public AuthController(
            AuthSessionService sessionService,
            AuthRateLimitService rateLimitService,
            AppProperties appProperties
    ) {
        this.sessionService = sessionService;
        this.rateLimitService = rateLimitService;
        this.appProperties = appProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResult<SessionResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientKey = loginClientKey(request.username(), servletRequest.getRemoteAddr());
        if (rateLimitService.isLimited(clientKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimitService.retryAfterSeconds(clientKey)))
                    .body(ApiResult.error(429, LOGIN_FAILURE_MESSAGE));
        }

        try {
            IssuedSession issued = sessionService.login(
                    request.username(),
                    request.password(),
                    AuthRequestTrace.resolve(servletRequest)
            );
            rateLimitService.recordSuccess(clientKey);
            return withRefreshCookie(issued, HttpStatus.OK);
        } catch (AuthenticationException exception) {
            rateLimitService.recordFailure(clientKey);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResult.error(401, LOGIN_FAILURE_MESSAGE));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResult<SessionResponse>> refresh(HttpServletRequest request) {
        if (!isTrustedOrigin(request.getHeader(HttpHeaders.ORIGIN))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .header(HttpHeaders.SET_COOKIE, clearRefreshCookie())
                    .body(ApiResult.error(403, "Refresh origin is not allowed."));
        }
        try {
            IssuedSession issued = sessionService.refresh(
                    refreshCookie(request),
                    AuthRequestTrace.resolve(request)
            );
            return withRefreshCookie(issued, HttpStatus.OK);
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, clearRefreshCookie())
                    .body(ApiResult.error(401, "Refresh session is invalid or expired."));
        }
    }

    @GetMapping("/me")
    public ApiResult<UserResponse> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResult.success(UserResponse.from(principal));
    }

    @PostMapping("/password")
    public ResponseEntity<ApiResult<Void>> changePassword(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        try {
            sessionService.changePassword(
                    principal,
                    request.currentPassword(),
                    request.newPassword(),
                    AuthRequestTrace.resolve(servletRequest)
            );
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, clearRefreshCookie())
                    .body(ApiResult.success(null));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResult.error(401, "Current password is invalid."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResult<Void>> logout(
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        String rawRefreshToken = refreshCookie(servletRequest);
        if (!rawRefreshToken.isBlank() && !isTrustedOrigin(servletRequest.getHeader(HttpHeaders.ORIGIN))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .header(HttpHeaders.SET_COOKIE, clearRefreshCookie())
                    .body(ApiResult.error(403, "Logout origin is not allowed."));
        }
        sessionService.logout(principal, rawRefreshToken, AuthRequestTrace.resolve(servletRequest));
        return clearedCookieSuccess();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResult<Void>> logoutAll(
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        sessionService.logoutAll(principal, AuthRequestTrace.resolve(servletRequest));
        return clearedCookieSuccess();
    }

    private ResponseEntity<ApiResult<SessionResponse>> withRefreshCookie(
            IssuedSession issued,
            HttpStatus status
    ) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(issued))
                .body(ApiResult.success(SessionResponse.from(issued)));
    }

    private ResponseEntity<ApiResult<Void>> clearedCookieSuccess() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie())
                .body(ApiResult.success(null));
    }

    private String refreshCookie(IssuedSession issued) {
        long maxAge = Math.max(Duration.between(Instant.now(), issued.refreshExpiresAt()).toSeconds(), 0);
        return REFRESH_COOKIE + "=" + issued.refreshToken()
                + "; Path=/api/auth; Max-Age=" + maxAge
                + "; HttpOnly" + secureAttribute() + "; SameSite=" + sameSite();
    }

    private String clearRefreshCookie() {
        return REFRESH_COOKIE + "=; Path=/api/auth; Max-Age=0; HttpOnly"
                + secureAttribute() + "; SameSite=" + sameSite();
    }

    private String secureAttribute() {
        return appProperties.getAuth().isCookieSecure() ? "; Secure" : "";
    }

    private String sameSite() {
        String configured = appProperties.getAuth().getCookieSameSite();
        String normalized = configured == null ? "lax" : configured.trim().toLowerCase(Locale.ROOT);
        String value = switch (normalized) {
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> "Lax";
        };
        if ("None".equals(value) && !appProperties.getAuth().isCookieSecure()) {
            throw new IllegalStateException("SameSite=None refresh cookies must be Secure.");
        }
        return value;
    }

    private String refreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return "";
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> REFRESH_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse("");
    }

    private boolean isTrustedOrigin(String origin) {
        return origin != null && appProperties.getCors().getAllowedOrigins().contains(origin.trim());
    }

    private String loginClientKey(String username, String remoteAddress) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return normalized + "|" + (remoteAddress == null ? "unknown" : remoteAddress.trim());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record SessionResponse(
            String accessToken,
            Instant accessExpiresAt,
            UserResponse user
    ) {
        private static SessionResponse from(IssuedSession issued) {
            return new SessionResponse(
                    issued.accessToken(),
                    issued.accessExpiresAt(),
                    UserResponse.from(issued.principal())
            );
        }
    }

    public record UserResponse(
            Long id,
            String username,
            String displayName,
            boolean enabled,
            boolean mustChangePassword,
            Set<String> roles,
            Set<PermissionKey> permissions
    ) {
        private static UserResponse from(AuthPrincipal principal) {
            return new UserResponse(
                    principal.userId(),
                    principal.username(),
                    principal.displayName(),
                    principal.enabled(),
                    principal.mustChangePassword(),
                    principal.roles(),
                    principal.permissions()
            );
        }
    }
}
