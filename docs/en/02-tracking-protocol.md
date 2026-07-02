# Tracking Protocol v1.0

Unified envelope for Web, Android, iOS, and server-side facts.

## Transport

| Item | Spec |
|------|------|
| Endpoint | `POST /v1/track` |
| Body | JSON array, gzip recommended, ≤50 events or 256KB per batch |
| Auth | Header `X-App-Key` (401 if invalid); query `?k=` for sendBeacon |
| Response | `204` success; `429` retry with backoff; `4xx` no retry (quarantine) |
| Timestamps | Client `ctime`; gateway `stime` (analytics use `stime`) |

## Envelope

```json
{
  "event": "element_click",
  "log_id": "uuid",
  "ctime": 1770000000123,
  "common": {
    "app_id": "myapp",
    "platform": "android",
    "app_vrsn": "1.4.0",
    "did": "device-id",
    "env": "prod"
  },
  "page": { "pgid": "video_feed", "pg_params": {} },
  "element": { "eid": "video_card", "el_params": { "vid": "v1" } }
}
```

## Standard events

`app_install`, `app_launch`, `app_foreground`, `app_background`, `page_enter`, `page_exit`, `element_exposure`, `element_click`, `biz_event`.

## Remote config

`GET /v1/config` returns SDK tunables: batch size, exposure thresholds, `event_sample_rates`, `events_disabled`.

## Environment routing

| `common.env` | Kafka topic (typical) |
|--------------|------------------------|
| `test` | `giso_events_raw_test` |
| `prod` | `giso_events_raw` |

Validation errors go to `giso_events_quarantine` (replay via `server/ops/replay_quarantine.py`).

Full Chinese spec: [02-上报协议规范.md](../tracking/02-上报协议规范.md)
