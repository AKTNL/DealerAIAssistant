# Quality Guidelines

> Code quality standards for backend development.

---

## Overview

The project uses JUnit 5 (Jupiter) with AssertJ assertions and Mockito for mocking. Tests follow the same package structure as main source. The project does not use `@SpringBootTest` for unit tests -- manual wiring via constructors is preferred. The build runs via `mvn test` (with optional `-Dfrontend.skip=true` to skip frontend build).

Backend static analysis uses PMD through `maven-pmd-plugin`. The project-owned ruleset lives at `backend/config/pmd-ruleset.xml`, starts from PMD's Java error-prone rules, and excludes legacy high-noise rules that would require broad style-only churn. Run the PMD gate from `backend/` with:

```bash
mvn "-Dfrontend.skip=true" pmd:check
```

---

## Scenario: Backend PMD Quality Gate

### 1. Scope / Trigger
- Trigger: Backend Java changes must pass PMD before tests in CI.
- This is an infra and backend quality contract because PMD is now a required Maven goal and CI step.

### 2. Signatures
- Local command from `backend/`: `mvn "-Dfrontend.skip=true" pmd:check`
- Maven plugin: `org.apache.maven.plugins:maven-pmd-plugin:3.28.0`
- Ruleset file: `backend/config/pmd-ruleset.xml`
- CI step: `.github/workflows/ci.yml` runs `mvn -B -ntp -Dfrontend.skip=true pmd:check` before backend tests.

### 3. Contracts
- PMD must fail the build on violations (`failOnViolation=true`).
- PMD must print failing errors (`printFailingErrors=true`) so CI logs are actionable.
- The project ruleset starts from `category/java/errorprone.xml`.
- The first PMD baseline excludes legacy high-noise rules that would force broad style-only churn: `AvoidDuplicateLiterals`, `AvoidFieldNameMatchingMethodName`, `AvoidLiteralsInIfCondition`, `NullAssignment`, `UseLocaleWithCaseConversions`, and `UseProperClassLoader`.
- Do not add Checkstyle or SpotBugs under this contract; the selected backend static-analysis gate is PMD only.

### 4. Validation & Error Matrix
- PMD reports a non-excluded error-prone violation -> fix the code or deliberately tune the project ruleset with a narrow reason.
- `failOnViolation=false` -> reject; PMD would stop being a gate.
- Ruleset points directly at the full built-in category and creates broad historical churn -> reject; use the project-owned ruleset.
- CI omits `pmd:check` -> reject; local and CI quality gates must match.
- Maven system properties are unquoted in PowerShell documentation -> reject; use `mvn "-Dfrontend.skip=true" ...`.

### 5. Good/Base/Bad Cases
- Good: `mvn "-Dfrontend.skip=true" pmd:check` passes locally and in CI without business-code rewrites.
- Base: Existing tests remain the behavioral authority after PMD passes.
- Bad: Fixing PMD by renaming many legacy literals or reformatting unrelated analytics code in the same task.

### 6. Tests Required
- Quality verification must include `cd backend && mvn "-Dfrontend.skip=true" pmd:check`.
- Backend behavior verification must still include `cd backend && mvn "-Dfrontend.skip=true" test`.
- If changing PMD rules, inspect violations before widening or narrowing the ruleset.

### 7. Wrong vs Correct

Wrong:
```xml
<rulesets>
    <ruleset>category/java/errorprone.xml</ruleset>
</rulesets>
```

Correct:
```xml
<rulesets>
    <ruleset>config/pmd-ruleset.xml</ruleset>
</rulesets>
```

---

## Required Patterns

### Constructor Injection

All services, controllers, and config classes use **constructor injection** (no `@Autowired` on fields):

```java
// ChatController.java:31-39
public ChatController(
        ChatService chatService,
        SessionMemoryService sessionMemoryService,
        SessionOwnershipService sessionOwnershipService
) {
    this.chatService = chatService;
    this.sessionMemoryService = sessionMemoryService;
    this.sessionOwnershipService = sessionOwnershipService;
}
```

`RuleBasedAnalyticsService` uses `@Autowired` on its constructor (it has many dependencies):

```java
// RuleBasedAnalyticsService.java:81-82
@Autowired
public RuleBasedAnalyticsService(
        PromptFactory promptFactory,
        DealerRepository dealerRepository,
        // ... more dependencies ...
) {
```

### Java Records for DTOs

All DTOs are Java records (immutable, transparent data carriers):

```java
// dto/request/ChatRequest.java
public record ChatRequest(
        @NotBlank String sessionId,
        @NotBlank String message,
        String baseUrl,
        String apiKey,
        String model
) {}

// dto/response/ApiResult.java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResult<T>(int code, T data, String message) { ... }

// dto/response/ChatResponse.java
public record ChatResponse(String reply) {}
```

### Jakarta Bean Validation

Request DTOs use Jakarta validation annotations and controllers apply `@Valid`:

```java
// ChatController.java:42-43
@PostMapping
public ResponseEntity<ChatResponse> chat(
        @Valid @RequestBody ChatRequest request, ...
```

### Type-Safe Configuration

Configuration properties use `@ConfigurationProperties` with inner static classes per domain:

```java
// config/AppProperties.java
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private final Auth auth = new Auth();
    private final Security security = new Security();
    private final Cors cors = new Cors();
    private final Excel excel = new Excel();
    private final Model model = new Model();

    public static class Auth {
        private String accessKey = "";
        private String sessionSecret = "";
        private Duration sessionTtl = Duration.ofHours(8);
        // getters and setters
    }
    // ...
}
```

Application class enables scanning with:

```java
// AgentPocApplication.java:21
@ConfigurationPropertiesScan
```

### Scenario: Dashboard MVP Aggregate Endpoint

#### 1. Scope / Trigger
- Trigger: Browser users need an authenticated landing dashboard before entering chat analysis.
- This is a backend API, security, active-batch, OpenAPI, and frontend request contract because one aggregate endpoint feeds the dashboard workspace and its chat-analysis entry points.

#### 2. Signatures
- Backend endpoint: `GET /api/dashboard`
- Controller return type: `ApiResult<DashboardSummary>`
- Response record: `dto.response.DashboardSummary`
- Service entry point: `DashboardService.getSummary(): DashboardSummary`
- Security:
  - `ApiKeyFilter` must whitelist `/api/dashboard` from the internal `X-API-Key` gate.
  - `SessionTokenFilter` must protect `/api/dashboard` with the browser Bearer session token.
