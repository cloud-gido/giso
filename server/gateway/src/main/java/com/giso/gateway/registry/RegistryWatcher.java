package com.giso.gateway.registry;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 多副本注册表热更新：PostgreSQL LISTEN + revision 轮询兜底。
 */
public final class RegistryWatcher implements AutoCloseable {
    private final com.giso.gateway.Registry registry;
    private final RegistryStore store;
    private final int pollIntervalSec;
    private volatile boolean running = true;
    private Thread listenThread;
    private Thread pollThread;

    public RegistryWatcher(com.giso.gateway.Registry registry, RegistryStore store, int pollIntervalSec) {
        this.registry = registry;
        this.store = store;
        this.pollIntervalSec = Math.max(5, pollIntervalSec);
    }

    public void start() {
        if (!"postgres".equals(store.backendName())) return;
        PostgresRegistryStore pg = (PostgresRegistryStore) store;
        pollThread = Thread.ofVirtual().name("giso-registry-poll").start(this::pollLoop);
        listenThread = Thread.ofVirtual().name("giso-registry-listen").start(() -> listenLoop(pg));
    }

    private void pollLoop() {
        long last = registry.globalRevision();
        while (running) {
            try {
                Thread.sleep(pollIntervalSec * 1000L);
                long rev = store.fetchGlobalRevision();
                if (rev != last) {
                    registry.reload();
                    last = rev;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[giso-registry] poll failed: " + e.getMessage());
            }
        }
    }

    private void listenLoop(PostgresRegistryStore pg) {
        while (running) {
            try (Connection c = DriverManager.getConnection(pg.jdbcUrl(), pg.dbUser(), pg.dbPassword())) {
                PGConnection pgConn = c.unwrap(PGConnection.class);
                try (Statement st = c.createStatement()) {
                    st.execute("LISTEN giso_registry");
                }
                while (running) {
                    PGNotification[] notifications = pgConn.getNotifications(5_000);
                    if (notifications == null) continue;
                    for (PGNotification ignored : notifications) {
                        registry.reload();
                    }
                }
            } catch (Exception e) {
                if (!running) break;
                System.err.println("[giso-registry] listen reconnect: " + e.getMessage());
                try {
                    Thread.sleep(3_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    @Override
    public void close() {
        running = false;
        if (listenThread != null) listenThread.interrupt();
        if (pollThread != null) pollThread.interrupt();
    }
}
