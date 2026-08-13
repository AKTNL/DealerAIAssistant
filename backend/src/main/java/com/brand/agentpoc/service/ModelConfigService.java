package com.brand.agentpoc.service;

import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.dto.request.ChatRequest;
import com.brand.agentpoc.dto.request.ModelConfigRequest;
import com.brand.agentpoc.dto.response.ModelConfigTestResponse;
import com.brand.agentpoc.modelconfig.application.TenantModelConfigRegistry;
import com.brand.agentpoc.modelconfig.application.TenantModelConfigRegistry.ModelConfigView;
import com.brand.agentpoc.modelconfig.application.TenantModelConfigRegistry.ResolvedModelConfig;
import io.micrometer.observation.ObservationRegistry;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

@Service
public class ModelConfigService {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigService.class);

    private final ToolCallingManager toolCallingManager;
    private final RetryTemplate retryTemplate;
    private final ObservationRegistry observationRegistry;
    private final AppProperties appProperties;
    private final TenantModelConfigRegistry tenantConfigRegistry;

    @Autowired
    public ModelConfigService(
            ToolCallingManager toolCallingManager,
            RetryTemplate retryTemplate,
            ObservationRegistry observationRegistry,
            AppProperties appProperties,
            TenantModelConfigRegistry tenantConfigRegistry
    ) {
        this.toolCallingManager = toolCallingManager;
        this.retryTemplate = retryTemplate;
        this.observationRegistry = observationRegistry;
        this.appProperties = appProperties;
        this.tenantConfigRegistry = tenantConfigRegistry;
    }

    public ModelConfigService(
            ToolCallingManager toolCallingManager,
            RetryTemplate retryTemplate,
            ObservationRegistry observationRegistry,
            AppProperties appProperties
    ) {
        this(toolCallingManager, retryTemplate, observationRegistry, appProperties, null);
    }

    public ChatModel createChatModel(ChatRequest request) {
        return createChatModel(resolveModelConfig(request));
    }

    public ChatModel createChatModel(ChatRequest request, Long tenantId) {
        ResolvedModelConfig tenantConfig = resolveTenantConfig(tenantId);
        if (tenantConfig != null) {
            return createChatModel(tenantConfig.settings(), tenantConfig.allowedHosts());
        }
        return createChatModel(request);
    }

    public ChatModel createChatModel(ModelConfigRequest request) {
        return createChatModel(request, null);
    }

    private ChatModel createChatModel(ModelConfigRequest request, List<String> tenantAllowedHosts) {
        validate(request, tenantAllowedHosts);

        String baseUrl = request.baseUrl().trim();
        String completionsPath = resolveCompletionsPath(baseUrl);

        log.info("Creating OpenAiApi — baseUrl={}, completionsPath={}, model={}",
                baseUrl, completionsPath, request.model().trim());

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(request.apiKey().trim())
                .completionsPath(completionsPath)
                .build();

        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                .model(request.model().trim())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(defaultOptions)
                .toolCallingManager(toolCallingManager)
                .retryTemplate(retryTemplate)
                .observationRegistry(observationRegistry)
                .build();
    }

    public boolean hasConfiguredModelSettings(ChatRequest request) {
        ModelConfigRequest resolved = resolveModelConfig(request);
        return hasText(resolved.baseUrl()) && hasText(resolved.apiKey()) && hasText(resolved.model());
    }

    public boolean hasConfiguredModelSettings(ChatRequest request, Long tenantId) {
        return resolveTenantConfig(tenantId) != null || hasConfiguredModelSettings(request);
    }

    public ModelConfigView saveTenantConfig(
            Long tenantId,
            ModelConfigRequest request,
            List<String> allowedHosts
    ) {
        requireRegistry();
        validateTenantSave(request, allowedHosts);
        return tenantConfigRegistry.save(tenantId, request, allowedHosts);
    }

    public java.util.Optional<ModelConfigView> tenantConfigView(Long tenantId) {
        requireRegistry();
        return tenantConfigRegistry.view(tenantId);
    }

    public void deleteTenantConfig(Long tenantId) {
        requireRegistry();
        tenantConfigRegistry.delete(tenantId);
    }

    private ModelConfigRequest resolveModelConfig(ChatRequest request) {
        AppProperties.Model defaults = appProperties.getModel();
        return new ModelConfigRequest(
                firstText(request == null ? null : request.baseUrl(), defaults.getBaseUrl()),
                firstText(request == null ? null : request.apiKey(), defaults.getApiKey()),
                firstText(request == null ? null : request.model(), defaults.getName())
        );
    }

    private String firstText(String preferred, String fallback) {
        if (hasText(preferred)) {
            return preferred.trim();
        }
        if (hasText(fallback)) {
            return fallback.trim();
        }
        return "";
    }

    private String resolveCompletionsPath(String baseUrl) {
        String path = URI.create(baseUrl).getPath();
        if (path != null && path.matches(".*/v\\d+/?$")) {
            return "/chat/completions";
        }
        return "/v1/chat/completions";
    }

    public ModelConfigTestResponse testConnection(ModelConfigRequest request) {
        return testConnection(request, com.brand.agentpoc.tenant.domain.TenantScoped.DEFAULT_TENANT_ID);
    }

    public ModelConfigTestResponse testConnection(ModelConfigRequest request, Long tenantId) {
        return testConnection(request, tenantId, null);
    }

    public ModelConfigTestResponse testConnection(
            ModelConfigRequest request,
            Long tenantId,
            List<String> allowedHosts
    ) {
        if (tenantId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant context is required.");
        }
        try {
            ModelConfigRequest resolved = resolveTestConfig(request, tenantId);
            ChatModel chatModel = createChatModel(resolved, allowedHosts);
            String reply = chatModel.call("Reply with OK.");

            if (reply == null || reply.isBlank()) {
                return new ModelConfigTestResponse(false, "Connection test failed.");
            }

            return new ModelConfigTestResponse(true, "Connection test succeeded.");
        } catch (IllegalArgumentException exception) {
            return new ModelConfigTestResponse(false, exception.getMessage());
        } catch (Exception exception) {
            log.warn("Model connection test failed — baseUrl={}, model={}, error={}",
                    request.baseUrl(), request.model(), exception.getMessage(), exception);
            return new ModelConfigTestResponse(false, describeFailure(exception));
        }
    }

    private void validate(ModelConfigRequest request, List<String> tenantAllowedHosts) {
        if (request == null) {
            throw new IllegalArgumentException("Model settings are required.");
        }

        if (!hasText(request.baseUrl()) || !hasText(request.apiKey()) || !hasText(request.model())) {
            throw new IllegalArgumentException("Base URL, API key, and model are required.");
        }

        validateBaseUrl(request.baseUrl().trim(), tenantAllowedHosts);
    }

    private ModelConfigRequest resolveTestConfig(ModelConfigRequest request, Long tenantId) {
        if (request == null || hasText(request.apiKey()) || tenantConfigRegistry == null) {
            return request;
        }
        ResolvedModelConfig stored = tenantConfigRegistry.resolve(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Model API key is required."));
        return new ModelConfigRequest(request.baseUrl(), stored.settings().apiKey(), request.model());
    }

    private void validateTenantSave(ModelConfigRequest request, List<String> allowedHosts) {
        if (request == null || !hasText(request.baseUrl()) || !hasText(request.model())) {
            throw new IllegalArgumentException("Base URL and model are required.");
        }
        if (allowedHosts == null || allowedHosts.stream().noneMatch(this::hasText)) {
            throw new IllegalArgumentException("At least one allowed model host is required.");
        }
        validateBaseUrl(request.baseUrl().trim(), allowedHosts);
    }

    private void validateBaseUrl(String baseUrl, List<String> tenantAllowedHosts) {
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme();

            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Invalid base URL.");
            }

            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("Invalid base URL.");
            }

            validateAllowedModelHost(uri, tenantAllowedHosts);
        } catch (IllegalArgumentException exception) {
            if ("Invalid base URL.".equals(exception.getMessage())
                    || "Model base URL is not allowed.".equals(exception.getMessage())) {
                throw exception;
            }

            throw new IllegalArgumentException("Invalid base URL.", exception);
        }
    }

    private void validateAllowedModelHost(URI uri, List<String> tenantAllowedHosts) {
        String host = uri.getHost();
        if (!appProperties.getModel().isAllowPrivateHosts() && isUnsafeHost(host)) {
            throw new IllegalArgumentException("Model base URL is not allowed.");
        }

        List<String> allowedHosts = tenantAllowedHosts == null
                ? appProperties.getModel().getAllowedHosts()
                : tenantAllowedHosts;
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            return;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        boolean allowed = allowedHosts.stream()
                .filter(this::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(pattern -> hostMatches(pattern, normalizedHost));

        if (!allowed) {
            throw new IllegalArgumentException("Model base URL is not allowed.");
        }
    }

    private boolean hostMatches(String pattern, String host) {
        if (pattern.startsWith("*.")) {
            String suffix = pattern.substring(1);
            return host.endsWith(suffix) && host.length() > suffix.length();
        }
        return host.equals(pattern);
    }

    private boolean isUnsafeHost(String host) {
        if (!hasText(host)) {
            return true;
        }

        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalized) || normalized.endsWith(".localhost")) {
            return true;
        }

        String candidate = normalized;
        if (candidate.startsWith("[") && candidate.endsWith("]")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }

        if (!looksLikeIpLiteral(candidate)) {
            return false;
        }

        try {
            InetAddress address = InetAddress.getByName(candidate);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress();
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean looksLikeIpLiteral(String value) {
        return value.matches("\\d{1,3}(?:\\.\\d{1,3}){3}") || value.contains(":");
    }

    private String describeFailure(Exception exception) {
        String message = exception.getMessage();

        if (!hasText(message)) {
            return "Connection test failed. (no details)";
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("401") || normalized.contains("403")
                || normalized.contains("unauthorized") || normalized.contains("authentication")) {
            return "Authentication failed. Upstream response: " + message;
        }
        if (normalized.contains("timeout")) {
            return "Connection timed out.";
        }
        if (normalized.contains("unknownhost") || normalized.contains("connection refused")
                || normalized.contains("i/o error") || normalized.contains("unreachable")) {
            return "Upstream service is not reachable. Details: " + message;
        }

        return message;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ResolvedModelConfig resolveTenantConfig(Long tenantId) {
        if (tenantId == null || tenantConfigRegistry == null) {
            return null;
        }
        return tenantConfigRegistry.resolve(tenantId).orElse(null);
    }

    private void requireRegistry() {
        if (tenantConfigRegistry == null) {
            throw new IllegalStateException("Tenant model configuration persistence is unavailable.");
        }
    }
}
