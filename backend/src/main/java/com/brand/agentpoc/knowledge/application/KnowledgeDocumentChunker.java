package com.brand.agentpoc.knowledge.application;

import com.brand.agentpoc.knowledge.domain.KnowledgeChunk;
import com.brand.agentpoc.knowledge.domain.KnowledgeDocument;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KnowledgeDocumentChunker {

    public static final int DEFAULT_MAX_CHUNK_CHARS = 1_200;
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+(.+)$");

    private final int maxChunkChars;

    public KnowledgeDocumentChunker() {
        this(DEFAULT_MAX_CHUNK_CHARS);
    }

    public KnowledgeDocumentChunker(int maxChunkChars) {
        if (maxChunkChars < 200) {
            throw new IllegalArgumentException("maxChunkChars must be at least 200.");
        }
        this.maxChunkChars = maxChunkChars;
    }

    public List<KnowledgeChunk> split(List<KnowledgeDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("At least one knowledge document is required.");
        }
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (KnowledgeDocument document : documents) {
            splitDocument(document, chunks);
        }
        return List.copyOf(chunks);
    }

    private void splitDocument(KnowledgeDocument document, List<KnowledgeChunk> target) {
        List<Section> sections = parseSections(document);
        for (Section section : sections) {
            int chunkIndex = 1;
            for (String content : splitSection(section.content())) {
                target.add(toChunk(document, section.title(), chunkIndex, content));
                chunkIndex++;
            }
        }
    }

    private List<Section> parseSections(KnowledgeDocument document) {
        List<Section> sections = new ArrayList<>();
        String currentTitle = document.title();
        StringBuilder currentContent = new StringBuilder();
        String normalizedContent = document.content().replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalizedContent.split("\n", -1)) {
            Matcher matcher = HEADING_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                addSection(sections, currentTitle, currentContent);
                currentTitle = matcher.group(1).trim();
                currentContent = new StringBuilder();
            } else {
                if (!currentContent.isEmpty()) {
                    currentContent.append('\n');
                }
                currentContent.append(line);
            }
        }
        addSection(sections, currentTitle, currentContent);
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("Knowledge document has no indexable content: " + document.documentId());
        }
        return sections;
    }

    private void addSection(List<Section> sections, String title, StringBuilder content) {
        String normalized = content.toString().trim();
        if (!normalized.isEmpty()) {
            sections.add(new Section(title, normalized));
        }
    }

    private List<String> splitSection(String content) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : content.split("\\n\\s*\\n")) {
            String normalized = paragraph.replaceAll("\\s+", " ").trim();
            if (normalized.isEmpty()) {
                continue;
            }
            if (normalized.length() > maxChunkChars) {
                flush(chunks, current);
                splitLongParagraph(chunks, normalized);
            } else if (current.isEmpty()) {
                current.append(normalized);
            } else if (current.length() + 2 + normalized.length() <= maxChunkChars) {
                current.append("\n\n").append(normalized);
            } else {
                flush(chunks, current);
                current.append(normalized);
            }
        }
        flush(chunks, current);
        return chunks;
    }

    private void splitLongParagraph(List<String> chunks, String paragraph) {
        for (int start = 0; start < paragraph.length(); start += maxChunkChars) {
            int end = Math.min(start + maxChunkChars, paragraph.length());
            chunks.add(paragraph.substring(start, end).trim());
        }
    }

    private void flush(List<String> chunks, StringBuilder current) {
        if (!current.isEmpty()) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }

    private KnowledgeChunk toChunk(
            KnowledgeDocument document,
            String section,
            int chunkIndex,
            String content
    ) {
        String chunkId = document.documentId()
                + ":" + document.version()
                + ":" + sectionKey(section)
                + ":" + chunkIndex;
        return new KnowledgeChunk(
                chunkId,
                document.documentId(),
                document.title(),
                document.type(),
                document.version(),
                document.source(),
                section,
                chunkIndex,
                content
        );
    }

    private String sectionKey(String section) {
        String normalized = Normalizer.normalize(section, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("^-|-$", "");
        return normalized.isBlank() ? "section" : normalized;
    }

    private record Section(String title, String content) {
    }
}