- Frontend API wrapper: `frontend/src/api/dashboard.js -> getDashboardSummary()` through `requestJson("/api/dashboard", ...)`.
- OpenAPI path: `backend/src/main/resources/static/openapi.json -> /api/dashboard` with `BearerAuth`.

#### 3. Contracts
- Dashboard aggregation must read only the active import batch for every business entity (`Dealer`, `Target`, `Opportunity`, `Lead`, `Task`, `Campaign`) via `ImportBatchService.filterActive(...)`.
- The controller stays thin: no repository access, no aggregation logic, and no manual response-envelope construction beyond `ApiResult.success(...)`.
- Dashboard DTOs are Java records under `dto/response`; do not expose JPA entities directly.
- `DashboardSummary` includes these top-level sections:
  - `dataStatus`
  - `overview`
  - `targetAchievement`
  - `opportunityFunnel`
  - `leadSources`
  - `followUpTasks`
  - `campaignEffect`
- Rate calculations must preserve comparable-denominator semantics: nullable targets are included in observed counts but excluded from rate denominators.
- Frontend dashboard requests must use the shared API client and send the stored session token as `Authorization: Bearer <token>`.
- Dashboard analysis buttons must reuse the existing chat submission path rather than creating a second analysis transport.

#### 4. Validation & Error Matrix
- Missing or invalid Bearer token -> `SessionTokenFilter` returns HTTP 401 with the existing session-expired JSON body.
- Missing `X-API-Key` on `/api/dashboard` -> allowed through `ApiKeyFilter`; browser APIs do not require the internal key.
- Repository rows from inactive batches -> excluded before counting, ranking, or calculating rates.
- No active batch row exists -> `ImportBatchService` legacy fallback rules apply and legacy rows remain visible.
- Malformed frontend response shape -> API wrapper normalizes to `null`/empty records so the view can render loading/empty/error states safely.
- Dashboard card clicked while chat is sending -> do not emit a duplicate analysis request.

#### 5. Good/Base/Bad Cases
- Good: Workbook A is imported, then workbook B is activated; `/api/dashboard` shows only workbook B metrics and status.
- Good: A target row with `asKTarget=null` contributes to observed won counts but not to `targetAchievementRate`.
- Base: `/api/dashboard` returns a 200 `ApiResult` envelope with all seven top-level sections, even when some lists are empty.
- Bad: Calling `targetRepository.findAll()` and summing directly leaks inactive-batch rows into the user-facing dashboard.
- Bad: The dashboard frontend calls raw `fetch()` or posts prompts to a new endpoint instead of reusing `submitPrompt(...)`.

#### 6. Tests Required
- `DashboardServiceTest`: assert active-batch filtering and comparable-rate calculations across all dashboard sections.
- `DashboardControllerTest`: assert the `ApiResult` envelope and representative nested fields.
- `ApiKeyFilterTest`: assert `/api/dashboard` bypasses internal API-key enforcement.
- `SessionTokenFilterTest`: assert `/api/dashboard` requires a valid Bearer session token.
- `frontend/src/api/__tests__/dashboard.spec.js`: assert `requestJson("/api/dashboard", ...)` and Bearer header usage.
- `DashboardView.spec.js` and `ChatView.spec.js`: assert dashboard states, default workspace behavior, and analysis prompt handoff into chat.
- Quality verification must include `npm run lint`, `npm test`, `npm run build`, `mvn "-Dfrontend.skip=true" pmd:check`, and `mvn "-Dfrontend.skip=true" test` when this contract changes.

#### 7. Wrong vs Correct

Wrong:
```java
List<Target> targets = targetRepository.findAll();
double achievement = percentage(sumWon(targets), sumTarget(targets));
```

Correct:
```java
List<Target> targets = importBatchService.filterActive(targetRepository.findAll());
List<Target> comparable = targets.stream()
        .filter(target -> target.getAsKTarget() != null)
        .toList();
double achievement = percentage(sumWon(comparable), sumTarget(comparable));
```

Wrong:
```js
const response = await fetch("/api/dashboard");
```

Correct:
```js
const response = await requestJson("/api/dashboard", {
  headers: token ? { Authorization: `Bearer ${token}` } : {}
});
```

### Scenario: Default Model Configuration With Request Override

#### 1. Scope / Trigger
- Trigger: Chat requests can use backend-provided default OpenAI-compatible model settings, while the browser settings panel can still send request-scoped overrides.
- This is a backend config and cross-layer request contract because env keys, `ChatRequest` fields, `ModelConfigService`, and frontend local settings all participate in the final model connection.

#### 2. Signatures
- Config: `AppProperties.Model.baseUrl/apiKey/name`
- YAML/env:
  - `app.model.base-url` -> `APP_MODEL_BASE_URL`
  - `app.model.api-key` -> `APP_MODEL_API_KEY`
  - `app.model.name` -> `APP_MODEL_NAME`
- Request DTO: `ChatRequest(sessionId, message, baseUrl, apiKey, model)`
- Service methods:
  - `ModelConfigService.hasConfiguredModelSettings(ChatRequest request)`
  - `ModelConfigService.createChatModel(ChatRequest request)`
  - `ModelConfigService.createChatModel(ModelConfigRequest request)` for explicit model-connection tests

#### 3. Contracts
- `ChatRequest.baseUrl/apiKey/model` are optional for chat calls.
- For chat calls, each non-blank request field overrides the corresponding backend default.
- Blank request fields fall back independently to `app.model.base-url`, `app.model.api-key`, and `app.model.name`.
- `ModelConfigRequest` remains explicit and does not silently fall back for `/api/model-config/test`; controller validation still requires all fields.
- Resolved model settings must still pass URL scheme, host, private-host, and allowlist validation before an `OpenAiChatModel` is created.
- API keys and session tokens must never be logged.

#### 4. Validation & Error Matrix
- Request blank + defaults blank -> `hasConfiguredModelSettings(...) == false`; chat returns configuration guidance or analytics fallback.
- Request blank + all defaults present -> `hasConfiguredModelSettings(...) == true`; chat can create the configured model.
- Request field present + different backend default -> request value wins.
- Resolved base URL is localhost/private and private hosts are disabled -> `IllegalArgumentException("Model base URL is not allowed.")`.
- Resolved host is outside `app.model.allowed-hosts` -> `IllegalArgumentException("Model base URL is not allowed.")`.
- Explicit `/api/model-config/test` request with blank fields -> request validation fails before model creation.

#### 5. Good/Base/Bad Cases
- Good: `APP_MODEL_BASE_URL`, `APP_MODEL_API_KEY`, and `APP_MODEL_NAME` are set; a chat request with empty model fields still uses the configured model.
- Good: Browser local settings send `baseUrl/apiKey/model`; those values override backend defaults for that request only.
- Base: Existing browser-only configuration continues to work when backend defaults are empty.
- Bad: `ChatService` checks only request fields and returns "model not configured" even though backend defaults are present.
- Bad: Model settings resolution logs API keys or bypasses host validation.

