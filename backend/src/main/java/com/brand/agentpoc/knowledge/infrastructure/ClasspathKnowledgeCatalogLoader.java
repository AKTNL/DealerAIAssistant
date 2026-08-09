package com.brand.agentpoc.knowledge.infrastructure;

import com.brand.agentpoc.knowledge.application.KnowledgeDocumentSource;
import com.brand.agentpoc.knowledge.domain.KnowledgeDocument;
import com.brand.agentpoc.knowledge.domain.KnowledgeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class ClasspathKnowledgeCatalogLoader implements KnowledgeDocumentSource {

    static final String DEFAULT_CATALOG = "classpath:/knowledge/catalog.json";
    private static final String ALLOWED_RESOURCE_PREFIX = "classpath:/knowledge/";
    private static final Pattern DOCUMENT_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");
    private static final Pattern VERSION_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public ClasspathKnowledgeCatalogLoader(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<KnowledgeDocument> load() {
        Catalog catalog = readCatalog();
        if (catalog.documents() == null || catalog.documents().isEmpty()) {
            throw new IllegalStateException("Knowledge catalog must contain at least one document.");
        }
        Set<String> documentIds = new HashSet<>();
        return catalog.documents().stream()
                .map(entry -> loadDocument(entry, documentIds))
                .toList();
    }

    private Catalog readCatalog() {
        Resource resource = resourceLoader.getResource(DEFAULT_CATALOG);
        if (!resource.exists()) {
            throw new IllegalStateException("Knowledge catalog is missing.");
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, Catalog.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Knowledge catalog cannot be read.", exception);
        }
    }

    private KnowledgeDocument loadDocument(CatalogEntry entry, Set<String> documentIds) {
        if (entry == null) {
            throw new IllegalStateException("Knowledge catalog contains an empty entry.");
        }
        validateIdentity(entry);
        String documentId = entry.documentId().trim();
        if (!documentIds.add(documentId)) {
            throw new IllegalStateException("Knowledge catalog contains a missing or duplicate documentId.");
        }
        validateResource(entry.resource());
        validateSource(entry.source(), entry.resource());
        Resource resource = resourceLoader.getResource(entry.resource());
        if (!resource.exists()) {
            throw new IllegalStateException("Knowledge document is missing: " + documentId);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return new KnowledgeDocument(
                    documentId,
                    entry.title(),
                    entry.type(),
                    entry.version(),
                    entry.source(),
                    content
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Knowledge document cannot be read: " + documentId, exception);
        }
    }

    private void validateIdentity(CatalogEntry entry) {
        if (entry.documentId() == null
                || !DOCUMENT_ID_PATTERN.matcher(entry.documentId().trim()).matches()) {
            throw new IllegalStateException("Knowledge catalog contains an invalid documentId.");
        }
        if (entry.version() == null || !VERSION_PATTERN.matcher(entry.version().trim()).matches()) {
            throw new IllegalStateException("Knowledge catalog contains an invalid version.");
        }
    }

    private void validateResource(String resource) {
        if (resource == null
                || !resource.startsWith(ALLOWED_RESOURCE_PREFIX)
                || !resource.endsWith(".md")
                || resource.contains("..")) {
            throw new IllegalStateException("Knowledge resources must be controlled classpath Markdown files.");
        }
    }

    private void validateSource(String source, String resource) {
        String expectedSource = resource.substring("classpath:/".length());
        if (source == null || !source.trim().equals(expectedSource)) {
            throw new IllegalStateException("Knowledge source must identify the controlled Markdown resource.");
        }
    }

    private record Catalog(List<CatalogEntry> documents) {
    }

    private record CatalogEntry(
            String documentId,
            String title,
            KnowledgeType type,
            String version,
            String source,
            String resource
    ) {
    }
}
