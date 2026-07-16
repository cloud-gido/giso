# 接入常见问题 FAQ

> 面向 **外部 App / 业务线客户端** 与 **数据负责人** 的快速查阅。  
> 协议细节见 [02-上报协议规范](02-上报协议规范.md)；接入步骤见 [06-接入指南](06-接入指南.md)；长视频问卷见 [07-外部视频App接入问卷](07-外部视频App接入问卷.md)。

## 目录

| 章节 | 题号 |
|------|------|
| 一、架构与部署 | Q1–Q10 |
| 二、App Key 与鉴权 | Q11–Q13 |
| 三、注册表与登记 | Q14–Q18 |
| 四、session_id、uid、did、biz_did | Q19–Q23 |
| 五、生命周期与标准事件 | Q24–Q27 |
| 六、联调与校验 | Q28–Q39 |
| 七、资金与服务端事实 | Q40 |
| 八、文档索引 | — |
| 九、仍不清楚时提供什么信息 | — |

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

### Q5. 启动报 `permission denied for database giso`？

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

### Q6. Gateway 是 Spring Boot 吗？为什么用单体 JAR？

**不是 Spring。** `giso-gateway` 是 **Java 21 单体可执行 JAR**，刻意保持轻量：

| 组件 | 技术 |
|------|------|
| HTTP 服务 | JDK 内置 `HttpServer`（非 Tomcat / Spring MVC） |
| JSON | Jackson |
| 配置 | SnakeYAML + 环境变量 |
| 管理台 | 静态 HTML + ES Module（同进程托管） |

埋点接入是 **高并发、短路径、无状态**（解压 → 校验 → 写 Kafka → `204`），不需要 Spring 那套容器开销。生产通过 **K8s 多副本 + 负载均衡** 水平扩展，而不是拆成很多微服务。

### Q7. HTTPS 接口上报会影响 App 性能吗？

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

### Q8. 数据量很大，Gateway 单体会不会崩？

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

### Q9. 大约 10 万 DAU，单副本 Gateway 够吗？

**性能上够，高可用上仍建议 ≥2 副本。**

DAU 不能直接换算成 QPS。在 SDK **默认攒批**（`batch_size: 20`、`flush_interval_ms: 15000`）且生产 **关闭 `debug`** 的前提下，10 万 DAU 的典型峰值 HTTP 量级如下：

| 估算方式 | 假设 | 峰值 HTTP QPS（约） |
|----------|------|---------------------|
| 在线用户 | 峰值 5% 同时在线（5000 人），约每 15s 打 1 次包 | **~330** |
| 在线用户 | 峰值 10% 同时在线（1 万人） | **~670** |
| 在线用户 | 峰值 15% 同时在线（1.5 万人） | **~1000** |
| 日均事件 | 每人每天 150 条事件，集中在 4 小时晚高峰 | 平均 ~10，高峰 **~60–120** |

换算成事件吞吐：1000 req/s × 20 条/批 ≈ **2 万 events/s**（已是偏激进的晚高峰假设）。

**压测参考**（源码仓 `server/gateway/bench/`，每请求 20 条事件的批量 body）：

| 场景 | 并发 | 吞吐 | 说明 |
|------|------|------|------|
| file sink | 50–200 | **~6600 req/s** | 测网关 CPU 上限 |
| Docker + Kafka | 50，3 万请求 | **~7000 req/s**，p99 **15ms** | 更接近生产链路 |

单副本实测约 **3000–7000 req/s**（视 CPU、Kafka、注册表规模而定），相对 10 万 DAU 常见高峰 **~300–1000 req/s** 仍有数倍余量。

**结论**：

| 维度 | 建议 |
|------|------|
| **容量** | 10 万 DAU + 正常攒批 → **单副本性能足够** |
| **高可用** | 发布滚动、节点故障 → 生产仍建议 **≥2 副本**（为 HA，不为 QPS） |
| **联调** | 多副本时配置 `debug_buffer.backend: redis`，见 Q34 |

**会打破「单副本够用」的情况**：

- 生产长期 `debug: true`（不攒批，QPS 可涨一个数量级）
- `batch_size` 很小或 `flush_interval_ms` 很短
- 注册表极大导致校验 CPU 上升
- Kafka 变慢（看 `giso_kafka_spilled_total` 持续增长）

