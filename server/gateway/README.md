# giso-gateway · GISO 玑源接入网关

GISO 数据源头 — 接入网关 + 管理控制台。

## 功能

| 端点 | 说明 |
|---|---|
| `POST /v1/track` | 事件上报：批量 JSON（支持 gzip、sendBeacon 单条对象），逐条按 `schema/` 注册表校验 |
| `GET /v1/config` | SDK 远程配置下发（曝光口径、攒批参数） |
| `GET /health` | 存活探针（含 registry backend/revision） |
| `GET /ready` | 就绪探针（PostgreSQL 注册表可连） |
| `GET /metrics` | Prometheus 指标（文本格式，零依赖实现） |
| `GET /admin/` | 管理控制台：实时联调 / 注册表配置 / 质量统计 |
| `GET /admin/api/me` | 当前登录用户与角色 |
| `GET /admin/api/users` | 有效管理账号列表（admin 专用，无密码） |
| `GET /admin/api/*` | 管理 REST API（registry CRUD、publish/deprecate、audit、events、stats、hourly、SSE） |
| `POST /admin/api/assert` | 联调用例断言：声明期望事件序列，与设备实报按序比对，返回逐条命中明细 |

校验结果三分类（与方案文档 §流程治理 一致）：

- **正常** → raw 流
- **缺失**（必携参数为空）→ raw 流并打 `_quality=missing` 标记
- **错误**（未登记事件/元素/页面/参数、类型错误、**元素未绑定到页面结构体**、**端上冒充 server 事实事件**）→ quarantine 隔离流（带 `_issues` 失败明细），可回放

### 联调用例断言示例

```bash
curl -X POST localhost:8123/admin/api/assert -d '{
  "did": "测试设备did",
  "expect": [
    { "event": "page_enter",    "pgid": "video_feed" },
    { "event": "element_click", "eid":  "video_card" },
    { "event": "biz_event",     "code": "video_play_start" }
  ]
}'
# → { "pass": true/false, "matched": n, "detail": [ {expect, hit, at}... ] }
# 有序子序列匹配：每条期望只能命中上一条命中位置之后的实报事件
```

## 出口插拔式（sink）

raw / quarantine 两条流的去向由 `gateway.yaml` 配置，可多路双写：

| sink | 用途 | 说明 |
|---|---|---|
| `file` | 本地开发 / 小流量 | JSONL 按天滚动（`giso_events_raw-2026-06-10.jsonl`） |
| `kafka` | 生产管道 | 分区 key=did 保序、acks=all + 幂等 producer + lz4；**broker 不可用自动 spill 本地兜底，恢复后后台线程每 60s 自动回放**（保留 did 分区 key，失败行写回下轮再试）；下游 Doris Routine Load 落地（见 `../doris/`） |

新出口（如 Doris Stream Load 直写、S3）实现 `sink/EventSink` 接口后在 `SinkFactory` 注册即可。

## 安全与防护（生产必配）

`gateway.yaml` 的 `auth` / `limits` 段，默认全关（本地开发零配置）：

| 配置 | 行为 | 未通过时 |
|---|---|---|
| `auth.app_keys` | 上报鉴权白名单，校验 `X-App-Key` Header 或 `?k=` query（sendBeacon 场景） | `401` |
| `auth.admin_user` / `admin_password` | 管理控制台 Basic Auth（**管理员**） | `401` |
| `auth.viewer_user` / `viewer_password` | **只读账号**（产品/QA） | 写操作 `403` |
| `auth.users` | 扩展多账号；`role` 可为 `admin` / `editor` / `viewer` | 同上 |
| `GISO_ADMIN_USERS` | 环境变量多账号：`user:pass:role` 逗号分隔 | 同上 |
| `auth.login_security` | 管理台登录防暴力破解（账号锁定 + IP 限流，见下） | `401` / `423` / `429` |

**`auth.login_security`（生产 postgres 建议 `enabled: true`）**

| 字段 | 默认（生产模板） | 说明 |
|---|---|---|
| `enabled` | `true` | 本地 Docker 建议 `false` |
| `max_attempts_per_user` | `5` | 同账号在窗口内连续输错次数 → 锁定 |
| `lockout_minutes` | `15` | 账号锁定时长 |
| `attempt_window_minutes` | `15` | 失败计数滑动窗口 |
| `max_attempts_per_ip` | `30` | 单 IP 在窗口内总登录尝试上限 |
| `ip_window_minutes` | `10` | IP 计数窗口 |
| `ip_block_minutes` | `15` | IP 超限封禁时长 |
| `delay_ms_per_failure` | `500` | 每次失败后累加等待（上限 3s），减缓撞库 |

