package com.giso.gateway;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** HTTP 工具：gzip 解包、JSON 响应、CORS、query 解析。 */
final class Http {
    private Http() { }

    static byte[] readBody(HttpExchange ex) throws IOException {
        return readBody(ex, Long.MAX_VALUE);
    }

    /** 限长读取（按解压后字节数），超限返回 null（调用方应回 413）。防 gzip 炸弹/OOM。 */
    static byte[] readBody(HttpExchange ex, long maxBytes) throws IOException {
        InputStream in = ex.getRequestBody();
        String enc = ex.getRequestHeaders().getFirst("Content-Encoding");
        if (enc != null && enc.toLowerCase().contains("gzip")) {
            in = new GZIPInputStream(in);
        }
        var out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            if (out.size() + n > maxBytes) {
                in.close();
                return null;
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** 客户端 IP：优先 X-Forwarded-For 第一跳（部署在 LB/CDN 后），否则对端地址 */
    static String clientIp(HttpExchange ex) {
        String xff = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }

    /** Basic Auth 校验；未配置（user 为空）时放行 */
    static boolean basicAuthOk(HttpExchange ex, String user, String password) {
        if (user == null || user.isEmpty()) return true;
        String decoded = basicCredentials(ex);
        return decoded != null && decoded.equals(user + ":" + password);
    }

    /**
     * 管理台角色判定：
     *   "admin"  管理员（可写注册表/清缓冲）
     *   "viewer" 只读（联调/统计/注册表查看）
     *   null     凭证无效
     * admin_user 未配置时整个管理台不鉴权（本地开发），所有人视为 admin。
     */
    static String adminRole(HttpExchange ex, GatewayConfig cfg) {
        if (cfg.adminUser == null || cfg.adminUser.isEmpty()) return "admin";
        String decoded = basicCredentials(ex);
        if (decoded == null) return null;
        if (decoded.equals(cfg.adminUser + ":" + cfg.adminPassword)) return "admin";
        if (!cfg.viewerUser.isEmpty()
                && decoded.equals(cfg.viewerUser + ":" + cfg.viewerPassword)) return "viewer";
        return null;
    }

    /** 写审计用的操作者名（本地无鉴权时为 admin）。 */
    static String adminOperator(HttpExchange ex, GatewayConfig cfg) {
        String role = adminRole(ex, cfg);
        if (role == null) return null;
        if (role.equals("admin")) {
            return cfg.adminUser == null || cfg.adminUser.isEmpty() ? "admin" : cfg.adminUser;
        }
        return cfg.viewerUser;
    }

    private static String basicCredentials(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Basic ")) return null;
        try {
            return new String(java.util.Base64.getDecoder().decode(header.substring(6)),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static void unauthorizedBasic(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"giso-admin\"");
        ex.sendResponseHeaders(401, -1);
        ex.close();
    }

    /** 管理 API 未登录（配合登录页，不触发浏览器 Basic 弹窗）。 */
    static void unauthorizedJson(HttpExchange ex) throws IOException {
        json(ex, 401, "{\"error\":\"unauthorized\",\"code\":\"unauthorized\"}");
    }

    static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    static void cors(HttpExchange ex) {
        var h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type, X-App-Key, Authorization, X-GISO-Space");
    }

    static boolean handlePreflight(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equals("OPTIONS")) {
            cors(ex);
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return true;
        }
        return false;
    }

    static void json(HttpExchange ex, int status, String body) throws IOException {
        cors(ex);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    static void csvAttachment(HttpExchange ex, String filename, String body) throws IOException {
        attachment(ex, filename, "text/csv; charset=utf-8", body);
    }

    static void jsonAttachment(HttpExchange ex, String filename, String body) throws IOException {
        attachment(ex, filename, "application/json; charset=utf-8", body);
    }

    private static void attachment(HttpExchange ex, String filename, String contentType, String body)
            throws IOException {
        cors(ex);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + filename.replace("\"", "") + "\"");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    static void empty(HttpExchange ex, int status) throws IOException {
        cors(ex);
        ex.sendResponseHeaders(status, -1);
        ex.close();
    }

    static Map<String, String> query(HttpExchange ex) {
        Map<String, String> q = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return q;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                q.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return q;
    }

    static String spaceKey(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst(com.giso.gateway.space.SpaceService.HEADER_SPACE);
        if (h != null && !h.isBlank()) return h.trim();
        String q = query(ex).get("space");
        if (q != null && !q.isBlank()) return q.trim();
        return com.giso.gateway.space.SpaceService.DEFAULT_SPACE;
    }
}
