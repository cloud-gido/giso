# Flutter 接入 FAQ（接入助手语料）

## Flutter 能用 Android SDK 吗？

**不推荐。** `com.giso:tracker` 的自动曝光/点击/参数继承依赖 Android **View 树**。标准 Flutter UI 只有一个 `FlutterView`，`bind()` 对 Dart Widget **无效**。

## Flutter 官方 SDK 是什么？

**`giso_tracker` Dart 包**（路径 `sdk/flutter/giso_tracker`），与 Android/iOS/Web 共用 `POST /v1/track` 协议。

安装（公开仓库，**ref: v1.0.5**，勿用 v1.0.1）：

```yaml
dependencies:
  giso_tracker:
    git:
      url: https://github.com/cloud-gido/giso.git
      ref: v1.0.5
      path: sdk/flutter/giso_tracker
```

## 注册表要和 Android 分开吗？

**不用。** 长视频共用 `video_feed` / `video_detail` 等登记与 `video-android-beta` App Key（`longvideo` 空间）。详见 `docs/tracking/16-长视频双端Demo与接入对照.md`。

## 必须自己实现什么？

| 能力 | Flutter |
|------|---------|
| 页面 enter/exit | `TrackedPage` 或路由钩子 |
| 元素曝光/点击 | 手动 `elementExposure` / `elementClick` |
| 业务事件 | `bizEvent` |
| 生命周期 | `GisoLifecycleBinding.attach()` |
| 队列/gzip | SDK 内置 |

## platform 填什么？

填 **`android` 或 `ios`**（设备 OS），不要填 `flutter`。

## 联调怎么做？

1. `GisoConfig(debug: true)` 或 `env: test`
2. 管理台 → 实时联调 → 按 `common.did` 过滤
3. 业务事件筛 `biz_event`，看 `biz.code`

## 混合 App（Flutter + 原生页）

原生 Activity 继续用 Android SDK；Flutter 页用 `giso_tracker`。跨栈边界成对 `exitPage` / `enterPage`。

## 完整文档

- `docs/tracking/14-Flutter接入指南.md`
- `docs/tracking/16-长视频双端Demo与接入对照.md`
- 管理台下载：**Flutter 接入清单**（`/admin/templates/flutter-onboarding-checklist.md`）