**10 万 DAU 量级推荐**：Gateway **2 副本**（HA）+ Pod **2C4G** 起步；Kafka 分区 ≥6；监控 QPS、p99、429 率、Kafka lag。持续 **>1500 req/s** 或 p99 **>100ms** 再考虑加副本。

复现压测：

```bash
cd server/gateway && mvn package -DskipTests
cd bench && java -jar ../target/giso-gateway.jar --config gateway-bench.yaml
# 另开终端
ab -n 30000 -c 50 -p payload-20.json -T application/json \
  -H "X-App-Key: bench-key" http://127.0.0.1:18123/v1/track
```

### Q10. 和「Spring 微服务」比，这套架构的取舍？

| | GISO 单体 Gateway | Spring 微服务 |
|--|-------------------|---------------|
| 内存 / 启动 | 更轻 | 更重 |
| 接入场景匹配度 | 高（校验 + 转发） | 功能过剩 |
| 扩展方式 | 水平扩副本（无状态） | 拆服务 + 扩副本 |
| 适用阶段 | 创业 ~ 中等流量 | 超大规模可演进专用 ingest 集群 |

**结论**：不是「没用 Spring 就容易崩」，而是 **流量上来时扩 Gateway 副本 + 限流 + Kafka 扩容**。协议与 SDK 设计（9 事件收敛、攒批）从源头控制了数据量。

---

## 二、App Key 与鉴权

### Q11. App Key 是什么？怎么申请？

- Header：`X-App-Key: video-android-beta`（sendBeacon 等无法带 Header 时用 `?k=`）
- 配置在 **Doppler** `INFRA_GISO_APP_KEYS`（逗号分隔）→ K8s `GISO_APP_KEYS`
- **不要**写进 ConfigMap 或代码仓库

命名建议：

| App Key | 用途 | SDK `env` |
|---------|------|-----------|
| `video-android-beta` | Android 内测包 | `test` |
| `video-android-prod` | Android 正式包 | `prod` |
| `video-ios-beta` / `video-ios-prod` | iOS 同理 | `test` / `prod` |

### Q12. 管理台怎么登录？有哪些角色？

与 **GIDO** 类似分三层：**Ingress 内网白名单** + **登录页** +（App 侧独立）**X-App-Key**。

| 角色 | 能做什么 |
|------|----------|
| **admin** | 批准/驳回、改注册表、发布/废弃、账号管理、清缓冲 |
| **editor** | 提交登记（**待审批**），不可直接上线 |
| **viewer** | 只看联调/统计/注册表、跑断言 |

**默认管理员**（PostgreSQL 空库、Doppler 未配 `GISO_ADMIN_*` 时自动创建一次）：`admin` / `admin123`。登录后在 **账号管理** 改密并创建 editor/viewer，**不必**先配 Doppler。

Doppler 可选：`INFRA_GISO_ADMIN_USER` / `PASSWORD`、`INFRA_GISO_VIEWER_*`、`INFRA_GISO_ADMIN_USERS`。详见 [09-账号与权限体系](09-账号与权限体系.md)。

访问 `/admin/` 打开登录页；左下角可退出。

### Q13. `env=test` 和 `env=prod` 有什么区别？

| SDK `env` | Kafka Topic | 用途 |
|-----------|-------------|------|
| `test`（或 debug 模式） | `giso_events_raw_test` | 联调、内测包 |
| `prod`（缺省） | `giso_events_raw` | 正式上线 |

网关按事件 `common.env`（或 SDK 配置）路由，**测试流量不进生产 ODS**。

---

## 三、注册表与登记

### Q14. 必须先登记才能上报吗？

**是。** 未登记的 `pgid` / `eid` / `biz.code` / 参数 key：

- HTTP 仍可能返回 `204`（已接收）
- 校验结果为 **error** → 事件进 **`giso_events_quarantine` 隔离区**，**不进主 Doris 表**

管理台实时联调里显示 **红色「错误」**。

### Q15. 登记有哪些入口？

