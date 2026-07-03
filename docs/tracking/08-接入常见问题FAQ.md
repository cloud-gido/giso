# 接入常见问题 FAQ

> 面向 **外部 App / 业务线客户端** 与 **数据负责人** 的快速查阅。  
> 协议细节见 [02-上报协议规范](02-上报协议规范.md)；接入步骤见 [06-接入指南](06-接入指南.md)；长视频问卷见 [07-外部视频App接入问卷](07-外部视频App接入问卷.md)。

---

## 一、架构与部署

### Q1. GISO 整体链路是什么？

```
三端 SDK ──POST /v1/track──► Gateway（校验 + 鉴权）
                                  │
                    ┌─────────────┼─────────────┐
                    ▼             ▼             ▼
            giso_events_raw   giso_events_raw_test   giso_events_quarantine
            （prod）          （test）                 （校验失败）
                    │             │
                    └─────► Doris Routine Load ──► 看板 / Metabase
```

注册表（页面/元素/参数/业务事件）在 **PostgreSQL** 持久化；Gateway 内存缓存 + 多副本热更新。`schema/*.yaml` 仍用于 CI 代码生成与 Git 审计镜像。

### Q2. 测试环境公网能访问哪些地址？

| 路径 | 公网 | 说明 |
|------|------|------|
| `POST /v1/track` | ✅ | App 上报 |
| `GET /v1/config` | ✅ | SDK 远程配置 |
| `GET /health`、`/ready` | 内网 / VPN | 运维探活 |
| `/`、`/admin/` | 内网 IP 白名单 | 产品导航、管理台 |

测试域名示例：`https://gamelinelab-giso.envir.dev`

### Q3. 注册表存在哪里？管理台保存会丢吗？

| 环境 | 存储 | 管理台保存 |
|------|------|------------|
| **生产 / EKS** | PostgreSQL 库 `giso`（与 GIDO/DataEase **共用 RDS 实例**，库名独立） | ✅ **持久保存**，Pod 重启不丢 |
| **本地 compose** | PostgreSQL 容器 + 可选 YAML 种子 | ✅ 持久 |
| **裸跑 yaml 模式** | `schema/*.yaml` 文件 | 仅开发用 |

运行时权威 = **PostgreSQL**；`schema/*.yaml` = SDK 常量生成 + Git 审计（CronJob 定时导出，或 `tools/registry/export_yaml.py`）。

### Q4. 建 PostgreSQL 库要跑很复杂的脚本吗？

**不需要。** 与 GIDO/DataEase 一样，**RDS 上 `CREATE DATABASE giso` 需人工一次**（控制台或一条 SQL），deployment **不会**自动建库。

建库后 Gateway 启动会自动：

1. 执行表结构迁移（`V1__registry.sql`、`V2__admin_users.sql`）
2. 若库为空，从镜像内 `/app/schema` **种子导入**

可选脚本（在能连 RDS 的机器或 `kubectl run postgres` 临时 Pod 上）：

```bash
# giso 仓库根目录
bash tools/registry/setup.sh --create-db --migrate --import   # 需 RDS 主账号
```

多数团队：**RDS 控制台建库 + Doppler 配 `INFRA_GISO_DB_SERVICE_*`（schema 默认 `public`，与 GIDO 一致）+ 发 Gateway 镜像** 即可。

### Q4b. 启动报 `permission denied for database giso`？

**原因**：之前配置用了独立 schema `giso`，应用账号 `giso-user` 无权 `CREATE SCHEMA`。

**处理（推荐，与 GIDO 一致）**：使用默认 **`public` schema**（库名仍是 `giso`，与 GIDO 库名 `gido` 同理隔离）：

```yaml
# gateway.yaml 或环境变量
registry:
  postgres:
    schema: public
# 或 GISO_DB_SCHEMA=public
```

`public` 在 PostgreSQL 里默认存在，SERVICE 账号可直接 `CREATE TABLE`，**无需 DBA 额外授权**。

若必须用独立 schema `giso`，才需主账号执行 `tools/registry/bootstrap_schema.sql` 一次。

**说明**：`SLF4J: Failed to load class org.slf4j.impl.StaticLoggerBinder` 仅为日志实现缺失警告，不影响启动，可忽略。

### Q5. Gateway 是 Spring Boot 吗？为什么用单体 JAR？

