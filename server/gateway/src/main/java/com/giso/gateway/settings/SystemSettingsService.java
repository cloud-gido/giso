package com.giso.gateway.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.giso.gateway.GatewayConfig;
import com.giso.gateway.assistant.AssistantFactory;
import com.giso.gateway.assistant.AssistantProvider;
import com.giso.gateway.sink.SinkRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 系统设置：YAML/Env 打底 + PostgreSQL 覆盖；支持 Copilot 与出口管道热更新。
 */
public final class SystemSettingsService {
    public static final String KEY_ASSISTANT = "assistant";
    public static final String KEY_SINKS = "sinks";

    private static final ObjectMapper M = new ObjectMapper();
    private static final Set<String> ALLOWED_SINKS = Set.of("file", "kafka", "s3");
    private static final Set<String> ALLOWED_PROVIDERS = Set.of("doc", "openai", "gido_proxy");

    private final GatewayConfig base;
    private final SystemSettingsStore store;
    private final SinkRegistry sinkRegistry;
    private volatile AssistantProvider assistantProvider;

    public SystemSettingsService(GatewayConfig base, SystemSettingsStore store, SinkRegistry sinkRegistry)
            throws Exception {
        this.base = base;
        this.store = store;
        this.sinkRegistry = sinkRegistry;
        reloadFromStore();
    }

    public static SystemSettingsService create(GatewayConfig base, SystemSettingsStore store,
            SinkRegistry sinkRegistry) throws Exception {
        return new SystemSettingsService(base, store, sinkRegistry);
    }

    public boolean writable() { return store != null; }

    public AssistantProvider assistantProvider() { return assistantProvider; }

    public GatewayConfig effectiveConfig() throws Exception {
        GatewayConfig c = cloneBase();
        if (store == null) return c;
        applyAssistantOverlay(c, store.get(KEY_ASSISTANT).orElse(null));
        applySinksOverlay(c, store.get(KEY_SINKS).orElse(null));
        return c;
    }

