package com.giso.gateway.auth;

import com.sun.net.httpserver.HttpExchange;

import java.util.List;

/**
 * 管理台会话 Cookie 唯一出口（Path / Secure / SameSite 集中维护，避免删不干净）。
 */
public final class AdminSessionCookies {
    /** 当前会话 Cookie（opaque session id）。 */
    public static final String SESSION = "giso_sid";
    /** 已废弃：曾把 Base64(user:pass) 写入 Cookie，退出时必须一并清除。 */
    private static final String LEGACY_CREDENTIAL = "giso_admin_sess";

    private static final String PATH = "/";
    private static final int MAX_AGE_SEC = 86_400;

    private AdminSessionCookies() { }

    public static void writeSession(HttpExchange ex, String sessionId) {
        ex.getResponseHeaders().add("Set-Cookie", build(SESSION, sessionId, MAX_AGE_SEC, ex));
    }

    public static void clearAll(HttpExchange ex) {
        for (String name : List.of(SESSION, LEGACY_CREDENTIAL)) {
            ex.getResponseHeaders().add("Set-Cookie", build(name, "", 0, ex));
            ex.getResponseHeaders().add("Set-Cookie", buildLegacyPath(name, ex));
        }
    }

    public static String readSessionId(HttpExchange ex) {
        return read(ex, SESSION);
    }

    private static String build(String name, String value, int maxAgeSec, HttpExchange ex) {
        return name + "=" + value
                + "; Path=" + PATH
                + "; HttpOnly"
                + "; SameSite=Lax"
                + "; Max-Age=" + maxAgeSec
                + secureSuffix(ex);
    }

    /** 清除旧版 Path=/admin 的 Cookie。 */
    private static String buildLegacyPath(String name, HttpExchange ex) {
        return name + "=; Path=/admin; HttpOnly; SameSite=Lax; Max-Age=0" + secureSuffix(ex);
    }

    private static String secureSuffix(HttpExchange ex) {
        String proto = ex.getRequestHeaders().getFirst("X-Forwarded-Proto");
        return proto != null && proto.equalsIgnoreCase("https") ? "; Secure" : "";
    }

    private static String read(HttpExchange ex, String name) {
        String cookies = ex.getRequestHeaders().getFirst("Cookie");
        if (cookies == null || cookies.isBlank()) return null;
        String prefix = name + "=";
        for (String part : cookies.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(prefix)) {
                String val = trimmed.substring(prefix.length());
                return val.isBlank() ? null : val;
            }
        }
        return null;
    }
}
