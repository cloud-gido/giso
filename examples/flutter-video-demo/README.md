# GISO Flutter 长视频演示

与 [android-video-demo](../android-video-demo/) 对齐的 **Flutter** 样例：推荐流 → 详情页 → 播放/点赞埋点。

## 能力

| 场景 | pgid | 事件 |
|------|------|------|
| 推荐流 | `video_feed` | `page_enter/exit`、`element_click` · `video_card` |
| 详情/播放 | `video_detail` | `video_play_start/end`、`element_click` · `like_btn` |

## 运行

```bash
cd examples/flutter-video-demo
flutter pub get
flutter run \
  --dart-define=GISO_ENDPOINT=https://gamelinelab-giso.envir.dev/v1/track \
  --dart-define=GISO_APP_KEY=video-android-beta
```

本地 compose 网关：

```bash
flutter run --dart-define=GISO_ENDPOINT=http://10.0.2.2:8123/v1/track --dart-define=GISO_APP_KEY=demo-key
```

## 联调

1. 管理台 → **实时联调** → 按 `did` 过滤（SDK init 后可在设备日志或后端查 common.did）
2. 滚动列表 → 点卡片 → 进详情 → 播放/点赞
3. 筛 `biz_event` 看 `video_play_start` / `video_play_end`

## 依赖

本地 path 依赖 `sdk/flutter/giso_tracker`；生产 App 改用 Git tag 依赖，见 [sdk/flutter/giso_tracker/README.md](../../sdk/flutter/giso_tracker/README.md)。
