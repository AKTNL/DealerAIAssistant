# P2-4A 可观测性基线调研

## 结论

推荐以 Spring Boot Actuator + Micrometer Observation 作为业务代码的稳定接口，通过 Micrometer Tracing OpenTelemetry bridge 生成 trace/span，将 OTLP 作为可选导出边界。本地和测试不依赖 Collector，生产再按配置对接任意 OTLP 后端。

## 仓库现状

* 后端使用 Spring Boot 3.4.5，已引入 `spring-boot-starter-actuator`，并在模型客户端构建时传入 `ObservationRegistry`。
* 默认只暴露 `health,info`，尚未提供 Prometheus/OTLP 导出或结构化日志配置。
* `X-Request-ID` 的读取、截断和生成逻辑分散在认证、组织和报告控制器/过滤器中；请求结束时没有统一回传响应头，非法字符也没有统一拒绝或替换契约。
* `ChatService` 另行生成 8 位随机 trace ID，因此 HTTP 请求、SSE、Agent 工具和模型调用当前不是同一条 trace。
* `StreamingResponseBody` 在异步线程执行；如不显式捕获与恢复 observation/context，MDC 和当前 span 可能丢失。
* 报告生成、投递和协作通知使用定时轮询。持久化 job trace ID 已经存在，但生产与延迟消费更适合使用 producer/consumer span 和 span link，不应长时保持原请求 span 为开启状态。
* 日志中已有部分受控 Agent trace 事件，但整体仍是自由文本，事件名、状态、结果码和耗时没有共享契约。

## 官方能力约束

* Spring Boot 3.4 使用 Micrometer Observation 同时驱动 metrics 和 traces；低基数标签进入 metrics 和 traces，高基数字段只能进 trace，不应成为 metric tag。
* Spring Boot 可通过 `micrometer-tracing-bridge-otel` 桥接 OpenTelemetry，再使用 OTLP exporter 对接 Collector；默认采样率为 10%，应按环境外部化配置。
* 启用 Micrometer Tracing 后，Spring Boot 可以在日志 MDC 中自动提供 `traceId` 和 `spanId`。
* OpenTelemetry 将延迟队列工作建模为 producer/consumer span，并可使用 span link 关联早已结束的上游 trace。

## 可行方案

### A. Micrometer-first + 可选 OTLP（推荐）

* 在统一入站过滤器中验证/生成 request ID，回传响应头，并将它作为受控业务关联字段；标准 OTel trace ID 由 tracing SDK 管理。
* 对模型、导入、报告和 job 使用 Observation/Span 契约，在 SSE 和 scheduler 边界显式传播或关联上下文。
* 业务代码只依赖 Micrometer/OTel 标准接口；OTLP endpoint、采样率和导出开关全部外部化。
* 优点：契合现有依赖和 Spring AI observation，契约可测试，不绑定 SaaS。
* 代价：需要收口现有 trace 逻辑，并为异步边界加针对性代码。

### B. OpenTelemetry Java Agent 为主

* 通过部署时 Java Agent 自动采集 HTTP/JDBC/调度等 span，业务代码只补少量属性。
* 优点：自动覆盖面大，初期代码改动少。
* 代价：业务事件契约、测试和本地一致性较弱，部署参数成为必要运行条件，与“无外部平台仍可启动”的目标不如 A 契合。

### C. 仅结构化日志与 metrics

* 先统一事件字段并暴露 metrics，暂不接入 tracing bridge。
* 优点：变更最小。
* 代价：无法满足 SSE、Agent、模型和异步 job 的端到端 trace 验收，只适合作为过渡步骤。

## 建议的 4A MVP 边界

* 包含：后端统一 request/trace 基础设施、安全字段规则、核心 Observation 契约、SSE/job 传播、可选 OTLP 配置、本地 metrics/trace 诊断和契约测试。
* 不包含：业务前端中的运维 Dashboard、自建 trace 存储/搜索、具体 Grafana/Tempo/Jaeger 采购与部署。
* 为后续保留：P2-4B 复用模型 Observation 记录用量/费用；P2-4C 复用低基数 metrics 和结果码建立告警。

## 参考

* Spring Boot 3.4.5 `actuator/tracing.adoc`：Micrometer Tracing bridge、OTLP exporter、日志关联 ID、默认 10% 采样。
* Spring Boot 3.4.5 `actuator/observability.adoc`：ObservationRegistry、低/高基数标签和线程上下文传播。
* OpenTelemetry `concepts/signals/traces.md`：trace/span 模型、上下文传播、span link 及 producer/consumer span。
