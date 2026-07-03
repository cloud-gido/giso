# 长视频双端 Demo 与接入对照（Android / Flutter）

> **读者**：长视频业务 App 团队、联调同学、平台方。  
> **结论**：Android 与 Flutter **共用同一注册表、同一协议、同一 App Key 空间**；差异仅在「元素埋点谁自动、谁手动」。

---

## 一、两个 Demo 是什么关系？

| | Android 原生 | Flutter |
|--|--------------|---------|
| 路径 | `examples/android-video-demo/` | `examples/flutter-video-demo/` |
| 包名 | `com.giso.demo.video` | `com.giso.demo.flutter` |
| SDK | `sdk/android` · `com.giso:tracker` | `sdk/flutter/giso_tracker` @ **`v1.0.4`** |
| 构建 | `./gradlew :app:assembleDebug` | `./build-apk.sh` |
| APK | `app/build/outputs/apk/debug/app-debug.apk` | `build/app/outputs/flutter-apk/app-debug.apk` |

**各打一个独立 App**，不是「一个 App 里两个模块」。场景对齐：推荐流、播放、剧集、联调 did 面板。

默认联调配置（两端相同）：

| 项 | 值 |
|----|-----|
| endpoint | `https://gamelinelab-giso.envir.dev/v1/track` |
| App Key | `video-android-beta` |
| env | `test` |
| 业务空间 | **`longvideo`**（`video-*` Key 自动路由） |
| 管理台 | https://gamelinelab-giso.envir.dev/admin/ |

本地 docker 用 `demo-key` 只会进 `default` 空间，**看不到长视频注册表**；长视频联调请用测试网关 + `video-android-beta`。

---

## 二、注册表要改吗？

**不用。** 两端从同一份 `schema/*.yaml` 生成常量，校验同一套 `longvideo` 登记：

| 类型 | 登记 key | Android 常量 | Flutter 常量 |
|------|----------|--------------|--------------|
| 页面 | `video_feed` | `Pages.VIDEO_FEED` | `Pages.videoFeed` |
| 页面 | `video_detail` | `Pages.VIDEO_DETAIL` | `Pages.videoDetail` |
| 页面 | `video_series` | `Pages.VIDEO_SERIES` | `Pages.videoSeries` |
| 元素 | `video_card` | `Elements.VIDEO_CARD` | `Elements.videoCard` |
| 元素 | `episode_item` | `Elements.EPISODE_ITEM` | `Elements.episodeItem` |
| 业务 | `video_play_start` | `BizEvents.VIDEO_PLAY_START` | `BizEvents.videoPlayStart` |
| 参数 | `vid` / `series_id` / `tab_name` … | `Params.VID` … | `Params.vid` … |

无需为 Flutter 单独登记 pgid/eid，也**不必**新建 `video-flutter-beta` Key（除非统计上要区分端）。

---

## 三、上报 JSON 一样吗？

**信封结构完全一样**（`event` + `common` + `page` + `element` / `biz`），走同一网关校验、进同一 Kafka topic（`env=test` → `giso_events_raw_test`）。

示例（两端等效）：

```json
{
  "event": "page_enter",
  "common": {
    "app_id": "video-android-beta",
    "platform": "android",
    "env": "test",
    "did": "..."
  },
  "page": {
    "pgid": "video_feed",
    "pg_params": { "tab_name": "recommend" }
  }
}
```

管理台 **实时联调** 按 `did` 过滤，确认 `common.space` 为 `longvideo` 即可。

---

## 四、生命周期 / 系统类事件怎么报？

| 事件 | Android | Flutter |
|------|---------|---------|
| `app_install` | SDK `Application` 首次启动 | `GisoTracker.init()` 内 `SharedPreferences` 标记 |
| `app_launch` | SDK 冷启动 | `init()` 完成后自动 |
| `app_foreground` | Activity 生命周期 | `GisoLifecycleBinding.attach()` → `WidgetsBindingObserver` |
| `app_background` | 同上，带 `fg_dur` + flush | 同上 |

Flutter 业务代码**无需手写**上述事件，只需：

```dart
await GisoTracker.instance.init(GisoConfig(..., debug: kDebugMode));
GisoLifecycleBinding.attach();
```

---

## 五、页面 / 元素 / 播放 —— 对照表

