package com.giso.tracker;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.UUID;

/** 事件信封，与协议文档 §2 对应。内部使用 JSONObject 组装，避免引入序列化依赖。 */
public final class TrackEvent {
    public final String event;
    public final String logId;
    public final long ctime;
    public final JSONObject payload;

    private TrackEvent(String event, JSONObject payload) {
        this.event = event;
        this.logId = UUID.randomUUID().toString();
        this.ctime = System.currentTimeMillis();
        this.payload = payload;
    }

    static TrackEvent of(String event, JSONObject common, JSONObject page,
                         JSONObject element, JSONObject biz) {
        return of(event, common, page, element, biz, null);
    }

    static TrackEvent of(String event, JSONObject common, JSONObject page,
                         JSONObject element, JSONObject biz, JSONObject pt) {
        JSONObject p = new JSONObject();
        try {
            p.put("common", common);
            if (page != null) p.put("page", page);
            if (element != null) p.put("element", element);
            if (biz != null) p.put("biz", biz);
            if (pt != null && pt.length() > 0) p.put("pt", pt);
        } catch (JSONException ignored) { }
        return new TrackEvent(event, p);
    }

    JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("event", event);
            o.put("log_id", logId);
            o.put("ctime", ctime);
            // 展平 payload 的 common/page/element/biz 到信封顶层
            for (java.util.Iterator<String> it = payload.keys(); it.hasNext(); ) {
                String k = it.next();
                o.put(k, payload.get(k));
            }
        } catch (JSONException ignored) { }
        return o;
    }

    static JSONObject mapToJson(Map<String, Object> map) {
        if (map == null) return null;
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, Object> e : map.entrySet()) o.put(e.getKey(), e.getValue());
        } catch (JSONException ignored) { }
        return o;
    }
}
