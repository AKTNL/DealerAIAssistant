package com.brand.agentpoc.reporting.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brand.agentpoc.config.AppProperties;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AesGcmNotificationSecretProviderTest {

    @Test
    void ciphertextIsBoundToTenantNotificationContext() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        AesGcmNotificationSecretProvider provider = new AesGcmNotificationSecretProvider(key);

        String ciphertext = provider.protect(7L, "smtp-password");

        assertThat(ciphertext).doesNotContain("smtp-password");
        assertThat(provider.reveal(7L, ciphertext)).isEqualTo("smtp-password");
        assertThatThrownBy(() -> provider.reveal(8L, ciphertext))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void preservesSignificantWhitespaceInSmtpPasswords() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 9);
        AesGcmNotificationSecretProvider provider = new AesGcmNotificationSecretProvider(key);

        String ciphertext = provider.protect(7L, " leading-and-trailing ");

        assertThat(provider.reveal(7L, ciphertext)).isEqualTo(" leading-and-trailing ");
    }

    @Test
    void productionRequiresIndependentNotificationKey() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new AesGcmNotificationSecretProvider(new AppProperties(), environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_NOTIFICATION_SECRET_KEY");
    }
}
