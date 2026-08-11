package com.brand.agentpoc.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthSecurityConfigurationTest {

    @Test
    void createsAnAdaptiveSelfDescribingPasswordEncoder() {
        SecurityErrorWriter writer = new SecurityErrorWriter(new ObjectMapper());
        AuthSecurityConfiguration configuration = new AuthSecurityConfiguration(
                new JsonAuthenticationEntryPoint(writer),
                new JsonAccessDeniedHandler(writer)
        );

        PasswordEncoder encoder = configuration.passwordEncoder();
        String encoded = encoder.encode("temporary-password");

        assertThat(encoded).startsWith("{");
        assertThat(encoded).doesNotContain("temporary-password");
        assertThat(encoder.matches("temporary-password", encoded)).isTrue();
    }
}
