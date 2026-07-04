package com.giso.gateway.auth;

/** 管理台登录防暴力破解策略（auth.login_security）。 */
public final class LoginSecurityConfig {
    /** 是否启用；本地开发建议 false，生产 postgres 建议 true。 */
    public boolean enabled = false;
    /** 同账号在窗口内连续失败次数上限，达到后锁定账号。 */
    public int maxAttemptsPerUser = 5;
    /** 账号锁定时长（分钟）。 */
    public int lockoutMinutes = 15;
    /** 账号失败计数滑动窗口（分钟），超出窗口则重新计数。 */
    public int attemptWindowMinutes = 15;
    /** 单 IP 在窗口内允许的登录尝试总次数（含所有账号）。 */
    public int maxAttemptsPerIp = 30;
    /** IP 计数窗口（分钟）。 */
    public int ipWindowMinutes = 10;
    /** IP 超限后的封禁时长（分钟）。 */
    public int ipBlockMinutes = 15;
    /** 每次登录失败后额外等待毫秒数（按失败次数累加，上限 3s），减缓撞库。 */
    public int delayMsPerFailure = 500;

    public static LoginSecurityConfig productionDefaults() {
        var c = new LoginSecurityConfig();
        c.enabled = true;
        return c;
    }

    public static LoginSecurityConfig disabled() {
        return new LoginSecurityConfig();
    }
}
