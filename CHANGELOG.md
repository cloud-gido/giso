# Changelog

All notable changes to **GISO · 玑源** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

**License**: Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).  
**Maintainer**: Felix Zhu \<troyzhujingbin@163.com\>

---

## [Unreleased]

---

## [1.0.5] - 2026-07-03

### Added

- **长视频 Flutter Demo**（`examples/flutter-video-demo`）：与 Android demo 对齐的推荐流 / 播放 / 剧集 / 播放心跳 / 联调面板；含 `android/` 工程与 `build-apk.sh`。
- **`scripts/install-android-ndk.sh`**：国内镜像（googledownloads.cn）安装 NDK，解决 Flutter 构建 `dl.google.com` 不可达。
- **文档**：[16-长视频双端Demo与接入对照](docs/tracking/16-长视频双端Demo与接入对照.md) — Android / Flutter 同一注册表、埋点差异、联调清单。

### Changed

- Flutter / SDK 接入文档示例依赖统一为 **`ref: v1.0.4`**（修复 v1.0.1 `Params` 重复导出）。
- Android demo 版本号 `1.0.4-demo`；README 互链 Flutter 版。

---

## [1.0.4] - 2026-07-03

### Fixed

- **Flutter SDK**: use unnamed `library;` so top-level doc comment passes `dangling_library_doc_comments` under `--fatal-infos`.

---

## [1.0.3] - 2026-07-03

### Fixed

- **Flutter SDK**: resolve `ambiguous_export` — registry keys stay `Params` class; runtime maps renamed to `ParamMap`.
- Remove unnecessary `library` directive for `flutter analyze --fatal-infos`.

---

## [1.0.2] - 2026-07-03

### Fixed

- **Flutter SDK**: registry constants use Dart `lowerCamelCase` (`videoFeed`, `newsRead`) so `flutter analyze --fatal-infos` passes in `sdk-publish` CI.
- **Codegen**: `tools/codegen/generate.py` `dart_const_name()` aligned with Swift style.

---

## [1.0.1] - 2026-07-03

### Added

- **Flutter SDK** (`sdk/flutter/giso_tracker`): `GisoTracker` page/element/biz/lifecycle events, gzip queue, schema codegen.
- **Flutter demo** (`examples/flutter-video-demo`): long-video feed/detail sample.
- **SDK CI**: `publish-flutter` job on `v*` tags (analyze, test, tarball artifact).
- **Admin**: Flutter onboarding checklist download + Copilot `faq-flutter` corpus.
- **Docs**: [14-Flutter接入指南](docs/tracking/14-Flutter接入指南.md).

---

## [1.0.0] - 2026-07-02

First public SDK release and external-app integration baseline.

### Added

- **SDK distribution** ([docs/tracking/13-SDK分发与版本.md](docs/tracking/13-SDK分发与版本.md))
  - Android: `com.giso:tracker:1.0.0` (GitHub Packages Maven)
  - Web: `@cloud-gido/giso-tracker-web@1.0.0` (GitHub Packages npm)
  - iOS: SwiftPM tag `v1.0.0`, product `GISOTracker`
- GitHub Actions workflow `sdk-publish` (tag `v*`).
- Root `Package.swift` for iOS SwiftPM consumers.

### Changed

- Gateway admin: session cache, SSE reconnect on space switch, registry read performance.
- Documentation: spaces/Kafka single-write, play heartbeat FAQ, deployment guides.

### Gateway image

- `ghcr.io/cloud-gido/giso/giso-gateway` — align with tag `v1.0.0` or `latest` from `main`.

---

## [0.1.0] - 2026-06

Initial open-source baseline (pre-SDK package publish).

### Added

- Four-end SDK (Web / Android / iOS / Server reporter).
- Gateway: 9-event protocol, registry validation, Kafka sinks, admin console.
- PostgreSQL registry backend, multi-space tenancy, approval workflow.
- Doris Routine Load SQL, Docker Compose local stack, EKS deployment manifests (deployment repo).
- Apache 2.0 [LICENSE](LICENSE), product docs under `docs/tracking/`.

---

[Unreleased]: https://github.com/cloud-gido/giso/compare/v1.0.3...HEAD
[1.0.3]: https://github.com/cloud-gido/giso/releases/tag/v1.0.3
[1.0.2]: https://github.com/cloud-gido/giso/releases/tag/v1.0.2
[1.0.1]: https://github.com/cloud-gido/giso/releases/tag/v1.0.1
[1.0.0]: https://github.com/cloud-gido/giso/releases/tag/v1.0.0
[0.1.0]: https://github.com/cloud-gido/giso/releases/tag/v0.1
