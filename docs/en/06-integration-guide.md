# Integration Guide

**Rule of thumb:** register first, use generated constants, let the SDK handle timing, server facts via Kafka.

## Step 0 — Define the question

Write the metric and dimensions (page, position, version) before coding.

## Step 1 — Registry

| Action | Where |
|--------|-------|
| Create draft entries | Admin → Registry or `schema/*.yaml` PR |
| Approve / publish | Admin (space admin+) |
| Codegen constants | CI `tools/codegen/generate.py` |

Naming (snake_case, ≤32 chars):

- Page `pgid`: `video_feed`
- Element `eid`: `play_btn` (global, not per-page)
- Biz event `code`: `video_play_start`

**Visual picker v2:** Admin → Visual Picker → screenshot + boxes → saves element/page drafts.

## Step 2 — Use constants

```typescript
import { Tracker, Pages, Elements } from '@giso/tracker';
Tracker.pageEnter(Pages.VIDEO_FEED);
Tracker.elementClick(Elements.PLAY_BTN, { vid: 'v1' });
```

## Step 3 — App Key & endpoint

| Key pattern | Typical use |
|-------------|-------------|
| `video-android-beta` | Android debug → `env=test` |
| `video-android-prod` | Release → `env=prod` |

Configure gateway URL + App Key in SDK init.

## Step 4 — Debug

Enable `debug: true` in SDK → events appear in Admin **Live Debug** (SSE).

## Step 5 — Assert

Admin **Assertions**: declare ordered event sequence for a `did`.

## Step 6 — Release checklist

- [ ] Registry entries `live`
- [ ] Constants synced in app release
- [ ] Coverage API shows no missing live pages/elements
- [ ] Doris / Metabase dashboards updated

Chinese guide: [06-接入指南.md](../tracking/06-接入指南.md)
