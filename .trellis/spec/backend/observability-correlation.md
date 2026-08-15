# Observability and Correlation

> Executable contracts for request correlation, Micrometer observations, asynchronous trace propagation, safe fields, and optional OTLP export.

## Scenario: Vendor-Neutral Operational Telemetry

### 1. Scope / Trigger

- Trigger: any change to HTTP/SSE correlation, Agent or model calls, import/report operations, scheduled job runners, Actuator exposure, metrics, tracing, structured logs, or OTLP configuration.
- This is a cross-layer infrastructure contract. Servlet filters, application services, scheduler threads, Spring AI, Micrometer, runtime configuration, and production network exposure must remain aligned.

### 2. Signatures

- Request headers: inbound/outbound `X-Request-ID`; outbound `X-Trace-ID`.
- Safe correlation format: `[A-Za-z0-9][A-Za-z0-9._:-]{0,127}`.
- Shared resolver: `RequestCorrelation.requestId(HttpServletRequest)` and `RequestCorrelation.traceId(HttpServletRequest)`.
- SSE boundary: `AsyncTraceContext.capture(requestId).run(IoOperation)`.
- Business API: `OperationalTelemetry.observe(OperationalEvent, Function<EventContext,T>)` and `observeVoid(OperationalEvent, Consumer<EventContext>)`.
- Correlation API: `EventContext.correlate(CorrelationField, Object)`; outcome API: `EventContext.outcome(OperationalOutcome)`.
- Observation names:
  - `agentpoc.data.import`
  - `agentpoc.report.generate`
  - `agentpoc.report.job.execute`
  - `agentpoc.report.delivery`
  - `agentpoc.report.collaboration.notification`
- Runtime keys:
  - `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` (default `0.1`)
  - `MANAGEMENT_OTLP_TRACING_EXPORT_ENABLED` (default `false`)
  - `MANAGEMENT_OTLP_TRACING_ENDPOINT`
  - `MANAGEMENT_OTLP_TRACING_CONNECT_TIMEOUT`
  - `MANAGEMENT_OTLP_TRACING_TIMEOUT`
  - `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE`
  - `LOGGING_STRUCTURED_FORMAT_CONSOLE` (production default `logstash`)

### 3. Contracts

- `RequestCorrelationFilter` validates or generates one request ID, stores it as a request attribute and MDC value, and returns both correlation headers. Malformed or overlong untrusted IDs are replaced, never truncated and accepted.
- W3C is the produced propagation format. W3C, B3 single, and B3 multi are accepted. The tracing SDK owns standard trace/span IDs; application code must not overwrite them as custom attributes.
- HTTP, synchronous chat, SSE, analytics planning, controlled Agent sessions, and Spring AI model observations reuse the resolved trace ID/context. Agent fallback IDs are 32 lowercase hex characters.
- SSE captures Micrometer thread-local context before `StreamingResponseBody` starts and restores it on the worker thread together with `requestId` MDC.
- Delayed jobs start a new observation. They attach persisted upstream correlation, job/subscription/tenant/user/report/delivery/event IDs as high-cardinality attributes instead of keeping the original request span open.
- Every custom business metric has only `app.component` and `app.outcome` low-cardinality tags. All correlation IDs are trace-only and must return `false` from `TelemetryFieldPolicy.metricTagAllowed`.
- Allowed outcomes are `success`, `error`, `fallback`, `skipped`, `cancelled`, and `rejected`. A returned permanent/retry/unknown business failure must be mapped to `error` even when the application service handles the exception internally.
- Passwords, raw tokens, API keys, secrets, session families, prompts, completions, tool arguments, request/response bodies, and business payloads are forbidden in logs, metrics, and traces. Spring AI `include-error-logging`, `log-prompt`, and `log-completion` remain explicitly `false`.
- Local exposure defaults to `health,info,metrics,prometheus`. Production exposure defaults to `health,info`. Expanding production exposure requires network or proxy access control for `/actuator/**`.
- OTLP export is optional and disabled by default. The application and tests must start without a Collector.
- `OperationalTelemetry.noop()` must emit no observations or completion logs so legacy manually wired unit tests retain their prior behavior.
- When a Spring component has a production constructor plus a private/test compatibility constructor, mark the production constructor `@Autowired`; otherwise Spring may search for a nonexistent default constructor.