| 方式 | 适用 |
|------|------|
| **管理台 → 注册表配置** | 编辑员提交 **待审批**；管理员批准后 **live 生效** |
| **管理台 → 待审批** | 管理员批量批准 / 驳回 |
| **管理台 → 发布** | 管理员将 draft/testing → live（跳过审批的运维路径） |
| **`schema/*.yaml` PR** | SDK 常量生成、正式评审与 Git 审计 |
| **问卷 [07](07-外部视频App接入问卷.md)** | 外部 App 接入前由产品填写 |

推荐流程：**编辑员登记(pending) → 管理员批准(live) → 联调 → 导出 YAML 合 PR**。

### Q16. 待审批和 draft 有什么区别？

| 状态 | 含义 | 谁产生 |
|------|------|--------|
| **pending** | 已提交、等管理员批准 | 编辑员保存登记 |
| **draft** | 登记中 / 被驳回退回 | 管理员驳回或管理员直接建 |

两者都不参与线上校验；**只有 `live`（及 `testing`）会校验 App 上报**。

### Q17. 新增页面要登记哪些表？

顺序建议：

1. **参数池**（页面用到的 `vid` 等先在 `params` 登记）
2. **元素池**（按钮、卡片等 `eid`）
3. **页面池**（`pgid` + 可选 `elements` 结构体绑定）
4. **业务事件**（仅曝光/点击表达不了的，如 `video_play_start`）

### Q18. 元素为什么要全局唯一？能和页面绑在一起命名吗？

- ✅ `video_card` + 不同 `pgid` 区分页面
- ❌ `feed_video_card`、`detail_video_card`（把页面编进元素名）

页面可选声明 `elements: [...]`，声明后 **该页出现未绑定元素会判 error**。

---

## 四、session_id、uid、did、biz_did

### Q19. `session_id` 谁生成？规则是什么？

**SDK 自动生成并维护**，业务方 **不要** 自己造 session_id。

| 平台 | 行为 |
|------|------|
| Android / iOS | 进前台时检查：距上次活跃 **> 30 分钟** → 生成新 `session_id`（`s-{uuid}`） |
| Web | `sessionStorage` 记录 id + 时间戳，同样 **30 分钟** 间隔重开 |

用于会话级 PV/停留分析，与登录无关（未登录也有 session）。

### Q20. `uid`、`did`、`biz_did` 和 `session_id` 有什么区别？

| 字段 | 含义 | 谁设置 |
|------|------|--------|
| `did` | SDK 设备标识，卸载前持久 | SDK |
| `biz_did` | 业务设备 ID（历史账号/去重体系） | 业务调用 `setBizDid()` / `clearBizDid()` |
| `session_id` | 一次使用会话（30min 规则） | SDK |
| `uid` | 登录用户 ID | 业务调用 `setUid()` / `clearUid()` |

登录只影响 `uid`，**不会**单独重置 session（除非同时满足 30min 规则）。

- **新安装 / 合规匿名设备**：用 `did`（卸载重装会变）。
- **兼容历史「用业务设备 ID 当唯一用户」**：用 `biz_did`（App 负责生成/恢复；SDK 不生成、不持久化）。
- Doris 未单独落列，查询：`json_extract_string(common_ext,'$.biz_did')`。

### Q21. `app_launch` / `app_install` 会不会改 `did`？

**都不会去改写 `did`。**

| 事件 | 与 `did` 的关系 |
|------|----------------|
| `app_launch` | 每次**进程冷启动**都报；`did` 仍从本地读，**不变** |
| `app_install` | 本地尚无激活标记时报一次；事件本身**不生成**新 `did` |

`did` 只在这些时候变：

1. **首次安装 / 清除应用数据后第一次进 App**：本地没有 `did` → SDK 生成新 UUID 并持久化（此时通常会连着出现 `app_install` + `app_launch`）
2. **卸载重装**，或用户/系统 **清除应用数据**

因此：看到 `app_install` 往往意味着「新的一次安装周期开始」，和「新 `did`」经常一起出现；真正换 `did` 的是本地存储没了/新建，不是 launch/install 去改写。

### Q22. 没有账号体系怎么判断新用户？卸载重装算不算？

没有登录时，**无法 100% 认出「以前装过又卸了」的同一个人**——卸载会清掉本地 `did`，下次是新 `did` 并再打一次 `app_install`。这是系统与隐私限制下的常态。

