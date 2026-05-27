package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.brand.agentpoc.config.AppProperties;
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

    private ModelConfigService serviceWithAllowedHosts(List<String> allowedHosts) {
        AppProperties properties = new AppProperties();
        properties.getModel().setAllowedHosts(allowedHosts);
        properties.getModel().setAllowPrivateHosts(false);

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
}
