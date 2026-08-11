# P2-4A 可观测性与全链路追踪

## Goal

建立供应商无关的结构化可观测性基线，使一个用户请求、SSE、Agent 工具、模型调用、导入、报告和后台 job 可以通过安全的 trace/request/job 标识关联。

## Dependencies

* 前置：P2-1A 身份与安全事件稳定后即可启动。
* 后续：P2-4B 成本、P2-4C 告警、P2-3B 后台 job。

## Requirements

* 定义 traceId、requestId、jobId、tenantId、userId、session family、batchId、reportId 的允许记录规则；原始 token、密码、prompt、工具参数和业务明细禁止记录。
* HTTP 入站接受/生成受控 traceId，响应回传；SSE、Agent、模型和后台任务正确传播。
* 统一结构化安全、模型、导入、知识、报告和 job 事件名、状态、耗时、结果码。
* 使用 Micrometer/OpenTelemetry 等标准接口导出 metrics/traces，业务代码不绑定特定监控 SaaS。
* 定义采样、保留、PII 清理和高基数字段策略。
* 提供本地诊断方式、基础 dashboard 查询和 trace 关联测试。

## Acceptance Criteria

* [ ] 任一报告或失败 job 能关联到请求/订阅、tenant、数据批次和模型调用元数据。
* [ ] SSE 与异步边界不丢 trace，上游伪造非法 traceId 被替换。
* [ ] 日志/trace/metric 中不存在密码、原始 token、API key 或完整业务 payload。
* [ ] 关键事件字段有契约测试，重复/高基数标签受控。
* [ ] 无外部观测平台时本地与测试仍可启动。

## Out of Scope

* 不在本任务选择或采购日志/Tracing SaaS。
* 模型价格和预算策略属于 P2-4B。

