# GISO 玑源 · 行为分析 BI（Metabase + Doris）

GISO 数据链路最后一环：`ods_events`（Doris）→ DWD 视图 → Metabase 看板。

## 启动

```bash
cd server/bi && docker compose up -d
# http://localhost:3000 完成初始化
```

## 添加 Doris 数据源

Doris 兼容 MySQL 协议，Metabase 里选 **MySQL**：

| 项 | 值 |
|---|---|
| Host / Port | Doris FE 地址 / `9030` |
| Database | `tracking` |
| Username / Password | 建议建只读账号：`CREATE USER bi_ro IDENTIFIED BY '...'; GRANT SELECT_PRIV ON tracking.* TO bi_ro;` |

## 预置看板

先执行视图与看板 SQL：

```bash
mysql -h <doris-fe> -P 9030 -u root < ../doris/04_analysis_views.sql
```

然后把 `04_analysis_views.sql` 第二部分的 8 个 ADS 查询逐个保存为 Metabase Question，组成两个 Dashboard：

**产品看板**
1. 大盘：DAU / 新增 / 人均事件数
2. 次日/7日留存
3. 元素 CTR 排行（页面 × 元素）
4. 长视频漏斗（浏览 → 详情 → 播放）
5. 博彩转化漏斗（盘口曝光 → 提交意图 → 服务端成交，含提交成功率）
6. 视频完播率
7. 推荐实验分桶 CTR（pt 透传归因）

**质量看板**
8. 数据质量日报（缺失率 / 隔离量）——配合网关 `/metrics` 的 Prometheus 告警使用

## 口径提醒

- 资金类指标（投注成交、充值）一律查 `platform='server'` 的事实流，端上事件只用于意图/漏斗分析
- 曝光口径由 SDK 统一（≥50% 可视 & ≥500ms，去重每页一次），BI 不要再加二次去重
- 日期口径统一用 `event_date`（服务端接收时间 stime），避免客户端时钟漂移
