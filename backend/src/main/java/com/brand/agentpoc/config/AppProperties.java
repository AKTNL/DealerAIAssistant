package com.brand.agentpoc.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Auth auth = new Auth();
    private final Security security = new Security();
    private final Cors cors = new Cors();
    private final Excel excel = new Excel();
    private final Model model = new Model();

    public Auth getAuth() {
        return auth;
    }

    public Security getSecurity() {
        return security;
    }

    public Cors getCors() {
        return cors;
    }

    public Excel getExcel() {
        return excel;
    }

    public Model getModel() {
        return model;
    }

    public static class Auth {
        private String accessKey = "";
        private String sessionSecret = "";
        private Duration sessionTtl = Duration.ofHours(8);

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSessionSecret() {
            return sessionSecret;
        }

        public void setSessionSecret(String sessionSecret) {
            this.sessionSecret = sessionSecret;
        }

        public Duration getSessionTtl() {
            return sessionTtl;
        }

        public void setSessionTtl(Duration sessionTtl) {
            this.sessionTtl = sessionTtl;
        }
    }

    public static class Security {
        private String apiKey = "";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173");

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins == null
                    ? List.of()
                    : allowedOrigins.stream()
                            .filter(value -> value != null && !value.isBlank())
                            .map(String::trim)
                            .toList();
        }
    }

    public static class Excel {
        private String path = "classpath:Sample Data.xlsx";

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static class Model {
        private List<String> allowedHosts = List.of();
        private boolean allowPrivateHosts = false;

        public List<String> getAllowedHosts() {
            return allowedHosts;
        }

        public void setAllowedHosts(List<String> allowedHosts) {
            this.allowedHosts = allowedHosts == null
                    ? List.of()
                    : allowedHosts.stream()
                            .filter(value -> value != null && !value.isBlank())
                            .map(String::trim)
                            .toList();
        }

        public boolean isAllowPrivateHosts() {
            return allowPrivateHosts;
        }

        public void setAllowPrivateHosts(boolean allowPrivateHosts) {
            this.allowPrivateHosts = allowPrivateHosts;
        }
    }
}
