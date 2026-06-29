# GISO 玑源 · 部署指南

## 生产部署

**本地联调**用本文；**生产上线**见 [**PRODUCTION.md**](PRODUCTION.md)（镜像 GHCR、Kafka、Doris、K8s）。

```bash
# 构建镜像（与 gido 同款 GHCR 流程）
bash scripts/build-images.sh

# K8s 部署 Gateway
cp k8s/giso-deploy.env.example k8s/giso-deploy.env
bash k8s/apply-giso-stack.sh
```

## 一键本地栈（Docker Compose）

```bash
cd deploy
cp .env.example .env   # 首次：填入飞书 Webhook URL
docker compose up -d --build

# 验证网关
curl -s http://localhost:8123/metrics | head -5

# 端到端：上报 → Kafka → event-bridge → ClickHouse
./scripts/seed-events.sh
```

| 服务 | 地址 | 默认凭证 |
|---|---|---|
| **产品导航** | http://localhost:8123/ | 无需登录 |
| 管理控制台 | http://localhost:8123/admin/ | admin / admin123 |
| 上报 API | POST http://localhost:8123/v1/track | Header `X-App-Key: demo-key` |
| **Grafana** | http://localhost:3001/ | admin / admin123 |
| **Prometheus** | http://localhost:9090/ | — |
| **Alertmanager** | http://localhost:9093/ | — |
| Kafka | localhost:9092 | topics 自动创建 |
| ClickHouse | HTTP http://localhost:8124/play · 原生 `localhost:9000` | 用户 `default`，本地无密码 |
| **Metabase** | http://localhost:3000/ | `admin@giso.local` / `Giso@Metabase2026`（自动初始化） |

### 端到端数据链路

```
SDK / curl → Gateway :8123/v1/track
    → Kafka (events_raw | events_raw_test | events_quarantine)
    → event-bridge（幂等 + :9100/metrics）→ ClickHouse tracking.ods_events
    → Doris Routine Load（--profile doris）→ tracking.ods_events
    → Metabase 预置看板 / ClickHouse Play
```

- **env 分流**：SDK `debug=true` 默认 `env=test`，否则 `prod`；网关按 env 路由 Kafka topic
- **登记覆盖率**：`GET /admin/api/coverage?env=prod`（内存 + ClickHouse 反算，重启不丢）
- **飞书告警**：Prometheus → Alertmanager → `lark-webhook` → 飞书群（`deploy/.env` 配置 Webhook）

### Metabase 预置看板

`metabase-setup` 容器在 Metabase 就绪后自动：

1. 创建管理员 `admin@giso.local` / `Giso@Metabase2026`
2. 连接 **ClickHouse** → 看板 **GISO 埋点总览**（prod）与 **GISO 埋点总览（test）**
3. 若 Doris FE 可达（`--profile doris`）→ 连接 **GISO Doris** → 看板 **GISO 埋点总览（Doris）** / **（Doris · test）**
4. 推荐入口：**GISO 数据总览**（prod）与 **GISO 数据总览（test）** — 左 ClickHouse、右 Doris

打开 http://localhost:3000/ → 集合 **GISO 埋点分析**

| 看板 | 数据源 | env 过滤 |
|---|---|---|
| GISO 埋点总览 | ClickHouse | prod |
| GISO 埋点总览（test） | ClickHouse | test |
| GISO 埋点总览（Doris） | Doris | prod |
| GISO 埋点总览（Doris · test） | Doris | test |
| **GISO 数据总览** | CH + Doris 左右对照 | prod |
| **GISO 数据总览（test）** | CH + Doris 左右对照 | test |

已有 Metabase 数据卷时，补建/刷新看板：

```bash
./scripts/fix-metabase-dashboard.sh
```

若需完全重建，删除 volume 后重启：`docker volume rm deploy_metabase-data`

### 监控与告警

- **Prometheus** 抓取 gateway `:8123/metrics`、event-bridge `:9100/metrics`
- **Alertmanager** 告警推送飞书（配置 `deploy/.env` 中 `LARK_WEBHOOK_URL`）
- **Grafana** 预置看板 **GISO Gateway 埋点治理**

