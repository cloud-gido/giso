# GISO 生产部署指南

> 本地联调栈见 [README.md](README.md)。本文描述 **GitHub 开源 + 生产上线** 路径，镜像构建对齐 [GIDO](https://github.com/cloud-gido/gido) 的 GHCR 发布模式。

## 架构（生产）

```
SDK / ServerReporter
    → HTTPS → Gateway (×N, 无状态)
    → Kafka (events_raw | events_raw_test | events_quarantine)
    → Doris Routine Load → tracking.ods_events
    → Metabase / 自研 BI
```

**不要**把 `deploy/docker-compose.yml` 原样用于生产（含 ClickHouse、event-bridge、demo 密钥）。

| 组件 | 生产方案 |
|---|---|
| Gateway | `ghcr.io/<org>/<repo>/giso-gateway` K8s Deployment |
| Kafka | 托管或 3 节点集群，RF≥3 |
| Doris | 官方 FE/BE 集群 + `server/doris/*.sql` |
| 告警 | Prometheus + Alertmanager + `giso-lark-webhook` |
| 联调 Demo | `--profile demo` 可选，不进生产 |

---

## 1. 构建镜像

### 本地构建

```bash
# 默认 tag: giso/giso-gateway:local
bash scripts/build-images.sh

# 指定仓库与版本
export GISO_IMAGE_REGISTRY=ghcr.io/your-org/giso
export GISO_IMAGE_TAG=1.0.0
bash scripts/build-images.sh

# 构建并 push
export GISO_PUSH=1
bash scripts/build-images.sh
```

### GitHub Actions（推荐）

推送到 `main` / `dev` 后，`.github/workflows/docker-publish.yml` 自动构建并推送：

| 镜像 | 用途 |
|---|---|
| `giso-gateway` | 接入网关 + 管理台 |
| `giso-lark-webhook` | Alertmanager → 飞书 |
| `giso-news-demo` | 资讯联调 Demo（可选） |

标签策略（与 gido 一致）：

- `main` → `latest` + `<branch>-<sha>`
- `dev` → `dev` + `dev-<sha>`

首次 push 后，在 GitHub Packages 将镜像设为 **public**（开源分发）。

### 国内网络

构建时可覆盖基础镜像（参考 gido）：

```bash
export MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21
export JRE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre-alpine
bash scripts/build-images.sh
```

Maven 已内置 `deploy/maven/settings.xml`（阿里云 mirror）。

---

## 2. Kafka Topic

```bash
KAFKA_BOOTSTRAP=broker1:9092,broker2:9092,broker3:9092 \
KAFKA_REPLICATION_FACTOR=3 \
./deploy/scripts/create-kafka-topics.sh
```

| Topic | 分区建议 | 用途 |
|---|---|---|
| `events_raw` | 8 | 生产埋点 |
| `events_raw_test` | 4 | 联调/debug |
| `events_quarantine` | 2 | 校验失败隔离 |

---

## 3. Doris 落地

```bash
mysql -h <doris-fe> -P 9030 -u root < server/doris/01_create_tables.sql
# 编辑 broker 地址后：
mysql -h <doris-fe> -P 9030 -u root < server/doris/02_routine_load.sql
```

已有集群补 test 链路：`./deploy/scripts/apply-doris-test-pipeline.sh`

---

## 4. Gateway 生产配置

复制模板并按环境修改：

```bash
cp deploy/config/gateway-prod.yaml.example /path/to/gateway.yaml
```

**敏感项用环境变量注入**（不写进镜像）：

| 变量 | 说明 |
|---|---|
| `GISO_APP_KEYS` | 上报鉴权，逗号分隔 |
| `GISO_ADMIN_USER` / `GISO_ADMIN_PASSWORD` | 管理台 |
| `GISO_VIEWER_USER` / `GISO_VIEWER_PASSWORD` | 只读账号 |
| `GISO_KAFKA_BOOTSTRAP` | Kafka broker 列表 |
| `GISO_RATE_LIMIT_RPS` | 建议 100 |

健康检查：`GET /health`（K8s probe）· 指标：`GET /metrics`

---

## 5. Kubernetes

```bash
cp k8s/giso-deploy.env.example k8s/giso-deploy.env
# 填写 GISO_IMAGE_REGISTRY、GISO_KAFKA_BOOTSTRAP、密钥

bash k8s/apply-giso-stack.sh
kubectl -n giso port-forward svc/gateway 8123:8123
```

Ingress 域名示例：`t.example.com` → `POST /v1/track`

详见 [k8s/README.md](../k8s/README.md)。

---

## 6. 监控与对账

```bash
# Prometheus 规则
server/ops/prometheus-rules.yml

# 小时对账（网关接收 vs Doris 落地）
GATEWAY=https://t.example.com ADMIN_AUTH=admin:xxx \
DORIS_HOST=... ./server/ops/reconcile.sh
```

K8s 可部署 CronJob 定期执行对账脚本。

---

## 7. 上线 Checklist

- [ ] `GISO_APP_KEYS` 已轮换，禁用 `demo-key`
- [ ] `sinks: [kafka]`，无 file sink
- [ ] `rate_limit_rps ≥ 100`
- [ ] Kafka RF≥3，Doris 集群健康
- [ ] Routine Load 三作业 RUNNING
- [ ] Ingress TLS 已配置
- [ ] Prometheus 告警 → 飞书
- [ ] schema 变更走 Git PR + `generate.py --check`
- [ ] 灰度单业务线，断言 API 通过后再全量

---

## 8. 测试环境一键部署（deployment 仓库）

与 **GIDO** 相同：push 源码 → GHCR 镜像 → 改 deployment 的 `images.newTag` → ArgoCD 同步。

完整步骤见 [**deploy/DEPLOYMENT.md**](DEPLOYMENT.md)。

| 项 | 值 |
|---|---|
| deployment 路径 | `apps/bigdata/giso/` |
| ArgoCD | `wave-4-giso` |
| 域名 | `gamelinelab-giso.envir.dev` |
| 镜像 | `ghcr.io/cloud-gido/giso/giso-gateway:dev` |

---

| | 本地 `docker compose` | 生产 |
|---|---|---|
| 数仓 | ClickHouse + event-bridge | **Doris Routine Load** |
| 密钥 | demo-key / admin123 | Secret / 环境变量 |
| Gateway 副本 | 1 | ≥2 |
| 镜像 | 本地 build | **GHCR** |
| Demo | `--profile demo` | 不部署 |
