package com.giso.tracker;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewParent;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Android 埋点 SDK 门面（单例）。
 *
 * 事件收敛：10 个标准事件全部由 SDK 控制触发时机——
 * 生命周期（含前台 app_heartbeat）、页面、元素曝光/点击。
 */
public final class Tracker {
    private static volatile Tracker instance;
    private static final long HEARTBEAT_MIN_MS = 15_000L;
    private static final long HEARTBEAT_MAX_MS = 300_000L;

    private final TrackerConfig config;
    private final CommonParams common;
    private final EventQueue queue;
    private final ExposureTracker exposure;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** View → 元素声明；参数继承沿 View 树向上查找 */
    private final Map<View, ElementMeta> metas = new java.util.WeakHashMap<>();

    private String curPgid = "";
    private Map<String, Object> curPgParams;
    private Map<String, Object> curPgPt;
    private long pageEnterTs;
    private String refPgid = "";
    private String refEid = "";
    private long foregroundTs;
    private long heartbeatIntervalMs;
    private long lastHeartbeatTs;
    private final Runnable heartbeatTick = this::onHeartbeatTick;

    public static synchronized Tracker init(Application app, TrackerConfig config) {
        if (instance == null) {
            instance = new Tracker(app, config);
        }
        return instance;
    }

    public static Tracker get() {
        if (instance == null) throw new IllegalStateException("Tracker.init() first");
        return instance;
    }

    private Tracker(Application app, TrackerConfig config) {
        this.config = config;
        this.heartbeatIntervalMs = clampHeartbeat(config.heartbeatIntervalMs);
        this.common = new CommonParams(app, config);
        this.queue = new EventQueue(app, config);
        this.exposure = new ExposureTracker(
                config.exposureRatio, config.exposureDurationMs, config.exposureMaxPerPage,
                (view, dur, ratio) -> onExposure(view, dur, ratio));
        registerLifecycle(app);
        trackInstallAndLaunch(app);
        fetchRemoteConfig();
    }