#### 6. Tests Required
- `ModelConfigServiceTest` asserts backend defaults make an empty chat request configured.
- `ModelConfigServiceTest` asserts request-scoped fields override backend defaults.
- `ChatServiceTest` asserts a blank chat request can call the model when `ModelConfigService.hasConfiguredModelSettings(...)` returns true.
- Full backend verification must include `mvn "-Dfrontend.skip=true" pmd:check` and `mvn "-Dfrontend.skip=true" test`.

#### 7. Wrong vs Correct

Wrong:
```java
private boolean hasConfiguredModelSettings(ChatRequest request) {
    return hasText(request.baseUrl()) && hasText(request.apiKey()) && hasText(request.model());
}
```

Correct:
```java
public boolean hasConfiguredModelSettings(ChatRequest request) {
    ModelConfigRequest resolved = resolveModelConfig(request);
    return hasText(resolved.baseUrl()) && hasText(resolved.apiKey()) && hasText(resolved.model());
}
```

### Constant-Time Comparison for Secrets

API keys and access keys are compared using `MessageDigest.isEqual()` to prevent timing attacks:

```java
// ApiKeyFilter.java:81-83
return MessageDigest.isEqual(
        configured.getBytes(StandardCharsets.UTF_8),
        provided.getBytes(StandardCharsets.UTF_8));
```

### Static Method Factories for Response Objects

Response records use static factory methods for success/error creation:

```java
// ApiResult.java
public static <T> ApiResult<T> success(T data) { return new ApiResult<>(200, data, "success"); }
public static <T> ApiResult<T> error(int code, String message) { return new ApiResult<>(code, null, message); }
```

### Scenario: Controlled Agent Tool Runtime

#### 1. Scope / Trigger

- Trigger: an authenticated sync or SSE chat request is classified as dealer operations analytics, business knowledge, or a combination, and a configured model may select read-only business tools.
- This is a backend security, Spring AI integration, active-batch, knowledge-citation, fallback, and compatibility contract. A change to callback registration, tool arguments, scope verification, analytics/knowledge routing, or model handling must follow this scenario.

#### 2. Signatures

- Authenticated chat entry points:
  - `ChatService.chat(ChatRequest request, AgentRequestScope scope): String`
  - `ChatService.streamChat(ChatRequest request, OutputStream outputStream, AgentRequestScope scope): void`
- Request-scoped callback factory:
  - `ControlledAgentToolCallbacks.openSession(AgentRequestScope scope, String traceId): ControlledAgentToolSession`
- Fixed callback names and application entry points:
  - `getDashboardSummary()`
  - `queryMetric(String metric, Map<String, String> filters)`
  - `queryDetails(String dataset, Map<String, String> filters, Integer page, Integer pageSize, String sortBy, String sortOrder)`
  - `runScenarioAnalysis(String question, String language)`
  - `retrieveKnowledge(String query, Integer topK): KnowledgeSearchResult`
  - `generateReportDraft(String reportType, String language, String topic): ReportDraft`
- Default policy:
  - `AgentExecutionPolicy.DEFAULT_MAX_TOOL_CALLS == 4`
  - `AgentExecutionPolicy.DEFAULT_MAX_PAGE_SIZE == 50`

#### 3. Contracts

- `ChatController` claims/verifies session ownership first, then creates `AgentRequestScope.authenticated(sessionId, tokenSubject)`; it must not pass token values into tool arguments or logs.
- Only authenticated analytics, business-knowledge, or explicit report requests receive callbacks. Each model request gets a new `AgentExecutionContext`; all six callbacks share one request budget of four calls.
- Register exactly the six names above. Legacy callbacks such as `searchDealers`, `queryOpportunities`, `queryTargets`, and `queryLeads` remain compatibility code and are not attached to `ChatService`.
- The scope verifier requires a non-blank session and subject, `activeBatchOnly=true`, and current `SessionOwnershipService.owns(sessionId, subject)`.
- The application facade calls `DashboardService`, `AnalyticsApiService`, `RuleBasedAnalyticsService`, or the public `KnowledgeService`; it never accepts SQL, repository/bean names, arbitrary dataset names, import batch IDs, vector-store objects, or resource paths, and never accesses a repository directly.
- Metric/dataset values, filter keys, detail sort fields, sort direction, page, page size, scenario question length, and language are allowlisted or bounded before delegation.
- Existing application services remain responsible for active-import-batch filtering. Agent code cannot select a batch.
- Sync and SSE analytics/knowledge routes use the same controlled callbacks without changing `ChatRequest`, `ChatResponse`, HTTP paths, or SSE event names/order. Analytics failures use the rule report; knowledge-only failures use the deterministic cited/no-match composer.
- A Spring AI `@Tool` method must return a concrete serializable type such as `AgentToolResult` or `AgentScenarioAnalysis`. Do not declare an `Object` return type; Spring AI 1.0 treats it as a functional type and rejects callback creation.
- `retrieveKnowledge` returns the framework-neutral knowledge contract with `documentId`, `source`, `version`, `section`, `chunkId`, `excerpt`, `score`, and explicit `noMatch`. Knowledge excerpts explain policy/SOP/definitions only and never override structured KPI or active-batch facts.
- `generateReportDraft` calls only the reporting application port, accepts the same bounded language/type contract as the HTTP API, and records a deterministic Markdown draft without changing business data.
- Controlled trace logs contain only `traceId`, tool name, status, and a fixed safe reason. They never contain subject, token, user message, arguments, model output, or business details.

#### 4. Validation & Error Matrix

- Unauthenticated scope passed through a compatibility overload -> no Agent callbacks are attached.
- Scope no longer owns the session or `activeBatchOnly=false` -> tool call rejected before delegation; analytics request falls back to the deterministic report.
- Unknown tool name -> callback indexing/policy rejects it; it is never published.
- Unsupported metric/dataset/filter/sort/language or malformed integer -> `IllegalArgumentException`; no application query runs.
- Blank/overlong knowledge query or `topK` outside `1..8` -> `IllegalArgumentException`; no index query runs.
- Available knowledge index with no relevant hit -> `KnowledgeSearchResult.noMatch=true` and an empty hit list; do not invent policy text.
- Knowledge index/model/tool unavailable -> knowledge-only sync and SSE return `KnowledgeAnswerComposer` output, or the fixed unavailable message if deterministic retrieval also fails.
- `page < 1`, `pageSize < 1`, or `pageSize > 50` -> reject before calling `AnalyticsApiService`.
- Fifth tool call in one request -> `IllegalStateException("Agent tool call budget exceeded.")`; no fifth delegate call.
- Model creation/call, tool execution, budget, or final reply validation fails -> return/persist `AnalyticsPlan.fallbackReply()` for both sync and SSE analytics; knowledge-only requests return/persist the deterministic knowledge fallback.
- SSE writer I/O failure or accumulated output above `MAX_STREAMED_REPLY_CHARS` -> preserve the existing stream error/limit behavior; do not attempt to write an additional fallback after the transport is unsafe.

