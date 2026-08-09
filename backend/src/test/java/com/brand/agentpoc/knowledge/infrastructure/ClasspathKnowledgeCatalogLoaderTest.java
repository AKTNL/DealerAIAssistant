package com.brand.agentpoc.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.knowledge.domain.KnowledgeDocument;
import com.brand.agentpoc.knowledge.domain.KnowledgeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class ClasspathKnowledgeCatalogLoaderTest {

    @Test
    void loadsTheControlledBundledCatalogWithVersionedSources() {
        ClasspathKnowledgeCatalogLoader loader = new ClasspathKnowledgeCatalogLoader(
                new DefaultResourceLoader(),
                new ObjectMapper()
        );

        List<KnowledgeDocument> documents = loader.load();

        assertThat(documents).hasSize(4);
        assertThat(documents).extracting(KnowledgeDocument::type)
                .containsExactly(
                        KnowledgeType.KPI_DEFINITION,
                        KnowledgeType.SALES_SOP,
                        KnowledgeType.DEALER_POLICY,
                        KnowledgeType.PRODUCT_RULE
                );
        assertThat(documents).allSatisfy(document -> {
            assertThat(document.version()).isEqualTo("2026.08");
            assertThat(document.source()).startsWith("knowledge/");
            assertThat(document.content()).isNotBlank();
        });
    }

    @Test
    void rejectsDuplicateDocumentIdentifiers() throws IOException {
        String catalog = """
                {"documents":[
                  {"documentId":"duplicate","title":"A","type":"KPI_DEFINITION","version":"1.0","source":"knowledge/test.md","resource":"classpath:/knowledge/test.md"},
                  {"documentId":"duplicate","title":"B","type":"SALES_SOP","version":"1.1","source":"knowledge/test.md","resource":"classpath:/knowledge/test.md"}
                ]}
                """;

        ClasspathKnowledgeCatalogLoader loader = loader(catalog, "# Approved\n\nContent");

        assertThatThrownBy(loader::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate documentId");
    }

    @Test
    void rejectsInvalidDocumentIdentifiersAndVersions() throws IOException {
        ClasspathKnowledgeCatalogLoader invalidId = loader(
                catalog("../outside", "1.0", "knowledge/test.md"),
                "# Approved\n\nContent"
        );
        ClasspathKnowledgeCatalogLoader invalidVersion = loader(
                catalog("approved", "version with spaces", "knowledge/test.md"),
                "# Approved\n\nContent"
        );

        assertThatThrownBy(invalidId::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid documentId");
        assertThatThrownBy(invalidVersion::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid version");
    }

    @Test
    void rejectsEmptyDocumentsAndCitationSourceDrift() throws IOException {
        ClasspathKnowledgeCatalogLoader emptyDocument = loader(
                catalog("approved", "1.0", "knowledge/test.md"),
                "   "
        );
        ClasspathKnowledgeCatalogLoader mismatchedSource = loader(
                catalog("approved", "1.0", "knowledge/other.md"),
                "# Approved\n\nContent"
        );

        assertThatThrownBy(emptyDocument::load)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content must not be blank");
        assertThatThrownBy(mismatchedSource::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("controlled Markdown resource");
    }

    private ClasspathKnowledgeCatalogLoader loader(String catalog, String document) throws IOException {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        Resource catalogResource = resource(catalog);
        Resource documentResource = resource(document);
        when(resourceLoader.getResource(ClasspathKnowledgeCatalogLoader.DEFAULT_CATALOG))
                .thenReturn(catalogResource);
        when(resourceLoader.getResource("classpath:/knowledge/test.md"))
                .thenReturn(documentResource);
        return new ClasspathKnowledgeCatalogLoader(resourceLoader, new ObjectMapper());
    }

    private Resource resource(String content) throws IOException {
        Resource resource = mock(Resource.class);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8)
        ));
        return resource;
    }

    private String catalog(String documentId, String version, String source) {
        return """
                {"documents":[
                  {"documentId":"%s","title":"Approved","type":"KPI_DEFINITION","version":"%s","source":"%s","resource":"classpath:/knowledge/test.md"}
                ]}
                """.formatted(documentId, version, source);
    }
}
