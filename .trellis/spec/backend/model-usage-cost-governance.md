# Model Usage and Cost Governance

> Executable contracts for model-call metering, price snapshots, budgets, tenant isolation, and platform reporting.

## Scenario: Govern Model Usage and Estimated Cost

### 1. Scope / Trigger

- Trigger: any model or embedding integration, usage aggregation endpoint, price catalog, budget policy, or model-cost administration change.
- This is a cross-layer storage, security, streaming, and operations contract. A logical provider call must reconcile to one durable event without retaining request or response content.

### 2. Signatures

- Tracker: `ModelUsageTracker.track(ChatModel, ModelUsageContext) -> ChatModel`.
- Context: `ModelUsageContext(tenantId,userId,scenario,provider,model,traceId,cacheHit)`.
- Scenarios: `CHAT`, `AGENT`, `KNOWLEDGE`, `KNOWLEDGE_EMBEDDING`, `KNOWLEDGE_RETRIEVAL`, `REPORT`, `BACKGROUND_SUBSCRIPTION`, and `MODEL_CONFIG_TEST`.
- Tenant API:
  - `GET /api/admin/model-usage/summary?from&to`
  - `GET /api/admin/model-usage/events?from&to`
  - `GET|POST /api/admin/model-usage/prices`
  - `GET|PUT /api/admin/model-usage/budget`
- Platform API: `GET /api/platform/model-usage/summary?from&to`.
- Tables: `model_usage_events`, `model_price_versions`, `model_budget_policies`, and `model_budget_reservations`, created by Flyway V12.

### 3. Contracts

- One tracker wrapper records one event for one logical `ChatModel.call` or stream subscription. Transport retries inside the delegate remain part of that event; separate Agent model rounds produce separate events.
- Sync, stream completion, stream error, cancellation, and budget rejection settle at most once. Streaming usage metadata is provider-cumulative, so retain the maximum observed input/output/total values; never add per-chunk totals.
- Preserve provider usage states: known, partial, or unknown. Missing token metadata is not inferred from text length. Missing tokens or an applicable price keeps `estimatedCost` null and the cost source unknown.
- Price changes append immutable versions. Recording selects the effective tenant/provider/model version and copies its key, input/output rates, currency, and calculated cost into the event; historical events are never recalculated.
- Default governance is observational with soft budget state. Hard admission is enabled only by `hardLimitEnabled=true` and requires a positive reservation amount.
- Hard admission locks the tenant budget row pessimistically, counts current UTC-month spend plus active same-currency reservations, and creates a 10-minute reservation before the provider call. Completion/error/cancellation closes it exactly once. Expired reservations are recoverable.
- Governance infrastructure failure follows the policy's `failOpen` value. A true budget exceed always rejects; it is never converted to fail-open.
- Events, prices, and policies contain governance metadata only. Never persist prompt, completion, API key, Base URL, tool parameters, provider-native payloads, or secret-bearing errors.
- Tenant endpoints derive tenant ID only from `AuthPrincipal`. Reads require `MODEL_USAGE_READ`; price/budget mutations require `MODEL_USAGE_MANAGE` and write audit events.
- Platform aggregation requires `MODEL_USAGE_PLATFORM_READ` plus `AuthPrincipal.tenantId == TenantScoped.DEFAULT_TENANT_ID`, and every successful read writes `PLATFORM_MODEL_USAGE_READ` audit metadata.
- Provider invoices are external reconciliation input only. They do not overwrite event snapshots or turn estimated cost into an actual billing record.

### 4. Validation & Error Matrix

- Missing exact read/manage/platform authority -> HTTP 403 before governance data access.
- Tenant-scoped caller supplies or guesses another tenant -> ignored by design; no tenant request field exists.
- `from >= to`, range over 366 days, or malformed timestamp -> HTTP 400.
- Negative price, invalid three-letter currency, invalid threshold, or hard limit with zero reservation -> HTTP 400; no write or audit success.
- Stale budget `version` -> HTTP 409 and preserve the stored policy.
- Budget spend + active reservations + requested reservation exceeds limit -> record one rejected usage event and do not invoke the provider.
- Governance persistence/admission fails with fail-open enabled -> invoke the provider, emit safe operational telemetry, and avoid leaking exception data.
- Stream terminates by complete/error/cancel -> one terminal event and one reservation settlement.

### 5. Good/Base/Bad Cases

- Good: a stream emits cumulative totals 100, 160, 160; the event stores 160 once and closes one reservation.
- Good: price v1 handles an August call; adding v2 changes later estimates while the August event retains v1 rates and key.
- Base: a provider returns no usage metadata; the call is still visible with `tokenState=UNKNOWN` and unknown cost.
- Bad: add token counts from every stream chunk, produce an event per retry, estimate tokens from response length, or rewrite historical costs after a catalog update.
- Bad: accept `tenantId` from query/body, expose raw provider payloads, or rely only on a hidden frontend control for authorization.

### 6. Tests Required

- `ModelUsageSnapshotTest`: unknown/partial/known metadata and cumulative maximum merge.
- `ModelUsageTrackerTest`: sync and stream success/error/cancel, budget rejection, transport retry boundary, and exactly-once record/settlement.
- `ModelUsageRecordingServiceTest`: effective price selection, copied snapshot fields, missing-token/price unknown cost, and reservation closure.
- `ModelBudgetAdmissionPersistenceTest`: real H2 pessimistic-lock concurrency, limit boundary, same-currency spend, fail-open/fail-closed, and reservation expiry.
- `ModelUsageGovernanceServiceTest` and `ModelUsageControllerTest`: tenant isolation, platform dual gate/audit, aggregation reconciliation, validation, and optimistic 409.
- `AuthHttpIntegrationTest`: real HTTP matcher authority boundaries.
- `AgentPocApplicationStartupTest`: V12 tables/permission backfill, UTF-8 OpenAPI parsing, and Hibernate validation. Seed built-in roles before applying a migration that backfills their permissions.
- Final backend gates: `mvn.cmd "-Dfrontend.skip=true" pmd:check` and `mvn.cmd "-Dfrontend.skip=true" test`.

### 7. Wrong vs Correct

Wrong:

```java
stream.doOnNext(response -> record(response.getMetadata().getUsage()));
cost = latestPrice(tokens); // historical rate changes when the catalog changes
```

Correct:

```java
return tracker.track(delegate, serverResolvedContext);
// The tracker merges cumulative metadata and atomically settles once.
// Recording copies the effective immutable price version into the event.
```
