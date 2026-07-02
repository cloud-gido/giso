# FAQ

## Will existing apps break after upgrading the gateway?

No. Same `/v1/track` + `X-App-Key`. New fields (`common.space`, `env`) are additive. Unregistered events still accepted but may land in quarantine if invalid.

## Why is my event in quarantine?

Open Admin Live Debug → expand event → read `_issues`. Fix registry or client payload, then replay with `replay_quarantine.py replay`.

## `video-*` App Key validates against empty registry?

Video keys route to `longvideo` space. Ensure registry is synced (`default` mirror or manual import).

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

## English admin UI?

Use the **English / 中文** toggle in the admin sidebar. More strings will migrate to i18n over time.

## Where is deployment config?

This repo: `deploy/`, `k8s/`, `helm/giso/`. Production manifests live in the GitLab **deployment** repo (`apps/bigdata/giso/`), same pattern as GIDO.

Chinese FAQ: [08-接入常见问题FAQ.md](../tracking/08-接入常见问题FAQ.md)
