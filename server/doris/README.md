# Doris 落地层

链路：`SDK → 网关（校验）→ Kafka → Doris Routine Load → ods_events`

| 文件 | 内容 |
|---|---|
| `01_create_tables.sql` | ODS 建表：`ods_events`（含 `env` 列，动态分区365天）+ `ods_events_quarantine` |
| `02_routine_load.sql` | Routine Load：`events_raw`（prod）、`events_raw_test`（test）、`events_quarantine` |
| `03_alter_add_env.sql` | 已有表补 `env` 列（与 ClickHouse 对齐） |
| `03_example_queries.sql` | CTR、播放时长、投注漏斗、双链路对账、质量监控等示例查询 |

## env 分流（与 ClickHouse 一致）

| SDK | Kafka Topic | Doris `env` |
|---|---|---|
| `debug=false`（默认） | `events_raw` | prod |
| `debug=true` | `events_raw_test` | test |

本地 Docker：`deploy/scripts/apply-doris-test-pipeline.sh` 可为已有集群补 test 链路。

## 接入步骤

```bash
# 1. 网关切到 kafka 出口（gateway.yaml）
#    sinks: [kafka]   # 或迁移期双写 [file, kafka]

# 2. 建 Kafka topic
kafka-topics --create --topic events_raw --partitions 8 --replication-factor 3 ...
kafka-topics --create --topic events_raw_test --partitions 4 --replication-factor 3 ...
kafka-topics --create --topic events_quarantine --partitions 2 --replication-factor 3 ...

# 3. Doris 建表 + 启动 Routine Load（改 broker 地址后执行）
mysql -h <doris-fe> -P 9030 -u root < 01_create_tables.sql
mysql -h <doris-fe> -P 9030 -u root < 02_routine_load.sql
# 已有表无 env 列时：
mysql -h <doris-fe> -P 9030 -u root < 03_alter_add_env.sql

# 4. 验证
mysql> USE tracking; SHOW ROUTINE LOAD;
mysql> SELECT env, event, count(*) FROM tracking.ods_events GROUP BY env, event;
```

## 设计要点

- **高频字段拉平成列**（event/did/uid/pgid/eid/biz_code/env…）做分区裁剪和谓词下推；**长尾参数留 JSON 列**（`pg_params`/`el_params`/`biz_params`），注册表新增参数不用改表，`params->'$.xxx'` 直接查。
- **去重**：`log_id` 列保留，明细模型不强制去重（Routine Load 本身 exactly-once 到分区级）；需要严格幂等的报表在 DWD 层按 `log_id` 去重物化。
- **后续分层**：ODS 之上建 DWD（按业务域拆表+去重）、DWS（天级聚合：曝光点击宽表、漏斗宽表），用 Doris 物化视图或定时 INSERT 维护。
