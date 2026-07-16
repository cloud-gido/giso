package com.giso.tracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.DisplayMetrics;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/** 公共参数采集：did 自生成持久化；session 前后台间隔 30min 重开。 */
final class CommonParams {
    private static final String SDK_VERSION = "1.0.6";
    private static final String SP_NAME = "giso_tracker";
    private static final String KEY_DID = "did";
    private static final long SESSION_GAP_MS = 30 * 60 * 1000L;

    private final Context context;
    private final TrackerConfig config;
    private final String did;

    private volatile String uid = "";
    private String sessionId;
    private long lastActiveTs;

    CommonParams(Context context, TrackerConfig config) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.did = loadOrCreateDid();
        renewSession();
    }

    void setUid(String uid) { this.uid = uid == null ? "" : uid; }

    /** 进前台时调用：超过会话间隔则重开 session */
    synchronized void onForeground() {
        if (System.currentTimeMillis() - lastActiveTs > SESSION_GAP_MS) renewSession();
        lastActiveTs = System.currentTimeMillis();
    }

    synchronized void touch() { lastActiveTs = System.currentTimeMillis(); }

    private void renewSession() {
        sessionId = "s-" + UUID.randomUUID();
        lastActiveTs = System.currentTimeMillis();
    }

    private String loadOrCreateDid() {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String v = sp.getString(KEY_DID, null);
        if (v == null) {
            v = UUID.randomUUID().toString();
            sp.edit().putString(KEY_DID, v).apply();
        }
        return v;
    }

    JSONObject snapshot() {
        JSONObject o = new JSONObject();
        try {
            o.put("app_id", config.appId);
            o.put("app_pkg", context.getPackageName());
            o.put("platform", "android");
            o.put("app_vrsn", config.appVersion);
            o.put("sdk_vrsn", SDK_VERSION);
            o.put("sdk_runtime", "native"); // 与 Flutter sdk_runtime=flutter 区分
            o.put("did", did);
            o.put("uid", uid);
            o.put("session_id", sessionId);
            o.put("channel", config.channel);
            o.put("env", config.env);
            o.put("os_vrsn", Build.VERSION.RELEASE);
            o.put("dev_brand", Build.BRAND);
            o.put("dev_model", Build.MODEL);
            o.put("screen_res", screenRes());
            o.put("net_type", netType());
            o.put("lang", Locale.getDefault().toLanguageTag());
            o.put("tz", tzOffset());
        } catch (JSONException ignored) { }
        return o;
    }

    private String screenRes() {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.widthPixels + "x" + dm.heightPixels;
    }

    private String netType() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm == null ? null : cm.getActiveNetworkInfo();
            if (info == null || !info.isConnected()) return "none";
            return info.getType() == ConnectivityManager.TYPE_WIFI ? "wifi" : "cellular";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String tzOffset() {
        int m = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000;
        String sign = m >= 0 ? "+" : "-";
        m = Math.abs(m);
        return String.format(Locale.US, "%s%02d:%02d", sign, m / 60, m % 60);
    }
}
