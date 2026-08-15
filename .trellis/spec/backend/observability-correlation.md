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

## Scenario: Health, Alerting, and Performance Baseline

### 1. Scope / Trigger

- Trigger: any change to Actuator health groups, orchestration probes, dependency degradation, repository/model latency, queue gauges, Prometheus rules, Alertmanager routing, or performance baselines.
- This is a runtime and cross-layer contract because Spring health contributors, security paths, persistence state, metrics, alert routing, and deployment restart behavior must agree.

### 2. Signatures

- Liveness: `/actuator/health/liveness` and `/livez`; member list is exactly `livenessState`.
- Readiness: `/actuator/health/readiness` and `/readyz`; member list is exactly `readinessState,db,migration,knowledge`.
- Diagnostic-only contributor: `operationalQueue`; it is included in aggregate `/actuator/health` but excluded from both probe groups.
- Repository metrics: `agentpoc.database.query` timer and `agentpoc.database.slow.query` counter.
- Provider metric: `agentpoc.model.call` timer.
- Queue gauges: `agentpoc.report.job.{backlog,retry,failure}`, `agentpoc.report.delivery.{backlog,retry,failure}`, and `agentpoc.operational.queue.refresh.available`.
- Runtime keys: `APP_OBSERVABILITY_SLOW_QUERY_THRESHOLD`, `APP_OBSERVABILITY_QUEUE_REFRESH_INTERVAL`, `APP_OBSERVABILITY_QUEUE_REFRESH_INITIAL_DELAY`, `APP_OBSERVABILITY_JOB_BACKLOG_DEGRADED_THRESHOLD`, `APP_OBSERVABILITY_DELIVERY_BACKLOG_DEGRADED_THRESHOLD`, and `APP_OBSERVABILITY_PERMANENT_FAILURE_DEGRADED_THRESHOLD`.
- Operational assets: `ops/prometheus/agentpoc-alerts.yml`, `ops/alertmanager/alertmanager.example.yml`, and `ops/performance/baseline.mjs`.

### 3. Contracts

- Liveness represents only whether the process should be restarted. Database, migration, knowledge, model, import, job, and delivery state must never become liveness members.
- Readiness removes traffic when the database is unavailable, Flyway has pending migrations, or the configured knowledge index has not initialized. A readiness failure must not cause a liveness restart.
- Model provider, import, report job, and delivery failures are operational degradation signals. They emit metrics/alerts and may set aggregate health to `DEGRADED` with HTTP 200, but they never enter readiness.
- `MigrationHealthIndicator` is `UP` when Flyway is disabled, `OUT_OF_SERVICE` when migrations are pending, and `DOWN` when inspection fails. `KnowledgeHealthIndicator` is `OUT_OF_SERVICE` until `KnowledgeIndex.isAvailable()` succeeds.
- Repository timing measures the complete Spring Data repository call through `RepositoryQueryMetricsAspect`; it is not raw-SQL instrumentation. Do not attach repository name, method name, SQL, parameters, tenant, user, or correlation IDs.
- Queue snapshots refresh on a bounded schedule and retain the last numeric values with `refresh.available=0` if refresh fails. Backlog/retry/failure gauges use only `app.component` and `app.outcome` tags.
- Each logical provider sync call or stream subscription records exactly one `agentpoc.model.call` terminal duration. Budget rejection before provider invocation is not a provider call.
- Every alert has a non-empty `for`, impact, threshold, diagnostic URL, and runbook URL. Alertmanager groups duplicate events, limits repeats, sends resolved notifications, and inhibits matching warnings under a critical alert.
- The Node performance baseline requires an explicit write acknowledgement for report generation, discards response bodies, and persists only aggregate timing/status data. A local H2 result is a regression baseline, not production capacity evidence.

### 4. Validation & Error Matrix

| Condition | Required behavior |
| --- | --- |
| Process is alive but database is unavailable | `/livez` remains 200; `/readyz` returns 503 and traffic is removed |
| Flyway reports pending migrations | `migration=OUT_OF_SERVICE`; readiness returns 503 |
| Knowledge index is not initialized | `knowledge=OUT_OF_SERVICE`; readiness returns 503 |
| Model error ratio rises | Model degradation alert fires after debounce; readiness and liveness remain unchanged |
| Queue refresh throws | Preserve last counts, set refresh availability to 0, and set aggregate health to `DEGRADED` |
| Slow calls split across success/error series | Alert expression sums both series before applying the threshold |
| Alert clears | Alertmanager sends one resolved notification according to grouping/routing |
| Performance response or transport fails | Count the status/error, retain duration, and fail the baseline when the configured error-rate threshold is exceeded |

### 5. Good/Base/Bad Cases

- Good: a PostgreSQL outage stops new traffic through readiness while the orchestrator leaves the live process available for diagnosis.
- Good: three successful and two failed slow repository calls within the window satisfy a five-call slow-query alert.
- Good: an SMTP `UNKNOWN` delivery degrades aggregate health and alerts an operator without automatic replay or process restart.
- Base: Flyway disabled in local H2 reports migration state `disabled`; an initialized in-memory knowledge index remains ready.
- Bad: add the model provider or `operationalQueue` to readiness, causing fallback-capable requests to be drained or restarted.
- Bad: label a metric with SQL, repository method, tenant, user, prompt, token, request, job, or delivery identifiers.
- Bad: use a local H2 p95 to size production instances or run report load against a tenant that contains business data.

### 6. Tests Required

- `AgentPocApplicationStartupTest`: start a real random-port server, assert exact liveness/readiness group membership, and request `/livez` and `/readyz` successfully.
- Health indicator tests: disabled/current/pending/error migration states and unavailable/available/error knowledge states.
- `RepositoryQueryMetricsAspectTest`: success/error timing, slow threshold, rethrow behavior, and exactly the two safe tag keys.
- `OperationalQueueMetricsTest`: backlog/retry/failure values, refresh availability, degraded aggregate health, and safe tag keys.
- `ModelUsageTrackerTest`: one provider timer per logical sync/stream terminal outcome.
- `OperationalAssetsTest`: YAML structure, unique alert names, debounce/action annotations, summed slow-query expression, grouping/repeat/inhibition, secret-file webhook, and resolved notifications.
- Final gates: `mvn.cmd "-Dfrontend.skip=true" pmd:check`, full backend tests, frontend lint/test/build when bundled assets are affected, `node --check ops/performance/baseline.mjs`, an actual `/actuator/prometheus` scrape, and `git diff --check`.

### 7. Wrong vs Correct

Wrong:

```yaml
management.endpoint.health.group.liveness.include: livenessState,db,model
expr: increase(agentpoc_database_slow_query_total[10m]) >= 5
```

Correct:

```yaml
management.endpoint.health.group.liveness.include: livenessState
management.endpoint.health.group.readiness.include: readinessState,db,migration,knowledge
expr: sum(increase(agentpoc_database_slow_query_total[10m])) >= 5
```
