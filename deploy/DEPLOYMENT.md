# GISO → deployment 测试环境发布流程

与 **GIDO / DataEase** 相同的两仓模式：

| 仓库 | 职责 |
|---|---|
| **giso**（GitHub） | 源码、CI 构建镜像 → GHCR |
| **deployment**（GitLab） | K8s 清单、Doppler、Ingress、ArgoCD |

## 首次上线（一次性）

### 1. GitHub：推送源码，产出镜像

```bash
git push origin main
# Actions → ghcr.io/cloud-gido/giso/giso-gateway:latest
```

在 GitHub Packages 将 `giso-gateway` 设为 **public**（推荐；私有包见下方 GHCR pull secret），或与集群配置 pull secret。

### 2. Doppler（project `gamelinelab`, config `dev`）

| Key | 示例 |
|---|---|
| `INFRA_KAFKA_BOOTSTRAP_SERVERS` | `b-1...amazonaws.com:9096,b-2...,b-3...` |
| `INFRA_KAFKA_SASL_USERNAME` | MSK SCRAM 用户名（→ `GISO_KAFKA_SASL_USERNAME`） |
| `INFRA_KAFKA_SASL_PASSWORD` | MSK SCRAM 密码（→ `GISO_KAFKA_SASL_PASSWORD`） |
| `INFRA_GISO_APP_KEYS` | `video-android-beta,video-android-prod,video-ios-beta,video-ios-prod` |
| `INFRA_GISO_ADMIN_USER` | 可选；不配则用默认 `admin` / `admin123`（空库首次启动写入 PG，登录后改密） |
| `INFRA_GISO_ADMIN_PASSWORD` | 可选 |
| `INFRA_GISO_VIEWER_USER` | 可选 |
| `INFRA_GISO_VIEWER_PASSWORD` | 可选 |
| `INFRA_GISO_DB_SERVICE_URL` | `postgresql://...:5432/giso` | → `GISO_DB_URL` |
| `INFRA_GISO_DB_SERVICE_USER` | `giso-user` | → `GISO_DB_USER` |
| `INFRA_GISO_DB_SERVICE_PASSWORD` | `***` | → `GISO_DB_PASSWORD` |
| `INFRA_GISO_S3_BUCKET` | `gamelinelab-giso-raw` | → `GISO_S3_BUCKET`（湖仓 Bronze，可选） |
| `INFRA_GISO_S3_PREFIX` | `giso/` | → `GISO_S3_PREFIX` |
| `INFRA_GISO_S3_REGION` | `ap-southeast-1` | → `GISO_S3_REGION` |
| `INFRA_AWS_ACCESS_KEY_ID` / `SECRET` | — | → `GISO_AWS_*`（或集群 IRSA） |

Gateway 生产双写：`sinks: [kafka, s3]`，Flink/Paimon 作业见 `server/paimon/`（对齐 GIDO 湖仓）。

**Copilot（阿里云 MaaS LLM）**：

| Key | → Pod |
|-----|-------|
| `INFRA_GISO_LLM_API_KEY` | `GISO_LLM_API_KEY`（MaaS 控制台 API Key） |
| `INFRA_GISO_LLM_BASE_URL` | `GISO_LLM_BASE_URL`（可选，默认北京兼容端点） |
| `INFRA_GISO_LLM_MODEL` | `GISO_LLM_MODEL`（如 `qwen-plus`） |
| `INFRA_GISO_ASSISTANT_PROVIDER` | `GISO_ASSISTANT_PROVIDER`（生产建议 `openai`） |
| `INFRA_GISO_GIDO_COPILOT_URL` | `GISO_GIDO_COPILOT_URL` |

默认 LLM 端点：`https://ws-df7ipa997hhtkd8h.cn-beijing.maas.aliyuncs.com/compatible-mode/v1`。未配 Key 时可保持 `provider=doc`。详见 `docs/tracking/12-Copilot.md`。

**RDS 一次性**：`CREATE DATABASE giso`（控制台或 SQL，与 GIDO/DataEase 相同，deployment 不自动建库）。Gateway 启动自动迁表 + 空库种子导入。详见 `tools/registry/README.md`。

**App Key 命名（长视频等业务接入）：**

| Key | 端 | 包类型 | SDK `env` | Kafka |
|-----|-----|--------|-----------|-------|
| `video-android-beta` | Android | debug/内测 | `test` | `giso_events_raw_test` |
| `video-android-prod` | Android | release | `prod` | `giso_events_raw` |
| `video-ios-beta` | iOS | TestFlight | `test` | `giso_events_raw_test` |
| `video-ios-prod` | iOS | App Store | `prod` | `giso_events_raw` |

### 3. Kafka + Doris

```bash
# Topic
KAFKA_BOOTSTRAP=... ./deploy/scripts/create-kafka-topics.sh

# Doris（测试集群 FE）
mysql -h <doris-fe> -P9030 -uroot < server/doris/01_create_tables.sql
# 改 broker 后
mysql -h <doris-fe> -P9030 -uroot < server/doris/02_routine_load.sql
```

### 4. deployment 仓库

已包含（无需再建）：

- `apps/bigdata/giso/`
- `environments/dev/br/sync-waves/wave-4-giso.yaml`
- `apps/system/doppler/giso-secret.yaml`
- `apps/platform/configs/ingresses/giso-ingress.yaml`

`git push` deployment `main` → ArgoCD 同步。

## 日常发布（改代码后）

```bash
# 1. giso 仓库：push main 触发 CI
git push origin main

# 2. 查看 GHCR 新 tag，例如 main-f3a2b1c（或直接用 latest）

# 3. deployment 仓库：改 tag（需要固定版本时）
# apps/bigdata/giso/kustomization.yaml
#   images.newTag: main-f3a2b1c

git add apps/bigdata/giso/kustomization.yaml
git commit -m "chore(giso): bump gateway to main-f3a2b1c"
git push origin main
```

ArgoCD 自动滚动 `giso-gateway` Deployment。

## 测试环境清单（deployment 仓库）

| 项 | 当前测试配置 |
|----|--------------|
| 副本数 | `replicas: 1`（`apps/bigdata/giso/giso.yaml`） |
| 注册表 | PostgreSQL `giso` 库，`registry.postgres.schema: public` |
| Doppler 映射 | `apps/system/doppler/giso-secret.yaml` |
| 空间路由 | `video-*` → `longvideo`；见 [10-空间与多租户](../docs/tracking/10-空间与多租户.md) |

## Gateway 近期行为变更（需发新镜像）

| 变更 | 说明 |
|------|------|
| 管理台读性能 | 会话缓存 + `registry/meta` 读内存，减轻 PG 模式下只读浏览卡顿 |
| 联调切空间 | 切 `X-GISO-Space` 时自动重连 SSE、重载当前空间事件列表 |
| 联调 UI | 事件行展示 `space:`；工具栏显示当前联调空间 |

## 验证

```bash
curl -s https://gamelinelab-giso.envir.dev/health
# 期望 registry.backend=postgres, entries>0
open https://gamelinelab-giso.envir.dev/admin/   # 内网 IP
```

## 本地一键（不经过 ArgoCD）

```bash
cp k8s/giso-deploy.env.example k8s/giso-deploy.env
bash k8s/apply-giso-stack.sh
```
