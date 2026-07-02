# GISO · 玑源

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**GIDO 的数据源头** — Schema 驱动的移动端 + Web 行为分析平台。

事件收敛、参数继承、注册表强校验、Kafka → Doris 可插拔管道。方法论参考腾讯大同；可自托管、可 fork，适合强规范多业务线 App。

## 特性

- **9 个标准事件** — SDK 统一触发时机，业务差异用 `pgid / eid / biz.code + params` 表达
- **四端 SDK** — Web (TS) · Android (Java) · iOS (Swift) · Server (Kafka 事实流)
- **注册表驱动** — PostgreSQL 运行时权威 + `schema/*.yaml` Git 审计 → CI 生成常量 → 网关强校验
- **接入网关** — 鉴权 / 限流 / 三分类校验 / 远程配置 / 多副本热更新 / Prometheus
- **管理控制台** — 注册表 CRUD（持久化）· 发布/废弃 · 审计 · 实时联调 · 质量统计
- **数据落地** — Kafka → Doris Routine Load → DWD/ADS 看板 SQL + Metabase

## 架构（当前）

```
                    ┌─────────────────────────────────────┐
                    │  PostgreSQL（RDS 库 giso）            │
                    │  注册表运行时权威 · 审计 · 多 Pod 同步   │
                    └──────────────▲──────────────────────┘
                                   │ 读写
  schema/*.yaml ──CI codegen──► SDK  │     ┌── Gateway ×N ──┐
  (Git 审计镜像)                    └────►│ 校验 / 管理台   │
                                          └───┬────────────┘
                                              │ POST /v1/track
                    ┌─────────────────────────┼─────────────────────────┐
                    ▼                         ▼                         ▼
           giso_events_raw          giso_events_raw_test      giso_events_quarantine
              (env=prod)                 (env=test)                 (校验失败)
                    │                         │
                    └────────────► Doris ODS ──► Metabase / BI
```

| 组件 | 说明 |
|------|------|
| **注册表** | 生产：管理台 → PostgreSQL；空库时 Gateway 自动迁表 + 种子导入；定时导出 YAML 供 codegen |
| **鉴权** | `X-App-Key` 白名单（Doppler `INFRA_GISO_APP_KEYS`） |
| **分流** | `env=test` → 测试 Topic；`env=prod` → 生产 Topic |
| **入口** | 公网仅 `/v1/track`、`/v1/config`；管理台内网 |

外部 App 对接常见问题见 [**08-接入常见问题FAQ**](docs/tracking/08-接入常见问题FAQ.md)。English: [**docs/en/**](docs/en/README.md).

## 快速开始

### 本地开发（Docker Compose）

```bash
cd deploy && docker compose up -d --build
# 管理台 http://localhost:8123/admin/  (admin / admin123)
# 上报 Key: demo-key
# 注册表: 内置 PostgreSQL，Gateway 自动迁表 + 种子
```

### 测试 / 生产（EKS + deployment 仓）

1. **RDS**：一次性 `CREATE DATABASE giso`（与 GIDO/DataEase 同实例，库名独立）
2. **Doppler**：`INFRA_GISO_DB_*`、`INFRA_GISO_APP_KEYS`、Kafka、管理台密码
3. **推送 giso `main`** → GHCR 镜像 → **deployment `main`** → ArgoCD

详见 [**deploy/DEPLOYMENT.md**](deploy/DEPLOYMENT.md) · [**deploy/PRODUCTION.md**](deploy/PRODUCTION.md)

### 构建镜像

```bash
bash scripts/build-images.sh
# push main 后 CI: ghcr.io/cloud-gido/giso/giso-gateway:latest
```

### 裸跑网关（开发）

```bash
pip install pyyaml && python3 tools/codegen/generate.py
cd server/gateway && mvn package -DskipTests
java -jar target/giso-gateway.jar --config gateway.yaml --port 8123
```

## 文档

| 文档 | 说明 |
|---|---|
| [**08-接入常见问题FAQ**](docs/tracking/08-接入常见问题FAQ.md) | **App 对接 QA：session_id、登记、App Key、隔离区** |
| [**07-外部视频App接入问卷**](docs/tracking/07-外部视频App接入问卷.md) | 外部 App 登记清单 |
| [06-接入指南](docs/tracking/06-接入指南.md) | 业务方六步接入 |
| [02-上报协议规范](docs/tracking/02-上报协议规范.md) | 信封、session_id、pt 透传 |
| [00-开源产品全景方案](docs/tracking/00-开源产品全景方案.md) | 模块清单 · 路线图 |
| [deploy/DEPLOYMENT](deploy/DEPLOYMENT.md) | 测试环境 · ArgoCD · Doppler |
| [deploy/PRODUCTION](deploy/PRODUCTION.md) | 生产 checklist |
| [tools/registry/README](tools/registry/README.md) | 注册表 DB 脚本 |

## 仓库结构

```
schema/              注册表 Git 镜像（codegen / 种子）
tools/registry/      RDS 建库 · 导入 · 导出脚本
tools/codegen/       三端常量生成
sdk/                 web · android · ios · server
server/gateway/      接入网关 + 管理台（PostgreSQL 注册表）
server/doris/        Doris DDL + Routine Load
deploy/              Docker Compose（含 Postgres）
k8s/                 Gateway K8s 清单
```

## 许可证

[Apache License 2.0](LICENSE)
