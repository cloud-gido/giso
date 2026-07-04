package com.giso.gateway;

import com.giso.gateway.auth.AdminAuth;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/** 管理页面静态资源（打包进 jar 的 resources/admin/），受登录保护。 */
public final class StaticHandler implements HttpHandler {
    private static final Map<String, String> MIME = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "application/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "svg", "image/svg+xml");

    private final AdminAuth auth;

    public StaticHandler(AdminAuth auth) {
        this.auth = auth;
    }

    private static boolean isPublic(String rel) {
        return rel.equals("login.html")
                || rel.equals("js/login.js")
                || rel.equals("js/auth.js")
                || rel.startsWith("assets/");
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (!path.startsWith("/admin")) {
            Http.empty(ex, 404);
            return;
        }
        if (path.equals("/admin")) {
            Http.redirect(ex, "/admin/");
            return;
        }
        String rel = path.substring("/admin/".length());
        if (rel.isEmpty()) rel = "index.html";
        if (!isPublic(rel) && auth.unauthorized(ex)) {
            Http.redirect(ex, "/admin/login.html");
            return;
        }
        try (InputStream in = getClass().getResourceAsStream("/admin/" + rel)) {
            if (in == null) {
                Http.empty(ex, 404);
                return;
            }
            byte[] bytes = in.readAllBytes();
            String ext = rel.contains(".") ? rel.substring(rel.lastIndexOf('.') + 1) : "html";
            ex.getResponseHeaders().set("Content-Type", MIME.getOrDefault(ext, "application/octet-stream"));
            if ("html".equals(ext) || "js".equals(ext) || "css".equals(ext)) {
                ex.getResponseHeaders().set("Cache-Control", "no-store");
            }
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        }
    }
}
