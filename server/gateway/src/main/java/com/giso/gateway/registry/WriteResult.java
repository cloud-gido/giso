package com.giso.gateway.registry;

/** 注册表写操作结果。 */
public record WriteResult(String error, long revision) {
    public static WriteResult ok(long revision) {
        return new WriteResult(null, revision);
    }

    public static WriteResult fail(String error) {
        return new WriteResult(error, -1);
    }
}
