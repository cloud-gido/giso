package com.giso.tracker;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

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
 * 事件队列。
 *
 * <p>生命周期约定：
 * <ul>
 *   <li>冷启动 spill 延后到首次 {@link #onForeground} 之后发送</li>
 *   <li>{@link #onBackground} 在调用线程同步落盘+发送（持 WakeLock），避免 OEM 挂起后发不出去</li>
 *   <li>{@link #onForeground}：先排空残留 → foreground → 释放 spill</li>
 * </ul>
 */
final class EventQueue {
    private static final String TAG = "GisoTracker";
    private static final int MAX_STORED = 500;
    private static final long MAX_BACKOFF_MS = 60_000L;
    private static final String STORE_FILE = "giso_tracker_queue.jsonl";
    private static final String WAKE_LOCK_TAG = "giso:tracker_flush";

    private final Context appContext;
    private final TrackerConfig config;
    private final File storeFile;
    private final Object lock = new Object();

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "giso-tracker");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY + 1);
        return t;
    });

    private final Deque<TrackEvent> buffer = new ArrayDeque<>();
    private final Deque<JSONObject> spill = new ArrayDeque<>();
    private boolean spillReleased = false;

    private ScheduledFuture<?> timer;
    private long backoffMs = 1000L;
    private volatile int batchSize;
    private volatile long flushIntervalMs;

    EventQueue(Context context, TrackerConfig config) {
        this.appContext = context.getApplicationContext();
        this.config = config;
        this.batchSize = config.batchSize;
        this.flushIntervalMs = config.flushIntervalMs;
        this.storeFile = new File(appContext.getFilesDir(), STORE_FILE);
        worker.execute(() -> {
            synchronized (lock) {
                loadSpillFromDiskLocked();
            }
        });
    }

    void updateBatching(int batchSize, long flushIntervalMs) {
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
    }

    void push(TrackEvent ev) {
        worker.execute(() -> {
            synchronized (lock) {
                buffer.addLast(ev);
                if (config.debug) Log.d(TAG, ev.event + " " + ev.toJson());
                maybeFlushLocked(false);
            }
        });
    }

    /**
     * 进前台（Activity.onStart）。可在主线程调用；内部切到 worker 并短时等待。
     */
    void onForeground(TrackEvent foreground, long timeoutMs) {
        runOnWorkerBlocking(timeoutMs, () -> {
            flushAllLocked(false);
            buffer.addLast(foreground);
            if (config.debug) Log.d(TAG, foreground.event + " " + foreground.toJson());
            flushAllLocked(false);
            releaseSpillLocked();
        });
    }

    /**
     * 退后台（Activity.onStop）。
     * <ol>
     *   <li>先 barrier 等待 worker 把已排队的 page_exit 等事件写入 buffer</li>
     *   <li>依次加入最后一个不足周期的 heartbeat、background</li>
     *   <li>在调用线程持 WakeLock 同步落盘并发送，避免只投递异步任务后被 OEM 挂起</li>
     * </ol>
     */
    void onBackground(TrackEvent heartbeat, TrackEvent background, long timeoutMs) {
        long timeout = Math.max(2000L, timeoutMs);
        // 让 exitPage 等已 submit 的 push 先入队
        runOnWorkerBlocking(Math.min(800L, timeout / 3), () -> { /* queue barrier */ });

        PowerManager.WakeLock wakeLock = null;
        try {
            PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(timeout + 500L);
            }
        } catch (Exception e) {
            Log.w(TAG, "wake lock acquire failed", e);
        }

        long deadline = System.currentTimeMillis() + timeout;
        try {
            synchronized (lock) {
                cancelTimerLocked();
                if (heartbeat != null) {
                    buffer.addLast(heartbeat);
                    if (config.debug) Log.d(TAG, heartbeat.event + " " + heartbeat.toJson());
                }
                buffer.addLast(background);
                if (config.debug) Log.d(TAG, background.event + " " + background.toJson());
                persistAllLocked();
                Log.i(TAG, "onBackground: flushing queue size=" + buffer.size());
                flushAllLockedUntil(deadline, true);
                Log.i(TAG, "onBackground: done, remaining=" + buffer.size() + " spill=" + spill.size());
            }
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                } catch (Exception ignored) { }
            }
        }
    }

    void flush() {
        worker.execute(() -> {
            synchronized (lock) {
                flushAllLocked(false);
            }
        });
    }

    // ── locked helpers（调用方已持 lock，除 HTTP 外） ─────

    private void loadSpillFromDiskLocked() {
        if (!storeFile.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(storeFile))) {
            String line;
            while ((line = r.readLine()) != null && spill.size() < MAX_STORED) {
                line = line.trim();
                if (line.isEmpty()) continue;
                spill.addLast(new JSONObject(line));
            }
        } catch (Exception e) {
            Log.w(TAG, "load spill failed", e);
            spill.clear();
        }
        //noinspection ResultOfMethodCallIgnored
        storeFile.delete();
        if (!spill.isEmpty()) {
            persistAllLocked();
            Log.i(TAG, "loaded spill events: " + spill.size());
        }
    }

    private void releaseSpillLocked() {
        if (spillReleased) return;
        spillReleased = true;
        if (spill.isEmpty()) {
            persistAllLocked();
            return;
        }
        Log.i(TAG, "releasing spill after foreground, count=" + spill.size());
        while (!spill.isEmpty()) {
            List<JSONObject> batch = pollSpillBatchLocked();
            int status = sendJsonBatch(batch); // HTTP 在 lock 内：后台路径可接受；前台路径尽量快
            if (isAccept(status)) {
                backoffMs = 1000L;
            } else {
                for (int i = batch.size() - 1; i >= 0; i--) spill.addFirst(batch.get(i));
                persistAllLocked();
                scheduleRetryLocked();
                return;
            }
        }
        persistAllLocked();
    }

    private void maybeFlushLocked(boolean urgent) {
        if (urgent || config.debug) {
            flushAllLocked(urgent);
            return;
        }
        if (buffer.size() >= batchSize) {
            flushAllLocked(false);
        } else if (timer == null || timer.isDone()) {
            timer = worker.schedule(() -> {
                synchronized (lock) {
                    flushAllLocked(false);
                }
            }, flushIntervalMs, TimeUnit.MILLISECONDS);
        }
    }

    private void flushAllLocked(boolean urgent) {
        flushAllLockedUntil(Long.MAX_VALUE, urgent);
    }

    private void flushAllLockedUntil(long deadlineMs, boolean urgent) {
        cancelTimerLocked();
        while (!buffer.isEmpty() && System.currentTimeMillis() < deadlineMs) {
            List<TrackEvent> batch = pollTrackBatchLocked();
            int status = sendTrackBatch(batch);
            if (isAccept(status)) {
                backoffMs = 1000L;
            } else {
                for (int i = batch.size() - 1; i >= 0; i--) buffer.addFirst(batch.get(i));
                persistAllLocked();
                if (!urgent) scheduleRetryLocked();
                Log.w(TAG, "flush failed status=" + status + " remaining=" + buffer.size());
                return;
            }
        }
        if (buffer.isEmpty() && spill.isEmpty()) {
            //noinspection ResultOfMethodCallIgnored
            storeFile.delete();
        } else {
            persistAllLocked();
        }
    }

    private List<TrackEvent> pollTrackBatchLocked() {
        List<TrackEvent> batch = new ArrayList<>(Math.min(batchSize, buffer.size()));
        while (batch.size() < batchSize && !buffer.isEmpty()) batch.add(buffer.pollFirst());
        return batch;
    }

    private List<JSONObject> pollSpillBatchLocked() {
        List<JSONObject> batch = new ArrayList<>(Math.min(batchSize, spill.size()));
        while (batch.size() < batchSize && !spill.isEmpty()) batch.add(spill.pollFirst());
        return batch;
    }

    private void scheduleRetryLocked() {
        worker.schedule(() -> {
            synchronized (lock) {
                flushAllLocked(false);
                if (spillReleased && !spill.isEmpty()) releaseSpillLocked();
            }
        }, backoffMs, TimeUnit.MILLISECONDS);
        backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
    }

    private void cancelTimerLocked() {
        if (timer != null) {
            timer.cancel(false);
            timer = null;
        }
    }

    private static boolean isAccept(int status) {
        return status >= 200 && status < 500 && status != 429;
    }

    private int sendTrackBatch(List<TrackEvent> batch) {
        JSONArray arr = new JSONArray();
        for (TrackEvent ev : batch) arr.put(ev.toJson());
        return post(arr);
    }

    private int sendJsonBatch(List<JSONObject> batch) {
        JSONArray arr = new JSONArray();
        for (JSONObject o : batch) arr.put(o);
        return post(arr);
    }

    private int post(JSONArray arr) {
        byte[] body = arr.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(config.endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Content-Encoding", "gzip");
            conn.setRequestProperty("X-App-Key", config.appId);
            try (OutputStream os = new GZIPOutputStream(conn.getOutputStream())) {
                os.write(body);
            }
            int code = conn.getResponseCode();
            Log.d(TAG, "POST /track -> " + code + " batch=" + arr.length());
            return code;
        } catch (IOException e) {
            Log.w(TAG, "POST /track failed: " + e.getMessage());
            return -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void persistAllLocked() {
        if (spill.isEmpty() && buffer.isEmpty()) {
            //noinspection ResultOfMethodCallIgnored
            storeFile.delete();
            return;
        }
        try (FileWriter w = new FileWriter(storeFile, false)) {
            int written = 0;
            for (JSONObject o : spill) {
                if (written >= MAX_STORED) break;
                w.write(o.toString());
                w.write('\n');
                written++;
            }
            for (TrackEvent ev : buffer) {
                if (written >= MAX_STORED) break;
                w.write(ev.toJson().toString());
                w.write('\n');
                written++;
            }
        } catch (IOException e) {
            Log.w(TAG, "persist failed", e);
        }
    }

    private void runOnWorkerBlocking(long timeoutMs, Runnable task) {
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        worker.execute(() -> {
            try {
                synchronized (lock) {
                    task.run();
                }
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(Math.max(500L, timeoutMs), TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "lifecycle task timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
