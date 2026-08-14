# P2-3C 邮件通知投递通道

## Goal

为成功生成的订阅报告建立统一、可审计的投递端口，并以 tenant 级 SMTP 邮件完成首个生产级通道，使每个符合资格的订阅用户都能收到一封可留档的完整报告邮件。

## Dependencies

* 前置：P2-3B 可靠生成任务；P2-2C tenant secret/config 边界；P2-1 tenant 用户管理。
* 后续：P2-4C 投递告警、P2-4D 生产运行手册。

## Requirements

### 用户邮箱

* `tenant_memberships` 增加 nullable `email`，迁移不得破坏既有用户；保存时去除空白、规范化为小写，并使用 Jakarta Mail 解析及 `validate()` 校验为单一邮箱地址。
* 非空邮箱在 tenant 内唯一；拒绝 display-name/header 写法、CR/LF 和超过数据库边界的输入。邮箱属于 tenant 路由配置，不修改可跨 tenant 共享的全局身份。
* tenant 用户管理员可以在创建用户时填写邮箱，并可通过带 version 的独立操作更新或清空邮箱；更新写安全审计但不记录完整邮箱。
* tenant 用户管理视图可以显示完整邮箱；普通报告订阅收件人目录只增加 `emailConfigured`，不额外暴露邮箱地址。
* `email` 通道的订阅启用时，所有 recipient user IDs 必须仍然具备报告读取资格且已配置邮箱；历史数据缺失邮箱时返回明确不可执行原因。

### Tenant SMTP 配置

* 每个 tenant 最多保存一套 SMTP 配置：host、port、`STARTTLS|SMTPS`、username、write-only password、from address、可选 from display name 和 enabled。
* 只允许认证加密连接：STARTTLS 必须升级成功，SMTPS 使用隐式 TLS；不支持明文 SMTP。
* SMTP host 必须命中运维配置的 outbound allowlist，port 必须处于受控范围；禁止任意网络出口。
* SMTP password 使用 notification-specific、tenant-bound AES-GCM secret provider；沿用现有 secret-provider 模式，但不得保存到模型配置实体或使用 model-specific AAD。
* GET 只返回非秘密设置、`passwordConfigured`、enabled、version 和时间；password 不进入日志、审计 detail、API 响应或投递表。
* 提供配置保存、读取、删除及 test action；test 向当前管理员已配置邮箱发送一封明确标记的测试邮件，只返回安全结果，不泄露 SMTP 原始异常。
* 动态 sender 必须设置有限的 connection/read/write timeout，不能使用 Jakarta Mail 的无限默认值。

### 邮件内容

* 每个收件人单独发送一封 `text/plain; charset=UTF-8` 邮件，不把多个订阅用户放进 To/CC/BCC。
* Subject 使用固定产品前缀和有界报告标题；Body 包含报告标题、类型、生成时间和完整 `ReportDraft.markdown`。
* 邮件增加稳定的 `X-Report-Delivery-Key` 诊断 header，但不得声称 SMTP 服务端会据此去重。
* 地址、显示名和主题必须阻断 header injection；最终序列化邮件必须低于保守大小上限，超限以安全错误码永久失败，不静默截断报告。
* 首版不依赖公网报告链接，不添加附件或富 HTML 模板。

### 持久化与执行

* 应用层定义 provider-neutral `DeliveryRequest -> DeliveryResult` 端口；SMTP/Jakarta Mail 类型不得泄漏到报告 domain 与 application contract。
* 报告 job 成功并得到 `reportDraftId` 时，在同一数据库事务中按有效 recipient 创建 durable delivery outbox；远程 SMTP 调用在事务提交后由 runner 执行。
* 每个 recipient 建立独立投递，记录 tenant、subscription/job/draft、recipient user ID、channel、稳定 delivery key、状态、attempt/max attempts、lease、重试时间、nullable provider message ID、时间和安全错误码。
* 唯一约束保证同一 `job + channel + recipient user` 只有一个 delivery；多实例通过行锁/lease claim，不得并发发送同一记录。
* 状态至少覆盖 `READY`、`SENDING`、`RETRY_WAIT`、`SUCCEEDED`、`PERMANENT_FAILURE`、`UNKNOWN`、`CANCELLED`。
* 发送前重新校验 tenant、订阅、recipient 资格、当前邮箱和 SMTP 配置；已禁用 tenant/订阅、已撤销权限或缺失邮箱不得发送。

### SMTP 结果、重试与人工补偿

* `SUCCEEDED` 只表示 SMTP server 在 `DATA` 结束后正向接收并承担投递/退信责任，不表示最终收件箱送达或已读。
* SMTP 明确 4xx 临时拒绝以及事务开始前的连接失败进入 `RETRY_WAIT`，使用与报告 job 一致的有界退避并加入 jitter。
* SMTP 5xx、无效收件人、认证失败、TLS/证书失败、无效配置、消息构造失败或超限进入 `PERMANENT_FAILURE`，不自动重试。
* SMTP 事务开始后的 timeout/reset、最终 `DATA` 回应丢失、worker 在 `SENDING` 崩溃或无法证明发送阶段的异常进入 `UNKNOWN`，不得自动重试。
* 普通人工 retry 只能重放明确失败；`UNKNOWN` 仅允许通过明确确认“可能发送重复邮件”的 force replay，且必须记录安全审计。
* 错误只持久化稳定安全码，不持久化 SMTP 原始异常、服务器 banner、凭据、完整邮箱或邮件正文。

