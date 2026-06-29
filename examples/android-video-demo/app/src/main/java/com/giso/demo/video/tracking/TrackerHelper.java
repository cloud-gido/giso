package com.giso.demo.video.tracking;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.giso.demo.video.BuildConfig;
import com.giso.demo.video.R;

import java.net.HttpURLConnection;
import java.net.URL;

/** 演示辅助：读取 did、渲染联调面板、探测网关连通性。 */
public final class TrackerHelper {
    private static final String SP = "giso_tracker";
    private static final String KEY_DID = "did";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private TrackerHelper() { }

    public static String deviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SP, Context.MODE_PRIVATE);
        String did = prefs.getString(KEY_DID, "");
        return did.isEmpty() ? "（启动后生成）" : did;
    }

    public static void bindDebugPanel(Context context, View root, String pageId) {
        TextView did = root.findViewById(R.id.debugDid);
        TextView page = root.findViewById(R.id.debugPage);
        TextView endpoint = root.findViewById(R.id.debugEndpoint);
        TextView net = root.findViewById(R.id.debugNet);
        TextView mode = root.findViewById(R.id.debugMode);
        if (did != null) {
            did.setText("did: " + deviceId(context));
        }
        if (page != null) {
            page.setText("pgid: " + pageId);
        }
        if (endpoint != null) {
            endpoint.setText("endpoint: " + BuildConfig.TRACK_ENDPOINT);
        }
        if (mode != null) {
            mode.setText(BuildConfig.TRACK_DEBUG ? "debug 实时上报"
                    : "生产模式 · 攒批上报");
        }
        if (net != null) {
            net.setText("网关: 检测中…");
            probeGateway(context, net, true);
        }
    }

    /** 探测手机能否访问电脑上的 GISO 网关。 */
    public static void probeGateway(Context context, TextView statusView, boolean toastOnFail) {
        String configUrl = BuildConfig.TRACK_ENDPOINT.replaceFirst("/v1/track/?$", "/v1/config");
        new Thread(() -> {
            String result;
            boolean ok = false;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(configUrl).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                ok = code == 200;
                result = ok ? "网关: 已连通 ✓" : "网关: HTTP " + code + " ✗";
            } catch (Exception e) {
                result = "网关: 不可达 ✗（手机访问不到电脑）";
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
                            "连不上网关，请确认同一 Wi-Fi，并在手机浏览器打开\n"
                                    + configUrl.replace("/v1/config", "/metrics"),
                            Toast.LENGTH_LONG).show();
                }
            });
        }, "giso-probe").start();
    }
}