告警状态：http://localhost:9090/alerts · http://localhost:9093/#/alerts

告警状态：http://localhost:9090/alerts · http://localhost:9093/#/alerts

### Doris 试用（生产对齐，可选）

与 ClickHouse 栈并行存在，**默认不启动**。建议机器 **≥8GB 内存**。

```bash
# 起 Doris（FE+BE）+ 建表 + Routine Load 消费 Kafka（prod + test + quarantine）
docker compose --profile doris up -d --build

# 已有 Doris 卷、仅缺 test 链路时（补 env 列 + events_raw_test Routine Load）
./scripts/apply-doris-test-pipeline.sh

# 验证：上报 → Kafka → Doris（无需 event-bridge）
./scripts/verify-doris.sh
```

| Kafka Topic | Routine Load | env 默认 |
|---|---|---|
| `events_raw` | `load_ods_events` | prod |
| `events_raw_test` | `load_ods_events_test` | test |
| `events_quarantine` | `load_ods_quarantine` | — |

Web/Android 联调：`debug=true` → `env=test` → `events_raw_test`，与 ClickHouse 栈一致。

| 入口 | 地址 | 账号 |
|---|---|---|
| Doris FE Web UI | http://localhost:8030/ | — |
| MySQL 协议（DBeaver / CLI） | `localhost:9030` | `root` / 空密码 |
| 表 | `tracking.ods_events` | Routine Load 自动写入 |

```bash
# 常用 SQL
mysql -h 127.0.0.1 -P 9030 -uroot tracking -e "SHOW ROUTINE LOAD;"
mysql -h 127.0.0.1 -P 9030 -uroot -e "SELECT env, platform, count(*) FROM tracking.ods_events GROUP BY env, platform;"
```

链路对比：

| | ClickHouse 栈 | Doris profile |
|---|---|---|
| 落地 | event-bridge（Python） | **Routine Load**（内置） |
| 与生产 | 本地轻量方案 | **与 `server/doris/` 一致** |

可同时跑两套数仓对比；Kafka 同一份数据会被两边消费（不同 consumer group）。

**macOS 注意**：Doris BE 需要 `vm.max_map_count≥2000000`。Docker Desktop 请分配 **≥8GB 内存**。compose **不再强制 amd64**，Apple Silicon 会用原生 arm64 镜像（比 QEMU 快很多）。

若 BE 长时间 `Alive: false` 且进程显示 `[qemu]`，说明还在用旧的 amd64 缓存镜像。先单独拉 arm64 再重建（网络不稳会自动重试 5 次）：

```bash
./scripts/rebuild-doris-be.sh
```

`unexpected EOF` / `short read` 是镜像层下载中断，直接重跑上述脚本即可。

然后等 BE `Alive: true` 后执行 **`./scripts/run-doris-init.sh`**。

若 BE 仍重启，清空 BE 数据卷后重建（会清掉旧注册状态）：

```bash
docker rm -f qy-doris-be
docker volume rm deploy_doris-be-data
docker compose --profile doris up -d doris-be --force-recreate
```

## 生产部署（Kafka + Doris）

1. **Kafka**：创建 `events_raw`（8 分区）、`events_quarantine`（2 分区），副本 ≥ 3
2. **Gateway**：参考 `server/gateway/gateway.yaml`，`sinks: [kafka]`，配置 `auth` 和 `limits`
3. **Doris**：执行 `server/doris/01_create_tables.sql` + `02_routine_load.sql`（改 broker 地址）
4. **监控**：Prometheus 抓 `/metrics`，加载 `server/ops/prometheus-rules.yml`；Grafana 接 Prometheus
5. **对账**：Cron 每小时跑 `server/ops/reconcile.sh`

## 目录说明

```
deploy/
├── .env / .env.example         # 飞书 Webhook（不入库）
├── docker-compose.yml
├── lark-webhook/               # Alertmanager → 飞书
├── metabase-setup/             # Metabase 自动初始化
├── event-bridge/               # Kafka → ClickHouse（幂等 + metrics）
├── clickhouse/init/
├── alertmanager/
├── prometheus/
├── grafana/
├── scripts/seed-events.sh
└── config/gateway-docker.yaml
```
