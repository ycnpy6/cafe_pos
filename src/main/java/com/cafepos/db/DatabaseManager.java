package com.cafepos.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseManager.class);
    private static volatile boolean initialized;
    private static volatile String jdbcUrl;
    private static ConnectionPool pool;

    private DatabaseManager() {
    }

    public static void initialize() throws Exception {
        if (initialized) {
            return;
        }
        synchronized (DatabaseManager.class) {
            if (initialized) {
                return;
            }
            Path dbPath = getDbPath();
            Files.createDirectories(dbPath.getParent());

            jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
            LOG.info("Initialisation DB: {}", dbPath.toAbsolutePath());

            // Ouverture courte pour verifier la DB et charger le schema.
            try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
                applyPragmas(conn);
                runSchema(conn);
                runSeed(conn);
            }

            pool = new ConnectionPool(jdbcUrl, 2);
            initialized = true;
        }
    }

    public static Connection openConnection() throws SQLException {
        if (!initialized || pool == null) {
            throw new IllegalStateException("DB non initialisee");
        }
        return pool.borrowConnection();
    }

    private static Path getDbPath() {
        // Chemin Windows recommande pour eviter les ecritures dans Program Files.
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            String userHome = System.getProperty("user.home");
            return Paths.get(userHome, ".CafePOS", "data", "cafepos.db");
        }
        return Paths.get(appData, "CafePOS", "data", "cafepos.db");
    }

    static void applyPragmas(Connection conn) throws SQLException {
        // Reglages SQLite pour un bon compromis perf/memoire.
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("PRAGMA journal_mode=WAL");
            stmt.executeUpdate("PRAGMA synchronous=NORMAL");
            stmt.executeUpdate("PRAGMA cache_size=2000");
        }
    }

    private static void runSchema(Connection conn) throws Exception {
        String schemaSql = readResourceText("/db/schema.sql");
        String[] statements = schemaSql.split(";");
        for (String raw : statements) {
            String stmtText = raw.trim();
            if (stmtText.isEmpty()) {
                continue;
            }
            try (Statement stmt = conn.createStatement()) {
                try {
                    stmt.execute(stmtText);
                } catch (SQLException ex) {
                    // Ignore les erreurs de colonne existante pour les ALTER TABLE repetes.
                    String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                    if (message.contains("duplicate column name")) {
                        continue;
                    }
                    throw ex;
                }
            }
        }
    }

    private static void runSeed(Connection conn) throws Exception {
        String seedSql = readResourceText("/db/seed.sql");
        String[] statements = seedSql.split(";");
        for (String raw : statements) {
            String stmtText = raw.trim();
            if (stmtText.isEmpty()) {
                continue;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(stmtText);
            }
        }
    }

    private static String readResourceText(String path) throws Exception {
        try (InputStream input = DatabaseManager.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Schema introuvable: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
