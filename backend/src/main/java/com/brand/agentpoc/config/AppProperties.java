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
    private final Notification notification = new Notification();
    private final Observability observability = new Observability();

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

    public Notification getNotification() {
        return notification;
    }

    public Observability getObservability() {
        return observability;
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

    public static class Notification {
        private String secretKey = "";
        private List<String> smtpAllowedHosts = List.of();
        private Duration smtpConnectionTimeout = Duration.ofSeconds(5);
        private Duration smtpReadTimeout = Duration.ofSeconds(10);
        private Duration smtpWriteTimeout = Duration.ofSeconds(5);
        private int maxMessageBytes = 262144;

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey == null ? "" : secretKey.trim();
        }

        public List<String> getSmtpAllowedHosts() {
            return smtpAllowedHosts;
        }

        public void setSmtpAllowedHosts(List<String> smtpAllowedHosts) {
            this.smtpAllowedHosts = smtpAllowedHosts == null
                    ? List.of()
                    : smtpAllowedHosts.stream()
                            .filter(value -> value != null && !value.isBlank())
                            .map(value -> value.trim().toLowerCase(java.util.Locale.ROOT))
                            .distinct()
                            .sorted()
                            .toList();
        }

        public Duration getSmtpConnectionTimeout() {
            return smtpConnectionTimeout;
        }

        public void setSmtpConnectionTimeout(Duration smtpConnectionTimeout) {
            this.smtpConnectionTimeout = smtpConnectionTimeout;
        }

        public Duration getSmtpReadTimeout() {
            return smtpReadTimeout;
        }

        public void setSmtpReadTimeout(Duration smtpReadTimeout) {
            this.smtpReadTimeout = smtpReadTimeout;
        }

        public Duration getSmtpWriteTimeout() {
            return smtpWriteTimeout;
        }

        public void setSmtpWriteTimeout(Duration smtpWriteTimeout) {
            this.smtpWriteTimeout = smtpWriteTimeout;
        }

        public int getMaxMessageBytes() {
            return maxMessageBytes;
        }

        public void setMaxMessageBytes(int maxMessageBytes) {
            this.maxMessageBytes = maxMessageBytes;
        }
    }

    public static class Observability {
        private Duration slowQueryThreshold = Duration.ofMillis(500);
        private int jobBacklogDegradedThreshold = 100;
        private int deliveryBacklogDegradedThreshold = 100;
        private int permanentFailureDegradedThreshold = 1;

        public Duration getSlowQueryThreshold() {
            return slowQueryThreshold;
        }

        public void setSlowQueryThreshold(Duration slowQueryThreshold) {
            this.slowQueryThreshold = slowQueryThreshold;
        }

        public int getJobBacklogDegradedThreshold() {
            return jobBacklogDegradedThreshold;
        }

        public void setJobBacklogDegradedThreshold(int jobBacklogDegradedThreshold) {
            this.jobBacklogDegradedThreshold = jobBacklogDegradedThreshold;
        }

        public int getDeliveryBacklogDegradedThreshold() {
            return deliveryBacklogDegradedThreshold;
        }

        public void setDeliveryBacklogDegradedThreshold(int deliveryBacklogDegradedThreshold) {
            this.deliveryBacklogDegradedThreshold = deliveryBacklogDegradedThreshold;
        }

        public int getPermanentFailureDegradedThreshold() {
            return permanentFailureDegradedThreshold;
        }

        public void setPermanentFailureDegradedThreshold(int permanentFailureDegradedThreshold) {
            this.permanentFailureDegradedThreshold = permanentFailureDegradedThreshold;
        }
    }
}
