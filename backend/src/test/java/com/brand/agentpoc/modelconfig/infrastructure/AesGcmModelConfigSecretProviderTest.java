package com.brand.agentpoc.modelconfig.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AesGcmModelConfigSecretProviderTest {

    private final AesGcmModelConfigSecretProvider provider =
            new AesGcmModelConfigSecretProvider("0123456789abcdef0123456789abcdef"
                    .getBytes(StandardCharsets.UTF_8));

    @Test
    void roundTripsASecretWithoutEmbeddingPlaintext() {
        String ciphertext = provider.protect(1L, "sk-tenant-secret");

        assertThat(ciphertext).doesNotContain("sk-tenant-secret");
        assertThat(provider.reveal(1L, ciphertext)).isEqualTo("sk-tenant-secret");
    }

    @Test
    void usesFreshNoncesAndTenantBoundAuthenticatedContext() {
        String first = provider.protect(1L, "same-secret");
        String second = provider.protect(2L, "same-secret");

        assertThat(first).isNotEqualTo(second);
        assertThatThrownBy(() -> provider.reveal(2L, first))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stored model secret cannot be decrypted.");
    }
}
