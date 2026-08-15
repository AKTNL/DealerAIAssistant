package com.brand.agentpoc.observability.infrastructure.web;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import java.io.IOException;
import org.slf4j.MDC;

public final class AsyncTraceContext {

    private static final ContextSnapshotFactory SNAPSHOT_FACTORY = ContextSnapshotFactory.builder().build();

    private final ContextSnapshot snapshot;
    private final String requestId;

    private AsyncTraceContext(ContextSnapshot snapshot, String requestId) {
        this.snapshot = snapshot;
        this.requestId = requestId;
    }

    public static AsyncTraceContext capture(String requestId) {
        return new AsyncTraceContext(SNAPSHOT_FACTORY.captureAll(), requestId);
    }

    public void run(IoOperation operation) throws IOException {
        try (ContextSnapshot.Scope ignored = snapshot.setThreadLocals();
             MDC.MDCCloseable requestScope = MDC.putCloseable("requestId", requestId)) {
            operation.run();
        }
    }

    @FunctionalInterface
    public interface IoOperation {
        void run() throws IOException;
    }
}
