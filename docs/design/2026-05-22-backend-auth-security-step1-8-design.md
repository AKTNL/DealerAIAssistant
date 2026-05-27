# 后端凭证与认证响应安全设计（优化3 Step 1-8）

## 背景

`docs/优化3.md` 要求收紧后端认证边界，消除默认凭证和不安全的会话密钥派生，并让认证失败具备最小审计能力。

当前后端仍存在几处 POC 默认行为：

- `AppProperties` 中 Java 默认访问密钥为 `demo123`，内部 API key 为 `poc-api-key`。
- `application.yml` 在环境变量缺失时回退到默认凭证。
- `SessionTokenService` 在 `app.auth.session-secret` 缺失时从访问密钥派生 demo secret。
- `AuthService` 和 `ApiKeyFilter` 使用普通字符串相等比较。
- 认证失败响应缺少 `WWW-Authenticate`，失败日志也不足。
- CORS 来源写死为本地 Vite 地址，无法通过配置调整。
- `prod` profile 未显式禁用 H2 console。

本设计只覆盖 `docs/优化3.md` 的 Step 1-8。Step 9 的会话删除所有权校验需要扩展 token payload 以绑定 `sessionId`，Step 10 的速率限制需要新增失败计数和冷却状态，二者延后单独设计和实现。

## 目标

- 未配置 `APP_SESSION_SECRET` 时不再签发或校验依赖派生 secret 的会话令牌。
- Java 默认配置和 `application.yml` 默认值不再包含 `demo123` 或 `poc-api-key`。
- 访问密钥和内部 API key 比较使用恒定时间算法。
- `ApiKeyFilter` 和 `SessionTokenFilter` 的 401 响应包含 `WWW-Authenticate: Bearer`。
- 认证失败记录 warn 日志，日志包含路径、远端地址和失败原因，不记录完整凭证。
- CORS 允许来源从配置读取，默认仍只允许本地 Vite 开发地址。
- `prod` profile 禁用 H2 console。

## 非目标

- 不引入 Spring Security。
- 不建立用户、角色或权限系统。
- 不改变前端登录页面视觉和交互。
- 不实现 `DELETE /api/chat/{sessionId}` 所有权校验。
- 不实现 `/api/auth/verify` 速率限制。

## 推荐方案

采用小范围后端安全闭环：保留现有轻量 Filter 和 access-key 登录模型，只替换不安全默认值、比较方式、失败响应和配置来源。

这个方案能直接满足本轮主要验收标准，并避免把 token payload 结构变更或限流状态管理混入同一批提交。

## 后端设计

### 配置

`AppProperties.Auth.accessKey` 和 `AppProperties.Security.apiKey` 的 Java 默认值改为空字符串。空值表示未配置，不再表示可用的 demo 凭证。

`application.yml` 改为显式空默认：

```yaml
app:
  auth:
    access-key: ${APP_ACCESS_KEY:}
    session-secret: ${APP_SESSION_SECRET:}
  security:
    api-key: ${APP_API_KEY:}
```

新增 `AppProperties.Cors`：

- `allowedOrigins` 默认 `["http://localhost:5173", "http://127.0.0.1:5173"]`
- setter 过滤空值并 trim

新增 `application-prod.yml`：

```yaml
spring:
  h2:
    console:
      enabled: false
```

### 会话令牌 Secret

`SessionTokenService.resolveSecret()` 只接受非空 `app.auth.session-secret`。如果为空，抛出 `IllegalStateException`，消息包含 `app.auth.session-secret is required`。

`issueToken()` 会因此在配置缺失时失败。`isValid()` 在需要验签时也会触发同一配置校验；这能避免服务在错误配置下静默接受或生成令牌。

### 恒定时间比较

新增或复用小型私有比较方法，统一处理空值：

- 配置值为空：直接失败。
- 输入值为空：直接失败。
- 两边非空：使用 `MessageDigest.isEqual(configuredBytes, providedBytes)`。

`AuthService.verifyAccessKey()` 和 `ApiKeyFilter` 都使用该规则。

### Filter 响应与日志

`ApiKeyFilter` 拒绝请求时：

- 设置 HTTP 401。
- 设置 `WWW-Authenticate: Bearer`。
- 返回现有 JSON 结构：`{"code":401,"message":"Invalid API key"}`。
- 记录 warn 日志，包含 servlet path、remote address、失败原因。

`SessionTokenFilter` 拒绝请求时：

- 设置 HTTP 401。
- 设置 `WWW-Authenticate: Bearer`。
- 返回现有 JSON 结构：`{"code":401,"message":"Login session expired."}`。
- 记录 warn 日志，区分缺少 bearer token、格式错误或 token 校验失败等原因。

日志不包含完整 `X-API-Key`、`Authorization` header 或 token。

### CORS

`CorsConfig` 注入 `AppProperties`，从 `app.cors.allowed-origins` 读取来源列表：

```yaml
app:
  cors:
    allowed-origins:
      - http://localhost:5173
      - http://127.0.0.1:5173
```

如果配置为空列表，保持“无显式来源”的安全失败行为，不回退到通配符。

## 测试设计

先写失败测试，再改实现。

后端聚焦测试：

- `SessionTokenServiceTest`
  - 空 `session-secret` 时 `issueToken()` 抛出 `IllegalStateException`。
  - 保留签发、篡改、过期 token 测试。
- `AuthServiceTest`
  - 正确 access key 返回 true。
  - 错误、空输入、空配置返回 false。
- `ApiKeyFilterTest`
  - 缺少或错误 API key 返回 401。
  - 401 包含 `WWW-Authenticate: Bearer`。
  - 正确 API key 放行。
  - 空配置不接受任何请求凭证。
- `SessionTokenFilterTest`
  - 缺少或无效 token 返回 401。
  - 401 包含 `WWW-Authenticate: Bearer`。
  - 有效 token 放行。
- `CorsConfigTest`
  - 默认允许本地 Vite 来源。
  - 自定义 `app.cors.allowed-origins` 会被应用。

验证命令：

```powershell
cd backend
mvn "-Dfrontend.skip=true" "-Dtest=SessionTokenServiceTest,AuthServiceTest,ApiKeyFilterTest,SessionTokenFilterTest,CorsConfigTest" test
mvn "-Dfrontend.skip=true" test
```

## 验收标准

- 未配置 `APP_SESSION_SECRET` 时，不再派生 demo secret。
- Java 代码和 `application.yml` 中不再包含 `demo123`、`poc-api-key` 作为默认凭证。
- 访问密钥和 API key 比较使用恒定时间算法。
- 认证失败 401 响应包含 `WWW-Authenticate: Bearer`。
- 认证失败日志不泄漏完整凭证。
- CORS 来源可通过配置调整，默认仍限制为本地 Vite 地址。
- `prod` profile 禁用 H2 console。
