package com.giso.gateway.auth;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/** 登录失败计数与锁定状态（PostgreSQL 或进程内）。 */
public interface LoginSecurityStore {
    record BlockStatus(String kind, int retryAfterSec) { }

    record FailureStats(int failureCount, Integer attemptsRemaining, int delayMs) { }

    Optional<BlockStatus> checkBlock(String ip, String username) throws SQLException;

    FailureStats recordFailure(String ip, String username) throws SQLException;

    void recordSuccess(String username) throws SQLException;

    void unlockUser(String username) throws SQLException;

    static int retryAfterSec(Instant until) {
        if (until == null) return 0;
        long sec = until.getEpochSecond() - Instant.now().getEpochSecond();
        return (int) Math.max(1, sec);
    }
}