#### 5. Good/Base/Bad Cases

- Good: an authenticated target question receives exactly six callbacks, calls `queryMetric("target", ...)`, reads the active batch through `AnalyticsApiService`, and records a safe success trace.
- Good: an explicit weekly-report request calls `generateReportDraft`, preserves the active batch and `GLOBAL` scope, and returns the recorded Markdown draft on both sync and SSE paths.
- Good: “目标达成率口径是什么” routes to knowledge-only, calls `retrieveKnowledge`, and cites source/version without invoking rule analytics.
- Good: “目标达成率低，按什么 SOP 改善” remains an analytics route with `retrieveKnowledge` available; grounded KPI facts stay authoritative.
- Good: model creation is rejected by URL policy during analytics; sync and SSE still return the existing rule report, and SSE emits `done` rather than a model error.
- Base: a general non-analytics chat request uses the existing model path with no controlled callbacks.
- Bad: injecting the global `aiToolCallbackProvider` into chat exposes raw low-level tools and bypasses the business facade.
- Bad: accepting `batchId`, `sql`, or a free-form repository/dataset identifier and trusting the model to stay in scope.
- Bad: creating a separate budget per callback; this permits four calls per tool instead of four calls per request.

#### 6. Tests Required

- `AgentExecutionPolicyTest`: exact six-name allowlist, maximum 4 calls, maximum page size 50, unknown tool rejection, safe trace reasons.
- `ControlledAgentToolServiceTest`: mapping to existing services and `KnowledgeService`, plus invalid metric/dataset/filter, pagination, sort, language, scenario-length, query, and Top-K rejection without unintended delegate interaction.
- `ControlledAgentToolCallbacksTest`: exact published callback set, shared request budget, ownership denial, and no fifth delegate call.
- `SessionOwnershipAgentScopeVerifierTest`: authenticated subject/session, `activeBatchOnly`, and current ownership are all required.
- `ChatControllerTest`: claimed token subject becomes the exact authenticated `AgentRequestScope` for sync and SSE.
- `ChatServiceTest`: analytics and knowledge sync/SSE prompt options contain exactly the six controlled callbacks, explicit report requests use the reporting port, general chat contains none, knowledge-only/combined routing is explicit, and model/tool failures select the route-specific deterministic fallback.
- Full verification: `mvn "-Dfrontend.skip=true" pmd:check` and `mvn "-Dfrontend.skip=true" test`.

#### 7. Wrong vs Correct

Wrong -- expose the global low-level callback provider or use an abstract return type:

```java
requestSpec.toolCallbacks(aiToolCallbackProvider.getToolCallbacks());

@Tool(name = "queryMetric")
public Object queryMetric(String metric) {
    return analyticsApiService.getMetric(metric);
}
```

Correct -- create a request-scoped controlled session and return a concrete record:

```java
ControlledAgentToolSession session = controlledCallbacks.openSession(scope, traceId);
requestSpec = requestSpec.toolCallbacks(session.callbacks());

@Tool(name = "queryMetric")
public AgentToolResult queryMetric(String metric, Map<String, String> filters) {
    return toolService.queryMetric(metric, filters);
}
```

Wrong -- create the analytics model outside the fallback boundary:

```java
ChatModel model = modelConfigService.createChatModel(request);
try {
    return callGroundedAnalyticsModel(model, request, plan);
} catch (Exception exception) {
    return plan.fallbackReply();
}
```

Correct -- include model creation, tool execution, and model output validation in the same analytics fallback boundary:

```java
try {
    ChatModel model = modelConfigService.createChatModel(request);
    return callGroundedAnalyticsModel(model, request, plan);
} catch (Exception exception) {
    return plan.fallbackReply();
}
```

### Stream Chunking for Large Responses

ChatService limits streamed output to `MAX_STREAMED_REPLY_CHARS = 32_000` characters and uses a dedicated error message when the limit is exceeded:

```java
private static final String STREAMED_REPLY_LIMIT_MESSAGE =
        "The streamed reply exceeded the allowed output limit.";
```

### Scenario: Analysis Metadata SSE Contract

#### 1. Scope / Trigger
- Trigger: Analytics chat replies need a machine-readable explanation of the analysis lens, data sources, limitations, and confidence before the Markdown body is streamed.
- This is a backend streaming and frontend display contract because `ChatService` emits the event and `useChat` attaches it to the active assistant message for `AssistantMessage` to render.

#### 2. Signatures
- Backend model: `service.AnalyticsMetadata`
- Analytics plan field: `AnalyticsPlan.metadata(): AnalyticsMetadata`
- SSE event name: `analysis_metadata`
- Frontend message field: `message.analysisMetadata`

#### 3. Contracts
- `analysis_metadata` is emitted only for analytics replies after an `AnalyticsPlan` exists and before the first `message` event.
- The event payload is JSON with camelCase fields:
  - `scenarioLabel: string`
  - `scopeLabel: string`
  - `metricLens: string`
  - `dataSources: string[]`
  - `limitations: string[]`
  - `confidence: "high" | "medium" | "low" | ""`
- Empty metadata is not emitted.
- Frontend must display only backend-provided metadata and must not infer business meaning from Markdown.
- Direct greetings, out-of-scope replies, entity-not-found replies, and model-configuration guidance do not emit `analysis_metadata`.

#### 4. Validation & Error Matrix
- Metadata is `null` or all fields are empty -> skip `analysis_metadata`.
- Analytics fallback path without configured model -> emit metadata before the fallback `message`.
- Analytics configured-model path -> emit metadata before model-generated or fallback `message`.
- Frontend receives an invalid payload -> ignore it and continue rendering the Markdown reply.
- Unknown confidence value -> normalize to an empty confidence label, not a runtime error.

#### 5. Good/Base/Bad Cases
- Good: Campaign analytics emits limitations such as incomplete conversion fields so the UI warns that zero conversion fields do not prove campaign failure.
- Base: A normal target-achievement reply emits scope, metric lens, sources, and `high` or `medium` confidence before the body.
- Bad: Frontend parses Markdown headings or chart JSON to invent limitations; that duplicates backend business logic and can drift.