**不是 Spring。** `giso-gateway` 是 **Java 21 单体可执行 JAR**，刻意保持轻量：

| 组件 | 技术 |
|------|------|
| HTTP 服务 | JDK 内置 `HttpServer`（非 Tomcat / Spring MVC） |
| JSON | Jackson |
| 配置 | SnakeYAML + 环境变量 |
| 管理台 | 静态 HTML + ES Module（同进程托管） |

埋点接入是 **高并发、短路径、无状态**（解压 → 校验 → 写 Kafka → `204`），不需要 Spring 那套容器开销。生产通过 **K8s 多副本 + 负载均衡** 水平扩展，而不是拆成很多微服务。

### Q6. HTTPS 接口上报会影响 App 性能吗？

**正常使用 SDK 时影响很小**，因为上报在后台异步完成，不是「一点击就发一次请求」：

| SDK 机制 | 作用 |
|----------|------|
| **攒批** | 默认满 **20 条** 或 **15 秒** 才发一批 |
| **gzip** | 压缩 JSON，减少流量与耗时 |
| **后台线程** | Android `EventQueue` 单线程调度，主线程只入队 |
| **退后台 flush** | 进后台时补发，不阻塞 UI |
| **失败落盘 + 退避重试** | 网络差时本地排队，不堵业务线程 |
| **204 响应** | 无响应体，连接很快结束 |

HTTPS 比 HTTP 多 TLS 握手，但现代系统有连接复用；相对视频播放、业务 API，埋点流量通常占比极低。

**可能变重的误用**（需避免）：

- 业务线程自己高频 `POST /v1/track`，绕过 SDK 攒批
- 生产环境长期开 `debug: true`（联调用，会单条实时上报）
- 单批超大（协议上限：≤50 条 / ≤256KB，解压后 ≤1MB）

### Q7. 数据量很大，Gateway 单体会不会崩？

Gateway 是 **薄接入层**，不做数仓计算，职责是：

```
解压 → 按注册表校验 → 异步写 Kafka → 立刻 204
```

**洪峰由 Kafka 扛，存储由 Doris/ClickHouse 扛**，不应在 Gateway 进程里堆数据。

已有防护（`gateway.yaml` → `limits`）：

| 机制 | 说明 |
|------|------|
| `max_body_bytes` | 单请求解压后 ≤ **1MB**，超出 `413` |
| `rate_limit_rps` / `rate_limit_burst` | 按客户端 IP **令牌桶限流**，超出 `429`（生产示例 100 RPS） |
| Kafka **异步 producer** | `linger.ms` + lz4 压缩 + `acks=all` |
| **spill 兜底** | Broker 不可用时落本地文件，恢复后自动回放 |
| **隔离区** | 校验失败进 `giso_events_quarantine`，不污染主 ODS |

单机单实例有上限（极高 QPS、未开限流、注册表极大时 CPU 会吃紧）。应对方式：

1. **Helm / K8s 多副本**（建议 ≥2）+ Ingress 负载均衡  
2. 生产务必配置 **`rate_limit_rps`**  
3. Kafka **加分区**，Gateway 只负责快进快出  
4. 监控 QPS、429 率、`giso_kafka_spilled_total`（持续增长 = Kafka 故障）  
5. 部署回滚：镜像标签 `ghcr.io/.../giso-gateway:0.1` 或 `main-<sha>`

### Q8. 和「Spring 微服务」比，这套架构的取舍？

| | GISO 单体 Gateway | Spring 微服务 |
|--|-------------------|---------------|
| 内存 / 启动 | 更轻 | 更重 |
| 接入场景匹配度 | 高（校验 + 转发） | 功能过剩 |
| 扩展方式 | 水平扩副本（无状态） | 拆服务 + 扩副本 |
| 适用阶段 | 创业 ~ 中等流量 | 超大规模可演进专用 ingest 集群 |

**结论**：不是「没用 Spring 就容易崩」，而是 **流量上来时扩 Gateway 副本 + 限流 + Kafka 扩容**。协议与 SDK 设计（9 事件收敛、攒批）从源头控制了数据量。

---

## 二、App Key 与鉴权

### Q5. App Key 是什么？怎么申请？

- Header：`X-App-Key: video-android-beta`（sendBeacon 等无法带 Header 时用 `?k=`）
- 配置在 **Doppler** `INFRA_GISO_APP_KEYS`（逗号分隔）→ K8s `GISO_APP_KEYS`
- **不要**写进 ConfigMap 或代码仓库

