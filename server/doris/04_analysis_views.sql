-- ============================================================
-- 行为分析层（DWD 视图 + ADS 看板 SQL）
-- 直接建在 ods_events 之上；BI（Metabase/Superset）连 Doris 后
-- 把本文件的 ADS 查询保存为卡片即得到预置看板。
-- 口径约定：
--   · 曝光 = element_exposure（SDK 已做 ≥50% & ≥500ms 去重口径）
--   · 点击 = element_click
--   · 日期 = event_date（stime 服务端时间，分析统一口径）
--   · 资金事实只取 platform='server'（端上冒充已被网关隔离，双保险）
-- ============================================================
USE tracking;

-- ════════════════════════════════════════════════════════════
-- 一、DWD 视图（给 BI 当干净数据源，屏蔽 ODS 列细节）
-- ════════════════════════════════════════════════════════════

-- 曝光/点击明细（CTR 分析基础）
CREATE VIEW IF NOT EXISTS dwd_element_events AS
SELECT event_date, stime, event, platform, app_vrsn, channel,
       did, uid, session_id, pgid, eid, mod, pos,
       exp_dur, exp_ratio, el_params, pt
FROM ods_events
WHERE event IN ('element_exposure', 'element_click') AND quality = 'ok';

-- 页面浏览明细（带停留时长与来源链路）
CREATE VIEW IF NOT EXISTS dwd_page_views AS
SELECT event_date, stime, event, platform, app_vrsn, channel,
       did, uid, session_id, pgid, ref_pgid, ref_eid, pg_stay, pg_params
FROM ods_events
WHERE event IN ('page_enter', 'page_exit') AND quality IN ('ok', 'missing');

-- 业务事件明细（行为流 client + 事实流 server 同表，source 字段区分）
CREATE VIEW IF NOT EXISTS dwd_biz_events AS
SELECT event_date, stime, platform, app_vrsn, channel,
       did, uid, session_id, pgid, biz_code, biz_params, pt,
       IF(platform = 'server', 'server', 'client') AS source
FROM ods_events
WHERE event = 'biz_event';

-- 设备日活跃明细（留存/DAU 基础）
CREATE VIEW IF NOT EXISTS dwd_daily_active AS
SELECT event_date, did, ANY_VALUE(uid) AS uid,
       ANY_VALUE(platform) AS platform, ANY_VALUE(channel) AS channel,
       MIN(stime) AS first_seen, MAX(stime) AS last_seen,
       COUNT(*) AS event_cnt
FROM ods_events
GROUP BY event_date, did;

-- ════════════════════════════════════════════════════════════
-- 二、ADS 看板 SQL（保存为 BI 卡片）
-- ════════════════════════════════════════════════════════════

-- ── 1. 大盘：DAU / 新增 / 人均事件数（近30天）──────────────
-- 新增 = 当天出现 app_install 的设备
SELECT a.event_date,
       COUNT(DISTINCT a.did)                                   AS dau,
       COUNT(DISTINCT i.did)                                   AS new_devices,
       ROUND(SUM(a.event_cnt) / COUNT(DISTINCT a.did), 1)      AS events_per_device
FROM dwd_daily_active a
LEFT JOIN (SELECT DISTINCT event_date, did FROM ods_events WHERE event = 'app_install') i
       ON a.event_date = i.event_date AND a.did = i.did
WHERE a.event_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
GROUP BY a.event_date ORDER BY a.event_date;

-- ── 2. 次日/7日留存（按安装日期分组）───────────────────────
WITH installs AS (
    SELECT DISTINCT event_date AS install_date, did
    FROM ods_events WHERE event = 'app_install'
      AND event_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
)
SELECT i.install_date,
       COUNT(DISTINCT i.did)                                              AS installs,
       COUNT(DISTINCT IF(a.event_date = DATE_ADD(i.install_date, INTERVAL 1 DAY), a.did, NULL)) AS d1,
       COUNT(DISTINCT IF(a.event_date = DATE_ADD(i.install_date, INTERVAL 7 DAY), a.did, NULL)) AS d7,
       ROUND(COUNT(DISTINCT IF(a.event_date = DATE_ADD(i.install_date, INTERVAL 1 DAY), a.did, NULL))
             / COUNT(DISTINCT i.did) * 100, 1)                            AS d1_pct
FROM installs i
LEFT JOIN dwd_daily_active a ON a.did = i.did
GROUP BY i.install_date ORDER BY i.install_date;

-- ── 3. 元素 CTR 排行（页面 × 元素，近7天）──────────────────
-- 曝光点击同口径（继承参数在 el_params，需要可下钻 pos/分桶）
SELECT pgid, eid,
       SUM(event = 'element_exposure')                          AS exposures,
       SUM(event = 'element_click')                             AS clicks,
       ROUND(SUM(event = 'element_click')
             / GREATEST(SUM(event = 'element_exposure'), 1) * 100, 2) AS ctr_pct
