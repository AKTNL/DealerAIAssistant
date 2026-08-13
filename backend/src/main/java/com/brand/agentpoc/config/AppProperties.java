package com.brand.agentpoc.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Auth auth = new Auth();
    private final Cors cors = new Cors();
    private final Excel excel = new Excel();
    private final Model model = new Model();
    private final Knowledge knowledge = new Knowledge();

    public Auth getAuth() {
        return auth;
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

    public Knowledge getKnowledge() {
        return knowledge;
    }

    public static class Auth {
        private Duration accessTokenTtl = Duration.ofMinutes(30);
        private Duration refreshTokenTtl = Duration.ofDays(7);
        private boolean cookieSecure;
        private String cookieSameSite = "Lax";
        private final Bootstrap bootstrap = new Bootstrap();

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }

        public String getCookieSameSite() {
            return cookieSameSite;
        }

        public void setCookieSameSite(String cookieSameSite) {
            this.cookieSameSite = cookieSameSite == null ? "" : cookieSameSite.trim();
        }

        public Bootstrap getBootstrap() {
            return bootstrap;
        }

        public static class Bootstrap {
            private boolean required;
            private String username = "";
            private String password = "";
            private String displayName = "System Administrator";

            public boolean isRequired() {
                return required;
            }

            public void setRequired(boolean required) {
                this.required = required;
            }

            public String getUsername() {
                return username;
            }

            public void setUsername(String username) {
                this.username = username == null ? "" : username.trim();
            }

            public String getPassword() {
                return password;
            }

            public void setPassword(String password) {
                this.password = password == null ? "" : password;
            }

            public String getDisplayName() {
                return displayName;
            }

            public void setDisplayName(String displayName) {
                this.displayName = displayName == null ? "" : displayName.trim();
            }
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
        private boolean fallbackEnabled = true;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public boolean isFallbackEnabled() {
            return fallbackEnabled;
        }

        public void setFallbackEnabled(boolean fallbackEnabled) {
            this.fallbackEnabled = fallbackEnabled;
        }
    }

    public static class Model {
        private String baseUrl = "";
        private String apiKey = "";
        private String name = "";
        private List<String> allowedHosts = List.of();
        private boolean allowPrivateHosts = false;
        private String secretKey = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey == null ? "" : apiKey.trim();
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name == null ? "" : name.trim();
        }

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

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey == null ? "" : secretKey.trim();
        }
    }

    public static class Knowledge {
        private String vectorStore = "memory";
        private String schemaName = "public";
        private String tableName = "knowledge_vector_store";
        private int dimensions = 1536;
        private double similarityThreshold = 0.45;

        public String getVectorStore() {
            return vectorStore;
        }

        public void setVectorStore(String vectorStore) {
            this.vectorStore = normalize(vectorStore, "memory");
        }

        public String getSchemaName() {
            return schemaName;
        }

        public void setSchemaName(String schemaName) {
            this.schemaName = normalize(schemaName, "public");
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = normalize(tableName, "knowledge_vector_store");
        }

        public int getDimensions() {
            return dimensions;
        }

        public void setDimensions(int dimensions) {
            this.dimensions = dimensions;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }

        private String normalize(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }
    }
}
