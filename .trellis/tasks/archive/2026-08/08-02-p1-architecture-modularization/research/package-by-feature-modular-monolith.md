# 按业务能力分包的模块化单体研究

## 研究问题

在不改变现有 Spring Boot 单体运行方式、HTTP/SSE 协议和数据库行为的前提下，如何为当前按技术层组织的后端建立可渐进迁移的模块边界？

## 可比较的方案

### 方案 A：继续按技术层分包

典型结构是 `controller/`、`service/`、`repository/`、`entity/`、`dto/`。它对小型 CRUD 项目上手快，但同一业务用例会横跨多个根包，依赖方向难以从目录看出；当 `service` 类持续增长时，包名不能阻止跨领域耦合。

对本仓库的适配性较低：当前 `ChatService`、`RuleBasedAnalyticsService` 和 `ExcelImportService` 已经成为跨职责聚合点，继续扩展根 `service` 会让 P1-2 的目标失效。

### 方案 B：按业务能力分包的模块化单体（推荐）

以 `auth`、`dataimport`、`metrics`、`analytics`、`agent` 等业务能力作为一级包；每个模块内部再按 `controller`、`application`、`domain`、`infrastructure` 分层。模块通过应用服务或端口交互，模块内部实现细节不作为其他模块的直接依赖。

优点是目录与业务边界一致，支持增量迁移；可以继续以一个 Spring Boot 应用部署，不需要网络调用、服务发现或分布式事务。缺点是过渡期会有旧包和新包并存，必须通过文档、测试和代码审查逐步收紧边界。

这与路线图明确的“模块化单体”一致，也适合当前已有稳定回归测试、但尚未完成完整领域建模的 POC。

### 方案 C：引入 Spring Modulith 作为运行时/架构约束

保留单体部署，但通过 Spring Modulith 的模块发现、依赖验证、模块测试和事件机制强化边界。官方文档将应用模块建模为业务模块，并支持验证模块依赖、模块场景测试和应用事件。

优点是边界可自动验证，未来拆分或异步集成时有更强的演进路径；缺点是会增加框架依赖、测试约束和迁移成本。当前项目仍有大量遗留根包、超大服务和过渡性 DTO/entity/repository，立即引入会把“整理包结构”和“采用框架约束”绑定在一起，扩大 P1-2 的变更面。

## 共同约定

无论选择哪种方案，都应保留以下工程约束：

* Controller 只做 HTTP/SSE 协议适配、参数校验和响应映射；业务判断放在应用服务。
* 领域规则和事实计算不依赖 Web 层；模型适配、文件 I/O、数据库访问属于基础设施边界。
* 跨模块调用优先依赖公开的应用服务/端口，而不是直接引用另一模块的 repository、entity 或内部实现。
* 迁移以小切片完成，每个切片都保留原 API 和行为回归。

## 对本仓库的决策

选择方案 B。原因是它能满足 P1-2 的验收目标，且不需要一次性重写当前代码或引入新的运行时基础设施。方案 C 作为后续增强方向保留：当主要模块完成迁移、跨模块依赖稳定后，再评估加入模块验证，而不是现在同时做两类结构性改动。

## 迁移切片建议

第一步选择 `ChatReplyGuard`：它只被 `ChatService` 使用，职责集中、无数据库写入、无公开 API 和 SSE 协议定义，适合移动到 `com.brand.agentpoc.agent` 作为 agent 模块的输出边界。后续可依次拆出会话上下文、SSE writer、模型适配和规则分析编排；每一步都以现有 `ChatServiceTest`、Controller 测试和准确率回归测试为安全网。

## 参考

* Spring Modulith Reference — Fundamentals: https://docs.spring.io/spring-modulith/reference/fundamentals.html
* Spring Modulith Reference — Verification: https://docs.spring.io/spring-modulith/reference/verification.html
* Martin Fowler — Monolith First: https://martinfowler.com/bliki/MonolithFirst.html
* 本仓库：`docs/07-生产化升级路线图.md`，“模块边界”和“架构模块化”章节。
