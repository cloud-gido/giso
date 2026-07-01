package com.giso.gateway;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关配置（gateway.yaml）。全链路可配置：端口、注册表目录、出口 sink、SDK 远程配置。
 * CLI 参数（--port/--schema/--config）覆盖文件配置。
 */
public final class GatewayConfig {
    public int port = 8080;
    public String schemaDir = "../../schema";

    /** 注册表后端：yaml（本地）| postgres（生产，共用 RDS 库 giso） */
    public String registryBackend = "yaml";
    public String dbUrl = "";
    public String dbHost = "";
    public String dbPort = "5432";
    public String dbName = "giso";
    public String dbUser = "";
    public String dbPassword = "";
    public String dbSchema = "giso";
    /** postgres 且库为空时，从 schema_dir YAML 种子导入 */
    public boolean registryBootstrapFromYaml = true;
    public int registryPollIntervalSec = 10;

    /** 出口管道，可同时启用多个（如迁移期 file + kafka 双写） */
    public List<String> sinks = List.of("file");

    // file sink
    public String fileDir = "./data";

    // kafka sink
    public String kafkaBootstrapServers = "localhost:9092";
    public String kafkaTopicRaw = "giso_events_raw";
    public String kafkaTopicRawTest = "giso_events_raw_test";
    public String kafkaTopicQuarantine = "giso_events_quarantine";
    /** kafka producer 附加属性（acks/linger.ms/compression.type...），原样透传 */
    public Map<String, Object> kafkaProperties = Map.of();
    /** kafka 不可用时本地兜底目录（spill 文件，恢复后可回放） */
    public String kafkaSpillDir = "./data/spill";

    /** 管理页环形缓冲条数 */
    public int adminRecentBuffer = 2000;

    // ── 安全 ──
    /** 上报鉴权：X-App-Key 白名单；空列表 = 不校验（仅限本地开发） */
    public List<String> appKeys = List.of();
    /** 管理控制台 Basic Auth；user 为空 = 不开启（仅限本地开发） */
    public String adminUser = "";
    public String adminPassword = "";
    /** 只读账号（看联调/统计/注册表，不能改注册表/清缓冲）；空 = 不提供只读账号 */
    public String viewerUser = "";
    public String viewerPassword = "";

    // ── 防护 ──
    /** 单次请求 body 解压后字节上限 */
    public long maxBodyBytes = 1024 * 1024;
    /** 单 IP 每秒请求数上限（令牌桶速率）；0 = 不限流 */
    public int rateLimitRps = 0;
    /** 令牌桶容量（突发上限），默认 2×rps */
    public int rateLimitBurst = 0;

    /** SDK 远程配置（GET /v1/config 下发，客户端覆盖本地默认口径） */
    public Map<String, Object> sdkConfig = Map.of(
            "exposure_ratio", 0.5,
            "exposure_duration_ms", 500,
            "exposure_max_per_page", 3,
            "batch_size", 20,
            "flush_interval_ms", 15000);

    /** ClickHouse HTTP 地址（管理台覆盖率反算）；空 = 仅用进程内累计 */
    public String clickhouseUrl = "";

