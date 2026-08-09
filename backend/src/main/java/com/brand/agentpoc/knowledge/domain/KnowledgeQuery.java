package com.brand.agentpoc.knowledge.domain;

public record KnowledgeQuery(String text, int topK) {

    public KnowledgeQuery {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Knowledge query must not be blank.");
        }
        if (topK < 1) {
            throw new IllegalArgumentException("Knowledge topK must be positive.");
        }
        text = text.trim();
    }
}