### 4. Validation & Error Matrix

| Condition | Required behavior |
| --- | --- |
| Missing `X-Request-ID` | Generate a UUID request ID and return it |
| Safe inbound request ID | Preserve it exactly after trimming |
| Blank, malformed, or over-128-character inbound ID | Replace it and emit only the safe `http.request_id.replaced` reason |
| Active Micrometer span | Return its standard trace ID as `X-Trace-ID` |
| No active span | Use the safe request ID as correlation fallback; do not fail the request |
| SSE worker execution | Restore trace context and request MDC before calling `ChatService` |
| Correlation value blank or over its field limit | Omit the trace attribute |
| Import finds existing data | `app.outcome=skipped` |
| Import uses demo fallback | `app.outcome=fallback` |
| Strict import failure or thrown operation | Mark observation error, set `app.outcome=error`, and rethrow |
| Job returns retry/permanent/unknown status | `app.outcome=error` without inventing a raw exception payload |
| OTLP endpoint unavailable while export is disabled | Application still starts normally |

### 5. Good/Base/Bad Cases

- Good: a valid request ID reaches the controller, SSE worker, analytics plan, controlled Agent tool logs, and model observation while the response also exposes the standard trace ID.
- Good: a failed report job can be found by job, subscription, tenant, creator, persisted correlation, and report ID; none of those values become Prometheus labels.
- Good: production starts with only health/info exposed and OTLP disabled, then operators opt in through environment configuration.
- Base: manually constructed services use `OperationalTelemetry.noop()` and preserve the old unit-test behavior.
- Bad: add tenant, user, request, job, batch, or report ID as a low-cardinality key value or Meter tag.
- Bad: trust an arbitrary inbound request ID, truncate it into an accepted identifier, or generate separate 8/12-character trace IDs at each layer.
- Bad: log a prompt/completion/tool argument for troubleshooting, keep an HTTP span open until a scheduled job runs, or require a Collector for application startup.

### 6. Tests Required

- `RequestCorrelationTest`: safe preservation, invalid/overlong replacement, and request attribute reuse.
- `RequestCorrelationFilterTest`: response headers, MDC lifecycle, and safe replacement event.
- `AsyncTraceContextTest`: worker thread restores the captured trace and request MDC.
- `AgentExecutionPolicyTest`: cross-layer safe correlation format and 32-hex fallback.
- `ChatControllerTest` and `ChatServiceTest`: one trace value reaches synchronous/SSE chat, analytics, and controlled Agent sessions without breaking legacy overloads.
- `TelemetryFieldPolicyTest`: every correlation field is forbidden as a metric tag; secrets/payload keys and session family are denied.
- `OperationalTelemetryTest`: high-cardinality identifiers appear in observation context but not timer tags; exceptions produce `error` and rethrow.
- Import, report, and runner tests: observations are emitted, identifier tags are absent, and handled business failures map to the correct outcome.
- Startup tests: the full Spring context starts with default OTLP export disabled and without global model configuration.
- Final gates: `mvn.cmd "-Dfrontend.skip=true" pmd:check`, `mvn.cmd "-Dfrontend.skip=true" test`, frontend lint/test/build, and `git diff --check`.

### 7. Wrong vs Correct

Wrong:

```java
String traceId = request.getHeader("X-Request-ID").substring(0, 128);
registry.counter("job", "tenant", tenantId.toString()).increment();
StreamingResponseBody body = output -> chatService.streamChat(request, output, scope);
```

Correct:

```java
String requestId = RequestCorrelation.requestId(servletRequest);
String traceId = RequestCorrelation.traceId(servletRequest);
AsyncTraceContext asyncContext = AsyncTraceContext.capture(requestId);
StreamingResponseBody body = output -> asyncContext.run(
        () -> chatService.streamChat(request, output, scope, traceId));

operationalTelemetry.observeVoid(OperationalEvent.REPORT_JOB_EXECUTION, context -> {
    context.correlate(CorrelationField.JOB_ID, job.id());
    context.correlate(CorrelationField.TENANT_ID, job.tenantId());
    JobView completed = jobService.executeClaimed(job.id(), workerId, clock.instant());
    context.outcome(mapOutcome(completed.status()));
});
```

