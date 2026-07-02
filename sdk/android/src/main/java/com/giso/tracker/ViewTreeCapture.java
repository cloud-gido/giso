package com.giso.tracker;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewTree 圈选辅助：遍历可见 View，输出 normalized bounds + suggested eid。
 * 在 View 上 {@code setTag(R.string.giso_viewtree_eid, "play_btn")} 可覆盖 suggested eid。
 */
public final class ViewTreeCapture {
    /** {@link View#setTag(int, Object)} 用的 key，见 res/values/ids.xml */
    public static final int TAG_EID = 0x7f0a0001;

    private ViewTreeCapture() {}

    public static JSONArray capture(View root, int maxNodes) {
        List<JSONObject> out = new ArrayList<>();
        walk(root, root.getWidth(), root.getHeight(), out, maxNodes);
        JSONArray arr = new JSONArray();
        for (JSONObject o : out) arr.put(o);
        return arr;
    }

    private static void walk(View v, int vw, int vh, List<JSONObject> out, int max) {
        if (out.size() >= max || v.getVisibility() != View.VISIBLE || vw <= 0 || vh <= 0) return;
        Rect r = new Rect();
        if (!v.getGlobalVisibleRect(r) || r.width() < 8 || r.height() < 8) return;

        if (v.isClickable() || v.getTag(TAG_EID) != null) {
            try {
                JSONObject o = new JSONObject();
                o.put("tag", v.getClass().getSimpleName());
                CharSequence desc = v.getContentDescription();
                o.put("label", desc != null ? desc.toString() : "");
                o.put("suggested_eid", suggestEid(v));
                JSONObject bounds = new JSONObject();
                bounds.put("x", r.left / (double) vw);
                bounds.put("y", r.top / (double) vh);
                bounds.put("w", r.width() / (double) vw);
                bounds.put("h", r.height() / (double) vh);
                o.put("bounds", bounds);
                out.add(o);
            } catch (Exception ignored) { }
        }
        if (v instanceof ViewGroup vg) {
            for (int i = 0; i < vg.getChildCount(); i++) {
                walk(vg.getChildAt(i), vw, vh, out, max);
            }
        }
    }

    static String suggestEid(View v) {
        Object tag = v.getTag(TAG_EID);
        if (tag instanceof String s && !s.isEmpty()) return s;
        if (v.getContentDescription() != null) {
            return simplify(String.valueOf(v.getContentDescription()));
        }
        return simplify(v.getClass().getSimpleName());
    }

    private static String simplify(String raw) {
        return raw.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }
}
