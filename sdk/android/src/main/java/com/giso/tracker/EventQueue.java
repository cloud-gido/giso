package com.giso.tracker;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/**
 * 事件队列：单线程调度，攒批（条数/时间双触发）、gzip 上报、
 * 5xx 指数退避重试、失败落盘（文件队列，上限 500 条）、启动续传。
 */
final class EventQueue {
    private static final String TAG = "QyTracker";
    private static final int MAX_STORED = 500;
    private static final long MAX_BACKOFF_MS = 60_000L;
    private static final String STORE_FILE = "giso_tracker_queue.jsonl";

    private final TrackerConfig config;
    private final File storeFile;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "qy-tracker");
        t.setDaemon(true);
        return t;
    });

    private final Deque<TrackEvent> buffer = new ArrayDeque<>();
    private ScheduledFuture<?> timer;
    private long backoffMs = 1000L;
    private volatile int batchSize;
    private volatile long flushIntervalMs;

    EventQueue(Context context, TrackerConfig config) {
        this.config = config;
        this.batchSize = config.batchSize;
        this.flushIntervalMs = config.flushIntervalMs;
        this.storeFile = new File(context.getFilesDir(), STORE_FILE);
        worker.execute(this::restore);
    }

    /** 远程配置下发后更新攒批参数 */
    void updateBatching(int batchSize, long flushIntervalMs) {
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
    }

    void push(TrackEvent ev) {
        worker.execute(() -> {
            if (config.debug) {
                Log.d(TAG, ev.event + " " + ev.toJson());
                buffer.add(ev);
                flushNow(); // debug 模式不攒批，便于实时联调
                return;
            }
            buffer.add(ev);
            if (buffer.size() >= batchSize) {
                flushNow();
            } else if (timer == null || timer.isDone()) {
                timer = worker.schedule(this::flushNow, flushIntervalMs, TimeUnit.MILLISECONDS);
            }
        });
    }

    /** 退后台等时机调用 */
    void flush() {
        worker.execute(this::flushNow);
    }

    // ── worker 线程内 ──────────────────────────────────────

    private void flushNow() {
        if (timer != null) timer.cancel(false);
        if (buffer.isEmpty()) return;

        List<TrackEvent> batch = new ArrayList<>(Math.min(buffer.size(), batchSize));
        for (int i = 0; i < batchSize && !buffer.isEmpty(); i++) batch.add(buffer.poll());

        int status = send(batch);
        if (status >= 200 && status < 500 && status != 429) {
            // 2xx 成功；4xx（除 429 限流外）数据有误不重试（网关已记隔离区）
            backoffMs = 1000L;
            if (buffer.size() >= batchSize) flushNow();
        } else {
            // 5xx / 网络失败：回插重试 + 落盘
            for (int i = batch.size() - 1; i >= 0; i--) buffer.addFirst(batch.get(i));
            persist();
            worker.schedule(this::flushNow, backoffMs, TimeUnit.MILLISECONDS);
            backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
        }
    }

    private int send(List<TrackEvent> batch) {
        JSONArray arr = new JSONArray();
        for (TrackEvent ev : batch) arr.put(ev.toJson());
        byte[] body = arr.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(config.endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Content-Encoding", "gzip");
            conn.setRequestProperty("X-App-Key", config.appId);
            try (OutputStream os = new GZIPOutputStream(conn.getOutputStream())) {
                os.write(body);
            }
            return conn.getResponseCode();
        } catch (IOException e) {
            return -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void persist() {
        try (FileWriter w = new FileWriter(storeFile, false)) {
            int skip = Math.max(0, buffer.size() - MAX_STORED);
            int i = 0;
            for (TrackEvent ev : buffer) {
                if (i++ < skip) continue; // FIFO 淘汰最旧的
                w.write(ev.toJson().toString());
                w.write('\n');
            }
        } catch (IOException e) {
            Log.w(TAG, "persist failed", e);
        }
    }

    private void restore() {
        if (!storeFile.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(storeFile))) {
            String line;
            JSONArray pending = new JSONArray();
            while ((line = r.readLine()) != null && pending.length() < MAX_STORED) {
                pending.put(new org.json.JSONObject(line));
            }
            // 直接整批重发落盘数据（已是完整信封，不再重组）
            if (pending.length() > 0) sendRaw(pending);
        } catch (Exception e) {
            Log.w(TAG, "restore failed", e);
        }
        //noinspection ResultOfMethodCallIgnored
        storeFile.delete();
    }

    private void sendRaw(JSONArray arr) {
        byte[] body = arr.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(config.endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Content-Encoding", "gzip");
            conn.setRequestProperty("X-App-Key", config.appId);
            try (OutputStream os = new GZIPOutputStream(conn.getOutputStream())) {
                os.write(body);
            }
            conn.getResponseCode();
        } catch (IOException ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
