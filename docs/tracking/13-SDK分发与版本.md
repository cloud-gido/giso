# SDK 分发与版本（外部 App 对接包）

> **读者**：拿不到 GISO 源码、只需集成埋点 SDK 的外部 App 团队（Android / iOS / Web）及负责发包的 GISO 平台同学。  
> **原则**：只发**二进制包 + 配置 + 文档**，不发业务源码；登记与口径规则仍走 [06-接入指南](06-接入指南.md)。

---

## 一、对接前平台方应交付什么（清单）

把下面这张表填完整再交给 App 方，缺一项联调容易卡住。

| # | 交付物 | 示例 | 谁提供 |
|---|--------|------|--------|
| 1 | **SDK 版本号** | `1.0.0` | 平台（Git tag `v1.0.0` 触发 CI） |
| 2 | **Android Maven 坐标** | `com.giso:tracker:1.0.0` | 平台 |
| 3 | **Web npm 包名** | `@cloud-gido/giso-tracker-web@1.0.0` | 平台 |
| 4 | **iOS 引用方式** | SwiftPM：`cloud-gido/giso`，tag `v1.0.0`，产品 `GISOTracker` | 平台 |
| 5 | **私有仓库读权限** | GitHub PAT（`read:packages`）或组织成员身份 | 平台开通 |
| 6 | **上报 endpoint** | `https://gamelinelab-giso.envir.dev/v1/track` | 平台（测试）；生产另给域名 |
| 7 | **远程配置 URL** | 同上域名，`GET /v1/config`（SDK 自动拉） | 平台 |
| 8 | **App Key**（= SDK `appId`） | `video-android-beta` / `video-android-prod` | 平台写入 Doppler 白名单 |
| 9 | **env 约定** | 内测包 `test` → Kafka 测试 topic；正式包 `prod` | 双方对齐 |
| 10 | **业务空间** | `video-*` Key → 空间 `longvideo` | 平台说明 |
| 11 | **常量版本说明** | 本版 SDK 内置哪些 `pgid/eid/biz`（或附变更摘要） | 平台 |
| 12 | **接入文档链接** | 本文 + [06](06-接入指南.md) + [08-FAQ](08-接入常见问题FAQ.md) | 平台 |
| 13 | **长视频专项** | 填 [07-外部视频App接入问卷](07-外部视频App接入问卷.md) | App 产品 |
| 14 | **联调支持** | 管理台 URL（内网）、联系人 | 平台 |

**不要交给 App 方的**：Gateway 源码、Kafka 密码、Doppler 全量密钥、PostgreSQL 连接串。

---

## 二、包坐标与仓库地址（固定）

| 端 | 坐标 / 包名 | 私有仓库 |
|----|-------------|----------|
| Android | `com.giso:tracker:<version>` | `https://maven.pkg.github.com/cloud-gido/giso` |
| Web | `@cloud-gido/giso-tracker-web@<version>` | `https://npm.pkg.github.com` |
| iOS | SwiftPM 根 `Package.swift`，target `GISOTracker` | Git tag `v<version>` on `cloud-gido/giso` |

发布流水线：GitHub Actions [`.github/workflows/sdk-publish.yml`](../../.github/workflows/sdk-publish.yml)  
触发方式：打 tag `v*`（如 `v1.0.0`），或 Actions 手动 `workflow_dispatch` 填版本号。

---

## 三、App 方如何拉私有包

### 3.1 申请权限

1. 向 GISO 平台申请加入 GitHub 组织 **`cloud-gido`**（或获得只读 PAT）。
2. PAT 权限至少：**`read:packages`**（拉 Maven / npm）；不需要 `repo` 读源码。
3. 切勿把 PAT 打进 App 安装包；仅用于 CI / 本机 Gradle / npm。

### 3.2 Android（Gradle）

**`settings.gradle` / 项目 `build.gradle` 增加仓库：**

```gradle
maven {
    url = uri("https://maven.pkg.github.com/cloud-gido/giso")
    credentials {
        username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
        password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
    }
}
```

**`gradle.properties`（本机，勿提交）：**

```properties
gpr.user=你的GitHub用户名
gpr.key=ghp_xxxxxxxx  # read:packages
```

**依赖：**

```gradle
dependencies {
    implementation 'com.giso:tracker:1.0.0'
}
```

### 3.3 Web（npm）

项目根目录 **`.npmrc`**：

```ini
@cloud-gido:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=${GITHUB_TOKEN}
```

