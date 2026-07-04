package com.giso.gateway.auth;

import com.giso.gateway.registry.PostgresRegistryStore;
import com.sun.net.httpserver.HttpExchange;

import java.sql.SQLException;
import java.util.Optional;

/** 管理台登录安全门禁：账号锁定 + IP 限流 + 失败延迟。 */
public final class LoginSecurity {
    private final LoginSecurityConfig config;
    private final LoginSecurityStore store;

    private LoginSecurity(LoginSecurityConfig config, LoginSecurityStore store) {
        this.config = config;
        this.store = store;
    }

    /** 单测 / 同包构造 */
    static LoginSecurity forStore(LoginSecurityConfig config, LoginSecurityStore store) {
        return new LoginSecurity(config, store);
    }

    public static LoginSecurity create(LoginSecurityConfig config,
            PostgresRegistryStore registryStore, String dbSchema) {
        LoginSecurityStore store;
        if (config.enabled && registryStore != null) {
            store = new PostgresLoginSecurityStore(registryStore.dataSource(), dbSchema, config);
        } else if (config.enabled) {
            store = new InMemoryLoginSecurityStore(config);
        } else {
            store = new InMemoryLoginSecurityStore(LoginSecurityConfig.disabled());
        }
        return new LoginSecurity(config, store);
    }

    public boolean enabled() {
        return config.enabled;
    }

    public LoginSecurityConfig config() {
        return config;
    }

    public LoginResult guardLogin(String ip, HttpExchange ex, String username, String password,
            Authenticator authenticator, SessionFactory sessions) throws Exception {
        if (!config.enabled) {
            return tryAuth(username, password, authenticator, sessions, ex);
        }
        Optional<LoginSecurityStore.BlockStatus> block = store.checkBlock(ip, username);
        if (block.isPresent()) {
            LoginSecurityStore.BlockStatus b = block.get();
            if ("ip".equals(b.kind())) {
                return LoginResult.ipBlocked(
                        "登录尝试过于频繁，请 " + b.retryAfterSec() + " 秒后再试",
                        b.retryAfterSec());
            }
            return LoginResult.accountLocked(
                    "账号已临时锁定，请 " + b.retryAfterSec() + " 秒后再试或联系平台管理员解锁",
                    b.retryAfterSec());
        }
        String role = authenticator.authenticate(username, password);
        if (role == null) {
            LoginSecurityStore.FailureStats stats = store.recordFailure(ip, username);
            sleep(stats.delayMs());
            String msg = "用户名或密码错误";
            if (stats.attemptsRemaining() != null && stats.attemptsRemaining() > 0) {
                msg += "（还可尝试 " + stats.attemptsRemaining() + " 次）";
            } else if (stats.failureCount() >= config.maxAttemptsPerUser) {
                return LoginResult.accountLocked(
                        "登录失败次数过多，账号已锁定 " + config.lockoutMinutes + " 分钟",
                        config.lockoutMinutes * 60);
            }
            return LoginResult.invalid(msg, stats.attemptsRemaining(), stats.delayMs());
        }
        store.recordSuccess(username);
        return LoginResult.ok(sessions.establish(ex, username, role));
    }

    public void unlockUser(String username) throws SQLException {
        store.unlockUser(username);
    }

    private LoginResult tryAuth(String username, String password,
            Authenticator authenticator, SessionFactory sessions, HttpExchange ex) throws Exception {
        String role = authenticator.authenticate(username, password);
        if (role == null) {
            return LoginResult.invalid("用户名或密码错误", null, 0);
        }
        return LoginResult.ok(sessions.establish(ex, username, role));
    }

    private static void sleep(int ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    public interface Authenticator {
        String authenticate(String username, String password) throws Exception;
    }

    @FunctionalInterface
    public interface SessionFactory {
        AuthContext establish(HttpExchange ex, String username, String role) throws Exception;
    }
}
