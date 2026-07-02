# 性能与容量 FAQ

## App 端：HTTPS 批量上报卡不卡？

不卡的关键是 **必须用 SDK**，不要业务线程自己调接口。

SDK 默认：满 20 条或 15 秒发一批；gzip 压缩；Android `EventQueue` 后台单线程；失败写本地队列指数退避；进后台 flush。响应 204 无 body。

误用会变重：debug 实时模式、绕过攒批高频 POST、单批超协议上限（50 条/256KB，解压后 1MB）。

## 服务端：单体 JAR 扛得住吗？

Gateway 不做分析计算，只做：解压 JSON → 注册表校验 → 异步 Kafka → 204。

瓶颈通常在 Kafka 吞吐或 Gateway CPU（校验），不是「没用 Spring」。单机有上限；生产应：

- K8s **≥2 副本** + 负载均衡
- `limits.rate_limit_rps`（示例 100）防打满
- `max_body_bytes: 1048576`
- Kafka 分区扩容
- 看 `giso_kafka_spilled_total` 是否持续上涨

## 和微服务架构怎么选？

埋点 ingest 业界常见做法是 **轻量无状态接入 + 消息队列削峰**。GISO 单体 Gateway 可水平扩；流量极大时再演进专用 ingest 集群，协议不变。

## 部署回滚

镜像：`ghcr.io/cloud-gido/giso/giso-gateway:0.1`（baseline）或 `main-<git-sha>`。Helm 改 `image.tag` 即可。