| 能力 | Android | Flutter |
|------|---------|---------|
| 页面进出 | `BaseTrackedActivity` · `enterPage`/`exitPage` | `TrackedPage` 或路由钩子里同等调用 |
| 元素曝光/点击 | **`Tracker.bind()` 自动**（口径：可视≥50% 持续≥500ms） | **手动** `elementExposure` / `elementClick` |
| 播放心跳 | 业务 `PlaybackTracker` · 每 30s `bizEvent` | 同左 · `lib/tracking/playback_tracker.dart` |
| 播放 start/end/error | `BizEvents.VIDEO_PLAY_*` | `BizEvents.videoPlayStart` 等 |

### 典型联调事件序列（操作一致时应看到）

```
app_launch
→ page_enter(video_feed)
→ element_exposure(video_card)
→ element_click(video_card)
→ page_enter(video_detail)
→ biz_event(video_play_start)
→ biz_event(video_play_heartbeat)    # 约 30s
→ element_click(like_btn)
→ biz_event(video_play_end)
→ page_enter(video_series)             # 可选：剧集
→ element_click(episode_item)
```

Flutter Demo 列表 UI 较简，Feed 上主要报 `video_card`；详情页才有 `like_btn` / `share_btn`。条数可能与 Android 不完全相同，**事件名与注册表一致即可**。

### 实现差异（不影响注册表）

| 差异点 | 说明 |
|--------|------|
| 曝光时机 | Android SDK 统一口径；Flutter Demo 列表项出现时即报（正式接入建议 `visibility_detector` 对齐 50%/500ms） |
| Feed 卡片子元素 | Android 绑 `play_btn`/`like_btn`/`cp_avatar`/`share_btn`；Flutter Demo 主要 `video_card` |
| `rec_trace_id` | Android 放 element params + pt；Flutter Demo 放 `pt` 透传（推荐） |
| `common.dev_brand` | Flutter Demo 为 `flutter-demo`，便于区分 demo 包 |

---

## 六、业务方最小接入清单

### 1. 依赖

**Android** — `settings.gradle` 引用 `sdk/android` 或 Maven `com.giso:tracker:1.0.x`。

**Flutter** — `pubspec.yaml`（仓库已 public，无需 PAT）：

```yaml
dependencies:
  giso_tracker:
    git:
      url: https://github.com/cloud-gido/giso.git
      ref: v1.0.4
      path: sdk/flutter/giso_tracker
```

> **勿用 v1.0.1**：存在 `Params` 重复导出编译错误。升级后执行 `flutter pub cache clean && flutter pub get`。

### 2. 初始化（长视频测试环境）

```dart
await GisoTracker.instance.init(GisoConfig(
  appId: 'video-android-beta',
  appKey: 'video-android-beta',
  appVersion: packageInfo.version,
  endpoint: 'https://gamelinelab-giso.envir.dev/v1/track',
  debug: kDebugMode,
));
GisoLifecycleBinding.attach();
```

### 3. 页面 / 元素 / 播放

- 页面：`TrackedPage(pgid: Pages.videoFeed, ...)`
- 元素：点击/曝光手动 `elementClick` / `elementExposure`（常量 `Elements.*`、`Params.*`）
- 播放：播放器回调 + 30s 定时器 → `bizEvent(BizEvents.videoPlay*)`

详见 [06-接入指南](06-接入指南.md) · [14-Flutter接入指南](14-Flutter接入指南.md)。

### 4. 联调

1. `debug: true`（或 Android `TRACK_DEBUG=true`）
2. 管理台 → **长视频** 空间 → **实时联调** → 粘贴 App 内 **did**
3. 走一遍：刷 feed → 点视频 → 播放 → 点赞/选集

---

## 七、构建与安装

### Android

```bash
cd examples/android-video-demo
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Flutter

```bash
cd examples/flutter-video-demo
./build-apk.sh    # 首次会自动从 googledownloads.cn 下载 NDK（约 870MB）
adb install -r build/app/outputs/flutter-apk/app-debug.apk
```

仅缺 NDK 时：`bash scripts/install-android-ndk.sh`

---

## 八、相关文档

| 文档 | 说明 |
|------|------|
| [06-接入指南](06-接入指南.md) | 业务六步接入 · 播放心跳说明 |
| [13-SDK分发与版本](13-SDK分发与版本.md) | 坐标 · Key · PAT |
| [14-Flutter接入指南](14-Flutter接入指南.md) | Flutter 架构 · 手动元素 · HTTP 要点 |
| [08-接入常见问题FAQ](08-接入常见问题FAQ.md) | session_id · 隔离区 · SSE 多副本 |
| [examples/android-video-demo/README.md](../../examples/android-video-demo/README.md) | 原生 Demo 详解 |
| [examples/flutter-video-demo/README.md](../../examples/flutter-video-demo/README.md) | Flutter Demo 构建 |
