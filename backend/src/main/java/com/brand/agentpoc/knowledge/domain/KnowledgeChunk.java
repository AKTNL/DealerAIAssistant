package com.brand.agentpoc.knowledge.domain;

import java.util.Objects;

public record KnowledgeChunk(
        String chunkId,
        String documentId,
        String title,
        KnowledgeType type,
        String version,
        String source,
        String section,
        int chunkIndex,
        String content
) {

    public KnowledgeChunk {
        chunkId = requireText(chunkId, "chunkId");
        documentId = requireText(documentId, "documentId");
        title = requireText(title, "title");
        type = Objects.requireNonNull(type, "type must not be null.");
        version = requireText(version, "version");
        source = requireText(source, "source");
        section = requireText(section, "section");
        if (chunkIndex < 1) {
            throw new IllegalArgumentException("chunkIndex must be at least 1.");
        }
        content = requireText(content, "content");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return value.trim();
    }
}
