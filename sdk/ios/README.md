# GISO Tracker (iOS)

GISO 玑源 iOS SDK — SwiftPM 包，零依赖，三端同口径。

## 接入

```swift
import GISOTracker

var config = TrackerConfig(appId: "giso",
                           appVersion: version,
                           endpoint: "https://track.example.com/v1/track",
                           debug: true)
Tracker.shared.start(config: config)
```

## 能力

与 Android/Web 对齐：9 标准事件、曝光、继承、攒批、远程配置。

常量见 `Generated.swift`（CI 从 `schema/` 生成）。
