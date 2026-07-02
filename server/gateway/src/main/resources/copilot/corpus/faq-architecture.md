# 架构与性能 FAQ

## Q1. GISO 整体链路是什么？

三端 SDK → HTTPS POST /v1/track（批量 gzip）→ Gateway 校验 → Kafka → Doris → Metabase/BI。

注册表在 PostgreSQL；`schema/*.yaml` 用于 CI 常量生成与 Git 审计。

## Q2. Gateway 是 Spring 吗？

**不是。** `giso-gateway` 是 Java 21 **单体 JAR**：JDK `HttpServer` + Jackson + SnakeYAML，无 Spring/Tomcat。埋点接入是短路径无状态（解压→校验→写 Kafka→204），生产靠 **K8s 多副本** 扩展。

## Q3. HTTPS 上报会影响 App 性能吗？

**SDK 正常使用影响很小**：攒批（20 条/15 秒）、gzip、后台线程上报、失败落盘重试、退后台 flush。主线程只入队。避免生产开 debug 实时模式、业务线程自己高频 POST。

## Q4. 数据量大 Gateway 会崩吗？

Gateway 是薄接入层，**Kafka 扛洪峰、Doris 扛存储**。防护：单请求 ≤1MB、按 IP 限流（429）、Kafka 异步写、broker 故障 spill 落盘回放。应对：多副本、开 rate_limit、Kafka 加分区、监控 spill 指标。回滚镜像：`giso-gateway:0.1`。

## Q5. 公网能访问什么？

| 路径 | 公网 |
|------|------|
| POST /v1/track | ✅ |
| GET /v1/config | ✅ |
| /admin/ | 内网白名单 |

## Q6. 注册表存在哪里？

生产：PostgreSQL 库 `giso`（与 GIDO/DataEase 共用 RDS 实例，库名独立）。管理台保存持久化，Pod 重启不丢。

## Q7. 多空间隔离

Spaces + `X-GISO-Space` + `space_app_keys`。`video-*` App Key 路由到 `longvideo` 空间。
