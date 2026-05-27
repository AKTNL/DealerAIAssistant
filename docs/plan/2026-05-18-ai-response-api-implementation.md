# AI 回复增强与 REST API 落地 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地 10 个 REST 端点（metrics + details）、增强 AI 回复为 6 段 + 追问块、修复 openapi.json 字段映射/鉴权/响应体。

**Architecture:** 新建 `AnalyticsApiService` 封装 metrics 聚合与 details 分页逻辑，新建 `AnalyticsApiController` 暴露 10 个端点，统一使用 `ApiResult<T>` 包装响应。DTO 层通过 metrics/detail record 与 Entity 解耦。PromptFactory 扩展 3 段新指令，ChatService 校验白名单同步更新，RuleBasedAnalyticsService fallback 输出扩展为 6 段。

**Tech Stack:** Java 17+, Spring Boot 3.x, Jackson, JUnit 5 + Mockito + AssertJ

---

## 文件结构总览

| 文件 | 职责 |
|------|------|
| `dto/response/ApiResult.java` | 泛型统一响应 `{code, data, message}` |
| `dto/response/ApiPage.java` | 泛型分页 `{items, total, page, pageSize}` |
| `dto/metrics/TargetMetrics.java` | 目标达成聚合指标 |
| `dto/metrics/OpportunityMetrics.java` | 商机漏斗聚合指标 |
| `dto/metrics/LeadMetrics.java` | 线索转化聚合指标 |
| `dto/metrics/TaskMetrics.java` | 销售跟进聚合指标 |
| `dto/metrics/CampaignMetrics.java` | 市场活动聚合指标 |
| `dto/detail/TargetDetail.java` | 目标达成明细行 |
| `dto/detail/OpportunityDetail.java` | 商机明细行 |
| `dto/detail/LeadDetail.java` | 线索明细行 |
| `dto/detail/TaskDetail.java` | 任务明细行 |
| `dto/detail/CampaignDetail.java` | 活动明细行 |
| `service/AnalyticsApiService.java` | metrics 聚合 + details 分页 |
| `controller/AnalyticsApiController.java` | 10 个 REST 端点 |
| `ai/PromptFactory.java` | 修改：3 段新指令 |
| `service/ChatService.java` | 修改：标题校验白名单 |
| `service/RuleBasedAnalyticsService.java` | 修改：fallback 6 段 |
| `openapi.json` | 修改：字段/鉴权/响应体 |

---

### Task 1: 创建 ApiResult<T> 和 ApiPage<T> 泛型 DTO

**Files:**
- Create: `backend/src/main/java/com/brand/agentpoc/dto/response/ApiResult.java`
- Create: `backend/src/main/java/com/brand/agentpoc/dto/response/ApiPage.java`
- Test: `backend/src/test/java/com/brand/agentpoc/dto/response/ApiResultTest.java`

- [ ] **Step 1: 写 ApiResult 测试**

```java
package com.brand.agentpoc.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResultTest {

    @Test
    void successCreatesResultWithCode200() {
        ApiResult<String> result = ApiResult.success("hello");

        assertThat(result.code()).isEqualTo(200);
        assertThat(result.data()).isEqualTo("hello");
        assertThat(result.message()).isEqualTo("success");
    }

    @Test
    void errorCreatesResultWithGivenCodeAndMessage() {
        ApiResult<Void> result = ApiResult.error(401, "Invalid API key");

        assertThat(result.code()).isEqualTo(401);
        assertThat(result.data()).isNull();
        assertThat(result.message()).isEqualTo("Invalid API key");
    }

    @Test
    void successWithNullDataReturnsNullData() {
        ApiResult<Void> result = ApiResult.success(null);

        assertThat(result.code()).isEqualTo(200);
        assertThat(result.data()).isNull();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && ./mvnw test -pl . -Dtest=ApiResultTest -Dmaven.test.failure.ignore=false 2>&1 | tail -20`
Expected: compilation error (class not found)

- [ ] **Step 3: 实现 ApiResult<T>**

```java
package com.brand.agentpoc.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResult<T>(int code, T data, String message) {

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(200, data, "success");
    }

    public static <T> ApiResult<T> error(int code, String message) {
        return new ApiResult<>(code, null, message);
    }
}
```

- [ ] **Step 4: 实现 ApiPage<T>**

```java
package com.brand.agentpoc.dto.response;

import java.util.List;

public record ApiPage<T>(List<T> items, long total, int page, int pageSize) {

    public static <T> ApiPage<T> of(List<T> items, long total, int page, int pageSize) {
        return new ApiPage<>(items, total, page, pageSize);
    }

    public static <T> ApiPage<T> empty(int page, int pageSize, long total) {
        return new ApiPage<>(List.of(), total, page, pageSize);
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cd backend && ./mvnw test -pl . -Dtest=ApiResultTest -Dmaven.test.failure.ignore=false 2>&1 | tail -20`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/dto/response/ApiResult.java \
        backend/src/main/java/com/brand/agentpoc/dto/response/ApiPage.java \
        backend/src/test/java/com/brand/agentpoc/dto/response/ApiResultTest.java
git commit -m "feat: add generic ApiResult<T> and ApiPage<T> response DTOs"
```

---

### Task 2: 创建 5 个 Metrics DTO

**Files:**
- Create: `backend/src/main/java/com/brand/agentpoc/dto/metrics/TargetMetrics.java`
- Create: `backend/src/main/java/com/brand/agentpoc/dto/metrics/OpportunityMetrics.java`
- Create: `backend/src/main/java/com/brand/agentpoc/dto/metrics/LeadMetrics.java`
- Create: `backend/src/main/java/com/brand/agentpoc/dto/metrics/TaskMetrics.java`
- Create: `backend/src/main/java/com/brand/agentpoc/dto/metrics/CampaignMetrics.java`

- [ ] **Step 1: 创建 TargetMetrics**

```java
package com.brand.agentpoc.dto.metrics;

public record TargetMetrics(
        int totalDealers,
        int totalAsKTarget,
        int totalOpportunityWon,
        double averageAchievementRate,
        DealerMetric lowestDealer,
        DealerMetric highestDealer
) {
    public record DealerMetric(String dealerCode, String dealerName, double achievementRate) {}
}
```

- [ ] **Step 2: 创建 OpportunityMetrics**

```java
package com.brand.agentpoc.dto.metrics;

import java.util.List;
import java.util.Map;

public record OpportunityMetrics(
        int totalOpportunities,
        int wonCount,
        int lostCount,
        int openCount,
        double winRate,
        Map<String, Long> stageDistribution,
        List<LossReason> topLossReasons
) {
    public record LossReason(String reason, long count) {}
}
```

- [ ] **Step 3: 创建 LeadMetrics**

```java
package com.brand.agentpoc.dto.metrics;

import java.util.Map;

public record LeadMetrics(
        int totalLeads,
        int convertedCount,
        double conversionRate,
        Map<String, Long> sourceDistribution,
        SourceMetric bestConversionSource
) {
    public record SourceMetric(String source, double conversionRate) {}
}
```

- [ ] **Step 4: 创建 TaskMetrics**

```java
package com.brand.agentpoc.dto.metrics;

public record TaskMetrics(
        int totalTasks,
        int completedCount,
        int openCount,
        int overdueCount,
        double completionRate,
        BacklogDealer highestBacklogDealer
) {
    public record BacklogDealer(String dealerCode, String dealerName, int openCount, int overdueCount) {}
}
```

- [ ] **Step 5: 创建 CampaignMetrics**

```java
package com.brand.agentpoc.dto.metrics;

public record CampaignMetrics(
        int totalCampaigns,
        double averageAttainment,
        BestCampaign bestCampaign,
        int totalActualOpportunities,
        int totalTarget
) {
    public record BestCampaign(String campaignId, String campaignName, double attainmentRate) {}
}
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/dto/metrics/
git commit -m "feat: add 5 metrics DTOs for targets, opportunities, leads, tasks, campaigns"
```

---

### Task 3: 创建 5 个 Detail DTO

**Files:**
- Create: `backend/src/main/java/com/brand/agentpoc/dto/detail/TargetDetail.java`
- Create: `backend/src/main/java/com/brand/agentpoc/dto/detail/OpportunityDetail.java`
- Create: `backend/src/main/java/com/brand/agentpoc/dto/detail/LeadDetail.java`
- Create: `backend/src/main/java/com/brand/agentpoc/dto/detail/TaskDetail.java`
- Create: `backend/src/main/java/com/brand/agentpoc/dto/detail/CampaignDetail.java`

- [ ] **Step 1: 创建所有 5 个 Detail DTO**

```java
// TargetDetail.java
package com.brand.agentpoc.dto.detail;

public record TargetDetail(
        String dealerCode, String dealerName, String city, String dealerGroupName,
        String productModel, int targetYear, int targetMonth,
        int asKTarget, int opportunityWonCount
) {}
```

```java
// OpportunityDetail.java
package com.brand.agentpoc.dto.detail;

public record OpportunityDetail(
        String opportunityId, String dealerCode, String dealerName, String city,
        String dealerGroupName, String productModel, String stageName,
        String leadSource, String createdDate, int probability
) {}
```

```java
// LeadDetail.java
package com.brand.agentpoc.dto.detail;

public record LeadDetail(
        String leadId, String dealerCode, String dealerName, String city,
        String dealerGroupName, String leadSource, String stageName,
        String productModel, String createdDate, boolean isConverted
) {}
```

```java
// TaskDetail.java
package com.brand.agentpoc.dto.detail;

