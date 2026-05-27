package com.brand.agentpoc.config;

import com.brand.agentpoc.service.SessionTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionTokenFilter.class);
    public static final String TOKEN_SUBJECT_ATTRIBUTE = SessionTokenFilter.class.getName() + ".tokenSubject";
    private static final List<String> PROTECTED_PATHS = List.of(
            "/api/chat/**",
            "/api/model-config/**"
    );

    private final SessionTokenService sessionTokenService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public SessionTokenFilter(SessionTokenService sessionTokenService) {
        this.sessionTokenService = sessionTokenService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String path = request.getServletPath();
        return PROTECTED_PATHS.stream().noneMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String token = extractBearerToken(authorization);
        Optional<SessionTokenService.TokenClaims> claims = StringUtils.hasText(token)
                ? sessionTokenService.validate(token)
                : Optional.empty();
        if (claims.isPresent()) {
            request.setAttribute(TOKEN_SUBJECT_ATTRIBUTE, claims.get().subject());
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Session token authentication failed: path={}, remoteAddress={}, reason={}",
                request.getServletPath(),
                request.getRemoteAddr(),
                failureReason(authorization, token));
        writeUnauthorized(response);
    }

    private String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return "";
        }

        String prefix = "Bearer ";
        if (!authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return "";
        }

        return authorization.substring(prefix.length()).trim();
    }

    private String failureReason(String authorization, String token) {
        if (!StringUtils.hasText(authorization)) {
            return "missing_bearer_token";
        }
        String prefix = "Bearer ";
        if (!authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return "invalid_authorization_scheme";
        }
        if (!StringUtils.hasText(token)) {
            return "empty_bearer_token";
        }
        return "invalid_or_expired_token";
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"Login session expired.\"}");
    }
}
