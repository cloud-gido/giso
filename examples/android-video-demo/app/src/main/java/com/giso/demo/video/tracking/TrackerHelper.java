package com.giso.demo.video.tracking;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.giso.demo.video.BuildConfig;
import com.giso.demo.video.R;

import java.net.HttpURLConnection;
import java.net.URL;

/** 演示辅助：读取 did / biz_did、渲染联调面板、探测网关连通性。 */
public final class TrackerHelper {
    private static final String SP = "giso_tracker";
    private static final String KEY_DID = "did";
    private static final String BIZ_SP = "video_biz";
    private static final String KEY_BIZ_DID = "biz_did";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private TrackerHelper() { }

    public static String deviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SP, Context.MODE_PRIVATE);
        return prefs.getString(KEY_DID, "");
    }

    public static String bizDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(BIZ_SP, Context.MODE_PRIVATE);
        return prefs.getString(KEY_BIZ_DID, "");
    }

    public static void bindDebugPanel(Context context, View root, String pageId) {
        TextView did = root.findViewById(R.id.debugDid);
        TextView bizDid = root.findViewById(R.id.debugBizDid);
        TextView page = root.findViewById(R.id.debugPage);
        TextView appKey = root.findViewById(R.id.debugAppKey);
        TextView endpoint = root.findViewById(R.id.debugEndpoint);
        TextView net = root.findViewById(R.id.debugNet);
        TextView mode = root.findViewById(R.id.debugMode);
        Button copyDid = root.findViewById(R.id.debugCopyDid);

        String didValue = deviceId(context);
        String bizDidValue = bizDeviceId(context);
        if (did != null) {
            did.setText("did: " + (didValue.isEmpty() ? "（启动后生成）" : didValue));
        }
        if (bizDid != null) {
            bizDid.setText("biz_did: " + (bizDidValue.isEmpty() ? "（未设置）" : bizDidValue));
        }
        if (page != null) {
            page.setText("pgid: " + pageId);
        }
        if (appKey != null) {
            appKey.setText("app_key: " + BuildConfig.APP_KEY);
        }
        if (endpoint != null) {
            endpoint.setText("endpoint: " + BuildConfig.TRACK_ENDPOINT);
        }
        if (mode != null) {
            mode.setText((BuildConfig.TRACK_DEBUG ? "debug 实时上报" : "生产模式 · 攒批上报")
                    + " · env=" + BuildConfig.TRACK_ENV
                    + " · sdk=1.0.10");
        }
        if (copyDid != null) {
            copyDid.setOnClickListener(v -> {
                if (didValue.isEmpty()) {
                    Toast.makeText(context, "did 尚未生成", Toast.LENGTH_SHORT).show();
                    return;
                }
                ClipboardManager clipboard = (ClipboardManager)
                        context.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("did", didValue));
                Toast.makeText(context, "已复制 did", Toast.LENGTH_SHORT).show();
            });
        }
        if (net != null) {
            net.setText("网关: 检测中…");
            probeGateway(context, net, true);
        }
    }

    /** 探测能否访问 GISO 网关 /v1/config。 */
    public static void probeGateway(Context context, TextView statusView, boolean toastOnFail) {
        String configUrl = BuildConfig.TRACK_ENDPOINT.replaceFirst("/v1/track/?$", "/v1/config");
        new Thread(() -> {
            String result;
            boolean ok = false;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(configUrl).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                ok = code == 200;
                result = ok ? "网关: 已连通 ✓" : "网关: HTTP " + code + " ✗";
            } catch (Exception e) {
                boolean remote = BuildConfig.TRACK_ENDPOINT.startsWith("https://");
                result = remote
                        ? "网关: 不可达 ✗（检查网络或 App Key 白名单）"
                        : "网关: 不可达 ✗（手机与电脑须同一 Wi-Fi）";
            } finally {
                if (conn != null) conn.disconnect();
            }
            boolean finalOk = ok;
            String finalResult = result;
            MAIN.post(() -> {
                if (statusView != null) {
                    statusView.setText(finalResult);
                    statusView.setTextColor(context.getColor(
                            finalOk ? R.color.debug_ok : R.color.debug_bad));
                }
                if (toastOnFail && !finalOk) {
                    Toast.makeText(context,
                            "连不上网关\n" + configUrl + "\n管理台: " + BuildConfig.ADMIN_URL,
                            Toast.LENGTH_LONG).show();
                }
            });
        }, "giso-probe").start();
    }
}
