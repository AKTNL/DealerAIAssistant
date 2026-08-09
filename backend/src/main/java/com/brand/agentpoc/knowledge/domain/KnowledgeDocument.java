package com.brand.agentpoc.knowledge.domain;

import java.util.Objects;

public record KnowledgeDocument(
        String documentId,
        String title,
        KnowledgeType type,
        String version,
        String source,
        String content
) {

    public KnowledgeDocument {
        documentId = requireText(documentId, "documentId");
        title = requireText(title, "title");
        type = Objects.requireNonNull(type, "type must not be null.");
        version = requireText(version, "version");
        source = requireText(source, "source");
        content = requireText(content, "content");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return value.trim();
    }
}
