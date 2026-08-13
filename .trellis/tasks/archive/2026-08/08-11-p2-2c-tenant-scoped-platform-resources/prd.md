# P2-2C tenant 级平台资源

## Goal

把模型配置、知识、导入批次、报告历史和组织配置纳入已强制执行的 tenant 边界，消除共享平台资源造成的跨租户泄露。

## Dependencies

* 前置：P2-2B 请求/数据隔离强制执行。
* 后续：P2-2D、多 tenant 报告订阅和成本归集。

## Requirements

* tenant 级模型配置和允许主机策略独立存储，秘密加密或引用安全 secret provider，响应永不回显密钥。
* knowledge 文档、chunk、索引替换和检索带 tenant 条件；受控全局知识必须显式标记并只读。
* import batch、active batch 解析、质量状态和业务行全部 tenant 化。
* report draft、历史、scope、生成配置和读取入口全部 tenant 化。
* 管理 API 只能操作当前 tenant，跨 tenant 平台运维使用独立审计能力且不复用业务入口。
* tenant 删除/停用定义保留、归档和清理策略，禁止级联误删审计与共享资源。

## Acceptance Criteria

* [ ] tenant A 无法发现 tenant B 的模型配置存在性、知识引用、batch 状态或报告 ID。
* [ ] PGvector/内存知识适配器与 H2/PostgreSQL 数据路径具有一致 tenant 过滤语义。
* [ ] active batch 在 tenant 内唯一解析，不会选择另一个 tenant 的更新批次。
* [ ] tenant 停用后业务访问和后台生成停止，审计仍可保留。
* [ ] 秘密扫描、迁移验证、跨 tenant 回归和配置文档通过。

## Implementation Plan

1. 模型配置和 secret 契约 tenant 化。
2. knowledge source/index/retrieval tenant 化。
3. import batch、报告与组织配置 tenant 化。
4. 停用/保留策略、管理 API 和跨层回归。

## Out of Scope

* 不在此任务开放任意文件/URL/PDF/DOCX 摄取。
* 不实现 tenant 间知识或报告共享市场。

