package com.brand.agentpoc.auth.infrastructure;

import com.brand.agentpoc.auth.application.AuthSessionService;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OpaqueTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OpaqueTokenAuthenticationFilter.class);

    private final AuthSessionService sessionService;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;

    public OpaqueTokenAuthenticationFilter(
            AuthSessionService sessionService,
            JsonAuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.sessionService = sessionService;
        this.authenticationEntryPoint = authenticationEntryPoint;
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

    private String bearerToken(String authorization) {
        String prefix = "Bearer ";
        if (!authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return "";
        }
        return authorization.substring(prefix.length()).trim();
    }
}
