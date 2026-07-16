# Overview

GISO is a self-hosted behavioral analytics platform: **10 standard events**, **registry-driven validation**, and a **pluggable sink pipeline** (Kafka → Doris for realtime; optional S3 → Paimon for lakehouse).

## Architecture

```
SDK / ServerReporter
  → HTTPS POST /v1/track
  → Gateway (stateless, N replicas)
  → sinks: [kafka] or [kafka, s3]
       ├─ Kafka → Doris Routine Load → BI
       └─ S3 bronze JSONL → Flink → Paimon (optional, GIDO-style)
  → PostgreSQL registry (runtime authority)
```

| Component | Role |
|-----------|------|
| **Registry** | Params / pages / elements / biz events; space-scoped multi-tenant |
| **Gateway** | Auth, rate limit, validation (ok/missing/error), admin API, SSE debug |
| **SDKs** | Web (TS), Android, iOS, server Kafka reporter |
| **Doris** | Realtime ODS + DWD/ADS SQL |
| **S3 + Paimon** | Cold archive & lakehouse (aligns with GIDO deployment pattern) |

## Spaces (multi-tenant)

- Spaces isolate registry, SSE, stats, coverage, hourly counters.
- App Keys map to spaces (`space_app_keys` + prefix rules e.g. `video-*` → `longvideo`).
- Header `X-GISO-Space` on admin APIs.

## Validation

- Non-standard events → quarantine.
- Unregistered pgid/eid/params → error or missing.
- Page structure: if `pages.elements` is set, element events must belong to that list.
- Version gate: registry `since` + client `app_vrsn` (SemVer).

## Visual registry v2

- Admin **Visual Picker**: draw boxes on screenshots or import ViewTree JSON.
- Web SDK: `exportViewTreeJson()` from `sdk/web/src/viewtree.ts`.
- Android: `ViewTreeCapture.capture(root, max)`.
- API: `POST /admin/api/registry/visual-draft`.

## Deployment (GIDO-aligned)

Two-repo flow: **giso** (GitHub, GHCR) + **deployment** (GitLab, ArgoCD). See [deployment.md](deployment.md).

Default admin (empty DB): `admin` / `admin123` → change in production via Doppler.
