package com.giso.gateway;

import com.giso.gateway.auth.AdminUser;
import com.giso.gateway.debug.RedisConnections;
import com.giso.gateway.auth.LoginSecurityConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    public String dbSchema = "public";
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

    // s3 sink（湖仓 Bronze 归档，对齐 GIDO deployment S3 warehouse）
    public String s3Bucket = "";
    public String s3Prefix = "giso/";
    public String s3Region = "ap-southeast-1";
    public String s3Endpoint = "";
    public String s3BufferDir = "./data/s3-buffer";
    public long s3FlushBytes = 5 * 1024 * 1024;
    public String s3AccessKey = "";
    public String s3SecretKey = "";

    /** 管理页环形缓冲条数 */
    public int adminRecentBuffer = 2000;
    /** 联调缓冲：memory（单副本）| redis（多副本共享） */
    public String debugBufferBackend = "memory";
    public String debugRedisUrl = "";
    /** 解析后的 Redis 连接（避免 URI 对密码特殊字符解析失败） */
    public RedisConnections.Info debugRedisInfo;
    public String debugRedisHost = "";
    public String debugRedisPassword = "";
    public int debugRedisPort = 6379;
    public int debugRedisDb = 2;
    /** 短主机名（如 internal-redis）扩成 K8s 集群 DNS 的命名空间后缀 */
    public String debugRedisSearchNamespace = "";
    public String debugRedisKeyPrefix = "giso:debug";
    public int debugBufferTtlSec = 3600;
    /** 注册表预览图存储目录（默认 {file.dir}/screenshots） */
    public String screenshotsDir = "";

    // ── 安全 ──
    /** 上报鉴权：X-App-Key 白名单；空列表 = 不校验（仅限本地开发） */
    public List<String> appKeys = List.of();
    /** 管理控制台 Basic Auth；user 为空 = 不开启（仅限本地开发） */
    public String adminUser = "";
    public String adminPassword = "";
    /** 只读账号（看联调/统计/注册表，不能改注册表/清缓冲）；空 = 不提供只读账号 */
    public String viewerUser = "";
    public String viewerPassword = "";
    /**
     * 扩展多账号（yaml auth.users 或 GISO_ADMIN_USERS）。
     * 生产 postgres 模式下仅作<strong>首次种子</strong>，落库后改 Doppler 不会自动覆盖已有用户。
     */
    public List<Map<String, String>> authUsers = List.of();
    /** 管理台登录防暴力破解（生产建议开启，见 auth.login_security）。 */
    public LoginSecurityConfig loginSecurity = new LoginSecurityConfig();

    // ── 防护 ──
    /** 单次请求 body 解压后字节上限 */
    public long maxBodyBytes = 1024 * 1024;
    /** 单 IP 每秒请求数上限（令牌桶速率）；0 = 不限流 */
    public int rateLimitRps = 0;
    /** 令牌桶容量（突发上限），默认 2×rps */
    public int rateLimitBurst = 0;

    /** SDK 远程配置（GET /v1/config 下发，客户端覆盖本地默认口径） */
    public Map<String, Object> sdkConfig = defaultSdkConfig();

    static Map<String, Object> defaultSdkConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exposure_ratio", 0.5);
        m.put("exposure_duration_ms", 500);
        m.put("exposure_max_per_page", 3);
        m.put("batch_size", 20);
        m.put("flush_interval_ms", 15000);
        m.put("event_sample_rates", new LinkedHashMap<String, Object>());
        m.put("events_disabled", List.<String>of());
        return m;
    }

    /** ClickHouse HTTP 地址（管理台覆盖率反算）；空 = 仅用进程内累计 */
    public String clickhouseUrl = "";

    // ── Copilot（产品 / 埋点流程答疑，插拔式 provider）──
    public boolean assistantEnabled = true;
    /** doc（本地 FAQ 检索，默认）| openai | gido_proxy */
    public String assistantProvider = "doc";
    public List<String> assistantDocsDirs = List.of("../../docs/tracking", "../../docs/en");
    /** classpath 兜底语料目录（相对 resources） */
    public String assistantCorpusClasspath = "copilot/corpus";
    public int assistantMaxChunks = 5;
    public String assistantSystemPrompt = defaultAssistantPrompt();
    public String openAiBaseUrl = "https://ws-df7ipa997hhtkd8h.cn-beijing.maas.aliyuncs.com/compatible-mode/v1";
    public String openAiApiKey = "";
    public String openAiModel = "qwen-plus";
    /** GIDO 平台 Copilot 代理 URL（deployment 侧服务） */
    public String gidoProxyUrl = "";

    static String defaultAssistantPrompt() {
        return """
                你是 GISO 玑源 Copilot，GIDO 数据产品族中的埋点治理助手。
                职责：解答产品功能、埋点登记、上报协议、App Key、空间隔离、隔离区、Doris 链路等。
                回答须基于提供的文档片段与登记概况；不确定时明确说明并指向 docs/tracking/08-接入常见问题FAQ.md。
                使用简洁中文，步骤用有序列表。""";
    }

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

        Map<String, Object> s3 = (Map<String, Object>) doc.getOrDefault("s3", Map.of());
        c.s3Bucket = (String) s3.getOrDefault("bucket", c.s3Bucket);
        c.s3Prefix = (String) s3.getOrDefault("prefix", c.s3Prefix);
        c.s3Region = (String) s3.getOrDefault("region", c.s3Region);
        c.s3Endpoint = (String) s3.getOrDefault("endpoint", c.s3Endpoint);
        c.s3BufferDir = (String) s3.getOrDefault("buffer_dir", c.s3BufferDir);
        if (s3.get("flush_bytes") instanceof Number n) c.s3FlushBytes = n.longValue();

        Map<String, Object> admin = (Map<String, Object>) doc.getOrDefault("admin", Map.of());
        c.adminRecentBuffer = (int) admin.getOrDefault("recent_buffer", c.adminRecentBuffer);
        c.screenshotsDir = (String) admin.getOrDefault("screenshots_dir", c.screenshotsDir);

        Map<String, Object> debugBuf = (Map<String, Object>) doc.getOrDefault("debug_buffer", Map.of());
        c.debugBufferBackend = (String) debugBuf.getOrDefault("backend", c.debugBufferBackend);
        c.debugRedisUrl = (String) debugBuf.getOrDefault("redis_url", c.debugRedisUrl);
        c.debugRedisHost = (String) debugBuf.getOrDefault("redis_host", c.debugRedisHost);
        c.debugRedisPassword = (String) debugBuf.getOrDefault("redis_password", c.debugRedisPassword);
        if (debugBuf.get("redis_port") instanceof Number n) c.debugRedisPort = n.intValue();
        if (debugBuf.get("redis_db") instanceof Number n) c.debugRedisDb = n.intValue();
        c.debugRedisSearchNamespace = (String) debugBuf.getOrDefault(
                "redis_search_namespace", c.debugRedisSearchNamespace);
        c.debugRedisKeyPrefix = (String) debugBuf.getOrDefault("key_prefix", c.debugRedisKeyPrefix);
        if (debugBuf.get("recent_max") instanceof Number n) c.adminRecentBuffer = n.intValue();
        if (debugBuf.get("ttl_sec") instanceof Number n) c.debugBufferTtlSec = n.intValue();

        Map<String, Object> auth = (Map<String, Object>) doc.getOrDefault("auth", Map.of());
        if (auth.get("app_keys") instanceof List<?> keys) c.appKeys = (List<String>) keys;
        c.adminUser = (String) auth.getOrDefault("admin_user", c.adminUser);
        c.adminPassword = (String) auth.getOrDefault("admin_password", c.adminPassword);
        c.viewerUser = (String) auth.getOrDefault("viewer_user", c.viewerUser);
        c.viewerPassword = (String) auth.getOrDefault("viewer_password", c.viewerPassword);
        if (auth.get("users") instanceof List<?> users) {
            List<Map<String, String>> parsed = new ArrayList<>();
            for (Object o : users) {
                if (!(o instanceof Map<?, ?> raw)) continue;
                Map<String, Object> m = new HashMap<>();
                raw.forEach((k, v) -> m.put(String.valueOf(k), v));
                String u = String.valueOf(m.getOrDefault("username", "")).trim();
                String p = String.valueOf(m.getOrDefault("password", ""));
                String r = String.valueOf(m.getOrDefault("role", AdminUser.ROLE_ADMIN)).trim();
                if (!u.isEmpty() && !p.isEmpty()) {
                    parsed.add(Map.of("username", u, "password", p, "role", r));
                }
            }
            c.authUsers = parsed;
        }
        if (auth.get("login_security") instanceof Map<?, ?> ls) {
            Map<String, Object> m = new HashMap<>();
            ls.forEach((k, v) -> m.put(String.valueOf(k), v));
            LoginSecurityConfig sec = c.loginSecurity;
            if (m.get("enabled") instanceof Boolean b) sec.enabled = b;
            if (m.get("max_attempts_per_user") instanceof Number n) sec.maxAttemptsPerUser = n.intValue();
            if (m.get("lockout_minutes") instanceof Number n) sec.lockoutMinutes = n.intValue();
            if (m.get("attempt_window_minutes") instanceof Number n) sec.attemptWindowMinutes = n.intValue();
            if (m.get("max_attempts_per_ip") instanceof Number n) sec.maxAttemptsPerIp = n.intValue();
            if (m.get("ip_window_minutes") instanceof Number n) sec.ipWindowMinutes = n.intValue();
            if (m.get("ip_block_minutes") instanceof Number n) sec.ipBlockMinutes = n.intValue();
            if (m.get("delay_ms_per_failure") instanceof Number n) sec.delayMsPerFailure = n.intValue();
        }

        Map<String, Object> limits = (Map<String, Object>) doc.getOrDefault("limits", Map.of());
        c.maxBodyBytes = ((Number) limits.getOrDefault("max_body_bytes", c.maxBodyBytes)).longValue();
        c.rateLimitRps = (int) limits.getOrDefault("rate_limit_rps", c.rateLimitRps);
        c.rateLimitBurst = (int) limits.getOrDefault("rate_limit_burst",
                c.rateLimitRps > 0 ? c.rateLimitRps * 2 : 0);

        if (doc.get("sdk_config") instanceof Map<?, ?> s) {
            Map<String, Object> merged = new LinkedHashMap<>(defaultSdkConfig());
            merged.putAll((Map<String, Object>) s);
            c.sdkConfig = merged;
        }

        Map<String, Object> ch = (Map<String, Object>) doc.getOrDefault("clickhouse", Map.of());
        c.clickhouseUrl = (String) ch.getOrDefault("url", c.clickhouseUrl);

        Map<String, Object> asst = (Map<String, Object>) doc.getOrDefault("assistant", Map.of());
        if (asst.get("enabled") instanceof Boolean b) c.assistantEnabled = b;
        c.assistantProvider = (String) asst.getOrDefault("provider", c.assistantProvider);
        if (asst.get("docs_dirs") instanceof List<?> dirs) c.assistantDocsDirs = (List<String>) dirs;
        c.assistantCorpusClasspath = (String) asst.getOrDefault("corpus_classpath", c.assistantCorpusClasspath);
        if (asst.get("max_chunks") instanceof Number n) c.assistantMaxChunks = n.intValue();
        c.assistantSystemPrompt = (String) asst.getOrDefault("system_prompt", c.assistantSystemPrompt);
        Map<String, Object> oai = (Map<String, Object>) asst.getOrDefault("openai", Map.of());
        c.openAiBaseUrl = (String) oai.getOrDefault("base_url", c.openAiBaseUrl);
        c.openAiModel = (String) oai.getOrDefault("model", c.openAiModel);
        c.gidoProxyUrl = (String) asst.getOrDefault("gido_proxy_url", c.gidoProxyUrl);

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

    /** JDBC URL：优先 dbUrl，否则由 host/port/database 拼装。支持 Doppler 的 postgresql:// 格式。 */
    public String jdbcUrl() {
        if (dbUrl != null && !dbUrl.isBlank()) {
            String u = dbUrl.trim();
            if (u.startsWith("postgresql://")) return "jdbc:" + u;
            return u;
        }
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
        env("GISO_ADMIN_USERS").ifPresent(v -> c.authUsers = parseAdminUsersEnv(v));
        envBool("GISO_LOGIN_SECURITY_ENABLED").ifPresent(v -> c.loginSecurity.enabled = v);
        envInt("GISO_LOGIN_MAX_ATTEMPTS_USER").ifPresent(v -> c.loginSecurity.maxAttemptsPerUser = v);
        envInt("GISO_LOGIN_LOCKOUT_MINUTES").ifPresent(v -> c.loginSecurity.lockoutMinutes = v);
        envInt("GISO_LOGIN_ATTEMPT_WINDOW_MINUTES").ifPresent(v -> c.loginSecurity.attemptWindowMinutes = v);
        envInt("GISO_LOGIN_MAX_ATTEMPTS_IP").ifPresent(v -> c.loginSecurity.maxAttemptsPerIp = v);
        envInt("GISO_LOGIN_IP_WINDOW_MINUTES").ifPresent(v -> c.loginSecurity.ipWindowMinutes = v);
        envInt("GISO_LOGIN_IP_BLOCK_MINUTES").ifPresent(v -> c.loginSecurity.ipBlockMinutes = v);
        envInt("GISO_LOGIN_DELAY_MS").ifPresent(v -> c.loginSecurity.delayMsPerFailure = v);
        envInt("GISO_RATE_LIMIT_RPS").ifPresent(v -> c.rateLimitRps = v);
        envInt("GISO_RATE_LIMIT_BURST").ifPresent(v -> c.rateLimitBurst = v);
        envLong("GISO_MAX_BODY_BYTES").ifPresent(v -> c.maxBodyBytes = v);
        env("GISO_CLICKHOUSE_URL").ifPresent(v -> c.clickhouseUrl = v);
        envCsv("GISO_SINKS").ifPresent(v -> c.sinks = v);
        envBool("GISO_ASSISTANT_ENABLED").ifPresent(v -> c.assistantEnabled = v);
        env("GISO_ASSISTANT_PROVIDER").ifPresent(v -> c.assistantProvider = v);
        envCsv("GISO_ASSISTANT_DOCS_DIRS").ifPresent(v -> c.assistantDocsDirs = v);
        env("GISO_LLM_API_KEY").ifPresent(v -> c.openAiApiKey = v);
        env("GISO_LLM_BASE_URL").ifPresent(v -> c.openAiBaseUrl = v);
        env("GISO_LLM_MODEL").ifPresent(v -> c.openAiModel = v);
        env("GISO_GIDO_COPILOT_URL").ifPresent(v -> c.gidoProxyUrl = v);
        env("GISO_S3_BUCKET").ifPresent(v -> c.s3Bucket = v);
        env("GISO_S3_PREFIX").ifPresent(v -> c.s3Prefix = v);
        env("GISO_S3_REGION").ifPresent(v -> c.s3Region = v);
        env("GISO_S3_ENDPOINT").ifPresent(v -> c.s3Endpoint = v);
        env("GISO_S3_BUFFER_DIR").ifPresent(v -> c.s3BufferDir = v);
        envLong("GISO_S3_FLUSH_BYTES").ifPresent(v -> c.s3FlushBytes = v);
        env("GISO_AWS_ACCESS_KEY_ID").ifPresent(v -> c.s3AccessKey = v);
        env("GISO_AWS_SECRET_ACCESS_KEY").ifPresent(v -> c.s3SecretKey = v);
        env("GISO_REGISTRY_BACKEND").ifPresent(v -> c.registryBackend = v);
        env("GISO_DB_URL").ifPresent(v -> c.dbUrl = v);
        env("GISO_DB_HOST").ifPresent(v -> c.dbHost = v);
        env("GISO_DB_PORT").ifPresent(v -> c.dbPort = v);
        env("GISO_DB_NAME").ifPresent(v -> c.dbName = v);
        env("GISO_DB_USER").ifPresent(v -> c.dbUser = v);
        env("GISO_DB_PASSWORD").ifPresent(v -> c.dbPassword = v);
        env("GISO_DB_SCHEMA").ifPresent(v -> c.dbSchema = v);
        envInt("GISO_REGISTRY_POLL_SEC").ifPresent(v -> c.registryPollIntervalSec = v);
        env("GISO_DEBUG_BUFFER_BACKEND").ifPresent(v -> c.debugBufferBackend = v);
        env("GISO_DEBUG_REDIS_URL").ifPresent(v -> c.debugRedisUrl = v);
        env("GISO_DEBUG_REDIS_HOST").ifPresent(v -> c.debugRedisHost = v);
        env("GISO_DEBUG_REDIS_PASSWORD").ifPresent(v -> c.debugRedisPassword = v);
        envInt("GISO_DEBUG_REDIS_PORT").ifPresent(v -> c.debugRedisPort = v);
        envInt("GISO_DEBUG_REDIS_DB").ifPresent(v -> c.debugRedisDb = v);
        env("GISO_DEBUG_REDIS_SEARCH_NAMESPACE").ifPresent(v -> c.debugRedisSearchNamespace = v);
        env("GISO_DEBUG_REDIS_KEY_PREFIX").ifPresent(v -> c.debugRedisKeyPrefix = v);
        envInt("GISO_DEBUG_BUFFER_TTL_SEC").ifPresent(v -> c.debugBufferTtlSec = v);
        resolveDebugRedisUrl(c);
        applyKafkaSaslFromEnv(c);
    }

    /**
     * 未配置 redis_url 时，由 host + password + port + db 拼装（复用平台 INFRA_ARCHERY_REDIS_* 等）。
     * host 若已是 redis:// / rediss:// 整 URL，则解析后只改 logical db。
     */
    static void resolveDebugRedisUrl(GatewayConfig c) {
        if (c.debugRedisInfo != null) return;

        int db = c.debugRedisDb >= 0 ? c.debugRedisDb : 2;

        if (c.debugRedisUrl != null && !c.debugRedisUrl.isBlank()) {
            c.debugRedisInfo = RedisConnections.parseUrl(c.debugRedisUrl, null, -1);
            return;
        }

        if (c.debugRedisHost == null || c.debugRedisHost.isBlank()) return;
        String hostRaw = c.debugRedisHost.trim();

        if (RedisConnections.isRedisUri(hostRaw)) {
            c.debugRedisInfo = RedisConnections.parseUrl(hostRaw, c.debugRedisPassword, db);
            return;
        }

        if (c.debugRedisPassword == null || c.debugRedisPassword.isBlank()) return;
        String host = normalizeRedisHost(hostRaw, c.debugRedisSearchNamespace);
        int port = c.debugRedisPort > 0 ? c.debugRedisPort : 6379;
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(']') < 0) {
            try {
                port = Integer.parseInt(host.substring(colon + 1));
                host = host.substring(0, colon);
            } catch (NumberFormatException ignored) { }
        }
        c.debugRedisInfo = RedisConnections.fromParts("redis", host, c.debugRedisPassword, port, db);
    }

    static String normalizeRedisHost(String host, String searchNamespace) {
        if (RedisConnections.isRedisUri(host) || host.contains(".") || host.contains("svc.cluster.local")) {
            return host;
        }
        if (searchNamespace != null && !searchNamespace.isBlank()) {
            return host + "." + searchNamespace.trim() + ".svc.cluster.local";
        }
        return host;
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

    private static java.util.Optional<Boolean> envBool(String key) {
        return env(key).map(v -> v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes"));
    }

    private static java.util.Optional<List<String>> envCsv(String key) {
        return env(key).map(v -> java.util.Arrays.stream(v.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }

    /** 合并 legacy admin/viewer 与 auth.users，供配置种子或 PG bootstrap。 */
    public static List<AdminUser> resolveAuthUsers(GatewayConfig c) {
        Map<String, AdminUser> byName = new LinkedHashMap<>();
        if (c.adminUser != null && !c.adminUser.isBlank() && c.adminPassword != null) {
            byName.put(c.adminUser, new AdminUser(c.adminUser, c.adminPassword, AdminUser.ROLE_ADMIN));
        }
        if (c.viewerUser != null && !c.viewerUser.isBlank() && c.viewerPassword != null) {
            byName.put(c.viewerUser, new AdminUser(c.viewerUser, c.viewerPassword, AdminUser.ROLE_VIEWER));
        }
        for (Map<String, String> m : c.authUsers) {
            String u = m.get("username");
            String p = m.get("password");
            String r = m.getOrDefault("role", AdminUser.ROLE_ADMIN);
            if (u != null && p != null && !u.isBlank()) {
                byName.put(u, new AdminUser(u, p, r));
            }
        }
        return List.copyOf(byName.values());
    }

    private static List<Map<String, String>> parseAdminUsersEnv(String raw) {
        List<Map<String, String>> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String[] seg = part.trim().split(":", 3);
            if (seg.length < 2 || seg[0].isBlank()) continue;
            out.add(Map.of(
                    "username", seg[0].trim(),
                    "password", seg[1],
                    "role", seg.length > 2 ? seg[2].trim() : AdminUser.ROLE_ADMIN));
        }
        return out;
    }
}
