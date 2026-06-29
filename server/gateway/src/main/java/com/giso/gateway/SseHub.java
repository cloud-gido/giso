package com.giso.gateway;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/** SSE 推送：管理页面实时联调用，事件到达即推送。 */
final class SseHub {
    private final CopyOnWriteArrayList<OutputStream> clients = new CopyOnWriteArrayList<>();

    void subscribe(HttpExchange ex) throws IOException {
        Http.cors(ex);
        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, 0);
        OutputStream os = ex.getResponseBody();
        os.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
        os.flush();
        clients.add(os);
    }

    void broadcast(String json) {
        byte[] frame = ("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8);
        for (Iterator<OutputStream> it = clients.iterator(); it.hasNext(); ) {
            OutputStream os = it.next();
            try {
                os.write(frame);
                os.flush();
            } catch (IOException e) {
                clients.remove(os);
                try { os.close(); } catch (IOException ignored) { }
            }
        }
    }
}