#### 6. Tests Required
- `ChatServiceTest` asserts `analysis_metadata` ordering before `message` for built-in fallback streaming.
- `ChatServiceTest` asserts configured analytics streaming includes the metadata event.
- `useChat.spec.js` asserts the event attaches normalized metadata to the active assistant message.
- `AssistantMessage.spec.js` asserts scope, metric, source, limitation, and confidence render in the message-top banner.

#### 7. Wrong vs Correct

Wrong:
```java
sseEventWriter.writeChunkedEvent(writer, "message", analyticsPlan.fallbackReply());
// Frontend later tries to infer sources/limitations from Markdown text.
```

Correct:
```java
sseEventWriter.writeAnalysisMetadataEvent(writer, analyticsPlan.metadata());
sseEventWriter.writeChunkedEvent(writer, "message", analyticsPlan.fallbackReply());
```

### Entity Constructor Delegation

Entities use constructor chaining for default values rather than field initializers or setters:

```java
// Opportunity.java - shorter ctor delegates to full ctor with default for purchaseHorizon
public Opportunity(String opportunityId, ..., String productModel, String stageName, ...) {
    this(opportunityId, ..., productModel, "未知", stageName, ...);
}

// Full ctor applies null/blank guard
public Opportunity(String opportunityId, ..., String purchaseHorizon, ...) {
    this.purchaseHorizon = purchaseHorizon == null || purchaseHorizon.isBlank() ? "未知" : purchaseHorizon;
}
```

### Semantic Routing For Workbook Analytics Paraphrases

#### 1. Scope / Trigger
- Trigger: Dealer analytics accuracy questions can be rephrased while asking for the same metric and dimension.
- This is a backend service and test contract because `ChatService` decides whether a message is in business scope and `RuleBasedAnalyticsService` decides which deterministic aggregation answers it.
- Direct-question and workbook paraphrase predicate logic belongs in `service.analytics.DirectQuestionMatcher` when it is pure string matching; keep repository-backed aggregation and report construction in `RuleBasedAnalyticsService` or a scenario-specific helper.

#### 2. Signatures
- Chat entry points:
  - `ChatService.chat(ChatRequest request): String`
  - `ChatService.stream(ChatRequest request, Consumer<ChatStreamEvent> onEvent): void`
- Analytics entry point:
  - `RuleBasedAnalyticsService.plan(String message, String language): AnalyticsPlan`
- Routing surfaces:
  - `AnalyticsTopicClassifier.detect(String message): AnalysisTopic`
  - Direct-answer helpers under target, opportunity, task, lead, and campaign analysis.

#### 3. Contracts
- Route analytics questions by business intent plus metric/dimension semantics, not by exact workbook wording.
- Product-sales paraphrases such as "which model sold best" / "which car sells best" must remain in business scope and route to target or won-sales aggregation.
- Completion-rate paraphrases such as "highest target completion rate" and "highest achievement rate" must route to target achievement aggregation.
- Breakdown paraphrases such as "by stage distribution", "status respectively how many", "task type top three", and "source/channel distribution" must route to the relevant entity aggregation.
- Multi-entity count questions that mention two or more of opportunity, lead, task, and campaign must route to `DATA_OVERVIEW` before single-entity campaign/task/lead routing.
- Topic priority ordering lives in `service.analytics.AnalyticsTopicClassifier`; keep it focused on pure text classification and leave repository-backed aggregation in `RuleBasedAnalyticsService` or focused analytics collaborators.
- Greeting plus intro messages such as "Hello, who are you?" must return the built-in assistant introduction, not out-of-scope text.

#### 4. Validation & Error Matrix
- Business sales paraphrase is not recognized by `ChatService` -> reply incorrectly becomes out-of-scope.
- Message contains a valid metric but a different wording from the workbook -> must still call deterministic analytics.
- Message mentions multiple entity totals -> `DATA_OVERVIEW`, not the first matching single-entity topic.
- Message asks for a known dimension but data is absent -> return the normal no-data or low-confidence analytics result, not a fabricated answer.
- Message asks a non-business topic with no dealer analytics terms -> out-of-scope reply.

- "全量数据中赢单数最多的经销商是谁？" -> analytics ranking reply, not entity-not-found for `谁`.
- "活动最多的经销商是谁？" -> campaign ranking reply, not entity-not-found for `谁`.

#### 5. Good/Base/Bad Cases
- Good: "全国范围内哪款车卖得最好？" routes to target/won-sales aggregation and returns the top product model from repository data.
- Good: "商机按阶段怎么分布？" routes to opportunity funnel aggregation and reports counts by `stageName`.
- Good: "任务类型前三是什么？" routes to task aggregation and limits the ranked subject list to three items.
- Base: Original workbook wording continues to produce the same deterministic counts.
- Bad: Adding a branch that checks one exact workbook question string and returns a fixed answer without reading repositories.

#### 6. Tests Required
- Unit regression: each new paraphrase class asserts the selected `AnalyticsPlan.Scenario` and concrete data-backed values.
- Topic-classifier regression: routing-priority changes in `AnalyticsTopicClassifier` should have focused tests in `service/analytics/AnalyticsTopicClassifierTest`.
- Matcher regression: pure direct-question predicates added to `DirectQuestionMatcher` should have focused tests in `service/analytics/DirectQuestionMatcherTest`.
- Chat regression: business-scope keywords include product-sales and purchase-cycle wording so analytics is called.
- Runtime regression for accuracy work: import the workbook, verify baseline counts, run the original workbook set, and run an anti-overfit paraphrase set.
- Negative regression: intro/greeting and out-of-scope messages do not accidentally call analytics.

#### 7. Wrong vs Correct

Wrong:
```java
if (message.equals("全国范围内哪款车卖得最好？")) {
    return fixedAnswer("Nova X");
}
```

Correct:
```java
if (asksTopSalesVolume(normalized) && mentionsProductDimension(normalized)) {
    return answerTopTargetAggregate(
            aggregateTargets(filteredTargets, Target::getProductModel),
            targetWonDescending());
}
```

### Low-Confidence Analytics Reports Preserve Chart Empty Fences

#### 1. Scope / Trigger
- Trigger: Any deterministic analytics scenario suppresses a chart because data quality is `NO_DATA`, `DENOMINATOR_ZERO`, `ALL_ZERO_SIGNAL`, or `INSUFFICIENT_SAMPLE`.
- This is a backend response contract because `RuleBasedAnalyticsService` emits Markdown and the frontend renderer depends on fenced chart blocks to show an explicit empty state.

