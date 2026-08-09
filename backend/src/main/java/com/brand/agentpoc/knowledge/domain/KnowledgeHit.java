package com.brand.agentpoc.knowledge.domain;

import java.util.Objects;

public record KnowledgeHit(
        String chunkId,
        String documentId,
        String title,
        KnowledgeType type,
        String version,
        String source,
        String section,
        String excerpt,
        double score
) {

    public KnowledgeHit {
        chunkId = requireText(chunkId, "chunkId");
        documentId = requireText(documentId, "documentId");
        title = requireText(title, "title");
        type = Objects.requireNonNull(type, "type must not be null.");
        version = requireText(version, "version");
        source = requireText(source, "source");
        section = requireText(section, "section");
        excerpt = requireText(excerpt, "excerpt");
        if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score must be between 0 and 1.");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return value.trim();
    }
}
