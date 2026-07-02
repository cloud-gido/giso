# 埋点上报全流程

## 标准六步

1. **明确指标** — 要看什么数、按什么维度拆
2. **登记** — 管理台或 `schema/*.yaml` PR，先登记后使用
3. **拿常量** — CI 生成 `Pages` / `Elements` / `BizEvents`
4. **SDK 初始化** — 配置 Gateway URL + `X-App-Key`
5. **联调** — `debug: true` + 管理台 SSE 实时核对
6. **发布** — 登记 `live`，看覆盖率与 Doris 落地

## 数据链路

```
SDK POST /v1/track
  → Gateway 校验（ok / missing / error）
  → Kafka giso_events_raw | giso_events_raw_test | giso_events_quarantine
  → Doris Routine Load → 看板
```

## env 分流

| SDK env | App Key 示例 | Kafka topic |
|---------|--------------|-------------|
| test | video-android-beta | giso_events_raw_test |
| prod | video-android-prod | giso_events_raw |

## 校验失败

错误事件进 **隔离区** topic，带 `_issues` 明细。修 registry 或客户端后：

```bash
python3 server/ops/replay_quarantine.py replay --gateway $URL --app-key $KEY
```
