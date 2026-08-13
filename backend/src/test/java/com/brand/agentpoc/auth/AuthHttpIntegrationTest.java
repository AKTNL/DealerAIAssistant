package com.brand.agentpoc.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.brand.agentpoc.AgentPocApplication;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthSessionRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

class AuthHttpIntegrationTest {

    private static final String ORIGIN = "http://localhost:5173";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void exercisesRestrictedLoginPasswordChangeRefreshReplayAndRbac() throws Exception {
        SpringApplication application = new SpringApplication(AgentPocApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);

        try (ConfigurableApplicationContext context = application.run(
                "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:auth-http;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--app.excel.path=classpath:missing-auth-http.xlsx",
                "--app.auth.bootstrap.username=Initial.Admin",
                "--app.auth.bootstrap.password=temporary-password",
                "--app.auth.bootstrap.display-name=Initial Administrator"
        )) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            HttpResponse<String> temporaryLogin = post(port, "/api/auth/login", mapper.writeValueAsString(Map.of(
                    "username", "INITIAL.ADMIN",
                    "password", "temporary-password"
            )), null, null);
            assertThat(temporaryLogin.statusCode()).isEqualTo(200);
            String temporaryAccess = accessToken(mapper, temporaryLogin);
            String temporaryRefresh = refreshCookie(temporaryLogin);
            assertThat(temporaryLogin.headers().firstValue("Set-Cookie").orElseThrow())
                    .contains("HttpOnly")
                    .contains("SameSite=Lax");

            HttpResponse<String> me = get(port, "/api/auth/me", temporaryAccess);
            assertThat(me.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(me.body()).at("/data/mustChangePassword").asBoolean()).isTrue();
            assertThat(get(port, "/api/dashboard", temporaryAccess).statusCode()).isEqualTo(403);

            HttpResponse<String> passwordChange = post(port, "/api/auth/password", mapper.writeValueAsString(Map.of(
                    "currentPassword", "temporary-password",
                    "newPassword", "permanent-password-2"
            )), temporaryAccess, null);
            assertThat(passwordChange.statusCode()).isEqualTo(200);
            assertThat(get(port, "/api/auth/me", temporaryAccess).statusCode()).isEqualTo(401);

            HttpResponse<String> permanentLogin = post(port, "/api/auth/login", mapper.writeValueAsString(Map.of(
                    "username", "initial.admin",
                    "password", "permanent-password-2"
            )), null, null);
            String permanentAccess = accessToken(mapper, permanentLogin);
            String oldRefresh = refreshCookie(permanentLogin);
            assertThat(get(port, "/api/dashboard", permanentAccess).statusCode()).isEqualTo(200);
            assertThat(get(port, "/api/admin/users", permanentAccess).statusCode()).isEqualTo(200);
            assertThat(post(port, "/api/admin/roles", mapper.writeValueAsString(Map.of(
                    "roleKey", "OPS_VIEWER",
                    "displayName", "Operations Viewer",
                    "permissions", new String[]{"DASHBOARD_READ"}
            )), permanentAccess, null).statusCode()).isEqualTo(403);
            assertThat(post(port, "/api/admin/users", mapper.writeValueAsString(Map.of(
                    "username", "viewer.user",
                    "displayName", "Viewer User",
                    "temporaryPassword", "viewer-temporary-1",
                    "roles", new String[]{"VIEWER"}
            )), permanentAccess, null).statusCode()).isEqualTo(200);
            assertThat(post(port, "/api/admin/users", mapper.writeValueAsString(Map.of(
                    "username", "chat.user",
                    "displayName", "Chat User",
                    "temporaryPassword", "chat-temporary-1",
                    "roles", new String[]{"ANALYST"}
            )), permanentAccess, null).statusCode()).isEqualTo(200);
            JsonNode usersBody = mapper.readTree(get(port, "/api/admin/users", permanentAccess).body());
            JsonNode chatUser = findByText(usersBody.at("/data"), "username", "chat.user");
            long chatUserId = chatUser.path("id").asLong();
            long chatUserVersion = chatUser.path("version").asLong();
            assertThat(get(port, "/api/admin/users/" + chatUserId + "/sessions", permanentAccess).statusCode())
                    .isEqualTo(200);
            assertThat(post(
                    port,
                    "/api/admin/users/" + chatUserId + "/sessions/revoke",
                    "",
                    permanentAccess,
                    null
            ).statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(get(port, "/api/admin/audit-events", permanentAccess).body()).at("/data").isArray())
                    .isTrue();
            assertThat(patch(
                    port,
                    "/api/admin/users/" + chatUserId + "/enabled",
                    mapper.writeValueAsString(Map.of("enabled", true, "version", chatUserVersion + 99)),
                    permanentAccess
            ).statusCode()).isEqualTo(409);
            assertThat(patch(port, "/api/admin/users/1/enabled", "{\"enabled\":false}", permanentAccess)
                    .statusCode()).isEqualTo(409);

            HttpResponse<String> chatTemporaryLogin = post(port, "/api/auth/login", mapper.writeValueAsString(Map.of(
                    "username", "chat.user", "password", "chat-temporary-1"
            )), null, null);
            String chatTemporaryAccess = accessToken(mapper, chatTemporaryLogin);
            assertThat(post(port, "/api/auth/password", mapper.writeValueAsString(Map.of(
                    "currentPassword", "chat-temporary-1", "newPassword", "chat-permanent-2"
            )), chatTemporaryAccess, null).statusCode()).isEqualTo(200);
            String chatAccess = accessToken(mapper, post(port, "/api/auth/login", mapper.writeValueAsString(Map.of(
                    "username", "chat.user", "password", "chat-permanent-2"
            )), null, null));
            assertThat(post(port, "/api/chat", mapper.writeValueAsString(Map.of(
                    "sessionId", "permission-test", "message", "目标达成率怎么样？"
            )), chatAccess, null).statusCode()).isEqualTo(403);
            assertThat(post(port, "/api/chat", mapper.writeValueAsString(Map.of(
                    "sessionId", "permission-test", "message", "你好"
            )), chatAccess, null).statusCode()).isEqualTo(200);

            HttpResponse<String> logoutLogin = post(port, "/api/auth/login", mapper.writeValueAsString(Map.of(
                    "username", "initial.admin", "password", "permanent-password-2"
            )), null, null);
            String logoutAccess = accessToken(mapper, logoutLogin);
            String logoutRefresh = refreshCookie(logoutLogin);
            assertThat(postWithOrigin(
                    port,
                    "/api/auth/logout",
                    "",
                    null,
                    logoutRefresh,
                    "https://untrusted.example"
            ).statusCode()).isEqualTo(403);
            assertThat(get(port, "/api/auth/me", logoutAccess).statusCode()).isEqualTo(200);
            assertThat(post(port, "/api/auth/logout", "", null, logoutRefresh).statusCode()).isEqualTo(200);
            assertThat(get(port, "/api/auth/me", logoutAccess).statusCode()).isEqualTo(401);

            HttpResponse<String> refreshed = post(port, "/api/auth/refresh", "", null, oldRefresh);
            assertThat(refreshed.statusCode()).isEqualTo(200);
            String refreshedAccess = accessToken(mapper, refreshed);
            assertThat(refreshedAccess).isNotEqualTo(permanentAccess);
            assertThat(get(port, "/api/auth/me", permanentAccess).statusCode()).isEqualTo(401);

            HttpResponse<String> replay = post(port, "/api/auth/refresh", "", null, oldRefresh);
            assertThat(replay.statusCode()).isEqualTo(401);
            assertThat(get(port, "/api/auth/me", refreshedAccess).statusCode()).isEqualTo(401);

            String logoutAllAccessOne = accessToken(mapper, post(
                    port,
                    "/api/auth/login",
                    mapper.writeValueAsString(Map.of(
                            "username", "initial.admin", "password", "permanent-password-2"
                    )),
                    null,
                    null
            ));
            String logoutAllAccessTwo = accessToken(mapper, post(
                    port,
                    "/api/auth/login",
                    mapper.writeValueAsString(Map.of(
                            "username", "initial.admin", "password", "permanent-password-2"
                    )),
                    null,
                    null
            ));
            assertThat(post(port, "/api/auth/logout-all", "", logoutAllAccessOne, null).statusCode()).isEqualTo(200);
            assertThat(get(port, "/api/auth/me", logoutAllAccessOne).statusCode()).isEqualTo(401);
            assertThat(get(port, "/api/auth/me", logoutAllAccessTwo).statusCode()).isEqualTo(401);

            AuthSessionRepository sessions = context.getBean(AuthSessionRepository.class);
            AuthUserRepository users = context.getBean(AuthUserRepository.class);
            AuthAuditEventRepository audits = context.getBean(AuthAuditEventRepository.class);
            assertThat(sessions.findAll())
                    .allSatisfy(session -> {
                        assertThat(session.getAccessTokenHash()).hasSize(64).doesNotContain(temporaryAccess);
                        assertThat(session.getRefreshTokenHash()).hasSize(64).doesNotContain(temporaryRefresh);
                    });
            assertThat(users.findByUsernameIgnoreCase("initial.admin").getFirst().getPasswordHash())
                    .doesNotContain("temporary-password")
                    .doesNotContain("permanent-password-2");
            assertThat(audits.findAll())
                    .allSatisfy(event -> assertThat(event.getDetailCode())
                            .doesNotContain("chat-temporary-1", "temporary-password", "permanent-password-2"));
        }
    }

    private HttpResponse<String> get(int port, String path, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(port, path))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(
            int port,
            String path,
            String json,
            String accessToken,
            String refreshCookie
    ) throws Exception {
        return postWithOrigin(port, path, json, accessToken, refreshCookie, ORIGIN);
    }

    private HttpResponse<String> postWithOrigin(
            int port,
            String path,
            String json,
            String accessToken,
            String refreshCookie,
            String origin
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(port, path))
                .header("Content-Type", "application/json")
                .header("Origin", origin);
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        if (refreshCookie != null) {
            builder.header("Cookie", refreshCookie);
        }
        return httpClient.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> patch(int port, String path, String json, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(port, path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(int port, String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private String accessToken(ObjectMapper mapper, HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.body());
        return body.at("/data/accessToken").asText();
    }

    private String refreshCookie(HttpResponse<String> response) {
        return response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    }

    private JsonNode findByText(JsonNode array, String field, String value) {
        for (JsonNode item : array) {
            if (value.equals(item.path(field).asText())) {
                return item;
            }
        }
        throw new IllegalStateException("Expected record was not found.");
    }
}
