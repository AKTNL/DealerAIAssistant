# Backend Auth Security Step 1-8 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `docs/优化3.md` Step 1-8 so backend credentials, session-token signing, auth failure responses, CORS, and prod H2 settings are safer without changing the user-facing login flow.

**Architecture:** Keep the existing lightweight access-key login and servlet filters. Remove unsafe default credentials, require an explicit session signing secret, use constant-time comparisons, return standards-compatible 401 responses, log authentication failures without leaking credentials, make CORS origins configurable, and disable H2 console for the `prod` profile.

**Tech Stack:** Spring Boot 3.4, Servlet `OncePerRequestFilter`, `@ConfigurationProperties`, JUnit 5, AssertJ, Spring Mock servlet test utilities, Maven.

---

## File Structure

- Modify: `backend/src/main/java/com/brand/agentpoc/config/AppProperties.java`
  - Owns `app.*` configuration defaults and normalized setters.
  - Change auth/security defaults to empty strings.
  - Add `cors.allowedOrigins`.
- Modify: `backend/src/main/resources/application.yml`
  - Remove default credentials from environment placeholders.
  - Add configurable CORS origins.
- Create: `backend/src/main/resources/application-prod.yml`
  - Disable H2 console when `prod` profile is active.
- Modify: `backend/src/main/java/com/brand/agentpoc/service/SessionTokenService.java`
  - Require explicit `app.auth.session-secret`.
  - Keep signed token format unchanged.
- Modify: `backend/src/main/java/com/brand/agentpoc/service/AuthService.java`
  - Reject blank configured/input access keys.
  - Compare configured and supplied access keys with `MessageDigest.isEqual`.
- Modify: `backend/src/main/java/com/brand/agentpoc/config/ApiKeyFilter.java`
  - Reject blank configured/input API keys.
  - Compare keys with `MessageDigest.isEqual`.
  - Add `WWW-Authenticate: Bearer` and safe warn logging on 401.
- Modify: `backend/src/main/java/com/brand/agentpoc/config/SessionTokenFilter.java`
  - Add `WWW-Authenticate: Bearer` and safe warn logging on 401.
  - Preserve protected path list and bearer-token behavior.
- Modify: `backend/src/main/java/com/brand/agentpoc/config/CorsConfig.java`
  - Read allowed origins from `AppProperties`.
- Modify: `backend/src/test/java/com/brand/agentpoc/service/SessionTokenServiceTest.java`
  - Replace demo-secret fallback expectation with required-secret failure.
- Create: `backend/src/test/java/com/brand/agentpoc/service/AuthServiceTest.java`
  - Cover access-key behavior for correct, incorrect, blank, and null configuration.
- Modify: `backend/src/test/java/com/brand/agentpoc/config/ApiKeyFilterTest.java`
  - Cover valid key pass-through, missing key 401, `WWW-Authenticate`, and blank config rejection.
- Modify: `backend/src/test/java/com/brand/agentpoc/config/SessionTokenFilterTest.java`
  - Cover `WWW-Authenticate` for missing and invalid session token failures.
- Create: `backend/src/test/java/com/brand/agentpoc/config/CorsConfigTest.java`
  - Verify default and custom allowed origins are applied to `/**`.

## Task 1: Require Explicit Session Token Secret

**Files:**
- Modify: `backend/src/test/java/com/brand/agentpoc/service/SessionTokenServiceTest.java`
- Modify: `backend/src/main/java/com/brand/agentpoc/service/SessionTokenService.java`

- [ ] **Step 1: Write the failing session-secret test**

Replace the current `derivesDemoSecretFromAccessKeyWhenSessionSecretIsBlank` test in `SessionTokenServiceTest` and add the missing static import.

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

```java
@Test
void requiresConfiguredSessionSecretToIssueToken() {
    AppProperties properties = appProperties("", Duration.ofHours(8));
    SessionTokenService service = new SessionTokenService(properties, clock);

    assertThatThrownBy(service::issueToken)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.auth.session-secret is required");
}
```

