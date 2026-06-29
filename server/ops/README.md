# 运维：对账与告警

对标大同「数据链路 → 数据对账 / 预警体系」的最小落地。

## 数据对账（reconcile.sh）

比对**网关接收量 vs Doris 落地量**（UTC 小时级），覆盖 Kafka → Routine Load 这段链路的丢数据风险：

```
网关 /admin/api/hourly（内存计数，保留 48h）
        vs
Doris: SELECT COUNT(*) FROM ods_events WHERE stime IN [该小时)
```

- crontab 每小时第 10 分钟跑（给 Routine Load 留消费余量）：
  `10 * * * * GATEWAY=http://gw:8080 DORIS_HOST=doris reconcile.sh >> /var/log/qy-reconcile.log 2>&1`
- 偏差 > `THRESHOLD_PCT`（默认 1%）退出码 1，接告警通道（alertmanager webhook / 钉钉机器人）
- 注意：网关重启会丢内存小时计数，脚本对 received=0 的小时自动跳过，不误报

## 告警规则（prometheus-rules.yml）

| 告警 | 条件 | 含义 |
|---|---|---|
| GatewayDown | /metrics 抓不到 2min | 网关挂了 |
| EventErrorRateHigh | 错误率 > 5% 持续 10min | 大量未登记/类型错误事件进隔离区 |
| VersionErrorRateHigh | 单版本错误率 > 10% | 新版本埋点回归有问题（灰度质量门禁） |
| AuthFailureSpike | 401 > 10/s | app_key 配错或恶意流量 |
| KafkaSpilling | spill 计数增长 | broker 故障（数据不丢，自动回放，但要修） |
| RateLimitTriggered | 429 > 50/s | 容量预警或单 IP 异常打量 |

Prometheus 抓取配置示例：

```yaml
scrape_configs:
  - job_name: giso-gateway
    scrape_interval: 30s
    static_configs:
      - targets: ["gw:8080"]
    # 管理台开了 Basic Auth 的话 /metrics 不受影响（独立端点，无鉴权）
rule_files:
  - prometheus-rules.yml
```
