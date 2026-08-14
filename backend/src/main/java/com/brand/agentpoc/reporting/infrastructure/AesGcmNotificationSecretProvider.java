package com.brand.agentpoc.reporting.infrastructure;

import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.reporting.application.NotificationSecretProvider;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AesGcmNotificationSecretProvider implements NotificationSecretProvider {

    private static final Logger log = LoggerFactory.getLogger(AesGcmNotificationSecretProvider.class);
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int SECRET_VERSION = 1;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public AesGcmNotificationSecretProvider(AppProperties appProperties, Environment environment) {
        this(resolveKey(appProperties.getNotification().getSecretKey(), environment));
    }

    AesGcmNotificationSecretProvider(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("Notification secret key must contain exactly 32 bytes.");
        }
        this.key = new SecretKeySpec(Arrays.copyOf(keyBytes, keyBytes.length), "AES");
    }

    @Override
    public String protect(Long tenantId, String secret) {
        requireTenant(tenantId);
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("SMTP password is required.");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(tenantId));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Notification secret encryption failed.", exception);
        }
    }

    @Override
    public String reveal(Long tenantId, String protectedSecret) {
        requireTenant(tenantId);
        if (protectedSecret == null || protectedSecret.isBlank()) {
            throw new IllegalStateException("Stored notification secret is missing.");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(protectedSecret);
            if (payload.length <= NONCE_BYTES) {
                throw new IllegalStateException("Stored notification secret is invalid.");
            }
            byte[] nonce = Arrays.copyOfRange(payload, 0, NONCE_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, NONCE_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(tenantId));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Stored notification secret cannot be decrypted.", exception);
        }
    }

    @Override
    public int version() {
        return SECRET_VERSION;
    }

    private byte[] aad(Long tenantId) {
        return ("tenant-notification:" + tenantId).getBytes(StandardCharsets.UTF_8);
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required.");
        }
    }

    private static byte[] resolveKey(String configured, Environment environment) {
        if (configured != null && !configured.isBlank()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(configured.trim());
                if (decoded.length != KEY_BYTES) {
                    throw new IllegalStateException("APP_NOTIFICATION_SECRET_KEY must decode to exactly 32 bytes.");
                }
                return decoded;
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("APP_NOTIFICATION_SECRET_KEY must be valid Base64.", exception);
            }
        }
        if (environment != null && Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            throw new IllegalStateException("APP_NOTIFICATION_SECRET_KEY is required in production.");
        }
        byte[] generated = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(generated);
        log.warn("Notification secret key is ephemeral; persisted SMTP credentials will not survive restart.");
        return generated;
    }
}