    /** 拉取服务端口径配置（/v1/config），失败静默沿用本地默认值 */
    private void fetchRemoteConfig() {
        String url = config.endpoint.replaceFirst("/v1/track/?$", "/v1/config");
        if (url.equals(config.endpoint)) return;
        new Thread(() -> {
            java.net.HttpURLConnection conn = null;
            try {
                conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                if (conn.getResponseCode() != 200) return;
                byte[] body = readBytes(conn.getInputStream());
                JSONObject c = new JSONObject(new String(body, java.nio.charset.StandardCharsets.UTF_8));
                exposure.updateThresholds(
                        (float) c.optDouble("exposure_ratio", config.exposureRatio),
                        c.optLong("exposure_duration_ms", config.exposureDurationMs),
                        c.optInt("exposure_max_per_page", config.exposureMaxPerPage));
                queue.updateBatching(
                        c.optInt("batch_size", config.batchSize),
                        c.optLong("flush_interval_ms", config.flushIntervalMs));
                if (c.has("heartbeat_interval_ms")) {
                    heartbeatIntervalMs = clampHeartbeat(c.optLong("heartbeat_interval_ms", heartbeatIntervalMs));
                }
            } catch (Exception ignored) {
                // 静默降级
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "giso-tracker-config").start();
    }

    private static long clampHeartbeat(long ms) {
        if (ms < HEARTBEAT_MIN_MS) return HEARTBEAT_MIN_MS;
        if (ms > HEARTBEAT_MAX_MS) return HEARTBEAT_MAX_MS;
        return ms;
    }

    private void startHeartbeat() {
        stopHeartbeat();
        lastHeartbeatTs = System.currentTimeMillis();
        mainHandler.postDelayed(heartbeatTick, heartbeatIntervalMs);
    }

    private void stopHeartbeat() {
        mainHandler.removeCallbacks(heartbeatTick);
    }

    private void onHeartbeatTick() {
        long now = System.currentTimeMillis();
        long dur = Math.max(0L, now - lastHeartbeatTs);
        lastHeartbeatTs = now;
        JSONObject page = pageContext(null);
        try { page.put("fg_dur", dur); } catch (JSONException ignored) { }
        emit("app_heartbeat", page, null, null);
        mainHandler.postDelayed(heartbeatTick, heartbeatIntervalMs);
    }

    public void setUid(String uid) { common.setUid(uid); }
    public void clearUid() { common.setUid(""); }

    /** 业务设备 ID（历史账号体系兼容）；启动后尽早调用 */
    public void setBizDid(String bizDid) { common.setBizDid(bizDid); }
    public void clearBizDid() { common.setBizDid(""); }

    public void flush() { queue.flush(); }

    // ── 页面 ──────────────────────────────────────────────

    /** Activity.onResume / Fragment.onResume 调用 */
    public void enterPage(String pgid, Map<String, Object> pgParams) {
        enterPage(pgid, pgParams, null);
    }

    /** @param pt 后台下发的透传参数包（如推荐 trace），本页所有事件自动携带 */
    public void enterPage(String pgid, Map<String, Object> pgParams, Map<String, Object> pt) {
        if (!curPgid.isEmpty()) exitPage();
        curPgid = pgid;
        curPgParams = pgParams;
        curPgPt = pt;
        pageEnterTs = System.currentTimeMillis();
        exposure.resetPage();
        emit("page_enter", pageContext(null), null, null, mergePt(curPgPt, null));
    }

    /** Activity.onPause / Fragment.onPause 调用 */
    public void exitPage() {
        if (curPgid.isEmpty()) return;
        long stay = System.currentTimeMillis() - pageEnterTs;
        emit("page_exit", pageContext(stay), null, null, mergePt(curPgPt, null));
        refPgid = curPgid;
        curPgid = "";
        curPgParams = null;
        curPgPt = null;
    }

    // ── 元素 ──────────────────────────────────────────────

    /**
     * 声明元素：自动监测曝光，并接管点击上报（在业务 OnClickListener 之外自动上报）。
     * 容器（如视频卡）bind 一次参数，子元素 bind 后自动继承。
     */
    public void bind(View view, ElementMeta meta) {
        metas.put(view, meta);
        exposure.observe(view);
        hookClick(view);
    }

    public void unbind(View view) {
        metas.remove(view);
    }

    // ── 业务事件 ───────────────────────────────────────────

    public void bizEvent(String code, Map<String, Object> params) {
        bizEvent(code, params, null);
    }

    /** @param pt 后台下发的透传参数包，与页面级透传包合并后上报（本次调用优先） */
    public void bizEvent(String code, Map<String, Object> params, Map<String, Object> pt) {
        JSONObject biz = new JSONObject();
        try {
            biz.put("code", code);
            JSONObject p = TrackEvent.mapToJson(params);
            if (p != null) biz.put("params", p);
        } catch (JSONException ignored) { }
        emit("biz_event", pageContext(null), null, biz, mergePt(curPgPt, TrackEvent.mapToJson(pt)));
    }

    // ── 内部：曝光/点击 ────────────────────────────────────

    private void onExposure(View view, long dur, float ratio) {
        ElCtx ctx = elementContext(view);
        if (ctx == null) return;
        try {
            ctx.el.put("exp_dur", dur);
            ctx.el.put("exp_ratio", ratio);
        } catch (JSONException ignored) { }
        emit("element_exposure", pageContext(null), ctx.el, null, mergePt(curPgPt, ctx.pt));
    }

    private void hookClick(View view) {
        // 触摸抬起即上报「点击手势完成」，不依赖业务 listener，不改变事件分发
        view.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == android.view.MotionEvent.ACTION_UP && v.isPressed()) {
                ElCtx ctx = elementContext(v);
                if (ctx != null) {
                    refEid = metas.get(v) != null ? metas.get(v).eid : "";
                    emit("element_click", pageContext(null), ctx.el, null, mergePt(curPgPt, ctx.pt));
                }
            }
            return false;
        });
    }

    private static final class ElCtx {
        JSONObject el;
        JSONObject pt;
    }

    /** 沿 View 树向上收集祖先 bind 元素：参数继承（params 与 pt 同规则）+ mod 推导 */
    private ElCtx elementContext(View view) {
        ElementMeta meta = metas.get(view);
        if (meta == null) return null;

        Map<String, Object> merged = new LinkedHashMap<>();
        Map<String, Object> mergedPt = new LinkedHashMap<>();
        String mod = null;
        java.util.List<ElementMeta> chain = new java.util.ArrayList<>();
        ViewParent p = view.getParent();
        while (p instanceof View) {
            ElementMeta m = metas.get(p);
            if (m != null) {
                chain.add(m);
                if (mod == null) mod = m.eid;
            }
            p = ((View) p).getParent();
        }
        // 自根向叶合并，叶子（自身）优先级最高
        for (int i = chain.size() - 1; i >= 0; i--) {
            if (chain.get(i).params != null) merged.putAll(chain.get(i).params);
            if (chain.get(i).pt != null) mergedPt.putAll(chain.get(i).pt);
        }
        if (meta.params != null) merged.putAll(meta.params);
        if (meta.pt != null) mergedPt.putAll(meta.pt);

        ElCtx ctx = new ElCtx();
        ctx.el = new JSONObject();
        try {
            ctx.el.put("eid", meta.eid);
            if (mod != null) ctx.el.put("mod", mod);
            if (meta.pos != null) ctx.el.put("pos", meta.pos);
            if (!merged.isEmpty()) ctx.el.put("params", TrackEvent.mapToJson(merged));
        } catch (JSONException ignored) { }
        if (!mergedPt.isEmpty()) ctx.pt = TrackEvent.mapToJson(mergedPt);
        return ctx;
    }

    /** 合并页面级与元素/调用级透传包（后者优先）；皆空返回 null */
    private static JSONObject mergePt(Map<String, Object> pagePt, JSONObject overlay) {
        JSONObject base = TrackEvent.mapToJson(pagePt);
        if (base == null || base.length() == 0) return overlay;
        if (overlay == null || overlay.length() == 0) return base;
        try {
            for (java.util.Iterator<String> it = overlay.keys(); it.hasNext(); ) {
                String k = it.next();
                base.put(k, overlay.get(k));
            }
        } catch (JSONException ignored) { }
        return base;
    }

    // ── 内部：生命周期 ─────────────────────────────────────

    private void trackInstallAndLaunch(Context context) {
        SharedPreferences sp = context.getSharedPreferences("giso_tracker", Context.MODE_PRIVATE);
        if (!sp.getBoolean("activated", false)) {
            sp.edit().putBoolean("activated", true).apply();
            emit("app_install", null, null, null);
        }
        emit("app_launch", null, null, null);
    }

    private void registerLifecycle(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            private int started = 0;
            private boolean appForeground = false;

            @Override public void onActivityStarted(Activity a) {
                if (started++ == 0) {
                    appForeground = true;
                    common.onForeground();
                    foregroundTs = System.currentTimeMillis();
                    queue.onForeground(
                            TrackEvent.of("app_foreground", common.snapshot(), null, null, null),
                            3000L);
                    startHeartbeat();
                }
            }

            @Override public void onActivityStopped(Activity a) {
                if (--started == 0 && appForeground) {
                    appForeground = false;
                    stopHeartbeat();
                    long fgDur = System.currentTimeMillis() - foregroundTs;
                    JSONObject page = pageContext(null);
                    try { page.put("fg_dur", fgDur); } catch (JSONException ignored) { }
                    // 主线程同步 flush + WakeLock；超时加长以应对弱网 / OEM
                    queue.onBackground(
                            TrackEvent.of("app_background", common.snapshot(), page, null, null),
                            5000L);
                }
            }

            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityResumed(Activity a) { common.touch(); }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }

    // ── 内部：发送 ─────────────────────────────────────────

    private JSONObject pageContext(Long stay) {
        JSONObject o = new JSONObject();
        try {
            o.put("pgid", curPgid);
            JSONObject pp = TrackEvent.mapToJson(curPgParams == null ? new HashMap<>() : curPgParams);
            if (pp != null && pp.length() > 0) o.put("pg_params", pp);
            if (!refPgid.isEmpty()) o.put("ref_pgid", refPgid);
            if (!refEid.isEmpty()) o.put("ref_eid", refEid);
            if (stay != null) o.put("pg_stay", stay);
        } catch (JSONException ignored) { }
        return o;
    }

    private void emit(String event, JSONObject page, JSONObject element, JSONObject biz) {
        emit(event, page, element, biz, null);
    }

    private void emit(String event, JSONObject page, JSONObject element, JSONObject biz, JSONObject pt) {
        queue.push(TrackEvent.of(event, common.snapshot(), page, element, biz, pt));
    }

    /** API 33 以下无 InputStream.readAllBytes()，华为等旧系统会因此闪退。 */
    private static byte[] readBytes(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }
}
