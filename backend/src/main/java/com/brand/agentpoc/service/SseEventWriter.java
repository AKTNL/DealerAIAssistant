package com.brand.agentpoc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;

class SseEventWriter {

    private static final ObjectMapper SSE_EVENT_MAPPER = new ObjectMapper();

    void writeStepEvent(BufferedWriter writer, StepEvent step) throws IOException {
        String json = SSE_EVENT_MAPPER.writeValueAsString(Map.of(
                "trace_id", step.traceId(),
                "seq", step.seq(),
                "type", step.type().name(),
                "ts", step.ts(),
                "status", step.status(),
                "label", step.label() != null ? step.label() : "",
                "detail", step.detail() != null ? step.detail() : "",
                "meta", step.meta() != null ? step.meta() : Map.of()
        ));
        writeEvent(writer, "step", json);
    }

    void writeAnalysisMetadataEvent(BufferedWriter writer, AnalyticsMetadata metadata) throws IOException {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        writeEvent(writer, "analysis_metadata", SSE_EVENT_MAPPER.writeValueAsString(metadata));
    }

    void writeEvent(BufferedWriter writer, String event, String data) throws IOException {
        writer.write("event: " + event);
        writer.newLine();
        for (String line : data.split("\\R", -1)) {
            writer.write("data: " + line);
            writer.newLine();
        }
        writer.newLine();
        writer.flush();
    }

    void writeChunkedEvent(BufferedWriter writer, String event, String chunk) throws IOException {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        writeEvent(writer, event, chunk);
    }
}