命名建议：

| App Key | 用途 | SDK `env` |
|---------|------|-----------|
| `video-android-beta` | Android 内测包 | `test` |
| `video-android-prod` | Android 正式包 | `prod` |
| `video-ios-beta` / `video-ios-prod` | iOS 同理 | `test` / `prod` |

### Q6. 管理台怎么登录？有哪些角色？

与 **GIDO** 类似分三层：**Ingress 内网白名单** + **登录页** +（App 侧独立）**X-App-Key**。

| 角色 | 能做什么 |
|------|----------|
| **admin** | 批准/驳回、改注册表、发布/废弃、账号管理、清缓冲 |
| **editor** | 提交登记（**待审批**），不可直接上线 |
| **viewer** | 只看联调/统计/注册表、跑断言 |

**默认管理员**（PostgreSQL 空库、Doppler 未配 `GISO_ADMIN_*` 时自动创建一次）：`admin` / `admin123`。登录后在 **账号管理** 改密并创建 editor/viewer，**不必**先配 Doppler。

Doppler 可选：`INFRA_GISO_ADMIN_USER` / `PASSWORD`、`INFRA_GISO_VIEWER_*`、`INFRA_GISO_ADMIN_USERS`。详见 [09-账号与权限体系](09-账号与权限体系.md)。

访问 `/admin/` 打开登录页；左下角可退出。

### Q7. `env=test` 和 `env=prod` 有什么区别？

| SDK `env` | Kafka Topic | 用途 |
|-----------|-------------|------|
| `test`（或 debug 模式） | `giso_events_raw_test` | 联调、内测包 |
| `prod`（缺省） | `giso_events_raw` | 正式上线 |

网关按事件 `common.env`（或 SDK 配置）路由，**测试流量不进生产 ODS**。

---

## 三、注册表与登记

### Q8. 必须先登记才能上报吗？

**是。** 未登记的 `pgid` / `eid` / `biz.code` / 参数 key：

- HTTP 仍可能返回 `204`（已接收）
- 校验结果为 **error** → 事件进 **`giso_events_quarantine` 隔离区**，**不进主 Doris 表**

管理台实时联调里显示 **红色「错误」**。

### Q9. 登记有哪些入口？

| 方式 | 适用 |
|------|------|
| **管理台 → 注册表配置** | 编辑员提交 **待审批**；管理员批准后 **live 生效** |
| **管理台 → 待审批** | 管理员批量批准 / 驳回 |
| **管理台 → 发布** | 管理员将 draft/testing → live（跳过审批的运维路径） |
| **`schema/*.yaml` PR** | SDK 常量生成、正式评审与 Git 审计 |
| **问卷 [07](07-外部视频App接入问卷.md)** | 外部 App 接入前由产品填写 |

推荐流程：**编辑员登记(pending) → 管理员批准(live) → 联调 → 导出 YAML 合 PR**。

### Q10. 待审批和 draft 有什么区别？

| 状态 | 含义 | 谁产生 |
|------|------|--------|
| **pending** | 已提交、等管理员批准 | 编辑员保存登记 |
| **draft** | 登记中 / 被驳回退回 | 管理员驳回或管理员直接建 |

两者都不参与线上校验；**只有 `live`（及 `testing`）会校验 App 上报**。

### Q11. 新增页面要登记哪些表？

顺序建议：

1. **参数池**（页面用到的 `vid` 等先在 `params` 登记）
2. **元素池**（按钮、卡片等 `eid`）
3. **页面池**（`pgid` + 可选 `elements` 结构体绑定）
4. **业务事件**（仅曝光/点击表达不了的，如 `video_play_start`）

### Q12. 元素为什么要全局唯一？能和页面绑在一起命名吗？

- ✅ `video_card` + 不同 `pgid` 区分页面
- ❌ `feed_video_card`、`detail_video_card`（把页面编进元素名）

页面可选声明 `elements: [...]`，声明后 **该页出现未绑定元素会判 error**。

---

## 四、session_id、uid、did

### Q13. `session_id` 谁生成？规则是什么？

**SDK 自动生成并维护**，业务方 **不要** 自己造 session_id。

