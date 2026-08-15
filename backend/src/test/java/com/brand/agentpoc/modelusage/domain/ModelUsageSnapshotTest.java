package com.brand.agentpoc.modelusage.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

class ModelUsageSnapshotTest {

    @Test
    void distinguishesUnknownPartialAndKnownUsage() {
        assertThat(ModelUsageSnapshot.from(null).tokenState()).isEqualTo(ModelTokenState.UNKNOWN);
        assertThat(snapshot(12, null, 12).tokenState()).isEqualTo(ModelTokenState.PARTIAL);
        assertThat(snapshot(12, 8, 20).tokenState()).isEqualTo(ModelTokenState.KNOWN);
        assertThat(snapshot(-1, -2, -3)).isEqualTo(ModelUsageSnapshot.unknown());
    }

    @Test
    void mergesStreamingMetadataAsCumulativeMaximums() {
        ModelUsageSnapshot merged = snapshot(10, 2, 12)
                .mergeCumulative(snapshot(10, 6, 16))
                .mergeCumulative(snapshot(null, 4, null));

        assertThat(merged).isEqualTo(new ModelUsageSnapshot(10L, 6L, 16L));
    }

    private ModelUsageSnapshot snapshot(Integer input, Integer output, Integer total) {
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(input);
        when(usage.getCompletionTokens()).thenReturn(output);
        when(usage.getTotalTokens()).thenReturn(total);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(usage)
                .build();
        return ModelUsageSnapshot.from(new ChatResponse(List.of(), metadata));
    }
}