- [ ] **Step 2: Run the focused red test**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=SessionTokenServiceTest#requiresConfiguredSessionSecretToIssueToken" test
```

Expected: FAIL because `issueToken()` still succeeds by deriving a demo secret from the access key.

- [ ] **Step 3: Implement explicit secret requirement**

In `SessionTokenService.java`, remove the `Logger` field and the `LoggerFactory` import. Add:

```java
import java.security.GeneralSecurityException;
```

Change `sign` and `resolveSecret` to:

```java
private String sign(String encodedPayload) {
    String secret = resolveSecret();
    try {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return encode(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
        throw new IllegalStateException("Unable to sign session token.", exception);
    }
}

private String resolveSecret() {
    String configured = appProperties.getAuth().getSessionSecret();
    if (StringUtils.hasText(configured)) {
        return configured.trim();
    }

    throw new IllegalStateException("app.auth.session-secret is required to sign session tokens.");
}
```

- [ ] **Step 4: Run session token tests**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=SessionTokenServiceTest" test
```

Expected: PASS. The signed-token, tampering, expiry, and missing-secret tests all pass.

- [ ] **Step 5: Commit Task 1**

```powershell
git add -- backend/src/main/java/com/brand/agentpoc/service/SessionTokenService.java backend/src/test/java/com/brand/agentpoc/service/SessionTokenServiceTest.java
git commit -m "fix: require explicit session token secret"
```

## Task 2: Remove Default Access Key and Use Constant-Time Access-Key Comparison

**Files:**
- Create: `backend/src/test/java/com/brand/agentpoc/service/AuthServiceTest.java`
- Modify: `backend/src/main/java/com/brand/agentpoc/service/AuthService.java`
- Modify: `backend/src/main/java/com/brand/agentpoc/config/AppProperties.java`

- [ ] **Step 1: Write AuthService tests**

Create `AuthServiceTest.java`:

```java
package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.brand.agentpoc.config.AppProperties;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    @Test
    void acceptsMatchingConfiguredAccessKey() {
        AuthService service = new AuthService(appProperties("configured-key"));

        assertThat(service.verifyAccessKey("configured-key")).isTrue();
    }

    @Test
    void rejectsDifferentAccessKey() {
        AuthService service = new AuthService(appProperties("configured-key"));

        assertThat(service.verifyAccessKey("wrong-key")).isFalse();
    }

    @Test
    void rejectsBlankInputAccessKey() {
        AuthService service = new AuthService(appProperties("configured-key"));

        assertThat(service.verifyAccessKey("")).isFalse();
    }

    @Test
    void rejectsBlankConfiguredAccessKey() {
        AuthService service = new AuthService(appProperties(""));

        assertThat(service.verifyAccessKey("configured-key")).isFalse();
    }

    @Test
    void rejectsNullConfiguredAccessKey() {
        AuthService service = new AuthService(appProperties(null));

        assertThat(service.verifyAccessKey("configured-key")).isFalse();
    }

    private AppProperties appProperties(String accessKey) {
        AppProperties properties = new AppProperties();
        properties.getAuth().setAccessKey(accessKey);
        return properties;
    }
}
```

- [ ] **Step 2: Run the focused red AuthService test**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=AuthServiceTest#rejectsNullConfiguredAccessKey" test
```

Expected: FAIL because `verifyAccessKey` currently throws `NullPointerException` when the configured key is null.

- [ ] **Step 3: Implement constant-time access-key comparison**

Replace `AuthService.java` with:

```java
package com.brand.agentpoc.service;

import com.brand.agentpoc.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final AppProperties appProperties;

    public AuthService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public boolean verifyAccessKey(String inputKey) {
        String configured = appProperties.getAuth().getAccessKey();
        if (!StringUtils.hasText(configured) || !StringUtils.hasText(inputKey)) {
            return false;
        }

        return MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                inputKey.getBytes(StandardCharsets.UTF_8));
    }
}
```

In `AppProperties.Auth`, change:

```java
private String accessKey = "demo123";
```

to:

```java
private String accessKey = "";
```

- [ ] **Step 4: Run AuthService tests**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=AuthServiceTest" test
```

Expected: PASS.

- [ ] **Step 5: Commit Task 2**

```powershell
git add -- backend/src/main/java/com/brand/agentpoc/service/AuthService.java backend/src/main/java/com/brand/agentpoc/config/AppProperties.java backend/src/test/java/com/brand/agentpoc/service/AuthServiceTest.java
git commit -m "fix: harden access key validation"
```

## Task 3: Harden Internal API Key Filter

**Files:**
- Modify: `backend/src/test/java/com/brand/agentpoc/config/ApiKeyFilterTest.java`
- Modify: `backend/src/main/java/com/brand/agentpoc/config/ApiKeyFilter.java`
- Modify: `backend/src/main/java/com/brand/agentpoc/config/AppProperties.java`

- [ ] **Step 1: Expand ApiKeyFilter tests**

Replace `ApiKeyFilterTest.java` with:

