# P2-4A 可观测性与全链路追踪

## Goal

建立供应商无关的结构化可观测性基线，使一个用户请求、SSE、Agent 工具、模型调用、导入、报告和后台 job 可以通过安全的 trace/request/job 标识关联。

## Dependencies

* 前置：P2-1A 身份与安全事件稳定后即可启动。
* 后续：P2-4B 成本、P2-4C 告警、P2-3B 后台 job。

## What I Already Know

* 后端为 Spring Boot 3.4.5，已引入 Actuator，Spring AI 模型客户端已复用 `ObservationRegistry`。
* 现有 `X-Request-ID` 解析分散在多个控制器和认证过滤器中，与 `ChatService` 的 8 位 trace ID 不是同一契约。
* SSE 使用 `StreamingResponseBody`，报告生成/投递/协作通知使用定时 runner，都是需要显式处理上下文的异步边界。
* 现有 job 和审计记录已持久化 trace ID，因此应收口并复用，而不是建立第二套可观测数据库。

## Decision (ADR-lite)

**Context**: 项目已使用 Actuator 和 `ObservationRegistry`，但 request ID 重复实现，HTTP、SSE、Agent、模型与 job 之间没有统一 trace 契约。

**Decision**: 采用 Micrometer-first 后端 MVP。以 Micrometer Observation 作为业务观测接口，以 OpenTelemetry bridge + 可选 OTLP 作为导出边界；提供 Actuator/本地诊断和运维查询文档，不开发产品内 trace 搜索界面。

**Consequences**: 需要收口现有 request/trace 逻辑并显式处理 SSE 和延迟 job 边界；作为回报，业务代码不依赖监控厂商，P2-4B/4C 可复用同一契约。

## Requirements

* 定义 traceId、requestId、jobId、tenantId、userId、session family、batchId、reportId 的允许记录规则；原始 token、密码、prompt、工具参数和业务明细禁止记录。
* HTTP 入站接受/生成受控 traceId，响应回传；SSE、Agent、模型和后台任务正确传播。
* 统一结构化安全、模型、导入、知识、报告和 job 事件名、状态、耗时、结果码。
* 使用 Micrometer/OpenTelemetry 等标准接口导出 metrics/traces，业务代码不绑定特定监控 SaaS。
* 定义采样、保留、PII 清理和高基数字段策略。
* 提供本地诊断方式、基础 dashboard 查询和 trace 关联测试。

## Technical Approach (Proposed)

* 统一入站 request ID 验证、生成、响应回传与 MDC 关联；由 tracing SDK 管理标准 trace/span ID，业务 request/job ID 作为受控属性。
* 通过 Micrometer Observation 定义导入、模型、报告和 job 的低基数事件/耗时/结果码，避免 tenant/user/batch/report/trace ID 进入 metric tags。
* SSE 显式捕获与恢复 observation context；延迟 job 使用新 consumer span 和持久化关联 ID/span link，不保持长寿命上游 span。
* OTLP endpoint、导出开关、采样率和保留策略外部化；无 Collector 时应用和测试正常启动。

## Research References

* [`research/observability-baseline-options.md`](research/observability-baseline-options.md) — 现有链路缺口、Spring Boot/OpenTelemetry 约束与三种实现方案。

## Acceptance Criteria

* [x] 任一报告或失败 job 能关联到请求/订阅、tenant、数据批次和模型调用元数据。
* [x] SSE 与异步边界不丢 trace，上游伪造非法 traceId 被替换。
* [x] 日志/trace/metric 中不存在密码、原始 token、API key 或完整业务 payload。
* [x] 关键事件字段有契约测试，重复/高基数标签受控。
* [x] 无外部观测平台时本地与测试仍可启动。

## Definition of Done

* 后端单元/集成测试覆盖 request ID 验证、响应回传、SSE 上下文、job 关联和禁止字段。
* Maven 测试、PMD 和前端构建通过，且无 Collector 的默认配置可启动。
* 更新可观测契约、本地诊断和 OTLP 对接文档。
* 采样、敏感字段、高基数和回滚策略有明确运维说明。

## Out of Scope

* 不在本任务选择或采购日志/Tracing SaaS。
* 模型价格和预算策略属于 P2-4B。
* 不自建 trace 持久化/搜索引擎，不在产品前端复制 Grafana/Tempo/Jaeger 等运维界面。
