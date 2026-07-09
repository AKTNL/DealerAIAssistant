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
          AuthService.java
          AuthRateLimitService.java
          SessionTokenService.java
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
            AuthVerifyRequest.java
            ModelConfigRequest.java
          response/                    # Outbound payloads
            ChatResponse.java
            ApiResult.java
            ApiPage.java
            AuthVerifyResponse.java
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
        config/                        # Spring config and filters
          AppProperties.java           # @ConfigurationProperties(prefix = "app")
          AiConfig.java                # @Configuration
          CorsConfig.java              # @Configuration
          ApiKeyFilter.java            # @Component, OncePerRequestFilter
          SessionTokenFilter.java      # @Component, OncePerRequestFilter
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
          AuthControllerTest.java
          ...
        service/
          ChatServiceTest.java
          RuleBasedAnalyticsServiceTest.java
          ...
        config/
          ApiKeyFilterTest.java
          ...
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

1. **Controllers** live in `controller/`. Each controller handles one API path prefix (e.g., `/api/chat`, `/api/auth`). Annotated with `@RestController` and `@RequestMapping`.

2. **Services** live in `service/`. One `@Service` per domain concern. If a service grows large enough to need helper classes, create a sub-package under `service/` (see `service/analytics/`).

3. **Entities** live in `entity/`. One `@Entity` per database table. No business logic in entities -- they are pure data carriers.

4. **Repositories** live in `repository/`. One interface per entity, extending `JpaRepository<Entity, Long>`. No custom implementations needed for standard query derivation.

5. **DTOs** are Java records organized by direction and purpose under `dto/request/`, `dto/response/`, `dto/metrics/`, and `dto/detail/`.

6. **Configuration** classes live in `config/`. Annotated with `@Configuration` or `@Component` depending on Spring role.

7. **AI tool callbacks** live in `ai/`. These are `@Tool`-annotated methods exposed to Spring AI's `MethodToolCallbackProvider`.

---

## Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Controller | `{Domain}Controller` | `ChatController`, `AuthController` |
| Service | `{Domain}Service` | `ChatService`, `ExcelImportService` |
| Entity | `{SingularEntity}` | `Dealer`, `Opportunity`, `Lead` |
| Repository | `{Entity}Repository` | `DealerRepository`, `CampaignRepository` |
| DTO record | `{Purpose}` | `ChatRequest`, `ChatResponse`, `ApiResult` |
| Config class | `{Feature}Config` or `{Feature}Filter` | `CorsConfig`, `ApiKeyFilter` |
| AI tools | `{Domain}Tools` | `DealerTools`, `CampaignTools` |
| Test class | `{ClassUnderTest}Test` | `ChatServiceTest`, `ApiKeyFilterTest` |
| Table name | Plural lowercase, underscores | `dealers`, `dealer_tasks`, `dealer_targets` |

---

## Examples

Well-organized modules to use as reference:

- `controller/ChatController.java` -- Constructor injection, `@Valid` request bodies, session ownership checks, streaming endpoint, cleanup endpoint
- `service/ChatService.java` -- Service with business scope detection, analytics routing, stream-based response, and fallback handling
- `service/analytics/` -- Sub-package pattern for services with helper classes (`AnalyticsCalculator`, `DirectQuestionMatcher`, `ReportRenderer`)
- `config/AppProperties.java` -- Type-safe configuration with inner static classes per config domain
- `entity/Dealer.java` -- Canonical entity: `@Entity`, `@Table`, `@Id` with IDENTITY generation, `@Column` constraints, `protected` no-arg constructor, constructor injection, getters only
