# Changelog

All notable changes to **GISO · 玑源** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

**License**: Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).  
**Maintainer**: Felix Zhu \<troyzhujingbin@163.com\>

---

## [Unreleased]

---

## [1.0.10] - 2026-07-16

### Fixed

- **应用级心跳口径**：`app_heartbeat` 不再继承 `pgid` / 页面参数；前台每 60s 上报，退后台补最后不足一个周期的 `fg_dur`。
- **远程间隔首跳**：远程 `heartbeat_interval_ms` 返回后立即重排定时器，避免 Demo 本地 15s 配置导致首跳提前。
- **管理台联调**：事件筛选下拉补充 `app_heartbeat`。

---

## [1.0.9] - 2026-07-16

### Added

- **标准事件 `app_heartbeat`**：仅前台默认每 60s 上报；`/v1/config` 支持 `heartbeat_interval_ms`（15s–300s）。用于活跃时长兜底，与 `video_play_heartbeat` 无关。

### Fixed

- **全端 SDK 队列 / 生命周期**：切后台排空发送（Android WakeLock 同步 flush）；冷启动磁盘续传延后到首次 `app_foreground` 之后；回前台先清残留再发 `app_foreground`。

---

## [1.0.8] - 2026-07-16

### Added

- **全端 SDK**：`common.biz_did`（业务设备 ID），由 App `setBizDid` / `clearBizDid` 上报，用于兼容历史无账号体系的业务设备去重；与 SDK 自生成的 `did` 正交。不落 Doris 独立列，查询 `json_extract_string(common_ext,'$.biz_did')`。

---

## [1.0.7] - 2026-07-16

### Added

- **全端 SDK**：`common.sdk_runtime`（`native` | `flutter` | `web`），与 `platform`（OS）正交，便于分析区分 Flutter / 原生 / Web 上报栈。

---

## [1.0.6] - 2026-07-07

### Added

- **全端 SDK**：`common.app_pkg` 应用包名（Android `applicationId` / iOS Bundle ID / Flutter 自动采集；Web 可 `appPkg` 覆盖）。

### Fixed

- **管理台**：注册表编辑上传截图 `uploadScreenshot is not defined`（补 `api.js` 导入）。

---

## [1.0.5] - 2026-07-03

### Added

- **Flutter SDK**（`giso_tracker`）：`common` 自动采集 `os_vrsn` / `dev_brand` / `dev_model` / `screen_res` / `net_type` / `lang`（`device_info_plus`、`connectivity_plus`），与 Android SDK 对齐；`GisoConfig` 非空字段可覆盖。
- **长视频 Flutter Demo**（`examples/flutter-video-demo`）：推荐流 / 播放 / 剧集 / 播放心跳 / 联调面板；`android/` 工程与 `build-apk.sh`。
- **`scripts/bootstrap-android-sdk.sh`**、**`scripts/install-android-cmake.sh`**：国内镜像安装 NDK、CMake 3.22.1、build-tools 35、platform 36。
- **文档**：[16-长视频双端Demo与接入对照](docs/tracking/16-长视频双端Demo与接入对照.md)。

### Changed

- Flutter / SDK 接入文档示例依赖统一为 **`ref: v1.0.5`**（v1.0.4 无自动设备采集；勿用 v1.0.1 `Params` 重复导出）。
- Flutter demo：`android/build.gradle.kts` 为插件子工程配置 buildscript 阿里云镜像（`connectivity_plus` 等）。

### Fixed

- Flutter 真机 `common` 设备字段为空（此前需手填 `GisoConfig`，demo 曾写死 `flutter-demo`）。

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
- Gateway: 10-event protocol, registry validation, Kafka sinks, admin console.
- PostgreSQL registry backend, multi-space tenancy, approval workflow.
- Doris Routine Load SQL, Docker Compose local stack, EKS deployment manifests (deployment repo).
- Apache 2.0 [LICENSE](LICENSE), product docs under `docs/tracking/`.

---

[Unreleased]: https://github.com/cloud-gido/giso/compare/v1.0.10...HEAD
[1.0.10]: https://github.com/cloud-gido/giso/releases/tag/v1.0.10
[1.0.9]: https://github.com/cloud-gido/giso/releases/tag/v1.0.9
[1.0.8]: https://github.com/cloud-gido/giso/releases/tag/v1.0.8
[1.0.7]: https://github.com/cloud-gido/giso/releases/tag/v1.0.7
[1.0.6]: https://github.com/cloud-gido/giso/releases/tag/v1.0.6
[1.0.5]: https://github.com/cloud-gido/giso/releases/tag/v1.0.5
[1.0.3]: https://github.com/cloud-gido/giso/releases/tag/v1.0.3
[1.0.2]: https://github.com/cloud-gido/giso/releases/tag/v1.0.2
[1.0.1]: https://github.com/cloud-gido/giso/releases/tag/v1.0.1
[1.0.0]: https://github.com/cloud-gido/giso/releases/tag/v1.0.0
[0.1.0]: https://github.com/cloud-gido/giso/releases/tag/v0.1
