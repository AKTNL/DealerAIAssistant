package com.brand.agentpoc.observability.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class AsyncTraceContextTest {

    @Test
    void restoresTheRequestIdOnAnAsyncWorkerAndCleansItAfterward() throws Exception {
        AsyncTraceContext context = AsyncTraceContext.capture("request-async");
        AtomicReference<String> inside = new AtomicReference<>();
        AtomicReference<String> after = new AtomicReference<>();

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> future = executor.submit(() -> {
                try {
                    context.run(() -> inside.set(MDC.get("requestId")));
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
                after.set(MDC.get("requestId"));
            });
            future.get();
        }

        assertThat(inside.get()).isEqualTo("request-async");
        assertThat(after.get()).isNull();
    }
}
