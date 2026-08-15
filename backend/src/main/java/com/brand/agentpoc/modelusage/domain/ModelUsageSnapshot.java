package com.brand.agentpoc.modelusage.domain;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

public record ModelUsageSnapshot(Long inputTokens, Long outputTokens, Long totalTokens) {

    public static ModelUsageSnapshot unknown() {
        return new ModelUsageSnapshot(null, null, null);
    }

    public static ModelUsageSnapshot from(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return unknown();
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return unknown();
        }
        return new ModelUsageSnapshot(
                nonNegative(usage.getPromptTokens()),
                nonNegative(usage.getCompletionTokens()),
                nonNegative(usage.getTotalTokens())
        );
    }

    public ModelUsageSnapshot mergeCumulative(ModelUsageSnapshot other) {
        if (other == null) {
            return this;
        }
        return new ModelUsageSnapshot(
                maximum(inputTokens, other.inputTokens),
                maximum(outputTokens, other.outputTokens),
                maximum(totalTokens, other.totalTokens)
        );
    }

    public ModelTokenState tokenState() {
        if (inputTokens != null && outputTokens != null) {
            return ModelTokenState.KNOWN;
        }
        if (inputTokens != null || outputTokens != null || totalTokens != null) {
            return ModelTokenState.PARTIAL;
        }
        return ModelTokenState.UNKNOWN;
    }

    private static Long nonNegative(Integer value) {
        return value == null || value < 0 ? null : value.longValue();
    }

    private static Long maximum(Long left, Long right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.max(left, right);
    }
}
