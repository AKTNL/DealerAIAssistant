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
    private final Knowledge knowledge = new Knowledge();

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

    public Knowledge getKnowledge() {
        return knowledge;
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
