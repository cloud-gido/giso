package com.giso.gateway;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/** SSE 推送：按空间隔离，管理页面实时联调用。 */
public final class SseHub {
    private record Client(OutputStream os, String space) { }

    private final CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<>();

    public void subscribe(HttpExchange ex, String spaceKey) throws IOException {
        String space = spaceKey == null || spaceKey.isBlank()
                ? com.giso.gateway.space.SpaceService.DEFAULT_SPACE : spaceKey;
        Http.cors(ex);
        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, 0);
        OutputStream os = ex.getResponseBody();
        os.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
        os.flush();
        clients.add(new Client(os, space));
    }

    public void broadcast(String json, String spaceKey) {
        String space = spaceKey == null || spaceKey.isBlank()
                ? com.giso.gateway.space.SpaceService.DEFAULT_SPACE : spaceKey;
        byte[] frame = ("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8);
        for (Iterator<Client> it = clients.iterator(); it.hasNext(); ) {
            Client c = it.next();
            if (!c.space().equals(space)) continue;
            try {
                c.os().write(frame);
                c.os().flush();
            } catch (IOException e) {
                clients.remove(c);
                try { c.os().close(); } catch (IOException ignored) { }
            }
        }
    }
}
