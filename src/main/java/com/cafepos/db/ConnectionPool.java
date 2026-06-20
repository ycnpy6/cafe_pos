package com.cafepos.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConnectionPool {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionPool.class);
    private final String jdbcUrl;
    private final ArrayBlockingQueue<Connection> pool;

    public ConnectionPool(String jdbcUrl, int size) throws SQLException {
        this.jdbcUrl = jdbcUrl;
        this.pool = new ArrayBlockingQueue<>(size);
        for (int i = 0; i < size; i++) {
            Connection conn = DriverManager.getConnection(jdbcUrl);
            DatabaseManager.applyPragmas(conn);
            pool.offer(conn);
        }
    }

    /**
     * Borrow a pooled connection, waiting at most {@value #BORROW_TIMEOUT_SECONDS}
     * seconds. A timeout is far better than the original {@code take()} which
     * blocked forever — that turned any pool exhaustion into a silent UI freeze
     * ("application not responding"). With a timeout, the caller sees a real
     * SQLException, the stack trace is logged, and the UI thread is freed.
     */
    public Connection borrowConnection() throws SQLException {
        try {
            Connection physical = pool.poll(BORROW_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (physical == null) {
                LOG.error("Pool de connexions sature ({} actives). Operation annulee pour eviter un blocage UI.",
                        pool.remainingCapacity());
                throw new SQLException("Pool SQLite sature (timeout " + BORROW_TIMEOUT_SECONDS + "s)");
            }
            return wrap(physical);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interruption lors de l'acces a la connexion", ex);
        }
    }

    private static final long BORROW_TIMEOUT_SECONDS = 10;

    private void returnConnection(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            if (!conn.getAutoCommit()) {
                conn.rollback();
                conn.setAutoCommit(true);
            }
            if (conn.isClosed()) {
                Connection replacement = DriverManager.getConnection(jdbcUrl);
                DatabaseManager.applyPragmas(replacement);
                pool.offer(replacement);
                return;
            }
        } catch (SQLException ex) {
            LOG.warn("Connexion SQLite perdue, recreation echouee.", ex);
            return;
        }
        pool.offer(conn);
    }

    private Connection wrap(Connection conn) {
        AtomicBoolean returned = new AtomicBoolean(false);
        InvocationHandler handler = (proxy, method, args) -> {
            if ("close".equals(method.getName())) {
                if (returned.compareAndSet(false, true)) {
                    returnConnection(conn);
                }
                return null;
            }
            return method.invoke(conn, args);
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                handler
        );
    }
}
