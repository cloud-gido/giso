package com.giso.gateway.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.giso.gateway.GatewayConfig;
import com.giso.gateway.Metrics;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kafka 出口（生产管道）：
 *   raw → topic giso_events_raw（下游 Doris Routine Load 消费落地）
 *   quarantine → topic giso_events_quarantine
 *
 * 可靠性：
 *   · 分区 key = did，同设备事件保序
 *   · 默认 acks=all + 幂等 producer，可被 gateway.yaml kafka.properties 覆盖
 *   · 发送失败（broker 不可用等）落本地 spill 文件兜底
 *   · 后台线程每 60s 扫描 spill 目录自动回放，broker 恢复后无需人工重灌
 */
public final class KafkaSink implements EventSink {
    /** spill 文件名形如 giso_events_raw-2026-06-10.jsonl，捕获组 1 = topic */
    private static final Pattern SPILL_NAME = Pattern.compile("^(.+)-\\d{4}-\\d{2}-\\d{2}\\.jsonl$");
    private static final ObjectMapper M = new ObjectMapper();

    private final KafkaProducer<String, String> producer;
    private final String topicRaw;
    private final String topicRawTest;
    private final String topicQuarantine;
    private final Path spillDir;
    private final ScheduledExecutorService replayer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "kafka-spill-replay");
        t.setDaemon(true);
        return t;
    });

    public KafkaSink(GatewayConfig config) throws IOException {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.kafkaBootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "20");
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "5000");
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000");
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "30000"); // ≥ linger.ms + request.timeout.ms
        config.kafkaProperties.forEach((k, v) -> props.put(k, String.valueOf(v)));

        this.producer = new KafkaProducer<>(props);
        this.topicRaw = config.kafkaTopicRaw;
        this.topicRawTest = config.kafkaTopicRawTest;
        this.topicQuarantine = config.kafkaTopicQuarantine;
        this.spillDir = Path.of(config.kafkaSpillDir);
        Files.createDirectories(spillDir);
        replayer.scheduleWithFixedDelay(this::replaySpill, 60, 60, TimeUnit.SECONDS);
    }

    @Override
    public void accept(ObjectNode event, boolean quarantine) {
        String topic = quarantine ? topicQuarantine : resolveRawTopic(event);
        String key = event.path("common").path("did").asText("");
        String value = event.toString();
        try {
            producer.send(new ProducerRecord<>(topic, key.isEmpty() ? null : key, value), (meta, e) -> {
                if (e != null) spill(topic, value, e);
            });
        } catch (Exception e) {
            // send() 本身抛错（如元数据超时）也兜底落盘
            spill(topic, value, e);
        }
    }

    /** test 环境事件进独立 topic，避免污染生产分析表 */
    private String resolveRawTopic(ObjectNode event) {
        String env = event.path("common").path("env").asText("prod");
        if ("test".equalsIgnoreCase(env)) return topicRawTest;
        return topicRaw;
    }

    private void spill(String topic, String value, Exception cause) {
        System.err.println("[KafkaSink] send failed (" + cause.getMessage() + "), spilling locally");
        Metrics.inc("giso_kafka_spilled_total{topic=\"" + topic + "\"}");
        appendSpill(topic, List.of(value));
    }

    private synchronized void appendSpill(String topic, List<String> values) {
        Path f = spillDir.resolve(topic + "-" + LocalDate.now() + ".jsonl");
        try (BufferedWriter w = Files.newBufferedWriter(f, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (String v : values) {
                w.write(v);
                w.newLine();
            }
        } catch (IOException io) {
            System.err.println("[KafkaSink] spill failed: " + io.getMessage());
        }
    }

    // ── spill 自动回放 ────────────────────────────────────

    private void replaySpill() {
        List<Path> files;
        try (var stream = Files.list(spillDir)) {
            files = stream.filter(p -> SPILL_NAME.matcher(p.getFileName().toString()).matches())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return;
        }
        for (Path f : files) replayFile(f);
    }

    private void replayFile(Path f) {
        String name = f.getFileName().toString();
        Matcher m = SPILL_NAME.matcher(name);
        if (!m.matches()) return;
        String topic = m.group(1);

        // 先改名独占，避免与正在 append 的 spill 写入冲突
        Path work = f.resolveSibling(name + ".replaying");
        synchronized (this) {
            try {
                Files.move(f, work);
            } catch (IOException e) {
                return;
            }
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(work, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }

        List<String> failed = Collections.synchronizedList(new ArrayList<>());
        int sent = 0;
        for (String line : lines) {
            if (line.isBlank()) continue;
            sent++;
            try {
                producer.send(new ProducerRecord<>(topic, extractDid(line), line), (meta, e) -> {
                    if (e != null) failed.add(line);
                    else Metrics.inc("giso_kafka_replayed_total{topic=\"" + topic + "\"}");
                });
            } catch (Exception e) {
                failed.add(line);
            }
        }
        producer.flush();

        if (!failed.isEmpty()) appendSpill(topic, failed); // 写回当日文件，下轮再试
        try {
            Files.delete(work);
        } catch (IOException e) {
            System.err.println("[KafkaSink] delete replay file failed: " + e.getMessage());
        }
        System.out.println("[KafkaSink] spill replay " + name + ": "
                + (sent - failed.size()) + " ok, " + failed.size() + " requeued");
    }

    /** 回放时恢复分区 key（common.did），保持与实时路径相同的保序语义 */
    private static String extractDid(String json) {
        try {
            String did = M.readTree(json).path("common").path("did").asText("");
            return did.isEmpty() ? null : did;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String name() { return "kafka(" + topicRaw + "," + topicQuarantine + ")"; }

    @Override
    public void close() {
        replayer.shutdownNow();
        producer.close();
    }
}
