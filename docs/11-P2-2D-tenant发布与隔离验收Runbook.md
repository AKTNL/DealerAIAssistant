# P2-2D tenant 发布与隔离验收 Runbook

本 Runbook 面向未参与实现的发布/验收人员。所有命令都应在发布工单中保存输出；命令中的 `<...>` 占位符必须通过安全渠道替换，不能写入仓库或日志。

## 发布前

1. 对生产 PostgreSQL 做全量备份，并保存 `flyway_schema_history`、tenant/membership、active batch、knowledge metadata 和 report draft 计数。
2. 确认应用使用 `prod` profile、`APP_DB_*`、`APP_MODEL_SECRET_KEY`（Base64 解码后 32 bytes）和 `APP_EXCEL_FALLBACK_ENABLED=false`；`APP_MODEL_SECRET_KEY` 在发布后不得出现在环境导出、日志或前端响应中。
3. 在只读副本演练 `V6__create_tenant_foundation.sql` 与 `V7__tenantize_platform_resources.sql`，确认所有非空业务表的 `tenant_id`、外键和 tenant 复合索引。
4. 使用两个测试 tenant 创建相同 dealer、opportunity、lead、task、campaign、target、batch、组织节点、knowledge chunk 和 report draft business ID。
5. 记录当前应用版本、迁移版本、备份位置、回滚窗口和发布负责人。

## 可执行验收命令

在具备 PostgreSQL 客户端的发布环境执行（不要在没有数据库的开发机上宣称 smoke 已完成）：

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
cd backend
mvn "-Dfrontend.skip=true" "-DskipTests" flyway:info
mvn "-Dfrontend.skip=true" test
```

应用启动后，用只读账号检查迁移和 tenant 约束：

```sql
SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT tenant_key, enabled, COUNT(m.id) AS memberships
FROM tenants t LEFT JOIN tenant_memberships m ON m.tenant_id = t.id
GROUP BY tenant_key, enabled ORDER BY tenant_key;
SELECT table_name, column_name FROM information_schema.columns
WHERE column_name = 'tenant_id'
ORDER BY table_name;
EXPLAIN (COSTS OFF) SELECT * FROM import_batches
WHERE tenant_id = <tenant_a_id> AND active = true
ORDER BY activated_at DESC, id DESC LIMIT 1;
```

验收记录至少应包含：Flyway 已到 V7、每个 tenant 的 active batch 至多一个、业务/报告/知识查询使用 tenant 条件、以及上面查询计划命中 tenant 索引或合理复合索引。

## 对抗矩阵

| 场景 | 预期 |
| --- | --- |
| 无 bearer token | 401 |
| 有效 token，无 membership | 403，消息不区分 tenant 是否存在 |
| 多 membership，缺少 `X-Tenant-Key` | 403 |
| header 选择未加入或 disabled tenant | 403 |
| tenant A 猜测 tenant B 的业务、组织、batch、knowledge、report ID | 空结果或 403，不返回存在性 |
| tenant A 读取/保存/删除模型配置 | 只能看到 A 的 `baseUrl`、`model`、allowlist 和 `apiKeyConfigured`；永不返回密文；不能影响 B |
| tenant A 聊天请求伪造 `baseUrl`、`apiKey`、`model` | 旧字段即使存在也不作为浏览器合同；服务端按 A 的 tenant 配置解析，不能跨 tenant 读取密钥 |
| membership/role 被禁用后重试请求 | 下一请求立即采用新状态 |
| HTTP、SSE、Agent tool、报告、导入和管理 API | 使用同一 tenant context |

## 发布与回滚

1. 先执行备份校验，再执行 Flyway migrate；迁移失败立即停止应用发布，不执行 `clean`。
2. smoke test 通过后开放流量：登录、选择 tenant、dashboard、data query、SSE、knowledge、report、model config/test、organization admin。
3. 发现隔离问题时先撤回应用版本并阻断写入；按备份恢复或执行经过审批的反向数据修复，禁止直接删除审计记录。
4. 回滚后重新比较每个 tenant 的记录计数、active batch、knowledge metadata tenant filter、report draft tenant_id 和模型配置 `apiKeyConfigured` 状态，确认没有漂移。

## 回滚兼容窗口

- V6/V7 是前向迁移；旧应用二进制可以在短暂兼容窗口内读取原有业务列，但不得写入未带 tenant context 的业务接口。
- 回滚只允许回滚应用版本，不允许删除 `tenant_id`、membership、模型配置或审计列；恢复后必须重新执行双 tenant 对抗矩阵。
- 若密钥 provider 或 `APP_MODEL_SECRET_KEY` 不匹配，模型配置读取/测试应失败关闭，不能返回密文或回退到另一个 tenant 的配置。

## 证据保留

保留迁移日志、SQL explain/index 输出、双 tenant 对抗测试结果、HTTP/SSE/Agent 请求 trace、拒绝审计事件和恢复演练记录。任何跨 tenant 失败必须能通过 `X-Request-ID` 定位而不记录 secret、token 或 API key。
