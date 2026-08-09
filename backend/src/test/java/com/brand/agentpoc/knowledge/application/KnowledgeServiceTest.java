package com.brand.agentpoc.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brand.agentpoc.knowledge.domain.KnowledgeChunk;
import com.brand.agentpoc.knowledge.domain.KnowledgeQuery;
import com.brand.agentpoc.knowledge.domain.KnowledgeSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeServiceTest {

    @Test
    void appliesDefaultTopKAndTrimsTheQuery() {
        RecordingIndex index = new RecordingIndex(true);
        KnowledgeService service = new KnowledgeService(index);

        KnowledgeSearchResult result = service.retrieve("  目标达成率口径  ", null);

        assertThat(result.noMatch()).isTrue();
        assertThat(index.lastQuery.text()).isEqualTo("目标达成率口径");
        assertThat(index.lastQuery.topK()).isEqualTo(KnowledgeService.DEFAULT_TOP_K);
    }

    @Test
    void rejectsBlankLongAndOutOfRangeQueries() {
        KnowledgeService service = new KnowledgeService(new RecordingIndex(true));

        assertThatThrownBy(() -> service.retrieve(" ", 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
        assertThatThrownBy(() -> service.retrieve("a".repeat(KnowledgeService.MAX_QUERY_LENGTH + 1), 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum length");
        assertThatThrownBy(() -> service.retrieve("目标", KnowledgeService.MAX_TOP_K + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK");
    }

    @Test
    void exposesUnavailableIndexAsAnExplicitFailure() {
        KnowledgeService service = new KnowledgeService(new RecordingIndex(false));

        assertThatThrownBy(() -> service.retrieve("目标口径", 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available");
    }

    private static final class RecordingIndex implements KnowledgeIndex {

        private final boolean available;
        private KnowledgeQuery lastQuery;

        private RecordingIndex(boolean available) {
            this.available = available;
        }

        @Override
        public void replaceAll(List<KnowledgeChunk> chunks) {
            // Not used by this application-service test.
        }

        @Override
        public KnowledgeSearchResult search(KnowledgeQuery query) {
            lastQuery = query;
            return KnowledgeSearchResult.from(query.text(), List.of());
        }

        @Override
        public boolean isAvailable() {
            return available;
        }
    }
}
