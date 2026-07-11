package com.cafepos.test;

import com.cafepos.db.DatabaseManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

public final class TestDbHelper {
    private static boolean initialized;
    private static Path dbPath;

    private TestDbHelper() {
    }

    public static synchronized void initDatabase() throws Exception {
        if (initialized) {
            return;
        }
        Path tempDir = Files.createTempDirectory("cafepos-test-db");
        dbPath = tempDir.resolve("cafepos-test.db");
        System.setProperty("cafepos.db.path", dbPath.toString());
        DatabaseManager.initialize();
        initialized = true;
    }

    public static void resetData() throws Exception {
        try (Connection conn = DatabaseManager.openConnection();
             Statement stmt = conn.createStatement()) {
            // Enfants avant parents : les connexions du pool appliquent
            // foreign_keys=ON, l'ordre de suppression compte desormais.
            stmt.execute("DELETE FROM order_line_tags");
            stmt.execute("DELETE FROM refund_lines");
            stmt.execute("DELETE FROM refunds");
            stmt.execute("DELETE FROM waiting_order_line_tags");
            stmt.execute("DELETE FROM waiting_order_lines");
            stmt.execute("DELETE FROM waiting_orders");
            stmt.execute("DELETE FROM product_tag_groups");
            stmt.execute("DELETE FROM ingredient_movements");
            stmt.execute("DELETE FROM stock_movements");
            stmt.execute("DELETE FROM print_queue");
            stmt.execute("DELETE FROM order_lines");
            stmt.execute("DELETE FROM orders");
            stmt.execute("DELETE FROM product_ingredients");
            stmt.execute("DELETE FROM products");
            stmt.execute("DELETE FROM ingredients");
        }
    }
}
