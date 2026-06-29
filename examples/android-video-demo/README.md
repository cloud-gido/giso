# GISO 长视频 Android 演示 App

面向对外演示的 **长视频场景** 样例应用，完整接入 GISO 埋点 SDK，配合管理台实时联调。

## 演示能力

| 场景 | 页面 pgid | 埋点类型 |
|---|---|---|
| 推荐流 / 追剧 Tab | `video_feed` | `page_enter/exit`、卡片 `element_exposure/click` |
| 长视频播放 | `video_detail` | 点赞/分享/全屏元素 + `video_play_start/heartbeat/end` |
| 剧集合集 | `video_series` | 选集 `episode_item` 曝光/点击 |

底部浮层显示 **设备 did** 和当前 **pgid**，方便在管理台过滤。

## 快速演示（推荐）

### 1. 启动 GISO 网关

```bash
cd deploy && docker compose up -d --build
# 管理台 http://localhost:8123/admin/  (admin / admin123)
```

### 2. 用 Android Studio 打开工程

```
File → Open → examples/android-video-demo
```

首次打开会同步 Gradle；若提示缺少 SDK，按向导安装 Android SDK 34。

### 3. 运行到模拟器

- 默认上报地址：`http://10.0.2.2:8123/v1/track`（模拟器访问宿主机）
- App Key：`demo-key`（与 docker compose 配置一致）
- **debug 模式已开启**：事件实时上报，不攒批

### 4. 管理台联调

1. 打开 http://localhost:8123/admin/
2. 进入 **实时联调**
3. 复制 App 底部浮层的 **did**，粘贴到过滤框
4. 在 App 中：滚动推荐流 → 点视频卡片 → 播放 → 点赞/分享/切集

预期事件序列示例：

```
app_launch → page_enter(video_feed) → element_exposure(video_card)
→ element_click(play_btn) → page_enter(video_detail)
→ biz_event(video_play_start) → biz_event(video_play_heartbeat) ...
```

### 5. 断言 API（可选）

```bash
curl -s -X POST http://localhost:8123/admin/api/assert \
  -u admin:admin123 \
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

## 真机演示

修改 `app/build.gradle` 中的 `TRACK_ENDPOINT` 为电脑局域网 IP：

```gradle
buildConfigField 'String', 'TRACK_ENDPOINT', '"http://192.168.x.x:8123/v1/track"'
```

确保手机与电脑在同一 Wi-Fi，且网关端口已开放。

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

## 样片来源

演示视频使用 Google 公开样片 CDN（需联网）。离线环境可替换 `DemoCatalog` 中的 `streamUrl`。
