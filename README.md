# GISO · 玑源

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**GIDO 的数据源头** — Schema 驱动的移动端 + Web 行为分析平台。

事件收敛、参数继承、注册表强校验、Kafka → Doris 可插拔管道。方法论参考腾讯大同；可自托管、可 fork，适合强规范多业务线 App。

## 特性

- **9 个标准事件** — SDK 统一触发时机，业务差异用 `pgid / eid / biz.code + params` 表达
- **四端 SDK** — Web (TS) · Android (Java) · iOS (Swift) · Server (Kafka 事实流)
- **注册表驱动** — `schema/*.yaml` → CI 校验 → 三端常量生成 → 网关按表拦截
- **接入网关** — 鉴权 / 限流 / 三分类校验 / 远程配置 / Prometheus 指标
- **管理控制台** — 实时联调 · 用例断言 · 注册表 CRUD · 质量统计
- **数据落地** — Kafka → Doris Routine Load → DWD/ADS 看板 SQL + Metabase

## 架构

```
schema/ (Git) ──► CI codegen ──► SDK + Gateway 校验
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
              events_raw    events_quarantine    /admin
                    │               │
                    └──────► Doris ODS ──► Metabase
```

## 快速开始

### 本地开发（Docker Compose）

```bash
cd deploy && docker compose up -d --build
# 管理台 http://localhost:8123/admin/  (admin / admin123)
# 上报 Key: demo-key
# 资讯 Demo: docker compose --profile demo up -d news-web-demo
```

### 构建镜像（发布 / 生产）

```bash
# 本地构建
bash scripts/build-images.sh

# 推送到 GHCR（push main 后 CI 自动构建）
export GISO_IMAGE_REGISTRY=ghcr.io/<your-org>/<repo>
export GISO_IMAGE_TAG=1.0.0
export GISO_PUSH=1
bash scripts/build-images.sh
```

镜像：`giso-gateway` · `giso-lark-webhook` · `giso-news-demo`  
完整生产步骤见 [**deploy/PRODUCTION.md**](deploy/PRODUCTION.md) · [k8s/README.md](k8s/README.md)

### 本地开发（裸跑网关）

```bash
pip install pyyaml && python3 tools/codegen/generate.py
cd server/gateway && mvn package -DskipTests
java -jar target/giso-gateway.jar --config gateway.yaml --port 8123
```

## 文档

| 文档 | 说明 |
|---|---|
| [**00-开源产品全景方案**](docs/tracking/00-开源产品全景方案.md) | **架构选型 · 模块清单 · 路线图 · 竞品对比** |
| [01-总体方案](docs/tracking/01-总体方案.md) | 设计思想与演进 |
| [06-接入指南](docs/tracking/06-接入指南.md) | 业务方六步接入 |
| [deploy/README](deploy/README.md) | 本地联调栈 |
| [deploy/PRODUCTION](deploy/PRODUCTION.md) | 生产部署 · 镜像构建 |
| [**deploy/DEPLOYMENT**](deploy/DEPLOYMENT.md) | **测试环境 · deployment 仓 ArgoCD** |
| [k8s/README](k8s/README.md) | Kubernetes 清单 |

## 仓库结构

```
schema/          注册表（参数/页面/元素/业务事件）
sdk/             web · android · ios · server
server/gateway/  接入网关 + 管理控制台
server/doris/    Doris DDL + Routine Load + 分析 SQL
deploy/          Docker Compose 联调栈 · PRODUCTION.md
k8s/             生产 Gateway K8s 清单
scripts/         build-images.sh 镜像构建
tools/codegen/   注册表校验 + 三端代码生成
```

## 许可证

[Apache License 2.0](LICENSE)
