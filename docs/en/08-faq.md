# FAQ

## Will existing apps break after upgrading the gateway?

No. Same `/v1/track` + `X-App-Key`. New fields (`common.space`, `env`) are additive. Unregistered events still accepted but may land in quarantine if invalid.

## Why is my event in quarantine?

Open Admin Live Debug → expand event → read `_issues`. Fix registry or client payload, then replay with `replay_quarantine.py replay`.

## `video-*` App Key validates against empty registry?

Video keys route to `longvideo` space. Ensure registry is synced (`default` mirror or manual import).

## Does Kafka get duplicate writes per space?

**No.** Each `/v1/track` request resolves **one** space from `X-App-Key`, writes **one** Kafka record with `common.space`. Registry mirroring (`default` → `longvideo`) is **config only**, not event duplication.

## Does play heartbeat use a long connection?

**No.** `video_play_heartbeat` is a normal `biz_event` from an app-side timer (~30s). The SDK sends **short HTTP POSTs** (batched). Admin **SSE** (`/admin/api/stream`) is browser-only for live debug, not used by mobile SDKs.

## test vs prod data mixed?

Set SDK `env: 'test'` for beta keys. Doris stores `env` column; filter in SQL. Physical isolation (separate DB) is not enabled by default.

## How to add S3 / Paimon like GIDO?

```yaml
sinks: [kafka, s3]
s3:
  bucket: your-bucket
  prefix: giso/
```

Deploy Flink jobs from `server/paimon/*.sql`. See [server/paimon/README.md](../../server/paimon/README.md).

## Will HTTPS batch reporting hurt app performance?

**Not if you use the SDK.** Batching (20 events / 15s), gzip, background thread upload, local spill + retry, flush on background. Main thread only enqueues. Avoid `debug: true` in production and raw high-frequency POST from business threads.

## Is the gateway a Spring Boot app?

**No.** `giso-gateway` is a lightweight Java 21 JAR: JDK `HttpServer`, Jackson, no Spring. Scale with **stateless K8s replicas**, not heavier frameworks.

## Can a monolithic gateway crash under high volume?

The gateway is a thin ingest layer: decompress → validate → async Kafka → `204`. Kafka absorbs spikes; Doris stores data. Protections: 1MB body limit, per-IP rate limit (429), Kafka spill/replay. Run ≥2 replicas, enable `rate_limit_rps`, scale Kafka partitions. Rollback image tag: `0.1` or `main-<sha>`.

## English admin UI?

Use the **English / 中文** toggle in the admin sidebar. More strings will migrate to i18n over time.

## Where is deployment config?

This repo: `deploy/`, `k8s/`, `helm/giso/`. Production manifests live in the GitLab **deployment** repo (`apps/bigdata/giso/`), same pattern as GIDO.

Chinese FAQ: [08-接入常见问题FAQ.md](../tracking/08-接入常见问题FAQ.md)
