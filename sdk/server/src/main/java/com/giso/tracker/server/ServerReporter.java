package com.giso.tracker.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Future;

/**
 * 服务端事实上报（02-协议 §6 的实现载体）。
 *
 * 适用：资金/结算类事件（bet_placed / bet_settled / pm_order_filled / deposit_success...），
 * 即「钱相关以服务端为准」的事实流。与端上行为流同信封、同 topic，下游同一张 ods_events。
 *
 * 与端上 SDK 的区别：
 *   · 不走网关，业务服务**事务提交后**直写 Kafka（少一跳，不受网关可用性影响）
 *   · platform 固定 "server"；common 仅 app_id/uid（无设备上下文）
 *   · 分区 key = uid，同一用户的事实事件保序
 *   · 事件 code 必须在 biz_events.yaml 登记且 source: server，
 *     端上冒充上报 server 事件会被网关判 error 进隔离区
 *
 * 用法（业务服务内单例复用）：
 *   var reporter = new ServerReporter("kafka:9092", "giso_events_raw", "giso");
 *   reporter.report(uid, "bet_placed", Map.of("bet_id", "b1", "stake_amt", 100), null);
 *   // 关闭服务时 reporter.close()
 */
public final class ServerReporter implements AutoCloseable {
    private static final ObjectMapper M = new ObjectMapper();

    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final String appId;

    public ServerReporter(String bootstrapServers, String topic, String appId) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // 事实流不容忍丢失：acks=all + 幂等
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "20");
        this.producer = new KafkaProducer<>(props);
        this.topic = topic;
        this.appId = appId;
    }

    /**
     * 上报一条服务端事实事件（异步，事实流建议在事务提交后调用并对失败告警）。
     *
     * @param uid    用户 ID（分区 key，保证同用户事件顺序）
     * @param code   事件 code，必须在 biz_events.yaml 登记且 source: server
     * @param params 事件参数（按注册表登记的必携参数填）
     * @param pt     可选透传包（赔率版本、风控标签等），落 Doris pt 列
     */
    public Future<?> report(String uid, String code, Map<String, Object> params, Map<String, Object> pt) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("event code 不能为空");

        ObjectNode ev = M.createObjectNode();
        long now = System.currentTimeMillis();
        ev.put("event", "biz_event");
        ev.put("log_id", UUID.randomUUID().toString());
        ev.put("ctime", now);
        ev.put("stime", now); // 直写不经网关，stime 由本端落（分析口径）

        ObjectNode common = ev.putObject("common");
        common.put("app_id", appId);
        common.put("platform", "server");
        common.put("uid", uid == null ? "" : uid);

        ObjectNode biz = ev.putObject("biz");
        biz.put("code", code);
        if (params != null && !params.isEmpty()) biz.set("params", M.valueToTree(params));
        if (pt != null && !pt.isEmpty()) ev.set("pt", M.valueToTree(pt));

        String key = (uid == null || uid.isEmpty()) ? null : uid;
        return producer.send(new ProducerRecord<>(topic, key, ev.toString()));
    }

    /** flush 后关闭，确保缓冲事件全部送达 */
    @Override
    public void close() {
        producer.close();
    }
}
