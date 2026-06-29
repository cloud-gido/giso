# GISO Tracker (Android)

GISO 玑源 Android SDK — 零依赖，与 Web/iOS 同协议同口径。

## 接入

```java
Tracker.init(this, TrackerConfig.builder("giso", BuildConfig.VERSION_NAME,
        "https://track.example.com/v1/track")
    .debug(BuildConfig.DEBUG)
    .build());
```

## 能力

- 生命周期自动事件（install/launch/foreground/background）
- 曝光追踪 + 参数继承
- 攒批重试 + SharedPreferences 持久化
- 远程配置下发

常量类 `Pages` / `Elements` / `Params` / `BizEvents` 由 `tools/codegen/generate.py` 生成。
