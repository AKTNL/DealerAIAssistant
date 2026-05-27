# Conservative Security Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend-verifiable session protection for chat/model APIs and guard model base URLs while preserving the documented POC access-key workflow.

**Architecture:** Keep the current simple access-key login, but return a signed session token from `/api/auth/verify`. A lightweight servlet filter protects `/api/chat/**` and `/api/model-config/**` with `Authorization: Bearer <token>`, while the existing `X-API-Key` filter continues protecting data APIs. Model URLs are validated against local/private address rules and optional host allowlists before any outbound model call.

**Tech Stack:** Java 21, Spring Boot 3.4, Spring MVC filters, HMAC-SHA256 via JDK crypto, Vue 3 composables, Vite/Vitest.

---

## File Structure

Backend files:

- Create `backend/src/main/java/com/brand/agentpoc/service/SessionTokenService.java`: stateless HMAC token creation and validation.
- Create `backend/src/main/java/com/brand/agentpoc/config/SessionTokenFilter.java`: protects browser-facing chat/model endpoints.
- Modify `backend/src/main/java/com/brand/agentpoc/config/AppProperties.java`: add session-token and model-URL guard configuration.
- Modify `backend/src/main/resources/application.yml`: document defaults and env var bindings.
- Modify `backend/src/main/java/com/brand/agentpoc/controller/AuthController.java`: issue token on successful access-key verification.
- Modify `backend/src/main/java/com/brand/agentpoc/dto/response/AuthVerifyResponse.java`: add `sessionToken` and `expiresAt`.
- Modify `backend/src/main/java/com/brand/agentpoc/service/ModelConfigService.java`: validate unsafe/disallowed `baseUrl`.
- Test `backend/src/test/java/com/brand/agentpoc/service/SessionTokenServiceTest.java`.
- Test `backend/src/test/java/com/brand/agentpoc/config/SessionTokenFilterTest.java`.
- Test `backend/src/test/java/com/brand/agentpoc/controller/AuthControllerTest.java`.
- Test `backend/src/test/java/com/brand/agentpoc/service/ModelConfigServiceTest.java`.
- Modify `backend/src/test/java/com/brand/agentpoc/config/ApiKeyFilterTest.java`: keep explicit coverage that chat/model endpoints do not require internal API key.

Frontend files:

- Create `frontend/src/api/sessionToken.js`: token read/write/clear/expiry helpers.
- Modify `frontend/src/api/client.js`: preserve existing JSON behavior and expose typed HTTP errors.
- Modify `frontend/src/api/chat.js`: attach bearer token to chat stream and clear-session requests.
- Modify `frontend/src/api/modelConfig.js`: attach bearer token to model connection tests.
- Modify `frontend/src/composables/useAuth.js`: store token response in `sessionStorage`.
- Modify `frontend/src/composables/useChat.js`: map session expiration distinctly from model API-key errors.
- Modify `frontend/src/views/ChatView.vue`: handle auth expiry from model-test flow and chat flow.
- Modify `frontend/src/i18n/messages.js`: add localized auth-expired copy.
- Test `frontend/src/api/__tests__/sessionToken.spec.js`.
- Modify `frontend/src/api/__tests__/modelConfig.spec.js`.
- Modify `frontend/src/composables/__tests__/useChat.spec.js`.

---

## Task 1: Backend Session Token Service

**Files:**

- Modify: `backend/src/main/java/com/brand/agentpoc/config/AppProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/brand/agentpoc/service/SessionTokenService.java`
- Test: `backend/src/test/java/com/brand/agentpoc/service/SessionTokenServiceTest.java`

- [ ] **Step 1: Write the failing token service tests**

Create `backend/src/test/java/com/brand/agentpoc/service/SessionTokenServiceTest.java`:

```java
package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.brand.agentpoc.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SessionTokenServiceTest {

    private final Instant now = Instant.parse("2026-05-21T08:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @Test
    void createsAndValidatesSignedTokens() {
        SessionTokenService service = new SessionTokenService(appProperties("secret-value", Duration.ofHours(8)), clock);

        SessionTokenService.IssuedToken issued = service.issueToken();

        assertThat(issued.token()).startsWith("v1.");
        assertThat(issued.expiresAt()).isEqualTo(now.plus(Duration.ofHours(8)));
        assertThat(service.isValid(issued.token())).isTrue();
    }

    @Test
    void rejectsTamperedTokens() {
        SessionTokenService service = new SessionTokenService(appProperties("secret-value", Duration.ofHours(8)), clock);
        String token = service.issueToken().token();
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(service.isValid(tampered)).isFalse();
    }

    @Test
    void rejectsExpiredTokens() {
        AppProperties properties = appProperties("secret-value", Duration.ofMinutes(5));
        SessionTokenService issuer = new SessionTokenService(properties, clock);
        String token = issuer.issueToken().token();

        Clock afterExpiry = Clock.fixed(now.plus(Duration.ofMinutes(6)), ZoneOffset.UTC);
        SessionTokenService validator = new SessionTokenService(properties, afterExpiry);

        assertThat(validator.isValid(token)).isFalse();
    }

    @Test
    void derivesDemoSecretFromAccessKeyWhenSessionSecretIsBlank() {
        AppProperties properties = appProperties("", Duration.ofHours(8));
        properties.getAuth().setAccessKey("demo-access-key");
        SessionTokenService service = new SessionTokenService(properties, clock);

        assertThat(service.isValid(service.issueToken().token())).isTrue();
    }

    private AppProperties appProperties(String secret, Duration ttl) {
        AppProperties properties = new AppProperties();
        properties.getAuth().setSessionSecret(secret);
        properties.getAuth().setSessionTtl(ttl);
        return properties;
    }
}
```

