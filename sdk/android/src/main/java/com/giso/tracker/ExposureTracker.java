package com.giso.tracker;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 曝光监测：基于 ViewTreeObserver 的 onDraw/onScrollChanged 轮询可视比例。
 * 口径与 Web 端一致：可视面积 ≥ ratio 持续 ≥ duration 记一次；
 * 滚出（可视 < 20%）后可重记；单次页面进入内每实例最多 maxPerPage 次。
 */
final class ExposureTracker {
    interface Listener {
        void onExposure(View view, long durationMs, float maxRatio);
    }

    private static final float EXIT_RATIO = 0.2f;

    private volatile float ratio;
    private volatile long durationMs;
    private volatile int maxPerPage;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final WeakHashMap<View, Pending> pending = new WeakHashMap<>();
    private WeakHashMap<View, Integer> counts = new WeakHashMap<>();
    private final WeakHashMap<View, Boolean> observed = new WeakHashMap<>();

    private static final class Pending {
        long enterTs;
        float maxRatio;
        Runnable timer;
    }

    ExposureTracker(float ratio, long durationMs, int maxPerPage, Listener listener) {
        this.ratio = ratio;
        this.durationMs = durationMs;
        this.maxPerPage = maxPerPage;
        this.listener = listener;
    }

    void observe(final View view) {
        if (observed.containsKey(view)) return;
        observed.put(view, Boolean.TRUE);
        ViewTreeObserver vto = view.getViewTreeObserver();
        ViewTreeObserver.OnPreDrawListener l = () -> {
            check(view);
            return true;
        };
        vto.addOnPreDrawListener(l);
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { }
            @Override public void onViewDetachedFromWindow(View v) {
                clearPending(v);
                if (v.getViewTreeObserver().isAlive()) {
                    v.getViewTreeObserver().removeOnPreDrawListener(l);
                }
                observed.remove(v);
            }
        });
    }

    /** 远程配置下发后更新口径 */
    void updateThresholds(float ratio, long durationMs, int maxPerPage) {
        this.ratio = ratio;
        this.durationMs = durationMs;
        this.maxPerPage = maxPerPage;
    }

    /** 页面切换时调用：重置每页曝光计数 */
    void resetPage() {
        counts = new WeakHashMap<>();
        for (Map.Entry<View, Pending> e : pending.entrySet()) {
            if (e.getValue().timer != null) handler.removeCallbacks(e.getValue().timer);
        }
        pending.clear();
    }

    private void check(View view) {
        float r = visibleRatio(view);
        Pending p = pending.get(view);
        if (r >= ratio) {
            if (p == null) {
                final Pending np = new Pending();
                np.enterTs = System.currentTimeMillis();
                np.maxRatio = r;
                np.timer = () -> fire(view);
                pending.put(view, np);
                handler.postDelayed(np.timer, durationMs);
            } else {
                p.maxRatio = Math.max(p.maxRatio, r);
            }
        } else if (r < EXIT_RATIO && p != null) {
            clearPending(view);
        }
    }

    private void fire(View view) {
        Pending p = pending.remove(view);
        if (p == null) return;
        Integer c = counts.get(view);
        int count = c == null ? 0 : c;
        if (count >= maxPerPage) return;
        counts.put(view, count + 1);
        listener.onExposure(view, System.currentTimeMillis() - p.enterTs,
                Math.round(p.maxRatio * 100f) / 100f);
    }

    private void clearPending(View view) {
        Pending p = pending.remove(view);
        if (p != null && p.timer != null) handler.removeCallbacks(p.timer);
    }

    private static float visibleRatio(View view) {
        if (!view.isShown() || view.getWidth() == 0 || view.getHeight() == 0) return 0f;
        Rect visible = new Rect();
        if (!view.getGlobalVisibleRect(visible)) return 0f;
        float visibleArea = visible.width() * (float) visible.height();
        float totalArea = view.getWidth() * (float) view.getHeight();
        return totalArea <= 0 ? 0f : visibleArea / totalArea;
    }
}
