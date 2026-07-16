package com.giso.demo.video;

import android.app.Application;
import android.content.SharedPreferences;

import com.giso.tracker.Tracker;
import com.giso.tracker.TrackerConfig;

import java.util.UUID;

/** 应用入口：初始化 GISO 埋点 SDK。 */
public final class VideoApp extends Application {
    /** 模拟历史业务设备 ID 存储（与 SDK did 分库，演示 setBizDid） */
    private static final String BIZ_SP = "video_biz";
    private static final String KEY_BIZ_DID = "biz_did";

    @Override
    public void onCreate() {
        super.onCreate();
        Tracker.init(this, TrackerConfig.builder(BuildConfig.APP_KEY, BuildConfig.VERSION_NAME,
                        BuildConfig.TRACK_ENDPOINT)
                .channel("demo")
                .debug(BuildConfig.TRACK_DEBUG)
                .env(BuildConfig.TRACK_ENV)
                .exposureDurationMs(500L)
                .batchSize(5)
                .flushIntervalMs(8000L)
                .build());
        // 历史无账号体系：业务自管设备 ID → common.biz_did
        Tracker.get().setBizDid(loadOrCreateBizDid());
    }

    private String loadOrCreateBizDid() {
        SharedPreferences sp = getSharedPreferences(BIZ_SP, MODE_PRIVATE);
        String v = sp.getString(KEY_BIZ_DID, null);
        if (v == null || v.isEmpty()) {
            v = "biz-" + UUID.randomUUID();
            sp.edit().putString(KEY_BIZ_DID, v).apply();
        }
        return v;
    }
}