#### 2. Signatures
- Analytics entry point:
  - `RuleBasedAnalyticsService.plan(String message, String language): AnalyticsPlan`
- Rendering helper:
  - `buildEnrichedReply(String language, String conclusion, List<String[]> dataRows, SummaryContext summaryContext, String mermaid, String fallback, List<String> attributions, List<String> recommendations, List<String> followUps): String`

#### 3. Contracts
- Low-confidence replies must include a `chart-empty` fenced block in the Data Support section.
- The `fallback` argument passed to `buildEnrichedReply` is renderable output, not disposable metadata.
- If `mermaid` and `fallback` are both present, render both in Data Support: primary chart first, fallback or empty-state block second.
- Low-confidence replies must avoid best-practice, benchmark, replication, or top-performer language.

#### 4. Validation & Error Matrix
- `quality.suppressChart() == true` and reply omits `chart-empty` -> frontend has no explicit chart empty state.
- `fallback` is passed but not appended by `buildEnrichedReply` -> chart-empty and ASCII fallback bars silently disappear.
- `ALL_ZERO_SIGNAL` or `INSUFFICIENT_SAMPLE` still emits ranking language -> reply overstates unreliable data.

#### 5. Good/Base/Bad Cases
- Good: Target achievement with zero denominator returns factual counts plus a `chart-empty` fence.
- Base: Normal-quality analytics may return chart JSON or Mermaid plus fallback bars.
- Bad: Low-confidence reply says the chart is hidden but contains no fenced empty-state block.

#### 6. Tests Required
- Unit regression: `RuleBasedAnalyticsServiceTest.assertLowConfidenceReply` must assert `fallbackReply()` contains a `chart-empty` fence and excludes benchmark/playbook language.
- Full backend regression: `mvn "-Dfrontend.skip=true" test` must pass after changing analytics report rendering.

#### 7. Wrong vs Correct

Wrong:
```java
if (mermaid != null && !mermaid.isBlank()) {
    body.append(mermaid);
}
```

Correct:
```java
if (mermaid != null && !mermaid.isBlank()) {
    body.append("\n").append(mermaid).append("\n");
}
if (fallback != null && !fallback.isBlank()) {
    body.append("\n").append(fallback).append("\n");
}
```

### Boundary Detection: Unknown Entity Cross-Check Against Database

#### 1. Scope / Trigger
- Trigger: A user message mentions an entity name (dealer, customer, store) that may not exist in the database.
- ChatService must intercept unknown-entity messages **before** they reach analytics, returning an entity-not-found reply instead of a fabricated analysis.

#### 2. Signatures
- `ChatService.extractUnknownDemoEntityName(String userMessage, String language): Optional<String>`
- `ChatService.isKnownDealer(String dealerName): boolean`
- Pattern constants for entity extraction:
  - `UNKNOWN_ZH_CUSTOMER_PATTERN` — matches "客户X" references
  - `UNKNOWN_ZH_DEALER_PATTERN` — matches "经销商不存在XYZ" references  
  - `UNKNOWN_ZH_DEALER_SUFFIX_PATTERN` — matches "经销商XYZ的..." references with entity suffix
  - `IMPLICIT_ZH_DEALER_PATTERN` — matches "经销商名叫XYZ" / "门店叫XYZ" implicit references
  - `UNKNOWN_GENERIC_ENTITY_PATTERN` — matches "经销商XYZ" type references needing DB cross-check

#### 3. Contracts
- Explicit unknown patterns (`UNKNOWN_ZH_CUSTOMER_PATTERN`, `UNKNOWN_ZH_DEALER_PATTERN`) return entity-not-found immediately without hitting the DB.
- Implicit and suffix patterns extract the entity name, then cross-check against `DealerRepository` via `isKnownDealer()`.
- `isKnownDealer()` checks both dealer codes and dealer names (case-insensitive).
- If the dealer is known, the message proceeds to analytics normally.
- If the dealer is unknown, return entity-not-found reply.
- Generic ranking/interrogative words are not entity names. Phrases such as `经销商是谁`, `哪个经销商`, `哪家门店`, `哪些经销商`, `最多`, `最高`, and `最低` must bypass unknown-entity interception and proceed to analytics routing.

#### 4. Validation & Error Matrix
- "经销商不存在XYZ的目标达成率怎么样？" but dealer XYZ is unknown → entity-not-found reply.
- "经销商名叫XYZ" (implicit pattern) and XYZ exists in DB → proceed to analytics.
- "经销商ABC的赢单率" (suffix pattern) and ABC is unknown → entity-not-found reply.
- "客户A的目标怎么样？" (customer pattern) → entity-not-found reply (no DB check needed).

#### 5. Good/Base/Bad Cases
- Good: "经销商不存在的门店的目标怎么样？" → entity-not-found reply.
- Base: "星星门店的目标达成率怎么样？" → extracts "星星门店", cross-checks DB, proceeds to analytics if found.
- Good: "全量数据中目标达成率最高的经销商是谁？" bypasses unknown-entity handling because `谁` is an interrogative placeholder, not a dealer name.
- Bad: Adding exact string matching for every unknown dealer name instead of using the pattern + DB cross-check pipeline.
- Bad: Treating `经销商是谁` or `活动最多的经销商是谁` as a literal unknown dealer entity and returning "未找到".

#### 6. Tests Required
- Pattern matching tests for each regex pattern (explicit, suffix, implicit, generic).
- `isKnownDealer` unit tests with known and unknown dealer names/codes.
- Integration test: unknown dealer message returns entity-not-found, known dealer proceeds to analytics.
- Chat regression: dealer ranking questions containing `谁` / `哪个` / `哪家` call `RuleBasedAnalyticsService.plan(...)` and do not return entity-not-found.

#### 7. Wrong vs Correct

Wrong:
```java
if (userMessage.contains("经销商不存在")) {
    return entityNotFoundReply();
}
// Missing: doesn't handle "经销商XYZ的..." suffix patterns
```

Correct:
```java
Pattern[] patterns = {
    UNKNOWN_ZH_CUSTOMER_PATTERN,
    UNKNOWN_ZH_DEALER_PATTERN,
    UNKNOWN_ZH_DEALER_SUFFIX_PATTERN,
    IMPLICIT_ZH_DEALER_PATTERN,
    UNKNOWN_GENERIC_ENTITY_PATTERN
};
Optional<String> extracted = extractUnknownDemoEntityName(userMessage, language);
if (extracted.isPresent() && !isKnownDealer(extracted.get())) {
    return entityNotFoundReply();
}
```

