package com.brand.agentpoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;

class AgentPocApplicationStartupTest {

    @Test
    void startsWithoutGlobalOpenAiConfiguration() {
        assertThatCode(() -> {
            SpringApplication application = new SpringApplication(AgentPocApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);

            try (ConfigurableApplicationContext ignored = application.run(
                    "--spring.datasource.url=jdbc:h2:mem:startup-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                    "--spring.datasource.driver-class-name=org.h2.Driver",
                    "--spring.datasource.username=sa",
                    "--spring.datasource.password="
            )) {
                // Context close is part of the assertion.
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void applicationYamlDoesNotDefaultCredentials() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));

        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("app.auth.access-key")).isEqualTo("${APP_ACCESS_KEY:}");
        assertThat(properties.getProperty("app.auth.session-secret")).isEqualTo("${APP_SESSION_SECRET:}");
        assertThat(properties.getProperty("app.security.api-key")).isEqualTo("${APP_API_KEY:}");
    }

    @Test
    void prodProfileDisablesH2Console() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application-prod.yml"));

        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.h2.console.enabled")).isEqualTo("false");
    }

    @Test
    void readmeDoesNotDocumentRemovedDefaultCredentials() throws IOException {
        String readme = Files.readString(Path.of("../README.md"));

        assertThat(readme).doesNotContain("demo123", "poc-api-key", "从访问密钥派生");
    }
}
