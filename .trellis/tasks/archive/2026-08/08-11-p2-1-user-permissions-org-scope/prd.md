# P2-1A 身份、RBAC 与会话基础

## Goal

将当前“共享 access key + 自签随机 session subject + 部分 `X-API-Key`”的 POC 鉴权替换为真实用户身份、数据库可配置 RBAC、可即时撤销的 access/refresh session 和统一的业务 API 授权边界。该增量先建立可信身份与功能权限，不在本任务中宣称组织数据范围或多租户隔离已经完成。

## Requirements

### 1. 用户身份与首次管理员初始化

* 引入 Spring Security，后端身份是所有 HTTP、SSE、Agent tool、知识查询和报告用例的安全事实源。
* 用户至少包含：不可变 ID、大小写不敏感且唯一的规范化用户名、显示名、密码哈希、启用状态、`mustChangePassword`、创建/更新时间。
* 密码使用 Spring Security `PasswordEncoder` 的自描述自适应单向哈希；明文密码不得写入数据库、日志、错误或审计记录。
* 仅当用户表为空且初始化用户名/密码配置完整有效时创建首个 `ADMIN`；过程必须幂等，不得覆盖已有账号或硬编码默认密码。
* 初始化管理员、新建用户和管理员重置密码均使用临时密码，并设置 `mustChangePassword=true`。
* 临时密码登录只获得受限会话，只能访问当前身份、修改密码和退出；改密成功后撤销旧会话并要求使用新密码重新登录。
* 删除 `APP_ACCESS_KEY`、旧 shared-key 校验服务及 `/api/auth/verify`，不保留兼容入口。

### 2. 可撤销会话生命周期

* 登录使用用户名和密码，成功后签发默认 30 分钟的 opaque access token 和默认 7 天的 refresh token；两个 TTL 均可配置。
* 原始 access token 只在签发响应中返回一次；refresh token 只通过 `HttpOnly` Cookie 传输，并在生产环境启用 `Secure` 和合适的 `SameSite`。
* 数据库只保存 access/refresh token 摘要、用户、会话族、签发/过期/轮换/撤销元数据，不保存原始 token。
* refresh 成功必须轮换 refresh token；已轮换 token 再次使用视为重放，撤销整个会话族并写入安全审计事件。
* 提供登录、刷新、当前用户、修改密码、退出当前会话和退出全部会话能力。
* logout 撤销当前会话族；logout-all、账号禁用、密码重置撤销该用户全部会话。
* refresh/logout 的 Cookie 凭据路径必须具备明确的 CSRF/Origin 防护；CORS credentials 只允许配置中的可信前端来源。
* 前端只保留短期 access token；页面恢复或 token 过期时通过 refresh Cookie 恢复会话。并发 401 只能触发一个刷新请求，其余请求等待同一结果。

### 3. 固定权限目录与可配置角色

* 权限键由代码固定定义，数据库角色只能组合应用认识的权限键，不允许写入无真实校验点的任意字符串。
* 首版权限目录：
  * `USER_READ`、`USER_MANAGE`
  * `ROLE_READ`、`ROLE_MANAGE`
  * `DASHBOARD_READ`、`DATA_READ`
  * `CHAT_USE`、`KNOWLEDGE_QUERY`
  * `REPORT_READ`、`REPORT_GENERATE`
  * `MODEL_CONFIG_TEST`
* 幂等预置三个角色：
  * `ADMIN`：全部首版权限。
  * `ANALYST`：`DASHBOARD_READ`、`DATA_READ`、`CHAT_USE`、`KNOWLEDGE_QUERY`、`REPORT_READ`、`REPORT_GENERATE`、`MODEL_CONFIG_TEST`。
  * `VIEWER`：`DASHBOARD_READ`、`DATA_READ`、`REPORT_READ`。
* 角色权限、用户角色或账号状态变更后，新请求必须读取数据库中的最新授权结果；权威权限不得固化进长效 token。
* `CHAT_USE` 不自动授予 `DATA_READ`、`KNOWLEDGE_QUERY` 或 `REPORT_GENERATE`；Agent/tool 层必须按实际工具能力再次检查权限。
* `/api/auth/me` 返回用户 ID、用户名、显示名、账号状态、`mustChangePassword`、角色和权限键，供前端渲染允许的入口；前端隐藏不能替代后端校验。

