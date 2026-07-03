# giso_tracker

Official GISO tracking SDK for **Flutter** (Android / iOS).

- Page / element / biz / lifecycle events aligned with [docs/tracking/02-上报协议规范.md](../../../docs/tracking/02-上报协议规范.md)
- Registry constants (`Pages`, `Elements`, `Params`, `BizEvents`) generated from `schema/`
- Gzip batch upload, offline queue (SharedPreferences), remote `/v1/config`

> **No `bind()` automation** — Flutter UI is not on the Android View tree. Use `TrackedPage`, manual `elementClick` / `elementExposure`, and `bizEvent`. See [14-Flutter接入指南.md](../../../docs/tracking/14-Flutter接入指南.md).

## Install (git tag, private repo)

```yaml
dependencies:
  giso_tracker:
    git:
      url: https://github.com/cloud-gido/giso.git
      ref: v1.0.4
      path: sdk/flutter/giso_tracker
```

For GitLab mirror, replace `url` with `https://gitlab.com/gamelinelab/data/giso.git` and the same `ref` / `path`.

PAT needs **`read:packages`** or repo read access. Do not embed tokens in the app — use CI / local `~/.netrc` or SSH git URL.

## Quick start

```dart
import 'package:giso_tracker/giso_tracker.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await GisoTracker.instance.init(const GisoConfig(
    appId: 'video-android-beta',
    appKey: 'video-android-beta',
    appVersion: '1.0.0',
    endpoint: 'https://gamelinelab-giso.envir.dev/v1/track',
    debug: kDebugMode,
  ));
  GisoLifecycleBinding.attach();
  runApp(const MyApp());
}
```

```dart
TrackedPage(
  pgid: Pages.videoFeed,
  pgParams: {Params.tabName: 'recommend'},
  child: FeedList(),
)
```

```dart
GisoTracker.instance.elementClick(
  eid: Elements.videoCard,
  pos: index,
  params: {Params.vid: video.id},
);

GisoTracker.instance.bizEvent(
  BizEvents.videoPlayStart,
  {Params.vid: vid},
);
```

## API surface

| Method | Event |
|--------|-------|
| `enterPage` / `exitPage` | `page_enter` / `page_exit` |
| `elementClick` | `element_click` |
| `elementExposure` | `element_exposure` |
| `bizEvent` | `biz_event` |
| lifecycle via `GisoLifecycleBinding` | `app_install`, `app_launch`, `app_foreground`, `app_background` |

## Versioning

Released with Git tag `v*` on the monorepo (same tag as Android `com.giso:tracker` and Web npm package). See [13-SDK分发与版本.md](../../../docs/tracking/13-SDK分发与版本.md).

## License

Apache-2.0 — see [LICENSE](../../../LICENSE).
