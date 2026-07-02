# App Key FAQ

## App Key 是什么？

Header `X-App-Key` 白名单鉴权。无效 Key 返回 **401**，客户端不应重试。

## 长视频命名约定

| Key | 端 | env | Kafka |
|-----|-----|-----|-------|
| video-android-beta | Android 内测 | test | giso_events_raw_test |
| video-android-prod | Android 正式 | prod | giso_events_raw |
| video-ios-beta | iOS TestFlight | test | giso_events_raw_test |
| video-ios-prod | App Store | prod | giso_events_raw |

## 配置位置

- Doppler: `INFRA_GISO_APP_KEYS` → `GISO_APP_KEYS`
- gateway.yaml `auth.app_keys`

## SDK 初始化

```javascript
Tracker.init({
  endpoint: 'https://giso.example.com/v1/track',
  appKey: 'video-android-beta',
  env: 'test',  // 与 Key 类型一致
});
```

## 与空间的关系

App Key 决定 `common.space` 注入；校验使用该空间的 registry。