### 4. 最小用户与角色管理 API

* 提供仅管理员可用的用户查询、创建、启用/禁用、密码重置和角色分配 API。
* 提供仅管理员可用的角色查询、创建自定义角色和更新权限组合 API。
* 用户名规范化后重复、空角色、未知角色、未知权限、受保护预置角色非法变更等输入必须稳定拒绝。
* 系统必须阻止停用最后一个有效管理员，或从最后一个有效管理员移除管理能力。
* 本任务只交付 API/OpenAPI 契约，不实现用户/角色图形化管理页面。

### 5. 统一业务 API 授权

* 删除 `ApiKeyFilter`、`APP_API_KEY`、`app.security.api-key` 和 `X-API-Key` 契约。
* 登录/刷新所需端点、健康检查和静态应用资源显式公开；其余业务 `/api/**` 默认要求用户 bearer session。
* 缺少或无效身份返回统一 JSON 401；身份有效但缺少权限返回统一 JSON 403。
* 原始数据、聚合指标、Dashboard、Chat/SSE、Agent tools、知识查询、报告读取/生成和模型配置测试均必须有明确的 endpoint 与共享 service/tool 权限检查。
* 未来系统对系统访问使用独立、可审计的 service account 设计，不复用共享静态 API key。

### 6. 安全审计与失败加固

* 审计账号创建、启停、密码重置、角色分配、角色权限变更、会话重放和其他安全敏感结果。
* 审计记录至少包含 actor、action、target、outcome、timestamp 和 trace/request ID；不得包含明文密码或原始 token。
* 登录失败使用不泄露“用户不存在/密码错误/账号禁用”差异的通用响应，并按规范化用户名 + 客户端来源做限流，不采用可被恶意触发的永久锁号。
* 页面刷新、多标签页、并发 refresh、过期 access token、禁用账号、角色变更、密码重置、refresh 重放和数据库重启都必须有回归覆盖。
* 身份和角色使用稳定不可变 ID，并通过单一授权上下文保留未来组织/tenant 扩展点；P2-1A 不提前增加无法正确执行的 tenant 隔离声明。

### 7. 前端与文档迁移

* 登录页改为用户名 + 密码，支持通用登录错误、加载态和临时密码强制修改视图。
* 前端集中维护当前用户和权限；Dashboard、Chat、报告相关入口、模型配置等按权限隐藏或禁用。
* 自动刷新失败、账号禁用或会话撤销时清空本地 access token 和身份状态并回到登录页。
* README、联调文档、生产路线图状态、OpenAPI、配置说明和测试不再要求或描述 `APP_ACCESS_KEY`、`APP_SESSION_SECRET`、`APP_API_KEY` 或 `X-API-Key` 为有效 P2-1A 契约。
* 生产 schema 使用 Flyway 新迁移；默认 H2 演示/测试与 PostgreSQL `prod` 校验路径必须一致通过。

## Acceptance Criteria

* [ ] 空用户库在完整有效的初始化配置下只创建一个临时密码 `ADMIN`；重启不重复创建或覆盖密码，且不存在硬编码默认账号。
* [ ] 用户能够登录、查询当前身份、强制修改临时密码、刷新会话、退出当前会话和退出全部会话。
* [ ] 数据库、日志和审计中不存在明文密码或原始 access/refresh token。
* [ ] refresh 轮换成功；旧 refresh token 重放会撤销会话族；logout、禁用和密码重置能即时撤销相关会话。
* [ ] 权限或角色变更后的下一请求立即按数据库最新状态授权，不依赖 token 中的权限快照。
* [ ] `ADMIN`、`ANALYST`、`VIEWER` 按约定权限矩阵幂等创建；未知权限键不能写入角色。
* [ ] 管理员能通过 API 管理用户和自定义角色；非管理员得到 403；系统不能失去最后一个有效管理员。
* [ ] 所有业务 API、SSE 和 Agent/tool 能力具备允许/拒绝回归；Chat 不能绕过数据、知识或报告权限。
* [ ] 匿名业务访问返回 401，越权访问返回 403，错误体遵守统一 JSON 契约且不泄露敏感认证细节。
* [ ] 前端登录、强制改密、自动刷新、权限入口和失效退出流程均有组件/composable/API 测试。
* [ ] `APP_ACCESS_KEY`、`APP_SESSION_SECRET`、`APP_API_KEY`、`X-API-Key` 及旧 filters/services 从运行契约、OpenAPI 和文档中移除。
* [ ] H2 与 PostgreSQL Flyway schema 契约、后端 PMD/测试、前端 lint/test/build 全部通过。