| 口径 | 怎么判 | 含义 |
|------|--------|------|
| **安装新用户（推荐默认）** | 出现 `app_install`，或该 `did` 首次出现 | 「这一次安装周期」的新用户；**含重装** |
| **设备终身新用户** | 需要跨卸载仍不变的 ID | 本地 `did` 做不到；需业务自管更持久的标识 |

建议：

- **拉新 / 激活**：`app_install` 或 `did` 首次出现
- **活跃**：`app_foreground` / `app_heartbeat` 等按 `did` 去重
- **怀疑重装**：若业务能用 `setBizDid` 恢复跨卸载的同一 `biz_did`，则可看「同一 `biz_did` 下多个 `did` / 多次 `app_install`」；若 `biz_did` 也只存在普通本地存储里，卸载后一样会丢

一句话：无账号就用 **安装级新用户**；跨卸载识别靠你们自己的 `biz_did`（或以后上账号），不是靠 `app_launch`。

### Q23. `rec_trace_id` 和 `session_id` 是一回事吗？

**不是。**

| 字段 | 位置 | 用途 |
|------|------|------|
| `session_id` | `common` | 客户端会话，SDK 管 |
| `rec_trace_id` | 多在 `pt` 透传包或业务参数 | **推荐/广告一次请求**的追踪 ID，由**推荐服务下发**，用于效果归因 |

`pt` 包内容 **不登记、不校验**，原样落库 JSON 列。

---

## 五、生命周期与标准事件

### Q24. 哪些事件 SDK 自动报？业务还要写吗？

| 事件 | 谁报 |
|------|------|
| `app_install` / `app_launch` / `app_foreground` / `app_background` / `app_heartbeat` | **SDK 自动** |
| `page_enter` / `page_exit` | 业务在页面生命周期调 `enterPage` / `exitPage` |
| `element_exposure` / `element_click` | `bind` 后 SDK 自动（勿自写滚动监听判曝光） |
| `biz_event` | 业务调 `bizEvent()` |

`app_heartbeat`：仅前台、默认 60s（`/v1/config` 的 `heartbeat_interval_ms` 可调，15s–300s）。用于活跃时长兜底，**不替代** `app_background.fg_dur`；与长视频 `video_play_heartbeat`（播放业务心跳）不是一回事。

### Q25. `app_launch` 和进前台重复吗？SDK 会清业务数据吗？

**不重复；也不会清业务数据。**

| 事件 | 含义 |
|------|------|
| `app_launch` | **进程冷启动**（`Tracker.init` / 进程被杀后再开） |
| `app_foreground` | 进程还在，从后台回到前台 |
| `app_background` | 切到后台（尽量上报；部分 OEM 杀得太快时可能看不到） |

正常路径：

- **第一次打开 / 被系统杀掉后再开**：`app_launch` → `app_foreground` → …
- **只按 Home 再点图标（进程没死）**：只有 `app_foreground`，**不应再有** `app_launch`

若每次「退出再进」都看到 `app_launch`，说明系统把进程杀掉了（冷启动），不是 SDK 把 launch 和 foreground 写成一回事。

SDK **只**读写自己的本地键（如 `giso_tracker` 下的 `did` / 激活标记）与内存上报队列，**不会** `clear` 业务 SharedPreferences、数据库或登录态。联调时感觉「数据没了」，多半是进程被杀导致**未持久化的内存状态**丢失，或把管理台上新的一轮 `app_launch` 误当成 App 被重置。

### Q26. 登录要怎么埋？

```java
Tracker.setUid("10086");   // 登录成功
Tracker.clearUid();        // 登出
```

**不要**为登录单独造 `biz_event`，除非已在事件字典登记且有明确看数需求。

### Q27. 只有 10 个标准事件名吗？

是。业务差异用 **`pgid` / `eid` / `biz.code` + params** 表达，禁止自造 `xxx_click` 事件名。

---

## 六、联调与校验

### Q28. 管理台颜色各代表什么？

| 标记 | 校验结果 | 数据去向 |
|------|----------|----------|
| 绿 | ok | `giso_events_raw` / `_test` |
| 黄 | missing（必携参数空） | raw + `_quality=missing` |
| 红 | error（未登记、类型错、元素未绑定页面等） | **quarantine 隔离区** |

### Q29. 联调怎么只看自己设备？

