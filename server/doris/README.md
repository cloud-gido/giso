# Doris 落地层

链路：`SDK → 网关（校验）→ Kafka → Doris Routine Load → ods_events`

| 文件 | 内容 |
|---|---|
| `01_create_tables.sql` | ODS 建表：`ods_events`（含 `env` 列，动态分区365天）+ `ods_events_quarantine` |
| `02_routine_load.sql` | Routine Load：`giso_events_raw`（prod）、`giso_events_raw_test`（test）、`giso_events_quarantine` |
| `03_alter_add_env.sql` | 已有表补 `env` 列（与 ClickHouse 对齐） |
| `03_example_queries.sql` | CTR、播放时长、投注漏斗、双链路对账、质量监控等示例查询 |
| `06_rebuild_quarantine_raw_only.sql` | 隔离区 raw-only 重建 + 全量重灌（新 consumer group） |
| `07_alter_ods_events_keep_raw.sql` | 已有表补 `fg_dur` / `page_ext` / `element_ext` / `biz_ext` / `raw` |

隔离区 `ods_events_quarantine` 入库只保留 `event_date` / `stime` / `raw` 三列（jsonpaths 仅 `$.`），`stime`/`event_date` 用入库时刻；原始事件时间从 `get_json_string(raw, '$.stime')` 读取，避免 `stime/ctime=0` 落到 1970 分区导致 Routine Load 失败。

## env 分流（与 ClickHouse 一致）

| SDK | Kafka Topic | Doris `env` |
|---|---|---|
| `debug=false`（默认） | `giso_events_raw` | prod |
| `debug=true` | `giso_events_raw_test` | test |

本地 Docker：`deploy/scripts/apply-doris-test-pipeline.sh` 可为已有集群补 test 链路。

## 接入步骤

```bash
# 1. 网关切到 kafka 出口（gateway.yaml）
#    sinks: [kafka]   # 或迁移期双写 [file, kafka]

# 2. 建 Kafka topic
kafka-topics --create --topic giso_events_raw --partitions 8 --replication-factor 3 ...
kafka-topics --create --topic giso_events_raw_test --partitions 4 --replication-factor 3 ...
kafka-topics --create --topic giso_events_quarantine --partitions 2 --replication-factor 3 ...

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

- **高频字段拉平成列**（event/did/uid/pgid/eid/biz_code/env/`fg_dur`…）做分区裁剪和谓词下推；**长尾参数留 JSON**（`pg_params`/`el_params`/`biz_params`）；**嵌套整包**（`page_ext`/`element_ext`/`biz_ext`/`common_ext`）+ **`raw` 原文**保证未映射字段不丢，事后用 `get_json_*` / `->` 补查。
- **去重**：`log_id` 列保留，明细模型不强制去重（Routine Load 本身 exactly-once 到分区级）；需要严格幂等的报表在 DWD 层按 `log_id` 去重物化。
- **后续分层**：ODS 之上建 DWD（按业务域拆表+去重）、DWS（天级聚合：曝光点击宽表、漏斗宽表），用 Doris 物化视图或定时 INSERT 维护。

## 排障：`no partition for this tuple` → Routine Load PAUSED

### 现象

- Kafka topic（如 `giso_events_raw_test`）**有数据**
- Gateway 写 Kafka 正常（`/data/spill` 空、broker TCP 通）
- Doris Routine Load：`State=PAUSED`，`loadedRows=0`，`errorRows` 接近 `totalRows`
- ErrorLog：`Reason: no partition for this tuple`，`event_date` 为历史日（例如 `2026-06-30`）

### 根因

1. Load 使用 `OFFSET_BEGINNING`（或新 `group.id` 从头消费）会重放 Kafka **历史消息**。
2. `ods_events` 按 `event_date`（来自 `stime`）动态分区；**刚 DROP/CREATE 表时**，动态分区往往只预建「今天附近」分区，历史日尚不存在。
3. 历史行全部失败 → 超过 `max_error_number` → 任务 **PAUSED**。
4. **任务暂停后，当天有分区的新数据也不会再入库**，直到 `RESUME`。

这与 Gateway / Kafka `Node disconnected` **无关**（Producer 断连噪音 ≠ Doris 消费失败）。

### 处理步骤

```sql
USE tracking;

-- 1) 确认 Load 与分区
SHOW ROUTINE LOAD FOR load_ods_events_test\G
SHOW PARTITIONS FROM ods_events;

-- 2) 动态分区开启时不能直接手工 ADD，先关掉
ALTER TABLE ods_events SET ("dynamic_partition.enable" = "false");

-- 3) 补历史分区（按 error log 里最小 event_date 起，到今天+几天）
-- Doris 2.1+/3.x：
ALTER TABLE ods_events
ADD PARTITIONS FROM ("2026-06-30") TO ("2026-07-14") INTERVAL 1 DAY;

-- 若不支持批量，逐条（注意双引号、左闭右开）：
-- ALTER TABLE ods_events ADD PARTITION p20260630 VALUES [("2026-06-30"), ("2026-07-01"));

-- 4) 重新打开动态分区
ALTER TABLE ods_events SET ("dynamic_partition.enable" = "true");

-- 5) 恢复
RESUME ROUTINE LOAD FOR load_ods_events_test;
SHOW ROUTINE LOAD FOR load_ods_events_test\G
-- 期望 State=RUNNING，loadedRows 上涨
```

验证：

```sql
SELECT event_date, env, event, count(*)
FROM ods_events
WHERE env = 'test'
GROUP BY 1, 2, 3
ORDER BY 1 DESC, 4 DESC
LIMIT 50;
```

### 预防

| 场景 | 建议 |
|------|------|
| 建表后立刻开 Load | 先 `SHOW PARTITIONS`，确认覆盖将要写入的 `event_date` 再 `CREATE ROUTINE LOAD` |
| 只要增量、不要历史 | 新 `group.id` + `property.kafka_default_offsets=OFFSET_END` |
| 需要全量重灌 | `OFFSET_BEGINNING` **之前**先按 topic 最早 `stime` 补齐分区 |
| 隔离区历史坑 | 见上文 quarantine：`event_date`/`stime` 用入库时刻，避免脏时间戳落到无分区日 |

### 相关文件

- 建表：`01_create_tables.sql`（`dynamic_partition.start=-365`）
- 双 topic 同表：`02_routine_load.sql` / `02_routine_load_msk.example.sql`
- 补列：`07_alter_ods_events_keep_raw.sql`
