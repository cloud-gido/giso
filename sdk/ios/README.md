# GISO Tracker (iOS)

GISO 玑源 iOS SDK — SwiftPM 包，零依赖，三端同口径。

**外部团队**：完整对接清单见 [docs/tracking/13-SDK分发与版本.md](../../docs/tracking/13-SDK分发与版本.md)。

## SwiftPM（按 Git tag）

- 仓库：`https://github.com/cloud-gido/giso.git`
- 版本：tag `v1.0.0` → 依赖规则 `1.0.0`
- Product：`GISOTracker`（monorepo 根目录 `Package.swift`）

## 接入

```swift
import GISOTracker

var config = TrackerConfig(
    appId: "video-ios-beta",
    appVersion: version,
    endpoint: "https://gamelinelab-giso.envir.dev/v1/track",
    debug: true
)
Tracker.initialize(config: config)
```

## 能力

与 Android/Web 对齐：9 标准事件、曝光、继承、攒批、远程配置。

常量见 `Generated.swift`（CI 从 `schema/` 生成）。

## 许可证

Apache License 2.0 · [NOTICE](../../NOTICE) · Maintainer: [troyzhujingbin@163.com](mailto:troyzhujingbin@163.com)
