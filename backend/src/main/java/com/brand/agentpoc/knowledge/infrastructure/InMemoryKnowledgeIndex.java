package com.brand.agentpoc.knowledge.infrastructure;

import com.brand.agentpoc.knowledge.application.KnowledgeIndex;
import com.brand.agentpoc.knowledge.domain.KnowledgeChunk;
import com.brand.agentpoc.knowledge.domain.KnowledgeHit;
import com.brand.agentpoc.knowledge.domain.KnowledgeQuery;
import com.brand.agentpoc.knowledge.domain.KnowledgeSearchResult;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.knowledge",
        name = "vector-store",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryKnowledgeIndex implements KnowledgeIndex {

    private static final double MIN_SCORE = 0.08;
    private static final int MAX_EXCERPT_CHARS = 480;
    private static final Pattern TOKEN_RUN = Pattern.compile("[\\p{IsHan}]+|[\\p{L}\\p{N}_-]+");

    private final ConcurrentMap<Long, List<KnowledgeChunk>> chunks = new ConcurrentHashMap<>();

    @Override
    public void replaceAll(List<KnowledgeChunk> replacement) {
        replaceAll(com.brand.agentpoc.tenant.domain.TenantScoped.DEFAULT_TENANT_ID, replacement);
    }

    @Override
    public void replaceAll(Long tenantId, List<KnowledgeChunk> replacement) {
        if (replacement == null || replacement.isEmpty()) {
            throw new IllegalArgumentException("Knowledge index requires at least one chunk.");
        }
        Set<String> chunkIds = new HashSet<>();
        for (KnowledgeChunk chunk : replacement) {
            if (!chunkIds.add(chunk.chunkId())) {
                throw new IllegalArgumentException("Duplicate knowledge chunk id: " + chunk.chunkId());
            }
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required.");
        }
        chunks.put(tenantId, List.copyOf(replacement));
    }

    @Override
    public KnowledgeSearchResult search(KnowledgeQuery query) {
        return search(query, com.brand.agentpoc.tenant.domain.TenantScoped.DEFAULT_TENANT_ID);
    }

    @Override
    public KnowledgeSearchResult search(KnowledgeQuery query, Long tenantId) {
        List<KnowledgeChunk> snapshot = chunks.get(tenantId);
        if (snapshot == null) {
            throw new IllegalStateException("Knowledge index has not been initialized.");
        }
        Set<String> queryTokens = tokenize(query.text());
        if (queryTokens.isEmpty()) {
            return KnowledgeSearchResult.from(query.text(), List.of());
        }
        List<KnowledgeHit> matches = new ArrayList<>();
        for (KnowledgeChunk chunk : snapshot) {
            double score = score(query.text(), queryTokens, chunk);
            if (score >= MIN_SCORE) {
                matches.add(toHit(chunk, score));
            }
        }
        List<KnowledgeHit> hits = matches.stream()
                .sorted(Comparator.comparingDouble(KnowledgeHit::score).reversed()
                        .thenComparing(KnowledgeHit::documentId)
                        .thenComparing(KnowledgeHit::chunkId))
                .limit(query.topK())
                .toList();
        return KnowledgeSearchResult.from(query.text(), hits);
    }

    @Override
    public boolean isAvailable() {
        return !chunks.isEmpty();
    }

    private double score(String query, Set<String> queryTokens, KnowledgeChunk chunk) {
        Set<String> titleTokens = tokenize(chunk.title());
        Set<String> sectionTokens = tokenize(chunk.section());
        Set<String> contentTokens = tokenize(chunk.content());
        double matchedWeight = 0.0;
        for (String token : queryTokens) {
            if (titleTokens.contains(token)) {
                matchedWeight += 3.0;
            } else if (sectionTokens.contains(token)) {
                matchedWeight += 2.0;
            } else if (contentTokens.contains(token)) {
                matchedWeight += 1.0;
            }
        }
        double normalized = matchedWeight / (queryTokens.size() * 3.0);
        String normalizedQuery = normalize(query);
        String normalizedContent = normalize(chunk.title() + " " + chunk.section() + " " + chunk.content());
        if (normalizedQuery.length() > 1 && normalizedContent.contains(normalizedQuery)) {
            normalized += 0.2;
        }
        return Math.min(1.0, normalized);
    }

    private Set<String> tokenize(String value) {
        String normalized = normalize(value);
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = TOKEN_RUN.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group();
            if (isHanRun(token)) {
                addHanGrams(tokens, token);
            } else if (token.length() > 1) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean isHanRun(String token) {
        return token.codePoints().allMatch(codePoint -> Character.UnicodeScript.of(codePoint)
                == Character.UnicodeScript.HAN);
    }

    private void addHanGrams(Set<String> tokens, String token) {
        int[] codePoints = token.codePoints().toArray();
        if (codePoints.length == 1) {
            tokens.add(token);
            return;
        }
        for (int index = 0; index < codePoints.length - 1; index++) {
            tokens.add(new String(codePoints, index, 2));
        }
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private KnowledgeHit toHit(KnowledgeChunk chunk, double score) {
        String excerpt = chunk.content().length() <= MAX_EXCERPT_CHARS
                ? chunk.content()
                : chunk.content().substring(0, MAX_EXCERPT_CHARS).trim() + "…";
        return new KnowledgeHit(
                chunk.chunkId(),
                chunk.documentId(),
                chunk.title(),
                chunk.type(),
                chunk.version(),
                chunk.source(),
                chunk.section(),
                excerpt,
                score
        );
    }
}
