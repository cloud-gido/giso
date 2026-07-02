# 架构 FAQ

## Q1. GISO 整体链路是什么？

三端 SDK → POST /v1/track → Gateway 校验 → Kafka → Doris → Metabase/BI。

注册表在 PostgreSQL；`schema/*.yaml` 用于 CI 常量生成与 Git 审计。

## Q2. 公网能访问什么？

| 路径 | 公网 |
|------|------|
| POST /v1/track | ✅ |
| GET /v1/config | ✅ |
| /admin/ | 内网白名单 |

## Q3. 注册表存在哪里？

生产：PostgreSQL 库 `giso`（与 GIDO/DataEase 共用 RDS 实例，库名独立）。管理台保存持久化，Pod 重启不丢。

## Q4. 多空间隔离

Spaces + `X-GISO-Space` + `space_app_keys`。`video-*` App Key 路由到 `longvideo` 空间。