- [ ] **Step 2: Run the failing backend token tests**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=SessionTokenServiceTest" test
```

Expected: compilation fails because `SessionTokenService`, `setSessionSecret`, and `setSessionTtl` do not exist.

- [ ] **Step 3: Add auth session configuration**

In `backend/src/main/java/com/brand/agentpoc/config/AppProperties.java`, replace the `Auth` inner class with:

```java
    public static class Auth {
        private String accessKey = "demo123";
        private String sessionSecret = "";
        private java.time.Duration sessionTtl = java.time.Duration.ofHours(8);

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSessionSecret() {
            return sessionSecret;
        }

        public void setSessionSecret(String sessionSecret) {
            this.sessionSecret = sessionSecret;
        }

        public java.time.Duration getSessionTtl() {
            return sessionTtl;
        }

        public void setSessionTtl(java.time.Duration sessionTtl) {
            this.sessionTtl = sessionTtl;
        }
    }
```

In `backend/src/main/resources/application.yml`, extend `app.auth`:

```yaml
app:
  auth:
    access-key: ${APP_ACCESS_KEY:demo123}
    session-secret: ${APP_SESSION_SECRET:}
    session-ttl: ${APP_SESSION_TTL:8h}
```

Keep the existing `app.security` and `app.excel` entries unchanged.

- [ ] **Step 4: Implement the session token service**

Create `backend/src/main/java/com/brand/agentpoc/service/SessionTokenService.java`:

```java
package com.brand.agentpoc.service;

