# GISO · Paimon Lakehouse (GIDO-aligned)

> GIDO deployment 使用 **Flink + Paimon on S3** 作为湖仓；GISO 实时链路仍是 **Kafka → Doris**。
> 本目录提供 **可选** 的 Paimon ODS 表与 Flink SQL，消费 Gateway S3 Bronze 或 Kafka 双写归档。

## 架构

```
Gateway sinks: [kafka, s3]
    ├─ Kafka → Doris Routine Load (实时 BI，主路径)
    └─ S3 bronze JSONL → Flink SQL → Paimon ODS (湖仓 / 重算)
```

与 deployment 仓 `apps/bigdata/gido` 模式一致：warehouse 在 S3，Flink 作业由 ArgoCD wave 部署。

## 前置

| 项 | 说明 |
|---|---|
| S3 bucket | 与 `GISO_S3_BUCKET` / Doppler `INFRA_GISO_S3_BUCKET` 相同 |
| Flink | 1.18+，集群已装 Paimon connector |
| 权限 | Flink SA 对 bucket `s3://…/giso/` 读写 |

## 1. 创建 Paimon 表

在 Flink SQL Client 或 deployment 仓 FlinkDeployment 中执行：

```bash
flink run -d -f server/paimon/01_create_paimon_ods.sql
```

或逐段粘贴 `01_create_paimon_ods.sql`。

## 2. 从 Kafka 同步（推荐，与 Doris 同源）

```bash
# 修改 02_kafka_to_paimon.sql 内 bootstrap / warehouse 后提交
flink run -d -f server/paimon/02_kafka_to_paimon.sql
```

## 3. 从 S3 Bronze 批量回补

Gateway `S3Sink` 路径：`{prefix}raw/dt=YYYY-MM-DD/*.jsonl`

```bash
flink run -d -f server/paimon/03_s3_jsonl_to_paimon.sql
```

## Doppler / deployment 环境变量（对齐 GIDO）

| Doppler (INFRA_*) | GISO Gateway | Flink job |
|---|---|---|
| `INFRA_GISO_S3_BUCKET` | `GISO_S3_BUCKET` | `warehouse` path |
| `INFRA_GISO_S3_PREFIX` | `GISO_S3_PREFIX` | bronze prefix |
| `INFRA_GISO_S3_REGION` | `GISO_S3_REGION` | — |
| `INFRA_AWS_ACCESS_KEY_ID` | `GISO_AWS_ACCESS_KEY_ID` | IRSA 优先 |
| `INFRA_AWS_SECRET_ACCESS_KEY` | `GISO_AWS_SECRET_ACCESS_KEY` | — |

Gateway 双写示例（`deploy/config/gateway-prod.yaml.example`）：

```yaml
sinks: [kafka, s3]
s3:
  bucket: gamelinelab-giso-raw
  prefix: giso/
  region: ap-southeast-1
  buffer_dir: /data/s3-buffer
```

## 与 Doris 的关系

| 场景 | 选型 |
|---|---|
| 实时看板、联调校验 | Doris ODS（主） |
| 历史重算、湖仓探索 | Paimon ODS |
| 合规冷备 | S3 bronze JSONL |

Metrics 字典见 `schema/metrics.yaml`；Paimon 表字段与 `server/doris/01_create_tables.sql` ODS 对齐。