---

## Forbidden Patterns

- **Exact string matching on user questions** to return fixed answers. Always use semantic intent detection and route through analytics. See `ChatService.getBusinessScopeKeywords()` which defines a keyword-based classifier rather than exact-match dispatch.

- **Field-level `@Autowired`**. Use constructor injection. The single exception in the codebase (`RuleBasedAnalyticsService`) still uses `@Autowired` on the constructor, not on fields.

- **Public no-arg constructors on entities**. Always use `protected` to satisfy JPA while preventing accidental creation of invalid entities.

- **Setters on entities**. Entities are immutable after construction. No setters are present in any entity.

- **Logging secrets or API keys**. Use `MessageDigest.isEqual()` for comparison but never log keys, tokens, or authentication material.

- **Ambiguous keyword detection without priority ordering**. The `AnalyticsTopicClassifier.detect()` chain must order checks so specific combinations win over generic keywords. Example: "来源" alone is ambiguous (could be LEAD_SOURCE or OPPORTUNITY). When the message also contains "商机", the OPPORTUNITY check must execute before LEAD_SOURCE. Never add a high-priority keyword to a routing branch without verifying it doesn't overshadow more specific combinations earlier in the chain.

- **Returning hard-coded strings that bypass the repository layer**. All data returned to users must come from the database via repositories, rendered through the analytics pipeline.

- **Skipping the `fallback` parameter in `buildEnrichedReply`** when rendering analytics reports. If `fallback` is non-blank, it must be appended to the response body (see Low-Confidence Analytics Reports scenario above).

- **String concatenation for SQL/JPA queries**. Use Spring Data method derivation (`findBy...IgnoreCase`, `findBy...Between`). No `@Query` with JPQL has been needed in this project.

---

## Testing Requirements

### Framework and Libraries

- **JUnit 5 (Jupiter)** -- `@Test`, `@BeforeEach`
- **AssertJ** -- `assertThat(...).isEqualTo(...)`, `assertThat(...).isNotNull()`, etc.
- **Mockito** -- `mock()`, `when()`, `verify()`, `ArgumentCaptor`
- **Spring MockMvc** -- `MockMvcBuilders.standaloneSetup()` for controller tests, `MockMvcBuilders.webAppContextSetup()` for integration tests
- **Spring Mock** -- `MockHttpServletRequest`, `MockHttpServletResponse`, `MockFilterChain`

### Test File Location and Naming

Test files mirror the main source directory structure under `backend/src/test/java/`. Naming convention: `{ClassUnderTest}Test.java`.

Examples:
| Main Class | Test Class |
|---|---|
| `service/ChatService.java` | `service/ChatServiceTest.java` |
| `config/ApiKeyFilter.java` | `config/ApiKeyFilterTest.java` |
| `controller/AuthController.java` | `controller/AuthControllerTest.java` |
| `dto/response/ApiResult.java` | `dto/response/ApiResultTest.java` |

### Test Patterns

**Unit tests without Spring context** (preferred pattern):

```java
// ChatServiceTest.java:37-66
class ChatServiceTest {
    private SessionMemoryService sessionMemoryService;
    private LanguageDetector languageDetector;
    private RuleBasedAnalyticsService analyticsService;
    private PromptFactory promptFactory;
    private ModelConfigService modelConfigService;
    private DealerRepository dealerRepository;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        sessionMemoryService = mock(SessionMemoryService.class);
        languageDetector = mock(LanguageDetector.class);
        analyticsService = mock(RuleBasedAnalyticsService.class);
        promptFactory = mock(PromptFactory.class);
        modelConfigService = mock(ModelConfigService.class);
        dealerRepository = mock(DealerRepository.class);

        chatService = new ChatService(
                sessionMemoryService, languageDetector, analyticsService,
                promptFactory, modelConfigService, dealerRepository
        );
    }
}
```

**Filter tests with Spring Mock classes**:

```java
// ApiKeyFilterTest.java
class ApiKeyFilterTest {
    @Test
    void rejectsProtectedRequestsWithoutTheInternalApiKey() throws Exception {
        MockHttpServletResponse response = doFilter("POST", "/api/v1/data/dealers", null, appProperties("configured-api-key"));
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).isEqualTo("{\"code\":401,\"message\":\"Invalid API key\"}");
    }
}
```

**Controller tests with MockMvc**:

```java
// ChatControllerTest.java:47-50
mockMvc = MockMvcBuilders.standaloneSetup(
                new ChatController(chatService, sessionMemoryService, sessionOwnershipService))
        .setValidator(validator)
        .build();
```

**SSE payload tests normalize line endings**:

CI runs on Linux while local development may happen on Windows. Tests that
assert streamed SSE blocks must normalize `\r\n` to `\n`, or parse event data
through a helper, before checking ordering:

```java
String normalizedPayload = payload.replace("\r\n", "\n");
assertThat(normalizedPayload).containsSubsequence(
        "event: message\ndata: first",
        "event: message\ndata: second");
```

Prefer asserting parsed event data when a helper exists:

```java
assertThat(extractEventData(payload, "message")).containsSequence("first", "second");
```

### Running Tests And Static Analysis

```bash
# Run backend PMD only (skip frontend build)
mvn "-Dfrontend.skip=true" pmd:check

# Run backend tests only (skip frontend build)
mvn "-Dfrontend.skip=true" test

# Run all tests including frontend
mvn test
```

When documenting or running Maven system properties, quote the full `-D...`
argument. The quoted form works in Bash and PowerShell, and it prevents
PowerShell from parsing comma-separated values such as `-Dtest=A,B` as an
argument list:

```bash
mvn "-Dfrontend.skip=true" "-Dtest=AccuracyWorkbookRegressionTest,RuleBasedAnalyticsServiceTest" test
```

When launching Maven from a background PowerShell automation such as
`Start-Process`, prefer calling `mvn.cmd` directly with an argument array
instead of embedding the quoted Maven command inside another PowerShell
script string. Nested PowerShell command strings can still split
`-Dfrontend.skip=true` incorrectly and make Maven see `.skip=true` as a
lifecycle phase.

```powershell
Start-Process -FilePath "mvn.cmd" -ArgumentList @("-Dfrontend.skip=true", "spring-boot:run")
```

### Expected Test Coverage

- Controller tests: cover auth checks, request validation, success and error response paths
- Service tests: cover business logic, routing decisions, edge cases
- Filter tests: cover allow/deny decisions, error response format
- DTO tests: verify factory methods and serialization behavior
- AI tool tests: verify tool registration and parameter handling

---

## Scenario: Report Draft Generation

