package com.giso.gateway.auth;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginSecurityTest {
    private LoginSecurityConfig config;
    private InMemoryLoginSecurityStore store;
    private LoginSecurity security;

    @BeforeEach
    void setup() {
        config = new LoginSecurityConfig();
        config.enabled = true;
        config.maxAttemptsPerUser = 3;
        config.lockoutMinutes = 5;
        config.attemptWindowMinutes = 15;
        config.maxAttemptsPerIp = 10;
        config.ipWindowMinutes = 10;
        config.ipBlockMinutes = 5;
        config.delayMsPerFailure = 0;
        store = new InMemoryLoginSecurityStore(config);
        security = LoginSecurity.forStore(config, store);
    }

    @Test
    void locksAccountAfterRepeatedFailures() throws Exception {
        var ex = exchange();
        for (int i = 0; i < 3; i++) {
            LoginResult r = security.guardLogin("10.0.0.9", ex, "alice", "wrong",
                    (u, p) -> null, (e, u, role) -> new AuthContext(u, role));
            assertFalse(r.success());
        }
        LoginResult locked = security.guardLogin("10.0.0.9", ex, "alice", "wrong",
                (u, p) -> null, (e, u, role) -> new AuthContext(u, role));
        assertEquals(LoginResult.CODE_LOCKED, locked.code());
        assertEquals(423, locked.httpStatus());
    }

    @Test
    void successClearsFailureCount() throws Exception {
        var ex = exchange();
        security.guardLogin("10.0.0.9", ex, "bob", "wrong", (u, p) -> null, (e, u, r) -> new AuthContext(u, r));
        LoginResult ok = security.guardLogin("10.0.0.9", ex, "bob", "secret",
                (u, p) -> "user", (e, u, r) -> new AuthContext(u, r));
        assertTrue(ok.success());
        LoginResult again = security.guardLogin("10.0.0.9", ex, "bob", "wrong",
                (u, p) -> null, (e, u, r) -> new AuthContext(u, r));
        assertEquals(LoginResult.CODE_INVALID, again.code());
        assertEquals(2, again.attemptsRemaining());
    }

    private static HttpExchange exchange() {
        return new HttpExchange() {
            @Override public Headers getRequestHeaders() { return new Headers(); }
            @Override public Headers getResponseHeaders() { return new Headers(); }
            @Override public URI getRequestURI() { return URI.create("http://127.0.0.1/admin/api/login"); }
            @Override public String getRequestMethod() { return "POST"; }
            @Override public HttpContext getHttpContext() { return null; }
            @Override public void close() { }
            @Override public java.io.InputStream getRequestBody() { return java.io.InputStream.nullInputStream(); }
            @Override public java.io.OutputStream getResponseBody() { return java.io.OutputStream.nullOutputStream(); }
            @Override public void sendResponseHeaders(int rCode, long responseLength) { }
            @Override public InetSocketAddress getRemoteAddress() {
                return new InetSocketAddress("10.0.0.9", 12345);
            }
            @Override public int getResponseCode() { return 0; }
            @Override public InetSocketAddress getLocalAddress() {
                return new InetSocketAddress("127.0.0.1", 8080);
            }
            @Override public String getProtocol() { return "HTTP/1.1"; }
            @Override public Object getAttribute(String name) { return null; }
            @Override public void setAttribute(String name, Object value) { }
            @Override public void setStreams(java.io.InputStream i, java.io.OutputStream o) { }
            @Override public HttpPrincipal getPrincipal() { return null; }
        };
    }
}
