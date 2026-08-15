package com.brand.agentpoc.auth.infrastructure;

import com.brand.agentpoc.auth.application.AuthSessionService;
import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.observability.infrastructure.web.RequestCorrelation;
import com.brand.agentpoc.tenant.application.TenantAuthorizationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OpaqueTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OpaqueTokenAuthenticationFilter.class);

    private final AuthSessionService sessionService;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;
    private final AuthAuditService auditService;

    public OpaqueTokenAuthenticationFilter(
            AuthSessionService sessionService,
            TenantAuthorizationService tenantAuthorizationService,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            AuthAuditService auditService
    ) {
        this.sessionService = sessionService;
        this.tenantAuthorizationService = tenantAuthorizationService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = bearerToken(authorization);
        Optional<AuthPrincipal> principal = sessionService.authenticateAccessToken(token);
        if (principal.isEmpty()) {
            log.warn("Opaque session authentication failed: path={}, remoteAddress={}, reason={}",
                    request.getServletPath(), request.getRemoteAddr(), "invalid_or_expired_token");
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new org.springframework.security.authentication.BadCredentialsException("Invalid bearer token.")
            );
            return;
        }

        AuthPrincipal authenticated = principal.get();
        if (requiresTenant(request)) {
            try {
                authenticated = tenantAuthorizationService.resolve(
                        authenticated,
                        request.getHeader(TenantAuthorizationService.TENANT_HEADER)
                );
            } catch (AccessDeniedException exception) {
                log.warn("Tenant authorization failed: path={}, userId={}, reason={}",
                        request.getServletPath(), authenticated.userId(), "tenant_context_denied");
                auditService.record(
                        null,
                        authenticated.userId(),
                        "TENANT_ACCESS_DENIED",
                        "TENANT_CONTEXT",
                        null,
                        "FAILURE",
                        traceId(request),
                        "tenant_context_denied"
                );
                accessDeniedHandler.handle(request, response, exception);
                return;
            }
        }
        List<SimpleGrantedAuthority> authorities = authenticated.mustChangePassword()
                ? List.of()
                : authenticated.permissions().stream()
                        .map(permission -> new SimpleGrantedAuthority(permission.name()))
                        .toList();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(authenticated, token, authorities)
        );
        filterChain.doFilter(request, response);
    }

    private String traceId(HttpServletRequest request) {
        return RequestCorrelation.traceId(request);
    }

    private boolean requiresTenant(HttpServletRequest request) {
        String path = request.getServletPath();
        if (!path.startsWith("/api/")) {
            return false;
        }
        return !path.equals("/api/auth/login")
                && !path.equals("/api/auth/refresh")
                && !path.equals("/api/auth/logout")
                && !path.equals("/api/auth/me")
                && !path.equals("/api/auth/password")
                && !path.equals("/api/auth/logout-all");
    }

    private String bearerToken(String authorization) {
        String prefix = "Bearer ";
        if (!authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return "";
        }
        return authorization.substring(prefix.length()).trim();
    }
}