### 1. Scope / Trigger

- Trigger: an authenticated browser or controlled Agent request needs a daily, weekly, monthly, or topic report derived from the current Dashboard snapshot.
- This is a cross-layer API, security, application-port, persistence, migration, and chat/SSE contract.

### 2. Signatures

- HTTP:
  - `POST /api/reports/drafts -> ApiResult<ReportDraft>`
  - `GET /api/reports/drafts -> ApiResult<List<ReportDraft>>`
  - `GET /api/reports/drafts/{id} -> ApiResult<ReportDraft>`
  - `GET /api/reports/drafts/{id}/markdown -> text/markdown;charset=UTF-8`
- Application entry point: `ReportService.generate(ReportGenerationRequest): ReportDraft`
- Controlled tool: `generateReportDraft(String reportType, String language, String topic): ReportDraft`
- Production schema: `db/postgresql/V3__create_report_drafts.sql -> report_drafts`
- Store profiles: `InMemoryReportDraftStore` for `!prod`; `JdbcReportDraftStore` for `prod`.

### 3. Contracts

- Request fields:
  - `reportType`: required; `daily`, `weekly`, `monthly`, or `topic` (domain parsing may also accept the documented Chinese aliases).
  - `language`: required; exactly `zh` or `en` after case normalization.
  - `scopeType`: optional; defaults to `GLOBAL`. No other scope is supported in P1-5.
  - `scopeId`: must be blank for `GLOBAL`.
  - `topic`: required for a topic report and limited to 500 characters.
- Every persisted draft records `reportType`, `language`, `markdown`, `generatedAt`, `importBatchId`, `scope`, `model`, and `promptVersion`.
- Generation reads `DashboardService.getSummary()` and the active batch only. It must not infer unavailable trends, causes, organization scopes, or historical facts.
- The controller returns the standard `ApiResult` envelope for JSON endpoints. The OpenAPI response schema must describe that envelope, not a bare draft or array.
- `/api/reports/**` bypasses the internal `X-API-Key` filter but remains protected by the browser Bearer session filter.
- Sync and SSE chat may call the reporting port directly only for an explicit report request with an authenticated, `activeBatchOnly=true` `AgentRequestScope`; both return the same recorded Markdown draft.
- Markdown is the only export in P1-5. PDF/Word export, subscriptions, organization scope, and tenant history are outside this contract.

### 4. Validation & Error Matrix

- Missing request, report type, or language -> HTTP 400 / `IllegalArgumentException`; do not read Dashboard data.
- Unsupported report type or language -> HTTP 400; do not save a draft.
- `topic` report with blank topic, or topic longer than 500 characters -> HTTP 400; do not save a draft.
- Non-`GLOBAL` scope or non-blank `scopeId` -> HTTP 400; do not read or save data.
- Missing draft ID -> HTTP 400. Unknown draft ID -> HTTP 404.
- Missing/invalid Bearer session on `/api/reports/**` -> HTTP 401 from `SessionTokenFilter`.
- Authenticated chat scope with `activeBatchOnly=false` -> do not generate a report draft through the direct sync/SSE path.
- Production database or migration failure -> fail production startup/operation; never silently replace the JDBC store with memory.

### 5. Good/Base/Bad Cases

- Good: an authenticated weekly request reads one active-batch Dashboard snapshot, persists its batch/scope/version metadata, and returns the same draft through JSON and Markdown export.
- Good: local tests use the in-memory adapter without PostgreSQL or model infrastructure.
- Base: an empty configured model name is recorded as `deterministic`; a configured model name is preserved as generation metadata.
- Bad: a report aggregates repositories directly, uses inactive batches, or fabricates weekly/monthly trends from a point-in-time snapshot.
- Bad: a `prod` context creates `InMemoryReportDraftStore` after a JDBC failure.

### 6. Tests Required

- `ReportServiceTest`: type/language/scope/topic validation, active-batch metadata, Chinese encoding, persistence, and newest-first listing.
- `ReportMarkdownRendererTest`: supported type aliases and deterministic rendering helpers.
- `JdbcReportDraftStoreTest`: round-trip every persisted metadata field and preserve generated timestamps.
- `ReportControllerTest`: `ApiResult` JSON envelope, validation/not-found mapping, UTF-8 Markdown body, and attachment filename.
- `ChatServiceTest`: authenticated sync/SSE report routing uses the same reporting port and bypasses model/rule analytics; unauthenticated compatibility calls must not create drafts.
- Agent policy/callback tests: exact six-tool allowlist, shared four-call budget, and scope denial before delegate execution.
- Final gates: UTF-8 JSON parse of `static/openapi.json`, `mvn "-Dfrontend.skip=true" pmd:check`, and `mvn "-Dfrontend.skip=true" test`.

### 7. Wrong vs Correct

Wrong:

```java
@Profile("prod")
class ReportService {
    ReportDraft generate(...) {
        return memoryStore.save(renderFromAllRepositoryRows());
    }
}
```

Correct:

```java
DashboardSummary summary = dashboardService.getSummary();
ReportDraft draft = new ReportDraft(
        id, reportType, title, language,
        renderer.render(reportType, language, summary, topic, batchId),
        generatedAt, batchId, ReportScope.global(), model, PROMPT_VERSION);
return draftStore.save(draft); // !prod memory adapter; prod JDBC adapter
```

---

## Code Review Checklist

- [ ] Constructor injection used (no `@Autowired` on fields)
- [ ] DTOs are immutable records
- [ ] `@Valid` applied to request bodies in controllers
- [ ] Entities have `protected` no-arg constructor, getters only, no setters
- [ ] Repository methods use Spring Data query derivation (no manual `@Query` unless necessary)
- [ ] `@Column(nullable, length)` specified on all entity fields
- [ ] Authentication uses `MessageDigest.isEqual()` for constant-time comparison
- [ ] Logger declared as `private static final Logger log = LoggerFactory.getLogger(ClassName.class)`
- [ ] `log.warn()` for security events (auth failures), `log.info()` for operational milestones, `log.debug()` for row-level details
- [ ] Key=value structured logging for auth events (`path={}, remoteAddress={}, reason={}`)
- [ ] Errors caught and handled at the appropriate layer (controller maps to HTTP, service degrades gracefully)
- [ ] No hard-coded strings bypassing the repository/analytics pipeline
- [ ] Streamed replies respect `MAX_STREAMED_REPLY_CHARS` limit
- [ ] No secrets, tokens, or PII logged at any level
- [ ] Test file mirrors main source path, named `{ClassName}Test.java`
- [ ] Tests use AssertJ assertions (not JUnit `assertEquals`)