| 平台 | 行为 |
|------|------|
| Android / iOS | 进前台时检查：距上次活跃 **> 30 分钟** → 生成新 `session_id`（`s-{uuid}`） |
| Web | `sessionStorage` 记录 id + 时间戳，同样 **30 分钟** 间隔重开 |

用于会话级 PV/停留分析，与登录无关（未登录也有 session）。

### Q14. `uid` 和 `session_id` 有什么区别？

| 字段 | 含义 | 谁设置 |
|------|------|--------|
| `did` | 设备标识，卸载前持久 | SDK |
| `session_id` | 一次使用会话（30min 规则） | SDK |
| `uid` | 登录用户 ID | 业务调用 `setUid()` / `clearUid()` |

登录只影响 `uid`，**不会**单独重置 session（除非同时满足 30min 规则）。

### Q15. `rec_trace_id` 和 `session_id` 是一回事吗？

**不是。**

| 字段 | 位置 | 用途 |
|------|------|------|
| `session_id` | `common` | 客户端会话，SDK 管 |
| `rec_trace_id` | 多在 `pt` 透传包或业务参数 | **推荐/广告一次请求**的追踪 ID，由**推荐服务下发**，用于效果归因 |

`pt` 包内容 **不登记、不校验**，原样落库 JSON 列。

---

## 五、生命周期与标准事件

### Q16. 哪些事件 SDK 自动报？业务还要写吗？

| 事件 | 谁报 |
|------|------|
| `app_install` / `app_launch` / `app_foreground` / `app_background` | **SDK 自动** |
| `page_enter` / `page_exit` | 业务在页面生命周期调 `enterPage` / `exitPage` |
| `element_exposure` / `element_click` | `bind` 后 SDK 自动（勿自写滚动监听判曝光） |
| `biz_event` | 业务调 `bizEvent()` |

### Q17. 登录要怎么埋？

```java
Tracker.setUid("10086");   // 登录成功
Tracker.clearUid();        // 登出
```

**不要**为登录单独造 `biz_event`，除非已在事件字典登记且有明确看数需求。

### Q18. 只有 9 个标准事件名吗？

是。业务差异用 **`pgid` / `eid` / `biz.code` + params** 表达，禁止自造 `xxx_click` 事件名。

---

## 六、联调与校验

### Q19. 管理台颜色各代表什么？

| 标记 | 校验结果 | 数据去向 |
|------|----------|----------|
| 绿 | ok | `giso_events_raw` / `_test` |
| 黄 | missing（必携参数空） | raw + `_quality=missing` |
| 红 | error（未登记、类型错、元素未绑定页面等） | **quarantine 隔离区** |

### Q20. 联调怎么只看自己设备？

1. SDK `debug: true`（实时上报、走 test topic）
2. 管理台 → **实时联调** → 按 `did` 过滤
3. 可选：`POST /admin/api/assert` 固化期望事件序列

### Q21. 长视频 Demo 对接测试环境怎么配？

```kotlin
TrackerConfig.builder("video-android-beta", version, endpoint)
    .env("test")   // 或 debug=true
    .build()
// endpoint: https://gamelinelab-giso.envir.dev/v1/track
```

复用登记：`video_feed`、`video_detail`、`video_series` 及 Demo 中已有元素/事件常量（见 `sdk/android/.../Pages.java` 等）。

### Q21b. 多空间会往 Kafka 写两份吗？

**不会。** 每条上报只解析 **一个** 空间（`X-App-Key` → `space_key`），Kafka **只写一条**；空间标识在 `common.space`。Topic 不按空间拆分，查数时用 `space_key` 过滤。

### Q21c. 联调时为什么 default 和长视频空间都有数据？

常见原因（**不是双写**）：

| 现象 | 原因 |
|------|------|
| 两个空间**注册表**很像 | `default` → `longvideo` **注册表镜像**（配置同步） |
| 两个空间**联调**都有事件 | 用了不同的 App Key（`video-*` vs 其他），或切换空间前旧 SSE 未刷新（新版本已修复：切空间重连 SSE） |
| 一条事件有两个 space | **不应出现**；展开 JSON 查 `common.space`，若不对则查 App Key |

长视频连调请统一使用 `video-android-beta` / `video-android-prod` 等 **`video-*` Key**。

### Q21d. 管理台「实时联调」的 SSE 是什么？和 App 上报一样吗？

**不一样。**

