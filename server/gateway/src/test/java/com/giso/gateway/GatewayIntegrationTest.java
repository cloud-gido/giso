package com.giso.gateway;

import com.giso.gateway.auth.AdminAuth;
import com.giso.gateway.registry.RegistryWatcher;
import com.giso.gateway.settings.SystemSettingsService;
import com.giso.gateway.sink.SinkFactory;
import com.giso.gateway.sink.SinkRegistry;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 网关 + 管理 API 端到端（内存 HttpExchange，无真实 socket）。 */
class GatewayIntegrationTest {
    private static Registry registry;
    private static EventStore store;
    private static GatewayConfig config;
    private static HttpHandler track;
    private static HttpHandler admin;

    @BeforeAll
    static void setup() throws Exception {
        config = new GatewayConfig();
        config.schemaDir = "../../schema";
        config.registryBackend = "yaml";
        config.appKeys = List.of("test-key");
        config.sinks = List.of("file");
        config.fileDir = Files.createTempDirectory("giso-it").toString();
        config.adminRecentBuffer = 200;
        config.adminUser = "";
        config.adminPassword = "";

        registry = Registry.create(config);
        AdminAuth auth = new AdminAuth(config, null, null);
        RegistryWatcher watcher = new RegistryWatcher(registry, registry.store(), 60);
        watcher.start();
        SinkRegistry sinkRegistry = new SinkRegistry();
        sinkRegistry.reload(config);
        SystemSettingsService systemSettings = SystemSettingsService.create(config, null, sinkRegistry);
        store = new EventStore(sinkRegistry, config.adminRecentBuffer);
        SseHub sse = new SseHub();
        track = new TrackHandler(registry, store, sse, config, null);
        admin = new AdminHandler(registry, store, sse, auth, config, null, systemSettings);
    }

    @AfterAll
    static void teardown() {
        // no-op
    }

    @Test
    void trackValidEventReturns204() throws Exception {
        String body = """
            [{"event":"app_launch","log_id":"it-1",
              "common":{"app_id":"web","platform":"web","did":"d-it-1"}}]""";
        var ex = exchange("POST", "/v1/track", body, Map.of("X-App-Key", "test-key"));
        track.handle(ex);
        assertEquals(204, ex.status);
    }

    @Test
    void trackInvalidAppKeyReturns401() throws Exception {
        var ex = exchange("POST", "/v1/track", "[]", Map.of("X-App-Key", "bad-key"));
        track.handle(ex);
        assertEquals(401, ex.status);
    }

    @Test
    void trackUnregisteredPageMarksErrorInBuffer() throws Exception {
        String body = """
            [{"event":"page_enter","log_id":"it-2",
              "common":{"app_id":"web","platform":"web","did":"d-it-2"},
              "page":{"pgid":"no_such_page_xyz"}}]""";
        var ex = exchange("POST", "/v1/track", body, Map.of("X-App-Key", "test-key"));
        track.handle(ex);
        assertEquals(204, ex.status);

        var evEx = exchange("GET", "/admin/api/events?limit=5&status=error", "", Map.of("X-GISO-Space", "default"));
        admin.handle(evEx);
        assertTrue(evEx.responseBody().contains("no_such_page_xyz"), evEx.responseBody());
    }

    @Test
    void adminRegistryListsPages() throws Exception {
        var ex = exchange("GET", "/admin/api/registry", "", Map.of("X-GISO-Space", "default"));
        admin.handle(ex);
        assertEquals(200, ex.status);
        assertTrue(ex.responseBody().contains("\"pages\""));
        assertTrue(ex.responseBody().contains("home"));
    }

    private static RecordingExchange exchange(String method, String path, String body, Map<String, String> headers)
            throws Exception {
        var ex = new RecordingExchange(method, URI.create("http://127.0.0.1" + path), body);
        headers.forEach(ex.requestHeaders::set);
        return ex;
    }

    private static final class RecordingExchange extends HttpExchange {
        private final String method;
        private final URI uri;
        private final byte[] body;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        int status = -1;

        RecordingExchange(String method, URI uri, String body) {
            this.method = method;
            this.uri = uri;
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        String responseBody() {
            return responseBody.toString(StandardCharsets.UTF_8);
        }

        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public URI getRequestURI() { return uri; }
        @Override public String getRequestMethod() { return method; }
        @Override public com.sun.net.httpserver.HttpContext getHttpContext() { return null; }
        @Override public void close() { }
        @Override public InputStream getRequestBody() { return new ByteArrayInputStream(body); }
        @Override public OutputStream getResponseBody() { return responseBody; }
        @Override public void sendResponseHeaders(int rCode, long responseLength) {
            status = rCode;
        }
        @Override public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 54321);
        }
        @Override public int getResponseCode() { return status; }
        @Override public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 8080);
        }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) { }
        @Override public void setStreams(InputStream i, OutputStream o) { }
        @Override public com.sun.net.httpserver.HttpPrincipal getPrincipal() { return null; }
    }
}
