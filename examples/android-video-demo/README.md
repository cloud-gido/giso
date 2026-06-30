# GISO 长视频 Android 演示 App

面向对外演示的 **长视频场景** 样例应用，完整接入 GISO 埋点 SDK，配合管理台实时联调。

## 演示能力

| 场景 | 页面 pgid | 埋点类型 |
|---|---|---|
| 推荐流 / 追剧 Tab | `video_feed` | `page_enter/exit`、卡片 `element_exposure/click` |
| 长视频播放 | `video_detail` | 点赞/分享/全屏元素 + `video_play_start/heartbeat/end` |
| 剧集合集 | `video_series` | 选集 `episode_item` 曝光/点击 |

底部浮层显示 **设备 did** 和当前 **pgid**，方便在管理台过滤。

## 快速演示

### 方式 A：测试环境（默认，全国任意网络）

App 默认连 **EKS 测试网关**，无需局域网。已构建 debug APK：

```bash
cd examples/android-video-demo
./gradlew :app:assembleDebug
# 安装包：app/build/outputs/apk/debug/app-debug.apk
```

| 构建 | endpoint | App Key | env | Kafka topic |
|------|----------|---------|-----|-------------|
| debug | `https://gamelinelab-giso.envir.dev/v1/track` | `video-android-beta` | test | `giso_events_raw_test` |
| release | 同上 | `video-android-prod` | prod | `giso_events_raw` |

1. 确认 Doppler 已配置 `INFRA_GISO_APP_KEYS`（含上述 key，见 `deploy/DEPLOYMENT.md`）
2. 真机安装 `app-debug.apk`（或 Android Studio Run debug 包，4G 即可）
3. 底部浮层点 **复制 did** → 管理台 **实时联调** 粘贴过滤
4. 管理台：https://gamelinelab-giso.envir.dev/admin/（办公室 IP / VPN）

### 方式 B：本地 docker compose（开发网关）

```bash
cd deploy && docker compose up -d --build
# 管理台 http://localhost:8123/admin/  (admin / admin123)
```

修改 `app/build.gradle`：

```gradle
buildConfigField 'String', 'TRACK_ENDPOINT', '"http://10.0.2.2:8123/v1/track"'  // 模拟器
// 真机：'"http://192.168.x.x:8123/v1/track"'
buildConfigField 'String', 'APP_KEY', '"demo-key"'
buildConfigField 'String', 'TRACK_ENV', '"test"'
```

手机与电脑须同一 Wi-Fi。

### 管理台联调

1. 打开管理台（见上方链接）
2. 进入 **实时联调**
3. 复制 App 底部浮层的 **did**，粘贴到过滤框
4. 在 App 中：滚动推荐流 → 点视频卡片 → 播放 → 点赞/分享/切集

预期事件序列示例：

```
app_launch → page_enter(video_feed) → element_exposure(video_card)
→ element_click(play_btn) → page_enter(video_detail)
→ biz_event(video_play_start) → biz_event(video_play_heartbeat) ...
```

### 断言 API（可选）

```bash
curl -s -X POST https://gamelinelab-giso.envir.dev/admin/api/assert \
  -u admin:<密码> \
  -H 'Content-Type: application/json' \
  -d '{
    "did": "你的did",
    "expect": [
      {"event": "page_enter", "pgid": "video_feed"},
      {"event": "element_click", "eid": "video_card"},
      {"event": "page_enter", "pgid": "video_detail"},
      {"event": "biz_event", "code": "video_play_start"}
    ]
  }'
```

## App Key 命名（Doppler 白名单）

| Key | 用途 |
|-----|------|
| `video-android-beta` | Android debug / 内测包，`env=test` |
| `video-android-prod` | Android release，`env=prod` |
| `video-ios-beta` | iOS 内测（预留） |
| `video-ios-prod` | iOS 正式（预留） |

Doppler 一行配置：

```
INFRA_GISO_APP_KEYS=video-android-beta,video-android-prod,video-ios-beta,video-ios-prod
```

## 工程结构

```
examples/android-video-demo/
  app/                    演示 App
    FeedActivity            推荐流（video_feed）
    DetailActivity          播放页（video_detail + 播放业务事件）
    SeriesActivity          剧集页（video_series）
    tracking/PlaybackTracker  播放 start/heartbeat/end
  :giso-tracker             引用 ../../sdk/android
```

## 埋点接入要点（演示用）

1. **初始化** — `VideoApp` 中 `Tracker.init()`，`debug=true`
2. **页面** — `BaseTrackedActivity` 统一 `enterPage` / `exitPage`
3. **元素** — `Tracker.bind(view, ElementMeta.of(...))` 使用生成常量
4. **播放** — `PlaybackTracker` 上报 `BizEvents.VIDEO_PLAY_*`
5. **禁止手写字符串** — 全部使用 `Pages` / `Elements` / `Params` / `BizEvents`

## 华为 / 荣耀手机安装说明

本 Demo **只申请联网权限**（`INTERNET`），不读通讯录、定位、相册等敏感权限。

若安装后 **弹窗提示风险** 或 **点图标闪退**，按下面排查：

### 1. 华为安全提示（最常见）

侧载 debug 包会触发 **「风险应用」/「纯净模式」** 提示，不是 App 要了额外权限：

1. 安装时选择 **「继续安装」**（不要只点取消）
2. **设置 → 系统 → 纯净模式** → 关闭，或把本 App 加入信任
3. **设置 → 应用和服务 → 应用管控** → 找到「玑源长视频」→ **允许运行**
4. **设置 → 应用 → 玑源长视频 → 流量使用情况** → 允许 **WLAN 和移动数据**

### 2. 闪退（已修复）

旧版 SDK 在 **Android 12 及以下** 启动时会闪退（`readAllBytes` 兼容性）。请重新安装最新 `app-debug.apk`。

### 3. 仍打不开

用数据线连电脑执行，把崩溃日志发开发：

```bash
adb logcat -d | grep -E "AndroidRuntime|giso.demo"
```

## 样片来源

演示视频使用 Google 公开样片 CDN（需联网）。离线环境可替换 `DemoCatalog` 中的 `streamUrl`。
