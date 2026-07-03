# Flutter 接入指南

> 面向 **Flutter / Dart 跨端 App** 团队。GISO 当前**无官方 Flutter Plugin**；本文说明为何不宜直接使用 Android SDK，以及推荐的 **Dart 直连接入** 方式。  
> 协议与登记规则与 [02-上报协议规范](02-上报协议规范.md)、[06-接入指南](06-接入指南.md) 完全一致。

---

## 一、结论（给架构 / TL）

| 问题 | 答案 |
|------|------|
| 能用 `com.giso:tracker` Android AAR 吗？ | **不推荐**。自动化元素埋点依赖原生 View 树，对标准 Flutter UI **基本无效**。 |
| 官方 Flutter SDK？ | **有** — `giso_tracker` Dart 包（Git tag 依赖）；无 `bind()` 自动化。 |
| 还能用 GISO 吗？ | **可以**。页面 / 业务 / 生命周期事件 + 管理台登记 + 联调校验全链路可用。 |
| 元素曝光 / 点击怎么办？ | Dart 侧**手动上报** `element_exposure` / `element_click`，或用 `VisibilityDetector` 等实现与 Web/Android **同口径**（≥50% 可视 ≥500ms）。 |
| 注册表 / App Key 要单独改吗？ | **不用**。与 Android 共用 `schema/` 与 `video-*` → `longvideo` 空间；见 [16-长视频双端Demo对照](16-长视频双端Demo与接入对照.md)。 |
| 推荐 SDK 版本？ | Git tag **`v1.0.4`**（勿用 v1.0.1，有 `Params` 重复导出编译错误）。 |

---

## 二、为何 Android SDK 不适合 Flutter

GISO Android SDK 的三项差异化能力（源码 `sdk/android/`）均绑定 **Android View**：

| 能力 | 实现 | Flutter 下的问题 |
|------|------|------------------|
| 自动曝光 | `ExposureTracker`：`OnPreDrawListener` + `getGlobalVisibleRect` | Dart Widget 不在 View 树；整屏通常只有一个 `FlutterView` |
| 自动点击 | `bind()` → `setOnTouchListener` | 手势在 Dart/Engine 层消费，原生层拿不到「点了哪张卡片」 |
| 参数继承 | `elementContext()` 沿 `ViewParent` 合并 `ElementMeta` | 无 bind 过的 View 链，继承不成立 |

因此：**在 Flutter 里引入 Android SDK，只能用到手动 API（`enterPage` / `exitPage` / `bizEvent`）和 Application 生命周期自动事件**；为 `bind()` 拖入 AAR **性价比极低**。

> 传输层（攒批、gzip、失败落盘重试）Android SDK 有实现（`EventQueue`），但 Flutter 用 Dart 自建即可；GISO 的价值在 **协议 + 注册表校验 + 联调治理**，不在队列本身。

---

## 三、推荐架构

```
┌─────────────────────────────────────────────────────────┐
│  Flutter App (Dart)                                      │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────┐ │
│  │ GoRouter /  │  │ Visibility   │  │ 业务层          │ │
│  │ 路由钩子     │  │ Detector 等  │  │ bizEvent 手动   │ │
│  └──────┬──────┘  └──────┬───────┘  └────────┬────────┘ │
│         │ enter/exit     │ exposure/click     │          │
│         └────────────────┴────────────────────┘          │
│                          │                              │
│                   GisoTracker (Dart)                    │
│                   · 信封组装 common/page/element/biz     │
│                   · 队列攒批 + gzip + 重试               │
└──────────────────────────┬──────────────────────────────┘
                           │ POST /v1/track
                           │ Header: X-App-Key
                           ▼
                    GISO Gateway（校验 → Kafka / 隔离区）
```

**与 Web SDK 对齐**：Web 端也是 DOM `bind()`，不是 Android View；Flutter 更接近 **「像 Web 一样自己管 UI 事件，只复用 GISO 协议」**。

---

## 四、接入步骤

### 第 1 步：登记（与其它端相同）

管理台 → **注册表配置** → 登记 `pgid` / `eid` / 参数 / 业务事件 → 联调通过后 **发布 live**。  
命名见 [03-命名与登记规范](03-命名与登记规范.md)。**未登记 = 校验 error → 隔离区**。

### 第 2 步：安装 SDK

```yaml
# pubspec.yaml
dependencies:
  giso_tracker:
    git:
      url: https://github.com/cloud-gido/giso.git
      ref: v1.0.4
      path: sdk/flutter/giso_tracker
```

详见 [sdk/flutter/giso_tracker/README.md](../../sdk/flutter/giso_tracker/README.md) 与 [13-SDK分发与版本 §3.5](13-SDK分发与版本.md)。

### 第 3 步：常量

三端 CI 从 `schema/*.yaml` 生成常量；Flutter 团队可：

- 复制 Web 侧 `Pages` / `Elements` / `Params` / `BizEvents` 为 Dart `class` / `const`（或内部脚本从 YAML 生成）；
- **禁止**在业务代码里手写 `"video_feed"` 等魔法字符串。