    @SuppressWarnings("unchecked")
    public static GatewayConfig load(Path file) throws IOException {
        GatewayConfig c = new GatewayConfig();
        if (file == null || !Files.exists(file)) return c;
        Map<String, Object> doc = new Yaml().load(Files.readString(file));
        if (doc == null) return c;

        c.port = (int) doc.getOrDefault("port", c.port);
        c.schemaDir = (String) doc.getOrDefault("schema_dir", c.schemaDir);

        Object sinks = doc.get("sinks");
        if (sinks instanceof List<?> l) c.sinks = (List<String>) l;

        Map<String, Object> file_ = (Map<String, Object>) doc.getOrDefault("file", Map.of());
        c.fileDir = (String) file_.getOrDefault("dir", c.fileDir);

        Map<String, Object> kafka = (Map<String, Object>) doc.getOrDefault("kafka", Map.of());
        c.kafkaBootstrapServers = (String) kafka.getOrDefault("bootstrap_servers", c.kafkaBootstrapServers);
        c.kafkaTopicRaw = (String) kafka.getOrDefault("topic_raw", c.kafkaTopicRaw);
        c.kafkaTopicRawTest = (String) kafka.getOrDefault("topic_raw_test", c.kafkaTopicRawTest);
        c.kafkaTopicQuarantine = (String) kafka.getOrDefault("topic_quarantine", c.kafkaTopicQuarantine);
        c.kafkaSpillDir = (String) kafka.getOrDefault("spill_dir", c.kafkaSpillDir);
        if (kafka.get("properties") instanceof Map<?, ?> p) {
            c.kafkaProperties = new HashMap<>((Map<String, Object>) p);
        }

        Map<String, Object> admin = (Map<String, Object>) doc.getOrDefault("admin", Map.of());
        c.adminRecentBuffer = (int) admin.getOrDefault("recent_buffer", c.adminRecentBuffer);

        Map<String, Object> auth = (Map<String, Object>) doc.getOrDefault("auth", Map.of());
        if (auth.get("app_keys") instanceof List<?> keys) c.appKeys = (List<String>) keys;
        c.adminUser = (String) auth.getOrDefault("admin_user", c.adminUser);
        c.adminPassword = (String) auth.getOrDefault("admin_password", c.adminPassword);
        c.viewerUser = (String) auth.getOrDefault("viewer_user", c.viewerUser);
        c.viewerPassword = (String) auth.getOrDefault("viewer_password", c.viewerPassword);

        Map<String, Object> limits = (Map<String, Object>) doc.getOrDefault("limits", Map.of());
        c.maxBodyBytes = ((Number) limits.getOrDefault("max_body_bytes", c.maxBodyBytes)).longValue();
        c.rateLimitRps = (int) limits.getOrDefault("rate_limit_rps", c.rateLimitRps);
        c.rateLimitBurst = (int) limits.getOrDefault("rate_limit_burst",
                c.rateLimitRps > 0 ? c.rateLimitRps * 2 : 0);

        if (doc.get("sdk_config") instanceof Map<?, ?> s) c.sdkConfig = (Map<String, Object>) s;

        Map<String, Object> ch = (Map<String, Object>) doc.getOrDefault("clickhouse", Map.of());
        c.clickhouseUrl = (String) ch.getOrDefault("url", c.clickhouseUrl);

        Map<String, Object> reg = (Map<String, Object>) doc.getOrDefault("registry", Map.of());
        c.registryBackend = (String) reg.getOrDefault("backend", c.registryBackend);
        Map<String, Object> pg = (Map<String, Object>) reg.getOrDefault("postgres", Map.of());
        c.dbUrl = (String) pg.getOrDefault("jdbc_url", c.dbUrl);
        c.dbHost = (String) pg.getOrDefault("host", c.dbHost);
        c.dbPort = String.valueOf(pg.getOrDefault("port", c.dbPort));
        c.dbName = (String) pg.getOrDefault("database", c.dbName);
        c.dbUser = (String) pg.getOrDefault("user", c.dbUser);
        c.dbPassword = (String) pg.getOrDefault("password", c.dbPassword);
        c.dbSchema = (String) pg.getOrDefault("schema", c.dbSchema);
        if (reg.get("bootstrap_from_yaml") instanceof Boolean b) c.registryBootstrapFromYaml = b;
        if (reg.get("poll_interval_sec") instanceof Number n) c.registryPollIntervalSec = n.intValue();

        applyEnvironmentOverrides(c);
        return c;
    }

    /** JDBC URL：优先 dbUrl，否则由 host/port/database 拼装。 */
    public String jdbcUrl() {
        if (dbUrl != null && !dbUrl.isBlank()) return dbUrl.trim();
        if (dbHost == null || dbHost.isBlank()) {
            throw new IllegalStateException("postgres registry requires GISO_DB_URL or GISO_DB_HOST");
        }
        String base = "jdbc:postgresql://" + dbHost.trim() + ":" + dbPort.trim() + "/" + dbName.trim();
        if (dbSchema != null && !dbSchema.isBlank()) {
            return base + "?currentSchema=" + dbSchema.trim();
        }
        return base;
    }

