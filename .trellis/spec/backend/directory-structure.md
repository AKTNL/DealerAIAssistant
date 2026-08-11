# Directory Structure

> How backend code is organized in this project.

---

## Overview

The backend is a Spring Boot 3.4.5 application with the Maven group `com.brand.agentpoc`. All source lives under `backend/src/main/java/com/brand/agentpoc/`.

The package is flat at the top level, with domain sub-packages for DTOs and analytics. Controllers, services, entities, repositories, config, and AI tool classes each have their own peer package.

---

## Directory Layout

```
backend/
  pom.xml                        # Spring Boot 3.4.5, Java 21, H2, Spring AI
  src/
    main/
      java/com/brand/agentpoc/
        AgentPocApplication.java       # @SpringBootApplication entry point
        controller/                    # REST controllers (@RestController)
          ChatController.java
          AuthController.java
          AnalyticsApiController.java
          DataQueryController.java
          ModelConfigController.java
        service/                       # Business logic (@Service)
          ChatService.java
          RuleBasedAnalyticsService.java
          ExcelImportService.java
          DataQueryService.java
          AnalyticsApiService.java
          AuthRateLimitService.java
          SessionMemoryService.java
          SessionOwnershipService.java
          ModelConfigService.java
          InMemoryChatMemory.java
          AnalyticsPlan.java
          AnalyticsScenarioCatalog.java
          StepType.java
          StepEvent.java
          analytics/                   # Service sub-package
            AnalyticsCalculator.java
            DirectQuestionMatcher.java
            ReportRenderer.java
        entity/                        # JPA entities (@Entity)
          Dealer.java
          Opportunity.java
          Lead.java
          Task.java
          Target.java
          Campaign.java
        repository/                    # Spring Data JPA interfaces
          DealerRepository.java
          OpportunityRepository.java
          LeadRepository.java
          TaskRepository.java
          TargetRepository.java
          CampaignRepository.java
        dto/
          request/                     # Inbound payloads
            ChatRequest.java
            ModelConfigRequest.java
          response/                    # Outbound payloads
            ChatResponse.java
            ApiResult.java
            ApiPage.java
            SimpleSuccessResponse.java
            CurrentDateResponse.java
            DataQueryResponse.java
            ModelConfigTestResponse.java
          metrics/                     # Analytics metric records
            TargetMetrics.java
            OpportunityMetrics.java
            LeadMetrics.java
            TaskMetrics.java
            CampaignMetrics.java
          detail/                      # Detail view records
            TargetDetail.java
            OpportunityDetail.java
            LeadDetail.java
            TaskDetail.java
            CampaignDetail.java
        config/                        # Shared Spring configuration
          AppProperties.java           # @ConfigurationProperties(prefix = "app")
          AiConfig.java                # @Configuration
          CorsConfig.java              # @Configuration
        auth/                          # Database identity, session, RBAC module
          controller/
          application/
          domain/
          infrastructure/
            persistence/
        ai/                            # Spring AI tool callbacks
          PromptFactory.java
          LanguageDetector.java
          CalcStep.java
          CurrentDateTools.java
          DealerTools.java
          OpportunityTools.java
          CampaignTools.java
          TaskTools.java
          TargetTools.java
          LeadTools.java
          ToolFilterSupport.java
      resources/
        application.yml
    test/
      java/com/brand/agentpoc/
        # Mirrors main source structure
        controller/
          ChatControllerTest.java
          ...
        service/
          ChatServiceTest.java
          RuleBasedAnalyticsServiceTest.java
          ...
        auth/
          AuthHttpIntegrationTest.java
          controller/
          infrastructure/
        dto/
          response/
            ApiResultTest.java
          ...
        ai/
          ...
```

---

## Module Organization

New features follow these rules:

1. **Legacy controllers** live in `controller/`. New business-first modules keep protocol adapters in `<module>/controller/` (for example `auth/controller/AuthController`). Each controller handles one API path prefix and uses `@RestController` plus `@RequestMapping`.

2. **Services** live in `service/`. One `@Service` per domain concern. If a service grows large enough to need helper classes, create a sub-package under `service/` (see `service/analytics/`).

3. **Entities** live in `entity/`. One `@Entity` per database table. No business logic in entities -- they are pure data carriers.

4. **Repositories** live in `repository/`. One interface per entity, extending `JpaRepository<Entity, Long>`. No custom implementations needed for standard query derivation.

5. **DTOs** are Java records organized by direction and purpose under `dto/request/`, `dto/response/`, `dto/metrics/`, and `dto/detail/`.

6. **Configuration** classes live in `config/`. Annotated with `@Configuration` or `@Component` depending on Spring role.

7. **AI tool callbacks** live in `ai/`. These are `@Tool`-annotated methods exposed to Spring AI's `MethodToolCallbackProvider`.

## P1-2 Modular Monolith Transition