```bash
export GITHUB_TOKEN=ghp_xxxxxxxx   # read:packages
npm install @cloud-gido/giso-tracker-web@1.0.0
```

### 3.4 iOS（SwiftPM）

Xcode → **File → Add Package Dependencies**：

- URL：`https://github.com/cloud-gido/giso.git`
- Dependency Rule：**Exact Version** `1.0.0`（对应 tag `v1.0.0`）

或 `Package.swift`：

```swift
.package(url: "https://github.com/cloud-gido/giso.git", exact: "1.0.0")
```

> iOS 当前为**源码 target 按 tag 分发**（仍无需业务方接触 Gateway 等服务端代码）。后续可改为 XCFramework 二进制 target，坐标不变。

---

## 四、SDK 初始化（三端必填项）

### 4.1 字段说明

| SDK 字段 | 含义 | 注意 |
|----------|------|------|
| `appId` | **App Key 名称** | HTTP Header `X-App-Key`；须已在网关白名单 |
| `appVersion` | App 版本号 | 如 `3.2.1` |
| `endpoint` | 上报地址 | 完整路径含 `/v1/track` |
| `debug` | 联调模式 | `true` 时默认 `env=test`、不攒批 |
| `env` | `test` / `prod` | 显式覆盖 debug 推断；决定 Kafka topic |
| `channel` | 渠道（可选） | 应用商店渠道等 |

**测试环境公网 endpoint（当前）：**

```
https://gamelinelab-giso.envir.dev/v1/track
```

### 4.2 Android

```java
// Application.onCreate
Tracker.init(this, TrackerConfig.builder(
        "video-android-beta",           // App Key，与平台下发一致
        BuildConfig.VERSION_NAME,
        "https://gamelinelab-giso.envir.dev/v1/track")
    .debug(BuildConfig.DEBUG)           // DEBUG 包 → env=test
    .env("test")                        // 或 release 用 .env("prod")
    .build());
```

### 4.3 iOS

```swift
import GISOTracker

var config = TrackerConfig(
    appId: "video-ios-beta",
    appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0",
    endpoint: "https://gamelinelab-giso.envir.dev/v1/track",
    debug: true
)
config.env = "test"
Tracker.initialize(config: config)
```

### 4.4 Web

```ts
import { Tracker } from '@cloud-gido/giso-tracker-web';

const tracker = Tracker.init({
  appId: 'video-web-beta',
  appVersion: import.meta.env.VITE_APP_VERSION,
  endpoint: 'https://gamelinelab-giso.envir.dev/v1/track',
  debug: import.meta.env.DEV,
  env: import.meta.env.DEV ? 'test' : 'prod',
});
```

### 4.5 登录态

用户登录 / 登出时同步（勿自造 `session_id`）：

```java
tracker.setUid(uid);
tracker.clearUid();
```

---

## 五、App Key 与 env 对照（长视频示例）

| App Key | 端 | 包类型 | SDK `env` | Kafka Topic |
|---------|-----|--------|-----------|-------------|
| `video-android-beta` | Android | debug / 内测 | `test` | `giso_events_raw_test` |
| `video-android-prod` | Android | release | `prod` | `giso_events_raw` |
| `video-ios-beta` | iOS | TestFlight | `test` | `giso_events_raw_test` |
| `video-ios-prod` | iOS | App Store | `prod` | `giso_events_raw` |

- Key 前缀 `video-*` → 空间 **`longvideo`**，校验该空间注册表。
- 新 Key 由平台写入 Doppler `INFRA_GISO_APP_KEYS` 并滚动 Gateway。

---

## 六、常量与登记

SDK 内的 `Pages` / `Elements` / `Params` / `BizEvents`（Web 为 `generated.ts`）由平台在发版前从 `schema/*.yaml` 生成。

**App 方规则：**

1. **禁止手写** `pgid` / `eid` / 参数 key 字符串。
2. 新业务埋点须先在管理台或 YAML **登记为 `live`**，再等平台发**新 SDK 版本**带新常量。
3. 紧急联调可临时用 `python3 tools/codegen/generate.py` 生成补丁（仅平台内部）；外部团队等正式包。

埋点写法见 [06-接入指南 § 第 4 步](06-接入指南.md)。

---

## 七、联调与验收

