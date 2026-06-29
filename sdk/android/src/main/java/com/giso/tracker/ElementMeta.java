package com.giso.tracker;

import java.util.Map;

/** 元素声明：eid 必须在元素池登记，params 自动继承给子元素。 */
public final class ElementMeta {
    public final String eid;
    public final Integer pos;
    public final Map<String, Object> params;
    /** 后台下发的透传参数包（推荐 trace 等），端上不理解内容，原样上报；随 params 同规则继承 */
    public final Map<String, Object> pt;

    public ElementMeta(String eid, Integer pos, Map<String, Object> params) {
        this(eid, pos, params, null);
    }

    public ElementMeta(String eid, Integer pos, Map<String, Object> params, Map<String, Object> pt) {
        this.eid = eid;
        this.pos = pos;
        this.params = params;
        this.pt = pt;
    }

    public static ElementMeta of(String eid) {
        return new ElementMeta(eid, null, null);
    }

    public static ElementMeta of(String eid, int pos, Map<String, Object> params) {
        return new ElementMeta(eid, pos, params);
    }

    public static ElementMeta of(String eid, int pos, Map<String, Object> params, Map<String, Object> pt) {
        return new ElementMeta(eid, pos, params, pt);
    }
}
