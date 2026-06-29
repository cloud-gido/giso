package com.giso.demo.video.tracking;

import android.os.Handler;
import android.os.Looper;

import com.giso.demo.video.model.VideoEpisode;
import com.giso.tracker.BizEvents;
import com.giso.tracker.Params;
import com.giso.tracker.Tracker;

import java.util.HashMap;
import java.util.Map;

/**
 * 播放业务事件：video_play_start / heartbeat / end。
 * 对应 schema 登记的长视频领域事件，演示 biz_event 上报。
 */
public final class PlaybackTracker {
    private static final long HEARTBEAT_MS = 30_000L;

    private final Tracker tracker = Tracker.get();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeatTask = this::emitHeartbeat;

    private VideoEpisode episode;
    private long playStartMs;
    private long accumulatedMs;
    private long lastTickMs;
    private boolean started;
    private float speed = 1.0f;

    public void onEpisodeReady(VideoEpisode ep, boolean autoPlay) {
        if (started) return;
        episode = ep;
        started = true;
        playStartMs = System.currentTimeMillis();
        lastTickMs = playStartMs;
        Map<String, Object> p = baseParams();
        p.put(Params.IS_AUTO, autoPlay ? 1 : 0);
        tracker.bizEvent(BizEvents.VIDEO_PLAY_START, p);
        scheduleHeartbeat();
    }

    public void onPlaying(long positionMs) {
        lastTickMs = System.currentTimeMillis();
    }

    public void onPaused(long positionMs) {
        tick(positionMs);
        handler.removeCallbacks(heartbeatTask);
        emitHeartbeat(positionMs);
    }

    public void onSpeedChanged(float speed) {
        this.speed = speed;
    }

    public void onEnded(long positionMs) {
        finish(positionMs, true);
    }

    public void onStopped(long positionMs) {
        finish(positionMs, false);
    }

    private void finish(long positionMs, boolean completed) {
        if (!started) return;
        tick(positionMs);
        handler.removeCallbacks(heartbeatTask);
        Map<String, Object> p = baseParams();
        p.put(Params.PLAY_DUR, accumulatedMs);
        p.put(Params.PLAY_POS, positionMs);
        p.put(Params.VIDEO_DUR, episode.durationMs);
        tracker.bizEvent(BizEvents.VIDEO_PLAY_END, p);
        started = false;
    }

    public void onError(String reason) {
        if (episode == null) return;
        Map<String, Object> p = new HashMap<>();
        p.put(Params.VID, episode.vid);
        p.put(Params.FAIL_REASON, reason);
        tracker.bizEvent(BizEvents.VIDEO_PLAY_ERROR, p);
        started = false;
        handler.removeCallbacks(heartbeatTask);
    }

    private void emitHeartbeat() {
        if (!started || episode == null) return;
        emitHeartbeat(episode.durationMs > 0 ? accumulatedMs : 0);
        scheduleHeartbeat();
    }

    private void emitHeartbeat(long positionMs) {
        Map<String, Object> p = baseParams();
        p.put(Params.PLAY_DUR, accumulatedMs);
        p.put(Params.PLAY_POS, positionMs);
        p.put(Params.SPEED, (double) speed);
        tracker.bizEvent(BizEvents.VIDEO_PLAY_HEARTBEAT, p);
    }

    private void scheduleHeartbeat() {
        handler.removeCallbacks(heartbeatTask);
        handler.postDelayed(heartbeatTask, HEARTBEAT_MS);
    }

    private void tick(long positionMs) {
        long now = System.currentTimeMillis();
        if (lastTickMs > 0) {
            accumulatedMs += Math.max(0, now - lastTickMs);
        }
        lastTickMs = now;
    }

    private Map<String, Object> baseParams() {
        Map<String, Object> p = new HashMap<>();
        p.put(Params.VID, episode.vid);
        String series = episode.seriesId != null && !episode.seriesId.isEmpty()
                ? episode.seriesId : episode.vid;
        p.put(Params.SERIES_ID, series);
        p.put(Params.EP_NUM, episode.epNum > 0 ? episode.epNum : 1);
        p.put(Params.DEFINITION, episode.definition);
        return p;
    }
}
