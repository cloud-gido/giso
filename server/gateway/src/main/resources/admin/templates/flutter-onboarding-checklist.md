# Flutter SDK onboarding checklist — copy to Jira / 飞书 for external App teams.

## 基本信息

- [ ] App 名称：________________
- [ ] 平台：**Flutter**（Android / iOS 壳）
- [ ] 是否混合原生页（播放器 Activity 等）：是 / 否
- [ ] 业务空间（如 longvideo）：________________
- [ ] 联系人：________________

## 平台交付（GISO 平台方填写）

- [ ] SDK 版本：`giso_tracker` @ Git tag `v____`
- [ ] Git 依赖 URL：`https://github.com/cloud-gido/giso.git`（或 GitLab 镜像）
- [ ] path：`sdk/flutter/giso_tracker`
- [ ] endpoint：`https://gamelinelab-giso.envir.dev/v1/track`
- [ ] App Key（test）：________________
- [ ] App Key（prod）：________________
- [ ] env 约定：内测 `test` / 正式 `prod`

## App 方集成（Flutter 专用）

- [ ] `pubspec.yaml` 添加 `giso_tracker` Git 依赖（**不要**引入 Android AAR 做 bind）
- [ ] `GisoTracker.init` + `GisoLifecycleBinding.attach`
- [ ] 路由层：`TrackedPage` 或等价 `enterPage` / `exitPage`
- [ ] 列表卡片：`elementClick` + `elementExposure`（口径 ≥50% 可视 ≥500ms）
- [ ] 播放器：`bizEvent` · `video_play_start/heartbeat/end`
- [ ] 常量：使用生成的 `Pages` / `Elements` / `Params` / `BizEvents`
- [ ] `platform` 填真实 OS（`android` / `ios`），勿填 `flutter`

## 登记与联调

- [ ] 管理台登记全部 `pgid` / `eid` / 参数 / 业务事件 → **live**
- [ ] debug 包 `env: test`，管理台 **实时联调** 可见 did
- [ ] 用例断言通过（page → click → detail → biz 序列）
- [ ] 覆盖率：GET `/admin/api/coverage` 无 live 遗漏

## 明确不做

- [ ] 不承诺 Android SDK `bind()` 在 Flutter Widget 上自动化
- [ ] 不用 PlatformView 包每个卡片做原生曝光

## 参考文档

- [14-Flutter接入指南.md](https://github.com/cloud-gido/giso/blob/main/docs/tracking/14-Flutter接入指南.md)
- [13-SDK分发与版本.md](https://github.com/cloud-gido/giso/blob/main/docs/tracking/13-SDK分发与版本.md)
- 示例：`examples/flutter-video-demo/`