### API 与界面

* tenant 用户管理 API、OpenAPI、前端用户表单和列表支持邮箱创建/更新，使用既有 `USER_MANAGE` 边界和 optimistic version。
* tenant SMTP 配置 API/界面仅对 `USER_MANAGE` 开放；secret 输入遵循既有“已配置占位、不回显密码、未修改则保留”的模式。
* 报告投递列表仅允许 `REPORT_READ` 用户查看自己的订阅投递；`REPORT_GENERATE` 用户可执行普通 retry，force replay 必须单独确认。
* 报告 job 界面显示每个收件人的安全投递状态、attempt、时间和错误码，不显示完整邮箱或 SMTP 细节。

## Acceptance Criteria

* [x] Flyway 将既有 tenant membership 安全迁移为 nullable email，并创建 tenant 内邮箱唯一约束、tenant SMTP 配置和逐 recipient delivery outbox。
* [x] 用户创建/邮箱更新、格式与 header injection、大小、唯一性、optimistic version、tenant 隔离和安全审计均有回归。
* [x] SMTP 配置的 allowlist、TLS mode、port、secret preserve/replace、密文 tenant AAD、redacted view 和权限边界均有回归。
* [x] 报告 job 成功后按 recipient 幂等创建 delivery；重复物化、重复 claim 和 lease 恢复不会并发发送同一 delivery key。
* [x] 本地 mock SMTP 覆盖成功接收、4xx、5xx、认证/TLS失败、连接前失败、发送后超时、最终回应丢失和部分异常；结果严格映射到 retry/permanent/unknown。
* [x] 每封邮件只有一个收件人，主题/正文 UTF-8 正确，完整报告可读，大小超限不会静默截断。
* [x] 同一 delivery key 在自动执行路径中最多产生一次用户可见邮件；force replay 明确暴露重复风险并写审计。
* [x] tenant A 不能读取或使用 tenant B 的 SMTP 凭据、投递、报告、订阅或用户邮箱。
* [x] 日志、API、审计和异常输出不泄露 SMTP password、服务器原始信息、完整收件地址或报告正文。
* [x] OpenAPI UTF-8 解析、Hibernate schema validation、PMD、后端全量测试和前端测试/构建通过。

## Decision (ADR-lite)

**Context**: 飞书群 Webhook 改动较小，但只能把报告发到固定群，订阅 recipients 无法对应个人；用户更重视实际收取、搜索和留档。当前身份表没有邮箱，项目也没有 SMTP 依赖。

**Decision**: P2-3C 首版采用 tenant 级 SMTP 邮件。为 tenant membership 增加可选邮箱，为每个 tenant 增加一套加密 SMTP 配置，并按订阅 recipient 一人一条 outbox、一人一封纯文本完整报告邮件。飞书 Webhook 不进入本次实现。

**Consequences**: 需要同时修改身份、tenant 配置、投递后端和管理界面，改动大于群 Webhook；换来个人化、可留档的实际投递。SMTP 只提供服务器接受语义，歧义超时仍需 `UNKNOWN` 和人工补偿，退信/已读不在首版保证内。

## Research References

* [`research/smtp-delivery-contract.md`](research/smtp-delivery-contract.md) — Spring Mail 集成、SMTP 接受语义、地址校验和 retry/unknown 矩阵。
* [`research/feishu-custom-bot-contract.md`](research/feishu-custom-bot-contract.md) — 已评估但未选择的飞书低改动备选。

## Technical Approach

* 增加 `spring-boot-starter-mail`，在 notification infrastructure 内按 tenant resolved config 动态构造 sender；不使用单一全局 `spring.mail` 凭据。
* 在 `reporting` 模块增加 delivery domain/entity、repository、service、runner 和 provider-neutral port，沿用 P2-3B 的 claim/lease/安全错误码风格。
* 增加 tenant SMTP config registry、notification-specific secret provider、受权限保护的配置 API，以及对应前端设置面板。
* 扩展用户管理和 recipient directory 的 email readiness 数据流；发送时只以 recipient user ID 解析当前 tenant 身份与邮箱。
* 报告生成事务只落 outbox，不直接访问 SMTP；runner 每次只处理一个 recipient delivery。

## Out of Scope

* 不实现飞书、钉钉、企业微信或其他通知通道。
* 不实现邮箱自助绑定、所有权验证、邀请邮件或邮件地址变更确认。
* 不实现附件、PDF、富 HTML/自定义模板、营销群发、抄送/密送或公网报告链接。
* 不实现 DSN/退信回调、最终送达、垃圾箱检测、打开/点击追踪或已读回执。
* 不自动重放结果未知的 SMTP 发送。
* P2-4C 的告警与 P2-4D 的生产运行手册继续由后续任务交付。
