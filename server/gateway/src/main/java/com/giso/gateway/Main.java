package com.giso.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giso.gateway.auth.AdminAuth;
import com.giso.gateway.registry.PostgresRegistryStore;
import com.giso.gateway.registry.RegistryWatcher;
import com.giso.gateway.sink.EventSink;
import com.giso.gateway.sink.SinkFactory;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 埋点接入网关 + 管理控制台。
 *
 * 端点：
 *   POST /v1/track            事件上报（批量 JSON，支持 gzip）
 *   GET  /v1/config           SDK 远程配置下发（曝光口径、攒批参数）
 *   GET  /admin/              管理页面（注册表配置 / 实时联调 / 质量统计）
 *   GET  /admin/api/...       管理 REST API
 *
 * 启动：java -jar giso-gateway.jar [--config gateway.yaml] [--port 8080] [--schema ../../schema]
 * 出口管道由 gateway.yaml 的 sinks 配置（file / kafka，可多路双写），见 sink/ 包。
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        Path configFile = Path.of("gateway.yaml");
        Integer portOverride = null;
        String schemaOverride = null;
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--config" -> configFile = Path.of(args[i + 1]);
                case "--port" -> portOverride = Integer.parseInt(args[i + 1]);
                case "--schema" -> schemaOverride = args[i + 1];
            }
        }

        GatewayConfig config = GatewayConfig.load(configFile);
        if (portOverride != null) config.port = portOverride;
        if (schemaOverride != null) config.schemaDir = schemaOverride;

        Registry registry = Registry.create(config);
        PostgresRegistryStore pgStore = registry.store() instanceof PostgresRegistryStore pg ? pg : null;
        AdminAuth adminAuth = new AdminAuth(config, pgStore);
        RegistryWatcher registryWatcher = new RegistryWatcher(
                registry, registry.store(), config.registryPollIntervalSec);
        registryWatcher.start();
        List<EventSink> sinks = SinkFactory.create(config);
        EventStore store = new EventStore(sinks, config.adminRecentBuffer);
        SseHub sse = new SseHub();

        String sdkConfigJson = new ObjectMapper().writeValueAsString(config.sdkConfig);

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/v1/track", new TrackHandler(registry, store, sse, config));
        server.createContext("/v1/config", ex -> {
            if (!Http.handlePreflight(ex)) Http.json(ex, 200, sdkConfigJson);
        });
        server.createContext("/health", ex -> {
            if (!ex.getRequestMethod().equals("GET")) {
                Http.empty(ex, 405);
                return;
            }
            String body = new ObjectMapper().writeValueAsString(Map.of(
                    "status", "ok",
                    "registry", Map.of(
                            "backend", registry.backendName(),
                            "revision", registry.globalRevision(),
                            "entries", registry.entryCount()),
                    "auth", Map.of(
                            "enabled", adminAuth.authEnabled(),
                            "backend", pgStore != null ? "postgres" : "config")));
            Http.json(ex, 200, body);
        });
        server.createContext("/ready", ex -> {
            if (!ex.getRequestMethod().equals("GET")) {
                Http.empty(ex, 405);
                return;
            }
            if (!registry.registryReady()) {
                Http.json(ex, 503, "{\"status\":\"not_ready\",\"reason\":\"registry\"}");
                return;
            }
            Http.json(ex, 200, "{\"status\":\"ready\"}");
        });
        server.createContext("/metrics", ex -> {
            if (!ex.getRequestMethod().equals("GET")) {
                Http.empty(ex, 405);
                return;
            }
            byte[] body = Metrics.render().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/admin/api", new AdminHandler(registry, store, sse, adminAuth, config));
        server.createContext("/admin", new StaticHandler(adminAuth));
        server.createContext("/", new HubHandler());
        server.start();

        // 优雅停机：停收新请求 → 最多等 3s 在途请求处理完 → flush 并关闭 sink
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("shutting down: draining in-flight requests...");
            registryWatcher.close();
            server.stop(3);
            sinks.forEach(EventSink::close);
            System.out.println("shutdown complete");
        }, "graceful-shutdown"));

        System.out.println("giso-gateway listening on http://localhost:" + config.port);
        System.out.println("  product hub   : http://localhost:" + config.port + "/");
        System.out.println("  admin console : http://localhost:" + config.port + "/admin/");
        System.out.println("  registry      : " + registry.backendName()
                + " (revision=" + registry.globalRevision()
                + ", entries=" + registry.entryCount() + ")");
        for (EventSink sink : sinks) {
            System.out.println("  sink          : " + sink.name());
        }
    }
}