### 第 4 步：初始化

| 配置项 | 说明 |
|--------|------|
| `endpoint` | 测试：`https://gamelinelab-giso.envir.dev/v1/track` |
| `appId` | 与 Doppler `INFRA_GISO_APP_KEYS` 中某项一致 |
| `X-App-Key` | 同上，HTTP Header |
| `platform` | `"android"` / `"ios"`（按真实 OS，勿写 `flutter`） |
| `env` | 联调包 `test` → Kafka test topic；正式包 `prod` |
| `debug` | `true` 时不攒批，便于管理台 **实时联调** |

`did`：应用内生成 UUID 持久化（`shared_preferences` / Keychain）。  
`session_id`：前后台间隔 >30min 重新生成（与 [02-上报协议规范 §2](02-上报协议规范.md) 一致）。

### 第 5 步：页面事件（必做）

在路由 **可见** 时 `page_enter`，**离开** 时 `page_exit`（含 `pg_stay`）。

**go_router 示例：**

```dart
class TrackedRoute extends GoRoute {
  TrackedRoute({
    required String path,
    required String pgid,
    required Widget Function(BuildContext, GoRouterState) builder,
  }) : super(
          path: path,
          builder: (context, state) {
            return _TrackedPageShell(
              pgid: pgid,
              pgParams: _paramsFromState(state),
              child: builder(context, state),
            );
          },
        );
}

class _TrackedPageShell extends StatefulWidget {
  const _TrackedPageShell({
    required this.pgid,
    required this.pgParams,
    required this.child,
  });
  final String pgid;
  final Map<String, Object?> pgParams;
  final Widget child;

  @override
  State<_TrackedPageShell> createState() => _TrackedPageShellState();
}

class _TrackedPageShellState extends State<_TrackedPageShell> {
  @override
  void initState() {
    super.initState();
    GisoTracker.instance.enterPage(widget.pgid, widget.pgParams);
  }

  @override
  void dispose() {
    GisoTracker.instance.exitPage();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
```

**Navigator 2.0 / 其它路由**：原则相同——**Route 进栈 ≈ enterPage，出栈 ≈ exitPage**。切换页面前若未 `exitPage`，需在 `enterPage` 内先补发上一页 `page_exit`（与 Android/Web SDK 行为一致）。

### 第 6 步：元素曝光 / 点击（手动）

无自动 `bind()`，在 Widget 层显式上报：

```dart
// 点击（用户完成手势瞬间，不是接口返回成功时）
GisoTracker.instance.track({
  'event': 'element_click',
  'element': {
    'eid': Elements.videoCard,
    'mod': Elements.videoCard,
    'pos': index,
    'params': {'vid': video.id},
  },
});

// 曝光（建议用 visibility_detector 包，口径对齐 GISO：ratio≥0.5 持续≥500ms）
GisoTracker.instance.track({
  'event': 'element_exposure',
  'element': {
    'eid': Elements.videoCard,
    'pos': index,
    'params': {'vid': video.id},
    'exp_dur': visibleMs,
    'exp_ratio': maxRatio,
  },
});
```

参数合并（页面 → 父模块 → 自身）需在 **Dart 侧自己实现**一层 helper，逻辑等价于 Android `elementContext()`。

### 第 7 步：业务事件

```dart
GisoTracker.instance.bizEvent(
  BizEvents.videoPlayStart,
  {'vid': vid, 'series_id': seriesId, 'ep_num': ep},
);
```

播放心跳、完播等见 [04-业务埋点设计](04-业务埋点设计.md)。

### 第 8 步：App 生命周期

Dart 侧监听 `WidgetsBindingObserver` / `AppLifecycleListener`：

| 事件 | 何时报 |
|------|--------|
| `app_install` | 首次启动（本地 flag，仅一次） |
| `app_launch` | 冷启动 |
| `app_foreground` | 从后台回到前台 |
| `app_background` | 进后台，带 `fg_dur`，并 **flush** 队列 |

Android SDK 通过 `ActivityLifecycleCallbacks` 自动完成；Flutter **需自行实现**（约 30 行）。

---

## 五、HTTP 上报（Dart 最小实现要点）

与 [02-上报协议规范 §1](02-上报协议规范.md) 对齐：

```dart
Future<void> flush(List<Map<String, dynamic>> batch) async {
  final body = utf8.encode(jsonEncode(batch));
  final gz = gzip.encode(body);

  final req = await HttpClient().postUrl(Uri.parse(endpoint));
  req.headers.set('Content-Type', 'application/json');
  req.headers.set('Content-Encoding', 'gzip');
  req.headers.set('X-App-Key', appKey);
  req.add(gz);
  final resp = await req.close();
  // 204 成功；401/413/4xx 不重试；429/5xx 退避重试；失败落盘 ≤500 条
}
```

触发 flush：**满 20 条 / 15s / 进后台**（阈值可从 `GET /v1/config` 拉取，与原生 SDK 相同）。

---

## 六、事件 JSON 示例

**页面进入：**

