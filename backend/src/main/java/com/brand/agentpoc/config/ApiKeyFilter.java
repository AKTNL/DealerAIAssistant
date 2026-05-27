package com.brand.agentpoc.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);
    private static final List<String> WHITELIST = List.of(
            "/",
            "/index.html",
            "/assets/**",
            "/favicon.ico",
            "/api/auth/**",
            "/api/chat/**",
            "/api/model-config/**",
            "/openapi.json",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/h2-console/**",
            "/actuator/health"
    );

    private final AppProperties appProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ApiKeyFilter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String path = request.getServletPath();
        return WHITELIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String apiKey = request.getHeader("X-API-Key");
        if (matchesConfiguredApiKey(apiKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("API key authentication failed: path={}, remoteAddress={}, reason={}",
                request.getServletPath(),
                request.getRemoteAddr(),
                failureReason(apiKey));
        writeUnauthorized(response);
    }

    private boolean matchesConfiguredApiKey(String provided) {
        String configured = appProperties.getSecurity().getApiKey();
        if (!StringUtils.hasText(configured) || !StringUtils.hasText(provided)) {
            return false;
        }

        return MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private String failureReason(String provided) {
        if (!StringUtils.hasText(appProperties.getSecurity().getApiKey())) {
            return "api_key_not_configured";
        }
        if (!StringUtils.hasText(provided)) {
            return "missing_api_key";
        }
        return "invalid_api_key";
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"Invalid API key\"}");
    }
}