## Definition of Done

* 单元、controller/security integration、数据库迁移和前端回归已补齐。
* 后端 PMD 与测试、前端 lint/test/build、跨层权限流检查全部通过。
* README、OpenAPI、联调说明和 P2 状态说明与实际行为一致。
* 会话撤销、首次初始化和 Flyway 升级具备明确的发布/回滚说明。

## Out of Scope

* 组织树以及门店/区域/集团数据范围过滤（P2-1B）。
* 完整多集团/多品牌 tenant 隔离（P2-2）。
* 用户、角色、权限和组织的图形化管理页面（P2-1C）。
* 邮件/短信自助找回密码、外部身份提供商或 SSO。
* service account、API key 管理或机器客户端认证。
* 报告订阅、定时生成、外部渠道推送、完整运维监控与模型成本治理。
* PDF/Word 导出和任意文档摄取。

## Technical Approach

* Spring Boot 3.4.5 / Java 21 中引入 `spring-boot-starter-security`，使用 SecurityFilterChain、统一 authentication entry point/access denied handler，以及 service/method 级授权。
* 使用 Spring Data JPA 持久化用户、角色、角色权限、用户角色、access/refresh session 和安全审计；生产由下一版 Flyway migration 管理。
* opaque token 使用密码学安全随机值并只保存摘要；授权上下文在每次请求解析当前用户状态与权限。
* 前端沿用 Vue composable/API 分层，把 access token、refresh single-flight、当前用户和权限集中到认证状态中。
* 研究依据见 [`research/auth-and-org-scope-architecture.md`](research/auth-and-org-scope-architecture.md)。

## Decision (ADR-lite)

**Context**: P2 权限同时包含身份、功能权限、组织范围、管理审计和未来 tenant 边界，一次实现会形成过大的跨层变更；当前 shared-key 与双 filter 白名单也无法表达真实用户授权。

**Decision**: P2-1A 只交付身份、可配置 RBAC、最小管理 API、安全审计和可撤销 access/refresh session。采用一次性管理员初始化、临时密码强制修改、固定权限目录 + 数据库角色，并统一移除 shared access key 与 `X-API-Key`。

**Consequences**: 第一个增量先建立真实、可撤销、可审计的功能权限边界；组织数据过滤、完整管理页面、多租户和机器身份继续作为后续独立任务。新业务能力今后必须同步增加权限键、endpoint/service/tool 校验、预置角色映射和允许/拒绝测试。

## Implementation Plan (small increments)

1. **Schema and security foundation**: 新表/Flyway、JPA 模型、权限目录、预置角色、管理员初始化、Spring Security 骨架与统一 401/403。
2. **Authentication session flow**: 登录、me、refresh 轮换/重放、logout、logout-all、改密和会话撤销，并迁移前端登录/刷新/强制改密。
3. **RBAC administration and audit**: 最小用户/角色管理 API、最后管理员保护、安全审计与 OpenAPI。
4. **Business authorization convergence**: 所有 controller/service/Agent tool 权限校验，移除旧 shared-key 和 `X-API-Key` 代码/配置，增加权限感知前端入口。
5. **Cross-layer regression and docs**: H2/PostgreSQL 契约、PMD/test、frontend lint/test/build、README/联调/路线图与发布回滚说明。