```json
{
  "event": "page_enter",
  "log_id": "550e8400-e29b-41d4-a716-446655440000",
  "ctime": 1719980000123,
  "common": {
    "app_id": "video-android-beta",
    "platform": "android",
    "app_vrsn": "2.1.0",
    "did": "device-uuid",
    "env": "test"
  },
  "page": {
    "pgid": "video_feed",
    "pg_params": { "tab_name": "recommend" }
  }
}
```

**业务事件：**

```json
{
  "event": "biz_event",
  "biz": {
    "code": "video_play_end",
    "params": { "vid": "v123", "play_dur": 120000, "play_pos": 118000, "video_dur": 120000 }
  },
  "page": { "pgid": "video_detail", "pg_params": { "vid": "v123" } }
}
```

---

## 七、联调与验收

1. 包内 `env: test`（或 debug 模式），endpoint 指向测试网关  
2. 管理台 → **实时联调**，按 `did` 过滤  
3. 页面事件筛 `page_enter` / `page_exit`，看 `page.pgid`  
4. 业务事件筛 `biz_event`，看 `biz.code`  
5. 用 **用例断言** 声明期望序列，例如：

```json
{
  "did": "your-test-did",
  "expect": [
    { "event": "page_enter", "pgid": "video_feed" },
    { "event": "element_click", "eid": "video_card" },
    { "event": "page_enter", "pgid": "video_detail" },
    { "event": "biz_event", "code": "video_play_start" }
  ]
}
```

---

## 八、不推荐做法

| 做法 | 原因 |
|------|------|
| 为 Flutter 引入 Android SDK 并指望 `bind()` | View 树不可见，自动化失效 |
| 用 PlatformView 包每个卡片 | 性能与维护成本不可接受 |
| 只引 AAR 为了 EventQueue | 队列可 Dart 自建；AAR 增加体积与 Channel 复杂度 |
| 手写未登记 `pgid` / `eid` / `biz.code` | 进隔离区，主仓无数据 |
| 在接口成功回调里报 `element_click` | 口径错误；点击 = 手势完成瞬间 |

---

## 九、混合工程（Flutter + 原生页）

若 App 内**部分页面仍是原生 Activity/Fragment**（如播放器内核）：

- **原生页**：继续用 `com.giso:tracker` + `bind()`  
- **Flutter 页**：按本文 Dart 接入  
- 统一 `did`、同一 `X-App-Key`、同一登记空间  

跨栈跳转时在边界处成对调用 `exitPage`（Flutter）/ `enterPage`（原生），保证 `ref_pgid` 归因连续。

---

## 十、FAQ

### Q1. `platform` 填什么？

填 **`android` 或 `ios`**（设备 OS），不要填 `flutter`。框架信息可放 `common` 扩展字段或版本号备注，协议层无 `flutter` 枚举。

### Q2. 能否只报 `biz_event`，不报页面？

不推荐。网关强依赖 `page.pgid` 做页面级校验与漏斗；至少 **`enterPage` / `exitPage` 必须接**。

### Q3. 曝光口径必须和原生一致吗？

要。分析侧按 [02-上报协议规范 §3](02-上报协议规范.md) 统一定义：**可视 ≥50%、持续 ≥500ms、滚出 80% 后可重计、单页每元素最多 3 次**。Dart 实现请对齐该口径，否则 CTR/曝光 UV 不可比。

### Q4. 以后会有官方 Flutter Plugin 吗？

**已有 `giso_tracker` 包**（Git path @ tag）。元素层仍以 Dart 手动/可见性检测为主，不会提供 Android 式 `bind()` 自动化。

---

## 十一、相关文档

| 文档 | 内容 |
|------|------|
| [06-接入指南](06-接入指南.md) | 登记六步、联调 |
| [02-上报协议规范](02-上报协议规范.md) | 信封、传输、触发口径 |
| [08-接入常见问题FAQ](08-接入常见问题FAQ.md) | App Key、隔离区、SSE |
| [13-SDK分发与版本](13-SDK分发与版本.md) | endpoint、Key、Android/iOS/Web 坐标 |
| [16-长视频双端Demo对照](16-长视频双端Demo与接入对照.md) | Android / Flutter 长视频埋点与注册表对照 |
| [examples/web-news-demo](../../examples/web-news-demo/) | Web 侧手动 enterPage 参考（逻辑类似 Flutter） |

---

## 十二、对接清单（可复制到 Jira）

- [ ] 管理台登记本 App 全部 `pgid` / `eid` / 参数 / 业务事件并 **live**  
- [ ] 申请 `X-App-Key`，确认 `env` test/prod 分流  
- [ ] Dart `GisoTracker`：init / enterPage / exitPage / bizEvent / flush  
- [ ] 路由层接入 page_enter / page_exit  
- [ ] 列表卡片：手动 click + exposure（口径 50%/500ms）  
- [ ] 生命周期：install / launch / foreground / background  
- [ ] 联调：`did` 可见、用例断言通过  
- [ ] **不**引入 Android SDK 的 `bind()` 做 Flutter Widget 自动化  
