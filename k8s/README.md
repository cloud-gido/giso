# GISO · K8s 部署

## k3s 局域网一键部署（GHCR 镜像）

```bash
bash k8s/deploy-k3s.sh
```

默认拉起 Gateway + 单节点 Kafka，NodePort `30123`。GHCR 镜像为 private 时：

```bash
GISO_GHCR_TOKEN=<GitHub PAT> bash k8s/deploy-k3s.sh
```

---

## 快速开始（Kind 本地验证）

```bash
cp k8s/giso-deploy.env.example k8s/giso-deploy.env
# 编辑 giso-deploy.env：GISO_KAFKA_BOOTSTRAP、密钥等

bash k8s/apply-giso-stack.sh
kubectl -n giso port-forward svc/gateway 8123:8123
curl http://127.0.0.1:8123/health
```

## 镜像构建（本地 / 发布前）

```bash
# 本地 tag
bash scripts/build-images.sh

# 推 GHCR（与 GitHub Actions 命名一致）
export GISO_IMAGE_REGISTRY=ghcr.io/your-org/giso
export GISO_IMAGE_TAG=1.0.0
export GISO_PUSH=1
bash scripts/build-images.sh
```

## 镜像命名（与 gido 对齐）

| 镜像 | GHCR 路径 |
|---|---|
| 接入网关 | `ghcr.io/<owner>/<repo>/giso-gateway` |
| 飞书告警 | `ghcr.io/<owner>/<repo>/giso-lark-webhook` |
| 资讯 Demo | `ghcr.io/<owner>/<repo>/giso-news-demo` |

CI 在 push 到 `main` 时自动构建推送，见 `.github/workflows/docker-publish.yml`。

## 生产清单

1. `deploy/scripts/create-kafka-topics.sh` — 创建 topic（RF≥3）
2. `server/doris/*.sql` — Doris 建表 + Routine Load（改 broker）
3. `k8s/giso.yaml` — Gateway Deployment ×2 + Ingress
4. `server/ops/prometheus-rules.yml` — 告警规则
5. 完整步骤见 `deploy/PRODUCTION.md`

## 与 GIDO 的差异

| | GIDO | GISO |
|---|---|---|
| 核心应用 | backend + frontend | **gateway**（无状态） |
| 数据平台 | Flink + Paimon (+ S3) | **Kafka + Doris** (+ optional **S3 + Paimon**, see `server/paimon/`) |
| 重镜像 | flink-runtime | 无（Doris 用官方镜像） |
