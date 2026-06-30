# giso-server-sdk

服务端事实上报库 —— 「钱相关以服务端为准」（02-协议 §6）的实现载体。

## 何时用它（而不是端上 SDK）

| 事件类型 | 上报方 | 原因 |
|---|---|---|
| 行为流（曝光/点击/页面/播放心跳） | 端上 SDK → 网关 | 行为只有端上知道 |
| **事实流（投注成功/结算/充值到账/订单成交）** | **本库 → Kafka 直写** | 钱相关必须以服务端事务结果为准，端上点了「确认投注」不等于投注成功 |

双保险：事实事件在 `biz_events.yaml` 登记为 `source: server` 后，端上冒充上报会被网关判 error 进隔离区。

## 用法

```java
// 业务服务内单例（Spring 注册成 Bean，shutdown 时 close）
ServerReporter reporter = new ServerReporter("kafka:9092", "giso_events_raw", "giso");

// 事务提交后调用（异步返回 Future，事实流建议对失败打日志+告警）
reporter.report(
    uid,
    "bet_placed",                                  // 必须登记且 source: server
    Map.of("bet_id", "b123", "match_id", "m1",
           "stake_amt", 100.0, "currency", "USD",
           "odds", 1.95, "bet_type", "single"),
    Map.of("odds_vrsn", "20260611-03")             // 可选 pt 透传（赔率版本/风控标签）
);
```

## 约定

- **信封同构**：与端上同协议同 topic，下游同一张 `ods_events`（`platform='server'` 区分）
- **分区 key = uid**：同一用户的事实事件保序（端上是 did）
- **可靠性**：acks=all + 幂等 producer；`stime` 由本端落（不经网关）
- **登记先行**：事件 code 与参数先在 `schema/biz_events.yaml` 走登记流程，再开发上报
