package com.giso.gateway.auth;

import java.util.Optional;

/** 登录尝试结果（含安全门禁与凭证校验）。 */
public final class LoginResult {
    public static final String CODE_INVALID = "invalid_credentials";
    public static final String CODE_LOCKED = "account_locked";
    public static final String CODE_IP_BLOCKED = "ip_blocked";

    private final boolean success;
    private final AuthContext context;
    private final String code;
    private final String message;
    private final int httpStatus;
    private final int retryAfterSec;
    private final Integer attemptsRemaining;
    private final int failureDelayMs;

    private LoginResult(boolean success, AuthContext context, String code, String message,
            int httpStatus, int retryAfterSec, Integer attemptsRemaining, int failureDelayMs) {
        this.success = success;
        this.context = context;
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
        this.retryAfterSec = retryAfterSec;
        this.attemptsRemaining = attemptsRemaining;
        this.failureDelayMs = failureDelayMs;
    }

    public static LoginResult ok(AuthContext ctx) {
        return new LoginResult(true, ctx, null, null, 200, 0, null, 0);
    }

    public static LoginResult invalid(String message, Integer attemptsRemaining, int delayMs) {
        return new LoginResult(false, null, CODE_INVALID, message, 401, 0, attemptsRemaining, delayMs);
    }

    public static LoginResult accountLocked(String message, int retryAfterSec) {
        return new LoginResult(false, null, CODE_LOCKED, message, 423, retryAfterSec, null, 0);
    }

    public static LoginResult ipBlocked(String message, int retryAfterSec) {
        return new LoginResult(false, null, CODE_IP_BLOCKED, message, 429, retryAfterSec, null, 0);
    }

    public boolean success() { return success; }

    public Optional<AuthContext> context() { return Optional.ofNullable(context); }

    public String code() { return code; }

    public String message() { return message; }

    public int httpStatus() { return httpStatus; }

    public int retryAfterSec() { return retryAfterSec; }

    public Integer attemptsRemaining() { return attemptsRemaining; }

    /** 失败后节流等待（毫秒），调用方在返回响应前 sleep。 */
    public int failureDelayMs() {
        return failureDelayMs;
    }
}