环境变量：`GISO_LOGIN_SECURITY_ENABLED`、`GISO_LOGIN_MAX_ATTEMPTS_USER`、`GISO_LOGIN_LOCKOUT_MINUTES` 等。平台管理员可在「账号管理」**解锁**被锁账号，或 `POST /admin/api/users/{username}/unlock`。

生产 `registry.backend=postgres` 时，Doppler 种子账号在**空库**首次启动写入 `giso.admin_users`（BCrypt 哈希）；**编辑员**提交 `pending`，**管理员**批准后 `live` 生效。详见 [09-账号与权限体系](../../docs/tracking/09-账号与权限体系.md)。

| 配置 | 行为 | 未通过时 |
|---|---|---|
| `limits.max_body_bytes` | 单请求**解压后**体积上限（默认 1MB），限长流式读取防 gzip 炸弹 | `413` |
| `limits.rate_limit_rps` / `rate_limit_burst` | 单 IP 令牌桶限流（识别 `X-Forwarded-For`），0=关闭，生产建议 100 | `429`（SDK 会退避重试） |

```yaml
auth:
  app_keys: [giso-prod-key-xxx]
  admin_user: admin
  admin_password: <强密码>
  viewer_user: viewer        # 只读账号，发给产品/QA 看数和联调
  viewer_password: <密码>
limits:
  max_body_bytes: 1048576
  rate_limit_rps: 100
```

`GET /v1/config` 不鉴权（只下发口径参数，无敏感信息）。

## SDK 远程配置

`GET /v1/config` 下发曝光口径与攒批参数（`gateway.yaml` 的 `sdk_config` 段），客户端启动时拉取覆盖本地默认值——改口径不用发版。

## 可观测性与运维

- **`GET /metrics`**（Prometheus 文本格式）：
  - `giso_events_total{status="ok|missing|error"}` —— 事件质量分布，错误率告警直接用它
  - `giso_track_responses_total{code="..."}` —— 上报接口响应码分布（401 飙升=有人乱打 key，429=限流生效）
  - `giso_kafka_spilled_total` / `giso_kafka_replayed_total` —— spill 落盘与回放量（spilled 持续增长=broker 故障）
  - `giso_gateway_uptime_seconds`
- **优雅停机**：SIGTERM 后停收新请求 → 最多等 3s 在途请求处理完 → flush 并关闭 sink（Kafka 缓冲不丢），适配 k8s/systemd 滚动重启

## 构建与运行

```bash
mvn package          # 含单测（注册表校验 / 限流 / 指标）
java -jar target/giso-gateway.jar --config gateway.yaml
# CLI 覆盖: --port 8080 --schema ../../schema
# 管理控制台: http://localhost:8080/admin/
```

## 管理控制台

- **实时联调**：SSE 实时滚动事件流，红（错误）黄（缺失）绿（正常）标记 + 校验明细，按 did/事件/状态过滤
- **注册表配置**：参数池/页面池/元素池/业务事件 CRUD；**生产写 PostgreSQL 持久化**；支持 **发布(live) / 废弃 / 审计**
- **质量统计**：事件维度 + 参数维度异常明细

### 注册表后端

| 模式 | 配置 | 说明 |
|------|------|------|
| `postgres` | `registry.backend: postgres` + `GISO_DB_*` | **生产**；多 Pod `LISTEN` + 轮询热更新 |
| `yaml` | 默认 / 本地无 DB | 写 `schema/*.yaml`，仅开发 |

```yaml
registry:
  backend: postgres
  bootstrap_from_yaml: true    # 空库时从 schema_dir 种子导入
  poll_interval_sec: 10
  postgres:
    schema: public
```

环境变量：`GISO_REGISTRY_BACKEND`、`GISO_DB_HOST`、`GISO_DB_PORT`、`GISO_DB_NAME`、`GISO_DB_USER`、`GISO_DB_PASSWORD`。

## 校验规则来源

校验规则从 **Registry 内存快照** 加载（来源：PostgreSQL 或 YAML），管理台修改后全集群热更新，不存在第二份口径。