    /** 生产环境用环境变量覆盖敏感项（K8s Secret / compose env），不写入镜像。 */
    static void applyEnvironmentOverrides(GatewayConfig c) {
        envInt("GISO_PORT").ifPresent(v -> c.port = v);
        env("GISO_SCHEMA_DIR").ifPresent(v -> c.schemaDir = v);
        env("GISO_KAFKA_BOOTSTRAP").ifPresent(v -> c.kafkaBootstrapServers = v);
        env("GISO_KAFKA_TOPIC_RAW").ifPresent(v -> c.kafkaTopicRaw = v);
        env("GISO_KAFKA_TOPIC_RAW_TEST").ifPresent(v -> c.kafkaTopicRawTest = v);
        env("GISO_KAFKA_TOPIC_QUARANTINE").ifPresent(v -> c.kafkaTopicQuarantine = v);
        env("GISO_KAFKA_SPILL_DIR").ifPresent(v -> c.kafkaSpillDir = v);
        envCsv("GISO_APP_KEYS").ifPresent(v -> c.appKeys = v);
        env("GISO_ADMIN_USER").ifPresent(v -> c.adminUser = v);
        env("GISO_ADMIN_PASSWORD").ifPresent(v -> c.adminPassword = v);
        env("GISO_VIEWER_USER").ifPresent(v -> c.viewerUser = v);
        env("GISO_VIEWER_PASSWORD").ifPresent(v -> c.viewerPassword = v);
        envInt("GISO_RATE_LIMIT_RPS").ifPresent(v -> c.rateLimitRps = v);
        envInt("GISO_RATE_LIMIT_BURST").ifPresent(v -> c.rateLimitBurst = v);
        envLong("GISO_MAX_BODY_BYTES").ifPresent(v -> c.maxBodyBytes = v);
        env("GISO_CLICKHOUSE_URL").ifPresent(v -> c.clickhouseUrl = v);
        envCsv("GISO_SINKS").ifPresent(v -> c.sinks = v);
        env("GISO_REGISTRY_BACKEND").ifPresent(v -> c.registryBackend = v);
        env("GISO_DB_URL").ifPresent(v -> c.dbUrl = v);
        env("GISO_DB_HOST").ifPresent(v -> c.dbHost = v);
        env("GISO_DB_PORT").ifPresent(v -> c.dbPort = v);
        env("GISO_DB_NAME").ifPresent(v -> c.dbName = v);
        env("GISO_DB_USER").ifPresent(v -> c.dbUser = v);
        env("GISO_DB_PASSWORD").ifPresent(v -> c.dbPassword = v);
        env("GISO_DB_SCHEMA").ifPresent(v -> c.dbSchema = v);
        envInt("GISO_REGISTRY_POLL_SEC").ifPresent(v -> c.registryPollIntervalSec = v);
        applyKafkaSaslFromEnv(c);
    }

    /** MSK SASL/SCRAM：用户名密码走环境变量，组装 sasl.jaas.config（勿写入镜像/ConfigMap）。 */
    static void applyKafkaSaslFromEnv(GatewayConfig c) {
        env("GISO_KAFKA_SASL_JAAS").ifPresent(jaas -> {
            var props = new HashMap<>(c.kafkaProperties);
            props.put("sasl.jaas.config", jaas);
            c.kafkaProperties = props;
        });
        var user = env("GISO_KAFKA_SASL_USERNAME");
        var pass = env("GISO_KAFKA_SASL_PASSWORD");
        if (user.isEmpty() || pass.isEmpty()) return;

        var props = new HashMap<>(c.kafkaProperties);
        props.putIfAbsent("security.protocol", "SASL_SSL");
        props.putIfAbsent("sasl.mechanism", "SCRAM-SHA-512");
        props.put("sasl.jaas.config",
                "org.apache.kafka.common.security.scram.ScramLoginModule required "
                        + "username=\"" + jaasEscape(user.get()) + "\" "
                        + "password=\"" + jaasEscape(pass.get()) + "\";");
        c.kafkaProperties = props;
    }

    private static String jaasEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static java.util.Optional<String> env(String key) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? java.util.Optional.empty() : java.util.Optional.of(v.trim());
    }

    private static java.util.Optional<Integer> envInt(String key) {
        return env(key).map(Integer::parseInt);
    }

    private static java.util.Optional<Long> envLong(String key) {
        return env(key).map(Long::parseLong);
    }

    private static java.util.Optional<List<String>> envCsv(String key) {
        return env(key).map(v -> java.util.Arrays.stream(v.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }
}
