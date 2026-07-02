# Deployment (GIDO-aligned)

Same two-repo model as **GIDO** and **DataEase**:

| Repo | Role |
|------|------|
| **giso** (GitHub) | Source, CI → `ghcr.io/cloud-gido/giso/giso-gateway` |
| **deployment** (GitLab) | K8s, Doppler secrets, Ingress, ArgoCD wave `wave-4-giso` |

## Doppler keys (project `gamelinelab`)

| Doppler | Pod env |
|---------|---------|
| `INFRA_KAFKA_BOOTSTRAP_SERVERS` | `GISO_KAFKA_BOOTSTRAP` |
| `INFRA_KAFKA_SASL_USERNAME/PASSWORD` | `GISO_KAFKA_SASL_*` |
| `INFRA_GISO_APP_KEYS` | `GISO_APP_KEYS` |
| `INFRA_GISO_DB_SERVICE_URL` | `GISO_DB_URL` |
| `INFRA_GISO_DB_SERVICE_USER/PASSWORD` | `GISO_DB_USER/PASSWORD` |
| `INFRA_GISO_S3_BUCKET` | `GISO_S3_BUCKET` |
| `INFRA_GISO_S3_PREFIX` | `GISO_S3_PREFIX` (default `giso/`) |
| `INFRA_GISO_S3_REGION` | `GISO_S3_REGION` |
| `INFRA_AWS_ACCESS_KEY_ID` | `GISO_AWS_ACCESS_KEY_ID` (or IRSA) |
| `INFRA_AWS_SECRET_ACCESS_KEY` | `GISO_AWS_SECRET_ACCESS_KEY` |

## Gateway sinks (production)

```yaml
sinks: [kafka, s3]   # kafka = Doris path; s3 = bronze archive for Paimon
kafka:
  bootstrap_servers: ...
  topic_raw: giso_events_raw
  topic_raw_test: giso_events_raw_test
  topic_quarantine: giso_events_quarantine
s3:
  bucket: gamelinelab-giso-raw
  prefix: giso/
  region: ap-southeast-1
  buffer_dir: /data/s3-buffer
```

## GIDO vs GISO data platform

| | GIDO | GISO |
|---|------|------|
| App | backend + frontend | gateway only |
| Lake | Flink + Paimon | Kafka + Doris (+ optional S3/Paimon) |

## Paths in deployment repo

- `apps/bigdata/giso/`
- `environments/dev/br/sync-waves/wave-4-giso.yaml`
- `apps/system/doppler/giso-secret.yaml`

Details: [deploy/DEPLOYMENT.md](../../deploy/DEPLOYMENT.md) · [deploy/PRODUCTION.md](../../deploy/PRODUCTION.md)
