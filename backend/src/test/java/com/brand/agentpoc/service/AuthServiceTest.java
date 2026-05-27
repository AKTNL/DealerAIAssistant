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
    void rejectsNullInputAccessKey() {
        AuthService service = new AuthService(appProperties("configured-key"));

        assertThat(service.verifyAccessKey(null)).isFalse();
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
