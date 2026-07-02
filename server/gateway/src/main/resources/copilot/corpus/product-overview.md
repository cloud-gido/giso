# GISO 产品概览

## 定位

**GIDO 的数据源头** — Schema 驱动的行为分析平台，方法论参考腾讯大同。

## 核心能力

- 9 个标准事件 + 注册表强校验
- 四端 SDK（Web / Android / iOS / Server）
- 管理台：联调 SSE、注册表 CRUD、审批、空间、Copilot 答疑
- Kafka → Doris 实时 + 可选 S3/Paimon 湖仓

## 与 GIDO 关系

| | GIDO | GISO |
|---|------|------|
| 角色 | 大数据开发治理平台 | 埋点接入与治理 |
| 数据 | Flink + Paimon | Kafka + Doris |

同一 Doppler 项目、同一 deployment 两仓发布模式。

## Copilot

管理台 **玑源 Copilot** 解答产品与埋点流程问题。Provider 可插拔：`doc`（默认）/ `openai` / `gido_proxy`。