    public Map<String, Object> getPublicSettings() throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("writable", writable());
        out.put("assistant", publicAssistant());
        out.put("sinks", publicSinks());
        out.put("sink_catalog", sinkCatalog());
        return out;
    }

    public Map<String, Object> update(Map<String, Object> body, String operator) throws Exception {
        if (store == null) {
            throw new IllegalStateException("当前为 yaml 模式，请修改 gateway.yaml 或启用 PostgreSQL");
        }
        if (body.containsKey("assistant")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> a = (Map<String, Object>) body.get("assistant");
            saveAssistant(a, operator);
        }
        if (body.containsKey("sinks")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> s = (Map<String, Object>) body.get("sinks");
            saveSinks(s, operator);
        }
        reloadFromStore();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("active_sinks", sinkRegistry.activeNames());
        result.put("assistant_provider", assistantProvider.name());
        result.put("assistant_ready", assistantProvider.ready());
        return result;
    }

    private void reloadFromStore() throws Exception {
        GatewayConfig eff = effectiveConfig();
        sinkRegistry.reload(eff);
        assistantProvider = AssistantFactory.create(eff);
    }

    private void saveAssistant(Map<String, Object> patch, String operator) throws Exception {
        ObjectNode merged = M.createObjectNode();
        JsonNode existing = store.get(KEY_ASSISTANT).orElse(M.createObjectNode());
        merged.setAll((ObjectNode) existing);

        putBool(merged, patch, "enabled");
        putText(merged, patch, "provider", ALLOWED_PROVIDERS);
        putText(merged, patch, "openai_base_url");
        putText(merged, patch, "openai_model");
        if (patch.containsKey("openai_api_key")) {
            String key = String.valueOf(patch.get("openai_api_key")).trim();
            if (!key.isEmpty() && !key.startsWith("••")) {
                merged.put("openai_api_key", key);
            }
        }
        putText(merged, patch, "gido_proxy_url");

        store.put(KEY_ASSISTANT, merged, operator);
    }

    private void saveSinks(Map<String, Object> patch, String operator) throws Exception {
        ObjectNode merged = M.createObjectNode();
        JsonNode existing = store.get(KEY_SINKS).orElse(M.createObjectNode());
        merged.setAll((ObjectNode) existing);

        if (patch.get("enabled") instanceof List<?> list) {
            List<String> enabled = new ArrayList<>();
            for (Object o : list) {
                String id = String.valueOf(o).trim().toLowerCase();
                if (ALLOWED_SINKS.contains(id)) enabled.add(id);
            }
            if (enabled.isEmpty()) throw new IllegalArgumentException("至少选择一个出口");
            merged.putPOJO("enabled", enabled);
        }
        store.put(KEY_SINKS, merged, operator);
    }

    private Map<String, Object> publicAssistant() throws Exception {
        Map<String, Object> a = new LinkedHashMap<>();
        GatewayConfig eff = effectiveConfig();
        a.put("enabled", eff.assistantEnabled);
        a.put("provider", eff.assistantProvider);
        a.put("openai_base_url", eff.openAiBaseUrl);
        a.put("openai_model", eff.openAiModel);
        a.put("openai_api_key_set", eff.openAiApiKey != null && !eff.openAiApiKey.isBlank());
        a.put("openai_api_key_masked", mask(eff.openAiApiKey));
        a.put("gido_proxy_url", eff.gidoProxyUrl);
        a.put("ready", assistantProvider.ready());
        return a;
    }

    private Map<String, Object> publicSinks() throws Exception {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("enabled", effectiveConfig().sinks);
        s.put("active", sinkRegistry.activeNames());
        s.put("kafka_bootstrap", base.kafkaBootstrapServers);
        s.put("kafka_topics", Map.of(
                "raw", base.kafkaTopicRaw,
                "raw_test", base.kafkaTopicRawTest,
                "quarantine", base.kafkaTopicQuarantine));
        s.put("s3_bucket", base.s3Bucket);
        s.put("s3_prefix", base.s3Prefix);
        return s;
    }

    private static List<Map<String, String>> sinkCatalog() {
        return List.of(
                Map.of("id", "kafka", "title", "Kafka 实时管道",
                        "desc", "主路径：写入 topic → Doris Routine Load → 看板/BI"),
                Map.of("id", "s3", "title", "S3 湖仓归档",
                        "desc", "Bronze JSONL 冷备，供 Paimon/Flink 回补重算（非实时 BI）"),
                Map.of("id", "file", "title", "本地 JSONL",
                        "desc", "开发/调试兜底，生产勿选"));
    }

    private void applyAssistantOverlay(GatewayConfig c, JsonNode node) {
        if (node == null || !node.isObject()) return;
        if (node.has("enabled")) c.assistantEnabled = node.get("enabled").asBoolean();
        if (node.has("provider")) c.assistantProvider = node.get("provider").asText();
        if (node.has("openai_base_url")) c.openAiBaseUrl = node.get("openai_base_url").asText();
        if (node.has("openai_model")) c.openAiModel = node.get("openai_model").asText();
        if (node.has("openai_api_key")) {
            String k = node.get("openai_api_key").asText("");
            if (!k.isBlank()) c.openAiApiKey = k;
        }
        if (node.has("gido_proxy_url")) c.gidoProxyUrl = node.get("gido_proxy_url").asText();
    }

    private void applySinksOverlay(GatewayConfig c, JsonNode node) {
        if (node == null || !node.isObject() || !node.has("enabled")) return;
        List<String> enabled = new ArrayList<>();
        for (JsonNode n : node.get("enabled")) {
            String id = n.asText("").trim().toLowerCase();
            if (ALLOWED_SINKS.contains(id)) enabled.add(id);
        }
        if (!enabled.isEmpty()) c.sinks = enabled;
    }

    private GatewayConfig cloneBase() {
        GatewayConfig c = new GatewayConfig();
        c.port = base.port;
        c.schemaDir = base.schemaDir;
        c.registryBackend = base.registryBackend;
        c.sinks = new ArrayList<>(base.sinks);
        c.fileDir = base.fileDir;
        c.kafkaBootstrapServers = base.kafkaBootstrapServers;
        c.kafkaTopicRaw = base.kafkaTopicRaw;
        c.kafkaTopicRawTest = base.kafkaTopicRawTest;
        c.kafkaTopicQuarantine = base.kafkaTopicQuarantine;
        c.kafkaSpillDir = base.kafkaSpillDir;
        c.kafkaProperties = new LinkedHashMap<>(base.kafkaProperties);
        c.s3Bucket = base.s3Bucket;
        c.s3Prefix = base.s3Prefix;
        c.s3Region = base.s3Region;
        c.s3Endpoint = base.s3Endpoint;
        c.s3BufferDir = base.s3BufferDir;
        c.s3FlushBytes = base.s3FlushBytes;
        c.s3AccessKey = base.s3AccessKey;
        c.s3SecretKey = base.s3SecretKey;
        c.assistantEnabled = base.assistantEnabled;
        c.assistantProvider = base.assistantProvider;
        c.assistantDocsDirs = base.assistantDocsDirs;
        c.assistantCorpusClasspath = base.assistantCorpusClasspath;
        c.assistantMaxChunks = base.assistantMaxChunks;
        c.assistantSystemPrompt = base.assistantSystemPrompt;
        c.openAiBaseUrl = base.openAiBaseUrl;
        c.openAiApiKey = base.openAiApiKey;
        c.openAiModel = base.openAiModel;
        c.gidoProxyUrl = base.gidoProxyUrl;
        return c;
    }

    private static void putBool(ObjectNode target, Map<String, Object> patch, String key) {
        if (patch.containsKey(key)) target.put(key, Boolean.TRUE.equals(patch.get(key))
                || "true".equalsIgnoreCase(String.valueOf(patch.get(key))));
    }

    private static void putText(ObjectNode target, Map<String, Object> patch, String key) {
        putText(target, patch, key, null);
    }

    private static void putText(ObjectNode target, Map<String, Object> patch, String key, Set<String> allowed) {
        if (!patch.containsKey(key)) return;
        String v = String.valueOf(patch.get(key)).trim();
        if (allowed != null && !allowed.contains(v)) {
            throw new IllegalArgumentException("非法 " + key + ": " + v);
        }
        target.put(key, v);
    }

    private static String mask(String secret) {
        if (secret == null || secret.length() < 4) return "";
        return "••••" + secret.substring(secret.length() - 4);
    }
}
