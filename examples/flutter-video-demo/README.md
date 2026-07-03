# GISO 长视频 Flutter 演示 App

与 Android 演示 App 对齐的 **Flutter 长视频埋点样例**：推荐流、真实视频播放、剧集选集、底部联调面板。

## 演示能力

| 场景 | 页面 pgid | 埋点类型 |
|---|---|---|
| 推荐流 / 追剧 Tab | `video_feed` | `page_enter/exit`、卡片 `element_exposure/click` |
| 长视频播放 | `video_detail` | 点赞/分享/选集 + `video_play_start/heartbeat/end` |
| 剧集合集 | `video_series` | 选集 `episode_item` 曝光/点击 |

底部浮层显示 **设备 did**、**pgid**、**网关连通性**，方便管理台实时联调。

## 快速演示（测试环境，默认）

默认连 **EKS 测试网关**，全国网络可用：

| 配置 | 值 |
|------|-----|
| endpoint | `https://gamelinelab-giso.envir.dev/v1/track` |
| App Key | `video-android-beta` |
| env | `test` |
| 管理台 | https://gamelinelab-giso.envir.dev/admin/ |

### 构建 debug APK（真机侧载）

**前置**：本机已安装 [Flutter](https://docs.flutter.dev/get-started/install)。首次构建会自动从 **googledownloads.cn** 下载 NDK（约 870MB，仅需一次）。

```bash
cd examples/flutter-video-demo
./build-apk.sh
# 安装包：build/app/outputs/flutter-apk/app-debug.apk
adb install -r build/app/outputs/flutter-apk/app-debug.apk
```

若仅缺 NDK，可单独执行：

```bash
bash scripts/install-android-ndk.sh
```

**常见失败：NDK not configured** — 访问 `dl.google.com` 超时（SSL handshake）。不要用 Android Studio 在线拉取失败就卡住；用仓库脚本走国内镜像即可。

**常见失败：Gradle / AGP 版本警告** — 工程已对齐 Gradle 8.14 + AGP 8.11.1；仍报错可临时加 `--android-skip-build-dependency-validation`。

**Android 原生版**（ExoPlayer，功能更完整）见 [../android-video-demo/README.md](../android-video-demo/README.md)。

### USB 直连运行

```bash
flutter run \
  --dart-define=GISO_ENDPOINT=https://gamelinelab-giso.envir.dev/v1/track \
  --dart-define=GISO_APP_KEY=video-android-beta
```

### 管理台联调

1. 真机安装 APK，打开 App
2. 底部浮层点 **复制 did**
3. 管理台 → **实时联调** → 粘贴 did 过滤
4. 确认顶栏空间为 **长视频**（`longvideo`）
5. 滚动推荐流 → 点视频 → 播放 → 点赞 / 选集

## 本地 docker compose 联调

```bash
cd deploy && docker compose up -d --build
# 管理台 http://localhost:8123/admin/  (admin / admin123)
```

真机与电脑同一 *Wi‑Fi*，用电脑局域网 IP：

```bash
flutter run \
  --dart-define=GISO_ENDPOINT=http://192.168.x.x:8123/v1/track \
  --dart-define=GISO_APP_KEY=demo-key
```

## SDK 依赖

开发版使用 monorepo 本地 path：

```yaml
giso_tracker:
  path: ../../sdk/flutter/giso_tracker
```

对外团队可改用公开 Git 依赖（`ref: v1.0.4`）：

```yaml
giso_tracker:
  git:
    url: https://github.com/cloud-gido/giso.git
    ref: v1.0.4
    path: sdk/flutter/giso_tracker
```

## 工程结构

```
examples/flutter-video-demo/
  lib/
    main.dart
    model/demo_catalog.dart      # 与 Android 同款样片目录
    tracking/playback_tracker.dart
    pages/feed_page.dart         # video_feed
    pages/detail_page.dart       # video_detail + video_player
    pages/series_page.dart       # video_series
    widgets/debug_panel.dart     # did / 网关探测
```

## 样片来源

Google 公开样片 CDN（需联网）。离线环境可改 `lib/model/demo_catalog.dart` 中的 `streamUrl`。

## 与 Android 演示对比

| 能力 | Android | Flutter |
|------|---------|---------|
| 真实播放 | ExoPlayer | video_player |
| 元素 bind 自动曝光 | SDK bind() | ListView 可见项手动 exposure |
| 播放心跳 | 30s | 30s |
| 联调面板 | ✓ | ✓ |

Android 演示见 [../android-video-demo/README.md](../android-video-demo/README.md)。
