package com.giso.gateway.debug;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertNull;

/** 本地/CI 可选：设置 GISO_REDIS_PING_URL 后实测 ElastiCache rediss 连通性。 */
class RedisLivePingIT {

    @Test
    @EnabledIfEnvironmentVariable(named = "GISO_REDIS_PING_URL", matches = ".+")
    void pingConfiguredRedisUrl() {
        String url = System.getenv("GISO_REDIS_PING_URL");
        String pass = System.getenv("GISO_REDIS_PING_PASSWORD");
        var info = RedisConnections.parseUrl(url, null, pass, -1);
        System.out.println("label=" + info.safeLabel());
        System.out.println("auth=" + info.authMode());
        System.out.println("passwordLen=" + (info.password() == null ? 0 : info.password().length()));
        String err = RedisConnections.ping(info);
        if (err != null) System.err.println("ping failed: " + err);
        assertNull(err, err);
    }
}
