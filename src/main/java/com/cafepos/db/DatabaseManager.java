package com.cafepos.db;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                normalizeCategories(conn);
                seedIfEmpty(conn);
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
        String override = System.getProperty("cafepos.db.path");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
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

    private static void seedIfEmpty(Connection conn) throws Exception {
        if (isTableEmpty(conn, "products")) {
            String seedSql = readResourceText("/db/seed.sql");
            executeScript(conn, seedSql);
        }
        ensureBrandSettings(conn);
        ensureRequiredCategories(conn);
        ensureRequiredProducts(conn);
        ensureRequiredTagGroups(conn);
        ensureRequiredTags(conn);
        linkSupplementGroupsToBeverages(conn);
    }

    private static void normalizeCategories(Connection conn) throws Exception {
        cleanupDuplicateCategories(conn);
        migrateCategoriesUniqueNoCase(conn);
    }

    private static void cleanupDuplicateCategories(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            // Repoint products to the survivor category id (MIN id by lower(name)).
            stmt.executeUpdate("""
                    UPDATE products
                    SET category_id = (
                        SELECT MIN(c2.id)
                        FROM categories c2
                        WHERE LOWER(c2.name) = LOWER((
                            SELECT c3.name FROM categories c3 WHERE c3.id = products.category_id
                        ))
                    )
                    WHERE category_id IS NOT NULL
                    AND EXISTS (
                        SELECT 1 FROM categories c WHERE c.id = products.category_id
                    )
                    """);

            stmt.executeUpdate("""
                    DELETE FROM categories
                    WHERE id NOT IN (
                        SELECT MIN(id) FROM categories GROUP BY LOWER(name)
                    )
                    """);
        }
    }

    private static void migrateCategoriesUniqueNoCase(Connection conn) throws Exception {
        int oldCount = queryCount(conn, "SELECT COUNT(*) FROM categories");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys=OFF");
            stmt.execute("BEGIN IMMEDIATE TRANSACTION");

            stmt.execute("DROP TABLE IF EXISTS categories_new");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS categories_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        color TEXT DEFAULT '#6B2D1A',
                        icon TEXT,
                        icon_code TEXT DEFAULT 'mdi2s-star-outline',
                        sort_order INTEGER DEFAULT 0
                    )
                    """);

                 stmt.executeUpdate("""
                      INSERT OR IGNORE INTO categories_new (id, name, color, icon, icon_code, sort_order)
                      SELECT id,
                          name,
                          COALESCE(color, '#6B2D1A'),
                          icon,
                          COALESCE(icon_code, icon, 'mdi2s-star-outline'),
                          COALESCE(sort_order, 0)
                      FROM categories
                      ORDER BY id
                      """);

            // Ensure product category ids stay valid after table swap.
            stmt.executeUpdate("""
                    UPDATE products
                    SET category_id = (
                        SELECT cn.id
                        FROM categories c
                        JOIN categories_new cn ON LOWER(cn.name) = LOWER(c.name)
                        WHERE c.id = products.category_id
                        LIMIT 1
                    )
                    WHERE category_id IS NOT NULL
                    """);

            int newCount = queryCount(conn, "SELECT COUNT(*) FROM categories_new");
            if (oldCount > 0 && newCount <= 0) {
                stmt.execute("ROLLBACK");
                stmt.execute("PRAGMA foreign_keys=ON");
                LOG.warn("Migration categories ignoree: categories_new vide");
                return;
            }

            stmt.execute("DROP TABLE categories");
            stmt.execute("ALTER TABLE categories_new RENAME TO categories");
            stmt.execute("COMMIT");
            stmt.execute("PRAGMA foreign_keys=ON");
        } catch (Exception ex) {
            try (Statement rollback = conn.createStatement()) {
                rollback.execute("ROLLBACK");
                rollback.execute("PRAGMA foreign_keys=ON");
            } catch (Exception ignored) {
            }
            throw ex;
        }
    }

    private static int queryCount(Connection conn, String sql) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    private static void executeScript(Connection conn, String sqlScript) throws Exception {
        String[] statements = sqlScript.split(";");
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

    private static boolean isTableEmpty(Connection conn, String tableName) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }

    private static void ensureRequiredCategories(Connection conn) throws Exception {
        ensureCategory(conn, 1, "Hot Beverages", "#6B2D1A", 1);
        ensureCategory(conn, 2, "Cold Beverages", "#1A4A6B", 2);
        ensureCategory(conn, 3, "Sweets", "#A0522D", 3);
        ensureCategory(conn, 4, "Salties", "#7A4A1A", 4);
        ensureCategory(conn, 5, "Cards", "#2E5A2E", 5);
        ensureCategory(conn, 6, "Additions", "#4A3A6B", 6);
    }

    private static void ensureCategory(Connection conn, int id, String name, String color, int sortOrder) throws Exception {
        Integer existingCategoryId = findCategoryIdByName(conn, name);

        if (existingCategoryId == null) {
            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT OR IGNORE INTO categories (id, name, color, sort_order) VALUES (?, ?, ?, ?)")) {
                insert.setInt(1, id);
                insert.setString(2, name);
                insert.setString(3, color);
                insert.setInt(4, sortOrder);
                insert.executeUpdate();
            }
            existingCategoryId = findCategoryIdByName(conn, name);
        }

        int targetId = existingCategoryId == null ? id : existingCategoryId;

        try (PreparedStatement update = conn.prepareStatement(
                "UPDATE categories SET name = ?, color = ?, sort_order = ? WHERE id = ?")) {
            update.setString(1, name);
            update.setString(2, color);
            update.setInt(3, sortOrder);
            update.setInt(4, targetId);
            update.executeUpdate();
        }
    }

    private static Integer findCategoryIdByName(Connection conn, String name) throws Exception {
        String sql = "SELECT id FROM categories WHERE LOWER(name) = LOWER(?) ORDER BY id LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return null;
            }
        }
    }

    private static void ensureRequiredProducts(Connection conn) throws Exception {
        // Hot Beverages (category_id = 1)
        ensureProduct(conn, "Macchiato", 1, true);
        ensureProduct(conn, "Drip Coffee", 1, true);
        ensureProduct(conn, "Hot Chocolate", 1, true);
        ensureProduct(conn, "Espresso", 1, true);
        ensureProduct(conn, "Mocha", 1, true);
        ensureProduct(conn, "Double Espresso", 1, true);
        ensureProduct(conn, "Vienna Coffee", 1, true);
        ensureProduct(conn, "Hot Tea", 1, true);
        ensureProduct(conn, "Dalgona Coffee", 1, true);
        ensureProduct(conn, "Latte", 1, true);
        ensureProduct(conn, "Hot Milk", 1, true);
        ensureProduct(conn, "Chocolate Latte", 1, true);
        ensureProduct(conn, "Chocolate Milk", 1, true);
        ensureProduct(conn, "Cappuccino", 1, true);

        // Cold Beverages (category_id = 2)
        ensureProduct(conn, "Frappuccino Vanilla", 2, true);
        ensureProduct(conn, "Banana Juice", 2, true);
        ensureProduct(conn, "Iced Espresso", 2, true);
        ensureProduct(conn, "Frappuccino Caramel", 2, true);
        ensureProduct(conn, "Chocolate Milkshake", 2, true);
        ensureProduct(conn, "Iced Latte", 2, true);
        ensureProduct(conn, "Frappuccino Banana", 2, true);
        ensureProduct(conn, "Vanilla Milkshake", 2, true);
        ensureProduct(conn, "Iced Chocolate Latte", 2, true);
        ensureProduct(conn, "Caramel Milkshake", 2, true);
        ensureProduct(conn, "Banana Milkshake", 2, true);
        ensureProduct(conn, "Iced Tea", 2, true);
        ensureProduct(conn, "Juice", 2, false);
        ensureProduct(conn, "Banana Chocolate Milkshake", 2, true);
        ensureProduct(conn, "Orange Juice", 2, false);
        ensureProduct(conn, "Frappuccino Coffee", 2, true);
        ensureProduct(conn, "Lemonade", 2, false);

        // Sweets (category_id = 3)
        ensureProduct(conn, "Br Speculoos", 3, false);
        ensureProduct(conn, "Br Caramelo", 3, false);
        ensureProduct(conn, "Nutella Cookie", 3, false);
        ensureProduct(conn, "Chocolate Cookie", 3, false);
        ensureProduct(conn, "Br Bueno", 3, false);
        ensureProduct(conn, "Br Simple", 3, false);
        ensureProduct(conn, "Salted Caramel", 3, false);
        ensureProduct(conn, "Cookies Bueno", 3, false);
        ensureProduct(conn, "Br Ferrero", 3, false);
        ensureProduct(conn, "Br Pistache", 3, false);
        ensureProduct(conn, "Salbuz", 3, false);
        ensureProduct(conn, "Kinder Cookie", 3, false);
        ensureProduct(conn, "Pain au Chocolat", 3, false);
        ensureProduct(conn, "Br Oreo", 3, false);
        ensureProduct(conn, "Zlabiya", 3, false);
        ensureProduct(conn, "Lemon Bar", 3, false);
        ensureProduct(conn, "Croissant", 3, false);
        ensureProduct(conn, "Classic Cookies", 3, false);
        ensureProduct(conn, "Brownies", 3, false);
        ensureProduct(conn, "Dark Chocolate Cookie", 3, false);
        ensureProduct(conn, "FM's Cookies", 3, false);
        ensureProduct(conn, "Cheese Cake", 3, false);
        ensureProduct(conn, "Donut", 3, false);
        ensureProduct(conn, "Donut Gourmand", 3, false);
        ensureProduct(conn, "Donut Smile", 3, false);

        // Salties (category_id = 4)
        ensureProduct(conn, "Mini Pizza", 4, false);
        ensureProduct(conn, "Mini Burger", 4, false);
        ensureProduct(conn, "Club Sandwich", 4, false);
        ensureProduct(conn, "Mini Tacos", 4, false);
        ensureProduct(conn, "Bagels", 4, false);
        ensureProduct(conn, "Mini Sandwich", 4, false);
        ensureProduct(conn, "Pop Corn", 4, false);
    }

    private static void ensureRequiredTagGroups(Connection conn) throws Exception {
        ensureTagGroup(conn, 1, "Additions", true);
        ensureTagGroup(conn, 2, "Type de lait", false);
        ensureTagGroup(conn, 3, "Sucre", false);
        ensureTagGroup(conn, 4, "Taille", false);
    }

    private static void ensureTagGroup(Connection conn, int id, String name, boolean multiSelect) throws Exception {
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT OR IGNORE INTO tag_groups (id, name, multi_select) VALUES (?, ?, ?)")) {
            insert.setInt(1, id);
            insert.setString(2, name);
            insert.setInt(3, multiSelect ? 1 : 0);
            insert.executeUpdate();
        }

        try (PreparedStatement update = conn.prepareStatement(
                "UPDATE tag_groups SET name = ?, multi_select = ? WHERE id = ?")) {
            update.setString(1, name);
            update.setInt(2, multiSelect ? 1 : 0);
            update.setInt(3, id);
            update.executeUpdate();
        }
    }

    private static void ensureRequiredTags(Connection conn) throws Exception {
        // Additions tags (group_id = 1)
        ensureTag(conn, 1, "Chocolate", 0);
        ensureTag(conn, 1, "Hazelnut Syrup", 0);
        ensureTag(conn, 1, "Iced", 0);
        ensureTag(conn, 1, "Salted Caramel Syrup", 0);
        ensureTag(conn, 1, "Vanilla Syrup", 0);
        ensureTag(conn, 1, "Milk", 0);
        ensureTag(conn, 1, "Caramel Syrup", 0);
        ensureTag(conn, 1, "Money Back Espece", 0);

        // Type de lait tags (group_id = 2)
        ensureTag(conn, 2, "Lait entier", 0);
        ensureTag(conn, 2, "Lait demi", 0);
        ensureTag(conn, 2, "Lait d'avoine", 0);
        ensureTag(conn, 2, "Lait de soja", 0);
        ensureTag(conn, 2, "Sans lait", 0);

        // Sucre tags (group_id = 3)
        ensureTag(conn, 3, "Sans sucre", 0);
        ensureTag(conn, 3, "1 sucre", 0);
        ensureTag(conn, 3, "2 sucres", 0);

        // Taille tags (group_id = 4)
        ensureTag(conn, 4, "Small", 0);
        ensureTag(conn, 4, "Medium", 0);
        ensureTag(conn, 4, "Large", 0);
    }

    private static void ensureTag(Connection conn, int groupId, String name, double modifier) throws Exception {
        if (tagExists(conn, groupId, name)) {
            return;
        }

        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO tags (group_id, name, price_modifier) VALUES (?, ?, ?)")) {
            insert.setInt(1, groupId);
            insert.setString(2, name);
            insert.setDouble(3, modifier);
            insert.executeUpdate();
        }
    }

    private static boolean tagExists(Connection conn, int groupId, String name) throws Exception {
        String sql = "SELECT 1 FROM tags WHERE group_id = ? AND LOWER(name) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void ensureProduct(Connection conn, String name, int categoryId, boolean prepared) throws Exception {
        if (productExists(conn, name, categoryId)) {
            return;
        }

        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO products (name, price, cost, category_id, stock, active, is_prepared) VALUES (?, 0, 0, ?, 0, 1, ?)")) {
            insert.setString(1, name);
            insert.setInt(2, categoryId);
            insert.setInt(3, prepared ? 1 : 0);
            insert.executeUpdate();
        }
    }

    private static boolean productExists(Connection conn, String name, int categoryId) throws Exception {
        String sql = "SELECT 1 FROM products WHERE LOWER(name) = LOWER(?) AND category_id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void linkSupplementGroupsToBeverages(Connection conn) throws Exception {
        String linkSql = """
                INSERT OR IGNORE INTO product_tag_groups (product_id, group_id)
                SELECT p.id, tg.id
                FROM products p, tag_groups tg
                WHERE p.category_id IN (1, 2)
                AND tg.id IN (1, 2, 3, 4)
                """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(linkSql);
        }
    }

    private static void ensureBrandSettings(Connection conn) throws Exception {
        String[] sqlStatements = {
                "INSERT OR REPLACE INTO app_settings (key, value) VALUES ('app_name', 'Common Grounds')",
                "INSERT OR REPLACE INTO app_settings (key, value) VALUES ('theme', 'light')",
                "INSERT OR REPLACE INTO app_settings (key, value) VALUES ('brand_primary', '#6B2D1A')",
                "INSERT OR REPLACE INTO app_settings (key, value) VALUES ('brand_bg', '#F5ECD7')"
        };
        try (Statement stmt = conn.createStatement()) {
            for (String sql : sqlStatements) {
                stmt.executeUpdate(sql);
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
