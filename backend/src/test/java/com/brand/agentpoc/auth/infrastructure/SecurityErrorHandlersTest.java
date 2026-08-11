package com.brand.agentpoc.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class SecurityErrorHandlersTest {

    private JsonAuthenticationEntryPoint authenticationEntryPoint;
    private JsonAccessDeniedHandler accessDeniedHandler;

    @BeforeEach
    void setUp() {
        SecurityErrorWriter writer = new SecurityErrorWriter(new ObjectMapper());
        authenticationEntryPoint = new JsonAuthenticationEntryPoint(writer);
        accessDeniedHandler = new JsonAccessDeniedHandler(writer);
    }

    @Test
    void writesTheUnifiedAuthenticationErrorEnvelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticationEntryPoint.commence(
                new MockHttpServletRequest(),
                response,
                new BadCredentialsException("sensitive internal detail")
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).isEqualTo(
                "{\"code\":401,\"message\":\"Authentication required.\"}"
        );
        assertThat(response.getContentAsString()).doesNotContain("sensitive internal detail");
    }

    @Test
    void writesTheUnifiedAccessDeniedEnvelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(
                new MockHttpServletRequest(),
                response,
                new AccessDeniedException("sensitive internal detail")
        );

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).isEqualTo(
                "{\"code\":403,\"message\":\"Access denied.\"}"
        );
        assertThat(response.getContentAsString()).doesNotContain("sensitive internal detail");
    }
}