| | App 上报 | 管理台 SSE |
|--|----------|------------|
| 协议 | `POST /v1/track` 短连接 HTTP | `GET /admin/api/stream` 长连接 |
| 方向 | 客户端 → 服务端 | 服务端 → 浏览器 |
| 用途 | 埋点入库 | 联调页实时看校验结果 |

App **不需要**对 Gateway 建 WebSocket/SSE。

### Q21e. 切换管理台空间后，联调事件要不要手动刷新？

**不需要**（Gateway 新版本起）：切空间或再次进入「实时联调」会自动 **断开旧 SSE → 拉当前空间缓冲 → 重连 SSE**。工具栏显示当前联调空间名；每条事件展示 **space:** 标签。

### Q21f. 播放心跳（`video_play_heartbeat`）要建立长连接吗？

**不要。** 心跳是业务事件，由 **App 播放器层**实现：

- 登记：`schema/biz_events.yaml` → `video_play_heartbeat`（约每 **30s** + 暂停/退出补报）
- SDK：调 `bizEvent(BizEvents.VIDEO_PLAY_HEARTBEAT, params)`，**无内置播放器定时器**
- 参考实现：`examples/android-video-demo/.../PlaybackTracker.java`（`Handler.postDelayed(30_000)`）
- 上报：与其它事件相同，进 `EventQueue` **攒批**后 `POST /v1/track`（`debug: true` 时立即 POST）

典型播放序列：`video_play_start` → 若干 `video_play_heartbeat` → `video_play_end`。

### Q21g. 注册表改 PostgreSQL 后，管理台「只看参数」变慢？

**只读不应等 `poll_interval_sec`（默认 10s）**——该间隔只影响 **保存后** Gateway 校验缓存刷新。

若「打开注册表 / 切 Tab」明显变慢，常见原因是 PG 模式下 **每个管理 API 都做 BCrypt + 查库**（YAML 模式是内存比对）。Gateway 新版本已优化：

- 登录会话缓存（30min 内免重复 BCrypt）
- `/registry/meta` 改读内存快照（不再 `COUNT(*)` 查库）
- 空间角色短缓存

需发布新版 `giso-gateway` 镜像后生效。

---

## 七、资金与服务端事实

### Q22. 客户端能报「投注成功」「到账」吗？

**不能**（若事件字典标记 `source: server`）。网关会判 **error** 并隔离。

客户端可报 **意图**（如 `bet_submit`）；**事实**（`bet_placed`）由后端 `sdk/server` 直写 Kafka。

### Q23. Flutter App 能用 Android SDK 的 bind() 吗？

**不能指望自动化。** Android SDK 的曝光/点击/参数继承依赖原生 `View` 树，标准 Flutter UI 不在该树上。  
推荐：**Dart 薄客户端 + `POST /v1/track`**，路由层接 `page_enter`/`page_exit`，元素手动或 `VisibilityDetector` 上报。完整说明见 [14-Flutter接入指南](14-Flutter接入指南.md)。

---

## 八、文档索引

| 文档 | 内容 |
|------|------|
| [06-接入指南](06-接入指南.md) | 六步接入流程 |
| [02-上报协议规范](02-上报协议规范.md) | 信封结构、传输层 |
| [03-命名与登记规范](03-命名与登记规范.md) | 命名规则、status 生命周期 |
| [07-外部视频App接入问卷](07-外部视频App接入问卷.md) | 外部 App 登记清单 |
| [13-SDK分发与版本](13-SDK分发与版本.md) | **外部 App 拿包、坐标、Key、endpoint** |
| [14-Flutter接入指南](14-Flutter接入指南.md) | **Flutter 直连接入（无官方 Plugin）** |
| [09-账号与权限体系](09-账号与权限体系.md) | 管理台登录、角色、Doppler |
| [10-空间与多租户](10-空间与多租户.md) | 空间路由、Kafka 单写、SSE 联调 |
| [deploy/DEPLOYMENT.md](../../deploy/DEPLOYMENT.md) | 测试环境发布、Doppler、RDS |
| [tools/registry/README.md](../../tools/registry/README.md) | 注册表 DB 运维脚本 |

---

## 九、仍不清楚时提供什么信息

联系 GISO 数据负责人时请附带：

1. App Key 名称、包名、平台  
2. 管理台截图或 `did` + 报错事件 JSON  
3. 涉及的 `pgid` / `eid` / `biz.code` 是否已 **live** 登记  
4. SDK 版本、`env`、endpoint  
