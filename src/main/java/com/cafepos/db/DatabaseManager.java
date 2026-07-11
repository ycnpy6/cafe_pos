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
                migrateCustomersSchema(conn);
                normalizeCategories(conn);
                cleanupOrphanReferences(conn);

                // Customer seeding delegates to CustomerImporter, which uses
                // DatabaseManager.openConnection(). Make the pool available
                // before any seed work begins so a new database can import its
                // bundled customer list on first launch.
                pool = new ConnectionPool(jdbcUrl, 4);
                initialized = true;
                seedIfEmpty(conn);
            }

            // 4 connections is enough for: 1 long-running background load,
            // 1 short write, 1 FX-thread lazy read (e.g. UnitRegistry warm-up),
            // and 1 safety slot. Size 2 used to deadlock the UI when both
            // background tasks held the pool while a synchronous FX-thread
            // query (TableView cell render -> UnitRegistry.resolve -> DAO)
            // tried to borrow a 3rd connection.
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

    /**
     * Pragmas des connexions du pool (runtime). SQLite n'applique pas les
     * REFERENCES sans foreign_keys=ON (OFF par defaut, par connexion) : sans
     * lui, lignes orphelines possibles. Le pragma n'est PAS active sur la
     * connexion d'initialisation car schema.sql et les seeds inserent des
     * lignes avant leurs parents (ex: produits avant categories).
     */
    static void applyRuntimePragmas(Connection conn) throws SQLException {
        applyPragmas(conn);
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("PRAGMA foreign_keys=ON");
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
        seedCustomersIfEmpty(conn);
        seedIngredientsIfEmpty(conn);
        seedRecipesIfEmpty(conn);
    }

    /**
     * Seeds a minimal set of café-core ingredients + standard drink recipes
     * (espresso, latte, frappuccino, milkshakes…). Always runs, but every
     * insert uses INSERT OR IGNORE so user customisations and existing
     * (product_id, ingredient_id) pairs are preserved. Missing recipe lines
     * are quietly added.
     */
    private static void seedRecipesIfEmpty(Connection conn) throws Exception {
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            // Minimal core ingredient set (display unit == base unit, factor = 1).
            // Names chosen to avoid colliding with the 63-item baseline seed
            // (e.g. "Banane (pièce)" so we don't overwrite the KG "Banane").
            Object[][] coreIngredients = new Object[][] {
                // {name, displayUnit, packageSize, packagePrice, stock, min}
                {"Café moulu (espresso)",    "G",    1000.0, 0.0, 0.0, 200.0},
                {"Lait",                     "ML",   1000.0, 0.0, 0.0, 500.0},
                {"Glace",                    "ML",   1000.0, 0.0, 0.0, 500.0},
                {"Poudre de chocolat",       "G",    1000.0, 0.0, 0.0, 100.0},
                {"Chocolate Mordjane",       "G",     500.0, 0.0, 0.0,  50.0},
                {"Nescafé",                  "G",     200.0, 0.0, 0.0, 100.0},
                {"Sirop",                    "ML",    750.0, 0.0, 0.0, 200.0},
                {"Lait concentré sucré",     "ML",    400.0, 0.0, 0.0, 100.0},
                {"Thé",                      "G",     250.0, 0.0, 0.0,  50.0},
                {"Banane (pièce)",           "UNIT",    1.0, 0.0, 0.0,   5.0},
            };
            String insertIng = "INSERT OR IGNORE INTO ingredients "
                    + "(name, unit, unit_base, unit_factor, package_size, package_price, "
                    + " stock_quantity, min_quantity, stock_base_quantity, min_base_quantity, active) "
                    + "VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?, 1)";
            int coreInserted = 0;
            try (PreparedStatement ps = conn.prepareStatement(insertIng)) {
                for (Object[] row : coreIngredients) {
                    String name = (String) row[0];
                    String unit = (String) row[1];
                    double pkgSize  = ((Number) row[2]).doubleValue();
                    double pkgPrice = ((Number) row[3]).doubleValue();
                    double stock    = ((Number) row[4]).doubleValue();
                    double min      = ((Number) row[5]).doubleValue();
                    ps.setString(1, name);
                    ps.setString(2, unit);
                    ps.setString(3, unit);
                    ps.setDouble(4, pkgSize);
                    ps.setDouble(5, pkgPrice);
                    ps.setDouble(6, stock);
                    ps.setDouble(7, min);
                    ps.setDouble(8, stock);
                    ps.setDouble(9, min);
                    try {
                        coreInserted += ps.executeUpdate();
                    } catch (SQLException ex) {
                        LOG.warn("Seed recettes: echec insert ingredient '{}': {}", name, ex.getMessage());
                    }
                }
            }
            LOG.info("Seed recettes: {} ingredients core inseres (existants conserves)", coreInserted);

            // Resolve ingredient ids by name (handles the case where the row
            // already existed from a previous seed pass).
            java.util.Map<String, int[]> ingIds = new java.util.HashMap<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, name, unit FROM ingredients")) {
                while (rs.next()) {
                    ingIds.put(rs.getString("name"), new int[] { rs.getInt("id") });
                }
            }

            // Resolve product ids (case-insensitive).
            java.util.Map<String, Integer> productIds = new java.util.HashMap<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, name FROM products")) {
                while (rs.next()) {
                    productIds.put(rs.getString("name").toLowerCase(java.util.Locale.ROOT), rs.getInt("id"));
                }
            }

            // {productName, ingredientName, displayUnit, qtyInDisplayUnit}
            // Quantities chosen per the standard café recipe sheet
            // (8oz ≈ 240ml, 6oz ≈ 180ml, 1 scoop glace = 150ml).
            Object[][] recipes = new Object[][] {
                {"Espresso",                   "Café moulu (espresso)", "G",  15.0},
                {"Americano",                  "Café moulu (espresso)", "G",  15.0},
                {"Double Espresso",            "Café moulu (espresso)", "G",  30.0},

                {"Latte",                      "Café moulu (espresso)", "G",  15.0},
                {"Latte",                      "Lait",                  "ML", 240.0},

                {"Cappuccino",                 "Café moulu (espresso)", "G",  15.0},
                {"Cappuccino",                 "Lait",                  "ML", 240.0},

                {"Hot Chocolate",              "Lait",                  "ML", 300.0},
                {"Hot Chocolate",              "Poudre de chocolat",    "G",  30.0},
                {"Hot Chocolate",              "Chocolate Mordjane",    "G",  20.0},

                {"Chocolate Latte",            "Café moulu (espresso)", "G",  15.0},
                {"Chocolate Latte",            "Lait",                  "ML", 240.0},
                {"Chocolate Latte",            "Poudre de chocolat",    "G",  15.0},

                {"Macchiato",                  "Café moulu (espresso)", "G",  15.0},
                {"Macchiato",                  "Lait",                  "ML", 180.0},

                {"Mocha",                      "Café moulu (espresso)", "G",  15.0},
                {"Mocha",                      "Chocolate Mordjane",    "G",  20.0},

                {"Dalgona Coffee",             "Nescafé",               "G",  15.0},
                {"Dalgona Coffee",             "Lait",                  "ML", 180.0},

                {"Iced Latte",                 "Café moulu (espresso)", "G",  15.0},
                {"Iced Latte",                 "Lait",                  "ML", 180.0},
                {"Iced Latte",                 "Glace",                 "ML", 300.0},

                {"Iced Espresso",              "Café moulu (espresso)", "G",  15.0},
                {"Iced Espresso",              "Glace",                 "ML", 300.0},

                {"Iced Coffee",                "Café moulu (espresso)", "G",  15.0},
                {"Iced Coffee",                "Glace",                 "ML", 300.0},

                {"Iced Tea",                   "Thé",                   "G",  15.0},
                {"Iced Tea",                   "Sirop",                 "ML", 40.0},

                {"Spanish Latte",              "Café moulu (espresso)", "G",  15.0},
                {"Spanish Latte",              "Lait",                  "ML", 180.0},
                {"Spanish Latte",              "Lait concentré sucré",  "ML", 40.0},

                {"Banana Milkshake",           "Banane (pièce)",        "UNIT", 1.0},
                {"Banana Milkshake",           "Glace",                 "ML", 150.0},
                {"Banana Milkshake",           "Lait",                  "ML", 180.0},

                {"Chocolate Milkshake",        "Glace",                 "ML", 150.0},
                {"Chocolate Milkshake",        "Lait",                  "ML", 180.0},
                {"Chocolate Milkshake",        "Poudre de chocolat",    "G",  60.0},

                {"Caramel Milkshake",          "Glace",                 "ML", 150.0},
                {"Caramel Milkshake",          "Lait",                  "ML", 180.0},
                {"Caramel Milkshake",          "Poudre de chocolat",    "G",  60.0},

                {"Vanilla Milkshake",          "Glace",                 "ML", 150.0},
                {"Vanilla Milkshake",          "Lait",                  "ML", 180.0},
                {"Vanilla Milkshake",          "Poudre de chocolat",    "G",  60.0},

                {"Strawberry Milkshake",       "Glace",                 "ML", 150.0},
                {"Strawberry Milkshake",       "Lait",                  "ML", 180.0},
                {"Strawberry Milkshake",       "Sirop",                 "ML", 80.0},

                {"Banana Chocolate Milkshake", "Banane (pièce)",        "UNIT", 1.0},
                {"Banana Chocolate Milkshake", "Glace",                 "ML", 150.0},
                {"Banana Chocolate Milkshake", "Lait",                  "ML", 180.0},
                {"Banana Chocolate Milkshake", "Poudre de chocolat",    "G",  60.0},

                {"Frappuccino Coffee",         "Café moulu (espresso)", "G",  15.0},
                {"Frappuccino Coffee",         "Lait",                  "ML", 120.0},
                {"Frappuccino Coffee",         "Glace",                 "ML", 450.0},

                {"Frappuccino Vanilla",        "Café moulu (espresso)", "G",  15.0},
                {"Frappuccino Vanilla",        "Lait",                  "ML", 120.0},
                {"Frappuccino Vanilla",        "Glace",                 "ML", 450.0},
                {"Frappuccino Vanilla",        "Sirop",                 "ML", 40.0},

                {"Frappuccino Caramel",        "Café moulu (espresso)", "G",  15.0},
                {"Frappuccino Caramel",        "Lait",                  "ML", 120.0},
                {"Frappuccino Caramel",        "Glace",                 "ML", 450.0},
                {"Frappuccino Caramel",        "Sirop",                 "ML", 40.0},

                {"Frappuccino Chocolate",      "Café moulu (espresso)", "G",  15.0},
                {"Frappuccino Chocolate",      "Lait",                  "ML", 120.0},
                {"Frappuccino Chocolate",      "Glace",                 "ML", 450.0},
                {"Frappuccino Chocolate",      "Poudre de chocolat",    "G",  60.0},

                {"Frappuccino Hazelnut",       "Café moulu (espresso)", "G",  15.0},
                {"Frappuccino Hazelnut",       "Lait",                  "ML", 120.0},
                {"Frappuccino Hazelnut",       "Glace",                 "ML", 450.0},
                {"Frappuccino Hazelnut",       "Sirop",                 "ML", 40.0},

                {"Frappuccino Pistachio",      "Café moulu (espresso)", "G",  15.0},
                {"Frappuccino Pistachio",      "Lait",                  "ML", 120.0},
                {"Frappuccino Pistachio",      "Glace",                 "ML", 450.0},
                {"Frappuccino Pistachio",      "Sirop",                 "ML", 40.0},

                {"Frappuccino Banana",         "Café moulu (espresso)", "G",  15.0},
                {"Frappuccino Banana",         "Lait",                  "ML", 120.0},
                {"Frappuccino Banana",         "Glace",                 "ML", 450.0},
                {"Frappuccino Banana",         "Banane (pièce)",        "UNIT", 1.0},
            };

            String insertRecipe = "INSERT OR IGNORE INTO product_ingredients "
                    + "(product_id, ingredient_id, quantity, unit, quantity_base) "
                    + "VALUES (?, ?, ?, ?, ?)";
            int linked = 0;
            int skipped = 0;
            try (PreparedStatement ps = conn.prepareStatement(insertRecipe)) {
                for (Object[] row : recipes) {
                    String productName    = (String) row[0];
                    String ingredientName = (String) row[1];
                    String unit           = (String) row[2];
                    double quantity       = ((Number) row[3]).doubleValue();

                    Integer productId = productIds.get(productName.toLowerCase(java.util.Locale.ROOT));
                    int[] ingId = ingIds.get(ingredientName);
                    if (productId == null || ingId == null) {
                        skipped++;
                        continue;
                    }
                    // For our core ingredients display unit == base unit so
                    // quantity_base == quantity (no scaling needed).
                    ps.setInt(1, productId);
                    ps.setInt(2, ingId[0]);
                    ps.setDouble(3, quantity);
                    ps.setString(4, unit);
                    ps.setDouble(5, quantity);
                    try {
                        linked += ps.executeUpdate();
                    } catch (SQLException ex) {
                        LOG.warn("Seed recettes: echec insert '{}' / '{}': {}",
                                productName, ingredientName, ex.getMessage());
                    }
                }
            }

            // Mark every beverage that just received a recipe as prepared so
            // OrderService deducts the ingredient stock at sale time.
            String[] preparedNames = new String[] {
                "espresso","americano","double espresso","latte","cappuccino",
                "hot chocolate","chocolate latte","macchiato","mocha",
                "dalgona coffee","iced latte","iced espresso","iced coffee",
                "iced tea","spanish latte",
                "banana milkshake","chocolate milkshake","caramel milkshake",
                "vanilla milkshake","strawberry milkshake","banana chocolate milkshake",
                "frappuccino coffee","frappuccino vanilla","frappuccino caramel",
                "frappuccino chocolate","frappuccino hazelnut","frappuccino pistachio",
                "frappuccino banana"
            };
            int marked = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE products SET is_prepared = 1 WHERE LOWER(name) = ?")) {
                for (String n : preparedNames) {
                    ps.setString(1, n);
                    marked += ps.executeUpdate();
                }
            }

            conn.commit();
            LOG.info("Seed recettes: {} lignes inserees, {} ignorees, {} produits marques prepares",
                    linked, skipped, marked);
        } catch (Exception ex) {
            conn.rollback();
            LOG.warn("Echec seed recettes (rollback)", ex);
            throw ex;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    /**
     * Seeds a baseline set of café ingredients. Uses INSERT OR IGNORE so any
     * ingredient the user has already created (matched by UNIQUE name) is
     * left untouched; only missing defaults are added.
     */
    private static void seedIngredientsIfEmpty(Connection conn) throws Exception {
        // {name, display unit, base unit, factor, package size, package price, stock, min}
        Object[][] rows = new Object[][] {
            // ── Café / Coffee ──────────────────────────────
            {"Café en grains",            "KG",    "G",    1000.0,   1.0,   1800.0, 5.0,    1.0},
            {"Café moulu décaféiné",      "KG",    "G",    1000.0,   1.0,   2200.0, 1.0,    0.5},
            {"Sirop vanille",             "ML",    "ML",   1.0,      750.0, 1200.0, 1500.0, 500.0},
            {"Sirop caramel",             "ML",    "ML",   1.0,      750.0, 1200.0, 1500.0, 500.0},
            {"Sirop noisette",            "ML",    "ML",   1.0,      750.0, 1200.0, 1500.0, 500.0},
            {"Chocolat en poudre",        "KG",    "G",    1000.0,   1.0,   1500.0, 2.0,    0.5},
            {"Cacao non sucré",           "G",     "G",    1.0,      250.0, 600.0,  500.0,  200.0},

            // ── Lait & dérivés / Dairy ─────────────────────
            {"Lait entier",               "L",     "ML",   1000.0,   1.0,   180.0,  20.0,   5.0},
            {"Lait demi-écrémé",          "L",     "ML",   1000.0,   1.0,   170.0,  10.0,   5.0},
            {"Lait d'amande",             "L",     "ML",   1000.0,   1.0,   450.0,  6.0,    2.0},
            {"Lait de soja",              "L",     "ML",   1000.0,   1.0,   420.0,  4.0,    2.0},
            {"Crème fraîche",             "ML",    "ML",   1.0,      500.0, 350.0,  2000.0, 500.0},
            {"Crème chantilly",           "ML",    "ML",   1.0,      250.0, 300.0,  1000.0, 250.0},

            // ── Thé / Tea ──────────────────────────────────
            {"Sachet thé noir",           "UNIT",  "UNIT", 1.0,      100.0, 800.0,  100.0,  20.0},
            {"Sachet thé vert",           "UNIT",  "UNIT", 1.0,      100.0, 900.0,  80.0,   20.0},
            {"Sachet thé à la menthe",    "UNIT",  "UNIT", 1.0,      100.0, 850.0,  80.0,   20.0},
            {"Sachet infusion",           "UNIT",  "UNIT", 1.0,      100.0, 1000.0, 50.0,   10.0},

            // ── Sucre / Sweeteners ─────────────────────────
            {"Sucre blanc",               "KG",    "G",    1000.0,   1.0,   180.0,  10.0,   2.0},
            {"Sucre roux",                "KG",    "G",    1000.0,   1.0,   220.0,  5.0,    1.0},
            {"Dosette de sucre",          "UNIT",  "UNIT", 1.0,      500.0, 600.0,  2000.0, 500.0},
            {"Édulcorant",                "UNIT",  "UNIT", 1.0,      500.0, 800.0,  500.0,  100.0},
            {"Miel",                      "ML",    "ML",   1.0,      500.0, 900.0,  1500.0, 250.0},

            // ── Boissons froides / Cold drinks ─────────────
            {"Glaçons",                   "KG",    "G",    1000.0,   1.0,   80.0,   8.0,    2.0},
            {"Eau minérale 50cl",         "UNIT",  "UNIT", 1.0,      1.0,   60.0,   60.0,   12.0},
            {"Eau minérale 1.5L",         "UNIT",  "UNIT", 1.0,      1.0,   90.0,   30.0,   6.0},
            {"Coca-Cola 33cl",            "UNIT",  "UNIT", 1.0,      1.0,   100.0,  48.0,   12.0},
            {"Coca-Cola Zero 33cl",       "UNIT",  "UNIT", 1.0,      1.0,   100.0,  24.0,   12.0},
            {"Sprite 33cl",               "UNIT",  "UNIT", 1.0,      1.0,   100.0,  24.0,   12.0},
            {"Fanta 33cl",                "UNIT",  "UNIT", 1.0,      1.0,   100.0,  24.0,   12.0},
            {"Jus d'orange",              "L",     "ML",   1000.0,   1.0,   350.0,  5.0,    2.0},
            {"Jus de pomme",              "L",     "ML",   1000.0,   1.0,   350.0,  3.0,    2.0},
            {"Sirop fraise",              "ML",    "ML",   1.0,      700.0, 600.0,  1400.0, 350.0},
            {"Sirop citron",              "ML",    "ML",   1.0,      700.0, 600.0,  1400.0, 350.0},

            // ── Fruits ─────────────────────────────────────
            {"Banane",                    "KG",    "G",    1000.0,   1.0,   350.0,  5.0,    2.0},
            {"Fraise",                    "KG",    "G",    1000.0,   1.0,   1200.0, 2.0,    0.5},
            {"Citron",                    "UNIT",  "UNIT", 1.0,      1.0,   40.0,   30.0,   10.0},
            {"Orange",                    "KG",    "G",    1000.0,   1.0,   280.0,  5.0,    2.0},
            {"Menthe fraîche",            "G",     "G",    1.0,      100.0, 150.0,  300.0,  50.0},

            // ── Pâtisserie / Bakery ────────────────────────
            {"Farine de blé",             "KG",    "G",    1000.0,   1.0,   140.0,  10.0,   2.0},
            {"Beurre",                    "KG",    "G",    1000.0,   1.0,   1400.0, 3.0,    1.0},
            {"Œuf",                       "UNIT",  "UNIT", 1.0,      30.0,  900.0,  60.0,   12.0},
            {"Croissant nature",          "UNIT",  "UNIT", 1.0,      1.0,   60.0,   30.0,   10.0},
            {"Pain au chocolat",          "UNIT",  "UNIT", 1.0,      1.0,   70.0,   30.0,   10.0},
            {"Muffin chocolat",           "UNIT",  "UNIT", 1.0,      1.0,   120.0,  20.0,   6.0},
            {"Cookie",                    "UNIT",  "UNIT", 1.0,      1.0,   90.0,   24.0,   8.0},
            {"Donut glacé",               "UNIT",  "UNIT", 1.0,      1.0,   130.0,  20.0,   6.0},
            {"Brownie",                   "UNIT",  "UNIT", 1.0,      1.0,   180.0,  15.0,   5.0},

            // ── Salé / Savory ──────────────────────────────
            {"Pain panini",               "UNIT",  "UNIT", 1.0,      1.0,   80.0,   30.0,   10.0},
            {"Tortilla",                  "UNIT",  "UNIT", 1.0,      1.0,   60.0,   30.0,   10.0},
            {"Jambon de dinde",           "KG",    "G",    1000.0,   1.0,   1600.0, 2.0,    0.5},
            {"Fromage emmental",          "KG",    "G",    1000.0,   1.0,   1800.0, 2.0,    0.5},
            {"Fromage mozzarella",        "KG",    "G",    1000.0,   1.0,   2000.0, 1.5,    0.5},
            {"Tomate",                    "KG",    "G",    1000.0,   1.0,   300.0,  3.0,    1.0},
            {"Salade verte",              "UNIT",  "UNIT", 1.0,      1.0,   80.0,   5.0,    2.0},
            {"Sauce mayonnaise",          "ML",    "ML",   1.0,      500.0, 350.0,  1500.0, 500.0},
            {"Sauce ketchup",             "ML",    "ML",   1.0,      500.0, 300.0,  1500.0, 500.0},

            // ── Consommables / Disposables ─────────────────
            {"Gobelet carton 8oz",        "UNIT",  "UNIT", 1.0,      50.0,  600.0,  500.0,  100.0},
            {"Gobelet carton 12oz",       "UNIT",  "UNIT", 1.0,      50.0,  750.0,  500.0,  100.0},
            {"Couvercle gobelet",         "UNIT",  "UNIT", 1.0,      100.0, 900.0,  600.0,  150.0},
            {"Touillette bois",           "UNIT",  "UNIT", 1.0,      1000.0,400.0,  3000.0, 500.0},
            {"Paille papier",             "UNIT",  "UNIT", 1.0,      500.0, 350.0,  1500.0, 300.0},
            {"Serviette papier",          "UNIT",  "UNIT", 1.0,      500.0, 250.0,  2000.0, 500.0},
            {"Sac de transport",          "UNIT",  "UNIT", 1.0,      100.0, 400.0,  300.0,  50.0},
        };

        String sql = "INSERT OR IGNORE INTO ingredients " +
                "(name, unit, unit_base, unit_factor, package_size, package_price, " +
                " stock_quantity, min_quantity, stock_base_quantity, min_base_quantity, active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";
        int inserted = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] r : rows) {
                String name        = (String) r[0];
                String displayUnit = (String) r[1];
                String baseUnit    = (String) r[2];
                double factor      = ((Number) r[3]).doubleValue();
                double packSize    = ((Number) r[4]).doubleValue();
                double packPrice   = ((Number) r[5]).doubleValue();
                double stock       = ((Number) r[6]).doubleValue();
                double min         = ((Number) r[7]).doubleValue();
                ps.setString(1, name);
                ps.setString(2, displayUnit);
                ps.setString(3, baseUnit);
                ps.setDouble(4, factor);
                ps.setDouble(5, packSize);
                ps.setDouble(6, packPrice);
                ps.setDouble(7, stock);
                ps.setDouble(8, min);
                ps.setDouble(9, stock * factor);
                ps.setDouble(10, min * factor);
                try {
                    inserted += ps.executeUpdate();
                } catch (SQLException ex) {
                    LOG.warn("Echec insert ingredient '{}': {}", name, ex.getMessage());
                }
            }
        }
        LOG.info("Seed ingredients: {} elements de base inseres", inserted);
    }

    private static void seedCustomersIfEmpty(Connection conn) throws Exception {
        if (!isTableEmpty(conn, "customers")) {
            return;
        }
        try {
            com.cafepos.util.CustomerImporter.ImportResult result =
                    com.cafepos.util.CustomerImporter.importFromResource("/db/customers_seed.csv");
            LOG.info("Seed clients depuis customers_seed.csv: {}", result.summary());
        } catch (IllegalArgumentException e) {
            LOG.info("Aucun fichier de seed clients embarque (ignore).");
        } catch (Exception e) {
            LOG.warn("Echec seed clients", e);
        }
    }

    private static void migrateCustomersSchema(Connection conn) throws Exception {
        // SQLite ALTER TABLE cannot drop NOT NULL; if card_uid is NOT NULL we rebuild.
        boolean cardUidNotNull = false;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(customers)")) {
            while (rs.next()) {
                String col = rs.getString("name");
                int notNull = rs.getInt("notnull");
                if ("card_uid".equalsIgnoreCase(col) && notNull == 1) {
                    cardUidNotNull = true;
                    break;
                }
            }
        }
        if (!cardUidNotNull) {
            return;
        }
        LOG.info("Migration table customers: relaxe NOT NULL sur card_uid");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys=OFF");
            stmt.execute("BEGIN IMMEDIATE TRANSACTION");
            stmt.execute("DROP TABLE IF EXISTS customers_new");
            stmt.execute("""
                    CREATE TABLE customers_new (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      name TEXT NOT NULL,
                      card_uid TEXT UNIQUE,
                      balance REAL DEFAULT 0,
                      active INTEGER NOT NULL DEFAULT 1,
                      phone TEXT,
                      email TEXT,
                      address TEXT,
                      lifetime_spent REAL DEFAULT 0,
                      visit_count INTEGER DEFAULT 0,
                      last_visit_at TEXT,
                      created_at TEXT DEFAULT (datetime('now'))
                    )
                    """);
            stmt.execute("""
                    INSERT INTO customers_new (id, name, card_uid, balance, active, created_at)
                    SELECT id, name, card_uid, balance, active, created_at FROM customers
                    """);
            stmt.execute("DROP TABLE customers");
            stmt.execute("ALTER TABLE customers_new RENAME TO customers");
            stmt.execute("COMMIT");
            stmt.execute("PRAGMA foreign_keys=ON");
        } catch (SQLException e) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ROLLBACK");
            } catch (Exception ignore) {}
            throw e;
        }
    }

    /**
     * Les bases creees avant l'activation de foreign_keys=ON (connexions du
     * pool) peuvent contenir des references orphelines. Elles feraient
     * echouer les UPDATE de ces lignes une fois le pragma actif : on nettoie
     * les tables encore modifiables (l'historique immuable n'est pas touche).
     */
    private static void cleanupOrphanReferences(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            int products = stmt.executeUpdate(
                    "UPDATE products SET category_id = NULL "
                            + "WHERE category_id IS NOT NULL "
                            + "AND category_id NOT IN (SELECT id FROM categories)");
            int recipes = stmt.executeUpdate(
                    "DELETE FROM product_ingredients "
                            + "WHERE ingredient_id NOT IN (SELECT id FROM ingredients) "
                            + "OR product_id NOT IN (SELECT id FROM products)");
            int tags = stmt.executeUpdate(
                    "DELETE FROM tags WHERE group_id IS NOT NULL "
                            + "AND group_id NOT IN (SELECT id FROM tag_groups)");
            int productTagGroups = stmt.executeUpdate(
                    "DELETE FROM product_tag_groups "
                            + "WHERE product_id NOT IN (SELECT id FROM products) "
                            + "OR group_id NOT IN (SELECT id FROM tag_groups)");
            if (products + recipes + tags + productTagGroups > 0) {
                LOG.info("Nettoyage references orphelines: {} produits, {} lignes recette, {} tags, {} liens produit-groupe",
                        products, recipes, tags, productTagGroups);
            }
        }
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
        ensureProduct(conn, "Americano", 1, true);
        ensureProduct(conn, "Spanish Latte", 1, true);

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
        ensureProduct(conn, "Frappuccino Chocolate", 2, true);
        ensureProduct(conn, "Frappuccino Hazelnut", 2, true);
        ensureProduct(conn, "Frappuccino Pistachio", 2, true);
        ensureProduct(conn, "Iced Coffee", 2, true);
        ensureProduct(conn, "Strawberry Milkshake", 2, true);
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
