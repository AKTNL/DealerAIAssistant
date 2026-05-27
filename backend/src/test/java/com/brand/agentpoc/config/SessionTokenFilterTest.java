package com.brand.agentpoc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.service.SessionTokenService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SessionTokenFilterTest {

    private final SessionTokenService sessionTokenService = mock(SessionTokenService.class);
    private final SessionTokenFilter filter = new SessionTokenFilter(sessionTokenService);

    @Test
    void rejectsChatRequestsWithoutToken() throws Exception {
        MockHttpServletResponse response = doFilter("POST", "/api/chat/stream", null);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).isEqualTo("{\"code\":401,\"message\":\"Login session expired.\"}");
    }

    @Test
    void rejectsChatRequestsWithInvalidToken() throws Exception {
        when(sessionTokenService.validate("bad-token")).thenReturn(Optional.empty());

        MockHttpServletResponse response = doFilter("POST", "/api/chat/stream", "Bearer bad-token");

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    @Test
    void rejectsChatRequestsWithUnsupportedAuthorizationScheme() throws Exception {
        MockHttpServletResponse response = doFilter("POST", "/api/chat/stream", "Basic abc");

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    @Test
    void allowsModelConfigRequestsWithValidToken() throws Exception {
        when(sessionTokenService.validate("good-token")).thenReturn(Optional.of(new SessionTokenService.TokenClaims(
                Instant.parse("2026-05-21T08:00:00Z"),
                Instant.parse("2026-05-21T16:00:00Z"),
                "token-subject"
        )));

        FilterResult result = doFilterWithSubject("POST", "/api/model-config/test", "Bearer good-token");

        assertThat(result.response().getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
        assertThat(result.tokenSubject()).isEqualTo("token-subject");
    }

    @Test
    void skipsAuthAndHealthEndpoints() throws Exception {
        MockHttpServletResponse response = doFilter("POST", "/api/auth/verify", null);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
    }

    private MockHttpServletResponse doFilter(String method, String path, String authorization) throws Exception {
        return doFilterWithSubject(method, path, authorization).response();
    }

    private FilterResult doFilterWithSubject(String method, String path, String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> tokenSubject = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                tokenSubject.set((String) req.getAttribute(SessionTokenFilter.TOKEN_SUBJECT_ATTRIBUTE));
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        });

        filter.doFilter(request, response, chain);
        return new FilterResult(response, tokenSubject.get());
    }

    private record FilterResult(MockHttpServletResponse response, String tokenSubject) {
    }
}
