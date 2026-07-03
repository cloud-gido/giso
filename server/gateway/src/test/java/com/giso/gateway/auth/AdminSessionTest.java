package com.giso.gateway.auth;

import com.giso.gateway.GatewayConfig;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 管理台会话：登录签发、同请求 profile、Cookie 解析、退出失效、单端登录。 */
class AdminSessionTest {
    private AdminAuth auth;

    @BeforeEach
    void setup() throws Exception {
        GatewayConfig config = new GatewayConfig();
        config.adminUser = "admin";
        config.adminPassword = "admin123";
        config.registryBackend = "yaml";
        auth = new AdminAuth(config, null, null);
    }

    @Test
    void loginProfileWithoutRequestCookie() throws Exception {
        var loginEx = exchange("POST", "/admin/api/login", "");
        Optional<AuthContext> ctx = auth.login(loginEx, "admin", "admin123");
        assertTrue(ctx.isPresent());
        Map<String, Object> profile = auth.userProfile(ctx.get(), "default", 0);
        assertEquals("admin", profile.get("username"));
        assertNotNull(profile.get("spaces"));
    }

    @Test
    void loginLogoutCycle() throws Exception {
        var loginEx = exchange("POST", "/admin/api/login", "");
        Optional<AuthContext> ctx = auth.login(loginEx, "admin", "admin123");
        assertTrue(ctx.isPresent());

        String cookie = loginEx.responseHeaders.get("Set-Cookie").stream()
                .filter(c -> c.startsWith(AdminSessionCookies.SESSION + "=") && !c.contains("=;"))
                .findFirst()
                .orElse(null);
        assertNotNull(cookie);

        var authed = exchange("GET", "/admin/api/me", "");
        authed.requestHeaders.set("Cookie", cookie.split(";")[0].trim());
        assertEquals("admin", auth.operator(authed));

        var logoutEx = exchange("POST", "/admin/api/logout", "");
        logoutEx.requestHeaders.set("Cookie", cookie.split(";")[0].trim());
        auth.logout(logoutEx);
        assertTrue(logoutEx.responseHeaders.get("Set-Cookie").stream()
                .anyMatch(c -> c.startsWith(AdminSessionCookies.SESSION + "=;")));

        var after = exchange("GET", "/admin/api/me", "");
        after.requestHeaders.set("Cookie", cookie.split(";")[0].trim());
        assertNull(auth.operator(after));
    }

    @Test
    void secondLoginRevokesFirstSession() throws Exception {
        var first = exchange("POST", "/admin/api/login", "");
        auth.login(first, "admin", "admin123");
        String cookie1 = first.responseHeaders.get("Set-Cookie").stream()
                .filter(c -> c.startsWith(AdminSessionCookies.SESSION + "=") && !c.contains("=;"))
                .findFirst().orElseThrow().split(";")[0].trim();

        var second = exchange("POST", "/admin/api/login", "");
        auth.login(second, "admin", "admin123");

        var stale = exchange("GET", "/admin/api/me", "");
        stale.requestHeaders.set("Cookie", cookie1);
        assertNull(auth.operator(stale));
    }

    @Test
    void badPasswordRejected() throws Exception {
        var ex = exchange("POST", "/admin/api/login", "");
        assertTrue(auth.login(ex, "admin", "wrong").isEmpty());
        List<String> cookies = ex.responseHeaders.get("Set-Cookie");
        assertTrue(cookies == null || cookies.stream().noneMatch(c ->
                c.startsWith(AdminSessionCookies.SESSION + "=") && !c.contains("=;")));
    }

    private static RecordingExchange exchange(String method, String path, String body) {
        return new RecordingExchange(method, URI.create("http://127.0.0.1" + path), body);
    }

    private static final class RecordingExchange extends HttpExchange {
        private final String method;
        private final URI uri;
        private final Headers requestHeaders = new Headers();
        final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();

        RecordingExchange(String method, URI uri, String body) {
            this.method = method;
            this.uri = uri;
        }

        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public URI getRequestURI() { return uri; }
        @Override public String getRequestMethod() { return method; }
        @Override public com.sun.net.httpserver.HttpContext getHttpContext() { return null; }
        @Override public void close() { }
        @Override public InputStream getRequestBody() { return new ByteArrayInputStream(new byte[0]); }
        @Override public OutputStream getResponseBody() { return responseBody; }
        @Override public void sendResponseHeaders(int rCode, long responseLength) { }
        @Override public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 54321);
        }
        @Override public int getResponseCode() { return 0; }
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