1. SDK `debug: true`（实时上报、走 test topic）
2. 管理台 → **实时联调** → 按 `did` 过滤
3. 可选：`POST /admin/api/assert` 固化期望事件序列

### Q30. 长视频 Demo 对接测试环境怎么配？

```kotlin
TrackerConfig.builder("video-android-beta", version, endpoint)
    .env("test")   // 或 debug=true
    .build()
// endpoint: https://gamelinelab-giso.envir.dev/v1/track
```

复用登记：`video_feed`、`video_detail`、`video_series` 及 Demo 中已有元素/事件常量（见 `sdk/android/.../Pages.java` 等）。

### Q31. 多空间会往 Kafka 写两份吗？

**不会。** 每条上报只解析 **一个** 空间（`X-App-Key` → `space_key`），Kafka **只写一条**；空间标识在 `common.space`。Topic 不按空间拆分，查数时用 `space_key` 过滤。

### Q32. 联调时为什么 default 和长视频空间都有数据？

常见原因（**不是双写**）：

| 现象 | 原因 |
|------|------|
| 两个空间**注册表**很像 | `default` → `longvideo` **注册表镜像**（配置同步） |
| 两个空间**联调**都有事件 | 用了不同的 App Key（`video-*` vs 其他），或切换空间前旧 SSE 未刷新（新版本已修复：切空间重连 SSE） |
| 一条事件有两个 space | **不应出现**；展开 JSON 查 `common.space`，若不对则查 App Key |

长视频连调请统一使用 `video-android-beta` / `video-android-prod` 等 **`video-*` Key**。

### Q33. 管理台「实时联调」的 SSE 是什么？和 App 上报一样吗？

**不一样。**

| | App 上报 | 管理台 SSE |
|--|----------|------------|
| 协议 | `POST /v1/track` 短连接 HTTP | `GET /admin/api/stream` 长连接 |
| 方向 | 客户端 → 服务端 | 服务端 → 浏览器 |
| 用途 | 埋点入库 | 联调页实时看校验结果 |

App **不需要**对 Gateway 建 WebSocket/SSE。

### Q34. 切换管理台空间后，联调事件要不要手动刷新？

**不需要**（Gateway 新版本起）：切空间或再次进入「实时联调」会自动 **断开旧 SSE → 拉当前空间缓冲 → 重连 SSE**。工具栏显示当前联调空间名；每条事件展示 **space:** 标签。

### Q35. 播放心跳（`video_play_heartbeat`）要建立长连接吗？

**不要。** 心跳是业务事件，由 **App 播放器层**实现：

- 登记：`schema/biz_events.yaml` → `video_play_heartbeat`（约每 **30s** + 暂停/退出补报）
- SDK：调 `bizEvent(BizEvents.VIDEO_PLAY_HEARTBEAT, params)`，**无内置播放器定时器**
- 参考实现：`examples/android-video-demo/.../PlaybackTracker.java`（`Handler.postDelayed(30_000)`）
- 上报：与其它事件相同，进 `EventQueue` **攒批**后 `POST /v1/track`（`debug: true` 时立即 POST）

典型播放序列：`video_play_start` → 若干 `video_play_heartbeat` → `video_play_end`。

### Q36. 注册表改 PostgreSQL 后，管理台「只看参数」变慢？

**只读不应等 `poll_interval_sec`（默认 10s）**——该间隔只影响 **保存后** Gateway 校验缓存刷新。

若「打开注册表 / 切 Tab」明显变慢，常见原因是 PG 模式下 **每个管理 API 都做 BCrypt + 查库**（YAML 模式是内存比对）。Gateway 新版本已优化：

- 登录会话缓存（30min 内免重复 BCrypt）
- `/registry/meta` 改读内存快照（不再 `COUNT(*)` 查库）
- 空间角色短缓存

需发布新版 `giso-gateway` 镜像后生效。

---

### Q37. Gateway 多副本时，实时联调看不到 App 上报怎么办？

**原因**：旧版联调缓冲在**各 Pod 进程内存**，App 打到 Pod B、浏览器 SSE 连在 Pod A 时会漏事件。

**解法**（Gateway 新版本）：配置 **`debug_buffer.backend: redis`**，近期事件写入 Redis List + Pub/Sub，各 Pod 订阅后推本地 SSE。与副本数、Ingress 类型无关。