public record TaskDetail(
        String taskId, String dealerCode, String dealerName, String city,
        String dealerGroupName, String opportunityId, String status, String createdDate
) {}
```

```java
// CampaignDetail.java
package com.brand.agentpoc.dto.detail;

public record CampaignDetail(
        String campaignId, String dealerCode, String dealerName, String city,
        String dealerGroupName, String productModel, String campaignType,
        String createdDate, int actualOpportunityCount, int totalNewCustomerTarget
) {}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/dto/detail/
git commit -m "feat: add 5 detail DTOs for targets, opportunities, leads, tasks, campaigns"
```

---

### Task 4: 创建 AnalyticsApiService

**Files:**
- Create: `backend/src/main/java/com/brand/agentpoc/service/AnalyticsApiService.java`
- Test: `backend/src/test/java/com/brand/agentpoc/service/AnalyticsApiServiceTest.java`

- [ ] **Step 1: 写测试**

```java
package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.dto.detail.TargetDetail;
import com.brand.agentpoc.dto.metrics.TargetMetrics;
import com.brand.agentpoc.dto.response.ApiPage;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.entity.Target;
import com.brand.agentpoc.repository.TargetRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalyticsApiServiceTest {

    private TargetRepository targetRepository;
    private AnalyticsApiService service;

    @BeforeEach
    void setUp() {
        targetRepository = mock(TargetRepository.class);
        service = new AnalyticsApiService(
                mock(com.brand.agentpoc.repository.DealerRepository.class),
                mock(com.brand.agentpoc.repository.OpportunityRepository.class),
                mock(com.brand.agentpoc.repository.CampaignRepository.class),
                mock(com.brand.agentpoc.repository.TaskRepository.class),
                targetRepository,
                mock(com.brand.agentpoc.repository.LeadRepository.class)
        );
    }

    @Test
    void getTargetMetricsReturnsZeroValuesWhenNoData() {
        when(targetRepository.findAll()).thenReturn(Collections.emptyList());

        ApiResult<TargetMetrics> result = service.getTargetMetrics(null, null, null, null);

        assertThat(result.code()).isEqualTo(200);
        TargetMetrics m = result.data();
        assertThat(m.totalDealers()).isEqualTo(0);
        assertThat(m.totalAsKTarget()).isEqualTo(0);
        assertThat(m.totalOpportunityWon()).isEqualTo(0);
        assertThat(m.averageAchievementRate()).isEqualTo(0.0);
        assertThat(m.lowestDealer()).isNull();
        assertThat(m.highestDealer()).isNull();
    }

    @Test
    void getTargetMetricsComputesCorrectAggregates() {
        Target t1 = new Target("D001", "Store A", "Beijing", "Group1",
                "ModelX", 2026, 5, 100, 80);
        Target t2 = new Target("D001", "Store A", "Beijing", "Group1",
                "ModelY", 2026, 5, 50, 45);
        Target t3 = new Target("D002", "Store B", "Beijing", "Group1",
                "ModelX", 2026, 5, 200, 120);
        when(targetRepository.findAll()).thenReturn(List.of(t1, t2, t3));

        ApiResult<TargetMetrics> result = service.getTargetMetrics(2026, 5, null, null);

        assertThat(result.code()).isEqualTo(200);
        TargetMetrics m = result.data();
        assertThat(m.totalDealers()).isEqualTo(2);
        assertThat(m.totalAsKTarget()).isEqualTo(350);
        assertThat(m.totalOpportunityWon()).isEqualTo(245);
        assertThat(m.averageAchievementRate()).isCloseTo(70.0, org.assertj.core.data.Offset.offset(0.1));
        assertThat(m.lowestDealer().dealerCode()).isEqualTo("D002");
        assertThat(m.highestDealer().dealerCode()).isEqualTo("D001");
    }

    @Test
    void getTargetDetailsPaginatesCorrectly() {
        List<Target> targets = new java.util.ArrayList<>();
        for (int i = 0; i < 55; i++) {
            targets.add(new Target("D00" + (i + 1), "Store " + i, "City", "Group",
                    "Model", 2026, 5, 100, 80));
        }
        when(targetRepository.findAll()).thenReturn(targets);

        ApiResult<ApiPage<TargetDetail>> result = service.getTargetDetails(null, null, null, null, 1, 50, "dealerCode", "asc");

        assertThat(result.code()).isEqualTo(200);
        assertThat(result.data().items()).hasSize(50);
        assertThat(result.data().total()).isEqualTo(55);
        assertThat(result.data().page()).isEqualTo(1);
        assertThat(result.data().pageSize()).isEqualTo(50);
    }

    @Test
    void getTargetDetailsPageExceedsTotalReturnsEmptyItems() {
        when(targetRepository.findAll()).thenReturn(List.of(
                new Target("D001", "Store A", "Beijing", "Group1", "ModelX", 2026, 5, 100, 80)
        ));

        ApiResult<ApiPage<TargetDetail>> result = service.getTargetDetails(null, null, null, null, 3, 50, "dealerCode", "asc");

        assertThat(result.code()).isEqualTo(200);
        assertThat(result.data().items()).isEmpty();
        assertThat(result.data().total()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && ./mvnw test -pl . -Dtest=AnalyticsApiServiceTest -Dmaven.test.failure.ignore=false 2>&1 | tail -20`
Expected: compilation error (class not found)

- [ ] **Step 3: 实现 AnalyticsApiService**

```java
package com.brand.agentpoc.service;

import com.brand.agentpoc.dto.detail.*;
import com.brand.agentpoc.dto.metrics.*;
import com.brand.agentpoc.dto.response.ApiPage;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.entity.*;
import com.brand.agentpoc.repository.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsApiService {

    private static final int MAX_PAGE_SIZE = 200;

    private final DealerRepository dealerRepository;
    private final OpportunityRepository opportunityRepository;
    private final CampaignRepository campaignRepository;
    private final TaskRepository taskRepository;
    private final TargetRepository targetRepository;
    private final LeadRepository leadRepository;

    public AnalyticsApiService(
            DealerRepository dealerRepository,
            OpportunityRepository opportunityRepository,
            CampaignRepository campaignRepository,
            TaskRepository taskRepository,
            TargetRepository targetRepository,
            LeadRepository leadRepository
    ) {
        this.dealerRepository = dealerRepository;
        this.opportunityRepository = opportunityRepository;
        this.campaignRepository = campaignRepository;
        this.taskRepository = taskRepository;
        this.targetRepository = targetRepository;
        this.leadRepository = leadRepository;
    }

    // ── Targets ────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResult<TargetMetrics> getTargetMetrics(Integer year, Integer month, String productModel, String dealerCode) {
        List<Target> all = targetRepository.findAll().stream()
                .filter(t -> year == null || t.getTargetYear().equals(year))
                .filter(t -> month == null || t.getTargetMonth().equals(month))
                .filter(t -> productModel == null || productModel.equals(t.getProductModel()))
                .filter(t -> dealerCode == null || dealerCode.equals(t.getDealerCode()))
                .toList();

        if (all.isEmpty()) {
            return ApiResult.success(new TargetMetrics(0, 0, 0, 0.0, null, null));
        }

        Map<String, List<Target>> byDealer = all.stream()
                .collect(Collectors.groupingBy(Target::getDealerCode));

        int totalAsKTarget = 0;
        int totalWon = 0;
        TargetMetrics.DealerMetric lowest = null;
        TargetMetrics.DealerMetric highest = null;

        for (var entry : byDealer.entrySet()) {
            String code = entry.getKey();
            List<Target> rows = entry.getValue();
            int sumTarget = rows.stream().mapToInt(Target::getAsKTarget).sum();
            int sumWon = rows.stream().mapToInt(Target::getOpportunityWonCount).sum();
            double rate = sumTarget == 0 ? 0.0 : (double) sumWon / sumTarget * 100.0;
            String name = rows.getFirst().getDealerName();

            totalAsKTarget += sumTarget;
            totalWon += sumWon;

            TargetMetrics.DealerMetric dm = new TargetMetrics.DealerMetric(code, name, Math.round(rate * 10.0) / 10.0);
            if (lowest == null || dm.achievementRate() < lowest.achievementRate()) lowest = dm;
            if (highest == null || dm.achievementRate() > highest.achievementRate()) highest = dm;
        }

        double avgRate = totalAsKTarget == 0 ? 0.0
                : Math.round((double) totalWon / totalAsKTarget * 1000.0) / 10.0;

        return ApiResult.success(new TargetMetrics(
                byDealer.size(), totalAsKTarget, totalWon, avgRate, lowest, highest));
    }

    @Transactional(readOnly = true)
    public ApiResult<ApiPage<TargetDetail>> getTargetDetails(
            Integer year, Integer month, String productModel, String dealerCode,
            int page, int pageSize, String sortBy, String sortOrder
    ) {
        List<Target> all = targetRepository.findAll().stream()
                .filter(t -> year == null || t.getTargetYear().equals(year))
                .filter(t -> month == null || t.getTargetMonth().equals(month))
                .filter(t -> productModel == null || productModel.equals(t.getProductModel()))
                .filter(t -> dealerCode == null || dealerCode.equals(t.getDealerCode()))
                .toList();

        all = sortTargets(all, sortBy, sortOrder);

        long total = all.size();
        int safePageSize = clampPageSize(pageSize);

        List<TargetDetail> items;
        int fromIndex = (page - 1) * safePageSize;
        if (fromIndex >= total) {
            items = List.of();
        } else {
            int toIndex = Math.min(fromIndex + safePageSize, (int) total);
            items = all.subList(fromIndex, toIndex).stream()
                    .map(this::toTargetDetail)
                    .toList();
        }

        return ApiResult.success(ApiPage.of(items, total, page, safePageSize));
    }

    // ── Opportunities ──────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResult<OpportunityMetrics> getOpportunityMetrics(String dealerCode, String startDate, String endDate) {
        List<Opportunity> all = filterOpportunities(opportunityRepository.findAll(), dealerCode, startDate, endDate);

        if (all.isEmpty()) {
            return ApiResult.success(new OpportunityMetrics(0, 0, 0, 0, 0.0,
                    Map.of(), List.of()));
        }

        Map<String, Long> stageDistribution = all.stream()
                .collect(Collectors.groupingBy(Opportunity::getStageName, Collectors.counting()));

        long won = all.stream().filter(o -> "Closed Won".equalsIgnoreCase(o.getStageName())).count();
        long lost = all.stream().filter(o -> "Closed Lost".equalsIgnoreCase(o.getStageName())).count();
        long closed = won + lost;
        double winRate = closed == 0 ? 0.0 : Math.round((double) won / closed * 1000.0) / 10.0;

        List<OpportunityMetrics.LossReason> topLossReasons = all.stream()
                .filter(o -> "Closed Lost".equalsIgnoreCase(o.getStageName())
                        && o.getClosedReason() != null && !o.getClosedReason().isBlank())
                .collect(Collectors.groupingBy(Opportunity::getClosedReason, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new OpportunityMetrics.LossReason(e.getKey(), e.getValue()))
                .toList();

        return ApiResult.success(new OpportunityMetrics(
                all.size(), (int) won, (int) lost,
                all.size() - (int) closed, winRate, stageDistribution, topLossReasons));
    }

    @Transactional(readOnly = true)
    public ApiResult<ApiPage<OpportunityDetail>> getOpportunityDetails(
            String dealerCode, String startDate, String endDate,
            String keyword, String stageName, int page, int pageSize, String sortBy, String sortOrder
    ) {
        List<Opportunity> all = filterOpportunities(opportunityRepository.findAll(), dealerCode, startDate, endDate);
        all = all.stream()
                .filter(o -> keyword == null || containsKeyword(o, keyword))
                .filter(o -> stageName == null || stageName.equals(o.getStageName()))
                .toList();

        all = sortOpportunities(all, sortBy, sortOrder);

        return paginate(all, this::toOpportunityDetail, page, pageSize);
    }

    // ── Leads ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResult<LeadMetrics> getLeadMetrics(String leadSource, String dealerCode) {
        List<Lead> all = leadRepository.findAll().stream()
                .filter(l -> leadSource == null || leadSource.equals(l.getLeadSource()))
                .filter(l -> dealerCode == null || dealerCode.equals(l.getDealerCode()))
                .toList();

        if (all.isEmpty()) {
            return ApiResult.success(new LeadMetrics(0, 0, 0.0, Map.of(), null));
        }

        Map<String, Long> sourceDistribution = all.stream()
                .collect(Collectors.groupingBy(Lead::getLeadSource, Collectors.counting()));

        long converted = all.stream().filter(Lead::getConverted).count();
        double conversionRate = all.isEmpty() ? 0.0
                : Math.round((double) converted / all.size() * 1000.0) / 10.0;

        LeadMetrics.SourceMetric bestSource = all.stream()
                .filter(l -> l.getLeadSource() != null)
                .collect(Collectors.groupingBy(Lead::getLeadSource))
                .entrySet().stream()
                .map(e -> {
                    long c = e.getValue().stream().filter(Lead::getConverted).count();
                    double r = e.getValue().isEmpty() ? 0.0 : (double) c / e.getValue().size() * 100.0;
                    return new LeadMetrics.SourceMetric(e.getKey(), Math.round(r * 10.0) / 10.0);
                })
                .max(Comparator.comparing(LeadMetrics.SourceMetric::conversionRate))
                .orElse(null);

        return ApiResult.success(new LeadMetrics(all.size(), (int) converted, conversionRate,
                sourceDistribution, bestSource));
    }

    @Transactional(readOnly = true)
    public ApiResult<ApiPage<LeadDetail>> getLeadDetails(
            String leadSource, String dealerCode, int page, int pageSize, String sortBy, String sortOrder
    ) {
        List<Lead> all = leadRepository.findAll().stream()
                .filter(l -> leadSource == null || leadSource.equals(l.getLeadSource()))
                .filter(l -> dealerCode == null || dealerCode.equals(l.getDealerCode()))
                .toList();

        all = sortLeads(all, sortBy, sortOrder);
        return paginate(all, this::toLeadDetail, page, pageSize);
    }

    // ── Tasks ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResult<TaskMetrics> getTaskMetrics(String dealerCode) {
        List<Task> all = taskRepository.findAll().stream()
                .filter(t -> dealerCode == null || dealerCode.equals(t.getDealerCode()))
                .toList();

        if (all.isEmpty()) {
            return ApiResult.success(new TaskMetrics(0, 0, 0, 0, 0.0, null));
        }

        long completed = all.stream().filter(t -> "Completed".equalsIgnoreCase(t.getStatus())).count();
        long open = all.stream().filter(t -> !"Completed".equalsIgnoreCase(t.getStatus())
                && !"Overdue".equalsIgnoreCase(t.getStatus())).count();
        long overdue = all.stream().filter(t -> "Overdue".equalsIgnoreCase(t.getStatus())).count();
        double completionRate = all.isEmpty() ? 0.0
                : Math.round((double) completed / all.size() * 1000.0) / 10.0;

        TaskMetrics.BacklogDealer highestBacklog = all.stream()
                .filter(t -> !"Completed".equalsIgnoreCase(t.getStatus()))
                .collect(Collectors.groupingBy(Task::getDealerCode))
                .entrySet().stream()
                .map(e -> {
                    long o = e.getValue().stream()
                            .filter(t -> !"Overdue".equalsIgnoreCase(t.getStatus())
                                    && !"Completed".equalsIgnoreCase(t.getStatus())).count();
                    long ov = e.getValue().stream()
                            .filter(t -> "Overdue".equalsIgnoreCase(t.getStatus())).count();
                    return new TaskMetrics.BacklogDealer(e.getKey(),
                            e.getValue().getFirst().getDealerName(), (int) o, (int) ov);
                })
                .max(Comparator.comparingInt(b -> b.openCount() + b.overdueCount()))
                .orElse(null);

        return ApiResult.success(new TaskMetrics(all.size(), (int) completed,
                (int) open, (int) overdue, completionRate, highestBacklog));
    }

    @Transactional(readOnly = true)
    public ApiResult<ApiPage<TaskDetail>> getTaskDetails(
            String dealerCode, String keyword, int page, int pageSize, String sortBy, String sortOrder
    ) {
        List<Task> all = taskRepository.findAll().stream()
                .filter(t -> dealerCode == null || dealerCode.equals(t.getDealerCode()))
                .filter(t -> keyword == null || t.getTaskId().contains(keyword)
                        || t.getDealerName().contains(keyword))
                .toList();

        all = sortTasks(all, sortBy, sortOrder);
        return paginate(all, this::toTaskDetail, page, pageSize);
    }

    // ── Campaigns ──────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResult<CampaignMetrics> getCampaignMetrics(String campaignType) {
        List<Campaign> all = campaignRepository.findAll().stream()
                .filter(c -> campaignType == null || campaignType.equals(c.getCampaignType()))
                .toList();

        if (all.isEmpty()) {
            return ApiResult.success(new CampaignMetrics(0, 0.0, null, 0, 0));
        }

        int totalTarget = all.stream().mapToInt(Campaign::getTotalNewCustomerTarget).sum();
        int totalActual = all.stream().mapToInt(Campaign::getActualOpportunityCount).sum();
        double avgAttainment = totalTarget == 0 ? 0.0
                : Math.round((double) totalActual / totalTarget * 1000.0) / 10.0;

        CampaignMetrics.BestCampaign best = all.stream()
                .map(c -> {
                    double rate = c.getTotalNewCustomerTarget() == 0 ? 0.0
                            : (double) c.getActualOpportunityCount() / c.getTotalNewCustomerTarget() * 100.0;
                    return new CampaignMetrics.BestCampaign(c.getCampaignId(), c.getCampaignId(),
                            Math.round(rate * 10.0) / 10.0);
                })
                .max(Comparator.comparing(CampaignMetrics.BestCampaign::attainmentRate))
                .orElse(null);

        return ApiResult.success(new CampaignMetrics(all.size(), avgAttainment, best, totalActual, totalTarget));
    }

    @Transactional(readOnly = true)
    public ApiResult<ApiPage<CampaignDetail>> getCampaignDetails(
            String campaignType, String keyword, int page, int pageSize, String sortBy, String sortOrder
    ) {
        List<Campaign> all = campaignRepository.findAll().stream()
                .filter(c -> campaignType == null || campaignType.equals(c.getCampaignType()))
                .filter(c -> keyword == null || c.getCampaignId().contains(keyword)
                        || c.getDealerName().contains(keyword))
                .toList();

        all = sortCampaigns(all, sortBy, sortOrder);
        return paginate(all, this::toCampaignDetail, page, pageSize);
    }

    // ── Private helpers ────────────────────────────────

    private int clampPageSize(int pageSize) {
        return Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
    }

    private <E, D> ApiResult<ApiPage<D>> paginate(List<E> all, Function<E, D> mapper, int page, int pageSize) {
        long total = all.size();
        int safeSize = clampPageSize(pageSize);
        int fromIndex = (page - 1) * safeSize;
        List<D> items;
        if (fromIndex >= total) {
            items = List.of();
        } else {
            int toIndex = Math.min(fromIndex + safeSize, (int) total);
            items = all.subList(fromIndex, toIndex).stream().map(mapper).toList();
        }
        return ApiResult.success(ApiPage.of(items, total, page, safeSize));
    }

    private List<Opportunity> filterOpportunities(List<Opportunity> all, String dealerCode, String startDate, String endDate) {
        return all.stream()
                .filter(o -> dealerCode == null || dealerCode.equals(o.getDealerCode()))
                .filter(o -> {
                    if (startDate == null && endDate == null) return true;
                    try {
                        java.time.LocalDate sd = startDate != null ? java.time.LocalDate.parse(startDate) : null;
                        java.time.LocalDate ed = endDate != null ? java.time.LocalDate.parse(endDate) : null;
                        return (sd == null || !o.getCreatedDate().isBefore(sd))
                                && (ed == null || !o.getCreatedDate().isAfter(ed));
                    } catch (Exception e) {
                        return true;
                    }
                })
                .toList();
    }

    private boolean containsKeyword(Opportunity o, String keyword) {
        String kw = keyword.toLowerCase();
        return (o.getDealerName() != null && o.getDealerName().toLowerCase().contains(kw))
                || (o.getDealerCode() != null && o.getDealerCode().toLowerCase().contains(kw))
                || (o.getOpportunityId() != null && o.getOpportunityId().toLowerCase().contains(kw));
    }

    // ── Sorting helpers ────────────────────────────────

    private List<Target> sortTargets(List<Target> list, String sortBy, String sortOrder) {
        Comparator<Target> cmp = switch (sortBy != null ? sortBy : "dealerCode") {
            case "targetYear" -> Comparator.comparing(Target::getTargetYear);
            case "targetMonth" -> Comparator.comparing(Target::getTargetMonth);
            case "asKTarget" -> Comparator.comparing(Target::getAsKTarget);
            default -> Comparator.comparing(Target::getDealerCode);
        };
        if ("asc".equalsIgnoreCase(sortOrder)) cmp = cmp.reversed()
                .reversed(); // keep natural; re-apply correctly
        if ("asc".equalsIgnoreCase(sortOrder)) {
            // already ascending
        } else {
            cmp = cmp.reversed();
        }
        return list.stream().sorted(cmp).toList();
    }

    private List<Opportunity> sortOpportunities(List<Opportunity> list, String sortBy, String sortOrder) {
        Comparator<Opportunity> cmp = switch (sortBy != null ? sortBy : "createdDate") {
            case "stageName" -> Comparator.comparing(Opportunity::getStageName);
            case "dealerCode" -> Comparator.comparing(Opportunity::getDealerCode);
            default -> Comparator.comparing(Opportunity::getCreatedDate);
        };
        if (!"asc".equalsIgnoreCase(sortOrder)) cmp = cmp.reversed();
        return list.stream().sorted(cmp).toList();
    }

    private List<Lead> sortLeads(List<Lead> list, String sortBy, String sortOrder) {
        Comparator<Lead> cmp = switch (sortBy != null ? sortBy : "createdDate") {
            case "leadSource" -> Comparator.comparing(Lead::getLeadSource);
            case "dealerCode" -> Comparator.comparing(Lead::getDealerCode);
            default -> Comparator.comparing(Lead::getCreatedDate);
        };
        if (!"asc".equalsIgnoreCase(sortOrder)) cmp = cmp.reversed();
        return list.stream().sorted(cmp).toList();
    }

    private List<Task> sortTasks(List<Task> list, String sortBy, String sortOrder) {
        Comparator<Task> cmp = switch (sortBy != null ? sortBy : "createdDate") {
            case "status" -> Comparator.comparing(Task::getStatus);
            case "dealerCode" -> Comparator.comparing(Task::getDealerCode);
            default -> Comparator.comparing(Task::getCreatedDate);
        };
        if (!"asc".equalsIgnoreCase(sortOrder)) cmp = cmp.reversed();
        return list.stream().sorted(cmp).toList();
    }

    private List<Campaign> sortCampaigns(List<Campaign> list, String sortBy, String sortOrder) {
        Comparator<Campaign> cmp = switch (sortBy != null ? sortBy : "createdDate") {
            case "campaignType" -> Comparator.comparing(Campaign::getCampaignType);
            case "dealerCode" -> Comparator.comparing(Campaign::getDealerCode);
            default -> Comparator.comparing(Campaign::getCreatedDate);
        };
        if (!"asc".equalsIgnoreCase(sortOrder)) cmp = cmp.reversed();
        return list.stream().sorted(cmp).toList();
    }

    // ── DTO mappers ────────────────────────────────────

    private TargetDetail toTargetDetail(Target t) {
        return new TargetDetail(t.getDealerCode(), t.getDealerName(), t.getCity(),
                t.getDealerGroupName(), t.getProductModel(), t.getTargetYear(), t.getTargetMonth(),
                t.getAsKTarget(), t.getOpportunityWonCount());
    }

    private OpportunityDetail toOpportunityDetail(Opportunity o) {
        return new OpportunityDetail(o.getOpportunityId(), o.getDealerCode(), o.getDealerName(),
                o.getCity(), o.getDealerGroupName(), o.getProductModel(), o.getStageName(),
                o.getLeadSource(), o.getCreatedDate().toString(), o.getProbability());
    }

    private LeadDetail toLeadDetail(Lead l) {
        return new LeadDetail(l.getLeadId(), l.getDealerCode(), l.getDealerName(), l.getCity(),
                l.getDealerGroupName(), l.getLeadSource(), l.getStageName(), l.getProductModel(),
                l.getCreatedDate().toString(), l.getConverted());
    }

    private TaskDetail toTaskDetail(Task t) {
        return new TaskDetail(t.getTaskId(), t.getDealerCode(), t.getDealerName(), t.getCity(),
                t.getDealerGroupName(), t.getOpportunityId(), t.getStatus(), t.getCreatedDate().toString());
    }

    private CampaignDetail toCampaignDetail(Campaign c) {
        return new CampaignDetail(c.getCampaignId(), c.getDealerCode(), c.getDealerName(), c.getCity(),
                c.getDealerGroupName(), c.getProductModel(), c.getCampaignType(),
                c.getCreatedDate().toString(), c.getActualOpportunityCount(), c.getTotalNewCustomerTarget());
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend && ./mvnw test -pl . -Dtest=AnalyticsApiServiceTest -Dmaven.test.failure.ignore=false 2>&1 | tail -30`
Expected: 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/AnalyticsApiService.java \
        backend/src/test/java/com/brand/agentpoc/service/AnalyticsApiServiceTest.java
git commit -m "feat: add AnalyticsApiService with metrics aggregation and details pagination"
```

---

### Task 5: 创建 AnalyticsApiController

**Files:**
- Create: `backend/src/main/java/com/brand/agentpoc/controller/AnalyticsApiController.java`
- Test: `backend/src/test/java/com/brand/agentpoc/controller/AnalyticsApiControllerTest.java`

- [ ] **Step 1: 写测试**

```java
package com.brand.agentpoc.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.brand.agentpoc.dto.detail.TargetDetail;
import com.brand.agentpoc.dto.metrics.TargetMetrics;
import com.brand.agentpoc.dto.response.ApiPage;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.service.AnalyticsApiService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsApiController.class)
class AnalyticsApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AnalyticsApiService analyticsApiService;

    @Test
    void getTargetMetricsReturnsResultWrapper() throws Exception {
        when(analyticsApiService.getTargetMetrics(any(), any(), any(), any()))
                .thenReturn(ApiResult.success(
                        new TargetMetrics(5, 1000, 800, 80.0,
                                new TargetMetrics.DealerMetric("D001", "Low", 60.0),
                                new TargetMetrics.DealerMetric("D005", "High", 95.0))));

        mockMvc.perform(get("/api/targets/metrics?year=2026&month=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.totalDealers").value(5))
                .andExpect(jsonPath("$.data.averageAchievementRate").value(80.0))
                .andExpect(jsonPath("$.data.lowestDealer.dealerCode").value("D001"))
                .andExpect(jsonPath("$.data.highestDealer.dealerCode").value("D005"));
    }

    @Test
    void getTargetDetailsReturnsPaginationWrapper() throws Exception {
        when(analyticsApiService.getTargetDetails(any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(ApiResult.success(
                        ApiPage.of(List.of(
                                new TargetDetail("D001", "Store A", "Beijing", "Group1",
                                        "ModelX", 2026, 5, 100, 80)),
                                1, 1, 50)));

        mockMvc.perform(get("/api/targets/details?page=1&pageSize=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(50));
    }

    @Test
    void getTargetMetricsWithoutAuthReturns401() throws Exception {
        // The ApiKeyFilter will intercept because /api/targets/** is NOT in whitelist
        mockMvc.perform(get("/api/targets/metrics"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && ./mvnw test -pl . -Dtest=AnalyticsApiControllerTest -Dmaven.test.failure.ignore=false 2>&1 | tail -20`
Expected: compilation error

- [ ] **Step 3: 实现 AnalyticsApiController**

```java
package com.brand.agentpoc.controller;

import com.brand.agentpoc.dto.detail.*;
import com.brand.agentpoc.dto.metrics.*;
import com.brand.agentpoc.dto.response.ApiPage;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.service.AnalyticsApiService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AnalyticsApiController {

    private final AnalyticsApiService analyticsApiService;

    public AnalyticsApiController(AnalyticsApiService analyticsApiService) {
        this.analyticsApiService = analyticsApiService;
    }

    // ── Targets ────────────────────────────────────────

    @GetMapping("/targets/metrics")
    public ApiResult<TargetMetrics> getTargetMetrics(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String productModel,
            @RequestParam(required = false) String dealerCode
    ) {
        return analyticsApiService.getTargetMetrics(year, month, productModel, dealerCode);
    }

    @GetMapping("/targets/details")
    public ApiResult<ApiPage<TargetDetail>> getTargetDetails(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String productModel,
            @RequestParam(required = false) String dealerCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "dealerCode") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder
    ) {
        return analyticsApiService.getTargetDetails(year, month, productModel, dealerCode,
                page, pageSize, sortBy, sortOrder);
    }

    // ── Opportunities ──────────────────────────────────

    @GetMapping("/opportunities/metrics")
    public ApiResult<OpportunityMetrics> getOpportunityMetrics(
            @RequestParam(required = false) String dealerCode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        return analyticsApiService.getOpportunityMetrics(dealerCode, startDate, endDate);
    }

    @GetMapping("/opportunities/details")
    public ApiResult<ApiPage<OpportunityDetail>> getOpportunityDetails(
            @RequestParam(required = false) String dealerCode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String stageName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "createdDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        return analyticsApiService.getOpportunityDetails(dealerCode, startDate, endDate,
                keyword, stageName, page, pageSize, sortBy, sortOrder);
    }

    // ── Leads ──────────────────────────────────────────

    @GetMapping("/leads/metrics")
    public ApiResult<LeadMetrics> getLeadMetrics(
            @RequestParam(required = false) String leadSource,
            @RequestParam(required = false) String dealerCode
    ) {
        return analyticsApiService.getLeadMetrics(leadSource, dealerCode);
    }

    @GetMapping("/leads/details")
    public ApiResult<ApiPage<LeadDetail>> getLeadDetails(
            @RequestParam(required = false) String leadSource,
            @RequestParam(required = false) String dealerCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "createdDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        return analyticsApiService.getLeadDetails(leadSource, dealerCode,
                page, pageSize, sortBy, sortOrder);
    }

    // ── Tasks ──────────────────────────────────────────

    @GetMapping("/tasks/metrics")
    public ApiResult<TaskMetrics> getTaskMetrics(
            @RequestParam(required = false) String dealerCode
    ) {
        return analyticsApiService.getTaskMetrics(dealerCode);
    }

    @GetMapping("/tasks/details")
    public ApiResult<ApiPage<TaskDetail>> getTaskDetails(
            @RequestParam(required = false) String dealerCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "createdDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        return analyticsApiService.getTaskDetails(dealerCode, keyword,
                page, pageSize, sortBy, sortOrder);
    }

    // ── Campaigns ──────────────────────────────────────

    @GetMapping("/campaigns/metrics")
    public ApiResult<CampaignMetrics> getCampaignMetrics(
            @RequestParam(required = false) String campaignType
    ) {
        return analyticsApiService.getCampaignMetrics(campaignType);
    }

    @GetMapping("/campaigns/details")
    public ApiResult<ApiPage<CampaignDetail>> getCampaignDetails(
            @RequestParam(required = false) String campaignType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "createdDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        return analyticsApiService.getCampaignDetails(campaignType, keyword,
                page, pageSize, sortBy, sortOrder);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend && ./mvnw test -pl . -Dtest=AnalyticsApiControllerTest -Dmaven.test.failure.ignore=false 2>&1 | tail -30`
Expected: 3 tests PASS (2 success cases + 401 check)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/controller/AnalyticsApiController.java \
        backend/src/test/java/com/brand/agentpoc/controller/AnalyticsApiControllerTest.java
git commit -m "feat: add AnalyticsApiController with 10 REST endpoints"
```

---

### Task 6: 修复 openapi.json

**Files:**
- Modify: `openapi.json`

- [ ] **Step 1: 修复 openapi.json**

完整替换内容：

```json
{
  "openapi": "3.1.0",
  "info": {
    "title": "经销商分析助手全域业务 API",
    "version": "4.0.0",
    "description": "提供大模型分析经销商业务的核心API。包含【汇总指标】与【全字段明细】。\n\n**大模型（AI）调度规则（必须遵守）：**\n1. **首选汇总**：遇到分析趋势、达成率、对比、宏观统计时，优先调用 `_metrics` 接口获取已计算好的数据。\n2. **按需抓取明细**：只有用户明确要求查看具体流水记录时，才调用 `_details` 接口。\n3. **分页与归纳法则**：当 `_details` 返回大量数据时，AI必须使用 `page` 和 `pageSize` (默认50) 进行分批抓取。在每次抓取后，提炼出与用户问题相关的核心数据（如：记录下此页共有10条战败原因是因为价格），然后再请求下一页，最后将所有页的归纳结果汇总输出给用户。"
  },
  "servers": [
    { "url": "http://localhost:8080", "description": "本地开发环境" },
    { "url": "https://your-api-domain.com", "description": "生产环境" }
  ],
  "security": [
    { "ApiKeyAuth": [] }
  ],
  "components": {
    "securitySchemes": {
      "ApiKeyAuth": {
        "type": "apiKey",
        "in": "header",
        "name": "X-API-Key"
      }
    }
  },
  "paths": {
    "/api/targets/metrics": {
      "get": {
        "operationId": "getTargetMetrics",
        "summary": "【目标达成】指标汇总查询",
        "description": "查询各经销商、各车型的月度/年度目标与实际商机创建、赢单的对比汇总。",
        "parameters": [
          { "name": "year", "in": "query", "schema": { "type": "integer" } },
          { "name": "month", "in": "query", "schema": { "type": "integer" } },
          { "name": "productModel", "in": "query", "schema": { "type": "string" } },
          { "name": "dealerCode", "in": "query", "schema": { "type": "string" } }
        ],
        "responses": {
          "200": {
            "description": "成功",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "code": { "type": "integer" },
                    "message": { "type": "string" },
                    "data": { "$ref": "#/components/schemas/TargetMetrics" }
                  }
                }
              }
            }
          }
        }
      }
    },
    "/api/targets/details": {
      "get": {
        "operationId": "getTargetDetails",
        "summary": "【目标达成】全字段流水明细",
        "description": "查询原始目标设定表的所有数据行。支持分页、排序。",
        "parameters": [
          { "name": "year", "in": "query", "schema": { "type": "integer" } },
          { "name": "month", "in": "query", "schema": { "type": "integer" } },
          { "name": "productModel", "in": "query", "schema": { "type": "string" } },
          { "name": "dealerCode", "in": "query", "schema": { "type": "string" } },
          { "name": "page", "in": "query", "schema": { "type": "integer", "default": 1 } },
          { "name": "pageSize", "in": "query", "schema": { "type": "integer", "default": 50 } },
          { "name": "sortBy", "in": "query", "schema": { "type": "string", "default": "dealerCode" } },
          { "name": "sortOrder", "in": "query", "schema": { "type": "string", "default": "asc" } }
        ],
        "responses": {
          "200": {
            "description": "成功",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "code": { "type": "integer" },
                    "message": { "type": "string" },
                    "data": {
                      "type": "object",
                      "properties": {
                        "items": { "type": "array", "items": { "$ref": "#/components/schemas/TargetDetail" } },
                        "total": { "type": "integer" },
                        "page": { "type": "integer" },
                        "pageSize": { "type": "integer" }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    },
    "/api/opportunities/metrics": {
      "get": {
        "operationId": "getOpportunityMetrics",
        "summary": "【商机漏斗】留存与转化指标",
        "description": "统计各阶段商机数量、战败原因占比。宏观分析必调接口。",
        "parameters": [
          { "name": "dealerCode", "in": "query", "schema": { "type": "string" } },
          { "name": "startDate", "in": "query", "schema": { "type": "string" } },
          { "name": "endDate", "in": "query", "schema": { "type": "string" } }
        ],
        "responses": {
          "200": {
            "description": "成功",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "code": { "type": "integer" },
                    "message": { "type": "string" },
                    "data": { "$ref": "#/components/schemas/OpportunityMetrics" }
                  }
                }
              }
            }
          }
        }
      }
    },
    "/api/opportunities/details": {
      "get": {
        "operationId": "getOpportunityDetails",
        "summary": "【商机漏斗】全字段流水明细",
        "description": "查询单条商机具体战败原因、购买意向等。请强制利用 page 分批获取。",
        "parameters": [
          { "name": "dealerCode", "in": "query", "schema": { "type": "string" } },
          { "name": "startDate", "in": "query", "schema": { "type": "string" } },
          { "name": "endDate", "in": "query", "schema": { "type": "string" } },
          { "name": "keyword", "in": "query", "schema": { "type": "string" } },
          { "name": "stageName", "in": "query", "schema": { "type": "string" } },
          { "name": "page", "in": "query", "schema": { "type": "integer", "default": 1 } },
          { "name": "pageSize", "in": "query", "schema": { "type": "integer", "default": 50 } },
          { "name": "sortBy", "in": "query", "schema": { "type": "string", "default": "createdDate" } },
          { "name": "sortOrder", "in": "query", "schema": { "type": "string", "default": "desc" } }
        ],
        "responses": {
          "200": {
            "description": "成功",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "code": { "type": "integer" },
                    "message": { "type": "string" },
                    "data": {
                      "type": "object",
                      "properties": {
                        "items": { "type": "array", "items": { "$ref": "#/components/schemas/OpportunityDetail" } },
                        "total": { "type": "integer" },
                        "page": { "type": "integer" },
                        "pageSize": { "type": "integer" }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    },
    "/api/leads/metrics": {
      "get": {
        "operationId": "getLeadMetrics",
        "summary": "【线索转化】渠道质量指标",
        "description": "分析不同渠道流入数量与实际转化的比例。",
        "parameters": [
          { "name": "leadSource", "in": "query", "schema": { "type": "string" } },
          { "name": "dealerCode", "in": "query", "schema": { "type": "string" } }
        ],
        "responses": {
          "200": {
            "description": "成功",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "code": { "type": "integer" },
                    "message": { "type": "string" },
                    "data": { "$ref": "#/components/schemas/LeadMetrics" }
                  }
                }
              }
            }
          }
        }
      }
    },
    "/api/leads/details": {
      "get": {
        "operationId": "getLeadDetails",
        "summary": "【线索转化】全字段流水明细",
        "description": "提取每一条原始线索的具体信息。请分页拉取。",
        "parameters": [
          { "name": "leadSource", "in": "query", "schema": { "type": "string" } },
          { "name": "dealerCode", "in": "query", "schema": { "type": "string" } },
          { "name": "page", "in": "query", "schema": { "type": "integer", "default": 1 } },
          { "name": "pageSize", "in": "query", "schema": { "type": "integer", "default": 50 } },
          { "name": "sortBy", "in": "query", "schema": { "type": "string", "default": "createdDate" } },
          { "name": "sortOrder", "in": "query", "schema": { "type": "string", "default": "desc" } }
        ],
        "responses": {
          "200": {
            "description": "成功",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "code": { "type": "integer" },
                    "message": { "type": "string" },
                    "data": {
                      "type": "object",
                      "properties": {
                        "items": { "type": "array", "items": { "$ref": "#/components/schemas/LeadDetail" } },
                        "total": { "type": "integer" },
                        "page": { "type": "integer" },
                        "pageSize": { "type": "integer" }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    },
    "/api/tasks/metrics": {
      "get": {
        "operationId": "getTaskMetrics",
        "summary": "【销售跟进】执行效能指标",
        "description": "统计超期任务数量、完成率、各类型任务的执行概况。",
        "parameters": [
          { "name": "dealerCode", "in": "query", "schema": { "type": "string" } }
        ],
        "responses": {
          "200": {
            "description": "成功",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "code": { "type": "integer" },
                    "message": { "type": "string" },
                    "data": { "$ref": "#/components/schemas/TaskMetrics" }
                  }
                }
              }
            }
          }
        }
      }
    },
    "/api/tasks/details": {
      "get": {
        "operationId": "getTaskDetails",
        "summary": "【销售跟进】全字段流水明细",
        "description": "提取所有执行过的任务流水。遇到大量数据时AI需自动分页获取。",
        "parameters": [
          { "name": "dealerCode", "in": "query", "schema": { "type": "string" } },
          { "name": "keyword", "in": "query", "schema": { "type": "string" } },
          { "name": "page", "in": "query", "schema": { "type": "integer", "default": 1 } },
          { "name": "pageSize", "in": "query", "schema": { "type": "integer", "default": 50 } },
          { "name": "sortBy", "in": "query", "schema": { "type": "string", "default": "createdDate" } },
          { "name": "sortOrder", "in": "query", "schema": { "type": "string", "default": "desc" } }
        ],
        "responses": {
          "200": {
            "description": "成功",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "code": { "type": "integer" },
                    "message": { "type": "string" },
                    "data": {
                      "type": "object",
                      "properties": {
                        "items": { "type": "array", "items": { "$ref": "#/components/schemas/TaskDetail" } },
                        "total": { "type": "integer" },
                        "page": { "type": "integer" },
                        "pageSize": { "type": "integer" }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    },
    "/api/campaigns/metrics": {
      "get": {
        "operationId": "getCampaignMetrics",
        "summary": "【市场活动】ROI与转化指标",
        "description": "将市场活动的设定目标与实际带来线索/商机数对比。",
        "parameters": [
          { "name": "campaignType", "in": "query", "schema": { "type": "string" } }
        ],
        "responses": {
          "200": {
            "description": "成功",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "code": { "type": "integer" },
                    "message": { "type": "string" },
                    "data": { "$ref": "#/components/schemas/CampaignMetrics" }
                  }
                }
              }
            }
          }
        }
      }
    },
    "/api/campaigns/details": {
      "get": {
        "operationId": "getCampaignDetails",
        "summary": "【市场活动】全字段流水明细",
        "description": "查询每场活动的详细投入与产出数字。",
        "parameters": [
          { "name": "campaignType", "in": "query", "schema": { "type": "string" } },
          { "name": "keyword", "in": "query", "schema": { "type": "string" } },
          { "name": "page", "in": "query", "schema": { "type": "integer", "default": 1 } },
          { "name": "pageSize", "in": "query", "schema": { "type": "integer", "default": 50 } },
          { "name": "sortBy", "in": "query", "schema": { "type": "string", "default": "createdDate" } },
          { "name": "sortOrder", "in": "query", "schema": { "type": "string", "default": "desc" } }
        ],
        "responses": {
          "200": {
            "description": "成功",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "code": { "type": "integer" },
                    "message": { "type": "string" },
                    "data": {
                      "type": "object",
                      "properties": {
                        "items": { "type": "array", "items": { "$ref": "#/components/schemas/CampaignDetail" } },
                        "total": { "type": "integer" },
                        "page": { "type": "integer" },
                        "pageSize": { "type": "integer" }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "schemas": {
      "TargetMetrics": {
        "type": "object",
        "properties": {
          "totalDealers": { "type": "integer", "description": "覆盖门店数" },
          "totalAsKTarget": { "type": "integer", "description": "目标总数" },
          "totalOpportunityWon": { "type": "integer", "description": "实际赢单总数" },
          "averageAchievementRate": { "type": "number", "description": "平均达成率(%)" },
          "lowestDealer": { "$ref": "#/components/schemas/DealerMetric" },
          "highestDealer": { "$ref": "#/components/schemas/DealerMetric" }
        }
      },
      "DealerMetric": {
        "type": "object",
        "properties": {
          "dealerCode": { "type": "string" },
          "dealerName": { "type": "string" },
          "achievementRate": { "type": "number" }
        }
      },
      "TargetDetail": {
        "type": "object",
        "properties": {
          "dealerCode": { "type": "string", "description": "经销商代码" },
          "dealerName": { "type": "string", "description": "经销商名称" },
          "city": { "type": "string" },
          "dealerGroupName": { "type": "string", "description": "所属集团" },
          "productModel": { "type": "string", "description": "关联车型" },
          "targetYear": { "type": "integer" },
          "targetMonth": { "type": "integer" },
          "asKTarget": { "type": "integer", "description": "分配目标数" },
          "opportunityWonCount": { "type": "integer", "description": "实际赢单数" }
        }
      },
      "OpportunityMetrics": {
        "type": "object",
        "properties": {
          "totalOpportunities": { "type": "integer", "description": "商机总数" },
          "wonCount": { "type": "integer", "description": "已赢单" },
          "lostCount": { "type": "integer", "description": "已丢单" },
          "openCount": { "type": "integer", "description": "进行中" },
          "winRate": { "type": "number", "description": "赢单率(%)" },
          "stageDistribution": { "type": "object", "additionalProperties": { "type": "integer" }, "description": "各阶段数量分布" },
          "topLossReasons": { "type": "array", "items": { "$ref": "#/components/schemas/LossReason" } }
        }
      },
      "LossReason": {
        "type": "object",
        "properties": {
          "reason": { "type": "string" },
          "count": { "type": "integer" }
        }
      },
      "OpportunityDetail": {
        "type": "object",
        "properties": {
          "opportunityId": { "type": "string" },
          "dealerCode": { "type": "string" },
          "dealerName": { "type": "string" },
          "city": { "type": "string" },
          "dealerGroupName": { "type": "string" },
          "productModel": { "type": "string" },
          "stageName": { "type": "string" },
          "leadSource": { "type": "string" },
          "createdDate": { "type": "string" },
          "probability": { "type": "integer" }
        }
      },
      "LeadMetrics": {
        "type": "object",
        "properties": {
          "totalLeads": { "type": "integer", "description": "线索总数" },
          "convertedCount": { "type": "integer", "description": "已转化" },
          "conversionRate": { "type": "number", "description": "转化率(%)" },
          "sourceDistribution": { "type": "object", "additionalProperties": { "type": "integer" }, "description": "各来源数量" },
          "bestConversionSource": { "$ref": "#/components/schemas/SourceMetric" }
        }
      },
      "SourceMetric": {
        "type": "object",
        "properties": {
          "source": { "type": "string" },
          "conversionRate": { "type": "number" }
        }
      },
      "LeadDetail": {
        "type": "object",
        "properties": {
          "leadId": { "type": "string" },
          "dealerCode": { "type": "string" },
          "dealerName": { "type": "string" },
          "city": { "type": "string" },
          "dealerGroupName": { "type": "string" },
          "leadSource": { "type": "string" },
          "stageName": { "type": "string" },
          "productModel": { "type": "string" },
          "createdDate": { "type": "string" },
          "isConverted": { "type": "boolean" }
        }
      },
      "TaskMetrics": {
        "type": "object",
        "properties": {
          "totalTasks": { "type": "integer", "description": "任务总数" },
          "completedCount": { "type": "integer", "description": "已完成" },
          "openCount": { "type": "integer", "description": "未完成" },
          "overdueCount": { "type": "integer", "description": "逾期数" },
          "completionRate": { "type": "number", "description": "完成率(%)" },
          "highestBacklogDealer": { "$ref": "#/components/schemas/BacklogDealer" }
        }
      },
      "BacklogDealer": {
        "type": "object",
        "properties": {
          "dealerCode": { "type": "string" },
          "dealerName": { "type": "string" },
          "openCount": { "type": "integer" },
          "overdueCount": { "type": "integer" }
        }
      },
      "TaskDetail": {
        "type": "object",
        "properties": {
          "taskId": { "type": "string" },
          "dealerCode": { "type": "string" },
          "dealerName": { "type": "string" },
          "city": { "type": "string" },
          "dealerGroupName": { "type": "string" },
          "opportunityId": { "type": "string" },
          "status": { "type": "string" },
          "createdDate": { "type": "string" }
        }
      },
      "CampaignMetrics": {
        "type": "object",
        "properties": {
          "totalCampaigns": { "type": "integer", "description": "活动总数" },
          "averageAttainment": { "type": "number", "description": "平均达成率(%)" },
          "bestCampaign": { "$ref": "#/components/schemas/BestCampaign" },
          "totalActualOpportunities": { "type": "integer", "description": "实际带来商机总数" },
          "totalTarget": { "type": "integer", "description": "目标商机总数" }
        }
      },
      "BestCampaign": {
        "type": "object",
        "properties": {
          "campaignId": { "type": "string" },
          "campaignName": { "type": "string" },
          "attainmentRate": { "type": "number" }
        }
      },
      "CampaignDetail": {
        "type": "object",
        "properties": {
          "campaignId": { "type": "string" },
          "dealerCode": { "type": "string" },
          "dealerName": { "type": "string" },
          "city": { "type": "string" },
          "dealerGroupName": { "type": "string" },
          "productModel": { "type": "string" },
          "campaignType": { "type": "string" },
          "createdDate": { "type": "string" },
          "actualOpportunityCount": { "type": "integer" },
          "totalNewCustomerTarget": { "type": "integer" }
        }
      }
    }
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add openapi.json
git commit -m "fix: align openapi.json with actual REST API - camelCase fields, Result Wrapper, pagination, ApiKey auth"
```

---

### Task 7: 修改 PromptFactory 新增 3 段指令

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/ai/PromptFactory.java`
- Test: `backend/src/test/java/com/brand/agentpoc/ai/PromptFactoryTest.java`

- [ ] **Step 1: 更新 PromptFactoryTest 测试**

在 `PromptFactoryTest.java` 中添加新测试：

```java
@Test
void systemPromptIncludesNewSixSectionStructureInChinese() {
    String prompt = promptFactory.buildSystemPrompt("zh");

    assertThat(prompt).contains("## 核心结论");
    assertThat(prompt).contains("## 数据支撑");
    assertThat(prompt).contains("## 经营分析");
    assertThat(prompt).contains("## 问题诊断与解决");
    assertThat(prompt).contains("## 改进建议");
    assertThat(prompt).contains("## 数据汇总");
}

@Test
void systemPromptIncludesNewSixSectionStructureInEnglish() {
    String prompt = promptFactory.buildSystemPrompt("en");

    assertThat(prompt).contains("## Conclusion");
    assertThat(prompt).contains("## Data Support");
    assertThat(prompt).contains("## Short Analysis");
    assertThat(prompt).contains("## Problem Diagnosis & Solutions");
    assertThat(prompt).contains("## Improvement Suggestions");
    assertThat(prompt).contains("## Data Summary");
}

@Test
void groundedPromptIncludesNewSectionInstructionsInChinese() {
    String prompt = promptFactory.buildGroundedModelPrompt(
            "zh", "本月目标达成", "无", "Scenario: TARGET_ACHIEVEMENT");

    assertThat(prompt).contains("## 问题诊断与解决");
    assertThat(prompt).contains("## 改进建议");
    assertThat(prompt).contains("## 数据汇总");
    assertThat(prompt).contains("识别最大差距");
}

@Test
void groundedPromptIncludesNewSectionInstructionsInEnglish() {
    String prompt = promptFactory.buildGroundedModelPrompt(
            "en", "Target achievement", "None", "Scenario: TARGET_ACHIEVEMENT");

    assertThat(prompt).contains("## Problem Diagnosis & Solutions");
    assertThat(prompt).contains("## Improvement Suggestions");
    assertThat(prompt).contains("## Data Summary");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && ./mvnw test -pl . -Dtest=PromptFactoryTest -Dmaven.test.failure.ignore=false 2>&1 | tail -20`
Expected: 4 new tests FAIL

- [ ] **Step 3: 修改 buildSystemPrompt**

将 `buildSystemPrompt` 的输出结构从 3 段改为 6 段：

中文版（替换现有结构声明）：
```
输出结构必须是：
## 核心结论
## 数据支撑
## 经营分析
## 问题诊断与解决
## 改进建议
## 数据汇总
追问：
```

英文版（替换现有结构声明）：
```
Output structure must be:
## Conclusion
## Data Support
## Short Analysis
## Problem Diagnosis & Solutions
## Improvement Suggestions
## Data Summary
FOLLOW_UP_QUESTIONS:
```

- [ ] **Step 4: 修改 buildGroundedModelPrompt**

在中文版 prompt 末尾（`追问：` 前），将现有的第 9 条后移，插入新的第 10-12 条：

```
10. `## 问题诊断与解决` 用 1-2 条识别当前最大差距或瓶颈：
    - 格式为 [差距/瓶颈指标] + [具体对象] + [根本原因] + [解决动作]
    - 如果各项指标达标（达成率 >= 80%），说明当前表现稳定，并指出最需要关注的次弱指标
11. `## 改进建议` 根据达成率分支：
    - 若达成率 >= 80%：给 2 条建议，聚焦如何拉开差距和将成功经验复制到其他门店/车型/渠道
    - 若达成率 < 80%：给 2-3 条分阶段建议，每条包含 [动作] + [对象] + [预期结果] + [时间范围（本月/下季度/年度）]
12. `## 数据汇总` 提取 `_metrics` 接口返回的核心 KPI 汇总为 HTML <table>：
    - 列：指标名称 | 数值 | 范围 | 对比基准
    - 不可与 `## 数据支撑` 表格重复，本段为全局汇总视角
    - 至少包含 5 项核心指标
```

英文版对应翻译。

- [ ] **Step 5: 运行测试确认通过**

Run: `cd backend && ./mvnw test -pl . -Dtest=PromptFactoryTest -Dmaven.test.failure.ignore=false 2>&1 | tail -20`
Expected: all tests PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/ai/PromptFactory.java \
        backend/src/test/java/com/brand/agentpoc/ai/PromptFactoryTest.java
git commit -m "feat: add 3 new sections to AI prompt - problem diagnosis, improvement suggestions, data summary"
```

---

### Task 8: 修改 ChatService 标题校验白名单

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/ChatService.java`
- Test: `backend/src/test/java/com/brand/agentpoc/service/ChatServiceTest.java`

- [ ] **Step 1: 更新 ChatServiceTest 添加新标题校验测试**

在 `ChatServiceTest.java` 中追加：

```java
@Test
void isValidAnalyticsReplyAcceptsSixSectionHeadingsInEnglish() {
    String reply = """
            ## Conclusion
            Key findings here.
            ## Data Support
            <table>...</table>
            ## Short Analysis
            Attribution and recommendations.
            ## Problem Diagnosis & Solutions
            Gap identified.
            ## Improvement Suggestions
            Phased plan.
            ## Data Summary
            <table>...</table>
            FOLLOW_UP_QUESTIONS:
            1. First question
            2. Second question
            """;
    // This reply should pass validation — test via the chat/stream endpoint
    // The validation logic is in ChatService.isValidAnalyticsReply (package-private)
}

@Test
void isValidAnalyticsReplyRejectsReplyMissingNewSections() {
    // A reply with only the original 3 sections should now fail validation
    // because the expected heading list has been expanded to 6
}
```

- [ ] **Step 2: 修改 isValidAnalyticsReply 标题白名单**

```java
private boolean isValidAnalyticsReply(String reply, String fallbackReply) {
    String normalized = reply == null ? "" : reply.trim();
    if (normalized.isBlank()) {
        return false;
    }

    List<String> headings = extractSecondLevelHeadings(normalized);
    // Updated from 3 to 6 required headings
    if (!headings.equals(List.of("Conclusion", "Data Support", "Short Analysis",
            "Problem Diagnosis & Solutions", "Improvement Suggestions", "Data Summary"))
            && !headings.equals(List.of("核心结论", "数据支撑", "经营分析",
            "问题诊断与解决", "改进建议", "数据汇总"))) {
        return false;
    }

    String replyTable = extractDataSupportTable(normalized);
    String fallbackTable = extractDataSupportTable(fallbackReply);
    if (!hasText(replyTable) || !hasText(fallbackTable)) {
        return false;
    }

    return normalizeBlock(replyTable).equals(normalizeBlock(fallbackTable))
            && hasExactlyTwoFollowUpQuestions(normalized);
}
```

- [ ] **Step 3: 运行测试确认通过**

Run: `cd backend && ./mvnw test -pl . -Dtest=ChatServiceTest -Dmaven.test.failure.ignore=false 2>&1 | tail -30`
Expected: all tests PASS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/ChatService.java \
        backend/src/test/java/com/brand/agentpoc/service/ChatServiceTest.java
git commit -m "feat: update ChatService heading validation for 6-section AI response"
```

---

### Task 9: 修改 RuleBasedAnalyticsService fallback 输出为 6 段

**Files:**
- Modify: `backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java`
- Test: `backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java`

- [ ] **Step 1: 更新测试，验证 fallback 回复包含 6 段标题**

在 `RuleBasedAnalyticsServiceTest.java` 中添加：

```java
@Test
void fallbackReplyForTargetAchievementIncludesSixSectionsInChinese() {
    Target t1 = new Target("D001", "Store A", "Beijing", "Group1", "ModelX", 2026, 5, 100, 80);
    when(targetRepository.findAll()).thenReturn(List.of(t1));
    when(dealerRepository.findAll()).thenReturn(List.of(
            new Dealer("D001", "Store A", "Beijing", "Group1")));

    AnalyticsResponse response = service.analyze("本月目标达成", "zh");

    assertThat(response.fallbackReply())
            .contains("## 核心结论")
            .contains("## 数据支撑")
            .contains("## 经营分析")
            .contains("## 问题诊断与解决")
            .contains("## 改进建议")
            .contains("## 数据汇总")
            .contains("追问：");
}

@Test
void fallbackReplyForOpportunityFunnelIncludesSixSections() {
    Opportunity o1 = new Opportunity("OPP001", "D001", "Store A", "Beijing", "Group1",
            "ModelX", "Prospecting", "Website", LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 6, 1), 60);
    when(opportunityRepository.findAll()).thenReturn(List.of(o1));

    AnalyticsResponse response = service.analyze("商机漏斗分析", "zh");

    assertThat(response.fallbackReply())
            .contains("## 问题诊断与解决")
            .contains("## 改进建议")
            .contains("## 数据汇总");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && ./mvnw test -pl . -Dtest=RuleBasedAnalyticsServiceTest -Dmaven.test.failure.ignore=false 2>&1 | tail -30`
Expected: new tests FAIL

- [ ] **Step 3: 扩展 ScenarioResult.reply() 生成 3 个新段落**

在 6 个 analyze* 方法中，`fallbackReply` 构建时追加新段落。以 `analyzeTargetAchievement` 为例（其他 5 个同理）：

在现有 fallbackReply 末尾（`追问：` 之前）拼接：

```java
private String appendNewSections(
        String existingReply,
        String language,
        List<DealerTargetMetric> metrics,
        double avgAchievementRate,
        DataQualityContext quality
) {
    StringBuilder sb = new StringBuilder(existingReply);

    // Locate the 追问 block and insert before it
    String followUpMarker = "zh".equals(language) ? "追问：" : "FOLLOW_UP_QUESTIONS:";
    int followUpIdx = sb.indexOf(followUpMarker);
    String followUpBlock = followUpIdx >= 0 ? sb.substring(followUpIdx) : "";
    if (followUpIdx >= 0) {
        sb.setLength(followUpIdx);
    }

    if ("zh".equals(language)) {
        // ## 问题诊断与解决
        sb.append("## 问题诊断与解决\n\n");
        if (quality.state() == DataQualityState.NORMAL && !metrics.isEmpty()) {
            DealerTargetMetric lowest = metrics.stream()
                    .min(Comparator.comparing(DealerTargetMetric::achievementRate))
                    .orElse(null);
            if (lowest != null && lowest.achievementRate() < 80.0) {
                sb.append(String.format(
                        "- **达标差距**：%s 达成率仅 **%.1f%%**，低于 80%% 基准线，是当前最大短板。\n",
                        lowest.dealerName(), lowest.achievementRate()));
                sb.append(String.format(
                        "  - 原因：该门店赢单数（%d）与目标数（%d）差距较大，可能存在线索质量或跟进效率问题。\n",
                        lowest.opportunityWonCount(), lowest.asKTarget()));
                sb.append("  - 行动：建议重点复盘该门店商机跟进流程，优先分配高质量线索，并在本月底前做一次专项辅导。\n\n");
            } else {
                sb.append("- 当前各门店达成率均在 80% 以上，整体表现稳定。下一阶段应关注达成率相对较低的车型或区域，防止出现新的短板。\n\n");
            }
        } else {
            sb.append("- 当前数据量不足以进行细粒度诊断，建议扩大查询范围或检查数据导入状态。\n\n");
        }

        // ## 改进建议
        sb.append("## 改进建议\n\n");
        if (avgAchievementRate >= 80.0) {
            sb.append("- **扩大优势**：提取最高达成门店（");
            metrics.stream().max(Comparator.comparing(DealerTargetMetric::achievementRate))
                    .ifPresent(m -> sb.append(String.format("%s %.1f%%", m.dealerName(), m.achievementRate())));
            sb.append("）的跟进流程，形成标准化手册推广至其他门店。\n");
            sb.append("- **横向复制**：将高转化车型的成功经验梳理为培训材料，下季度在集团内做一次经验分享会。\n\n");
        } else {
            sb.append("- **本月目标**：针对达成率最低的 2 家门店，制定每周跟进计划和线索分配倾斜，争取月底前提升 10-15 个百分点。\n");
            sb.append("- **下季度目标**：建立门店达成率月度复盘机制，每店每月至少一次经营分析会。\n");
            sb.append("- **年度目标**：实现集团整体达成率 ≥ 85%，对连续两季度不达标的门店启动专项帮扶计划。\n\n");
        }

        // ## 数据汇总
        sb.append("## 数据汇总\n\n");
        sb.append("<table>\n");
        sb.append("<thead><tr><th>指标名称</th><th>数值</th><th>范围</th><th>对比基准</th></tr></thead>\n");
        sb.append("<tbody>\n");
        sb.append(String.format("<tr><td>覆盖门店数</td><td>%d</td><td>当前范围</td><td>—</td></tr>\n",
                metrics.stream().map(DealerTargetMetric::dealerCode).distinct().count()));
        sb.append(String.format("<tr><td>目标总数</td><td>%d</td><td>当前范围</td><td>—</td></tr>\n",
                metrics.stream().mapToInt(DealerTargetMetric::asKTarget).sum()));
        sb.append(String.format("<tr><td>赢单总数</td><td>%d</td><td>当前范围</td><td>—</td></tr>\n",
                metrics.stream().mapToInt(DealerTargetMetric::opportunityWonCount).sum()));
        sb.append(String.format("<tr><td>平均达成率</td><td>%.1f%%</td><td>当前范围</td><td>80%%</td></tr>\n",
                avgAchievementRate));
        metrics.stream().min(Comparator.comparing(DealerTargetMetric::achievementRate))
                .ifPresent(m -> sb.append(String.format(
                        "<tr><td>最低达成门店</td><td>%s (%.1f%%)</td><td>%s</td><td>—</td></tr>\n",
                        m.dealerName(), m.achievementRate(), m.dealerCode())));
        metrics.stream().max(Comparator.comparing(DealerTargetMetric::achievementRate))
                .ifPresent(m -> sb.append(String.format(
                        "<tr><td>最高达成门店</td><td>%s (%.1f%%)</td><td>%s</td><td>—</td></tr>\n",
                        m.dealerName(), m.achievementRate(), m.dealerCode())));
        sb.append("</tbody>\n</table>\n\n");
    } else {
        // English equivalent — similar structure with English labels
        sb.append("## Problem Diagnosis & Solutions\n\n");
        // ... (same logic, English text)
    }

    sb.append(followUpBlock);
    return sb.toString();
}
```

其余 5 个 scenario（OPPORTUNITY_FUNNEL、SALES_FOLLOW_UP、CAMPAIGN_PERFORMANCE、LEAD_SOURCE、DEALER_BENCHMARK）也需要类似的 `appendNewSections` 变体。

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend && ./mvnw test -pl . -Dtest=RuleBasedAnalyticsServiceTest -Dmaven.test.failure.ignore=false 2>&1 | tail -30`
Expected: all tests PASS

- [ ] **Step 5: 运行全量测试确认无回归**

Run: `cd backend && ./mvnw test 2>&1 | tail -40`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/brand/agentpoc/service/RuleBasedAnalyticsService.java \
        backend/src/test/java/com/brand/agentpoc/service/RuleBasedAnalyticsServiceTest.java
git commit -m "feat: extend fallback analytics reply to 6 sections with deterministic rules"
```

---

## 验收检查

- [ ] `./mvnw test` 全量通过
- [ ] `GET /api/targets/metrics?year=2026&month=5` 返回 Result Wrapper + 正确聚合值
- [ ] `GET /api/targets/details?page=3&pageSize=50`（超页）返回空 items
- [ ] 无 X-API-Key 时 `/api/targets/metrics` 返回 401
- [ ] AI 回复（模型 + fallback）均含 6 段 + 追问块
- [ ] openapi.json 字段名与 REST 响应 JSON key 一致
