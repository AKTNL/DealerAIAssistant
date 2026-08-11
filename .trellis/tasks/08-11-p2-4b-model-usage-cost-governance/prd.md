# P2-4B 模型使用与成本治理

## Goal

记录并归集每次模型/embedding 调用的次数、耗时、token 和估算费用，为用户、tenant、场景和模型提供可审计的用量视图与预算治理。

## Dependencies

* 前置：P2-4A 观测事件；P2-2B tenant 上下文。
* 后续：P2-4C 用量告警、P2-4D 运维收口。

## Requirements

* 使用调用元数据记录 provider/model、场景、tenant/user、input/output token、耗时、状态和 traceId；不保存秘密或默认保存完整 prompt/output。
* 建立版本化价格目录，费用计算保留币种、价格版本和估算/实际来源。
* 区分聊天、Agent、知识 embedding/retrieval、报告和后台订阅场景。
* 提供时间范围聚合、异常用量和高成本场景 API/管理视图。
* 定义缺失 token 元数据、重试、流式调用、缓存命中和 provider 账单差异处理。
* 预算策略先支持可配置软阈值；是否启用硬拒绝/熔断作为独立开关并失败安全。

## Acceptance Criteria

* [ ] 同一次重试或流式调用不会重复计费，缺失元数据明确标记为未知。
* [ ] 用户、tenant、场景、模型和日期聚合与原始事件可对账。
* [ ] tenant A 无法查看 tenant B 用量，平台汇总入口独立授权并审计。
* [ ] 价格更新不改写历史费用计算依据。
* [ ] 软预算告警和可选硬限制具有边界/并发回归。

## Open Question

* 首版只做观测与软预算，还是启用硬预算拒绝；推荐先观测和告警，积累基线后再启用硬限制。

## Out of Scope

* 不实现财务开票或 provider 账单支付。
* 不记录完整用户 prompt 作为成本治理依据。

