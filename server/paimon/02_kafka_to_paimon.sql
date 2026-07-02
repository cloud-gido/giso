-- Kafka → Paimon（与 Doris Routine Load 同源 topic）
-- 部署前替换 ${KAFKA_BOOTSTRAP} ${WAREHOUSE}

CREATE TABLE giso_events_kafka (
  value STRING
) WITH (
  'connector' = 'kafka',
  'topic' = 'giso_events_raw',
  'properties.bootstrap.servers' = '${KAFKA_BOOTSTRAP}',
  'properties.group.id' = 'giso-paimon-ods',
  'scan.startup.mode' = 'earliest-offset',
  'format' = 'raw'
);

-- 简化：整行 JSON 落 payload；生产建议用 JSON format + 显式列映射
INSERT INTO giso.ods_events
SELECT
  JSON_VALUE(value, '$.log_id') AS log_id,
  JSON_VALUE(value, '$.event') AS event,
  CAST(JSON_VALUE(value, '$.stime') AS TIMESTAMP(3)) AS stime,
  JSON_VALUE(value, '$.common.app_id') AS app_id,
  JSON_VALUE(value, '$.common.platform') AS platform,
  JSON_VALUE(value, '$.common.did') AS did,
  COALESCE(JSON_VALUE(value, '$.common.env'), 'prod') AS env,
  COALESCE(JSON_VALUE(value, '$.common.space'), 'default') AS space_key,
  JSON_VALUE(value, '$.common.app_vrsn') AS app_vrsn,
  JSON_VALUE(value, '$.page.pgid') AS pgid,
  JSON_VALUE(value, '$.element.eid') AS eid,
  JSON_VALUE(value, '$.biz.code') AS biz_code,
  COALESCE(JSON_VALUE(value, '$._quality'), 'ok') AS quality,
  value AS payload,
  DATE_FORMAT(CAST(JSON_VALUE(value, '$.stime') AS TIMESTAMP(3)), 'yyyy-MM-dd') AS dt
FROM giso_events_kafka;
