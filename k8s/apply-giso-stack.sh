#!/usr/bin/env bash
# 构建镜像并 apply GISO K8s 栈（Kind / 任意集群）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ENV_FILE="${GISO_DEPLOY_ENV:-$ROOT/k8s/giso-deploy.env}"
if [ -f "$ENV_FILE" ]; then
  # shellcheck source=/dev/null
  source "$ENV_FILE"
fi

REGISTRY="${GISO_IMAGE_REGISTRY:-giso}"
TAG="${GISO_IMAGE_TAG:-local}"
GATEWAY_IMAGE="${GISO_GATEWAY_IMAGE:-${REGISTRY}/giso-gateway:${TAG}}"
LARK_IMAGE="${GISO_LARK_WEBHOOK_IMAGE:-${REGISTRY}/giso-lark-webhook:${TAG}}"
KAFKA_BOOTSTRAP="${GISO_KAFKA_BOOTSTRAP:-kafka:9092}"

echo "[giso-k8s] build images..."
bash "$ROOT/scripts/build-images.sh"

GATEWAY_IMAGE="${REGISTRY}/giso-gateway:${TAG}"
LARK_IMAGE="${REGISTRY}/giso-lark-webhook:${TAG}"

if command -v kind &>/dev/null && kubectl config current-context 2>/dev/null | grep -q kind; then
  echo "[giso-k8s] load images into kind..."
  kind load docker-image "$GATEWAY_IMAGE" 2>/dev/null || true
  kind load docker-image "$LARK_IMAGE" 2>/dev/null || true
fi

TMP="$(mktemp)"
sed \
  -e "s|__GATEWAY_IMAGE__|${GATEWAY_IMAGE}|g" \
  -e "s|__LARK_WEBHOOK_IMAGE__|${LARK_IMAGE}|g" \
  -e "s|__KAFKA_BOOTSTRAP__|${KAFKA_BOOTSTRAP}|g" \
  "$ROOT/k8s/giso.yaml" > "$TMP"

# 打包 schema 到 ConfigMap
kubectl create namespace giso --dry-run=client -o yaml | kubectl apply -f -
kubectl create configmap giso-schema -n giso \
  --from-file="$ROOT/schema/params.yaml" \
  --from-file="$ROOT/schema/pages.yaml" \
  --from-file="$ROOT/schema/elements.yaml" \
  --from-file="$ROOT/schema/biz_events.yaml" \
  --dry-run=client -o yaml | kubectl apply -f -

if [ -n "${GISO_APP_KEYS:-}" ]; then
  kubectl -n giso create secret generic giso-gateway-secrets \
    --from-literal=GISO_APP_KEYS="$GISO_APP_KEYS" \
    --from-literal=GISO_ADMIN_USER="${GISO_ADMIN_USER:-admin}" \
    --from-literal=GISO_ADMIN_PASSWORD="${GISO_ADMIN_PASSWORD:-change-me}" \
    --from-literal=GISO_VIEWER_USER="${GISO_VIEWER_USER:-viewer}" \
    --from-literal=GISO_VIEWER_PASSWORD="${GISO_VIEWER_PASSWORD:-change-me}" \
    --dry-run=client -o yaml | kubectl apply -f -
fi

kubectl apply -f "$TMP"
rm -f "$TMP"

echo "[giso-k8s] rollout gateway..."
kubectl -n giso rollout status deployment/gateway --timeout=180s
echo "[giso-k8s] done — kubectl -n giso port-forward svc/gateway 8123:8123"
