#!/usr/bin/env bash
# GISO k3s 一键部署（GHCR 镜像 + 集群内 Kafka）
# 用法：bash k8s/deploy-k3s.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MANIFEST="${GISO_K3S_MANIFEST:-$SCRIPT_DIR/k3s.yaml}"

GATEWAY_IMAGE="${GISO_GATEWAY_IMAGE:-ghcr.io/cloud-gido/giso/giso-gateway:latest}"
NODE_PORT="${GISO_NODE_PORT:-30123}"
APP_KEYS="${GISO_APP_KEYS:-demo-key}"
ADMIN_USER="${GISO_ADMIN_USER:-admin}"
ADMIN_PASSWORD="${GISO_ADMIN_PASSWORD:-admin123}"
VIEWER_USER="${GISO_VIEWER_USER:-viewer}"
VIEWER_PASSWORD="${GISO_VIEWER_PASSWORD:-viewer123}"

if ! command -v kubectl &>/dev/null; then
  echo "error: kubectl not found" >&2
  exit 1
fi

if [ ! -f "$MANIFEST" ]; then
  echo "error: manifest not found: $MANIFEST" >&2
  exit 1
fi

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

PULL_SECRETS_BLOCK=""
if [ -n "${GISO_GHCR_TOKEN:-}" ]; then
  kubectl create namespace giso --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  kubectl -n giso create secret docker-registry ghcr \
    --docker-server=ghcr.io \
    --docker-username="${GISO_GHCR_USER:-git}" \
    --docker-password="$GISO_GHCR_TOKEN" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  PULL_SECRETS_BLOCK=$'      imagePullSecrets:\n        - name: ghcr'
fi

sed \
  -e "s|__GATEWAY_IMAGE__|${GATEWAY_IMAGE}|g" \
  -e "s|__NODE_PORT__|${NODE_PORT}|g" \
  -e "s|__APP_KEYS__|${APP_KEYS}|g" \
  -e "s|__ADMIN_USER__|${ADMIN_USER}|g" \
  -e "s|__ADMIN_PASSWORD__|${ADMIN_PASSWORD}|g" \
  -e "s|__VIEWER_USER__|${VIEWER_USER}|g" \
  -e "s|__VIEWER_PASSWORD__|${VIEWER_PASSWORD}|g" \
  -e "s|__IMAGE_PULL_SECRETS__|${PULL_SECRETS_BLOCK}|g" \
  "$MANIFEST" > "$TMP"

echo "[giso-k3s] apply stack..."
kubectl apply -f "$TMP"

echo "[giso-k3s] wait kafka..."
kubectl -n giso rollout status deployment/kafka --timeout=300s

echo "[giso-k3s] wait gateway..."
kubectl -n giso rollout status deployment/gateway --timeout=300s

NODE_IP="$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}' 2>/dev/null || true)"
if [ -z "$NODE_IP" ]; then
  NODE_IP="$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="ExternalIP")].address}' 2>/dev/null || true)"
fi
if [ -z "$NODE_IP" ]; then
  NODE_IP="<k3s-node-ip>"
fi

BASE="http://${NODE_IP}:${NODE_PORT}"
echo ""
echo "[giso-k3s] done"
echo "  health : ${BASE}/health"
echo "  admin  : ${BASE}/admin/  (${ADMIN_USER} / ${ADMIN_PASSWORD})"
echo "  track  : POST ${BASE}/v1/track  (X-App-Key: ${APP_KEYS%%%,*})"
echo ""
echo "  kubectl -n giso get pods,svc"
