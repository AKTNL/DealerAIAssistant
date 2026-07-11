package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.dto.request.ChatRequest;
import com.brand.agentpoc.dto.request.ModelConfigRequest;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatOptions;
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

    @Test
    void usesReasoningFriendlyDefaultTokenBudget() {
        ModelConfigService service = serviceWithAllowedHosts(List.of("api.openai.com"));

        ChatModel chatModel = service.createChatModel(request("https://api.openai.com/v1"));

        OpenAiChatOptions options = (OpenAiChatOptions) chatModel.getDefaultOptions();
        assertThat(options.getMaxTokens()).isNull();
    }

    @Test
    void reportsMissingModelSettingsWhenRequestAndDefaultsAreEmpty() {
        ModelConfigService service = serviceWithAllowedHosts(List.of());

        assertThat(service.hasConfiguredModelSettings(chatRequest("", "", ""))).isFalse();
    }

    @Test
    void usesBackendDefaultsWhenChatRequestDoesNotProvideModelSettings() {
        AppProperties properties = propertiesWithModelDefaults(
                "https://api.openai.com/v1",
                "sk-default",
                "gpt-default"
        );
        properties.getModel().setAllowedHosts(List.of("api.openai.com"));
        ModelConfigService service = serviceWithProperties(properties);

        assertThat(service.hasConfiguredModelSettings(chatRequest("", "", ""))).isTrue();
        service.createChatModel(chatRequest("", "", ""));
    }

    @Test
    void letsRequestScopedModelSettingsOverrideBackendDefaults() {
        AppProperties properties = propertiesWithModelDefaults(
                "https://blocked.example.com/v1",
                "sk-default",
                "gpt-default"
        );
        properties.getModel().setAllowedHosts(List.of("api.openai.com"));
        ModelConfigService service = serviceWithProperties(properties);

        service.createChatModel(chatRequest(
                "https://api.openai.com/v1",
                "sk-request",
                "gpt-request"
        ));
    }

    private ModelConfigService serviceWithAllowedHosts(List<String> allowedHosts) {
        AppProperties properties = new AppProperties();
        properties.getModel().setAllowedHosts(allowedHosts);
        properties.getModel().setAllowPrivateHosts(false);

        return serviceWithProperties(properties);
    }

    private ModelConfigService serviceWithProperties(AppProperties properties) {
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

    private AppProperties propertiesWithModelDefaults(String baseUrl, String apiKey, String modelName) {
        AppProperties properties = new AppProperties();
        properties.getModel().setBaseUrl(baseUrl);
        properties.getModel().setApiKey(apiKey);
        properties.getModel().setName(modelName);
        properties.getModel().setAllowPrivateHosts(false);
        return properties;
    }

    private ChatRequest chatRequest(String baseUrl, String apiKey, String model) {
        return new ChatRequest("s1", "message", baseUrl, apiKey, model);
    }
}
