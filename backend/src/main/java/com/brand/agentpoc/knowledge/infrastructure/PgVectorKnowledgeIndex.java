package com.brand.agentpoc.knowledge.infrastructure;

import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.knowledge.application.KnowledgeIndex;
import com.brand.agentpoc.knowledge.domain.KnowledgeChunk;
import com.brand.agentpoc.knowledge.domain.KnowledgeHit;
import com.brand.agentpoc.knowledge.domain.KnowledgeQuery;
import com.brand.agentpoc.knowledge.domain.KnowledgeSearchResult;
import com.brand.agentpoc.knowledge.domain.KnowledgeType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.knowledge", name = "vector-store", havingValue = "pgvector")
public class PgVectorKnowledgeIndex implements KnowledgeIndex {

    private static final String CATALOG_FILTER = "catalog == 'bundled'";
    private static final String CATALOG_VALUE = "bundled";
    private static final int MAX_EXCERPT_CHARS = 480;

    private final VectorStore vectorStore;
    private final double similarityThreshold;
    private final AtomicBoolean initialized = new AtomicBoolean();

    public PgVectorKnowledgeIndex(
            @Qualifier("knowledgeVectorStore") VectorStore vectorStore,
            AppProperties appProperties
    ) {
        this(vectorStore, appProperties.getKnowledge().getSimilarityThreshold());
    }

    PgVectorKnowledgeIndex(VectorStore vectorStore, double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public void replaceAll(List<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("Knowledge index requires at least one chunk.");
        }
        List<Document> documents = chunks.stream().map(this::toDocument).toList();
        vectorStore.delete(CATALOG_FILTER);
        vectorStore.add(documents);
        initialized.set(true);
    }

    @Override
    public KnowledgeSearchResult search(KnowledgeQuery query) {
        if (!initialized.get()) {
            throw new IllegalStateException("Knowledge index has not been initialized.");
        }
        SearchRequest request = SearchRequest.builder()
                .query(query.text())
                .topK(query.topK())
                .similarityThreshold(similarityThreshold)
                .filterExpression(CATALOG_FILTER)
                .build();
        List<KnowledgeHit> hits = vectorStore.similaritySearch(request).stream()
                .map(this::toHit)
                .toList();
        return KnowledgeSearchResult.from(query.text(), hits);
    }

    @Override
    public boolean isAvailable() {
        return initialized.get();
    }

    private Document toDocument(KnowledgeChunk chunk) {
        Map<String, Object> metadata = Map.of(
                "catalog", CATALOG_VALUE,
                "documentId", chunk.documentId(),
                "title", chunk.title(),
                "type", chunk.type().name(),
                "version", chunk.version(),
                "source", chunk.source(),
                "section", chunk.section(),
                "chunkIndex", chunk.chunkIndex()
        );
        return new Document(chunk.chunkId(), chunk.content(), metadata);
    }

    private KnowledgeHit toHit(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String text = document.getText();
        String excerpt = text.length() <= MAX_EXCERPT_CHARS
                ? text
                : text.substring(0, MAX_EXCERPT_CHARS).trim() + "…";
        Double documentScore = document.getScore();
        double score = documentScore == null ? 0.0 : Math.max(0.0, Math.min(1.0, documentScore));
        return new KnowledgeHit(
                document.getId(),
                metadataValue(metadata, "documentId"),
                metadataValue(metadata, "title"),
                KnowledgeType.valueOf(metadataValue(metadata, "type")),
                metadataValue(metadata, "version"),
                metadataValue(metadata, "source"),
                metadataValue(metadata, "section"),
                excerpt,
                score
        );
    }

    private String metadataValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("Knowledge vector metadata is incomplete.");
        }
        return value.toString();
    }
}