import com.brand.agentpoc.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SessionTokenService {

    private static final Logger log = LoggerFactory.getLogger(SessionTokenService.class);
    private static final String VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final AppProperties appProperties;
    private final Clock clock;

    public SessionTokenService(AppProperties appProperties, Clock clock) {
        this.appProperties = appProperties;
        this.clock = clock;
    }

    public IssuedToken issueToken() {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(resolveTtl());
        String payload = issuedAt.getEpochSecond() + "." + expiresAt.getEpochSecond() + "." + UUID.randomUUID();
        String encodedPayload = encode(payload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(encodedPayload);
        return new IssuedToken(VERSION + "." + encodedPayload + "." + signature, expiresAt);
    }

    public boolean isValid(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }

        String[] parts = token.split("\\.", 3);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            return false;
        }

        String expectedSignature = sign(parts[1]);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            return false;
        }

        try {
            String payload = new String(BASE64_URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            String[] payloadParts = payload.split("\\.", 3);
            if (payloadParts.length != 3) {
                return false;
            }

            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(payloadParts[1]));
            return Instant.now(clock).isBefore(expiresAt);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Duration resolveTtl() {
        Duration ttl = appProperties.getAuth().getSessionTtl();
        return ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofHours(8) : ttl;
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(resolveSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return encode(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign session token.", exception);
        }
    }

    private String resolveSecret() {
        String configured = appProperties.getAuth().getSessionSecret();
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }

        log.warn("app.auth.session-secret is not configured; deriving a demo session secret from access-key.");
        return "demo-session-secret:" + appProperties.getAuth().getAccessKey();
    }

    private String encode(byte[] bytes) {
        return BASE64_URL_ENCODER.encodeToString(bytes);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
```

- [ ] **Step 5: Run token tests and commit**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=SessionTokenServiceTest" test
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`.

Commit:

```powershell
git add -- backend/src/main/java/com/brand/agentpoc/config/AppProperties.java backend/src/main/resources/application.yml backend/src/main/java/com/brand/agentpoc/service/SessionTokenService.java backend/src/test/java/com/brand/agentpoc/service/SessionTokenServiceTest.java
git commit -m "feat: add signed session tokens"
```

---

## Task 2: Backend Login Response and Session Filter

**Files:**

- Modify: `backend/src/main/java/com/brand/agentpoc/dto/response/AuthVerifyResponse.java`
- Modify: `backend/src/main/java/com/brand/agentpoc/controller/AuthController.java`
- Create: `backend/src/main/java/com/brand/agentpoc/config/SessionTokenFilter.java`
- Create: `backend/src/test/java/com/brand/agentpoc/controller/AuthControllerTest.java`
- Create: `backend/src/test/java/com/brand/agentpoc/config/SessionTokenFilterTest.java`
- Modify: `backend/src/test/java/com/brand/agentpoc/config/ApiKeyFilterTest.java`

- [ ] **Step 1: Write failing auth controller tests**

Create `backend/src/test/java/com/brand/agentpoc/controller/AuthControllerTest.java`:

```java
package com.brand.agentpoc.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brand.agentpoc.service.AuthService;
import com.brand.agentpoc.service.SessionTokenService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AuthControllerTest {

    private AuthService authService;
    private SessionTokenService sessionTokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        sessionTokenService = mock(SessionTokenService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, sessionTokenService))
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsTokenWhenAccessKeyIsValid() throws Exception {
        when(authService.verifyAccessKey("demo123")).thenReturn(true);
        when(sessionTokenService.issueToken()).thenReturn(new SessionTokenService.IssuedToken(
                "signed-token",
                Instant.parse("2026-05-21T16:00:00Z")
        ));

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(APPLICATION_JSON)
                        .content("{\"key\":\"demo123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sessionToken").value("signed-token"))
                .andExpect(jsonPath("$.expiresAt").value("2026-05-21T16:00:00Z"));
    }

    @Test
    void omitsTokenWhenAccessKeyIsInvalid() throws Exception {
        when(authService.verifyAccessKey("wrong")).thenReturn(false);

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(APPLICATION_JSON)
                        .content("{\"key\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.sessionToken").doesNotExist())
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }
}
```

- [ ] **Step 2: Write failing session filter tests**

Create `backend/src/test/java/com/brand/agentpoc/config/SessionTokenFilterTest.java`:

```java
package com.brand.agentpoc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.service.SessionTokenService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SessionTokenFilterTest {

    private final SessionTokenService sessionTokenService = mock(SessionTokenService.class);
    private final SessionTokenFilter filter = new SessionTokenFilter(sessionTokenService);

    @Test
    void rejectsChatRequestsWithoutToken() throws Exception {
        MockHttpServletResponse response = doFilter("POST", "/api/chat/stream", null);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).isEqualTo("{\"code\":401,\"message\":\"Login session expired.\"}");
    }

    @Test
    void rejectsChatRequestsWithInvalidToken() throws Exception {
        when(sessionTokenService.isValid("bad-token")).thenReturn(false);

        MockHttpServletResponse response = doFilter("POST", "/api/chat/stream", "Bearer bad-token");

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void allowsModelConfigRequestsWithValidToken() throws Exception {
        when(sessionTokenService.isValid("good-token")).thenReturn(true);

        MockHttpServletResponse response = doFilter("POST", "/api/model-config/test", "Bearer good-token");

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
    }

    @Test
    void skipsAuthAndHealthEndpoints() throws Exception {
        MockHttpServletResponse response = doFilter("POST", "/api/auth/verify", null);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
    }

    private MockHttpServletResponse doFilter(String method, String path, String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        });

        filter.doFilter(request, response, chain);
        return response;
    }
}
```

- [ ] **Step 3: Run the failing auth/filter tests**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=AuthControllerTest,SessionTokenFilterTest" test
```

Expected: compilation fails because `AuthController` does not accept `SessionTokenService`, `AuthVerifyResponse` has no token fields, and `SessionTokenFilter` does not exist.

- [ ] **Step 4: Update auth response DTO**

Replace `backend/src/main/java/com/brand/agentpoc/dto/response/AuthVerifyResponse.java` with:

```java
package com.brand.agentpoc.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthVerifyResponse(boolean success, String sessionToken, Instant expiresAt) {

    public static AuthVerifyResponse success(String sessionToken, Instant expiresAt) {
        return new AuthVerifyResponse(true, sessionToken, expiresAt);
    }

    public static AuthVerifyResponse failure() {
        return new AuthVerifyResponse(false, null, null);
    }
}
```

- [ ] **Step 5: Issue tokens from AuthController**

Replace `backend/src/main/java/com/brand/agentpoc/controller/AuthController.java` with:

```java
package com.brand.agentpoc.controller;

import com.brand.agentpoc.dto.request.AuthVerifyRequest;
import com.brand.agentpoc.dto.response.AuthVerifyResponse;
import com.brand.agentpoc.service.AuthService;
import com.brand.agentpoc.service.SessionTokenService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionTokenService sessionTokenService;

    public AuthController(AuthService authService, SessionTokenService sessionTokenService) {
        this.authService = authService;
        this.sessionTokenService = sessionTokenService;
    }

    @PostMapping("/verify")
    public AuthVerifyResponse verify(@Valid @RequestBody AuthVerifyRequest request) {
        if (!authService.verifyAccessKey(request.key())) {
            return AuthVerifyResponse.failure();
        }

        SessionTokenService.IssuedToken issuedToken = sessionTokenService.issueToken();
        return AuthVerifyResponse.success(issuedToken.token(), issuedToken.expiresAt());
    }
}
```

- [ ] **Step 6: Implement the session token filter**

Create `backend/src/main/java/com/brand/agentpoc/config/SessionTokenFilter.java`:

```java
package com.brand.agentpoc.config;

import com.brand.agentpoc.service.SessionTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionTokenFilter extends OncePerRequestFilter {

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
        String token = extractBearerToken(request.getHeader("Authorization"));
        if (sessionTokenService.isValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"Login session expired.\"}");
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
}
```

- [ ] **Step 7: Preserve API-key filter coverage**

In `backend/src/test/java/com/brand/agentpoc/config/ApiKeyFilterTest.java`, rename the first test to clarify the split responsibility:

```java
    @Test
    void skipsInternalApiKeyForModelConfigBecauseSessionFilterProtectsIt() throws Exception {
        ApiKeyFilter filter = new ApiKeyFilter(appProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/model-config/test");
        request.setServletPath("/api/model-config/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        });

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
    }
```

Do not remove `/api/model-config/**` or `/api/chat/**` from `ApiKeyFilter.WHITELIST`; the new `SessionTokenFilter` protects those paths.

- [ ] **Step 8: Run auth/filter tests and commit**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=AuthControllerTest,SessionTokenFilterTest,ApiKeyFilterTest" test
```

Expected: all selected tests pass with zero failures.

Commit:

```powershell
git add -- backend/src/main/java/com/brand/agentpoc/dto/response/AuthVerifyResponse.java backend/src/main/java/com/brand/agentpoc/controller/AuthController.java backend/src/main/java/com/brand/agentpoc/config/SessionTokenFilter.java backend/src/test/java/com/brand/agentpoc/controller/AuthControllerTest.java backend/src/test/java/com/brand/agentpoc/config/SessionTokenFilterTest.java backend/src/test/java/com/brand/agentpoc/config/ApiKeyFilterTest.java
git commit -m "feat: protect browser APIs with session tokens"
```

---

## Task 3: Backend Model URL Guard

**Files:**

- Modify: `backend/src/main/java/com/brand/agentpoc/config/AppProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/brand/agentpoc/service/ModelConfigService.java`
- Test: `backend/src/test/java/com/brand/agentpoc/service/ModelConfigServiceTest.java`

- [ ] **Step 1: Write failing model URL guard tests**

Create `backend/src/test/java/com/brand/agentpoc/service/ModelConfigServiceTest.java`:

```java
package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.dto.request.ModelConfigRequest;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.retry.support.RetryTemplate;

class ModelConfigServiceTest {

    @Test
    void rejectsLocalhostBaseUrls() {
        ModelConfigService service = serviceWithAllowedHosts(List.of());

        assertThatThrownBy(() -> service.createChatModel(request("http://localhost:11434/v1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Model base URL is not allowed.");
    }

    @Test
    void rejectsPrivateNetworkBaseUrls() {
        ModelConfigService service = serviceWithAllowedHosts(List.of());

        assertThatThrownBy(() -> service.createChatModel(request("http://192.168.1.10:8000/v1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Model base URL is not allowed.");
    }

    @Test
    void rejectsHostsOutsideConfiguredAllowlist() {
        ModelConfigService service = serviceWithAllowedHosts(List.of("api.openai.com"));

        assertThatThrownBy(() -> service.createChatModel(request("https://api.example.com/v1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Model base URL is not allowed.");
    }

    @Test
    void acceptsExactAllowlistedHosts() {
        ModelConfigService service = serviceWithAllowedHosts(List.of("api.openai.com"));

        service.createChatModel(request("https://api.openai.com/v1"));
    }

    @Test
    void acceptsWildcardAllowlistedHosts() {
        ModelConfigService service = serviceWithAllowedHosts(List.of("*.example.com"));

        service.createChatModel(request("https://models.example.com/v1"));
    }

    private ModelConfigService serviceWithAllowedHosts(List<String> allowedHosts) {
        AppProperties properties = new AppProperties();
        properties.getModel().setAllowedHosts(allowedHosts);
        properties.getModel().setAllowPrivateHosts(false);

        return new ModelConfigService(
                mock(ToolCallingManager.class),
                RetryTemplate.defaultInstance(),
                ObservationRegistry.NOOP,
                properties
        );
    }

    private ModelConfigRequest request(String baseUrl) {
        return new ModelConfigRequest(baseUrl, "sk-test", "gpt-test");
    }
}
```

- [ ] **Step 2: Run failing model URL guard tests**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=ModelConfigServiceTest" test
```

Expected: compilation fails because `AppProperties.getModel()` and the `ModelConfigService` constructor with `AppProperties` do not exist.

- [ ] **Step 3: Add model URL guard configuration**

In `backend/src/main/java/com/brand/agentpoc/config/AppProperties.java`, add a `Model` field and getter beside existing fields:

```java
    private final Model model = new Model();

    public Model getModel() {
        return model;
    }
```

Add the `Model` inner class:

```java
    public static class Model {
        private java.util.List<String> allowedHosts = java.util.List.of();
        private boolean allowPrivateHosts = false;

        public java.util.List<String> getAllowedHosts() {
            return allowedHosts;
        }

        public void setAllowedHosts(java.util.List<String> allowedHosts) {
            this.allowedHosts = allowedHosts == null
                    ? java.util.List.of()
                    : allowedHosts.stream()
                            .filter(value -> value != null && !value.isBlank())
                            .map(String::trim)
                            .toList();
        }

        public boolean isAllowPrivateHosts() {
            return allowPrivateHosts;
        }

        public void setAllowPrivateHosts(boolean allowPrivateHosts) {
            this.allowPrivateHosts = allowPrivateHosts;
        }
    }
```

In `backend/src/main/resources/application.yml`, add:

```yaml
  model:
    allowed-hosts: ${APP_MODEL_ALLOWED_HOSTS:}
    allow-private-hosts: ${APP_MODEL_ALLOW_PRIVATE_HOSTS:false}
```

Place it under the existing `app:` block. Spring Boot binds comma-separated `APP_MODEL_ALLOWED_HOSTS` values into the `List<String>` property, for example `api.openai.com,*.example.com`.

- [ ] **Step 4: Inject AppProperties into ModelConfigService**

Update the constructor and field in `backend/src/main/java/com/brand/agentpoc/service/ModelConfigService.java`:

```java
    private final AppProperties appProperties;

    public ModelConfigService(
            ToolCallingManager toolCallingManager,
            RetryTemplate retryTemplate,
            ObservationRegistry observationRegistry,
            AppProperties appProperties
    ) {
        this.toolCallingManager = toolCallingManager;
        this.retryTemplate = retryTemplate;
        this.observationRegistry = observationRegistry;
        this.appProperties = appProperties;
    }
```

Add the import:

```java
import com.brand.agentpoc.config.AppProperties;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
```

Keep the existing imports and remove duplicated `java.net.URI` if present.

- [ ] **Step 5: Implement base URL policy validation**

In `validateBaseUrl(String baseUrl)`, after existing scheme/host checks, call:

```java
            validateAllowedModelHost(uri);
```

Add these helper methods to `ModelConfigService`:

```java
    private void validateAllowedModelHost(URI uri) {
        String host = uri.getHost();
        if (!appProperties.getModel().isAllowPrivateHosts() && isUnsafeHost(host)) {
            throw new IllegalArgumentException("Model base URL is not allowed.");
        }

        List<String> allowedHosts = appProperties.getModel().getAllowedHosts();
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            return;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        boolean allowed = allowedHosts.stream()
                .filter(this::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(pattern -> hostMatches(pattern, normalizedHost));

        if (!allowed) {
            throw new IllegalArgumentException("Model base URL is not allowed.");
        }
    }

    private boolean hostMatches(String pattern, String host) {
        if (pattern.startsWith("*.")) {
            String suffix = pattern.substring(1);
            return host.endsWith(suffix) && host.length() > suffix.length();
        }
        return host.equals(pattern);
    }

    private boolean isUnsafeHost(String host) {
        if (!hasText(host)) {
            return true;
        }

        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalized) || normalized.endsWith(".localhost")) {
            return true;
        }

        String candidate = normalized;
        if (candidate.startsWith("[") && candidate.endsWith("]")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }

        if (!looksLikeIpLiteral(candidate)) {
            return false;
        }

        try {
            InetAddress address = InetAddress.getByName(candidate);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress();
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean looksLikeIpLiteral(String value) {
        return value.matches("\\d{1,3}(?:\\.\\d{1,3}){3}") || value.contains(":");
    }
```

Keep the existing `"Invalid base URL."` handling for malformed URLs. The new policy rejection message must be exactly `"Model base URL is not allowed."`.

- [ ] **Step 6: Run model URL guard tests and commit**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=ModelConfigServiceTest,ModelConfigControllerTest" test
```

Expected: all selected tests pass with zero failures.

Commit:

```powershell
git add -- backend/src/main/java/com/brand/agentpoc/config/AppProperties.java backend/src/main/resources/application.yml backend/src/main/java/com/brand/agentpoc/service/ModelConfigService.java backend/src/test/java/com/brand/agentpoc/service/ModelConfigServiceTest.java
git commit -m "feat: guard model base urls"
```

---

## Task 4: Frontend Session Token Storage and API Headers

**Files:**

- Create: `frontend/src/api/sessionToken.js`
- Create: `frontend/src/api/__tests__/sessionToken.spec.js`
- Modify: `frontend/src/api/client.js`
- Modify: `frontend/src/api/chat.js`
- Modify: `frontend/src/api/modelConfig.js`
- Modify: `frontend/src/api/__tests__/modelConfig.spec.js`

- [ ] **Step 1: Write failing session token helper tests**

Create `frontend/src/api/__tests__/sessionToken.spec.js`:

```javascript
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearAuthSession,
  getAuthToken,
  isAuthSessionValid,
  readAuthSession,
  writeAuthSession
} from "../sessionToken";

describe("sessionToken", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    vi.useRealTimers();
  });

  it("stores and reads auth session tokens from sessionStorage", () => {
    writeAuthSession({
      sessionToken: "signed-token",
      expiresAt: "2026-05-21T16:00:00.000Z"
    });

    expect(readAuthSession()).toEqual({
      sessionToken: "signed-token",
      expiresAt: "2026-05-21T16:00:00.000Z"
    });
    expect(getAuthToken()).toBe("signed-token");
  });

  it("treats expired sessions as invalid", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-21T17:00:00.000Z"));

    writeAuthSession({
      sessionToken: "signed-token",
      expiresAt: "2026-05-21T16:00:00.000Z"
    });

    expect(isAuthSessionValid()).toBe(false);
    expect(getAuthToken()).toBe("");
  });

  it("clears malformed stored sessions", () => {
    window.sessionStorage.setItem("agentpoc.authVerified", "{bad-json");

    expect(readAuthSession()).toBeNull();
    expect(window.sessionStorage.getItem("agentpoc.authVerified")).toBeNull();
  });

  it("clears auth sessions", () => {
    writeAuthSession({
      sessionToken: "signed-token",
      expiresAt: "2026-05-21T16:00:00.000Z"
    });

    clearAuthSession();

    expect(readAuthSession()).toBeNull();
  });
});
```

- [ ] **Step 2: Run failing frontend session-token tests**

Run:

```powershell
cd frontend
npm.cmd run test -- src/api/__tests__/sessionToken.spec.js
```

Expected: fails because `src/api/sessionToken.js` does not exist.

- [ ] **Step 3: Implement session token helper**

Create `frontend/src/api/sessionToken.js`:

```javascript
import { STORAGE_KEYS } from "../constants/storageKeys";
import { readStorageValue, removeStorageValue, writeStorageValue } from "../utils/storage";

export function writeAuthSession(session) {
  const normalized = normalizeAuthSession(session);

  if (!normalized) {
    clearAuthSession();
    return false;
  }

  writeStorageValue("session", STORAGE_KEYS.auth, JSON.stringify(normalized));
  return true;
}

export function readAuthSession() {
  const raw = readStorageValue("session", STORAGE_KEYS.auth, "");

  if (!raw) {
    return null;
  }

  try {
    const normalized = normalizeAuthSession(JSON.parse(raw));
    if (!normalized) {
      clearAuthSession();
      return null;
    }
    return normalized;
  } catch {
    clearAuthSession();
    return null;
  }
}

export function clearAuthSession() {
  removeStorageValue("session", STORAGE_KEYS.auth);
}

export function getAuthToken() {
  const session = readAuthSession();
  return session && isFuture(session.expiresAt) ? session.sessionToken : "";
}

export function isAuthSessionValid() {
  return Boolean(getAuthToken());
}

function normalizeAuthSession(session) {
  if (
    !session ||
    typeof session !== "object" ||
    Array.isArray(session) ||
    typeof session.sessionToken !== "string" ||
    typeof session.expiresAt !== "string" ||
    !session.sessionToken.trim() ||
    !isFiniteDate(session.expiresAt)
  ) {
    return null;
  }

  return {
    sessionToken: session.sessionToken.trim(),
    expiresAt: session.expiresAt
  };
}

function isFiniteDate(value) {
  return Number.isFinite(Date.parse(value));
}

function isFuture(value) {
  return isFiniteDate(value) && Date.parse(value) > Date.now();
}
```

- [ ] **Step 4: Update API client error shape**

Replace `frontend/src/api/client.js` with:

```javascript
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export class ApiError extends Error {
  constructor(message, { status = 0, body = "" } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

export function buildUrl(path) {
  return `${API_BASE_URL}${path}`;
}

export async function requestJson(path, options = {}) {
  const response = await fetch(buildUrl(path), {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers ?? {})
    },
    ...options
  });

  if (!response.ok) {
    const body = await response.text();
    throw new ApiError(extractErrorMessage(body) || `Request failed with status ${response.status}`, {
      status: response.status,
      body
    });
  }

  return response.json();
}

export function extractErrorMessage(body) {
  if (!body) {
    return "";
  }

  try {
    const parsed = JSON.parse(body);
    return parsed?.message ?? parsed?.error ?? body;
  } catch {
    return body;
  }
}
```

- [ ] **Step 5: Attach bearer token in chat and model config APIs**

Replace `frontend/src/api/chat.js` with:

```javascript
import { useSseParser } from "../composables/useSseParser";
import { buildUrl, extractErrorMessage, requestJson, ApiError } from "./client";
import { getAuthToken } from "./sessionToken";

export function clearSession(sessionId) {
  return requestJson(`/api/chat/${sessionId}`, {
    method: "DELETE",
    headers: authHeaders()
  });
}

export async function streamChat({ sessionId, message, baseUrl, apiKey, model, signal, onEvent }) {
  const response = await fetch(buildUrl("/api/chat/stream"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...authHeaders()
    },
    body: JSON.stringify({ sessionId, message, baseUrl, apiKey, model }),
    signal
  });

  if (!response.ok || !response.body) {
    const body = await response.text();
    throw new ApiError(extractErrorMessage(body) || `Chat request failed with status ${response.status}`, {
      status: response.status,
      body
    });
  }

  const { consume } = useSseParser();
  await consume(response.body, onEvent);
}

function authHeaders() {
  const token = getAuthToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}
```

Replace `frontend/src/api/modelConfig.js` with:

```javascript
import { requestJson } from "./client";
import { getAuthToken } from "./sessionToken";

export function testModelConnection(modelConfig) {
  const token = getAuthToken();

  return requestJson("/api/model-config/test", {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: JSON.stringify(modelConfig)
  });
}
```

- [ ] **Step 6: Update API tests**

In `frontend/src/api/__tests__/modelConfig.spec.js`, add session setup before calling `testModelConnection`:

```javascript
window.sessionStorage.setItem(
  "agentpoc.authVerified",
  JSON.stringify({
    sessionToken: "signed-token",
    expiresAt: "2999-01-01T00:00:00.000Z"
  })
);
```

Then replace the existing `requestJsonMock` assertion with:

```javascript
expect(requestJsonMock).toHaveBeenCalledWith("/api/model-config/test", {
  headers: {
    Authorization: "Bearer signed-token"
  },
  body: JSON.stringify({
    apiKey: "sk-test",
    baseUrl: "https://api.example.com",
    model: "gpt-4.1-mini"
  }),
  method: "POST"
});
```

- [ ] **Step 7: Run frontend API tests and commit**

Run:

```powershell
cd frontend
npm.cmd run test -- src/api/__tests__/sessionToken.spec.js src/api/__tests__/modelConfig.spec.js
```

Expected: all selected tests pass with zero failures.

Commit:

```powershell
git add -- frontend/src/api/sessionToken.js frontend/src/api/__tests__/sessionToken.spec.js frontend/src/api/client.js frontend/src/api/chat.js frontend/src/api/modelConfig.js frontend/src/api/__tests__/modelConfig.spec.js
git commit -m "feat: attach session tokens to frontend APIs"
```

---

## Task 5: Frontend Login State and Auth-Expired UX

**Files:**

- Modify: `frontend/src/composables/useAuth.js`
- Modify: `frontend/src/composables/__tests__/useChat.spec.js`
- Modify: `frontend/src/composables/useChat.js`
- Modify: `frontend/src/views/ChatView.vue`
- Modify: `frontend/src/i18n/messages.js`
- Modify: `frontend/src/utils/modelErrors.js`
- Modify: `frontend/src/utils/__tests__/modelErrors.spec.js`

- [ ] **Step 1: Write failing model error mapping test**

In `frontend/src/utils/__tests__/modelErrors.spec.js`, add:

```javascript
it("maps expired app login sessions separately from upstream model auth", () => {
  expect(getModelErrorMessage("Login session expired.", { authExpired: "Please sign in again." }, "en")).toBe(
    "Please sign in again."
  );
});
```

- [ ] **Step 2: Update useChat auth-expiry test**

In `frontend/src/composables/__tests__/useChat.spec.js`, replace the existing `mountChatHarness` helper with:

```javascript
function mountChatHarness(modelSettings, overrides = {}) {
  const Harness = defineComponent({
    setup() {
      return useChat({
        authVerified: ref(true),
        dictionary: ref(dictionary),
        locale: ref("en"),
        modelSettings,
        openModelSettings: openModelSettingsMock,
        ...overrides
      });
    },
    template: "<div />"
  });

  return mount(Harness);
}
```

Add `authExpired` to the local `dictionary` object:

```javascript
  authExpired: "Your login session has expired. Please sign in again.",
```

Then add this test:

```javascript
it("calls the auth-expired handler when the chat stream returns 401", async () => {
  const onAuthExpired = vi.fn();
  streamChatMock.mockRejectedValueOnce(Object.assign(new Error("Login session expired."), { status: 401 }));
  const wrapper = mountChatHarness(
    ref({
      apiKey: "sk-test",
      baseUrl: "https://api.example.com",
      model: "gpt-4.1-mini"
    }),
    { onAuthExpired }
  );

  await wrapper.vm.submitPrompt("Hello");

  expect(onAuthExpired).toHaveBeenCalledTimes(1);
  expect(wrapper.vm.requestError).toBe(dictionary.authExpired);
});
```

- [ ] **Step 3: Run failing frontend auth UX tests**

Run:

```powershell
cd frontend
npm.cmd run test -- src/utils/__tests__/modelErrors.spec.js src/composables/__tests__/useChat.spec.js
```

Expected: tests fail because `authExpired` mapping and `onAuthExpired` handling are not implemented.

- [ ] **Step 4: Update login state management**

Replace `frontend/src/composables/useAuth.js` with:

```javascript
import { computed, ref } from "vue";
import { verifyAccessKey } from "../api/auth";
import { clearAuthSession, isAuthSessionValid, writeAuthSession } from "../api/sessionToken";

export function useAuth({ dictionary }) {
  const accessKey = ref("");
  const hasError = ref(false);
  const loginLoading = ref(false);
  const authVerified = ref(isAuthSessionValid());

  const loginError = computed(() =>
    hasError.value ? dictionary.value.loginError : ""
  );

  async function submitAccessKey() {
    if (!accessKey.value.trim() || loginLoading.value) {
      return;
    }

    hasError.value = false;
    loginLoading.value = true;

    try {
      const result = await verifyAccessKey(accessKey.value.trim());

      if (!result.success || !writeAuthSession(result)) {
        throw new Error(dictionary.value.loginError);
      }

      authVerified.value = true;
      accessKey.value = "";
    } catch {
      accessKey.value = "";
      hasError.value = true;
    } finally {
      loginLoading.value = false;
    }
  }

  function signOut() {
    authVerified.value = false;
    accessKey.value = "";
    hasError.value = false;
    clearAuthSession();
  }

  return {
    accessKey,
    authVerified,
    loginError,
    loginLoading,
    signOut,
    submitAccessKey
  };
}
```

- [ ] **Step 5: Add auth-expired copy**

In `frontend/src/i18n/messages.js`, add these keys to both locale dictionaries:

```javascript
authExpired: "登录状态已过期，请重新登录。",
```

```javascript
authExpired: "Your login session has expired. Please sign in again.",
```

Place them near existing login/auth strings.

- [ ] **Step 6: Map session expiration before model API-key errors**

In `frontend/src/utils/modelErrors.js`, add this check at the start of `getModelErrorMessage` after `normalized` is computed:

```javascript
  if (looksLikeSessionExpiredError(normalized)) {
    return dictionary?.authExpired ?? (locale === "zh"
      ? "登录状态已过期，请重新登录。"
      : "Your login session has expired. Please sign in again.");
  }
```

Add the helper:

```javascript
function looksLikeSessionExpiredError(normalized) {
  return normalized.includes("login session expired")
    || normalized.includes("session expired");
}
```

Keep upstream `401` model auth handling below this new branch.

- [ ] **Step 7: Wire auth-expired handling in useChat**

Change the `useChat` signature in `frontend/src/composables/useChat.js`:

```javascript
export function useChat({ authVerified, dictionary, locale, modelSettings, openModelSettings, onAuthExpired }) {
```

Add helper near `normalizeEventText` or inside `useChat`:

```javascript
function isAuthExpiredError(error) {
  return error?.status === 401 || String(error?.message ?? "").toLowerCase().includes("login session expired");
}
```

In the `catch (error)` block inside `submitPrompt`, before the `AbortError` branch, add:

```javascript
      if (isAuthExpiredError(error)) {
        requestError.value = dictionary.value.authExpired;
        onAuthExpired?.();
        return;
      }
```

The full catch branch should remain ordered as:

```javascript
    } catch (error) {
      if (isAuthExpiredError(error)) {
        requestError.value = dictionary.value.authExpired;
        onAuthExpired?.();
        return;
      }

      if (error.name === "AbortError") {
        requestError.value = locale.value === "zh" ? "请求已中断。" : "The request was interrupted.";
      } else {
        requestError.value = getModelErrorMessage(error, dictionary.value, locale.value);
      }
    } finally {
```

- [ ] **Step 8: Wire auth-expired handling in ChatView**

In `frontend/src/views/ChatView.vue`, change:

```javascript
defineEmits(["sign-out", "toggle-locale"]);
```

to:

```javascript
const emit = defineEmits(["sign-out", "toggle-locale"]);
```

Pass the callback into `useChat`:

```javascript
  onAuthExpired: () => emit("sign-out")
```

In `handleTestConnection`, inside `catch (error)`, before generic mapping:

```javascript
    if (error?.status === 401) {
      connectionMessage.value = props.dictionary.authExpired;
      connectionStatus.value = "error";
      emit("sign-out");
      return;
    }
```

Keep the `finally` block so `isTestingConnection` is reset.

- [ ] **Step 9: Run focused frontend auth UX tests and commit**

Run:

```powershell
cd frontend
npm.cmd run test -- src/utils/__tests__/modelErrors.spec.js src/composables/__tests__/useChat.spec.js src/api/__tests__/sessionToken.spec.js
```

Expected: all selected tests pass with zero failures.

Commit:

```powershell
git add -- frontend/src/composables/useAuth.js frontend/src/composables/useChat.js frontend/src/composables/__tests__/useChat.spec.js frontend/src/views/ChatView.vue frontend/src/i18n/messages.js frontend/src/utils/modelErrors.js frontend/src/utils/__tests__/modelErrors.spec.js
git commit -m "feat: handle frontend session expiry"
```

---

## Task 6: End-to-End Regression and Documentation

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Update README security boundary**

In `README.md`, update the security boundary section so it states:

```markdown
- `/api/auth/**`、静态资源、H2 Console 和健康检查保持演示白名单行为。
- `/api/chat/**` 与 `/api/model-config/**` 需要登录后返回的 `Authorization: Bearer <sessionToken>`。
- `/api/v1/data/**` 与 `/api/*/metrics`、`/api/*/details` 仍需要请求头 `X-API-Key`。
- 模型 API Key 仍保存在浏览器 `localStorage` 中，适合本地演示，不适合作为生产密钥托管方案。
- 模型 `Base URL` 会拒绝 localhost、内网地址和未进入允许列表的主机，允许列表可通过 `APP_MODEL_ALLOWED_HOSTS` 配置。
```

Update the auth API description to mention `sessionToken` and `expiresAt`.

- [ ] **Step 2: Run full backend verification**

Run:

```powershell
cd backend
mvn "-Dfrontend.skip=true" test
```

Expected: `BUILD SUCCESS` and `Failures: 0, Errors: 0`.

- [ ] **Step 3: Run full frontend tests**

Run:

```powershell
cd frontend
npm.cmd run test
```

Expected: all Vitest files pass.

- [ ] **Step 4: Run frontend build**

Run:

```powershell
cd frontend
npm.cmd run build
```

Expected: Vite build completes successfully and writes static assets to `backend/src/main/resources/static`.

- [ ] **Step 5: Review git diff for scoped changes**

Run:

```powershell
git status --short
git diff --stat
```

Expected: only files listed in this implementation plan are modified by this work, aside from generated frontend static assets if the repository tracks them.

- [ ] **Step 6: Commit documentation and final verified state**

Commit README updates:

```powershell
git add -- README.md
git commit -m "docs: document conservative security hardening"
```

---

## Final Verification Checklist

- [ ] `mvn "-Dfrontend.skip=true" test` passes.
- [ ] `npm.cmd run test` passes.
- [ ] `npm.cmd run build` passes.
- [ ] Login still uses the documented access-key flow.
- [ ] Successful login response includes `sessionToken` and `expiresAt`.
- [ ] Chat stream without bearer token returns `401`.
- [ ] Model connection test without bearer token returns `401`.
- [ ] Data APIs still reject missing `X-API-Key`.
- [ ] Unsafe model URLs are rejected before outbound calls.
- [ ] Existing SSE event names remain `progress`, `message`, `done`, and `error`.