```java
package com.brand.agentpoc.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiKeyFilterTest {

    @Test
    void skipsInternalApiKeyForModelConfigBecauseSessionFilterProtectsIt() throws Exception {
        MockHttpServletResponse response = doFilter("POST", "/api/model-config/test", null, appProperties("configured-api-key"));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
    }

    @Test
    void allowsProtectedRequestsWithTheConfiguredInternalApiKey() throws Exception {
        MockHttpServletResponse response = doFilter("POST", "/api/v1/data/dealers", "configured-api-key", appProperties("configured-api-key"));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
    }

    @Test
    void rejectsProtectedRequestsWithoutTheInternalApiKey() throws Exception {
        MockHttpServletResponse response = doFilter("POST", "/api/v1/data/dealers", null, appProperties("configured-api-key"));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).isEqualTo("{\"code\":401,\"message\":\"Invalid API key\"}");
    }

    @Test
    void rejectsProtectedRequestsWhenInternalApiKeyConfigIsBlank() throws Exception {
        MockHttpServletResponse response = doFilter("POST", "/api/v1/data/dealers", "configured-api-key", appProperties(""));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    private MockHttpServletResponse doFilter(String method, String path, String apiKey, AppProperties appProperties) throws Exception {
        ApiKeyFilter filter = new ApiKeyFilter(appProperties);
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr("203.0.113.10");
        if (apiKey != null) {
            request.addHeader("X-API-Key", apiKey);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        });

        filter.doFilter(request, response, filterChain);
        return response;
    }

    private AppProperties appProperties(String apiKey) {
        AppProperties appProperties = new AppProperties();
        appProperties.getSecurity().setApiKey(apiKey);
        return appProperties;
    }
}
```

- [ ] **Step 2: Run the focused red ApiKeyFilter test**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=ApiKeyFilterTest#rejectsProtectedRequestsWithoutTheInternalApiKey" test
```

Expected: FAIL because the 401 response does not yet include `WWW-Authenticate: Bearer`.

- [ ] **Step 3: Implement hardened API key validation**

In `AppProperties.Security`, change:

```java
private String apiKey = "poc-api-key";
```

to:

```java
private String apiKey = "";
```

Replace `ApiKeyFilter.java` with:

```java
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
```

- [ ] **Step 4: Run ApiKeyFilter tests**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=ApiKeyFilterTest" test
```

Expected: PASS.

- [ ] **Step 5: Commit Task 3**

```powershell
git add -- backend/src/main/java/com/brand/agentpoc/config/AppProperties.java backend/src/main/java/com/brand/agentpoc/config/ApiKeyFilter.java backend/src/test/java/com/brand/agentpoc/config/ApiKeyFilterTest.java
git commit -m "fix: harden internal api key filter"
```

## Task 4: Add Session Token 401 Header and Safe Failure Logging

**Files:**
- Modify: `backend/src/test/java/com/brand/agentpoc/config/SessionTokenFilterTest.java`
- Modify: `backend/src/main/java/com/brand/agentpoc/config/SessionTokenFilter.java`

- [ ] **Step 1: Add SessionTokenFilter header assertions**

Update `rejectsChatRequestsWithoutToken`:

```java
@Test
void rejectsChatRequestsWithoutToken() throws Exception {
    MockHttpServletResponse response = doFilter("POST", "/api/chat/stream", null);

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    assertThat(response.getContentAsString()).isEqualTo("{\"code\":401,\"message\":\"Login session expired.\"}");
}
```

Update `rejectsChatRequestsWithInvalidToken`:

```java
@Test
void rejectsChatRequestsWithInvalidToken() throws Exception {
    when(sessionTokenService.isValid("bad-token")).thenReturn(false);

    MockHttpServletResponse response = doFilter("POST", "/api/chat/stream", "Bearer bad-token");

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
}
```

Add a malformed authorization test:

```java
@Test
void rejectsChatRequestsWithUnsupportedAuthorizationScheme() throws Exception {
    MockHttpServletResponse response = doFilter("POST", "/api/chat/stream", "Basic abc");

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
}
```

- [ ] **Step 2: Run the focused red SessionTokenFilter test**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=SessionTokenFilterTest#rejectsChatRequestsWithoutToken" test
```

Expected: FAIL because the 401 response does not yet include `WWW-Authenticate: Bearer`.

- [ ] **Step 3: Implement session filter 401 header and logging**

Replace `SessionTokenFilter.java` with:

```java
package com.brand.agentpoc.config;

