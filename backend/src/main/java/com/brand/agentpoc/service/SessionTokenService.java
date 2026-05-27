package com.brand.agentpoc.service;

import com.brand.agentpoc.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SessionTokenService {

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

    public Optional<TokenClaims> validate(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }

        String[] parts = token.split("\\.", 3);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            return Optional.empty();
        }

        try {
            String expectedSignature = sign(parts[1]);
            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }

            String payload = new String(BASE64_URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            String[] payloadParts = payload.split("\\.", 3);
            if (payloadParts.length != 3) {
                return Optional.empty();
            }

            Instant issuedAt = Instant.ofEpochSecond(Long.parseLong(payloadParts[0]));
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(payloadParts[1]));
            String subject = payloadParts[2];
            if (!Instant.now(clock).isBefore(expiresAt) || !StringUtils.hasText(subject)) {
                return Optional.empty();
            }

            return Optional.of(new TokenClaims(issuedAt, expiresAt, subject));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Duration resolveTtl() {
        Duration ttl = appProperties.getAuth().getSessionTtl();
        return ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofHours(8) : ttl;
    }

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

    private String encode(byte[] bytes) {
        return BASE64_URL_ENCODER.encodeToString(bytes);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public record TokenClaims(Instant issuedAt, Instant expiresAt, String subject) {
    }
}
