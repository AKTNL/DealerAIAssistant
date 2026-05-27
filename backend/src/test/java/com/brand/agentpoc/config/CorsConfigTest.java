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
