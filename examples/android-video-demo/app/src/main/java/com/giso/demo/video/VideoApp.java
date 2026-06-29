package com.giso.demo.video;

import android.app.Application;

import com.giso.tracker.Tracker;
import com.giso.tracker.TrackerConfig;

/** 应用入口：初始化 GISO 埋点 SDK。 */
public final class VideoApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Tracker.init(this, TrackerConfig.builder(BuildConfig.APP_KEY, BuildConfig.VERSION_NAME,
                        BuildConfig.TRACK_ENDPOINT)
                .channel("demo")
                .debug(BuildConfig.TRACK_DEBUG)
                .exposureDurationMs(500L)
                .batchSize(5)
                .flushIntervalMs(8000L)
                .build());
    }
}
