package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(service.validate(issued.token())).isPresent();

        SessionTokenService.TokenClaims claims = service.validate(issued.token()).orElseThrow();
        assertThat(claims.issuedAt()).isEqualTo(now);
        assertThat(claims.expiresAt()).isEqualTo(now.plus(Duration.ofHours(8)));
        assertThat(claims.subject()).isNotBlank();
    }

    @Test
    void rejectsTamperedTokens() {
        SessionTokenService service = new SessionTokenService(appProperties("secret-value", Duration.ofHours(8)), clock);
        String token = service.issueToken().token();
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(service.validate(tampered)).isEmpty();
    }

    @Test
    void rejectsExpiredTokens() {
        AppProperties properties = appProperties("secret-value", Duration.ofMinutes(5));
        SessionTokenService issuer = new SessionTokenService(properties, clock);
        String token = issuer.issueToken().token();

        Clock afterExpiry = Clock.fixed(now.plus(Duration.ofMinutes(6)), ZoneOffset.UTC);
        SessionTokenService validator = new SessionTokenService(properties, afterExpiry);

        assertThat(validator.validate(token)).isEmpty();
    }

    @Test
    void requiresConfiguredSessionSecretToIssueToken() {
        AppProperties properties = appProperties("", Duration.ofHours(8));
        SessionTokenService service = new SessionTokenService(properties, clock);

        assertThatThrownBy(service::issueToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.auth.session-secret is required");
    }

    @Test
    void rejectsTokenValidationWhenSessionSecretIsBlank() {
        SessionTokenService service = new SessionTokenService(appProperties("", Duration.ofHours(8)), clock);

        assertThat(service.validate("v1.payload.signature")).isEmpty();
    }

    private AppProperties appProperties(String secret, Duration ttl) {
        AppProperties properties = new AppProperties();
        properties.getAuth().setSessionSecret(secret);
        properties.getAuth().setSessionTtl(ttl);
        return properties;
    }
}
