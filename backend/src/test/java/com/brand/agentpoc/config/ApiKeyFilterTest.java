package com.brand.agentpoc.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiKeyFilterTest {

    @Test
    void skipsInternalApiKeyForModelConfigBecauseSessionFilterProtectsIt() throws Exception {
        MockHttpServletResponse response = doFilter("POST", "/api/model-config/test", null, appProperties("configured-api-key"));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
    }

    @Test
    void allowsProtectedRequestsWithTheConfiguredInternalApiKey() throws Exception {
        MockHttpServletResponse response = doFilter("POST", "/api/v1/data/dealers", "configured-api-key", appProperties("configured-api-key"));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
    }

    @Test
    void rejectsProtectedRequestsWithoutTheInternalApiKey() throws Exception {
        MockHttpServletResponse response = doFilter("POST", "/api/v1/data/dealers", null, appProperties("configured-api-key"));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).isEqualTo("{\"code\":401,\"message\":\"Invalid API key\"}");
    }

    @Test
    void rejectsProtectedRequestsWhenInternalApiKeyConfigIsBlank() throws Exception {
        MockHttpServletResponse response = doFilter("POST", "/api/v1/data/dealers", "configured-api-key", appProperties(""));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    private MockHttpServletResponse doFilter(String method, String path, String apiKey, AppProperties appProperties) throws Exception {
        ApiKeyFilter filter = new ApiKeyFilter(appProperties);
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr("203.0.113.10");
        if (apiKey != null) {
            request.addHeader("X-API-Key", apiKey);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        });

        filter.doFilter(request, response, filterChain);
        return response;
    }

    private AppProperties appProperties(String apiKey) {
        AppProperties appProperties = new AppProperties();
        appProperties.getSecurity().setApiKey(apiKey);
        return appProperties;
    }
}