New business capabilities should use a business-first top-level package instead of adding another class to the legacy root `service/` package. The target module names are `auth`, `organization`, `dataimport`, `metrics`, `dashboard`, `analytics`, `agent`, `reporting`, and `knowledge`.

Each migrated module should converge on this internal shape:

```text
com.brand.agentpoc.<module>/
  controller/          # HTTP or SSE protocol adapters
  application/         # use-case orchestration and public module entry points
  domain/              # business rules and stable domain contracts
  infrastructure/      # database, file, model, and other external adapters
```

During the transition, `controller/`, `service/`, `ai/`, `dto/`, `entity/`, and `repository/` remain compatibility packages. They must not receive new responsibilities merely because they already exist. Cross-module calls should use a public application service or port rather than another module's repository, entity, or internal implementation.

The first migrated agent slice was `com.brand.agentpoc.agent.ChatReplyGuard`. P1-3 adds the controlled runtime under the module's standard layers:

```text
com.brand.agentpoc.agent/
  application/       # ControlledAgentToolService, AgentScopeVerifier, tool result records
  domain/            # tool names, data kinds, request scope, policy, execution context
  infrastructure/   # Spring AI adapters/callbacks and session ownership verification
  ChatReplyGuard.java
```

P1-4 adds the first complete `knowledge` module slice:

```text
com.brand.agentpoc.knowledge/
  application/       # framework-neutral index/source ports, retrieval use case, chunker, answer composer
  domain/            # document, chunk, query, hit, result, and knowledge-type records
  infrastructure/   # catalog/resource loader, memory and PGvector adapters, bean config, startup bootstrap
```

P1-5 adds the first complete `reporting` module slice:

```text
com.brand.agentpoc.reporting/
  application/       # report generation use case, Markdown renderer, draft store port
  domain/            # report type, global scope, and draft contracts
  infrastructure/   # in-memory local adapter and JDBC production adapter
```

P2-1 adds the database-backed `auth` module:

```text
com.brand.agentpoc.auth/
  controller/        # login/session and user/role administration APIs
  application/       # session lifecycle, administration, audit, input policy
  domain/            # principal, fixed permission catalog, built-in role matrix
  infrastructure/    # Spring Security, bootstrap, JSON errors, JPA persistence
```

Auth persistence classes stay under `auth/infrastructure/persistence`; do not place them in the legacy root `entity/` or `repository/` packages. See [Authentication and Authorization](./authentication-authorization.md).

`service.ChatService` may reference these public/application and infrastructure integration boundaries while the rest of chat orchestration remains in the legacy service package. `agent.domain` and `knowledge.domain/application` must stay free of Spring AI, Servlet, repository, JDBC, resource-loader, and model-SDK dependencies; knowledge Spring wiring belongs in infrastructure. The controlled application service reuses existing public services (`DashboardService`, `AnalyticsApiService`, `RuleBasedAnalyticsService`, and `KnowledgeService`) and must not access repositories or vector-store adapters directly. This seam keeps the existing HTTP/SSE contract stable and makes the migration reversible.

Tests mirror the package of the migrated production class. For example, `agent/domain/AgentExecutionPolicy.java` is covered by `agent/domain/AgentExecutionPolicyTest.java`, knowledge application/infrastructure tests mirror their production packages, and existing `service/ChatServiceTest.java` remains the regression guard for the compatibility caller, knowledge routing, and request-scoped callback registration.

---

## Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Controller | `{Domain}Controller` | `ChatController`, `AuthController` |
| Service | `{Domain}Service` | `ChatService`, `ExcelImportService` |
| Entity | `{SingularEntity}` | `Dealer`, `Opportunity`, `Lead` |
| Repository | `{Entity}Repository` | `DealerRepository`, `CampaignRepository` |
| DTO record | `{Purpose}` | `ChatRequest`, `ChatResponse`, `ApiResult` |
| Config class | `{Feature}Config` or `{Feature}Configuration` | `CorsConfig`, `AuthSecurityConfiguration` |
| AI tools | `{Domain}Tools` | `DealerTools`, `CampaignTools` |
| Test class | `{ClassUnderTest}Test` | `ChatServiceTest`, `AuthBootstrapTest` |
| Table name | Plural lowercase, underscores | `dealers`, `dealer_tasks`, `dealer_targets` |

---

## Examples

Well-organized modules to use as reference:

- `controller/ChatController.java` -- Constructor injection, `@Valid` request bodies, session ownership checks, streaming endpoint, cleanup endpoint
- `service/ChatService.java` -- Service with business scope detection, analytics routing, stream-based response, and fallback handling
- `service/analytics/` -- Sub-package pattern for services with helper classes (`AnalyticsCalculator`, `DirectQuestionMatcher`, `ReportRenderer`)
- `config/AppProperties.java` -- Type-safe configuration with inner static classes per config domain
- `entity/Dealer.java` -- Canonical entity: `@Entity`, `@Table`, `@Id` with IDENTITY generation, `@Column` constraints, `protected` no-arg constructor, constructor injection, getters only
