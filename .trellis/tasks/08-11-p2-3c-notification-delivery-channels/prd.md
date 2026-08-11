# P2-3C 通知投递通道

## Goal

建立统一、可审计的报告投递端口，并完成一个优先通道的生产级适配，使生成成功的报告能够幂等投递并记录回执。

## Dependencies

* 前置：P2-3B 可靠生成任务；P2-2C tenant secret/config 边界。
* 后续：P2-4C 投递告警、P2-4D 生产运行手册。

## Requirements

* 应用层定义 channel-neutral delivery request/result；适配器不能绕过 tenant、报告读取和权限检查。
* 投递记录包含 tenant、job/report、通道、目标摘要、状态、attempt、provider message id、时间和安全错误码。
* 使用稳定 idempotency key 防止重试产生重复消息；处理 provider 超时与未知结果。
* 通道凭据按 tenant 安全存储或引用 secret provider，不进入日志、审计 detail、API 响应或数据库明文。
* 校验收件目标，限制模板内容和链接；对 webhook/callback 做签名、时效和重放校验。
* 首版只交付一个优先通道，其余通过相同端口后续扩展。

## Acceptance Criteria

* [ ] 成功、超时、限流、永久拒绝、重复回调和重试均有回归。
* [ ] 同一 delivery key 最多产生一次用户可见消息。
* [ ] tenant A 不能使用 tenant B 的凭据、模板、报告或收件目标。
* [ ] 秘密扫描、回执审计和 provider sandbox/真实环境 smoke test 通过。
* [ ] 失败可由 P2-4C 告警并链接到可执行 runbook。

## Open Question

* 首个通道选择邮件、企业微信、钉钉或飞书；推荐以试点用户实际使用渠道为唯一首发通道。

## Out of Scope

* 不在一个任务中同时实现全部企业协作平台。
* 不支持任意自定义 HTML/脚本模板。

