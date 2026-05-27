package com.brand.agentpoc.service;

import java.util.Map;
import java.util.Objects;

public record StepEvent(
        String traceId,
        int seq,
        StepType type,
        long ts,
        String status,
        String label,
        String detail,
        Map<String, Object> meta
) {
    public StepEvent {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        Objects.requireNonNull(type, "type is required");
        if (seq < 0) {
            throw new IllegalArgumentException("seq must be non-negative");
        }
        meta = meta == null ? Map.of() : Map.copyOf(meta);
    }

    public static StepEvent success(String traceId, int seq, StepType type,
                                     String label, String detail, Map<String, Object> meta) {
        return new StepEvent(traceId, seq, type, System.currentTimeMillis(),
                "success", label, detail, meta);
    }

    public static StepEvent failed(String traceId, int seq, StepType type,
                                    String label, String detail, Map<String, Object> meta) {
        return new StepEvent(traceId, seq, type, System.currentTimeMillis(),
                "failed", label, detail, meta);
    }
}
