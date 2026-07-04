package com.giso.gateway.auth;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 进程内登录安全状态（yaml 模式 / 未启用 PG 时）。 */
public final class InMemoryLoginSecurityStore implements LoginSecurityStore {
    private final LoginSecurityConfig config;
    private final Map<String, IpBucket> ipBuckets = new ConcurrentHashMap<>();
    private final Map<String, UserBucket> userBuckets = new ConcurrentHashMap<>();

    private record IpBucket(Instant windowStart, int count, Instant blockedUntil) { }

    private record UserBucket(int count, Instant windowStart, Instant lockedUntil) { }

    public InMemoryLoginSecurityStore(LoginSecurityConfig config) {
        this.config = config;
    }

    @Override
    public Optional<BlockStatus> checkBlock(String ip, String username) throws SQLException {
        Instant now = Instant.now();
        IpBucket ipb = ipBuckets.get(ip);
        if (ipb != null && ipb.blockedUntil() != null && ipb.blockedUntil().isAfter(now)) {
            return Optional.of(new BlockStatus("ip", LoginSecurityStore.retryAfterSec(ipb.blockedUntil())));
        }
        if (username != null && !username.isBlank()) {
            UserBucket ub = userBuckets.get(username);
            if (ub != null && ub.lockedUntil() != null && ub.lockedUntil().isAfter(now)) {
                return Optional.of(new BlockStatus("account", LoginSecurityStore.retryAfterSec(ub.lockedUntil())));
            }
        }
        return Optional.empty();
    }

    @Override
    public FailureStats recordFailure(String ip, String username) throws SQLException {
        Instant now = Instant.now();
        int ipCount = bumpIp(ip, now);
        int delayBase = ipCount;
        Integer remaining = null;
        int userCount = 0;
        if (username != null && !username.isBlank()) {
            userCount = bumpUser(username, now);
            delayBase = Math.max(delayBase, userCount);
            if (userCount < config.maxAttemptsPerUser) {
                remaining = config.maxAttemptsPerUser - userCount;
            }
        }
        int delayMs = Math.min(3000, config.delayMsPerFailure * Math.max(1, delayBase));
        return new FailureStats(userCount, remaining, delayMs);
    }

    @Override
    public void recordSuccess(String username) {
        if (username != null) userBuckets.remove(username);
    }

    @Override
    public void unlockUser(String username) {
        if (username != null) userBuckets.remove(username);
    }

    private int bumpIp(String ip, Instant now) {
        IpBucket prev = ipBuckets.get(ip);
        Instant windowStart = now;
        int count = 1;
        Instant blockedUntil = null;
        if (prev != null) {
            if (prev.blockedUntil() != null && prev.blockedUntil().isAfter(now)) {
                return prev.count();
            }
            if (minutesBetween(prev.windowStart(), now) < config.ipWindowMinutes) {
                windowStart = prev.windowStart();
                count = prev.count() + 1;
            }
        }
        if (count >= config.maxAttemptsPerIp) {
            blockedUntil = now.plusSeconds(config.ipBlockMinutes * 60L);
        }
        ipBuckets.put(ip, new IpBucket(windowStart, count, blockedUntil));
        return count;
    }

    private int bumpUser(String username, Instant now) {
        UserBucket prev = userBuckets.get(username);
        int count = 1;
        Instant windowStart = now;
        Instant lockedUntil = null;
        if (prev != null) {
            if (minutesBetween(prev.windowStart(), now) < config.attemptWindowMinutes) {
                windowStart = prev.windowStart();
                count = prev.count() + 1;
            }
        }
        if (count >= config.maxAttemptsPerUser) {
            lockedUntil = now.plusSeconds(config.lockoutMinutes * 60L);
        }
        userBuckets.put(username, new UserBucket(count, windowStart, lockedUntil));
        return count;
    }

    private static long minutesBetween(Instant a, Instant b) {
        return Math.abs(b.getEpochSecond() - a.getEpochSecond()) / 60;
    }
}
