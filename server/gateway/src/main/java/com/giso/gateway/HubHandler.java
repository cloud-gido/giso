package com.giso.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/** GIDO 产品导航页（/ 与 /hub/），无需鉴权，方便本地栈一键跳转。 */
public final class HubHandler implements HttpHandler {
    private static final Map<String, String> MIME = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "application/javascript; charset=utf-8",
            "svg", "image/svg+xml");

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (Http.handlePreflight(ex)) return;
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) {
            serve(ex, "index.html");
            return;
        }
        if (path.equals("/hub") || path.equals("/hub/")) {
            serve(ex, "index.html");
            return;
        }
        if (path.startsWith("/hub/")) {
            serve(ex, path.substring("/hub/".length()));
            return;
        }
        Http.empty(ex, 404);
    }

    private static void serve(HttpExchange ex, String rel) throws IOException {
        if (rel.isEmpty()) rel = "index.html";
        try (InputStream in = HubHandler.class.getResourceAsStream("/hub/" + rel)) {
            if (in == null) {
                Http.empty(ex, 404);
                return;
            }
            byte[] bytes = in.readAllBytes();
            String ext = rel.contains(".") ? rel.substring(rel.lastIndexOf('.') + 1) : "html";
            ex.getResponseHeaders().set("Content-Type", MIME.getOrDefault(ext, "application/octet-stream"));
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        }
    }
}
