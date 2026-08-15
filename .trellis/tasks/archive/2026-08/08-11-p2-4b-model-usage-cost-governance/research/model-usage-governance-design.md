# Model Usage and Cost Governance Design Research

## Repository Baseline

* `ChatService` creates all externally configured chat models through `ModelConfigService`.
* `OpenAiChatModel` owns the configured `RetryTemplate`, so a decorator outside that model sees one logical result after transport retries rather than one billable event per retry attempt.
* Controlled tool calling can invoke the decorated model multiple times. Those are distinct provider calls and must remain distinct usage events.
* PGvector owns embedding calls internally. The current repository does not expose reliable per-request embedding usage metadata, so missing usage must be stored as unknown instead of estimated from prompt text.
* Reports and subscription jobs are deterministic today. They should not emit fake model usage merely to populate every scenario.

## Spring AI 1.0 Contract

Local dependency inspection confirms:

* `ChatResponse.getMetadata()` returns `ChatResponseMetadata`.
* `ChatResponseMetadata.getUsage()` returns `Usage`.
* `Usage` exposes nullable `getPromptTokens()`, `getCompletionTokens()`, `getTotalTokens()`, and provider-native metadata.
* `ChatModel.call(Prompt)` returns one `ChatResponse`; `ChatModel.stream(Prompt)` returns `Flux<ChatResponse>`.

Provider-native metadata is intentionally not persisted because its shape is untrusted and may contain fields outside the governance allowlist.

## Recording Boundary

The recommended boundary is a `ChatModel` decorator:

1. Allocate one call ID per `call` invocation or stream subscription.
2. Run budget admission before delegating.
3. For synchronous calls, persist the returned response usage exactly once.
4. For streams, retain the latest non-null/max cumulative usage instead of summing chunks.
5. Persist failures and cancellations once, with token state `UNKNOWN` unless the provider already returned complete cumulative metadata.
6. Release any hard-budget reservation in the same completion path.

This preserves retry semantics and correctly counts Agent model loops without coupling business code to OpenAI response classes.

## Pricing

Use append-only price versions keyed by tenant, provider, model, currency, and effective time. A usage event stores both the selected catalog row/version and the input/output unit prices used at recording time. Historical events therefore remain auditable after catalog changes.

Costs use decimal arithmetic per one million tokens. If tokens or a matching price version are absent, estimated cost is null and the event carries an explicit unknown state. Provider invoices remain an external reconciliation source; invoice differences are not silently written back over event history.

## Budget Concurrency

Soft budgets only compute threshold state and never reject calls. Optional hard budgets require a positive per-call reservation amount. Admission locks the tenant policy, removes expired reservations, and rejects when spent plus active reservations plus the new reservation exceeds the monthly limit. Completion closes the reservation and records actual cost.

This prevents a burst of concurrent requests from all passing the same boundary. Actual usage can still exceed the fixed reservation for one call, so the hard limit is an operational guardrail rather than a financial guarantee. It remains disabled by default.

## Authorization

Tenant APIs always derive tenant ID from the authenticated principal. No query parameter can select another tenant. Cross-tenant aggregation uses a separate permission and additionally requires the selected tenant to be the stable default platform tenant. Every platform summary read writes an audit event with safe filter metadata only.

## Security and Cardinality

Persist only provider key, model name, scenario, tenant/user IDs, call/correlation IDs, token counts, duration, status, price snapshot, currency, and cost source. Never persist prompt, completion, API key, Base URL, tool arguments, or provider-native response bodies. Tenant/user/call IDs remain database dimensions and trace attributes, not metric tags.