```yaml
debug_buffer:
  backend: redis          # 单副本 / 本地可省略，默认 memory
  redis_url: ""           # 生产用 GISO_DEBUG_REDIS_URL 环境变量注入
  key_prefix: giso:debug
  recent_max: 5000
  ttl_sec: 3600
```

测试环境 Doppler：**复用已有** `INFRA_ARCHERY_REDIS_HOST` + `INFRA_ARCHERY_REDIS_PASSWORD`（与 Archery 同 `internal-redis` 实例）。GISO Deployment 设 `GISO_DEBUG_REDIS_DB=2`、`GISO_DEBUG_REDIS_SEARCH_NAMESPACE=business-platform`（Archery 用 db/0；GISO 在 `bigdata` 命名空间，短主机名自动扩成集群 DNS）。

也可直接配整 URL（生产 ElastiCache 等）：

```
GISO_DEBUG_REDIS_URL=redis://:<password>@your-redis:6379/2
```

`GET /health` 返回 `debug_buffer: redis` 与 `instance_id`（当前 Pod 名），便于排障。

### Q38. Kafka 有数据，Doris 表却是空的 / Routine Load 一直 PAUSED？

先分清链路：

```text
Gateway → Kafka（有消息）→ Doris Routine Load → ods_events
```

Gateway 写 Kafka 成功 **不等于** Doris 已入库。常见情况：

| 现象 | 含义 |
|------|------|
| topic 有数据，`SHOW ROUTINE LOAD` 为 `PAUSED` | Doris 消费失败后停任务 |
| ErrorLog：`no partition for this tuple` | 行的 `event_date` 在表上没有对应 RANGE 分区 |
| `loadedRows=0`，`errorRows≈totalRows` | 本批几乎全部被拒 |

**典型触发**：删表重建后立刻用 `OFFSET_BEGINNING`（或新 `group.id`）开 Load → 重放历史 `event_date`（如 `2026-06-30`），而动态分区刚建表时往往只有「今天附近」分区 → 历史行全失败 → **超过 `max_error_number` 后 PAUSED** → **连当天已有分区的新数据也不再入库**，直到 `RESUME`。

这与 Gateway 日志里的 Kafka `Node disconnected` **无关**（多为 producer 空闲重连噪音）。

处理概要：关掉动态分区 → 按最早 `event_date` 补 RANGE 分区 → 再打开动态分区 → `RESUME ROUTINE LOAD`。完整 SQL 与预防措施见 [`server/doris/README.md`](../../server/doris/README.md)「排障：no partition for this tuple」。

不想灌历史时：换新 `group.id` + `OFFSET_END`，只消费增量。

### Q39. Flutter App 能用 Android SDK 的 bind() 吗？

**不能指望自动化。** Android SDK 的曝光/点击/参数继承依赖原生 `View` 树，标准 Flutter UI 不在该树上。  
推荐：官方 **`giso_tracker`** 包（Git path 依赖）+ 路由层 `enterPage`/`exitPage`，元素手动或 `VisibilityDetector` 上报。完整说明见 [14-Flutter接入指南](14-Flutter接入指南.md)。

区分 Flutter / 原生上报：看 `common.sdk_runtime`（`flutter` | `native` | `web`），**不要**用 `platform`（Flutter 在安卓上仍是 `android`）。Doris：`json_extract_string(common_ext,'$.sdk_runtime')`。

## 七、资金与服务端事实

### Q40. 客户端能报「投注成功」「到账」吗？

**不能**（若事件字典标记 `source: server`）。网关会判 **error** 并隔离。

客户端可报 **意图**（如 `bet_submit`）；**事实**（`bet_placed`）由后端 `sdk/server` 直写 Kafka。

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
| [server/doris/README.md](../../server/doris/README.md) | Doris 建表、Routine Load、缺分区 PAUSED 排障 |
| [tools/registry/README.md](../../tools/registry/README.md) | 注册表 DB 运维脚本 |

---

## 九、仍不清楚时提供什么信息

联系 GISO 数据负责人时请附带：

1. App Key 名称、包名、平台  
2. 管理台截图或 `did` + 报错事件 JSON  
3. 涉及的 `pgid` / `eid` / `biz.code` 是否已 **live** 登记  
4. SDK 版本、`env`、endpoint  
