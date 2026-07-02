# GISO Tracker (Android)

GISO 玑源 Android SDK — 零依赖，与 Web/iOS 同协议同口径。

**外部团队**：完整对接清单见 [docs/tracking/13-SDK分发与版本.md](../../docs/tracking/13-SDK分发与版本.md)。

## Maven 坐标（GitHub Packages）

| 项 | 值 |
|----|-----|
| groupId | `com.giso` |
| artifactId | `tracker` |
| 仓库 | `https://maven.pkg.github.com/cloud-gido/giso` |

```gradle
implementation 'com.giso:tracker:1.0.0'
```

需 `read:packages` PAT，见分发文档 § 三。

## 接入

```java
Tracker.init(this, TrackerConfig.builder(
        "video-android-beta",  // App Key = X-App-Key
        BuildConfig.VERSION_NAME,
        "https://gamelinelab-giso.envir.dev/v1/track")
    .debug(BuildConfig.DEBUG)
    .build());
```

## 能力

- 生命周期自动事件（install/launch/foreground/background）
- 曝光追踪 + 参数继承
- 攒批重试 + SharedPreferences 持久化
- 远程配置下发

常量类 `Pages` / `Elements` / `Params` / `BizEvents` 由 `tools/codegen/generate.py` 生成。