FROM dwd_element_events
WHERE event_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
GROUP BY pgid, eid
HAVING exposures > 100          -- 小流量去噪
ORDER BY exposures DESC LIMIT 50;

-- ── 4. 页面漏斗：首页 → 详情 → 转化（近7天，按业务线套用）──
-- 示例：长视频（home/video_feed → video_detail → video_play_start）
WITH funnel AS (
    SELECT did,
           MAX(event = 'page_enter' AND pgid IN ('home', 'video_feed'))  AS s1,
           MAX(event = 'page_enter' AND pgid = 'video_detail')           AS s2,
           MAX(event = 'biz_event'  AND biz_code = 'video_play_start')   AS s3
    FROM ods_events
    WHERE event_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
    GROUP BY did
)
SELECT SUM(s1) AS step1_browse, SUM(s1 AND s2) AS step2_detail, SUM(s1 AND s2 AND s3) AS step3_play,
       ROUND(SUM(s1 AND s2) / GREATEST(SUM(s1), 1) * 100, 1)            AS s1_to_s2_pct,
       ROUND(SUM(s1 AND s2 AND s3) / GREATEST(SUM(s1 AND s2), 1) * 100, 1) AS s2_to_s3_pct
FROM funnel;

-- ── 5. 博彩转化漏斗：盘口曝光 → 加注单 → 提交意图 → 服务端成交 ──
-- 端上意图（bet_submit, source=client）vs 服务端事实（bet_placed, source=server）
-- 两者差值即「提交未成交」（风控拒单/赔率变化/余额不足），是产品要盯的核心指标
WITH bet_funnel AS (
    SELECT did,
           MAX(event = 'element_exposure' AND eid = 'odds_btn')              AS s1,
           MAX(event = 'element_click'    AND eid = 'odds_btn')              AS s2,
           MAX(event = 'biz_event' AND biz_code = 'bet_submit')              AS s3,
           MAX(event = 'biz_event' AND biz_code = 'bet_placed'
               AND platform = 'server')                                      AS s4
    FROM ods_events
    WHERE event_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
    GROUP BY did
)
SELECT SUM(s1) AS odds_exposed, SUM(s2) AS odds_clicked,
       SUM(s3) AS bet_submitted, SUM(s4) AS bet_placed,
       ROUND(SUM(s4) / GREATEST(SUM(s3), 1) * 100, 1) AS submit_success_pct
FROM bet_funnel;

-- ── 6. 视频播放质量：人均播放/完播率（近7天）────────────────
SELECT event_date,
       COUNT(DISTINCT IF(biz_code = 'video_play_start', did, NULL))  AS play_devices,
       SUM(biz_code = 'video_play_start')                            AS plays,
       SUM(biz_code = 'video_play_end'
           AND CAST(biz_params->'$.play_dur' AS BIGINT)
               >= CAST(biz_params->'$.video_dur' AS BIGINT) * 0.9)   AS finishes,
       ROUND(SUM(biz_code = 'video_play_end'
           AND CAST(biz_params->'$.play_dur' AS BIGINT)
               >= CAST(biz_params->'$.video_dur' AS BIGINT) * 0.9)
           / GREATEST(SUM(biz_code = 'video_play_start'), 1) * 100, 1) AS finish_pct
FROM dwd_biz_events
WHERE event_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
GROUP BY event_date ORDER BY event_date;

-- ── 7. 推荐效果归因：透传包 pt 的用法示例 ───────────────────
-- 后台把 rec_trace_id/实验分桶塞进 pt，端上原样回传 → 直接按实验分桶看 CTR
SELECT pt->'$.exp_bucket'                                        AS exp_bucket,
       SUM(event = 'element_exposure')                           AS exposures,
       SUM(event = 'element_click')                              AS clicks,
       ROUND(SUM(event = 'element_click')
             / GREATEST(SUM(event = 'element_exposure'), 1) * 100, 2) AS ctr_pct
FROM dwd_element_events
WHERE event_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
  AND eid = 'video_card' AND pt->'$.exp_bucket' IS NOT NULL
GROUP BY exp_bucket ORDER BY exp_bucket;

-- ── 8. 数据质量日报：缺失率 / 隔离量（运营巡检）─────────────
SELECT o.event_date,
       COUNT(*)                                          AS total,
       SUM(o.quality = 'missing')                        AS missing_cnt,
       ROUND(SUM(o.quality = 'missing') / COUNT(*) * 100, 2) AS missing_pct,
       COALESCE(q.quarantined, 0)                        AS quarantined
FROM ods_events o
LEFT JOIN (SELECT event_date, COUNT(*) AS quarantined
           FROM ods_events_quarantine GROUP BY event_date) q
       ON o.event_date = q.event_date
WHERE o.event_date >= DATE_SUB(CURDATE(), INTERVAL 14 DAY)
GROUP BY o.event_date, q.quarantined ORDER BY o.event_date;