import com.brand.agentpoc.service.SessionTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
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
        if (StringUtils.hasText(token) && sessionTokenService.isValid(token)) {
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
```

- [ ] **Step 4: Run SessionTokenFilter tests**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=SessionTokenFilterTest" test
```

Expected: PASS.

- [ ] **Step 5: Commit Task 4**

```powershell
git add -- backend/src/main/java/com/brand/agentpoc/config/SessionTokenFilter.java backend/src/test/java/com/brand/agentpoc/config/SessionTokenFilterTest.java
git commit -m "fix: add session auth failure response metadata"
```

## Task 5: Configure CORS Origins and Disable H2 Console in Prod

**Files:**
- Create: `backend/src/test/java/com/brand/agentpoc/config/CorsConfigTest.java`
- Modify: `backend/src/main/java/com/brand/agentpoc/config/AppProperties.java`
- Modify: `backend/src/main/java/com/brand/agentpoc/config/CorsConfig.java`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-prod.yml`

- [ ] **Step 1: Write CorsConfig tests**

Create `CorsConfigTest.java`:

```java
package com.brand.agentpoc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class CorsConfigTest {

    @Test
    void appliesDefaultLocalViteOrigins() throws Exception {
        AppProperties properties = new AppProperties();
        CorsConfiguration configuration = corsConfiguration(properties);

        assertThat(configuration.getAllowedOrigins())
                .containsExactly("http://localhost:5173", "http://127.0.0.1:5173");
    }

    @Test
    void appliesConfiguredAllowedOrigins() throws Exception {
        AppProperties properties = new AppProperties();
        properties.getCors().setAllowedOrigins(List.of(" https://example.com ", "", "https://admin.example.com"));

        CorsConfiguration configuration = corsConfiguration(properties);

        assertThat(configuration.getAllowedOrigins())
                .containsExactly("https://example.com", "https://admin.example.com");
    }

    @SuppressWarnings("unchecked")
    private CorsConfiguration corsConfiguration(AppProperties properties) throws Exception {
        CorsRegistry registry = new CorsRegistry();
        new CorsConfig(properties).addCorsMappings(registry);

        Method method = CorsRegistry.class.getDeclaredMethod("getCorsConfigurations");
        method.setAccessible(true);
        Map<String, CorsConfiguration> configurations = (Map<String, CorsConfiguration>) method.invoke(registry);
        return configurations.get("/**");
    }
}
```

- [ ] **Step 2: Run the focused red CorsConfig test**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=CorsConfigTest" test
```

Expected: FAIL at compilation because `AppProperties.getCors()` and `CorsConfig(AppProperties)` do not exist yet.

- [ ] **Step 3: Add CORS properties**

In `AppProperties.java`, add a field next to the existing config groups:

```java
private final Cors cors = new Cors();
```

Add the getter:

```java
public Cors getCors() {
    return cors;
}
```

Add this nested class before `Excel`:

```java
public static class Cors {
    private List<String> allowedOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .toList();
    }
}
```

- [ ] **Step 4: Read CORS origins from configuration**

Replace `CorsConfig.java` with:

```java
package com.brand.agentpoc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    public CorsConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(appProperties.getCors().getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
```

- [ ] **Step 5: Update YAML configuration**

In `application.yml`, change auth and security defaults:

```yaml
app:
  auth:
    access-key: ${APP_ACCESS_KEY:}
    session-secret: ${APP_SESSION_SECRET:}
    session-ttl: ${APP_SESSION_TTL:8h}
  security:
    api-key: ${APP_API_KEY:}
  cors:
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:http://localhost:5173,http://127.0.0.1:5173}
```

Keep the existing `excel` and `model` sections below `cors`.

Create `application-prod.yml`:

```yaml
spring:
  h2:
    console:
      enabled: false
```

- [ ] **Step 6: Run CorsConfig tests**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=CorsConfigTest" test
```

Expected: PASS.

- [ ] **Step 7: Commit Task 5**

```powershell
git add -- backend/src/main/java/com/brand/agentpoc/config/AppProperties.java backend/src/main/java/com/brand/agentpoc/config/CorsConfig.java backend/src/main/resources/application.yml backend/src/main/resources/application-prod.yml backend/src/test/java/com/brand/agentpoc/config/CorsConfigTest.java
git commit -m "fix: configure cors origins and prod h2 console"
```

## Task 6: Focused Verification and Default Credential Scan

**Files:**
- Verify only. Do not modify files unless verification exposes a defect in Tasks 1-5.

- [ ] **Step 1: Run focused backend tests**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=SessionTokenServiceTest,AuthServiceTest,ApiKeyFilterTest,SessionTokenFilterTest,CorsConfigTest" test
```

Expected: PASS.

- [ ] **Step 2: Run backend full test suite**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" test
```

Expected: PASS.

- [ ] **Step 3: Scan backend runtime code for removed default credentials**

Run:

```powershell
rg "demo123|poc-api-key|demo-session-secret" backend/src/main backend/src/main/resources/application.yml
```

Expected: exit code 1 with no matches. Test files may still contain arbitrary sample strings, but runtime source and `application.yml` must not contain these default credentials.

- [ ] **Step 4: Confirm constant-time comparison is present in runtime code**

Run:

```powershell
rg "MessageDigest\\.isEqual" backend/src/main/java/com/brand/agentpoc/service/AuthService.java backend/src/main/java/com/brand/agentpoc/config/ApiKeyFilter.java
```

Expected: both files contain `MessageDigest.isEqual`.

- [ ] **Step 5: Handle verification failures**

If Steps 1-4 fail, return to the task that introduced the failing behavior and repeat that task's red-green-refactor loop with the exact files listed there. Commit the corrected files with that task's commit command after the focused test passes.

If Steps 1-4 pass, do not create an empty commit.