| 步骤 | 操作 |
|------|------|
| 1 | SDK `debug: true` 或 `env: test` |
| 2 | 平台开管理台 **实时联调**（内网 `/admin/`，需账号） |
| 3 | 用本机 `did` 过滤，操作功能，看绿/黄/红标记 |
| 4 | 长视频确认 `common.space` 为 `longvideo` |
| 5 | 播放心跳：业务定时器 + `bizEvent(VIDEO_PLAY_HEARTBEAT)`，**不是**长连接 |

常见误解见 [08-FAQ Q21b–Q21g](08-接入常见问题FAQ.md)、[10-空间与多租户](10-空间与多租户.md)。

---

## 八、平台方发版流程

```bash
# 1. 确保 schema 与 generate 一致
python3 tools/codegen/generate.py --check

# 2. 提交并打 tag
git tag v1.0.0
git push origin v1.0.0
# → Actions: sdk-publish → GH Packages (Maven + npm)

# 3. 把「第一节清单」填好发给 App 方
```

手动发版：GitHub → Actions → **sdk-publish** → Run workflow → 输入 `1.0.0`。

**版本策略建议：**

- **PATCH**（`1.0.1`）：常量增补、SDK bugfix，App 可选升级。
- **MINOR**（`1.1.0`）：新 `biz_event`、曝光口径默认值变更，App 应升级并联调。
- **MAJOR**（`2.0.0`）：破坏性 API 变更（极少）。

---

## 九、FAQ（对接小白）

### Q1. `appId` 填什么？和包名一样吗？

填平台下发的 **App Key 字符串**（如 `video-android-prod`），**不是** Android `applicationId`。它同时作为 `X-App-Key` 请求头。

### Q2. 401 / 403 上报失败？

- Key 未加入网关白名单 → 找平台加 Doppler。
- Key 拼写与包类型不匹配（beta Key 打在 release 包上）→ 换 Key 或改 `env`。

### Q3. 事件进隔离区（红）？

未登记 / 类型错 / 元素未 bind 页面 → 管理台看原因；先登记再测。见 [06 § 第 5 步](06-接入指南.md)。

### Q4. 需要 WebSocket 吗？

**不需要。** 上报全是 `POST /v1/track` 短请求；管理台 SSE 仅给浏览器联调用。

### Q5. 能自己 fork 改 SDK 吗？

许可 Apache-2.0 允许修改，但**口径分裂**会导致与数仓不一致；生产建议只用平台发布的版本号。

### Q6. 没有 GitHub 账号怎么办？

由平台提供：**离线 AAR**（Android）、**tgz**（npm）、或 **XCFramework**（iOS）+ 校验 SHA256（流程与坐标版本一致）。默认推荐 GitHub Packages。

---

## 十、相关文档

| 文档 | 用途 |
|------|------|
| [06-接入指南](06-接入指南.md) | 六步埋点开发 |
| [07-外部视频App接入问卷](07-外部视频App接入问卷.md) | 接入前登记 |
| [08-接入常见问题FAQ](08-接入常见问题FAQ.md) | App Key、Kafka、心跳、SSE |
| [02-上报协议规范](02-上报协议规范.md) | 协议字段 |
| [deploy/DEPLOYMENT.md](../../deploy/DEPLOYMENT.md) | 测试环境域名与 Doppler |
| [sdk/android/README.md](../../sdk/android/README.md) | Android API 摘要 |
| [sdk/web/README.md](../../sdk/web/README.md) | Web API 摘要 |
| [sdk/ios/README.md](../../sdk/ios/README.md) | iOS API 摘要 |

---

## 十一、对接邮件 / 飞书模板（复制即用）

```
【GISO SDK 对接包 v1.0.0】

一、SDK
- Android: com.giso:tracker:1.0.0
  仓库: https://maven.pkg.github.com/cloud-gido/giso
- iOS: SwiftPM cloud-gido/giso tag v1.0.0, product GISOTracker
- Web: @cloud-gido/giso-tracker-web@1.0.0

二、环境与密钥
- Endpoint: https://gamelinelab-giso.envir.dev/v1/track
- App Key（Android 内测）: video-android-beta, env=test
- App Key（Android 正式）: video-android-prod, env=prod
- 空间: longvideo

三、权限
- 请提供 GitHub 账号，我们加 cloud-gido 组织只读；或使用附件 PAT 配置说明（read:packages）

四、文档
- 分发说明: docs/tracking/13-SDK分发与版本.md
- 接入步骤: docs/tracking/06-接入指南.md
- FAQ: docs/tracking/08-接入常见问题FAQ.md

五、联调
- 内测包请 debug=true；联调 did 发群，我们开管理台实时联调

联系人: ___
```
