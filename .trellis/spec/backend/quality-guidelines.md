# Quality Guidelines

> Code quality standards for backend development.

---

## Overview

The project uses JUnit 5 (Jupiter) with AssertJ assertions and Mockito for mocking. Tests follow the same package structure as main source. The project does not use `@SpringBootTest` for unit tests -- manual wiring via constructors is preferred. The build runs via `mvn test` (with optional `-Dfrontend.skip=true` to skip frontend build).

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

### Stream Chunking for Large Responses

ChatService limits streamed output to `MAX_STREAMED_REPLY_CHARS = 32_000` characters and uses a dedicated error message when the limit is exceeded:

```java
private static final String STREAMED_REPLY_LIMIT_MESSAGE =
        "The streamed reply exceeded the allowed output limit.";
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
  - `RuleBasedAnalyticsService.detectTopic(String message): AnalysisTopic`
  - Direct-answer helpers under target, opportunity, task, lead, and campaign analysis.

#### 3. Contracts
- Route analytics questions by business intent plus metric/dimension semantics, not by exact workbook wording.
- Product-sales paraphrases such as "which model sold best" / "which car sells best" must remain in business scope and route to target or won-sales aggregation.
- Completion-rate paraphrases such as "highest target completion rate" and "highest achievement rate" must route to target achievement aggregation.
- Breakdown paraphrases such as "by stage distribution", "status respectively how many", "task type top three", and "source/channel distribution" must route to the relevant entity aggregation.
- Multi-entity count questions that mention two or more of opportunity, lead, task, and campaign must route to `DATA_OVERVIEW` before single-entity campaign/task/lead routing.
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

- **Ambiguous keyword detection without priority ordering**. The `detectTopic()` chain in `RuleBasedAnalyticsService` must order checks so specific combinations win over generic keywords. Example: "来源" alone is ambiguous (could be LEAD_SOURCE or OPPORTUNITY). When the message also contains "商机", the OPPORTUNITY check must execute before LEAD_SOURCE. Never add a high-priority keyword to a routing branch without verifying it doesn't overshadow more specific combinations earlier in the chain.

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

### Running Tests

```bash
# Run backend tests only (skip frontend build)
mvn test -Dfrontend.skip=true

# Run all tests including frontend
mvn test
```

### Expected Test Coverage

- Controller tests: cover auth checks, request validation, success and error response paths
- Service tests: cover business logic, routing decisions, edge cases
- Filter tests: cover allow/deny decisions, error response format
- DTO tests: verify factory methods and serialization behavior
- AI tool tests: verify tool registration and parameter handling

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
