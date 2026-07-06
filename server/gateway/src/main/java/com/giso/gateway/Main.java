package com.giso.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giso.gateway.auth.AdminAuth;
import com.giso.gateway.registry.PostgresRegistryStore;
import com.giso.gateway.registry.RegistryWatcher;
import com.giso.gateway.settings.PostgresSystemSettingsStore;
import com.giso.gateway.settings.SystemSettingsService;
import com.giso.gateway.settings.SystemSettingsStore;
import com.giso.gateway.debug.DebugBuffers;
import com.giso.gateway.sink.EventSink;
import com.giso.gateway.sink.SinkRegistry;
import com.giso.gateway.space.SpaceService;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 埋点接入网关 + 管理控制台。
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
        SpaceService spaces = pgStore != null ? SpaceService.create(pgStore.dataSource(), config) : null;
        AdminAuth adminAuth = new AdminAuth(config, pgStore, spaces);
        RegistryWatcher registryWatcher = new RegistryWatcher(
                registry, registry.store(), config.registryPollIntervalSec);
        registryWatcher.start();

        SinkRegistry sinkRegistry = new SinkRegistry();
        SystemSettingsStore settingsStore = pgStore != null
                ? new PostgresSystemSettingsStore(pgStore.dataSource(), config.dbSchema) : null;
        SystemSettingsService systemSettings = SystemSettingsService.create(config, settingsStore, sinkRegistry);

        SseHub sse = new SseHub();
        DebugBuffers.Handle debugBuffers = DebugBuffers.create(config, sse);
        EventStore store = new EventStore(sinkRegistry, debugBuffers.buffer());
        ScreenshotStore screenshots = ScreenshotStore.create(config);

        String sdkConfigJson = new ObjectMapper().writeValueAsString(config.sdkConfig);

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/v1/track", new TrackHandler(registry, store, config, spaces));
        server.createContext("/v1/config", ex -> {
            if (!Http.handlePreflight(ex)) Http.json(ex, 200, sdkConfigJson);
        });
        server.createContext("/health", ex -> {
            if (!ex.getRequestMethod().equals("GET")) {
                Http.empty(ex, 405);
                return;
            }
            var health = new java.util.LinkedHashMap<String, Object>();
            health.put("status", "ok");
            health.put("instance_id", GatewayInstance.id());
            health.put("debug_buffer", store.debugBuffer().backendName());
            health.put("registry", Map.of(
                    "backend", registry.backendName(),
                    "revision", registry.globalRevision(),
                    "entries", registry.entryCount()));
            health.put("auth", Map.of(
                    "enabled", adminAuth.authEnabled(),
                    "backend", pgStore != null ? "postgres" : "config",
                    "login_security", adminAuth.loginSecurity().enabled()));
            health.put("sinks", sinkRegistry.activeNames());
            if (pgStore != null) {
                try {
                    health.put("db_migrations", new DbMigrator(
                            pgStore.dataSource(), config.dbSchema, PostgresRegistryStore.class)
                            .appliedVersions());
                } catch (Exception e) {
                    health.put("db_migrations_error", e.getMessage());
                }
            }
            Http.json(ex, 200, new ObjectMapper().writeValueAsString(health));
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
        server.createContext("/admin/api",
                new AdminHandler(registry, store, sse, adminAuth, config, spaces, systemSettings, screenshots));
        server.createContext("/admin", new StaticHandler(adminAuth, screenshots));
        server.createContext("/", new HubHandler());
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("shutting down: draining in-flight requests...");
            registryWatcher.close();
            server.stop(3);
            sinkRegistry.close();
            debugBuffers.close();
            System.out.println("shutdown complete");
        }, "graceful-shutdown"));

        System.out.println("giso-gateway listening on http://localhost:" + config.port);
        System.out.println("  product hub   : http://localhost:" + config.port + "/");
        System.out.println("  admin console : http://localhost:" + config.port + "/admin/");
        System.out.println("  registry      : " + registry.backendName()
                + " (revision=" + registry.globalRevision()
                + ", entries=" + registry.entryCount() + ")");
        for (EventSink sink : sinkRegistry.current()) {
            System.out.println("  sink          : " + sink.name());
        }
    }
}
