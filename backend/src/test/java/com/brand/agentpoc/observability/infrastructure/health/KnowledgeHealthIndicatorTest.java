package com.brand.agentpoc.observability.infrastructure.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.knowledge.application.KnowledgeIndex;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class KnowledgeHealthIndicatorTest {

    @Test
    void reportsOutOfServiceUntilTheIndexIsInitialized() {
        KnowledgeIndex index = mock(KnowledgeIndex.class);

        var health = new KnowledgeHealthIndicator(index, new AppProperties()).health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("state", "not_initialized");
    }

    @Test
    void reportsUpForAnAvailableIndex() {
        KnowledgeIndex index = mock(KnowledgeIndex.class);
        when(index.isAvailable()).thenReturn(true);

        assertThat(new KnowledgeHealthIndicator(index, new AppProperties()).health().getStatus())
                .isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenTheIndexCheckFails() {
        KnowledgeIndex index = mock(KnowledgeIndex.class);
        when(index.isAvailable()).thenThrow(new IllegalStateException("vector store unavailable"));

        var health = new KnowledgeHealthIndicator(index, new AppProperties()).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("state", "check_failed")
                .containsEntry("reason", "IllegalStateException");
    }
}
